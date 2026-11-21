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
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.AccountHelper
import fr.heinisch.birthdayadapter.util.MySharedPreferenceChangeListener
import fr.heinisch.birthdayadapter.util.PurchaseHelperFactory
import fr.heinisch.birthdayadapter.util.SyncStatusManager
import fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked
import java.util.concurrent.Executors

class BaseActivity : AppCompatActivity() {

    @JvmField
    var mySharedPreferenceChangeListener: MySharedPreferenceChangeListener? = null

    companion object {
        private const val VERSION_CODE_CUSTOM_PREFS_MIGRATION = 31306
        private const val VERSION_CODE_ONBOARDING_MIGRATION = 43

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        handleMigrations(prefs)

        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
        val ignorePermissionCheck = intent.getBooleanExtra(
            OnboardingActivity.EXTRA_IGNORE_PERMISSION_CHECK_ONCE,
            false
        )

        if (!hasSeenOnboarding || (!ignorePermissionCheck && arePermissionsMissing())) {
            launchOnboarding()
            return
        }

        if (prefs.getBoolean(getString(R.string.pref_enabled_key), false)) {
            val accountHelper = AccountHelper(this)
            if (!accountHelper.isAccountActivated() && areAllPermissionsGranted()) {
                Executors.newSingleThreadExecutor().execute { accountHelper.addAccountAndSync() }
            }
        }

        PreferenceManager.setDefaultValues(this, R.xml.pref_preferences, false)
        setDefaultReminder()

        val mPurchaseHelper = PurchaseHelperFactory.create()
        if (!isFullVersionUnlocked(this)) {
            Executors.newSingleThreadExecutor().execute {
                mPurchaseHelper.verifyAndRestorePurchases(this)
            }
        }

        mySharedPreferenceChangeListener = MySharedPreferenceChangeListener(applicationContext)

        val title = if (!isFullVersionUnlocked(this)) {
            getString(R.string.app_name) + " (Free)"
        } else {
            getString(R.string.app_name)
        }

        setContent {
            BirthdayAdapterTheme {
                MainScreen(
                    title = title,
                    activity = this
                )
            }
        }
    }

    private fun handleMigrations(prefs: SharedPreferences) {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersionCode = pInfo.longVersionCode
            val lastSeenVersionCode = prefs.getLong("last_seen_version_code", 0)
            if (currentVersionCode > lastSeenVersionCode) {
                if (lastSeenVersionCode < VERSION_CODE_CUSTOM_PREFS_MIGRATION) {
                    migrateCustomPreferences(prefs)
                }
                if (lastSeenVersionCode < VERSION_CODE_ONBOARDING_MIGRATION) {
                    migrateOnboardingState(prefs)
                }
                prefs.edit().putLong("last_seen_version_code", currentVersionCode).apply()
            }
        } catch (_: PackageManager.NameNotFoundException) {
        }
    }

    private fun migrateCustomPreferences(defaultPrefs: SharedPreferences) {
        val customPrefs = getSharedPreferences("preferences", MODE_PRIVATE)
        if (customPrefs.contains(getString(R.string.pref_enabled_key))) {
            val enabled = customPrefs.getBoolean(getString(R.string.pref_enabled_key), false)
            defaultPrefs.edit().putBoolean(getString(R.string.pref_enabled_key), enabled).apply()
            customPrefs.edit().clear().apply()
        }
    }

    private fun migrateOnboardingState(prefs: SharedPreferences) {
        if (!prefs.getBoolean("has_seen_onboarding", false) && areAllPermissionsGranted()) {
            prefs.edit()
                .putBoolean("has_seen_onboarding", true)
                .putBoolean(getString(R.string.pref_enabled_key), true)
                .apply()

            val accountHelper = AccountHelper(this)
            Executors.newSingleThreadExecutor().execute { accountHelper.addAccountAndSync() }
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(mySharedPreferenceChangeListener)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(mySharedPreferenceChangeListener)
    }

    private fun arePermissionsMissing(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return true
            }
        }
        return false
    }

    private fun areAllPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun launchOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun setDefaultReminder() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getStringSet(getString(R.string.pref_reminders_key), null) == null) {
            val reminderSet = HashSet<String>()
            reminderSet.add(resources.getInteger(R.integer.pref_reminder_time_def).toString())
            prefs.edit().putStringSet(getString(R.string.pref_reminders_key), reminderSet).apply()
        }
    }
}

private class ViewPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BasePreferenceFragment()
            1 -> ExtendedPreferencesFragment()
            2 -> AccountListFragment()
            3 -> HelpFragment()
            4 -> AboutFragment()
            else -> BasePreferenceFragment()
        }
    }
}

@Composable
private fun MainScreen(
    title: String,
    activity: FragmentActivity
) {
    var isSyncing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.Observer<Boolean> { syncing ->
            isSyncing = syncing == true
        }
        SyncStatusManager.getInstance().isSyncing().observeForever(observer)
        onDispose {
            SyncStatusManager.getInstance().isSyncing().removeObserver(observer)
        }
    }

    val tabs = listOf(
        stringResource(R.string.tab_main),
        stringResource(R.string.tab_preferences),
        stringResource(R.string.tab_accounts),
        stringResource(R.string.tab_help),
        stringResource(R.string.tab_about)
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                tabs.forEachIndexed { index, tabTitle ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedTabIndex = index
                        },
                        text = {
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    ViewPager2(context).apply {
                        id = View.generateViewId()
                        adapter = ViewPagerAdapter(activity)
                        offscreenPageLimit = 4
                        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                            override fun onPageSelected(position: Int) {
                                selectedTabIndex = position
                            }
                        })
                    }
                },
                update = { viewPager ->
                    if (viewPager.currentItem != selectedTabIndex) {
                        viewPager.setCurrentItem(selectedTabIndex, true)
                    }
                }
            )
        }
    }
}
