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

    /** Delete the given contacts after user confirmation. */
    data class DeleteContacts(
        val contactIds: List<Long>,
        val displayNames: List<String>,
    ) : ContactsEvent()

    /** Share the given contacts as vCards. */
    data class ShareContacts(
        val lookupUris: List<android.net.Uri>,
    ) : ContactsEvent()

    /** Link/join the given contacts into one. */
    data class LinkContacts(
        val contactIds: List<Long>,
    ) : ContactsEvent()
}
