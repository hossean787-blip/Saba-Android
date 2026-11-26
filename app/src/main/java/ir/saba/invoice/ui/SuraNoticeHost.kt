package ir.saba.invoice.ui

import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

internal const val SURA_NOTICE_DURATION_MS = 5_000L

internal suspend fun dismissSuraNoticeAfterTimeout(data: SnackbarData) {
    delay(SURA_NOTICE_DURATION_MS)
    data.dismiss()
}

/** Each visible message receives its own five seconds; manual dismissal cancels its timer.
 * Only transient snackbar notices use this host. Confirmation dialogs and inline errors are unchanged.
 */
@Composable
internal fun SuraNoticeHost(state: SnackbarHostState) {
    val current = state.currentSnackbarData
    LaunchedEffect(current) {
        if (current != null) dismissSuraNoticeAfterTimeout(current)
    }
    SnackbarHost(hostState = state)
}
