package com.android.contacts.contacts.data

import android.content.Context
import android.net.Uri
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
        val isSearch = query.isNotBlank()
        val uri = if (isSearch) {
            Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_FILTER_URI,
                Uri.encode(query),
            )
        } else {
            ContactsContract.Contacts.CONTENT_URI
        }
        val projection = if (isSearch) FILTER_PROJECTION else ContactListAdapter.ContactQuery.CONTACT_PROJECTION_PRIMARY

        val contacts = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            Log.d("ContactsRepo", "cursor count: ${cursor.count}")
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)                      // _ID
                    val name = cursor.getString(1) ?: continue      // DISPLAY_NAME_PRIMARY
                    val lookupKey = cursor.getString(6) ?: continue // LOOKUP_KEY
                    val photoUri = cursor.getString(5)?.toUri()     // PHOTO_THUMBNAIL_URI
                    val snippet = if (isSearch) cursor.getString(9) else null // SNIPPET
                    add(ContactItem(
                        id = id,
                        displayName = name,
                        lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey),
                        photoUri = photoUri,
                        snippet = snippet,
                    ))
                }
            }
        } ?: emptyList()

        emit(contacts)
    }.flowOn(ioDispatcher)

    companion object {
        // Same as CONTACT_PROJECTION_PRIMARY + SearchSnippets.SNIPPET at index 9
        private val FILTER_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,                    // 0
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,  // 1
            ContactsContract.Contacts.CONTACT_PRESENCE,      // 2
            ContactsContract.Contacts.CONTACT_STATUS,        // 3
            ContactsContract.Contacts.PHOTO_ID,              // 4
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,   // 5
            ContactsContract.Contacts.LOOKUP_KEY,            // 6
            ContactsContract.Contacts.PHONETIC_NAME,         // 7
            ContactsContract.Contacts.STARRED,               // 8
            ContactsContract.SearchSnippets.SNIPPET,         // 9
        )
    }
}
