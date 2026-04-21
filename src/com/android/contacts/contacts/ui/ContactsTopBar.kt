@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.contacts.contacts.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.contacts.R

@Composable
internal fun ContactsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    title: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClosed: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchContent: @Composable ColumnScope.() -> Unit = {},
) {
    val horizontalPadding by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 16.dp,
        label = "SearchBarHorizontalPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 8.dp,
        label = "SearchBarBottomPadding",
    )
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = onSearchQueryChanged,
                onSearch = {},
                expanded = isSearchActive,
                onExpandedChange = { if (it) onSearchOpen() else onSearchClosed() },
                placeholder = {
                    Text(if (isSearchActive) stringResource(R.string.hint_findContacts) else title)
                },
                leadingIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = onSearchClosed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_arrow_content_description),
                            )
                        }
                    } else {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.navigation_drawer_open),
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (isSearchActive && searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.description_clear_search),
                            )
                        }
                    } else if (!isSearchActive) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.searchHint),
                        )
                    }
                },
            )
        },
        expanded = isSearchActive,
        onExpandedChange = { if (it) onSearchOpen() else onSearchClosed() },
        modifier = modifier.padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding),
        content = searchContent,
    )
}
