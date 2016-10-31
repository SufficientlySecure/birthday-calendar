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

import java.util.HashSet;
import java.util.List;

import android.accounts.Account;

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;
import org.birthdayadapter.service.MainIntentService;
import org.birthdayadapter.util.AccountListEntry;
import org.birthdayadapter.util.AccountListAdapter;
import org.birthdayadapter.util.AccountListLoader;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.annotation.SuppressLint;
import android.os.Bundle;

import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

@SuppressLint("NewApi")
public class AccountListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<List<AccountListEntry>> {

    AccountListAdapter mAdapter;
    BaseActivity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_list_fragment, null);
        return view;
    }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Can't be used with a custom content view:
        // setEmptyText("No accounts");

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new AccountListAdapter(getActivity());
        setListAdapter(mAdapter);

        mActivity = (BaseActivity) getActivity();

        // Start out with a progress indicator.
        // Can't be used with a custom content view:
        // setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);

        Button saveButton = (Button) getActivity().findViewById(R.id.account_list_save);
        saveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                HashSet<Account> blacklist = new HashSet<Account>();
                for (AccountListEntry entry : mAdapter.getData()) {
                    Log.d(Constants.TAG, "entry: " + entry.getLabel() + " " + entry.isSelected());
                    if (!entry.isSelected()) {
                        blacklist.add(entry.getAccount());
                    }
                }

                ProviderHelper.setAccountBlacklist(getActivity(), blacklist);

                // resync
                mActivity.mySharedPreferenceChangeListener.startServiceAction(MainIntentService.ACTION_MANUAL_COMPLETE_SYNC);
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        // Update underlying data and notify adapter of change. The adapter will update the view
        // automatically
        AccountListEntry entry = mAdapter.getItem(position);
        entry.setSelected(!entry.isSelected());
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public Loader<List<AccountListEntry>> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader with no arguments, so it is simple.
        return new AccountListLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<AccountListEntry>> loader, List<AccountListEntry> data) {
        // Set the new data in the adapter.
        mAdapter.setData(data);

        // The list should now be shown.
        if (isResumed()) {
            // Can't be used with a custom content view:
            // setListShown(true);
        } else {
            // Can't be used with a custom content view:
            // setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<AccountListEntry>> loader) {
        // Clear the data in the adapter.
        mAdapter.setData(null);
    }

}