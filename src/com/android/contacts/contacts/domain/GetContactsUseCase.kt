package com.android.contacts.contacts.domain

import com.android.contacts.contacts.data.ContactsRepository
import com.android.contacts.contacts.ui.ContactItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContactsUseCase @Inject constructor(
    private val repository: ContactsRepository
) {
    operator fun invoke(
        query: String = "",
        filter: ContactsFilter = ContactsFilter.AllContacts,
    ): Flow<List<ContactItem>> = repository.getContacts(query, filter)
}
