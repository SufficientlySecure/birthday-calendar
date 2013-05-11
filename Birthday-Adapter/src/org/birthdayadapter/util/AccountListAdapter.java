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

package org.birthdayadapter.util;

import java.util.List;

import org.birthdayadapter.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

@SuppressLint("NewApi")
public class AccountListAdapter extends ArrayAdapter<AccountListEntry> {
    private final LayoutInflater mInflater;

    public AccountListAdapter(Context context) {
        super(context, -1);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setData(List<AccountListEntry> data) {
        clear();
        if (data != null) {
            addAll(data);
        }
    }

    /**
     * Populate new items in the list.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.account_list_entry, parent, false);
        } else {
            view = convertView;
        }

        AccountListEntry entry = getItem(position);
        ((TextView) view.findViewById(R.id.account_list_text)).setText(entry.getLabel());
        ((TextView) view.findViewById(R.id.account_list_subtext)).setText(entry.getName());
        ((ImageView) view.findViewById(R.id.account_list_icon)).setImageDrawable(entry.getIcon());

        CheckBox cBox = (CheckBox) view.findViewById(R.id.account_list_cbox);

        if (cBox != null) {
            // bind position to tag of list item
            cBox.setTag("checkbox_" + position);

            cBox.setChecked(entry.isSelected());
        }

        return view;
    }

}
