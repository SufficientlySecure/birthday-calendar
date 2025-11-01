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

import android.Manifest;
import android.accounts.Account;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;
import org.birthdayadapter.service.MainIntentService;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.AccountListAdapter;
import org.birthdayadapter.util.AccountListEntry;
import org.birthdayadapter.util.AccountListLoader;

import java.util.HashSet;
import java.util.List;

public class AccountListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<List<AccountListEntry>> {

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

    AccountListAdapter mAdapter;
    BaseActivity mActivity;
    ListView mListView;
    TextView mEmptyView;
    private HashSet<Account> initialBlacklist;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_list_fragment, container, false);
        mListView = view.findViewById(R.id.account_list);
        mEmptyView = view.findViewById(android.R.id.empty);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mActivity = (BaseActivity) getActivity();
        if (mActivity == null) return;

        mAdapter = new AccountListAdapter(mActivity);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView); // Link the empty view to the list

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AccountListEntry entry = mAdapter.getItem(position);
                if (entry != null) {
                    entry.setSelected(!entry.isSelected());
                    mAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check for permissions and load accounts if granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            LoaderManager.getInstance(this).restartLoader(0, null, this);
        } else {
            // Request permission if not granted
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save the blacklist when the user leaves the screen, but only if it has changed
        applyBlacklistIfNeeded();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, now we can load the accounts
                LoaderManager.getInstance(this).restartLoader(0, null, this);
            } else {
                // Permission denied, show a message to the user
                Toast.makeText(getContext(), "Permission to read contacts denied. Accounts cannot be loaded.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applyBlacklistIfNeeded() {
        if (mAdapter == null || mActivity == null || mActivity.mySharedPreferenceChangeListener == null) {
            return;
        }

        HashSet<Account> newBlacklist = mAdapter.getAccountBlacklist();

        // Only save and sync if the blacklist has actually changed
        if (newBlacklist != null && !newBlacklist.equals(initialBlacklist)) {
            ProviderHelper.setAccountBlacklist(getActivity(), newBlacklist);

            AccountHelper accountHelper = new AccountHelper(mActivity, null);
            if (accountHelper.isAccountActivated()) {
                mActivity.mySharedPreferenceChangeListener.startServiceAction(
                        MainIntentService.ACTION_MANUAL_COMPLETE_SYNC);
            }
        }
    }

    @NonNull
    @Override
    public Loader<List<AccountListEntry>> onCreateLoader(int id, @Nullable Bundle args) {
        return new AccountListLoader(requireActivity());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<AccountListEntry>> loader, List<AccountListEntry> data) {
        mAdapter.setData(data);
        // Store the initial state of the blacklist after data is loaded
        if (mAdapter != null) {
            initialBlacklist = mAdapter.getAccountBlacklist();
        }
        
        if (data == null || data.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<AccountListEntry>> loader) {
        mAdapter.setData(null);
    }
}
