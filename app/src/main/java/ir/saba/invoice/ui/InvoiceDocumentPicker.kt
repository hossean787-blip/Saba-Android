package ir.saba.invoice.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ir.saba.invoice.data.InvoiceDocumentRef

/**
 * SAF documents stay in their original provider. Request a persistable, read-only URI grant;
 * the app does not copy/upload private bytes or require broad storage access.
 */
internal object InvoiceDocumentPicker {
    val acceptedMimeTypes: Array<String> = InvoiceDocumentRef.ALLOWED_MIME_TYPES.sorted().toTypedArray()

    /** Invoke on an IO dispatcher because the provider may stream up to six megabytes. */
    fun inspect(context: Context, uri: Uri): InvoiceDocumentRef {
        require(uri.scheme == "content") { "تنها اسناد انتخاب‌شده از مدیریت فایل دستگاه مجازند." }
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase().orEmpty()
        require(mime in InvoiceDocumentRef.ALLOWED_MIME_TYPES) { "نوع این فایل پشتیبانی نمی‌شود." }
        var name = ""
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty()
        }
        if (name.isBlank()) name = "سند"
        name = name.filterNot { it.isISOControl() }.trim().take(140)
        var count = 0L
        val buffer = ByteArray(8192)
        (resolver.openInputStream(uri) ?: throw IllegalArgumentException("فایل برای خواندن باز نشد.")).use { input ->
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                count += bytesRead
                require(count <= InvoiceDocumentRef.MAX_BYTES) {
                    "حجم هر سند باید حداکثر ۶ مگابایت باشد."
                }
            }
        }
        return InvoiceDocumentRef(uri.toString(), name, mime, count).validated()
    }

    fun open(context: Context, ref: InvoiceDocumentRef) {
        val safe = ref.validated()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(safe.uri), safe.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "نمایش سند پیوست"))
    }
}
