package ir.saba.invoice.data
data class InvoiceLine(val description:String, val quantity:Long, val unitPriceRial:Long, val discountRial:Long=0, val taxRatePercent:Int)
