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

import java.util.List;

import org.birthdayadapter.util.AccountListEntry;
import org.birthdayadapter.util.AccountListAdapter;
import org.birthdayadapter.util.AccountListLoader;
import org.birthdayadapter.util.Log;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.os.Bundle;

import android.view.View;
import android.widget.ListView;

@SuppressLint("NewApi")
public class AccountListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<List<AccountListEntry>> {
//    private Activity mActivity;
    // This is the Adapter being used to display the list's data.
    AccountListAdapter mAdapter;

    // private long mCurrentRowId;

    /**
     * Handle Checkboxes clicks here, because to enable context menus on longClick we had to disable
     * focusable and clickable on checkboxes in layout xml.
     */
    // @Override
    // public void onListItemClick(ListView l, View v, int position, long id) {
    // super.onListItemClick(l, v, position, id);
    // mCurrentRowId = id;
    //
    // // Checkbox tags are defined by cursor position in HostsCursorAdapter, so we can get
    // // checkboxes by position of cursor
    // CheckBox cBox = (CheckBox) v.findViewWithTag("checkbox_" + position);
    //
    // if (cBox != null) {
    // if (cBox.isChecked()) {
    // cBox.setChecked(false);
    // // change status based on row id from cursor
    // // ProviderHelper.updateHostsSourceEnabled(mActivity, mCurrentRowId, false);
    // } else {
    // cBox.setChecked(true);
    // // ProviderHelper.updateHostsSourceEnabled(mActivity, mCurrentRowId, true);
    // }
    // } else {
    // Log.e(Constants.TAG, "Checkbox could not be found!");
    // }
    // }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Give some text to display if there is no data. In a real
        // application this would come from a resource.
        setEmptyText("No applications");

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new AccountListAdapter(getActivity());
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Insert desired behavior here.
        Log.i("LoaderCustom", "Item clicked: " + id);
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
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<AccountListEntry>> loader) {
        // Clear the data in the adapter.
        mAdapter.setData(null);
    }

}