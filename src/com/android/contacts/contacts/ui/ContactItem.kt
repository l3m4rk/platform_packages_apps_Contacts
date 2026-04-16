package com.android.contacts.contacts.ui

import android.net.Uri
import androidx.compose.ui.graphics.Color

data class ContactItem(
    val id: Long,
    val displayName: String,
    val lookupUri: Uri,
    val photoUri: Uri?,
)

internal fun nameToColor(name: String): Color {
    val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
    val hue = ((hash % 360) + 360) % 360
    val hsv = floatArrayOf(hue.toFloat(), 0.6f, 0.75f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

internal val testContacts = listOf(
    // Common names
    "Alice Johnson",
    "Bob Smith",
    "Carol White",
    // Single name (no space)
    "Madonna",
    "Cher",
    // Long names
    "Alexander Bartholomew",
    "Svetlana Kovalenko",
    // Similar first letters (should still differ)
    "Anna Adams",
    "Andrew Allen",
    // Non-Latin / accented
    "José García",
    "Müller Hans",
    "Søren Nielsen",
    // Short names
    "Ed Wu",
    "Li Na",
    // Empty-ish edge case
    " ",
    "",
)
