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

package fr.heinisch.birthdayadapter.util;

import static fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import fr.heinisch.birthdayadapter.R;

public class PreferencesHelper {
    public static int getColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getInt(context.getString(R.string.pref_color_key), 
                ContextCompat.getColor(context, R.color.pref_color_def));
    }

    /**
     * Get all reminder minutes from preferences as int array
     */
    public static int[] getAllReminderMinutes(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (!isFullVersionUnlocked(context)) {
            // Free version: always has one default reminder for notifications.
            return new int[]{ context.getResources().getInteger(R.integer.pref_reminder_time_def) };
        }

        // Full version:
        Set<String> reminderSet = prefs.getStringSet(context.getString(R.string.pref_reminders_key), null);

        // If preferences for reminders have never been saved (e.g., new full user or just upgraded),
        // provide the default reminder as a starting point.
        if (reminderSet == null) {
            return new int[]{ context.getResources().getInteger(R.integer.pref_reminder_time_def) };
        }

        // If preferences exist but are empty (user deleted all reminders), return empty.
        if (reminderSet.isEmpty()) {
            return new int[0];
        }
        
        Integer[] minutes = new Integer[reminderSet.size()];
        int i = 0;
        for (String minuteStr : reminderSet) {
            minutes[i++] = Integer.parseInt(minuteStr);
        }

        final int ONE_DAY_MINUTES = 24 * 60;
        Arrays.sort(minutes, (m1, m2) -> {
            // Day extraction logic from ReminderPreferenceCompat
            int d1 = (m1 + ONE_DAY_MINUTES) / ONE_DAY_MINUTES;
            if (m1 % ONE_DAY_MINUTES == 0) d1--;
            int d2 = (m2 + ONE_DAY_MINUTES) / ONE_DAY_MINUTES;
            if (m2 % ONE_DAY_MINUTES == 0) d2--;

            if (d1 != d2) {
                // sort by day ascending
                return Integer.compare(d1, d2);
            }

            // Time of day extraction logic from ReminderPreferenceCompat
            int time1 = Math.abs(m1 - (d1 * ONE_DAY_MINUTES));
            int time2 = Math.abs(m2 - (d2 * ONE_DAY_MINUTES));
            // sort by time of day ascending
            return Integer.compare(time1, time2);
        });
        
        int[] result = new int[minutes.length];
        for (i = 0; i < minutes.length; i++) {
            result[i] = minutes[i];
        }
        return result;
    }

    public static Set<String> getReminderEventTypes(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> defaultValues = new HashSet<>(Arrays.asList(
                String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY),
                String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY),
                String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_OTHER),
                String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM)
        ));
        return prefs.getStringSet(context.getString(R.string.pref_reminder_event_types), defaultValues);
    }

    public static String getLabel(Context context, int eventType, boolean includeAge) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean prefLabelsEnabled = prefs.getBoolean(
                context.getString(R.string.pref_title_enable_key), context.getResources().getBoolean(R.bool.pref_title_enable_def));

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

    public static boolean getPreferDDSlashMM(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.pref_prefer_dd_slash_mm_key),
                context.getResources().getBoolean(R.bool.pref_prefer_dd_slash_mm_def));
    }

    public static String getJubileeYears(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(context.getString(R.string.pref_jubilee_years_key),
                context.getString(R.string.pref_jubilee_years_def));
    }

}
