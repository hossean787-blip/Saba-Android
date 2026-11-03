package ir.saba.invoice.data

import androidx.room.*

@Entity(tableName="invoice_creation_claims",foreignKeys=[
    ForeignKey(entity=Company::class,parentColumns=["id"],childColumns=["companyId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=Invoice::class,parentColumns=["id"],childColumns=["invoiceId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("companyId"),Index(value=["invoiceId"],unique=true),Index(value=["activeCompanyId"],unique=true)])
data class InvoiceCreationClaim(
    @PrimaryKey val requestId:String,
    val ownerId:String,
    val companyId:Long,
    val creditKind:String,
    val payloadJson:String,
    val invoiceId:Long?=null,
    val activeCompanyId:Long?=companyId
) {
    init { require((invoiceId==null)==(activeCompanyId!=null) && (activeCompanyId==null || activeCompanyId==companyId)) }
}
@Dao interface InvoiceCreationClaimDao {
    @Query("SELECT * FROM invoice_creation_claims WHERE companyId=:id") suspend fun forCompany(id:Long):List<InvoiceCreationClaim>
    @Insert suspend fun insert(claim:InvoiceCreationClaim)
    @Query("SELECT * FROM invoice_creation_claims WHERE requestId=:key LIMIT 1") suspend fun byKey(key:String):InvoiceCreationClaim?
    @Query("SELECT * FROM invoice_creation_claims WHERE activeCompanyId=:companyId LIMIT 1") suspend fun pending(companyId:Long):InvoiceCreationClaim?
    @Query("UPDATE invoice_creation_claims SET invoiceId=:invoiceId, activeCompanyId=NULL WHERE requestId=:key AND invoiceId IS NULL") suspend fun complete(key:String,invoiceId:Long):Int
}
