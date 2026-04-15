package com.android.contacts.contacts.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.contacts.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClosed: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement search
//    AnimatedContent(
//        targetState = isSearchActive,
//        transitionSpec = {
//            fadeIn(tween(200) togetherWith fadeOut())
//        },
//        modifier = modifier,
//        label = "TopBarTranstition"
//    ) { }
    TopAppBar(
        title = { Text(stringResource(R.string.contactsList)) },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.navigation_drawer_open)
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchOpen) {
                Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.searchHint))
            }
        }
    )
}
