package com.android.contacts.contacts.data.accounts

import android.graphics.drawable.Drawable
import com.android.contacts.list.ContactListFilter

data class AccountDisplayItem(
    val filter: ContactListFilter,
    val displayName: String,
    val icon: Drawable?,
)
