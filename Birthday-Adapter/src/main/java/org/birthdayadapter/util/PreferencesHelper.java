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

import java.util.HashSet;

import org.birthdayadapter.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.ContactsContract;

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
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        int result = prefs.getInt(context.getString(R.string.pref_color_key), context
                .getResources().getInteger(R.color.pref_color_def));

        return result;
    }

    @SuppressLint("NewApi")
    public static HashSet<String> getAccountsBlacklist(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        HashSet<String> result = (HashSet<String>) prefs.getStringSet(
                context.getString(R.string.pref_accounts_blacklist_key), new HashSet<String>());

        return result;
    }

    @SuppressLint("NewApi")
    public static void setAccountsBlacklist(Context context, HashSet<String> value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(context.getString(R.string.pref_accounts_blacklist_key), value);
        editor.commit();
    }

    /**
     * Get all reminder minutes from preferences as int array
     *
     * @param context
     * @return
     */
    public static int[] getAllReminderMinutes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);

        // get all reminders
        int[] minutes = new int[3];
        for (int i = 0; i < 3; i++) {
            String keyEnabled = context.getString(R.string.pref_reminder_enable_key) + i;
            boolean enabled = prefs.getBoolean(keyEnabled, false);

            if (enabled) {
                String key = context.getString(R.string.pref_reminder_time_key) + i;
                minutes[i] = prefs.getInt(key,
                        Integer.parseInt(context.getString(R.integer.pref_reminder_time_def)));
            } else {
                minutes[i] = Constants.DISABLED_REMINDER;
            }
        }

        return minutes;
    }

    public static String getLabel(Context context, int eventType, boolean includeAge) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);

        boolean prefLabelsEnabled = prefs.getBoolean(
                context.getString(R.string.pref_title_enable_key), false);

        switch (eventType) {
            case ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM:
                if (!prefLabelsEnabled) {
                    return includeAge ? context.getString(R.string.event_title_custom_with_age)
                            : context.getString(R.string.event_title_custom_without_age);
                } else {
                    return includeAge ? prefs.getString(
                            context.getString(R.string.pref_title_custom_with_age_key),
                            context.getString(R.string.event_title_custom_with_age)) : prefs.getString(
                            context.getString(R.string.pref_title_custom_without_age_key),
                            context.getString(R.string.event_title_custom_without_age));
                }
            case ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY:
                if (!prefLabelsEnabled) {
                    return includeAge ? context.getString(R.string.event_title_anniversary_with_age)
                            : context.getString(R.string.event_title_anniversary_without_age);
                } else {
                    return includeAge ? prefs.getString(
                            context.getString(R.string.pref_title_anniversary_with_age_key),
                            context.getString(R.string.event_title_anniversary_with_age)) : prefs
                            .getString(
                                    context.getString(R.string.pref_title_anniversary_without_age_key),
                                    context.getString(R.string.event_title_anniversary_without_age));
                }
            case ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY:
                if (!prefLabelsEnabled) {
                    return includeAge ? context.getString(R.string.event_title_birthday_with_age)
                            : context.getString(R.string.event_title_birthday_without_age);
                } else {
                    return includeAge ? prefs.getString(
                            context.getString(R.string.pref_title_birthday_with_age_key),
                            context.getString(R.string.event_title_birthday_with_age)) : prefs
                            .getString(context.getString(R.string.pref_title_birthday_without_age_key),
                                    context.getString(R.string.event_title_birthday_without_age));
                }
            default:
                // also ContactsContract.CommonDataKinds.Event.TYPE_OTHER
                if (!prefLabelsEnabled) {
                    return includeAge ? context.getString(R.string.event_title_other_with_age)
                            : context.getString(R.string.event_title_other_without_age);
                } else {
                    return includeAge ? prefs.getString(
                            context.getString(R.string.pref_title_other_with_age_key),
                            context.getString(R.string.event_title_other_with_age)) : prefs.getString(
                            context.getString(R.string.pref_title_other_without_age_key),
                            context.getString(R.string.event_title_other_without_age));
                }
        }
    }

    public static boolean getPreferddSlashMM(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        return prefs.getBoolean(context.getString(R.string.pref_prefer_dd_slash_mm_key),
                Boolean.parseBoolean(context.getString(R.string.pref_prefer_dd_slash_mm_def)));
    }

}