package com.android.contacts.contacts.data.groups

import android.content.Context
import android.provider.ContactsContract.Groups
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.group.GroupListItem
import com.android.contacts.group.GroupUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import kotlin.collections.buildList

interface GroupsRepository {
    fun getGroups(): Flow<List<GroupListItem>>
}

// TODO: migrate GroupUtil to Kotlin along with tests
internal class GroupsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GroupsRepository {
    override fun getGroups(): Flow<List<GroupListItem>> = flow {
        val groups = context.contentResolver.query(
            Groups.CONTENT_SUMMARY_URI,
            PROJECTION,
            GroupUtil.DEFAULT_SELECTION,
            null,
            GroupUtil.getGroupsSortOrder(),
        )?.use { cursor ->
            buildList {
                for (i in 0 until cursor.count) {
                    GroupUtil.getGroupListItem(cursor, i)?.let { add(it) }
                }
            }
        } ?: emptyList()
        emit(groups.filter { !GroupUtil.isEmptyFFCGroup(it) })
    }.flowOn(ioDispatcher)

    companion object {

        // same as GroupListLoader.COLUMNS
        private val PROJECTION = arrayOf(
            Groups.ACCOUNT_NAME,        // 0
            Groups.ACCOUNT_TYPE,        // 1
            Groups.DATA_SET,            // 2
            Groups._ID,                 // 3
            Groups.TITLE,               // 4
            Groups.SUMMARY_COUNT,       // 5
            Groups.GROUP_IS_READ_ONLY,  // 6
            Groups.SYSTEM_ID,           // 7
        )
    }
}
