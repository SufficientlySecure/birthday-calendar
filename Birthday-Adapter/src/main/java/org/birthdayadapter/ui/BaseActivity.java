/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

import java.util.ArrayList;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.BackgroundStatusHandler;
import org.birthdayadapter.util.FragmentStatePagerAdapterV14;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.Window;

import org.birthdayadapter.util.MySharedPreferenceChangeListener;
import org.birthdayadapter.util.PreferencesHelper;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class BaseActivity extends FragmentActivity {
    private FragmentActivity mActivity;

    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;

    public static final int BACKGROUND_STATUS_HANDLER_DISABLE = 0;
    public static final int BACKGROUND_STATUS_HANDLER_ENABLE = 1;

    public BackgroundStatusHandler mBackgroundStatusHandler = new BackgroundStatusHandler(this);

    public MySharedPreferenceChangeListener mySharedPreferenceChangeListener;

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = this;

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Load Activity for Android < 4.0
            Intent oldActivity = new Intent(mActivity, BaseActivityV8.class);
            oldActivity.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(oldActivity);
            finish();
        } else {
            // Load new design with tabs
            mViewPager = new ViewPager(this);
            mViewPager.setId(R.id.pager);

            setContentView(mViewPager);

            ActionBar actionBar = getActionBar();
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(false);

            mTabsAdapter = new TabsAdapter(this, mViewPager);

            mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_main)),
                    BaseFragment.class, null);

            mTabsAdapter.addTab(actionBar.newTab().setText(getString(R.string.tab_preferences)),
                    PreferencesFragment.class, null);

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
                    dialog.show(getFragmentManager(), "workaroundDialog");
                }
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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
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

        public TabsAdapter(FragmentActivity activity, ViewPager pager) {
            super(activity.getFragmentManager());
            mContext = activity;
            mActionBar = activity.getActionBar();
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

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            Object tag = tab.getTag();
            for (int i = 0; i < mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    mViewPager.setCurrentItem(i);
                }
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }

    }

}