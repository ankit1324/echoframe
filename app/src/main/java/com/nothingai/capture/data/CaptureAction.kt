package com.nothingai.capture.data

sealed interface CaptureAction {
    data class Amount(val value: String) : CaptureAction
    data class DateTime(val date: String, val time: String?) : CaptureAction
    data class Phone(val value: String) : CaptureAction
    data class Link(val url: String) : CaptureAction
    data class Address(val value: String) : CaptureAction
}

fun extractCaptureActions(text: String): List<CaptureAction> {
    val actions = mutableListOf<CaptureAction>()
    amountRegex.find(text)?.value?.let { actions += CaptureAction.Amount(it.trim()) }

    dateRegex.find(text)?.value?.let { date ->
        actions += CaptureAction.DateTime(date, timeRegex.find(text)?.value)
    }

    phoneRegex.find(text)?.value?.trim()?.takeIf { it.count(Char::isDigit) in 10..15 }?.let {
        actions += CaptureAction.Phone(it)
    }

    val links = linkRegex.findAll(text).map { match ->
        val raw = match.value.trimEnd('.', ',', ')', ']')
        if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }.distinctBy { it.removePrefix("https://").removePrefix("http://").removeSuffix("/") }
    links.firstOrNull()?.let { actions += CaptureAction.Link(it) }

    addressRegex.find(text)?.value?.trim()?.trimEnd('.', ',', ';')?.let { actions += CaptureAction.Address(it) }
    return actions
}

private val amountRegex = Regex("(?:₹|Rs\\.?|INR|\\$|€|£)\\s?\\d[\\d,]*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
private val dateRegex = Regex("\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})\\b")
private val timeRegex = Regex("\\b\\d{1,2}:\\d{2}\\s?(?:AM|PM)?\\b", RegexOption.IGNORE_CASE)
private val phoneRegex = Regex("(?:\\+\\d{1,3}[ -]?)?(?:\\d[ -]?){10,15}")
private val linkRegex = Regex("(?:https?://)?(?:www\\.)?[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z]{2,})+(?:/[^\\s]*)?", RegexOption.IGNORE_CASE)
private val addressRegex = Regex("\\b\\d{1,5}\\s+[A-Za-z][A-Za-z0-9 .'-]{2,}(?:Road|Rd|Street|St|Avenue|Ave|Lane|Ln|Nagar|Marg),?\\s*[A-Za-z .'-]{2,}", RegexOption.IGNORE_CASE)
