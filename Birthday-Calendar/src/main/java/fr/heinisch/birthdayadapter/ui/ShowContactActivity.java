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

import fr.heinisch.birthdayadapter.util.Constants;
import fr.heinisch.birthdayadapter.util.Log;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;

/*
 * Uri is built in CalendarSyncAdapterService.insertEvent() and looks like Uri
 * contactLookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
 * lookupKey);
 * 
 * Code related to the button is here:
 * https://github.com/CyanogenMod/android_packages_apps_Calendar
 * /blob/jellybean-stable/src/com/android/calendar/EventInfoFragment.java in
 * updateCustomAppButton()
 * 
 * Label of button can not be set!
 */
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
            String lookupKey = null;

            // Handle custom schemes: birthdayadapter://contact/LOOKUP_KEY or androidapp://contact/LOOKUP_KEY
            if ("birthdayadapter".equals(data.getScheme()) || "androidapp".equals(data.getScheme())) {
                lookupKey = data.getLastPathSegment();
            } 
            // Handle https landing page: https://birthdayadapter.heinisch.fr/contact/index.html?key=LOOKUP_KEY
            else if ("https".equals(data.getScheme()) || "http".equals(data.getScheme())) {
                lookupKey = data.getQueryParameter("key");
                // Fallback for old path-based links
                if (lookupKey == null) {
                    lookupKey = data.getLastPathSegment();
                }
            }

            if (lookupKey != null && !lookupKey.isEmpty() && !lookupKey.equals("index.html")) {
                uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
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

}
