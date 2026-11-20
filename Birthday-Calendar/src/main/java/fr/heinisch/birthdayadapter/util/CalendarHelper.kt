/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
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

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.BaseColumns
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import fr.heinisch.birthdayadapter.BuildConfig
import fr.heinisch.birthdayadapter.R
import java.util.ArrayList

object CalendarHelper {

    /**
     * Gets calendar id, when no calendar is present, create one!
     */
    @JvmStatic
    fun getCalendar(context: Context): Long {
        Log.d(Constants.TAG, "getCalendar Method...")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(Constants.TAG, "Missing calendar permissions to get or create calendar!")
            return -1
        }

        val contentResolver = context.contentResolver
        val calenderUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI)

        contentResolver.query(
            calenderUri,
            arrayOf(BaseColumns._ID),
            CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND " + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
            arrayOf(Constants.getAccountName(context), context.getString(R.string.account_type)),
            null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                return cursor.getLong(0)
            }
        }

        // Create calendar if not found
        val operationList = ArrayList<ContentProviderOperation>()
        val builder = ContentProviderOperation.newInsert(calenderUri)
        builder.withValue(CalendarContract.Calendars.ACCOUNT_NAME, Constants.getAccountName(context))
        builder.withValue(CalendarContract.Calendars.ACCOUNT_TYPE, context.getString(R.string.account_type))
        val calendarColumnName = "birthday_adapter"
        builder.withValue(CalendarContract.Calendars.NAME, calendarColumnName)
        builder.withValue(
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            context.getString(R.string.calendar_display_name)
        )
        builder.withValue(CalendarContract.Calendars.CALENDAR_COLOR, PreferencesHelper.getColor(context))
        if (BuildConfig.DEBUG) {
            builder.withValue(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_EDITOR)
        } else {
            builder.withValue(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
        }
        builder.withValue(CalendarContract.Calendars.OWNER_ACCOUNT, Constants.getAccountName(context))
        builder.withValue(CalendarContract.Calendars.SYNC_EVENTS, 1)
        builder.withValue(CalendarContract.Calendars.VISIBLE, 1)
        operationList.add(builder.build())

        return try {
            val results = contentResolver.applyBatch(CalendarContract.AUTHORITY, operationList)
            if (results.isNotEmpty()) {
                checkNotNull(results[0].uri)
                ContentUris.parseId(results[0].uri!!)
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "getCalendar() failed", e)
            -1
        }
    }

    /**
     * Deletes the birthday calendar and all its events.
     */
    @JvmStatic
    fun deleteCalendar(context: Context) {
        Log.d(Constants.TAG, "Deleting birthday calendar...")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to delete calendar!")
            return
        }

        val contentResolver = context.contentResolver
        val calendarUri = getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI)
        val deletedRows = contentResolver.delete(calendarUri, null, null)

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully deleted birthday calendar.")
        } else {
            Log.w(Constants.TAG, "Birthday calendar not found or could not be deleted.")
        }
    }

    /**
     * Deletes all events from the birthday calendar.
     */
    @JvmStatic
    fun clearAllEvents(context: Context) {
        Log.d(Constants.TAG, "Clearing all events from birthday calendar...")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Log.e(Constants.TAG, "Missing calendar permissions to clear events!")
            return
        }

        val contentResolver = context.contentResolver
        val eventsUri = getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI)
        val deletedRows = contentResolver.delete(eventsUri, "1", null)

        if (deletedRows > 0) {
            Log.i(Constants.TAG, "Successfully cleared $deletedRows old events.")
        } else {
            Log.d(Constants.TAG, "Calendar was already empty. No events to clear.")
        }
    }

    /**
     * Builds URI for Birthday Adapter based on account. Ensures that only the calendar of Birthday
     * Adapter is chosen.
     */
    @JvmStatic
    fun getBirthdayAdapterUri(context: Context, uri: Uri): Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, Constants.getAccountName(context))
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, context.getString(R.string.account_type))
            .build()
    }
}
