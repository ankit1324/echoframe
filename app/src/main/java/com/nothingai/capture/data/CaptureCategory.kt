package com.nothingai.capture.data

enum class CaptureCategory(val label: String) {
    RECEIPT("Receipts"),
    SHOPPING("Shopping"),
    WORK("Work"),
    TRAVEL("Travel"),
    IDEA("Ideas"),
    EVENT("Events"),
    OTHER("Other"),
}

fun categoryFor(text: String, labels: String): CaptureCategory {
    val value = "$text $labels".lowercase()
    return when {
        value.hasAny("receipt", "invoice", "total", "paid", "subtotal", "tax") -> CaptureCategory.RECEIPT
        value.hasAny("cart", "buy", "price", "product", "shop", "sale", "order") -> CaptureCategory.SHOPPING
        value.hasAny("flight", "airport", "hotel", "boarding", "travel", "trip", "train") -> CaptureCategory.TRAVEL
        value.hasAny("meeting", "agenda", "project", "work", "deadline", "office", "document") -> CaptureCategory.WORK
        value.hasAny("birthday", "party", "event", "concert", "appointment", "saturday", "sunday") -> CaptureCategory.EVENT
        value.hasAny("idea", "brainstorm", "build", "plan", "remember") -> CaptureCategory.IDEA
        else -> CaptureCategory.OTHER
    }
}

private fun String.hasAny(vararg terms: String): Boolean = terms.any(::contains)
