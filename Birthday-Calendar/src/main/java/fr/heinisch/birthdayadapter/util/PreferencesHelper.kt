/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
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

package fr.heinisch.birthdayadapter.util

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked
import java.util.HashSet

object PreferencesHelper {

    @JvmStatic
    fun getColor(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(
            context.getString(R.string.pref_color_key),
            ContextCompat.getColor(context, R.color.pref_color_def)
        )
    }

    /**
     * Get all reminder minutes from preferences as int array
     */
    @JvmStatic
    fun getAllReminderMinutes(context: Context): IntArray {
        return getReminderMinutes(
            context,
            context.getString(R.string.pref_reminders_key),
            context.resources.getInteger(R.integer.pref_reminder_time_def)
        )
    }

    /**
     * Get additional reminder minutes for a specific group from preferences as int array
     */
    @JvmStatic
    fun getAdditionalReminderMinutes(context: Context): IntArray {
        return getReminderMinutes(
            context,
            context.getString(R.string.pref_additional_reminders_key),
            context.resources.getInteger(R.integer.pref_additional_reminder_time_def)
        )
    }

    private fun getReminderMinutes(context: Context, prefKey: String, defaultValue: Int): IntArray {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (!isFullVersionUnlocked(context)) {
            // Free version: always has one default reminder for notifications.
            return intArrayOf(context.resources.getInteger(R.integer.pref_reminder_time_def))
        }

        // Full version:
        val reminderSet = prefs.getStringSet(prefKey, null)

        // If preferences for reminders have never been saved (e.g., new full user or just upgraded),
        // provide the default reminder as a starting point.
        if (reminderSet == null) {
            return intArrayOf(defaultValue)
        }

        // If preferences exist but are empty (user deleted all reminders), return empty.
        if (reminderSet.isEmpty()) {
            return intArrayOf()
        }

        val oneDayMinutes = 24 * 60
        return reminderSet.map { it.toInt() }.sortedWith { m1, m2 ->
            var d1 = (m1 + oneDayMinutes) / oneDayMinutes
            if (m1 % oneDayMinutes == 0) d1--
            var d2 = (m2 + oneDayMinutes) / oneDayMinutes
            if (m2 % oneDayMinutes == 0) d2--

            if (d1 != d2) {
                d1.compareTo(d2)
            } else {
                val time1 = Math.abs(m1 - (d1 * oneDayMinutes))
                val time2 = Math.abs(m2 - (d2 * oneDayMinutes))
                time1.compareTo(time2)
            }
        }.toIntArray()
    }

    @JvmStatic
    fun getAdditionalRemindersGroupName(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(
            context.getString(R.string.pref_additional_reminders_group_key),
            context.getString(R.string.pref_additional_reminders_group_def)
        )
    }

    @JvmStatic
    fun getReminderEventTypes(context: Context): MutableSet<String>? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultValues = HashSet(
            listOf(
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
                ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY.toString(),
                ContactsContract.CommonDataKinds.Event.TYPE_OTHER.toString(),
                ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM.toString()
            )
        )
        return prefs.getStringSet(context.getString(R.string.pref_reminder_event_types), defaultValues)
    }

    @JvmStatic
    fun getLabel(context: Context, eventType: Int, includeAge: Boolean): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val prefLabelsEnabled = prefs.getBoolean(
            context.getString(R.string.pref_title_enable_key),
            context.resources.getBoolean(R.bool.pref_title_enable_def)
        )

        return when (eventType) {
            ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM -> {
                if (!prefLabelsEnabled) {
                    if (includeAge) context.getString(R.string.event_title_custom_with_age)
                    else context.getString(R.string.event_title_custom_without_age)
                } else {
                    if (includeAge) {
                        prefs.getString(
                            context.getString(R.string.pref_title_custom_with_age_key),
                            context.getString(R.string.event_title_custom_with_age)
                        ) ?: context.getString(R.string.event_title_custom_with_age)
                    } else {
                        prefs.getString(
                            context.getString(R.string.pref_title_custom_without_age_key),
                            context.getString(R.string.event_title_custom_without_age)
                        ) ?: context.getString(R.string.event_title_custom_without_age)
                    }
                }
            }
            ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> {
                if (!prefLabelsEnabled) {
                    if (includeAge) context.getString(R.string.event_title_anniversary_with_age)
                    else context.getString(R.string.event_title_anniversary_without_age)
                } else {
                    if (includeAge) {
                        prefs.getString(
                            context.getString(R.string.pref_title_anniversary_with_age_key),
                            context.getString(R.string.event_title_anniversary_with_age)
                        ) ?: context.getString(R.string.event_title_anniversary_with_age)
                    } else {
                        prefs.getString(
                            context.getString(R.string.pref_title_anniversary_without_age_key),
                            context.getString(R.string.event_title_anniversary_without_age)
                        ) ?: context.getString(R.string.event_title_anniversary_without_age)
                    }
                }
            }
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> {
                if (!prefLabelsEnabled) {
                    if (includeAge) context.getString(R.string.event_title_birthday_with_age)
                    else context.getString(R.string.event_title_birthday_without_age)
                } else {
                    if (includeAge) {
                        prefs.getString(
                            context.getString(R.string.pref_title_birthday_with_age_key),
                            context.getString(R.string.event_title_birthday_with_age)
                        ) ?: context.getString(R.string.event_title_birthday_with_age)
                    } else {
                        prefs.getString(
                            context.getString(R.string.pref_title_birthday_without_age_key),
                            context.getString(R.string.event_title_birthday_without_age)
                        ) ?: context.getString(R.string.event_title_birthday_without_age)
                    }
                }
            }
            else -> {
                if (!prefLabelsEnabled) {
                    if (includeAge) context.getString(R.string.event_title_other_with_age)
                    else context.getString(R.string.event_title_other_without_age)
                } else {
                    if (includeAge) {
                        prefs.getString(
                            context.getString(R.string.pref_title_other_with_age_key),
                            context.getString(R.string.event_title_other_with_age)
                        ) ?: context.getString(R.string.event_title_other_with_age)
                    } else {
                        prefs.getString(
                            context.getString(R.string.pref_title_other_without_age_key),
                            context.getString(R.string.event_title_other_without_age)
                        ) ?: context.getString(R.string.event_title_other_without_age)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun getPreferDDSlashMM(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(
            context.getString(R.string.pref_prefer_dd_slash_mm_key),
            context.resources.getBoolean(R.bool.pref_prefer_dd_slash_mm_def)
        )
    }

    @JvmStatic
    fun getUseLastNameFirst(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(
            context.getString(R.string.pref_name_format_key),
            context.resources.getBoolean(R.bool.pref_name_format_def)
        )
    }

    @JvmStatic
    fun getJubileeYears(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(
            context.getString(R.string.pref_jubilee_years_key),
            context.getString(R.string.pref_jubilee_years_def)
        )
    }
}
