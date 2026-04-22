package com.android.contacts.contacts.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.net.toUri
import com.android.contacts.contacts.domain.ContactsFilter
import com.android.contacts.contacts.ui.ContactItem
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.list.ContactListAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface ContactsRepository {
    fun getContacts(
        query: String = "",
        filter: ContactsFilter = ContactsFilter.AllContacts,
    ): Flow<List<ContactItem>>
}

class ContactsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContactsRepository {

    override fun getContacts(query: String, filter: ContactsFilter): Flow<List<ContactItem>> {
        // Group membership is stored in Data, so observe that URI for group views.
        // All other views observe the Contacts URI.
        val observeUri = if (query.isBlank() && filter is ContactsFilter.ByGroup)
            ContactsContract.Data.CONTENT_URI
        else
            ContactsContract.Contacts.CONTENT_URI

        return observeContentUri(observeUri)
            .flatMapLatest { queryContacts(query, filter) }
            .flowOn(ioDispatcher)
    }

    private fun observeContentUri(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun queryContacts(query: String, filter: ContactsFilter): Flow<List<ContactItem>> = flow {
        val isSearch = query.isNotBlank()

        // Group filtering needs a two-step query; skip when in search mode
        if (!isSearch && filter is ContactsFilter.ByGroup) {
            emit(getGroupContacts(filter.groupId))
            return@flow
        }

        val uri = if (isSearch) {
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        } else if (filter is ContactsFilter.ByAccount) {
            ContactsContract.Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, filter.filter.accountName)
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, filter.filter.accountType)
                .build()
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
                    val id = cursor.getLong(ID_COLUMN_INDEX)
                    val name = cursor.getString(DISPLAY_NAME_PRIMARY_COLUMN_INDEX) ?: continue
                    val lookupKey = cursor.getString(LOOKUP_KEY_COLUMN_INDEX) ?: continue
                    val photoUri = cursor.getString(PHOTO_THUMBNAIL_URI_COLUMN_INDEX)?.toUri()
                    val snippet = if (isSearch) cursor.getString(SEARCH_SNIPPET_COLUMN_INDEX) else null
                    add(
                        ContactItem(
                            id = id,
                            displayName = name,
                            lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey),
                            photoUri = photoUri,
                            snippet = snippet,
                        )
                    )
                }
            }
        } ?: emptyList()

        emit(contacts)
    }

    private fun getGroupContacts(groupId: Long): List<ContactItem> {
        val contactIds: Set<Long> = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
            null,
        )?.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        } ?: return emptyList()

        if (contactIds.isEmpty()) return emptyList()

        val selection = "${ContactsContract.Contacts._ID} IN (${contactIds.joinToString(",")})"
        return context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            ContactListAdapter.ContactQuery.CONTACT_PROJECTION_PRIMARY,
            selection,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(ID_COLUMN_INDEX)
                    val name = cursor.getString(DISPLAY_NAME_PRIMARY_COLUMN_INDEX) ?: continue
                    val lookupKey = cursor.getString(LOOKUP_KEY_COLUMN_INDEX) ?: continue
                    val photoUri = cursor.getString(PHOTO_THUMBNAIL_URI_COLUMN_INDEX)?.toUri()
                    add(
                        ContactItem(
                            id = id,
                            displayName = name,
                            lookupUri = ContactsContract.Contacts.getLookupUri(id, lookupKey),
                            photoUri = photoUri,
                            snippet = null,
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    companion object {
        private const val ID_COLUMN_INDEX = 0
        private const val DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1
        private const val LOOKUP_KEY_COLUMN_INDEX = 6
        private const val PHOTO_THUMBNAIL_URI_COLUMN_INDEX = 5
        private const val SEARCH_SNIPPET_COLUMN_INDEX = 9

        // Same as CONTACT_PROJECTION_PRIMARY + SearchSnippets.SNIPPET at index 9
        private val FILTER_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID, // 0
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, // 1
            ContactsContract.Contacts.CONTACT_PRESENCE, // 2
            ContactsContract.Contacts.CONTACT_STATUS, // 3
            ContactsContract.Contacts.PHOTO_ID, // 4
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, // 5
            ContactsContract.Contacts.LOOKUP_KEY, // 6
            ContactsContract.Contacts.PHONETIC_NAME, // 7
            ContactsContract.Contacts.STARRED, // 8
            ContactsContract.SearchSnippets.SNIPPET, // 9
        )
    }
}
