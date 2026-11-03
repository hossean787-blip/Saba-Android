package ir.saba.invoice.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Only non-financial GET endpoints are available; this transport has no invoice submission method. */
interface MoadianPublicTransport {
    suspend fun nonce(): String
    suspend fun serverInformation(authorization: String): String
}
class OfficialMoadianPublicTransport : MoadianPublicTransport {
    override suspend fun nonce() = get("nonce?timeToLive=30",null)
    override suspend fun serverInformation(authorization: String) = get("server-information",authorization)
    private suspend fun get(path: String, authorization: String?): String = withContext(Dispatchers.IO) {
        val connection=URL("https://tp.tax.gov.ir/requestsmanager/api/v2/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="GET";connection.instanceFollowRedirects=false;connection.useCaches=false
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/json")
            if(authorization!=null){require(authorization.none{it=='\r'||it=='\n'});connection.setRequestProperty("Authorization","Bearer $authorization")}
            val status=connection.responseCode
            require(status in 200..299){"پاسخ اتصال سامانه مؤدیان ناموفق بود (کد $status)."}
            connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true){val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count);require(output.size()<=1_000_000){"پاسخ سرور بیش از حد بزرگ است."}}
                output.toString("UTF-8")
            }
        }finally{connection.disconnect()}
    }
}
class MoadianConnectionProbe(
    private val credentials: TaxpayerCredentialRepository,
    private val transport: MoadianPublicTransport,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Authenticates a metadata read only. Never means invoice acceptance or fiscal-memory approval. */
    suspend fun check(companyId: Long) {
        val profile=credentials.profile(companyId,clock()) ?: error("کلید و گواهی این مؤدی ثبت نشده است.")
        val raw=JSONObject(transport.nonce())
        val nonce=TaxpayerNonce(profile.identity,raw.getString("nonce"),Instant.parse(raw.getString("expDate")).toEpochMilli())
        val token=MoadianV2Signer(credentials).authorization(companyId,nonce,clock())
        val information=JSONObject(transport.serverInformation(token))
        require(information.getLong("serverTime")>0 && information.getJSONArray("publicKeys").length()>0){"اطلاعات معتبر از سرور دریافت نشد."}
        // No response fields are persisted as trusted encryption keys by this diagnostic.
    }
}
