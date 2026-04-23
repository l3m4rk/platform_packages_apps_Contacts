package com.android.contacts.contacts.ui

import android.net.Uri

data class ContactItem(
    val id: Long,
    val displayName: String,
    val lookupUri: Uri,
    val photoUri: Uri?,
    val snippet: String? = null,
)

internal fun nameToColorIndex(name: String, count: Int): Int =
    ((name.fold(0) { acc, c -> acc * 31 + c.code } % count) + count) % count

internal val testContacts = listOf(
    ". some contact 1",
    ". some contact 2",
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
