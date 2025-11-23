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
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import androidx.loader.content.AsyncTaskLoader;

import org.birthdayadapter.R;
import org.birthdayadapter.provider.ProviderHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A custom Loader that loads all active accounts.
 */
public class AccountListLoader extends AsyncTaskLoader<List<AccountListEntry>> {

    private List<AccountListEntry> mAccounts;

    public AccountListLoader(Context context) {
        super(context);
    }

    /**
     * Perform comparison of account entry objects, sorting by the sum of contacts and dates.
     */
    public static final Comparator<AccountListEntry> TOTAL_COUNT_COMPARATOR = new Comparator<>() {
        private final Collator sCollator = Collator.getInstance();

        @Override
        public int compare(AccountListEntry object1, AccountListEntry object2) {
            int total1 = object1.getContactCount() + object1.getDateCount();
            int total2 = object2.getContactCount() + object2.getDateCount();

            // Sort by total count descending
            int countCompare = Integer.compare(total2, total1);
            if (countCompare != 0) {
                return countCompare;
            }
            // If counts are equal, sort by label ascending
            return sCollator.compare(object1.getLabel(), object2.getLabel());
        }
    };

    @Override
    public List<AccountListEntry> loadInBackground() {
        AccountManager manager = AccountManager.get(getContext());
        AuthenticatorDescription[] descriptions = manager.getAuthenticatorTypes();
        Account[] accounts = manager.getAccounts();
        ArrayList<AccountListEntry> entries = new ArrayList<>();

        HashSet<Account> accountBlacklist = ProviderHelper.getAccountBlacklist(getContext());

        for (Account account : accounts) {
            // Don't show the app's own account in the filter list
            if (account.type.startsWith("org.birthdayadapter")) {
                continue;
            }

            for (AuthenticatorDescription description : descriptions) {
                if (description.type.equals(account.type)) {
                    boolean enabled = !accountBlacklist.contains(account);
                    AccountListEntry entry = new AccountListEntry(getContext(), account, description, enabled);
                    loadAccountDetails(entry);
                    entries.add(entry);
                    break; // Found descriptor, no need to look further
                }
            }
        }

        // Sort the list.
        entries.sort(TOTAL_COUNT_COMPARATOR);

        // Done!
        return entries;
    }

    private void loadAccountDetails(AccountListEntry entry) {
        Account account = entry.getAccount();
        ContentResolver resolver = getContext().getContentResolver();

        // 1. Get all groups for the account to map ID to Title
        Map<String, String> groupTitleMap = new HashMap<>();
        final String[] groupProjection = {ContactsContract.Groups._ID, ContactsContract.Groups.TITLE};
        final String groupSelection = ContactsContract.Groups.ACCOUNT_TYPE + " = ? AND " +
                ContactsContract.Groups.ACCOUNT_NAME + " = ? AND " +
                ContactsContract.Groups.DELETED + " = 0";
        final String[] groupSelectionArgs = {account.type, account.name};

        try (Cursor groupCursor = resolver.query(ContactsContract.Groups.CONTENT_URI, groupProjection, groupSelection, groupSelectionArgs, null)) {
            if (groupCursor != null) {
                int idColumn = groupCursor.getColumnIndex(ContactsContract.Groups._ID);
                int titleColumn = groupCursor.getColumnIndex(ContactsContract.Groups.TITLE);
                while (groupCursor.moveToNext()) {
                    String id = groupCursor.getString(idColumn);
                    String title = groupCursor.getString(titleColumn);
                    if (!TextUtils.isEmpty(title) && !title.startsWith("System Group:")) {
                        groupTitleMap.put(id, title);
                    }
                }
            }
        }

        // 2. Get ALL raw contacts for the account. This gives us the definitive total count.
        Set<String> allRawContactIds = new HashSet<>();
        final String[] rawContactsProjection = {ContactsContract.RawContacts._ID};
        final String rawContactsSelection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " + ContactsContract.RawContacts.ACCOUNT_NAME + " = ?";
        final String[] rawContactsSelectionArgs = {account.type, account.name};
        try (Cursor rawContactsCursor = resolver.query(ContactsContract.RawContacts.CONTENT_URI, rawContactsProjection, rawContactsSelection, rawContactsSelectionArgs, null)) {
            if (rawContactsCursor != null) {
                int idColumn = rawContactsCursor.getColumnIndex(ContactsContract.RawContacts._ID);
                while (rawContactsCursor.moveToNext()) {
                    allRawContactIds.add(rawContactsCursor.getString(idColumn));
                }
            }
        }
        entry.setContactCount(allRawContactIds.size());


        // 3. Query for details (Events and Group Memberships)
        final String[] dataProjection = {
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
        };
        final String dataSelection = ContactsContract.RawContacts.ACCOUNT_TYPE + " = ? AND " +
                ContactsContract.RawContacts.ACCOUNT_NAME + " = ? AND " +
                "(" + ContactsContract.Data.MIMETYPE + " = ? OR " +
                ContactsContract.Data.MIMETYPE + " = ?)";
        final String[] dataSelectionArgs = {
                account.type,
                account.name,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
        };

        Map<String, Set<String>> contactToGroupsMap = new HashMap<>(); // RawContactID -> Set<GroupID>
        Map<String, Integer> contactDateCounts = new HashMap<>(); // RawContactID -> Number of dates

        try (Cursor dataCursor = resolver.query(ContactsContract.Data.CONTENT_URI, dataProjection, dataSelection, dataSelectionArgs, null)) {
            if (dataCursor == null) {
                entry.setDateCount(0);
                return;
            }
            int rawContactIdCol = dataCursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
            int mimeTypeCol = dataCursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
            int groupIdCol = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID);

            while (dataCursor.moveToNext()) {
                String rawContactId = dataCursor.getString(rawContactIdCol);
                String mimeType = dataCursor.getString(mimeTypeCol);

                if (ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    String groupId = dataCursor.getString(groupIdCol);
                    contactToGroupsMap.computeIfAbsent(rawContactId, k -> new HashSet<>()).add(groupId);
                } else if (ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    contactDateCounts.put(rawContactId, contactDateCounts.getOrDefault(rawContactId, 0) + 1);
                }
            }
        }
        entry.setDateCount(contactDateCounts.values().stream().mapToInt(Integer::intValue).sum());


