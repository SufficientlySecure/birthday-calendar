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

import org.birthdayadapter.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

public class PreferencesHelper {
    public static boolean getFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_first_run_key),
                Boolean.parseBoolean(context.getString(R.string.pref_first_run_def)));
    }

    public static void setFirstRun(Context context, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.pref_first_run_key), value);
        editor.commit();
    }

    public static int getColor(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        int result = prefs.getInt(context.getString(R.string.pref_color_key),
                res.getInteger(R.color.pref_color_def));

        return result;
    }

    public static int getReminder(Context context, int reminderNo) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        String key = context.getString(R.string.pref_reminder_key) + reminderNo;
        String reminderStr = prefs.getString(key, context.getString(R.string.pref_reminder_def));

        Log.d(Constants.TAG, "Reminder minutes in prefs: " + reminderStr);

        return Integer.valueOf(reminderStr);
    }

    public static boolean getPreferddSlashMM(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_prefer_dd_slash_mm_key),
                Boolean.parseBoolean(context.getString(R.string.pref_prefer_dd_slash_mm_def)));
    }

}