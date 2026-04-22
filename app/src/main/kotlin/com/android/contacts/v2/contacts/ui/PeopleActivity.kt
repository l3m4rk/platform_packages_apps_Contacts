package com.android.contacts.v2.contacts.ui

import android.accounts.Account
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.Intents
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.contacts.ContactSaveService
import com.android.contacts.ContactsUtils
import com.android.contacts.activities.ContactSelectionActivity
import com.android.contacts.R
import com.android.contacts.activities.RequestPermissionsActivity
import com.android.contacts.contacts.ui.ContactsEvent
import com.android.contacts.contacts.ui.ContactsScreen
import com.android.contacts.contacts.ui.ContactsViewModel
import com.android.contacts.editor.ContactEditorFragment
import com.android.contacts.editor.SelectAccountDialogFragment
import com.android.contacts.group.GroupListItem
import com.android.contacts.group.GroupNameEditDialogFragment
import com.android.contacts.group.GroupUtil
import com.android.contacts.group.UpdateGroupMembersAsyncTask
import com.android.contacts.list.UiIntentActions
import com.android.contacts.logging.ScreenEvent
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.preference.ContactsPreferenceActivity
import com.android.contacts.ui.core.AppTheme
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.ImplicitIntentsUtil
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val TAG_SELECT_ACCOUNT_DIALOG = "selectAccountDialog"
private const val TAG_GROUP_NAME_EDIT_DIALOG = "groupNameEditDialog"
private const val KEY_NEW_GROUP_ACCOUNT = "newGroupAccount"

@AndroidEntryPoint
class PeopleActivity : AppCompatActivity(), SelectAccountDialogFragment.Listener {

    private val viewModel: ContactsViewModel by viewModels()
    private var newGroupAccount: AccountWithDataSet? = null

    // Holds the groupId for the pending "add member" result so we can update the right group.
    private var pendingAddMemberGroup: GroupListItem? = null

