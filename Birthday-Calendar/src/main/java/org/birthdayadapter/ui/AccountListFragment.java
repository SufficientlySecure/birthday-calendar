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
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.AccountListAdapter;
import org.birthdayadapter.util.AccountListEntry;
import org.birthdayadapter.util.AccountListLoader;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class AccountListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<List<AccountListEntry>>, AccountListAdapter.OnBlacklistChangedListener {

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

    private AccountListAdapter mAdapter;
    private BaseActivity mActivity;
    private ListView mListView;
    private TextView mEmptyView;
    private boolean blacklistChanged = false;

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
        mAdapter.setOnBlacklistChangedListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView); // Link the empty view to the list
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
        if (blacklistChanged) {
            Log.d(Constants.TAG, "Blacklist has changed, triggering manual sync.");
            AccountHelper accountHelper = new AccountHelper(mActivity);
            if (accountHelper.isAccountActivated()) {
                accountHelper.manualSync();
            }
            blacklistChanged = false; // Reset the flag
        }
    }

    @Override
    public void onBlacklistChanged() {
        saveBlacklist();
        blacklistChanged = true;
    }

    private void saveBlacklist() {
        if (mAdapter == null || mActivity == null) {
            return;
        }
        HashMap<Account, HashSet<String>> newBlacklist = mAdapter.getAccountBlacklist();
        Log.d(Constants.TAG, "Blacklist change detected, saving new blacklist");
        ProviderHelper.setAccountBlacklist(getActivity(), newBlacklist);
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

    @NonNull
    @Override
    public Loader<List<AccountListEntry>> onCreateLoader(int id, @Nullable Bundle args) {
        return new AccountListLoader(requireActivity());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<AccountListEntry>> loader, List<AccountListEntry> data) {
        mAdapter.setData(data);
        // Reset change tracking when new data is loaded
        blacklistChanged = false;

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
