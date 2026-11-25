package ir.saba.invoice.ui

/** A checkout quote is bound to one plan and quantity; disabled plans cannot reserve orders. */
internal data class PurchaseQuote(
    val kind:String,val units:Long,val subtotal:Long,val vat:Long,val total:Long,val enabled:Boolean
) {
    fun matches(plan:String,quantity:Long?)=kind==plan && units==quantity
    companion object {
        fun validate(kind:String,units:Long,subtotal:Long,vat:Long,total:Long,enabled:Boolean):PurchaseQuote {
            require(kind=="single" && units in 1L..999999L || kind=="monthly" && units==20L)
            require(subtotal in 1L..1000000000000000L)
            require(vat==(subtotal*10+50)/100 && total==subtotal+vat)
            require(kind!="single" || subtotal%units==0L)
            return PurchaseQuote(kind,units,subtotal,vat,total,enabled)
        }
    }
}
