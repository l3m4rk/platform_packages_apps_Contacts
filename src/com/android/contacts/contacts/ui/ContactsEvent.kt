package com.android.contacts.contacts.ui

sealed class ContactsEvent {
    /** All contacts have defaults — fire ACTION_SENDTO directly. */
    data class SendToGroup(val addresses: String, val scheme: String) : ContactsEvent()

    /** Some contacts need disambiguation — open ContactSelectionActivity picker. */
    data class OpenGroupPicker(
        val contactIds: LongArray,
        val defaultSelectionIds: LongArray,
        val scheme: String,
    ) : ContactsEvent()

    /** No email/phone data found for the group. */
    data class ShowNoContactDataToast(val scheme: String) : ContactsEvent()

    /** Network unavailable when user triggered pull-to-refresh. */
    data object ShowConnectionError : ContactsEvent()

    /** Remove the given contacts from their group via ContactSaveService. */
    data class RemoveFromGroup(
        val contactIds: LongArray,
        val groupId: Long,
        val accountName: String?,
        val accountType: String?,
        val dataSet: String?,
    ) : ContactsEvent()
}
