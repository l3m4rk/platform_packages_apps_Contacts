package com.android.contacts.contacts.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import androidx.core.net.toUri
import com.android.contacts.contacts.ui.ContactItem
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.list.ContactListAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

interface ContactsRepository {
    fun getContacts(query: String = ""): Flow<List<ContactItem>>
}

class ContactsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ContactsRepository {
    override fun getContacts(query: String): Flow<List<ContactItem>> = flow {
        val selection = if (query.isNotBlank())
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?" else null
        val selectionArgs = if (query.isNotBlank()) arrayOf("%$query%") else null

        val contacts = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            ContactListAdapter.ContactQuery.CONTACT_PROJECTION_PRIMARY,
            selection,
            selectionArgs,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            Log.d("ContactsRepo", "cursor count: ${cursor.count}")
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)                      // _ID
                    val name = cursor.getString(1) ?: continue      // DISPLAY_NAME_PRIMARY
                    val lookupKey = cursor.getString(6) ?: continue // LOOKUP_KEY
                    val photoUri = cursor.getString(5)?.toUri()     // PHOTO_THUMBNAIL_URI
                    // TODO: maybe borrow model from the old code
                    add(ContactItem(
                        id = id,
                        displayName = name,
                        lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey),
                        photoUri = photoUri
                    ))
                }
            }
        } ?: emptyList()

        emit(contacts)
    }.flowOn(ioDispatcher)
}