        // 4. Aggregate stats for groups
        Map<String, int[]> groupStats = new HashMap<>(); // GroupID -> {contactCount, dateCount}
        String noGroupKey = "-1"; // Special key for contacts in no group

        for (String rawContactId : allRawContactIds) { // Iterate over ALL contacts
            Set<String> groupIds = contactToGroupsMap.get(rawContactId);
            int dateCount = contactDateCounts.getOrDefault(rawContactId, 0);

            if (groupIds != null && !groupIds.isEmpty()) {
                for (String groupId : groupIds) {
                    int[] stats = groupStats.computeIfAbsent(groupId, k -> new int[2]);
                    stats[0]++; // Increment contact count for this group
                    stats[1] += dateCount; // Add this contact's dates to the group's date count
                }
            } else {
                // This contact is not in any group
                int[] stats = groupStats.computeIfAbsent(noGroupKey, k -> new int[2]);
                stats[0]++;
                stats[1] += dateCount;
            }
        }

        // 5. Build the final list for the entry
        List<GroupListEntry> groupEntries = new ArrayList<>();
        for (Map.Entry<String, int[]> statsEntry : groupStats.entrySet()) {
            String groupId = statsEntry.getKey();
            String groupTitle = groupTitleMap.get(groupId);

            if (groupId.equals(noGroupKey)) {
                // Only add the "No Group" entry if it actually contains contacts
                if (statsEntry.getValue()[0] > 0) {
                    groupTitle = getContext().getString(R.string.account_list_no_group);
                }
            }

            if (groupTitle != null) {
                int[] counts = statsEntry.getValue();
                groupEntries.add(new GroupListEntry(groupTitle, counts[0], counts[1]));
            }
        }
        groupEntries.sort(Comparator.comparing(GroupListEntry::getTitle));

        entry.setGroups(groupEntries);
    }


    @Override
    public void deliverResult(List<AccountListEntry> accounts) {
        if (isReset()) {
            if (accounts != null) {
                onReleaseResources();
            }
        }
        mAccounts = accounts;

        if (isStarted()) {
            super.deliverResult(accounts);
        }

        if (accounts != null) {
            onReleaseResources();
        }
    }

    @Override
    protected void onStartLoading() {
        if (mAccounts != null) {
            deliverResult(mAccounts);
        }

        if (takeContentChanged() || mAccounts == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(List<AccountListEntry> apps) {
        super.onCanceled(apps);
        onReleaseResources();
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();

        if (mAccounts != null) {
            onReleaseResources();
            mAccounts = null;
        }
    }

    protected void onReleaseResources() {
    }
}
