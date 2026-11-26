package ir.saba.invoice.ui

import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuraNoticeHostTest {
    private class Notice : SnackbarData {
        var dismissed = false
        override val visuals = object : SnackbarVisuals {
            override val message = "پیام نمونه"
            override val actionLabel = "بستن"
            override val withDismissAction = false
            override val duration = SnackbarDuration.Indefinite
        }
        override fun performAction() { dismissed = true }
        override fun dismiss() { dismissed = true }
    }
    @Test fun noticeDismissesAtFiveSecondsNotEarlier() = runTest {
        val notice = Notice()
        launch { dismissSuraNoticeAfterTimeout(notice) }
        runCurrent()
        advanceTimeBy(4999)
        runCurrent()
        assertFalse(notice.dismissed)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(notice.dismissed)
    }

    @Test fun oldTimerDoesNotDismissNewNoticeAfterManualClose() = runTest {
        val first = Notice()
        val timer = launch { dismissSuraNoticeAfterTimeout(first) }
        runCurrent()
        advanceTimeBy(1000)
        first.performAction()
        timer.cancel()
        val next = Notice()
        launch { dismissSuraNoticeAfterTimeout(next) }
        runCurrent()
        advanceTimeBy(4000)
        runCurrent()
        assertFalse(next.dismissed)
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(next.dismissed)
    }
}
