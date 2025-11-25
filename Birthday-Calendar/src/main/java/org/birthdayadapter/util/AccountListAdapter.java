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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;

import org.birthdayadapter.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class AccountListAdapter extends ArrayAdapter<AccountListEntry> {
    private final LayoutInflater mInflater;
    private OnBlacklistChangedListener mBlacklistChangedListener;
    private boolean mGroupFilteringEnabled;
    private ColorStateList mDefaultTintList;

    public interface OnBlacklistChangedListener {
        void onBlacklistChanged();
    }

    public AccountListAdapter(Context context) {
        super(context, -1);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setOnBlacklistChangedListener(OnBlacklistChangedListener listener) {
        this.mBlacklistChangedListener = listener;
    }

    public void setGroupFilteringEnabled(boolean enabled) {
        this.mGroupFilteringEnabled = enabled;
    }

    public void setData(List<AccountListEntry> data) {
        clear();
        if (data != null) {
            addAll(data);
        }
    }

    public HashMap<Account, HashSet<String>> getAccountBlacklist() {
        if (getCount() == 0) {
            return null;
        }

        HashMap<Account, HashSet<String>> blacklist = new HashMap<>();
        for (int i = 0; i < getCount(); i++) {
            AccountListEntry entry = getItem(i);
            if (entry != null) {
                HashSet<String> blacklistedGroups = new HashSet<>();
                // Always gather deselected groups to preserve their state
                for (GroupListEntry group : entry.getGroups()) {
                    if (!group.isSelected()) {
                        blacklistedGroups.add(group.getTitle());
                    }
                }

                if (entry.isNotSelected()) {
                    // Account is fully blacklisted. Add a null marker to signify this.
                    blacklistedGroups.add(null);
                    blacklist.put(entry.getAccount(), blacklistedGroups);
                } else {
                    // Account is not fully blacklisted, only add to map if there are specific groups blacklisted.
                    if (!blacklistedGroups.isEmpty()) {
                        blacklist.put(entry.getAccount(), blacklistedGroups);
                    }
                }
            }
        }
        return blacklist;
    }

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
        MaterialCheckBox cBox = view.findViewById(R.id.account_list_cbox);

        if (mDefaultTintList == null) {
            mDefaultTintList = cBox.getButtonTintList();
        }

        if (entry != null) {
            titleView.setText(entry.getLabel());
            subtitleView.setText(entry.getAccount().name);

            int contactCount = entry.getContactCount();
            int dateCount = entry.getDateCount();

            String contactsStr = getContext().getResources().getQuantityString(R.plurals.contacts_count, contactCount, contactCount);
            String datesStr = getContext().getResources().getQuantityString(R.plurals.dates_count, dateCount, dateCount);

            String countersSummary = getContext().getString(R.string.account_list_counters_format, contactsStr, datesStr);
            countersView.setText(countersSummary);

            // --- Checkbox State Logic ---
            if (entry.isNotSelected()) {
                // 1. Account is globally disabled -> UNCHECKED
                cBox.setCheckedState(MaterialCheckBox.STATE_UNCHECKED);
            } else {
                // Account is globally enabled, now check group states
                boolean hasDeselectedGroups = false;
                if (!entry.getGroups().isEmpty()) {
                    for (GroupListEntry group : entry.getGroups()) {
                        if (!group.isSelected()) {
                            hasDeselectedGroups = true;
                            break;
                        }
                    }
                }

                if (hasDeselectedGroups) {
                    // 2. Account is enabled, but some groups are disabled -> INDETERMINATE
                    cBox.setCheckedState(MaterialCheckBox.STATE_INDETERMINATE);
                } else {
                    // 3. Account is enabled and all groups are enabled (or no groups exist) -> CHECKED
                    cBox.setCheckedState(MaterialCheckBox.STATE_CHECKED);
                }
            }

            // --- Tinting logic for indeterminate state ---
            if (cBox.getCheckedState() == MaterialCheckBox.STATE_INDETERMINATE) {
                cBox.setButtonTintList(ColorStateList.valueOf(Color.GRAY));
            } else {
                // Reset to default tint
                cBox.setButtonTintList(mDefaultTintList);
            }


            // --- Click Listener Logic ---
            view.setOnClickListener(v -> {
                // This click only toggles the account's master selected state.
                // It does NOT change the individual group selections.
                entry.setSelected(entry.isNotSelected());
                notifyDataSetChanged();
                if (mBlacklistChangedListener != null) {
                    mBlacklistChangedListener.onBlacklistChanged();
                }
            });

            // --- Group Dialog Logic ---
            if (!mGroupFilteringEnabled || entry.getGroups().isEmpty()) {
                infoButton.setVisibility(View.GONE);
                infoButton.setOnClickListener(null);
            } else {
                infoButton.setVisibility(View.VISIBLE);
                infoButton.setImageResource(R.drawable.ic_group_24dp); // Always use the default icon
                infoButton.setOnClickListener(v -> {
                    View dialogView = mInflater.inflate(R.layout.dialog_group_list, null);
                    LinearLayout groupContainer = dialogView.findViewById(R.id.group_container);

                    for (GroupListEntry group : entry.getGroups()) {
                        View groupEntryView = mInflater.inflate(R.layout.group_list_entry, groupContainer, false);

                        MaterialCheckBox checkBox = groupEntryView.findViewById(R.id.group_list_cbox);
                        TextView groupTitleView = groupEntryView.findViewById(R.id.group_list_text);
                        TextView groupCountersView = groupEntryView.findViewById(R.id.group_list_counters);

                        groupTitleView.setText(group.getTitle());

                        String groupContactsStr = getContext().getResources().getQuantityString(R.plurals.contacts_count, group.getContactCount(), group.getContactCount());
                        String groupDatesStr = getContext().getResources().getQuantityString(R.plurals.dates_count, group.getDateCount(), group.getDateCount());
                        String groupCounters = getContext().getString(R.string.account_list_counters_format, groupContactsStr, groupDatesStr);
                        groupCountersView.setText(groupCounters);

                        checkBox.setChecked(group.isSelected());

                        groupEntryView.setOnClickListener(view_ -> {
                            group.setSelected(!group.isSelected());
                            checkBox.setChecked(group.isSelected());
                        });

                        groupContainer.addView(groupEntryView);
                    }

                    new AlertDialog.Builder(getContext())
                            .setTitle(entry.getLabel())
                            .setView(dialogView)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                if (mBlacklistChangedListener != null) {
                                    mBlacklistChangedListener.onBlacklistChanged();
                                }
                                // When the dialog closes, just notify the adapter to redraw the main list item.
                                // The getView() method will then re-evaluate the checkbox state.
                                notifyDataSetChanged();
                            })
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
                iconView.setVisibility(View.GONE);
            }
        }

        return view;
    }
}
