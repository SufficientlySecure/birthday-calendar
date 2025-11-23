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

import android.accounts.Account;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.birthdayadapter.R;

import java.util.HashSet;
import java.util.List;

public class AccountListAdapter extends ArrayAdapter<AccountListEntry> {
    private final LayoutInflater mInflater;

    public AccountListAdapter(Context context) {
        super(context, -1);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setData(List<AccountListEntry> data) {
        clear();
        if (data != null) {
            // addAll will call notifyDataSetChanged() for us
            addAll(data);
        }
    }

    public HashSet<Account> getAccountBlacklist() {
        if (getCount() == 0) {
            return null;
        }

        HashSet<Account> blacklist = new HashSet<>();
        for (int i = 0; i < getCount(); i++) {
            AccountListEntry entry = getItem(i);
            if (entry != null && !entry.isSelected()) {
                blacklist.add(entry.getAccount());
            }
        }
        return blacklist;
    }

    /**
     * Populate new items in the list.
     */
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.account_list_entry, parent, false);
        } else {
            view = convertView;
        }

        AccountListEntry entry = getItem(position);
        ImageView iconView = view.findViewById(R.id.account_list_icon);
        TextView titleView = view.findViewById(R.id.account_list_text);
        TextView subtitleView = view.findViewById(R.id.account_list_subtext);
        TextView countersView = view.findViewById(R.id.account_list_counters);
        ImageView infoButton = view.findViewById(R.id.account_list_info_button);

        if (entry != null) {
            titleView.setText(entry.getLabel());
            subtitleView.setText(entry.getAccount().name);

            int contactCount = entry.getContactCount();
            int dateCount = entry.getDateCount();

            String contactsStr = getContext().getResources().getQuantityString(R.plurals.contacts_count, contactCount, contactCount);
            String datesStr = getContext().getResources().getQuantityString(R.plurals.dates_count, dateCount, dateCount);

            String countersSummary = getContext().getString(R.string.account_list_counters_format, contactsStr, datesStr);
            countersView.setText(countersSummary);

            if (entry.getGroups().isEmpty()) {
                infoButton.setVisibility(View.GONE);
            } else {
                infoButton.setVisibility(View.VISIBLE);
                infoButton.setOnClickListener(v -> {
                    StringBuilder groupsBuilder = new StringBuilder();
                    for (GroupListEntry group : entry.getGroups()) {
                        String groupContactsStr = getContext().getResources().getQuantityString(R.plurals.contacts_count, group.getContactCount(), group.getContactCount());
                        String groupDatesStr = getContext().getResources().getQuantityString(R.plurals.dates_count, group.getDateCount(), group.getDateCount());
                        String groupCounters = getContext().getString(R.string.account_list_counters_format, groupContactsStr, groupDatesStr);
                        groupsBuilder.append(group.getTitle()).append(" (").append(groupCounters).append(")\n");
                    }

                    new AlertDialog.Builder(getContext())
                            .setTitle(entry.getLabel())
                            .setMessage(groupsBuilder.toString().trim())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }

            int textColor;
            int secondaryTextColor;
            if (entry.getDateCount() == 0) {
                textColor = Color.GRAY;
                secondaryTextColor = Color.GRAY;
            } else {
                TypedValue typedValue = new TypedValue();
                getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
                textColor = ContextCompat.getColor(getContext(), typedValue.resourceId);

                TypedValue secondaryTypedValue = new TypedValue();
                getContext().getTheme().resolveAttribute(android.R.attr.textColorSecondary, secondaryTypedValue, true);
                secondaryTextColor = ContextCompat.getColor(getContext(), secondaryTypedValue.resourceId);
            }
            titleView.setTextColor(textColor);
            subtitleView.setTextColor(secondaryTextColor);
            countersView.setTextColor(secondaryTextColor);

            Drawable icon = entry.getIcon();
            if (icon != null) {
                iconView.setImageDrawable(icon);
                iconView.setVisibility(View.VISIBLE);
            } else {
                // Hide the icon view if no icon is available to prevent crashes from recursive drawables
                iconView.setVisibility(View.GONE);
            }

            CheckBox cBox = view.findViewById(R.id.account_list_cbox);
            cBox.setChecked(entry.isSelected());
        }

        return view;
    }

}
