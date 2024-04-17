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
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;
import org.birthdayadapter.service.MainIntentService;
import org.birthdayadapter.util.AccountListAdapter;
import org.birthdayadapter.util.AccountListEntry;
import org.birthdayadapter.util.AccountListLoader;

import java.util.HashSet;
import java.util.List;

public class AccountListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<List<AccountListEntry>> {

    AccountListAdapter mAdapter;
    BaseActivity mActivity;
    ListView mListView;

    DataSetObserver dos = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            applyBlacklist();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_list_fragment, container, false);
        mListView = view.findViewById(R.id.account_list);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mActivity = (BaseActivity) getActivity();

        mAdapter = new AccountListAdapter(mActivity);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Update underlying data and notify adapter of change. The adapter will update the view
                // automatically
                AccountListEntry entry = mAdapter.getItem(position);
                entry.setSelected(!entry.isSelected());
                mAdapter.notifyDataSetChanged();
            }
        });

        mAdapter.registerDataSetObserver(dos);

        LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    private void applyBlacklist() {
        HashSet<Account> blacklist = mAdapter.getAccountBlacklist();

        if (blacklist != null) {
            ProviderHelper.setAccountBlacklist(getActivity(), blacklist);
            mActivity.mySharedPreferenceChangeListener.startServiceAction(
                    MainIntentService.ACTION_MANUAL_COMPLETE_SYNC);
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
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<AccountListEntry>> loader) {
        mAdapter.setData(null);
    }
}
