package com.android.contacts.contacts.data.accounts

import com.android.contacts.list.ContactListFilter

data class AccountData(
    val accounts: List<ContactListFilter>,
    val hasGroupWritableAccounts: Boolean,
)
