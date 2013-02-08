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

import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract.QuickContact;

/*
 * Uri is built in CalendarSyncAdapterService.insertEvent() and looks like Uri
 * contactLookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
 * lookupKey);
 * 
 * code related to the buton is here:
 * https://github.com/CyanogenMod/android_packages_apps_Calendar
 * /blob/jellybean-stable/src/com/android/calendar/EventInfoFragment.java in
 * updateCustomAppButton()
 * 
 * Label of button can not be set!
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ShowContactActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey(CalendarContract.EXTRA_CUSTOM_APP_URI)) {
            Uri uri = Uri.parse(extras.getString(CalendarContract.EXTRA_CUSTOM_APP_URI));
            Log.d(Constants.TAG, "Uri: " + uri);

            // Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            // viewIntent.setData(uri);
            // startActivity(viewIntent);

            QuickContact.showQuickContact(this, getIntent().getSourceBounds(), uri,
                    QuickContact.MODE_LARGE, null);

            finish();
        } else {
            Log.e(Constants.TAG, "getIntent().getData() is null!");
            finish();
        }
    }

}
