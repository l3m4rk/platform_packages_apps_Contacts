package com.android.contacts.contacts.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android.contacts.editor.ContactEditorFragment
import com.android.contacts.logging.ScreenEvent
import com.android.contacts.preference.ContactsPreferenceActivity
import com.android.contacts.ui.core.AppTheme
import com.android.contacts.util.AccountFilterUtil
import com.android.contacts.util.ImplicitIntentsUtil
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ContactsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ContactsScreen(
                    onContactClick = { uri ->
                        ImplicitIntentsUtil.startQuickContact(
                            this,
                            uri,
                            ScreenEvent.ScreenType.ALL_CONTACTS
                        )
                    },
                    onCreateContact = { createContact() },
                    onSettingsClick = { startActivity(createPreferenceIntent()) },
                )
            }
        }
    }

    private fun createPreferenceIntent() =
        Intent(this@ContactsActivity, ContactsPreferenceActivity::class.java)
            .apply {
                putExtra(
                    ContactsPreferenceActivity.EXTRA_NEW_LOCAL_PROFILE,
                    ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE,
                )
            }

    private fun createContact() {
        val filter = AccountFilterUtil.createContactsFilter(this@ContactsActivity)
        AccountFilterUtil.startEditorIntent(this@ContactsActivity, intent, filter)
    }
}
