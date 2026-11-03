package ir.saba.invoice.data

import androidx.room.*

/** A confirmed record is retained; only its active slot is released for another transfer. */
@Entity(tableName="credit_transfer_claims",foreignKeys=[
    ForeignKey(entity=Company::class,parentColumns=["id"],childColumns=["companyId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("companyId"),Index(value=["activeCompanyId"],unique=true)])
data class CreditTransferClaim(
    @PrimaryKey val requestId:String,
    val ownerId:String,
    val companyId:Long,
    val amount:Long,
    val confirmed:Boolean=false,
    val activeCompanyId:Long?=companyId,
    val receiptJson:String?=null
) {
    init {
        require(amount in 1..100_000_000L)
        require(if(confirmed) activeCompanyId==null && !receiptJson.isNullOrBlank() else activeCompanyId==companyId && receiptJson==null)
    }
}
@Dao interface CreditTransferClaimDao {
    @Insert suspend fun insert(row:CreditTransferClaim)
    @Query("SELECT * FROM credit_transfer_claims WHERE requestId=:id") suspend fun byId(id:String):CreditTransferClaim?
    @Query("SELECT * FROM credit_transfer_claims WHERE activeCompanyId=:id") suspend fun pending(id:Long):CreditTransferClaim?
    @Query("SELECT * FROM credit_transfer_claims WHERE companyId=:id") suspend fun forCompany(id:Long):List<CreditTransferClaim>
    @Query("UPDATE credit_transfer_claims SET confirmed=1, activeCompanyId=NULL, receiptJson=:receipt WHERE requestId=:id AND confirmed=0")
    suspend fun confirm(id:String,receipt:String):Int
}
