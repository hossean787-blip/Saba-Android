package ir.saba.invoice.security

import ir.saba.invoice.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MoadianInvoiceMapperTest {
    private val company=Company(id=1,purchaserId="owner",name="فروشنده",nationalId="1",economicId="12345678901")
    private val buyer=Customer(id=2,companyId=1,name="خریدار",nationalId="12345678901",economicId="12345678901")
    private val details=MoadianDetails(companyId=1,fiscalMemoryId="A11216")
    private val invoice=Invoice(id=3,companyId=1,customerId=2,invoiceNumber="12",issueDate="1405/08/12",totalAmountRial=2200,taxAmountRial=200,status="FINALIZED")
    private val lines=listOf(InvoiceLine(invoiceId=3,itemCode="2710000138624",description="کالا",quantity=2,unitPriceRial=1000,taxRatePercent=10))
    private val now=Instant.parse("2026-11-03T16:00:00Z").toEpochMilli()
    private fun map(i:Invoice=invoice,l:List<InvoiceLine> = lines,time:String="2026-11-03T12:00:00+03:30",unit:String="1627") =
        MoadianInvoiceMapper.map(company,buyer,null,details,i,l,time,unit,now)
    private fun rejected(block:()->Unit){try{block();fail("Expected invalid tax invoice")}catch(_:IllegalArgumentException){}}
    @Test fun explicitCashInvoiceMapsNumbersAndTotalsWithoutFloatingPoint() {
        val root=JSONObject(map());val h=root.getJSONObject("header");val b=root.getJSONArray("body").getJSONObject(0)
        assertEquals(1,h.getInt("setm"));assertEquals(2000L,h.getLong("cap"));assertFalse(h.has("insp"))
        assertEquals("000000000C",h.getString("inno"));assertEquals(22,h.getString("taxid").length)
        assertEquals(2,h.getInt("tob"));assertEquals("12345678901",h.getString("tinb"))
        assertEquals(2000L,b.getLong("prdis"));assertEquals(200L,b.getLong("vam"));assertEquals("1627",b.getString("mu"))
        assertEquals(0,root.getJSONArray("payments").length())
        assertEquals(2200L,b.getLong("cop"));assertEquals(200L,b.getLong("vop"));assertEquals(200L,h.getLong("tvop"))
    }
    @Test fun v79OptionalNotesAreMappedAndCannotExceedThirtyCharacters() {
        val valid = invoice.copy(localDetailsJson = InvoiceLocalDetails(
            note1 = "توضیح کوتاه اول", note2 = "توضیح کوتاه دوم").toJson())
        val header = JSONObject(map(valid)).getJSONObject("header")
        assertEquals("توضیح کوتاه اول", header.getString("nti1"))
        assertEquals("توضیح کوتاه دوم", header.getString("nti2"))
        val oversized = invoice.copy(localDetailsJson = InvoiceLocalDetails(
            note1 = "a".repeat(31)).toJson())
        rejected { map(oversized) }
        val article9 = invoice.copy(localDetailsJson = InvoiceLocalDetails(article9Flag = true).toJson())
        rejected { map(article9) }
        assertFalse(JSONObject(map()).getJSONObject("header").has("nti1"))
        assertFalse(JSONObject(map()).getJSONObject("header").has("nti2"))
    }

    @Test fun knownJalaliDatesIncludeLeapBoundary() {
        assertTrue(MoadianInvoiceMapper.defaultIssueTime("1404/01/01").startsWith("2025-03-21T12:00"))
        assertTrue(MoadianInvoiceMapper.defaultIssueTime("1403/12/30").startsWith("2025-03-20T12:00"))
        assertTrue(MoadianInvoiceMapper.defaultIssueTime("1405/08/12").startsWith("2026-11-03T12:00"))
        assertTrue(MoadianInvoiceMapper.defaultIssueTime("1399/01/01").startsWith("2020-03-20T12:00"))
    }
    @Test fun unsupportedOrInconsistentInputsCannotBeSubmitted() {
        rejected{map(invoice.copy(status="DRAFT"))};rejected{map(invoice.copy(companyId=99))}
        rejected{map(unit="unknown")};rejected{map(time="2026-11-04T12:00:00+03:30")}
        rejected{map(invoice.copy(totalAmountRial=2201))};rejected{map(l=lines.map{it.copy(itemCode="")})}
        rejected{map(invoice.copy(invoiceNumber="not-a-fiscal-serial"))}
    }
    @Test fun localRoundedVatCannotSilentlyReplaceRequiredTaxTruncation() {
        rejected{map(invoice.copy(totalAmountRial=116,taxAmountRial=11),lines.map{it.copy(quantity=1,unitPriceRial=105)})}
    }
    @Test fun buyerEconomicIdentityMustMatchPersonType() {
        val natural=buyer.copy(nationalId="1234567890",economicId="12345678901234")
        val payload=MoadianInvoiceMapper.map(company,natural,CustomerProfile(customerId=2,companyId=1),details,invoice,lines,"2026-11-03T12:00:00+03:30","1627",now)
        assertEquals(1,JSONObject(payload).getJSONObject("header").getInt("tob"))
        rejected{MoadianInvoiceMapper.map(company,natural.copy(economicId="12345678901"),CustomerProfile(customerId=2,companyId=1),details,invoice,lines,"2026-11-03T12:00:00+03:30","1627",now)}
        rejected{MoadianInvoiceMapper.map(company.copy(economicId="123456789012"),buyer,null,details,invoice,lines,"2026-11-03T12:00:00+03:30","1627",now)}
    }

    @Test fun fullCashSharesFollowV79Tables25And50And51AfterDiscounts() {
        val rows=listOf(lines.single().copy(discountRial=500),lines.single().copy(quantity=1,unitPriceRial=500,discountRial=250))
        val root=JSONObject(map(invoice.copy(totalAmountRial=1925,taxAmountRial=175),rows))
        val header=root.getJSONObject("header");val body=root.getJSONArray("body")
        // C = tbill - VAT; CPP = line bill * C / total net; VPP = line VAT * C / total net.
        assertEquals(1750L,header.getLong("cap"));assertEquals(1925L,header.getLong("tbill"))
        assertEquals(1650L,body.getJSONObject(0).getLong("cop"));assertEquals(275L,body.getJSONObject(1).getLong("cop"))
        assertEquals(150L,body.getJSONObject(0).getLong("vop"));assertEquals(25L,body.getJSONObject(1).getLong("vop"))
        assertEquals(175L,header.getLong("tvop"))
    }

    @Test fun preciseQuantityVatAndDifferentItemUnitsMapToOfficialPayload() {
        val first = lines.single().copy(
            quantity = 1, unitPriceRial = 1000, taxRatePercent = 9,
            quantityExact = "0.5", vatRateExact = "9.5", unitCode = "164"
        )
        val second = lines.single().copy(
            quantity = 1, unitPriceRial = 1000, taxRatePercent = 0,
            quantityExact = "1", vatRateExact = "0", unitCode = "1627"
        )
        val payload = JSONObject(map(
            invoice.copy(totalAmountRial = 1547, taxAmountRial = 47),
            listOf(first, second), unit = ""
        ))
        val body = payload.getJSONArray("body")
        assertEquals(2, body.length())
        assertEquals("164", body.getJSONObject(0).getString("mu"))
        assertEquals("1627", body.getJSONObject(1).getString("mu"))
        assertEquals(0, java.math.BigDecimal(body.getJSONObject(0).get("am").toString()).compareTo(java.math.BigDecimal("0.5")))
        assertEquals(0, java.math.BigDecimal(body.getJSONObject(0).get("vra").toString()).compareTo(java.math.BigDecimal("9.5")))
        assertEquals(47L, body.getJSONObject(0).getLong("vam"))
        assertEquals(47L, payload.getJSONObject("header").getLong("tvam"))
    }


    @Test fun referredInvoicesCarryExactOfficialSubjectAndReference() {
        val ref = "A1121600000000000000"
        val correction = JSONObject(map(invoice.copy(
            subject = "CORRECTION", referenceTaxId = ref
        ))).getJSONObject("header")
        assertEquals(2, correction.getInt("ins"))
        assertEquals(ref, correction.getString("irtaxid"))
        val partialReturn = JSONObject(map(invoice.copy(
            subject = "RETURN", referenceTaxId = ref
        ))).getJSONObject("header")
        assertEquals(4, partialReturn.getInt("ins"))
        assertEquals(ref, partialReturn.getString("irtaxid"))
        rejected { map(invoice.copy(subject = "ORIGINAL", referenceTaxId = ref)) }
        rejected { map(invoice.copy(subject = "CORRECTION", referenceTaxId = null)) }
        rejected { map(invoice.copy(subject = "CANCELLATION", referenceTaxId = ref)) }
    }

    @Test fun creditTypeOneHasZeroCashAndNetAmountOnCredit() {
        val payload=JSONObject(map(invoice.copy(settlementMode = "CREDIT", cashPaidRial = 0L)))
        val h=payload.getJSONObject("header")
        assertEquals(2,h.getInt("setm"))
        assertEquals(0,h.getLong("cap"))
        assertEquals(2000,h.getLong("insp"))
        assertEquals(0,h.getLong("tvop"))
        assertEquals(0,payload.getJSONArray("body").getJSONObject(0).getLong("cop"))
        assertEquals(0,payload.getJSONArray("body").getJSONObject(0).getLong("vop"))
        assertEquals(2200,h.getLong("tbill"))
    }

    @Test fun mixedTypeOneApportionsNetAndVatWithoutFloatingPoint() {
        val payload=JSONObject(map(invoice.copy(settlementMode = "MIXED", cashPaidRial = 1100L)))
        val h=payload.getJSONObject("header")
        assertEquals(3,h.getInt("setm"))
        assertEquals(1000L,h.getLong("cap"))
        assertEquals(1000L,h.getLong("insp"))
        assertEquals(100L,h.getLong("tvop"))
        val line=payload.getJSONArray("body").getJSONObject(0)
        assertEquals(1100L,line.getLong("cop"))
        assertEquals(100L,line.getLong("vop"))
        assertEquals(2200L,h.getLong("tbill"))
    }

    @Test fun impossibleMixedVatSplitFailsClosedInsteadOfRoundingClientCash() {
        val shortLine = lines.single().copy(quantity = 1L, unitPriceRial = 10L,
            taxRatePercent = 10)
        rejected { map(invoice.copy(totalAmountRial = 11L, taxAmountRial = 1L,
            settlementMode = "MIXED", cashPaidRial = 10L),listOf(shortLine)) }
    }

    @Test fun multiLineRialRoundingCannotInventExtraCashAgainstHeader() {
        val rows = listOf(
            lines.single().copy(quantity = 1L, unitPriceRial = 3L, taxRatePercent = 34),
            lines.single().copy(quantity = 1L, unitPriceRial = 3L, taxRatePercent = 34)
        )
        rejected {
            map(invoice.copy(totalAmountRial = 8L, taxAmountRial = 2L,
                settlementMode = "MIXED", cashPaidRial = 4L), rows)
        }
    }

    @Test fun nonCashRequiresExplicitAndAccuratePaidAmount() {
        rejected { map(invoice.copy(settlementMode = "CREDIT", cashPaidRial = null)) }
        rejected { map(invoice.copy(settlementMode = "MIXED", cashPaidRial = 2200L)) }
        rejected { map(invoice.copy(settlementMode = "MIXED", cashPaidRial = 0L)) }
    }

}
