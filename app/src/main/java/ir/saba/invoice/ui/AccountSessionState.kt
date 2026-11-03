package ir.saba.invoice.ui

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.saba.invoice.security.*

@Composable
internal fun rememberAuthenticatedAccount(token: String): AccountScope? {
    var account by remember(token) { mutableStateOf(runCatching { AccountSession.active(token).account }.getOrNull()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) account = runCatching { AccountSession.active(token).account }.getOrNull()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(token) { watchAccountSession(token) { account = it } }
    return account
}
