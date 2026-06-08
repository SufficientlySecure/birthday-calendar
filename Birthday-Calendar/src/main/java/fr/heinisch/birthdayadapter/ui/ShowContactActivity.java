/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
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

package fr.heinisch.birthdayadapter.ui;

import fr.heinisch.birthdayadapter.provider.BirthdayAdapterContract;
import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.Log;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;

public class ShowContactActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = null;
        Bundle extras = getIntent().getExtras();
        
        // 1. Handle "Open in App" button from calendar
        if (extras != null && extras.containsKey(CalendarContract.EXTRA_CUSTOM_APP_URI)) {
            String customAppUri = extras.getString(CalendarContract.EXTRA_CUSTOM_APP_URI);
            Log.d(Constants.TAG, "Handling EXTRA_CUSTOM_APP_URI: " + customAppUri);
            if (customAppUri != null) {
                Uri rawUri = Uri.parse(customAppUri);
                // If it's a contact lookup URI, try to heal it using our logic
                if (rawUri.toString().contains(ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString())) {
                    String lookupKey = rawUri.getLastPathSegment();
                    uri = resolveContactUri(lookupKey);
                } else {
                    uri = rawUri;
                }
            }
        } 
        // 2. Handle links from event description
        else if (getIntent().getData() != null) {
            Uri data = getIntent().getData();
            Log.d(Constants.TAG, "Handling Intent Data: " + data);
            String key = data.getQueryParameter("key");

            if (key != null && !key.isEmpty() && !key.equals("index.html") && !key.equals("contact")) {
                // Resolve mapping if it's an internal short ID
                if (key.matches("\\d+")) {
                    String resolved = resolveInternalId(key);
                    if (resolved != null) {
                        key = resolved;
                    }
                }
                uri = resolveContactUri(key);
            }
        }

        if (uri != null) {
            Log.d(Constants.TAG, "Final URI for QuickContact: " + uri);
            try {
                QuickContact.showQuickContact(this, getIntent().getSourceBounds(), uri,
                        QuickContact.MODE_LARGE, null);
            } catch (Exception e) {
                Log.e(Constants.TAG, "Error showing QuickContact", e);
            }
        } else {
            Log.e(Constants.TAG, "No valid contact found!");
        }

        finish();
    }

    /**
     * Tries to find the best possible URI for a contact.
     * If the direct lookup via Key fails, it performs a fallback search in the Data table.
     */
    private Uri resolveContactUri(String lookupKey) {
        Uri baseLookupUri = ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon()
                .appendPath(lookupKey)
                .build();
        
        // Strategy A: Direct lookup
        try (Cursor c = getContentResolver().query(baseLookupUri, 
                new String[]{ContactsContract.Contacts._ID}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                long contactId = c.getLong(0);
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            }
        } catch (Exception ignored) {}

        // Strategy B: Fallback via Data table (useful for complex/stale keys)
        Log.d(Constants.TAG, "Direct lookup failed, trying fallback for: " + lookupKey);
        try (Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data.CONTACT_ID},
                ContactsContract.Data.LOOKUP_KEY + " = ?",
                new String[]{lookupKey}, null)) {
            if (c != null && c.moveToFirst()) {
                long contactId = c.getLong(0);
                Log.d(Constants.TAG, "Fallback successful! Found ID: " + contactId);
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Fallback failed", e);
        }

        return baseLookupUri; // Return original as last resort
    }

    private String resolveInternalId(String id) {
        Uri uri = BirthdayAdapterContract.ContactMapping.buildUri(id);
        try (Cursor c = getContentResolver().query(uri, 
                new String[]{BirthdayAdapterContract.ContactMappingColumns.LOOKUP_KEY}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error resolving ID: " + id, e);
        }
        return null;
    }
}
