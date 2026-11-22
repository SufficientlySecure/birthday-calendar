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

package fr.heinisch.birthdayadapter.ui

import android.content.Context
import android.text.format.DateFormat
import fr.heinisch.birthdayadapter.R
import java.util.Calendar
import kotlin.math.abs

object ReminderPreferenceCompat {
    private const val ONE_DAY_MINUTES = 24 * 60
    private val DAY_BASE_VALUES = intArrayOf(0, 1, 2, 3, 5, 7, 10, 14)

    fun getSummary(context: Context, minutes: Int): String {
        val day = (minutes + ONE_DAY_MINUTES) / ONE_DAY_MINUTES
        val adjustedDay = if (minutes % ONE_DAY_MINUTES == 0) day - 1 else day
        var daySelection = 0
        for (i in DAY_BASE_VALUES.indices) {
            if (adjustedDay == DAY_BASE_VALUES[i]) {
                daySelection = i
                break
            }
        }
        val dayString = context.resources.getStringArray(R.array.pref_reminder_time_drop_down)[daySelection]

        val timeFromMinutes = abs(minutes - (adjustedDay * ONE_DAY_MINUTES))
        val hour = timeFromMinutes / 60
        val minute = timeFromMinutes % 60

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val timeString = DateFormat.getTimeFormat(context).format(cal.time)

        return context.getString(R.string.pref_reminder_summary, dayString, timeString)
    }
}
