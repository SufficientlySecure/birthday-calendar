/*
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

package org.birthdayadapter.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TimePicker;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Log;

public class ReminderPreferenceCompat extends Preference {

    private int lastMinutes = 0;
    private TimePicker picker = null;
    private Spinner spinner = null;

    private static final int ONE_DAY_MINUTES = 24 * 60;
    private static final int[] DAY_BASE_VALUES = {0, 1, 2, 3, 5, 7, 10, 14};

    public ReminderPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(preference -> {
            click();
            return true;
        });
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        lastMinutes = getPersistedInt(defaultValue != null ? (Integer) defaultValue : 0);
    }

    private void click() {
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

        LayoutInflater inflater = LayoutInflater.from(getContext());
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.pref_reminder, null);
        alert.setView(view);

        spinner = view.findViewById(R.id.pref_reminder_spinner);
        picker = view.findViewById(R.id.pref_reminder_timepicker);

        ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.pref_reminder_time_drop_down, android.R.layout.simple_spinner_item);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);

        if (DateFormat.is24HourFormat(getContext())) {
            picker.setIs24HourView(true);
        }

        alert.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> save());
        alert.setNegativeButton(android.R.string.cancel, null);

        bind();

        alert.create().show();
    }

    private void bind() {
        // reminder on the day after midnight are negative, so we add one day for the calculation
        int day = (lastMinutes + ONE_DAY_MINUTES) / ONE_DAY_MINUTES;
        if (lastMinutes % ONE_DAY_MINUTES == 0) day--;
        int daySelection = 0;
        for (int i = 0; i < DAY_BASE_VALUES.length; i++) {
            if (day == DAY_BASE_VALUES[i]) {
                daySelection = i;
                break;
            }
        }
        spinner.setSelection(daySelection);

        // reminders are negative minutes from the event, let's calculate the time from that
        int timeFromMinutes = Math.abs(lastMinutes - (day * ONE_DAY_MINUTES));
        int hour = timeFromMinutes / 60;
        int minute = timeFromMinutes % 60;

        picker.setHour(hour);
        picker.setMinute(minute);
    }

    private void save() {
        int hour = picker.getHour();
        int minute = picker.getMinute();

        int selectedDayValue = DAY_BASE_VALUES[spinner.getSelectedItemPosition()];
        int minutes = (selectedDayValue * ONE_DAY_MINUTES) - (hour * 60) - minute;

        if (callChangeListener(minutes)) {
            Log.d("BirthdayAdapter", "Persisting reminder minutes: " + minutes);
            persistInt(minutes);
            lastMinutes = minutes;
        }
    }
}
