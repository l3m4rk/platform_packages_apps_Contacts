package com.android.contacts.contacts.data.accounts

data class AccountData(
    val accounts: List<AccountDisplayItem>,
    val hasGroupWritableAccounts: Boolean,
)
