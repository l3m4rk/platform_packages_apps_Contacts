package com.android.contacts.contacts.domain

import android.content.ContentResolver
import android.provider.ContactsContract
import com.android.contacts.ContactsUtils
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.group.GroupMembersFragment
import com.android.contacts.group.GroupUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetGroupContactDataUseCase @Inject constructor(
    private val contentResolver: ContentResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    data class Result(
        val itemList: List<String>,
        val defaultSelectionIds: LongArray,
        /** True when every contact with data has either a primary item or exactly one item. */
        val allHaveDefaults: Boolean,
        /** True when some contact IDs had no email/phone data — caller should warn the user. */
        val hasMissingContacts: Boolean,
    )

    suspend operator fun invoke(contactIds: LongArray, scheme: String): Result =
        withContext(ioDispatcher) {
            val isEmail = scheme == ContactsUtils.SCHEME_MAILTO
            val selection =
                (if (isEmail) GroupMembersFragment.Query.EMAIL_SELECTION
                else GroupMembersFragment.Query.PHONE_SELECTION) +
                    " AND " + ContactsContract.Data.CONTACT_ID +
                    " IN (" + GroupUtil.convertArrayToString(contactIds) + ")"
            val projection =
                if (isEmail) GroupMembersFragment.Query.EMAIL_PROJECTION
                else GroupMembersFragment.Query.PHONE_PROJECTION

            data class DataRow(val itemId: Long, val isPrimary: Boolean, val data: String)

            val contactMap = mutableMapOf<String, MutableList<DataRow>>()
            val itemList = mutableListOf<String>()

            contentResolver.query(
                ContactsContract.Data.CONTENT_URI, projection, selection, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(GroupMembersFragment.Query.CONTACT_ID)
                    val itemId = cursor.getLong(GroupMembersFragment.Query.ITEM_ID)
                    val isPrimary = cursor.getInt(GroupMembersFragment.Query.PRIMARY) != 0
                    val data = cursor.getString(GroupMembersFragment.Query.DATA1)
                    if (data.isNotEmpty()) {
                        contactMap.getOrPut(contactId) { mutableListOf() }
                            .add(DataRow(itemId, isPrimary, data))
                        itemList.add(data)
                    }
                }
            }

            val defaultSelectionIds = contactMap.values.mapNotNull { rows ->
                (rows.firstOrNull { it.isPrimary } ?: rows.firstOrNull())?.itemId
            }.toLongArray()

            // Matches ContactDataHelperClass.hasDefaultItem():
            // a contact "has a default" if it has a primary item OR only one item.
            val allHaveDefaults = contactMap.values.all { rows ->
                rows.any { it.isPrimary } || rows.size == 1
            }

            val hasMissingContacts = contactMap.size < contactIds.size

            Result(itemList, defaultSelectionIds, allHaveDefaults, hasMissingContacts)
        }
}
