/*
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

package org.birthdayadapter.ui;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.ProgressBar;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.BackgroundStatusHandler;
import org.birthdayadapter.util.MySharedPreferenceChangeListener;
import org.birthdayadapter.util.PreferencesHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity implements BackgroundStatusHandler.StatusChangeListener {

    public BackgroundStatusHandler mBackgroundStatusHandler = new BackgroundStatusHandler(this);

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        progressBar = (ProgressBar) findViewById(R.id.progress_spinner);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        mySharedPreferenceChangeListener = new MySharedPreferenceChangeListener(this,
                mBackgroundStatusHandler);

        /*
         * Show workaround dialog for Android bug http://code.google.com/p/android/issues/detail?id=34880
         * Bug exists on Android 4.1 (SDK 16) and on some phones like Galaxy S4
         */
        if (BuildConfig.GOOGLE_PLAY_VERSION && PreferencesHelper.getShowWorkaroundDialog(this)
                && !isPackageInstalled("org.birthdayadapter.jb.workaround")) {
            if ((Build.VERSION.SDK_INT == 16)
                    || Build.DEVICE.toUpperCase(Locale.US).startsWith("GT-I9000")
                    || Build.DEVICE.toUpperCase(Locale.US).startsWith("GT-I9500")) {
                InstallWorkaroundDialogFragment dialog = InstallWorkaroundDialogFragment.newInstance();
                dialog.show(getSupportFragmentManager(), "workaroundDialog");
            }
        }
    }

    public void setIndeterminateProgress(boolean visible) {
        progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new BasePreferenceFragment(), getString(R.string.tab_main));
        adapter.addFragment(new ExtendedPreferencesFragment(), getString(R.string.tab_preferences));
        adapter.addFragment(new AccountListFragment(), getString(R.string.tab_accounts));
        adapter.addFragment(new HelpFragment(), getString(R.string.tab_help));
        adapter.addFragment(new AboutFragment(), getString(R.string.tab_about));
        viewPager.setAdapter(adapter);
    }

    @Override
    public void onStatusChange(boolean progress) {
        setIndeterminateProgress(progress);
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }

    public boolean isPackageInstalled(String targetPackage) {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

}