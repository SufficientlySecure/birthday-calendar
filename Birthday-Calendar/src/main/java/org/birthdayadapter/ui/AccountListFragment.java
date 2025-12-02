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
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;

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
        LoaderManager.LoaderCallbacks<List<AccountListEntry>>, AccountListAdapter.OnBlacklistChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private AccountListAdapter mAdapter;
    private ListView mListView;
    private TextView mEmptyView;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        // Permission is granted. Continue the action or workflow in your app.
                        if (isAdded()) {
                            LoaderManager.getInstance(this).restartLoader(0, null, this);
                        }
                    } else {
                        // Explain to the user that the feature is unavailable because the
                        // features requires a permission that the user has denied.
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.permission_read_contacts_denied, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

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

        Activity activity = getActivity();
        if (activity == null) return;

        mAdapter = new AccountListAdapter(activity);
        mAdapter.setOnBlacklistChangedListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setEmptyView(mEmptyView); // Link the empty view to the list
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;

        // Use the default shared preferences file to be consistent with the PreferenceFragments.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Always update the adapter's state when the fragment resumes
        updateGroupFilteringState();

        // To prevent the permission dialog from becoming unresponsive on first launch,
        // we post the permission check to the view's message queue. This ensures that the
        // request is made only after the UI has been fully drawn and is interactive.
        View view = getView();
        if (view != null) {
            view.post(() -> {
                Context postContext = getContext();
                if (isAdded() && postContext != null) {
                    if (ContextCompat.checkSelfPermission(postContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        LoaderManager.getInstance(this).restartLoader(0, null, this);
                    } else {
                        // Request permission if not granted
                        requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
                    }
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Use the default shared preferences file to be consistent with the PreferenceFragments.
        Context context = getContext();
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (isAdded() && key != null && key.equals(getString(R.string.pref_group_filtering_key))) {
            // The preference has changed, so update the adapter's state and reload the data
            updateGroupFilteringState();
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    // Make sure fragment is still attached
                    if (isAdded()) {
                        LoaderManager.getInstance(this).restartLoader(0, null, this);
                    }
                });
            }
        }
    }

    @Override
    public void onBlacklistChanged() {
        saveBlacklist();

        Activity activity = getActivity();
        if (activity == null) return;

        Log.d(Constants.TAG, "Blacklist has changed, triggering manual sync.");
        AccountHelper accountHelper = new AccountHelper(activity);
        if (accountHelper.isAccountActivated()) {
            accountHelper.differentialSync();
        }
    }

    private void updateGroupFilteringState() {
        Context context = getContext();
        if (mAdapter != null && context != null && isAdded()) {
            // Use the default shared preferences file to be consistent with the PreferenceFragments.
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean groupFilteringEnabled = sharedPreferences.getBoolean(
                    getString(R.string.pref_group_filtering_key),
                    getResources().getBoolean(R.bool.pref_group_filtering_def)
            );
            mAdapter.setGroupFilteringEnabled(groupFilteringEnabled);
        }
    }

    private void saveBlacklist() {
        Activity activity = getActivity();
        if (mAdapter == null || activity == null) {
            return;
        }
        HashMap<Account, HashSet<String>> newBlacklist = mAdapter.getAccountBlacklist();
        Log.d(Constants.TAG, "Blacklist change detected, saving new blacklist");
        ProviderHelper.setAccountBlacklist(activity, newBlacklist);
    }

    @NonNull
    @Override
    public Loader<List<AccountListEntry>> onCreateLoader(int id, @Nullable Bundle args) {
        Activity activity = getActivity();
        Context context = getContext();
        if (activity == null || context == null) {
            // Return an empty loader if the activity or context is not available
            return new AccountListLoader(context, false);
        }
        // Use the default shared preferences file to be consistent with the PreferenceFragments.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean groupFilteringEnabled = sharedPreferences.getBoolean(
                getString(R.string.pref_group_filtering_key),
                getResources().getBoolean(R.bool.pref_group_filtering_def)
        );
        return new AccountListLoader(activity, groupFilteringEnabled);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<AccountListEntry>> loader, List<AccountListEntry> data) {
        if (!isAdded()) {
            return; // Fragment is not attached, do not update UI
        }
        mAdapter.setData(data);

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
        if (isAdded()) {
            mAdapter.setData(null);
        }
    }
}
