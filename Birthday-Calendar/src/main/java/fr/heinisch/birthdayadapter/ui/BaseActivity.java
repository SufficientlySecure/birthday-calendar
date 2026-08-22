
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

package fr.heinisch.birthdayadapter.ui;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.util.AccountHelper;
import fr.heinisch.birthdayadapter.util.IPurchaseHelper;
import fr.heinisch.birthdayadapter.util.MySharedPreferenceChangeListener;
import fr.heinisch.birthdayadapter.util.PurchaseHelperFactory;
import fr.heinisch.birthdayadapter.util.SyncStatusManager;

public class BaseActivity extends AppCompatActivity {

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;
    private ProgressBar mProgressBar;

    private static final int VERSION_CODE_CUSTOM_PREFS_MIGRATION = 31306;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable Edge-to-Edge display
        EdgeToEdge.enable(this);
        
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Run migration for existing users
        handleMigrations(prefs);

        boolean hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false);
        boolean ignorePermissionCheck = getIntent().getBooleanExtra(OnboardingActivity.EXTRA_IGNORE_PERMISSION_CHECK_ONCE, false);

        if (!hasSeenOnboarding) {
            launchOnboarding();
            return;
        }

        if (!ignorePermissionCheck && arePermissionsMissing()) {
            launchOnboarding();
            return;
        }

        // Set default values from XML before any UI is created
        PreferenceManager.setDefaultValues(this, R.xml.pref_preferences, false);

        setDefaultReminder();

        setContentView(R.layout.activity_base);

        IPurchaseHelper mPurchaseHelper = PurchaseHelperFactory.create();

        final Toolbar toolbar = findViewById(R.id.toolbar);
        final AppBarLayout appBarLayout = findViewById(R.id.app_bar_layout);
        
        if (!isFullVersionUnlocked(this)) {
            toolbar.setTitle(getString(R.string.app_name) + " (Free)");
        } else {
            toolbar.setTitle(getString(R.string.app_name));
        }
        setSupportActionBar(toolbar);

        final ViewPager2 viewPager = findViewById(R.id.viewpager);
        View mainContent = findViewById(R.id.main_content);

        // Apply insets to handle edge-to-edge display using the modern API
        ViewCompat.setOnApplyWindowInsetsListener(mainContent, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Padding the main content to avoid overlap with system bars
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            
            // Apply the top inset as padding to the AppBarLayout instead of the Toolbar
            // This prevents the title from being pushed out of view
            appBarLayout.setPadding(appBarLayout.getPaddingLeft(), systemBars.top, appBarLayout.getPaddingRight(), appBarLayout.getPaddingBottom());

            return windowInsets;
        });

        mProgressBar = findViewById(R.id.progress_spinner);

        setupViewPager(viewPager);

        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(((ViewPagerAdapter) Objects.requireNonNull(viewPager.getAdapter())).getPageTitle(position))
        ).attach();

        mySharedPreferenceChangeListener = new MySharedPreferenceChangeListener(getApplicationContext());

        // Observe the global sync status
        SyncStatusManager.getInstance().isSyncing().observe(this, isSyncing -> {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(isSyncing ? View.VISIBLE : View.GONE);
            }
        });

        // Check for existing purchases and restore them if necessary
        if (!isFullVersionUnlocked(this)) {
            ExecutorService executor = newSingleThreadExecutor();
            executor.execute(() -> mPurchaseHelper.verifyAndRestorePurchases(this));
        }
    }

    private void handleMigrations(SharedPreferences prefs) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long currentVersionCode;
            currentVersionCode = pInfo.getLongVersionCode();
            long lastSeenVersionCode = prefs.getLong("last_seen_version_code", 0);
            if (currentVersionCode > lastSeenVersionCode) {
                // This is an update. Check if we need to migrate existing users.
                if (lastSeenVersionCode < VERSION_CODE_CUSTOM_PREFS_MIGRATION) {
                    migrateCustomPreferences(prefs);
                }
                if (!prefs.getBoolean("has_seen_onboarding", false) && areAllPermissionsGranted()) {
                    // This is an existing user with all permissions. Mark onboarding as seen and enable the adapter.
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("has_seen_onboarding", true);
                    editor.putBoolean(getString(R.string.pref_enabled_key), true);
                    editor.apply();

                    // Activate the account in the background
                    AccountHelper accountHelper = new AccountHelper(this);
                    ExecutorService executor = newSingleThreadExecutor();
                    executor.execute(accountHelper::addAccountAndSync);
                }

                // Update the last seen version code
                prefs.edit().putLong("last_seen_version_code", currentVersionCode).apply();
            }
        } catch (PackageManager.NameNotFoundException e) {
            // This should not happen
        }
    }

    private void migrateCustomPreferences(SharedPreferences defaultPrefs) {
        SharedPreferences customPrefs = getSharedPreferences("preferences", MODE_PRIVATE);
        if (customPrefs.contains(getString(R.string.pref_enabled_key))) {
            boolean enabled = customPrefs.getBoolean(getString(R.string.pref_enabled_key), false);
            defaultPrefs.edit().putBoolean(getString(R.string.pref_enabled_key), enabled).apply();
            // Clear the old file
            customPrefs.edit().clear().apply();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mySharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(mySharedPreferenceChangeListener);
    }

    private boolean arePermissionsMissing() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private boolean areAllPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void launchOnboarding() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }

    private void setDefaultReminder() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Only set default reminder if it has never been set before
        if (prefs.getStringSet(getString(R.string.pref_reminders_key), null) == null) {
            SharedPreferences.Editor editor = prefs.edit();
            Set<String> reminderSet = new HashSet<>();
            reminderSet.add(String.valueOf(getResources().getInteger(R.integer.pref_reminder_time_def)));
            editor.putStringSet(getString(R.string.pref_reminders_key), reminderSet);
            editor.apply();
        }
    }

    private void setupViewPager(ViewPager2 viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);
    }

    class ViewPagerAdapter extends FragmentStateAdapter {
        private final String[] mFragmentTitles = new String[]{
                getString(R.string.tab_main),
                getString(R.string.tab_preferences),
                getString(R.string.tab_accounts),
                getString(R.string.tab_help),
                getString(R.string.tab_about)
        };

        public ViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new BasePreferenceFragment();
                case 1:
                    return new ExtendedPreferencesFragment();
                case 2:
                    return new AccountListFragment();
                case 3:
                    return new HelpFragment();
                case 4:
                    return new AboutFragment();
            }
            return new BasePreferenceFragment();
        }

        @Override
        public int getItemCount() {
            return mFragmentTitles.length;
        }

        public CharSequence getPageTitle(int position) {
            return mFragmentTitles[position];
        }
    }
}
