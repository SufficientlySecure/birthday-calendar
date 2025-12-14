/*
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2025 Matthias Heinisch <matthias@matthiasheinisch.de>
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

package org.birthdayadapter.ui;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.MySharedPreferenceChangeListener;
import org.birthdayadapter.util.PurchaseHelper;
import org.birthdayadapter.util.SyncStatusManager;
import org.birthdayadapter.util.VersionHelper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class BaseActivity extends AppCompatActivity {

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // overwrite locale to EN, for testing and screenshots only
        //    Locale locale = new Locale("en");
        //    Locale.setDefault(locale);
        //    Resources resources = getResources();
        //    Configuration config = resources.getConfiguration();
        //    config.setLocale(locale);
        //    resources.updateConfiguration(config, resources.getDisplayMetrics());

        // Set default values from XML before any UI is created
        PreferenceManager.setDefaultValues(this, R.xml.pref_preferences, false);

        setDefaultReminder();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.base_activity);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (!VersionHelper.isFullVersionUnlocked(this)) {
            toolbar.setTitle(getString(R.string.app_name) + " (Free)");
        }

        final ViewPager2 viewPager = findViewById(R.id.viewpager);
        View mainContent = findViewById(R.id.main_content);

        // Apply insets to handle edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(mainContent, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Apply the top inset as padding to the toolbar
            toolbar.setPadding(toolbar.getPaddingLeft(), systemBars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());

            // Apply the bottom inset as padding to the ViewPager
            viewPager.setPadding(viewPager.getPaddingLeft(), viewPager.getPaddingTop(), viewPager.getPaddingRight(), systemBars.bottom);

            // Return the original insets to allow children to handle them
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
        if (!VersionHelper.isFullVersionUnlocked(this)) {
            PurchaseHelper.verifyAndRestorePurchases(this);
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
