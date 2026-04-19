package com.android.contacts.contacts.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.contacts.R
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel(),
    onContactClick: (Uri) -> Unit,
    onCreateContact: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var searchBarHeightPx by remember { mutableFloatStateOf(0f) }
    var searchBarOffsetPx by remember { mutableFloatStateOf(0f) }
    val isSearchActive by rememberUpdatedState(uiState.isSearchActive)

    LaunchedEffect(uiState.isSearchActive) {
        searchBarOffsetPx = 0f
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSearchActive) {
                    searchBarOffsetPx = (searchBarOffsetPx + available.y)
                        .coerceIn(-searchBarHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ContactsDrawerContent(
                uiState = uiState,
                onAllContactsClick = {
                    viewModel.onViewSelected(ContactsView.ALL_CONTACTS)
                    scope.launch { drawerState.close() }
                },
                onGroupClick = { group ->
                    viewModel.onGroupSelected(group)
                    scope.launch { drawerState.close() }
                },
                onCreateLabelClick = {
                    // TODO: navigate to create group
                },
                onAccountClick = { filter ->
                    viewModel.onAccountSelected(filter)
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = onSettingsClick,
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            topBar = {
                ContactsTopBar(
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onSearchOpen = viewModel::onSearchOpen,
                    onSearchClosed = viewModel::onSearchClosed,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { searchBarHeightPx = it.height.toFloat() }
                        .graphicsLayer { translationY = searchBarOffsetPx },
                    searchContent = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            HorizontalDivider()
                            if (uiState.contacts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(stringResource(R.string.listFoundAllContactsZero))
                                }
                            } else {
                                LazyColumn {
                                    items(uiState.contacts, key = { it.id }) { contact ->
                                        ContactListItem(
                                            contact = contact,
                                            onClick = { onContactClick(contact.lookupUri) },
                                            searchQuery = uiState.searchQuery,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (uiState.isFabVisible) {
                    FloatingActionButton(onClick = onCreateContact) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_menu_add_new_contact_button),
                        )
                    }
                }
            },
        ) { padding ->
            val density = LocalDensity.current
            if (!uiState.isSearchActive) {
                ContactsContent(
                    uiState = uiState,
                    onContactClick = onContactClick,
                    onRefresh = { /*later: trigger Google resync via ContentResolver*/ },
                    modifier = Modifier.padding(
                        top = (padding.calculateTopPadding() +
                            with(density) { searchBarOffsetPx.toDp() })
                            .coerceAtLeast(0.dp),
                        bottom = padding.calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun ContactsContent(
    uiState: ContactsUiState,
    onContactClick: (Uri) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    when {
        uiState.showLoadingUi() -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.contacts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.noContacts))
            }
        }
        else -> {
            val listState = rememberLazyListState()
            Box(modifier = modifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollbar(listState),
                ) {
                    items(uiState.contacts, key = { it.id }) { contact ->
                        ContactListItem(
                            contact = contact,
                            onClick = { onContactClick(contact.lookupUri) },
                        )
                    }
                }
            }
        }
    }
}

// TODO: move to ui utils
fun Modifier.scrollbar(state: LazyListState): Modifier = this.drawWithContent {
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
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(size.width - 6.dp.toPx(), thumbTop),
        size = Size(4.dp.toPx(), thumbBottom - thumbTop),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}
