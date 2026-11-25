package ir.saba.invoice.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** A tap opens the system dialer with the configured number. It never places a call. */
internal object SuraContact {
    const val PHONE = "09364382859"
    fun dialIntent(): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", PHONE, null))
    fun openDialer(context: Context): Boolean = try {
        val intent = dialIntent()
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
