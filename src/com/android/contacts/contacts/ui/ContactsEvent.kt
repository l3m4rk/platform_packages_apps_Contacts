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
}
