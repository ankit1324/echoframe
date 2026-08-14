package com.nothingai.capture.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.nothingai.capture.R
import com.nothingai.capture.data.CaptureAction
import java.text.SimpleDateFormat
import java.util.Locale

fun launchCaptureAction(context: Context, action: CaptureAction): Boolean = runCatching {
    if (action is CaptureAction.Amount) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Amount", action.value))
        return true
    }
    val intent = when (action) {
        is CaptureAction.DateTime -> Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(
                CalendarContract.Events.TITLE,
                context.getString(R.string.calendar_event_title, context.getString(R.string.app_name)),
            )
            parseDateTime(action)?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        }
        is CaptureAction.Phone -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(action.value)}"))
        is CaptureAction.Link -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is CaptureAction.Address -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.value)}"))
        is CaptureAction.Amount -> error("Amount handled above")
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(context::startActivity)
    true
}.getOrDefault(false)

private fun parseDateTime(action: CaptureAction.DateTime): Long? {
    val value = listOfNotNull(action.date, action.time).joinToString(" ")
    val formats = if (action.time == null) {
        listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    } else {
        listOf("dd/MM/yyyy h:mm a", "dd-MM-yyyy h:mm a", "yyyy-MM-dd h:mm a", "dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm")
    }
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(value)?.time }.getOrNull()
    }
}
