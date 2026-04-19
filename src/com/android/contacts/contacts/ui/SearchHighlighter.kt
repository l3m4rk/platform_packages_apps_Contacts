package com.android.contacts.contacts.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Walks code points to find the char index in [name] that corresponds to the end of the match
 * for [query] starting at [matchStart]. Mirrors SearchUtil.contains() traversal so surrogate
 * pairs are counted correctly.
 */
internal fun matchEndIndex(name: String, matchStart: Int, query: String): Int {
    var nameIdx = matchStart
    var queryIdx = 0
    while (queryIdx < query.length && nameIdx < name.length) {
        val qcp = Character.codePointAt(query, queryIdx)
        nameIdx += Character.charCount(Character.codePointAt(name, nameIdx))
        queryIdx += Character.charCount(qcp)
    }
    return nameIdx
}

/**
 * Parses a provider snippet string with `[match]` delimiters into a highlighted AnnotatedString.
 * Example input: "+380 [50] 974 6758" → "+380 **50** 974 6758" (bold+colored)
 */
internal fun parseSnippet(raw: String, highlightColor: Color) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '[' -> {
                val end = raw.indexOf(']', i)
                if (end == -1) {
                    append(raw.substring(i)); break
                }
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(raw.substring(i + 1, end))
                }
                i = end + 1
            }
            ']' -> i++
            else -> {
                val nextOpen = raw.indexOf('[', i).takeIf { it != -1 } ?: raw.length
                val nextClose = raw.indexOf(']', i).takeIf { it != -1 } ?: raw.length
                val next = minOf(nextOpen, nextClose)
                append(raw.substring(i, next))
                i = next
            }
        }
    }
}
