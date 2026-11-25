package ir.saba.invoice.ui

import ir.saba.invoice.data.ArchiveCreditSnapshot
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.AccountSession

/** Read-only, no restore, transfer, consume or grant route. Credit remains exclusively server-owned. */
internal object AccountArchiveCreditClient {
    suspend fun read(account:AccountScope,token:String,ids:Set<Long>):ArchiveCreditSnapshot {
        AccountSession.requireOwner(token,account)
        require(ids.size<=2000) { "تعداد مؤدیان برای استعلام پشتیبان بیش از حد مجاز است." }
        val balances=linkedMapOf<Long,Long>()
        var owner:Long?=null
        for(id in ids.sorted().ifEmpty { listOf(1L) }) {
            val response=CreditTransferClient.request(token,id)
            val current=CreditServerResponse.balance(response,"ownerBalance")
            // A concurrent change during the read is not a coherent snapshot. Retry the whole operation.
            require(owner==null || owner==current) { "موجودی حین استعلام تغییر کرد؛ دوباره تلاش کنید." }
            owner=current
            val target=CreditServerResponse.balance(response,"taxpayerBalance")
            if(id in ids) balances[id]=target
        }
        val gift=FreeCreditClient.remaining(token).toLong()
        AccountSession.requireOwner(token,account)
        return ArchiveCreditSnapshot(requireNotNull(owner),gift,balances,System.currentTimeMillis())
    }
}
