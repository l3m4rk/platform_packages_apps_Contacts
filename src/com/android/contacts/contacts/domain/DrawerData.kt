package com.android.contacts.contacts.domain

import com.android.contacts.contacts.data.accounts.AccountDisplayItem
import com.android.contacts.group.GroupListItem

data class DrawerData(
    val groups: List<GroupListItem>,
    val accounts: List<AccountDisplayItem>,
    val hasGroupWritableAccounts: Boolean,
)
