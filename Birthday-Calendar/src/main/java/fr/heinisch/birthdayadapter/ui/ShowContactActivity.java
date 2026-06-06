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
        
        // Handle "Open in App" button from calendar
        if (extras != null && extras.containsKey(CalendarContract.EXTRA_CUSTOM_APP_URI)) {
            uri = Uri.parse(extras.getString(CalendarContract.EXTRA_CUSTOM_APP_URI));
        } else if (getIntent().getData() != null) {
            Uri data = getIntent().getData();
            String key = data.getQueryParameter("key");

            if (key != null && !key.isEmpty() && !key.equals("index.html") && !key.equals("contact")) {
                // Check if it's our internal short ID (numeric)
                if (key.matches("\\d+")) {
                    String resolvedLookupKey = resolveInternalId(key);
                    if (resolvedLookupKey != null) {
                        key = resolvedLookupKey;
                    }
                }
                
                uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, key);
            }
        }

        if (uri != null) {
            Log.d(Constants.TAG, "Showing contact for Uri: " + uri);
            QuickContact.showQuickContact(this, getIntent().getSourceBounds(), uri,
                    QuickContact.MODE_LARGE, null);
        } else {
            Log.e(Constants.TAG, "No valid contact lookup key found in intent!");
        }

        finish();
    }

    /**
     * Resolves our internal short ID back to the Android Lookup Key
     */
    private String resolveInternalId(String id) {
        Uri uri = BirthdayAdapterContract.ContactMapping.buildUri(id);
        try (Cursor c = getContentResolver().query(uri, 
                new String[]{BirthdayAdapterContract.ContactMappingColumns.LOOKUP_KEY}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error resolving internal ID: " + id, e);
        }
        return null;
    }

}
