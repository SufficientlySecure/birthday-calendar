/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of Birthday Adapter.
 *
 * Birthday Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fr.heinisch.birthdayadapter.ui

import android.Manifest
import android.accounts.Account
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.preference.PreferenceManager
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.provider.ProviderHelper
import fr.heinisch.birthdayadapter.util.AccountHelper
import fr.heinisch.birthdayadapter.util.AccountListEntry
import fr.heinisch.birthdayadapter.util.AccountListLoader
import fr.heinisch.birthdayadapter.util.Constants
import fr.heinisch.birthdayadapter.util.Log

class AccountListFragment : Fragment(),
    LoaderManager.LoaderCallbacks<List<AccountListEntry>>,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var accountEntries by mutableStateOf<List<AccountListEntry>?>(null)
    private var isLoading by mutableStateOf(true)
    private var permissionDenied by mutableStateOf(false)
    private var refreshTrigger by mutableIntStateOf(0)

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    permissionDenied = false
                    if (isAdded) {
                        LoaderManager.getInstance(this).restartLoader(0, null, this)
                    }
                } else {
                    permissionDenied = true
                    val ctx = context
                    if (ctx != null) {
                        Toast.makeText(
                            ctx,
                            R.string.permission_read_contacts_denied,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BirthdayAdapterTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AccountListScreen(
                            entries = accountEntries,
                            isLoading = isLoading,
                            permissionDenied = permissionDenied,
                            isGroupFilteringEnabled = isGroupFilteringEnabled(),
                            refreshTrigger = refreshTrigger,
                            onAccountChanged = {
                                saveBlacklist()
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val context = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(this)

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            LoaderManager.getInstance(this).restartLoader(0, null, this)
        } else {
            permissionDenied = true
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onPause() {
        super.onPause()
        val context = context ?: return
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (isAdded && key == getString(R.string.pref_group_filtering_key)) {
            activity?.runOnUiThread {
                if (isAdded) {
                    LoaderManager.getInstance(this).restartLoader(0, null, this)
                }
            }
        }
    }

    private fun isGroupFilteringEnabled(): Boolean {
        val ctx = context ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getBoolean(
            getString(R.string.pref_group_filtering_key),
            resources.getBoolean(R.bool.pref_group_filtering_def)
        )
    }

    private fun saveBlacklist() {
        val act = activity ?: return
        val currentEntries = accountEntries ?: return

        val blacklist = HashMap<Account, HashSet<String?>>()
        for (entry in currentEntries) {
            val blacklistedGroups = HashSet<String?>()
            for ((title, _, _, isSelected) in entry.groups) {
                if (!isSelected) {
                    blacklistedGroups.add(title)
                }
            }

            if (entry.isNotSelected) {
                blacklistedGroups.add(null)
                blacklist[entry.account] = blacklistedGroups
            } else if (blacklistedGroups.isNotEmpty()) {
                blacklist[entry.account] = blacklistedGroups
            }
        }

        Log.d(Constants.TAG, "Blacklist change detected, saving new blacklist")
        ProviderHelper.setAccountBlacklist(act, blacklist)

        val accountHelper = AccountHelper(act)
        if (accountHelper.isAccountActivated()) {
            accountHelper.differentialSync()
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<AccountListEntry>> {
        val act = activity ?: requireContext()
        val groupFilteringEnabled = isGroupFilteringEnabled()
        return AccountListLoader(act, groupFilteringEnabled)
    }

    override fun onLoadFinished(
        loader: Loader<List<AccountListEntry>>,
        data: List<AccountListEntry>?
    ) {
        if (!isAdded) return
        isLoading = false
        accountEntries = data
    }

    override fun onLoaderReset(loader: Loader<List<AccountListEntry>>) {
        if (isAdded) {
            accountEntries = null
            isLoading = true
        }
    }
}

@Composable
private fun AccountListScreen(
    entries: List<AccountListEntry>?,
    isLoading: Boolean,
    permissionDenied: Boolean,
    isGroupFilteringEnabled: Boolean,
    refreshTrigger: Int,
    onAccountChanged: () -> Unit
) {
    var selectedGroupDialogEntry by remember { mutableStateOf<AccountListEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.account_list_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            permissionDenied -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.permission_read_contacts_denied),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            entries.isNullOrEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.account_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = entries,
                        key = { entry -> entry.account.name + "_" + entry.account.type + "_" + refreshTrigger }
                    ) { entry ->
                        AccountItemRow(
                            entry = entry,
                            isGroupFilteringEnabled = isGroupFilteringEnabled,
                            refreshTrigger = refreshTrigger,
                            onToggleSelection = {
                                entry.setSelected(entry.isNotSelected)
                                onAccountChanged()
                            },
                            onOpenGroupDialog = {
                                selectedGroupDialogEntry = entry
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    selectedGroupDialogEntry?.let { entry ->
        GroupSelectionDialog(
            entry = entry,
            onDismiss = { selectedGroupDialogEntry = null },
            onConfirmed = {
                onAccountChanged()
            }
        )
    }
}

@Composable
private fun AccountItemRow(
    entry: AccountListEntry,
    isGroupFilteringEnabled: Boolean,
    refreshTrigger: Int,
    onToggleSelection: () -> Unit,
    onOpenGroupDialog: () -> Unit
) {
    val hasNoDates = entry.dateCount == 0

    val primaryTextColor = if (hasNoDates) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val secondaryTextColor = if (hasNoDates) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contactsStr = pluralStringResource(R.plurals.contacts_count, entry.contactCount, entry.contactCount)
    val datesStr = pluralStringResource(R.plurals.dates_count, entry.dateCount, entry.dateCount)
    val countersSummary = stringResource(R.string.account_list_counters_format, contactsStr, datesStr)

    val isAccountSelected = !entry.isNotSelected
    val hasDeselectedGroups = remember(entry.groups, entry.isNotSelected, refreshTrigger) {
        entry.groups.any { !it.isSelected }
    }

    val toggleableState = remember(isAccountSelected, hasDeselectedGroups, refreshTrigger) {
        when {
            !isAccountSelected -> ToggleableState.Off
            hasDeselectedGroups -> ToggleableState.Indeterminate
            else -> ToggleableState.On
        }
    }

    Surface(
        onClick = onToggleSelection,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val drawable = entry.icon
            if (drawable != null) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            setImageDrawable(drawable)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label ?: entry.account.type,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryTextColor
                )
                Text(
                    text = entry.account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
                Text(
                    text = countersSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            if (isGroupFilteringEnabled && entry.groups.isNotEmpty()) {
                IconButton(onClick = onOpenGroupDialog) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_group_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            TriStateCheckbox(
                state = toggleableState,
                onClick = onToggleSelection
            )
        }
    }
}

@Composable
private fun GroupSelectionDialog(
    entry: AccountListEntry,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val groups = remember(entry) { entry.groups }
    var groupStates by remember(entry) {
        mutableStateOf(groups.map { it.isSelected })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = entry.label ?: entry.account.name,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                groups.forEachIndexed { index, group ->
                    val isChecked = groupStates.getOrElse(index) { false }
                    val groupTitle = if (Constants.GROUP_TITLE_NO_GROUP == group.title) {
                        stringResource(R.string.account_list_no_group)
                    } else {
                        group.title
                    }

                    val groupContactsStr = pluralStringResource(
                        R.plurals.contacts_count, group.contactCount, group.contactCount
                    )
                    val groupDatesStr = pluralStringResource(
                        R.plurals.dates_count, group.dateCount, group.dateCount
                    )
                    val groupCounters = stringResource(
                        R.string.account_list_counters_format, groupContactsStr, groupDatesStr
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = groupStates.toMutableList()
                                updated[index] = !isChecked
                                groupStates = updated
                                group.isSelected = !isChecked
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val updated = groupStates.toMutableList()
                                updated[index] = checked
                                groupStates = updated
                                group.isSelected = checked
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = groupTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = groupCounters,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmed()
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
