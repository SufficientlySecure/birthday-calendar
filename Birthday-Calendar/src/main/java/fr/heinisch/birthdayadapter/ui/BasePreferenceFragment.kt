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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.AccountHelper
import fr.heinisch.birthdayadapter.util.Constants
import fr.heinisch.birthdayadapter.util.SyncStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.GET_ACCOUNTS,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

class BasePreferenceFragment : Fragment() {

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
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        BasePreferenceScreen(lifecycleOwner = viewLifecycleOwner)
                    }
                }
            }
        }
    }
}

@Composable
private fun BasePreferenceScreen(lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accountHelper = remember(context) { AccountHelper(context) }
    val syncStatusPrefs = remember(context) {
        context.getSharedPreferences("sync_status_prefs", Context.MODE_PRIVATE)
    }

    var isEnabled by remember { mutableStateOf(accountHelper.isAccountActivated()) }
    var lastSyncTimestamp by remember {
        mutableLongStateOf(syncStatusPrefs.getLong("last_sync_timestamp", 0))
    }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }

    // Observe WorkManager periodic sync schedule
    DisposableEffect(lifecycleOwner) {
        val liveData = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("periodic_sync")
        val observer = androidx.lifecycle.Observer<List<WorkInfo>> { list ->
            workInfo = list.firstOrNull()
        }
        liveData.observe(lifecycleOwner, observer)
        onDispose {
            liveData.removeObserver(observer)
        }
    }

    // Refresh current time every 60s for relative time updates
    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = System.currentTimeMillis()
            delay(60.seconds)
        }
    }

    // SharedPreferences listener for last sync timestamp
    DisposableEffect(syncStatusPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "last_sync_timestamp") {
                lastSyncTimestamp = sharedPreferences.getLong("last_sync_timestamp", 0)
            }
        }
        syncStatusPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            syncStatusPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Check account activation state when screen appears
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val activated = accountHelper.isAccountActivated()
            launch(Dispatchers.Main) {
                isEnabled = activated
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val essentialPermissionsGranted = REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (essentialPermissionsGranted) {
            coroutineScope.launch(Dispatchers.IO) {
                accountHelper.addAccountAndSync()
                launch(Dispatchers.Main) {
                    isEnabled = true
                }
            }
        } else {
            isEnabled = false
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                accountHelper.addAccountAndSync()
                launch(Dispatchers.Main) {
                    isEnabled = true
                }
            }
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    val syncSummary = remember(lastSyncTimestamp, workInfo, isEnabled, currentTime) {
        buildSyncSummary(context, lastSyncTimestamp, workInfo, accountHelper.isAccountActivated())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Status Category
        PreferenceCategoryHeader(title = stringResource(R.string.pref_enable))
        SwitchPreferenceItem(
            title = stringResource(R.string.pref_enable_switch),
            summary = stringResource(R.string.pref_enable_summary),
            iconRes = R.drawable.ic_cake,
            checked = isEnabled
        ) { checked ->
            if (checked) {
                checkAndRequestPermissions()
            } else {
                isEnabled = false
                coroutineScope.launch(Dispatchers.IO) {
                    accountHelper.removeAccount()
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Contacts Category
        PreferenceCategoryHeader(title = stringResource(R.string.pref_contacts))
        ClickablePreferenceItem(
            title = stringResource(R.string.pref_contacts_open),
            summary = stringResource(R.string.pref_contacts_summary),
            iconRes = R.drawable.ic_contacts,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
                context.startActivity(intent)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Calendar Category
        PreferenceCategoryHeader(title = stringResource(R.string.pref_calendar))
        ClickablePreferenceItem(
            title = stringResource(R.string.pref_calendar_open),
            summary = stringResource(R.string.pref_calendar_summary),
            iconRes = R.drawable.ic_calendar,
            onClick = {
                val builder = CalendarContract.CONTENT_URI.buildUpon()
                builder.appendPath("time")
                ContentUris.appendId(builder, Calendar.getInstance().timeInMillis)
                val intent = Intent(Intent.ACTION_VIEW).setData(builder.build())
                context.startActivity(intent)
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Sync Category
        PreferenceCategoryHeader(title = stringResource(R.string.pref_sync))
        ClickablePreferenceItem(
            title = stringResource(R.string.pref_force_sync),
            summary = syncSummary,
            iconRes = R.drawable.ic_sync,
            onClick = {
                SyncStatusManager.getInstance().setSyncing(isSyncing = true)
                accountHelper.differentialSync()
            }
        )
    }
}

private fun buildSyncSummary(
    context: Context,
    lastSync: Long,
    workInfo: WorkInfo?,
    isAccountActivated: Boolean
): String {
    var summary: String = if (lastSync == 0L) {
        context.getString(R.string.last_sync_never)
    } else {
        context.getString(
            R.string.last_sync,
            DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        )
    }

    if ((isAccountActivated && workInfo != null)) {
        val nextRun = workInfo.nextScheduleTimeMillis
        val now = System.currentTimeMillis()
        val sanityThreshold = now + TimeUnit.DAYS.toMillis(Constants.SYNC_INTERVAL_DAYS.toLong() * 2)

        if (nextRun in (now + 1)..<sanityThreshold) {
            summary += "\n" + context.getString(
                R.string.next_sync,
                DateUtils.getRelativeTimeSpanString(nextRun, now, DateUtils.MINUTE_IN_MILLIS)
            )
        }
    }

    return summary
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchPreferenceItem(
    title: String,
    summary: String,
    iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ClickablePreferenceItem(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
