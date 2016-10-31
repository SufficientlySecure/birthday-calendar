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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.BackgroundStatusHandler;
import org.birthdayadapter.util.FragmentStatePagerAdapterV14;
import org.birthdayadapter.util.MySharedPreferenceChangeListener;
import org.birthdayadapter.util.PreferencesHelper;

import java.util.ArrayList;

public class BaseActivity extends AppCompatActivity {

    public BackgroundStatusHandler mBackgroundStatusHandler = new BackgroundStatusHandler(this);

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity mActivity = this;

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Load new design with tabs
        ViewPager mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.pager);

        setContentView(mViewPager);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);

        TabsAdapter mTabsAdapter = new TabsAdapter(this, mViewPager);

        mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_main)),
                BasePreferenceFragment.class, null);

        mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_preferences)),
                ExtendedPreferencesFragment.class, null);

        mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_accounts)),
                AccountListFragment.class, null);

        mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_help)),
                HelpFragment.class, null);

        mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_about)),
                AboutFragment.class, null);

        // default is disabled:
        mActivity.setProgressBarIndeterminateVisibility(Boolean.FALSE);

        mySharedPreferenceChangeListener = new MySharedPreferenceChangeListener(mActivity,
                mBackgroundStatusHandler);

            /*
             * Show workaround dialog for Android bug http://code.google.com/p/android/issues/detail?id=34880
             * Bug exists on Android 4.1 (SDK 16) and on some phones like Galaxy S4
             */
        if (BuildConfig.GOOGLE_PLAY_VERSION && PreferencesHelper.getShowWorkaroundDialog(mActivity)
                && !isPackageInstalled("org.birthdayadapter.jb.workaround")) {
            if ((Build.VERSION.SDK_INT == 16)
                    || Build.DEVICE.toUpperCase().startsWith("GT-I9000") || Build.DEVICE.toUpperCase().startsWith("GT-I9500")) {
                InstallWorkaroundDialogFragment dialog = InstallWorkaroundDialogFragment.newInstance();
                dialog.show(getSupportFragmentManager(), "workaroundDialog");
            }
        }
    }

    public boolean isPackageInstalled(String targetPackage) {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static class TabsAdapter extends FragmentStatePagerAdapterV14 implements
            ActionBar.TabListener, ViewPager.OnPageChangeListener {
        private final Context mContext;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        static final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, Bundle _args) {
                clss = _class;
                args = _args;
            }
        }

        public TabsAdapter(AppCompatActivity activity, ViewPager pager) {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mActionBar = activity.getSupportActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
            TabInfo info = new TabInfo(clss, args);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
        }

        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            mActionBar.setSelectedNavigationItem(position);
        }

        public void onPageScrollStateChanged(int state) {
        }

        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
            Object tag = tab.getTag();
            for (int i = 0; i < mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    mViewPager.setCurrentItem(i);
                }
            }
        }

        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        }

    }

}