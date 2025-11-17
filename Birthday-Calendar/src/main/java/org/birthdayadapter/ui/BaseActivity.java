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
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.birthdayadapter.R;
import org.birthdayadapter.util.MySharedPreferenceChangeListener;

import java.util.List;

public class BaseActivity extends AppCompatActivity {

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.base_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mProgressBar = findViewById(R.id.progress_spinner);

        ViewPager2 viewPager = findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(((ViewPagerAdapter) viewPager.getAdapter()).getPageTitle(position))
        ).attach();

        mySharedPreferenceChangeListener = new MySharedPreferenceChangeListener(getApplicationContext());

        // Observe both manual and periodic sync workers
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("manual_sync")
                .observe(this, new Observer<List<WorkInfo>>() {
                    @Override
                    public void onChanged(List<WorkInfo> workInfos) {
                        updateSpinner(workInfos);
                    }
                });
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("birthday_sync")
                .observe(this, new Observer<List<WorkInfo>>() {
                    @Override
                    public void onChanged(List<WorkInfo> workInfos) {
                        updateSpinner(workInfos);
                    }
                });
    }

    private void updateSpinner(List<WorkInfo> workInfos) {
        if (workInfos == null || workInfos.isEmpty()) {
            return;
        }

        boolean isSyncing = false;
        for (WorkInfo workInfo : workInfos) {
            if (workInfo.getState() == WorkInfo.State.RUNNING || workInfo.getState() == WorkInfo.State.ENQUEUED) {
                isSyncing = true;
                break;
            }
        }

        if (mProgressBar != null) {
            mProgressBar.setVisibility(isSyncing ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void setupViewPager(ViewPager2 viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
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
