package com.android.contacts.contacts.domain

import com.android.contacts.list.ContactListFilter

sealed class ContactsFilter {
    data object AllContacts : ContactsFilter()
    data class ByGroup(val groupId: Long) : ContactsFilter()
    data class ByAccount(val filter: ContactListFilter) : ContactsFilter()
}
