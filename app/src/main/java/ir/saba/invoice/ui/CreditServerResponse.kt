package ir.saba.invoice.ui

import org.json.JSONObject

/** The custom server contract returns JSON integers and explicit boolean consumption evidence. */
internal object CreditServerResponse {
    const val MAX_SAFE_CREDITS = 9_007_199_254_740_991L
    fun balance(response: JSONObject, name: String, max: Long = MAX_SAFE_CREDITS): Long {
        require(!response.has("error")) { "سرور عملیات اعتبار را تأیید نکرد." }
        val raw = response.get(name)
        require(raw is Int || raw is Long) { "ماندهٔ معتبر عدد صحیح از سرور دریافت نشد." }
        return (raw as Number).toLong().also { require(it in 0..max) { "ماندهٔ اعتبار خارج از محدوده معتبر است." } }
    }
    fun consumed(response: JSONObject, max: Long = MAX_SAFE_CREDITS): Long {
        fun flag(name: String): Boolean {
            if (!response.has(name)) return false
            val value = response.get(name)
            require(value is Boolean) { "پاسخ مصرف اعتبار معتبر نیست." }
            return value
        }
        require(flag("consumed") xor flag("already_consumed")) { "مصرف اعتبار در سرور تأیید نشد." }
        return balance(response,"remaining",max)
    }
    fun transferReceipt(response: JSONObject, requestId: String, amount: Long): Boolean {
        if (response.opt("status") != "transferred" || response.has("error")) return false
        val receipt = response.optJSONObject("receipt") ?: return false
        return receipt.opt("id") == requestId && runCatching { balance(receipt,"credits",100_000_000L) == amount }.getOrDefault(false)
    }
}