    private val saveServiceListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ContactSaveService.BROADCAST_GROUP_DELETED) {
                onGroupDeleted(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.PeopleActivityTheme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState != null) {
            newGroupAccount = savedInstanceState.getString(KEY_NEW_GROUP_ACCOUNT)
                ?.let { AccountWithDataSet.unstringify(it) }
        }

        RequestPermissionsActivity.startPermissionActivityIfNeeded(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }

        setContent {
            AppTheme {
                ContactsScreen(
                    onContactClick = { uri ->
                        ImplicitIntentsUtil.startQuickContact(
                            this, uri, ScreenEvent.ScreenType.ALL_CONTACTS
                        )
                    },
                    onCreateContact = { createContact() },
                    onCreateLabel = { onCreateGroupMenuItemClicked() },
                    onAddMember = { group -> addMemberToGroup(group) },
                    onRenameGroup = { group -> renameGroup(group) },
                    onDeleteGroup = { group -> deleteGroup(group) },
                    onSettingsClick = { startActivity(createPreferenceIntent()) },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        newGroupAccount?.let { outState.putString(KEY_NEW_GROUP_ACCOUNT, it.stringify()) }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            saveServiceListener,
            IntentFilter(ContactSaveService.BROADCAST_GROUP_DELETED),
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(saveServiceListener)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        if (requestCode == GroupUtil.RESULT_GROUP_ADD_MEMBER) {
            val group = pendingAddMemberGroup ?: return
            pendingAddMemberGroup = null

            var contactIds = data.getLongArrayExtra(UiIntentActions.TARGET_CONTACT_IDS_EXTRA_KEY)
            if (contactIds == null) {
                val contactId = data.getLongExtra(UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, -1L)
                if (contactId > -1L) contactIds = longArrayOf(contactId)
            }
            if (contactIds != null) {
                UpdateGroupMembersAsyncTask(
                    UpdateGroupMembersAsyncTask.TYPE_ADD, this, contactIds,
                    group.groupId, group.accountName, group.accountType, group.dataSet,
                ).execute()
            }
        }
    }

    // region Group operations

    private fun onCreateGroupMenuItemClicked() {
        val extras = intent.extras
        val account = extras?.getParcelable<Account>(Intents.Insert.EXTRA_ACCOUNT)
        if (account == null) {
            selectAccountForNewGroup()
        } else {
            val dataSet = extras.getString(Intents.Insert.EXTRA_DATA_SET)
            onAccountChosen(AccountWithDataSet(account.name, account.type, dataSet), null)
        }
    }

    private fun selectAccountForNewGroup() {
        val accounts: List<AccountInfo> = Futures.getUnchecked(
            AccountTypeManager.getInstance(this)
                .filterAccountsAsync(AccountTypeManager.AccountFilter.GROUPS_WRITABLE)
        )
        if (accounts.isEmpty()) {
            Toast.makeText(this, R.string.groupCreateFailedToast, Toast.LENGTH_SHORT).show()
            return
        }
        if (accounts.size == 1) {
            onAccountChosen(accounts[0].account, null)
            return
        }
        SelectAccountDialogFragment.show(
            fragmentManager,
            R.string.dialog_new_group_account,
            AccountTypeManager.AccountFilter.GROUPS_WRITABLE,
            null,
            TAG_SELECT_ACCOUNT_DIALOG,
        )
    }

    override fun onAccountChosen(account: AccountWithDataSet, extraArgs: Bundle?) {
        newGroupAccount = account
        GroupNameEditDialogFragment.newInstanceForCreation(account, GroupUtil.ACTION_CREATE_GROUP)
            .show(fragmentManager, TAG_GROUP_NAME_EDIT_DIALOG)
    }

    override fun onAccountSelectorCancelled() {}

    private fun renameGroup(group: GroupListItem) {
        GroupNameEditDialogFragment.newInstanceForUpdate(
            AccountWithDataSet(group.accountName, group.accountType, group.dataSet),
            GroupUtil.ACTION_UPDATE_GROUP,
            group.groupId,
            group.title,
        ).show(fragmentManager, TAG_GROUP_NAME_EDIT_DIALOG)
    }

    private fun deleteGroup(group: GroupListItem) {
        startService(ContactSaveService.createGroupDeletionIntent(this, group.groupId))
    }

    private fun addMemberToGroup(group: GroupListItem) {
        pendingAddMemberGroup = group
        val intent = Intent(Intent.ACTION_PICK).apply {
            setClass(this@PeopleActivity, ContactSelectionActivity::class.java)
            type = Groups.CONTENT_TYPE
            putExtra(UiIntentActions.GROUP_ACCOUNT_NAME, group.accountName)
            putExtra(UiIntentActions.GROUP_ACCOUNT_TYPE, group.accountType)
            putExtra(UiIntentActions.GROUP_ACCOUNT_DATA_SET, group.dataSet)
            putStringArrayListExtra(UiIntentActions.GROUP_CONTACT_IDS, ArrayList())
        }
        startActivityForResult(intent, GroupUtil.RESULT_GROUP_ADD_MEMBER)
    }

    // endregion

    private fun handleEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.SendToGroup -> {
                val title = if (event.scheme == ContactsUtils.SCHEME_MAILTO)
                    getString(R.string.menu_sendEmailOption)
                else
                    getString(R.string.menu_sendMessageOption)
                val intent = Intent(
                    Intent.ACTION_SENDTO,
                    Uri.fromParts(event.scheme, event.addresses, null),
                )
                startActivity(Intent.createChooser(intent, title))
            }
            is ContactsEvent.OpenGroupPicker -> {
                val title = if (event.scheme == ContactsUtils.SCHEME_MAILTO)
                    getString(R.string.menu_sendEmailOption)
                else
                    getString(R.string.menu_sendMessageOption)
                startActivity(
                    GroupUtil.createSendToSelectionPickerIntent(
                        this,
                        event.contactIds,
                        event.defaultSelectionIds,
                        event.scheme,
                        title,
                    )
                )
            }
            is ContactsEvent.ShowNoContactDataToast -> {
                val msgRes = if (event.scheme == ContactsUtils.SCHEME_MAILTO)
                    R.string.groupSomeContactsNoEmailsToast
                else
                    R.string.groupSomeContactsNoPhonesToast
                Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show()
            }
            ContactsEvent.ShowConnectionError -> showConnectionErrorMsg()
            is ContactsEvent.RemoveFromGroup -> {
                UpdateGroupMembersAsyncTask(
                    UpdateGroupMembersAsyncTask.TYPE_REMOVE, this, event.contactIds,
                    event.groupId, event.accountName, event.accountType, event.dataSet,
                ).execute()
            }
        }
    }

    private fun createContact() {
        val filter = AccountFilterUtil.createContactsFilter(this)
        AccountFilterUtil.startEditorIntent(this, intent, filter)
    }

    private fun onGroupDeleted(intent: Intent) {
        if (!ContactSaveService.canUndo(intent)) return
        val message = getString(R.string.groupDeletedToast)
        val rootView: View = findViewById(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                startService(ContactSaveService.createUndoIntent(this, intent))
            }
            .show()
    }

    // region Stubs — kept for Java fragments that still compile-time-reference PeopleActivity

    fun isGroupView() = false
    fun isAllContactsView() = true
    fun isAccountView() = false
    fun isInSecondLevel() = false
    fun updateStatusBarBackground() {}
    fun updateStatusBarBackground(@Suppress("UNUSED_PARAMETER") color: Int) {}
    fun showConnectionErrorMsg() {
        val rootView: View = findViewById(android.R.id.content)
        Snackbar.make(rootView, R.string.connection_error_message, Snackbar.LENGTH_LONG).show()
    }
    fun updateDrawerGroupMenu(@Suppress("UNUSED_PARAMETER") groupId: Long) {}
    fun switchToAllContacts() {}
    fun showFabWithAnimation(@Suppress("UNUSED_PARAMETER") showFab: Boolean) {}
    fun getToolbar(): Toolbar? = null
    fun setDrawerLockMode(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {}
    fun closeDrawer() {}
    fun setContactsView(@Suppress("UNUSED_PARAMETER") mode: Any?) {}

    // endregion

    private fun createPreferenceIntent() =
        Intent(this, ContactsPreferenceActivity::class.java).apply {
            putExtra(
                ContactsPreferenceActivity.EXTRA_NEW_LOCAL_PROFILE,
                ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE,
            )
        }
}
