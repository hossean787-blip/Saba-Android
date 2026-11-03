package ir.saba.invoice.ui

/** Credit authorization boundary. Production always delegates to the authenticated server. */
interface InvoiceCreditGateway {
    suspend fun paidBalance(token: String, companyId: Long): Long
    suspend fun giftBalance(token: String): Int
    suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String): Long
    suspend fun consumeGift(token: String, invoiceKey: String): Int
}

internal object ServerInvoiceCreditGateway : InvoiceCreditGateway {
    override suspend fun paidBalance(token: String, companyId: Long): Long =
        CreditTransferClient.request(token, companyId).getLong("taxpayerBalance").also {
            require(it >= 0) { "موجودی معتبر نیست." }
        }

    override suspend fun giftBalance(token: String): Int =
        FreeCreditClient.remaining(token).also { require(it >= 0) { "موجودی هدیه معتبر نیست." } }

    override suspend fun consumePaid(token: String, companyId: Long, invoiceKey: String): Long =
        CreditTransferClient.consume(token, companyId, invoiceKey).getLong("remaining").also {
            require(it >= 0) { "پاسخ مصرف اعتبار معتبر نیست." }
        }

    override suspend fun consumeGift(token: String, invoiceKey: String): Int =
        FreeCreditClient.consume(token, invoiceKey).also { require(it >= 0) { "پاسخ مصرف هدیه معتبر نیست." } }
}
