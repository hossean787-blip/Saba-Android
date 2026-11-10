package ir.saba.invoice.data

import ir.saba.invoice.domain.JalaliCalendar
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class InvoiceSendScheduleTest {
    private val schedule = InvoiceLocalDetails(
        sendLaterDate = "۱۴۰۵/۰۸/۲۵", sendLaterTime = "۱۴:۳۰"
    )
    private val expected = JalaliCalendar.toGregorian(1405, 8, 25)
        .atTime(14, 30).atZone(ZoneId.of("Asia/Tehran")).toInstant()

    @Test fun persianDigitsAndTehranZoneAreIndependentOfPhoneZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(expected, InvoiceSendSchedule.dueAt(schedule))
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
            assertEquals(expected, InvoiceSendSchedule.dueAt(schedule))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun futureSchedulesBlockEarlyManualSendAndAllowItExactlyAtDueTime() {
        assertFalse(InvoiceSendSchedule.isDue(schedule, expected.minusMillis(1)))
        assertTrue(InvoiceSendSchedule.isDue(schedule, expected))
        assertTrue(InvoiceSendSchedule.isDue(schedule, expected.plusSeconds(60)))
        try {
            InvoiceSendSchedule.requireDueForManualSubmission(schedule, expected.minusMillis(1))
            fail("Premature submission must be rejected")
        } catch (_: IllegalArgumentException) {}
        InvoiceSendSchedule.requireDueForManualSubmission(schedule, expected)
        InvoiceSendSchedule.requireFutureOnSave(schedule, expected.minusSeconds(1))
        try {
            InvoiceSendSchedule.requireFutureOnSave(schedule, expected)
            fail("New schedule is no longer future")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun absenceOfScheduleDoesNotBlockAnyExistingManualSend() {
        assertNull(InvoiceSendSchedule.dueAt(InvoiceLocalDetails()))
        assertTrue(InvoiceSendSchedule.isDue(InvoiceLocalDetails(), Instant.EPOCH))
        InvoiceSendSchedule.requireDueForManualSubmission(InvoiceLocalDetails(), Instant.EPOCH)
    }

    @Test fun incompleteOrInvalidTimesAreRejectedAndNothingIsSent() {
        for (invalid in listOf(
            InvoiceLocalDetails(sendLaterDate = "۱۴۰۵/۰۸/۲۵"),
            InvoiceLocalDetails(sendLaterTime = "۱۲:۰۰"),
            InvoiceLocalDetails(sendLaterDate = "۱۴۰۵/۰۸/۲۵", sendLaterTime = "25:00"),
            InvoiceLocalDetails(sendLaterDate = "۱۴۰۵/۱۳/۲۵", sendLaterTime = "12:00")
        )) {
            try {
                InvoiceSendSchedule.dueAt(invalid)
                fail("Invalid date or hour must be rejected")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
