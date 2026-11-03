package ir.saba.invoice.ui

/** Credit authorization boundary. Production always delegates to the authenticated server. */
interface InvoiceCreditGateway {
    suspend fun paidBalance(token: String, companyId: Long): Long
    suspend fun giftBalance(token: String): Int
    suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String): Long
    suspend fun consumeGift(token: String, invoiceKey: String): Int
}

internal class ServerInvoiceCreditGateway(private val account: ir.saba.invoice.security.AccountScope) : InvoiceCreditGateway {
    private fun requireSession(token: String) = ir.saba.invoice.security.AccountSession.requireOwner(token,account)
    override suspend fun paidBalance(token: String, companyId: Long): Long =
        run { requireSession(token); CreditServerResponse.balance(CreditTransferClient.request(token,companyId),"taxpayerBalance") }

    override suspend fun giftBalance(token: String): Int =
        run { requireSession(token); FreeCreditClient.remaining(token) }

    override suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String): Long =
        run { requireSession(token); CreditServerResponse.consumed(CreditTransferClient.consume(token,companyId,invoiceKey)) }

    override suspend fun consumeGift(token: String, invoiceKey: String): Int =
        run { requireSession(token); FreeCreditClient.consume(token,invoiceKey) }
}
