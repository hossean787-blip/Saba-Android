package ir.saba.invoice.ui

import androidx.room.withTransaction
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import org.json.JSONObject
import java.util.UUID

internal data class PendingCreditTransfer(val requestId: String, val amount: Long)

/** No cached preferences: commit/rollback and the active slot are enforced by SQLite. */
internal class CreditTransferJournal(private val db:SabaDatabase, private val account:AccountScope, private val taxpayerId:Long) {
    init { require(taxpayerId in 1..CreditServerResponse.MAX_SAFE_CREDITS) }
    private suspend fun requireOwner() { require(db.companyDao().byId(taxpayerId)?.purchaserId==account.ownerId) }
    private fun checked(row:CreditTransferClaim):PendingCreditTransfer {
        require(row.ownerId==account.ownerId && row.companyId==taxpayerId)
        return PendingCreditTransfer(row.requestId,row.amount)
    }
    suspend fun pending():PendingCreditTransfer?=db.withTransaction {
        requireOwner()
        db.creditTransferClaimDao().pending(taxpayerId)?.let(::checked)
    }
    suspend fun begin(amount:Long,restoredRequestId:String?=null):PendingCreditTransfer=db.withTransaction {
        requireOwner()
        val pending=db.creditTransferClaimDao().pending(taxpayerId)
        if(pending!=null) {
            require(pending.amount==amount){"مقدار درخواست ناتمام قابل تغییر نیست."}
            return@withTransaction checked(pending)
        }
        require(amount in 1..100_000_000L)
        val id=restoredRequestId ?: UUID.randomUUID().toString()
        val uuid=UUID.fromString(id)
        require(uuid.toString()==id && uuid.version() in 1..8 && uuid.variant()==2)
        db.creditTransferClaimDao().byId(id)?.let {
            require(it.amount==amount)
            return@withTransaction checked(it) // SavedState may still hold a confirmed original request.
        }
        CreditTransferClaim(id,account.ownerId,taxpayerId,amount).also{db.creditTransferClaimDao().insert(it)}.let(::checked)
    }
    suspend fun complete(expected:PendingCreditTransfer,receipt:JSONObject):Boolean=db.withTransaction {
        requireOwner()
        val row=requireNotNull(db.creditTransferClaimDao().byId(expected.requestId))
        require(checked(row)==expected)
        if(!CreditServerResponse.transferReceipt(receipt,expected.requestId,expected.amount)) return@withTransaction false
        if(!row.confirmed) check(db.creditTransferClaimDao().confirm(row.requestId,receipt.toString())==1)
        true
    }
}
