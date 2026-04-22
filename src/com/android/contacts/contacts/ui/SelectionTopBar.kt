@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.contacts.contacts.ui

import androidx.compose.material.icons.Icons
import android.icu.text.MessageFormat
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.contacts.R
import java.util.Locale

@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val title = remember(selectedCount) {
        MessageFormat(context.getString(R.string.contacts_count), Locale.getDefault())
            .format(mapOf("count" to selectedCount))
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel_button_content_description),
                )
            }
        },
        actions = {
            if (selectedCount >= 2) {
                IconButton(onClick = onLink) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.CallMerge,
                        contentDescription = stringResource(R.string.menu_joinAggregate),
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.menu_share),
                )
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.menu_deleteContact),
                )
            }
        },
        modifier = modifier,
    )
}
