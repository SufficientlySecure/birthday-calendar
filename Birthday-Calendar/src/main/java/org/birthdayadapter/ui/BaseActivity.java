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

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.MySharedPreferenceChangeListener;

import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity {

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;
    private SyncStatusObserver mSyncStatusObserver;
    private Object mSyncObserverHandle;
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
                (tab, position) -> tab.setText(((ViewPagerAdapter)viewPager.getAdapter()).getPageTitle(position))
        ).attach();

        mySharedPreferenceChangeListener = new MySharedPreferenceChangeListener(this);

        mSyncStatusObserver = new SyncStatusObserver() {
            @Override
            public void onStatusChanged(int which) {
                runOnUiThread(() -> {
                    Account account = new Account(Constants.ACCOUNT_NAME, getString(R.string.account_type));
                    boolean syncActive = ContentResolver.isSyncActive(account, Constants.CONTENT_AUTHORITY);
                    if (mProgressBar != null) {
                        mProgressBar.setVisibility(syncActive ? View.VISIBLE : View.GONE);
                    }
                });
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING | ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
        mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask, mSyncStatusObserver);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    private void setupViewPager(ViewPager2 viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        adapter.addFragment(new BasePreferenceFragment(), getString(R.string.tab_main));
        adapter.addFragment(new ExtendedPreferencesFragment(), getString(R.string.tab_preferences));
        adapter.addFragment(new AccountListFragment(), getString(R.string.tab_accounts));
        adapter.addFragment(new HelpFragment(), getString(R.string.tab_help));
        adapter.addFragment(new AboutFragment(), getString(R.string.tab_about));
        viewPager.setAdapter(adapter);
    }

    class ViewPagerAdapter extends FragmentStateAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        public Fragment createFragment(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getItemCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

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
