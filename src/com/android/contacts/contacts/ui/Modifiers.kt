package com.android.contacts.contacts.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


fun Modifier.scrollbar(state: LazyListState, color: Color): Modifier = this.drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return@drawWithContent

    val visibleItems = layoutInfo.visibleItemsInfo
    val firstVisible = visibleItems.firstOrNull() ?: return@drawWithContent
    val lastVisible = visibleItems.lastOrNull() ?: return@drawWithContent

    val thumbsStartFraction = firstVisible.index.toFloat() / totalItems
    val thumbsEndFraction = (lastVisible.index + 1).toFloat() / totalItems

    val thumbTop = size.height * thumbsStartFraction
    val thumbBottom = size.height * thumbsEndFraction

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - 6.dp.toPx(), thumbTop),
        size = Size(4.dp.toPx(), thumbBottom - thumbTop),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}
