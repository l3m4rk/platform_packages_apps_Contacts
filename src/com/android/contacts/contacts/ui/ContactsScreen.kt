package com.android.contacts.contacts.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.contacts.ContactsUtils
import com.android.contacts.R
import com.android.contacts.contacts.data.accounts.AccountDisplayItem
import com.android.contacts.group.GroupListItem
import kotlinx.coroutines.launch
import android.icu.text.MessageFormat
import java.util.Locale

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel(),
    onContactClick: (Uri) -> Unit,
    onCreateContact: () -> Unit,
    onCreateLabel: () -> Unit = {},
    onAddMember: (GroupListItem) -> Unit = {},
    onRenameGroup: (GroupListItem) -> Unit = {},
    onDeleteGroup: (GroupListItem) -> Unit = {},
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val allContactsTitle = stringResource(R.string.contactsList)
    val selectedGroup = uiState.groups.find { it.groupId == uiState.selectedGroupId }
    val topBarTitle = when (uiState.currentView) {
        ContactsView.ALL_CONTACTS -> allContactsTitle
        ContactsView.GROUP_VIEW -> selectedGroup?.title ?: allContactsTitle
        ContactsView.ACCOUNT_VIEW -> uiState.selectedAccount?.displayName ?: allContactsTitle
    }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.selectedGroupId) {
        groupMenuExpanded = false
        viewModel.onExitGroupEditMode()
    }

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
                    scope.launch { drawerState.close() }
                    onCreateLabel()
                },
                onAccountClick = { item ->
                    viewModel.onAccountSelected(item)
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = onSettingsClick,
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            topBar = {
                if (uiState.currentView == ContactsView.GROUP_VIEW && uiState.isGroupEditMode) {
                    BackHandler { viewModel.onExitGroupEditMode() }
                    selectedGroup?.let { group ->
                        GroupEditTopBar(
                            selectedCount = uiState.selectedContactIds.size,
                            onClose = { viewModel.onExitGroupEditMode() },
                            onRemove = { viewModel.onRemoveSelectedFromGroup(group) },
                        )
                    }
                } else if (uiState.currentView == ContactsView.GROUP_VIEW) {
                    GroupTopBar(
                        title = topBarTitle,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        actions = {
                            selectedGroup?.let { group ->
                                IconButton(onClick = { onAddMember(group) }) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = stringResource(R.string.menu_addContactsToGroup),
                                    )
                                }
                                Box {
                                    IconButton(onClick = { groupMenuExpanded = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.menu_addToGroup),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = groupMenuExpanded,
                                        onDismissRequest = { groupMenuExpanded = false },
                                    ) {
                                        val hasContacts = uiState.contacts.isNotEmpty()
                                        if (hasContacts) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_sendEmailOption)) },
                                                onClick = {
                                                    groupMenuExpanded = false
                                                    viewModel.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_sendMessageOption)) },
                                                onClick = {
                                                    groupMenuExpanded = false
                                                    viewModel.onSendToGroup(ContactsUtils.SCHEME_SMSTO)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_editGroup)) },
                                                onClick = {
                                                    groupMenuExpanded = false
                                                    viewModel.onEnterGroupEditMode()
                                                },
                                            )
                                        }
                                        if (!group.isReadOnly) {
                                            if (hasContacts) HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_renameGroup)) },
                                                onClick = {
                                                    groupMenuExpanded = false
                                                    onRenameGroup(group)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_deleteGroup)) },
                                                onClick = {
                                                    groupMenuExpanded = false
                                                    viewModel.onViewSelected(ContactsView.ALL_CONTACTS)
                                                    onDeleteGroup(group)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                } else {
                ContactsTopBar(
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    title = topBarTitle,
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
                }
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
                val topPadding = (padding.calculateTopPadding() +
                    with(density) { searchBarOffsetPx.toDp() })
                    .coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier.padding(
                        top = topPadding,
                        bottom = padding.calculateBottomPadding(),
                    ),
                ) {
                    if (uiState.currentView == ContactsView.GROUP_VIEW) {
                        selectedGroup?.let { group ->
                            GroupInfoHeader(group = group, accounts = uiState.accounts)
                        }
                    }
                    ContactsContent(
                        uiState = uiState,
                        filterTitle = topBarTitle,
                        onContactClick = onContactClick,
                        onRefresh = viewModel::onRefresh,
                        isRefreshEnabled = uiState.currentView != ContactsView.GROUP_VIEW &&
                            !uiState.isSearchActive,
                        onSelectionToggle = viewModel::onToggleContactSelection,
                        modifier = Modifier.weight(1f),
                        onAddContacts = selectedGroup?.let { group -> { onAddMember(group) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactsContent(
    uiState: ContactsUiState,
    filterTitle: String,
    onContactClick: (Uri) -> Unit,
    onRefresh: () -> Unit,
    isRefreshEnabled: Boolean = true,
    onSelectionToggle: (Long) -> Unit = {},
    modifier: Modifier,
    onAddContacts: (() -> Unit)? = null,
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
            when (uiState.currentView) {
                ContactsView.GROUP_VIEW -> {
                    Column(
                        modifier = Modifier.fillMaxSize().then(modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.emptyGroup),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onAddContacts != null) {
                            Spacer(Modifier.size(16.dp))
                            FilledTonalButton(onClick = onAddContacts) {
                                Text(stringResource(R.string.menu_addContactsToGroup))
                            }
                        }
                    }
                }
                else -> {
                    val emptyMessage = when (uiState.currentView) {
                        ContactsView.ALL_CONTACTS -> stringResource(R.string.noContacts)
                        else -> stringResource(R.string.listFoundAllContactsZero)
                    }
                    Box(Modifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
                        Text(emptyMessage)
                    }
                }
            }
        }
        else -> {
            val listState = rememberLazyListState()
            if (isRefreshEnabled) {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = modifier,
                ) {
                    ContactList(
                        contacts = uiState.contacts,
                        listState = listState,
                        onContactClick = onContactClick,
                    )
                }
            } else {
                Box(modifier = modifier) {
                    ContactList(
                        contacts = uiState.contacts,
                        listState = listState,
                        onContactClick = onContactClick,
                        selectedContactIds = uiState.selectedContactIds,
                        onSelectionToggle = if (uiState.isGroupEditMode) onSelectionToggle else null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GroupInfoHeader(
    group: GroupListItem,
    accounts: List<AccountDisplayItem>,
) {
    val context = LocalContext.current
    val accountItem = accounts.find {
        it.filter.accountName == group.accountName && it.filter.accountType == group.accountType
    }
    val headerText = remember(group.memberCount, group.accountName) {
        val pattern = context.getString(R.string.contacts_count_with_account)
        MessageFormat(pattern, Locale.getDefault())
            .format(mapOf("count" to group.memberCount, "account" to (group.accountName ?: "")))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bitmap = remember(accountItem?.icon) { accountItem?.icon?.toBitmap()?.asImageBitmap() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountBox,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = headerText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactList(
    contacts: List<ContactItem>,
    listState: LazyListState,
    onContactClick: (Uri) -> Unit,
    selectedContactIds: Set<Long> = emptySet(),
    onSelectionToggle: ((Long) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollbar(listState),
    ) {
        items(contacts, key = { it.id }) { contact ->
            ContactListItem(
                contact = contact,
                onClick = { onContactClick(contact.lookupUri) },
                isSelected = contact.id in selectedContactIds,
                onSelectionToggle = onSelectionToggle?.let { toggle -> { toggle(contact.id) } },
            )
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
