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

import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TimePicker;

public class ReminderPreference extends DialogPreference {
    private int lastMinutes = 0;
    private TimePicker picker = null;
    private Spinner spinner = null;

    private static final int ONE_DAY_MINUTES = 24 * 60;
    private static final int[] DAY_BASE_MINUTES = { -ONE_DAY_MINUTES, 0, ONE_DAY_MINUTES,
            2 * ONE_DAY_MINUTES };

    public ReminderPreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);

        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    @Override
    protected View onCreateDialogView() {
        // inflate view
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.pref_reminder, null);
        spinner = (Spinner) view.findViewById(R.id.pref_reminder_spinner);
        picker = (TimePicker) view.findViewById(R.id.pref_reminder_timepicker);

        // populate spinner with entries
        ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.pref_reminder_time_drop_down, android.R.layout.simple_spinner_item);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);

        // set 24h format of date picker based on Android's preference
        if (DateFormat.is24HourFormat(getContext())) {
            picker.setIs24HourView(true);
        }

        return (view);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);

        // select day of reminder based on DAY_BASE_MINUTES
        int daySelection = 0;
        for (int i = 0; i < DAY_BASE_MINUTES.length; i++) {
            if (lastMinutes >= DAY_BASE_MINUTES[i]) {
                daySelection = i;
            }
        }
        spinner.setSelection(daySelection);
        int dayMinutes = ONE_DAY_MINUTES - (lastMinutes - DAY_BASE_MINUTES[daySelection]);
        Log.d(Constants.TAG, "dayMinutes: " + dayMinutes);

        // select day minutes for this specific day
        int hour = dayMinutes / 60;
        int minute = dayMinutes % 60;
        picker.setCurrentHour(hour);
        picker.setCurrentMinute(minute);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            int dayBase = DAY_BASE_MINUTES[spinner.getSelectedItemPosition()];
            int dayTime = picker.getCurrentMinute() + picker.getCurrentHour() * 60;

            lastMinutes = dayBase + ONE_DAY_MINUTES - dayTime;

            if (callChangeListener(lastMinutes)) {
                Log.d(Constants.TAG, "persist time: " + lastMinutes);

                persistInt(lastMinutes);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        // convert default to Integer
        Integer defaultInt = null;
        if (defaultValue == null) {
            defaultInt = 0;
        } else if (defaultValue instanceof Number) {
            defaultInt = (Integer) defaultValue;
        } else {
            defaultInt = Integer.valueOf(defaultValue.toString());
        }

        Integer time = null;
        if (restoreValue) {
            time = getPersistedInt(defaultInt);
        } else {
            time = defaultInt;
        }

        lastMinutes = time;
    }
}