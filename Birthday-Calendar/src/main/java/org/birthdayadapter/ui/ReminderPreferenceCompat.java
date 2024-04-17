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
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Build;
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
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

public class ReminderPreferenceCompat extends Preference {

    private int lastMinutes = 0;
    private TimePicker picker = null;
    private Spinner spinner = null;

    private static final int ONE_DAY_MINUTES = 24 * 60;
    private static final int[] DAY_BASE_MINUTES = {-ONE_DAY_MINUTES, 0, ONE_DAY_MINUTES,
            2 * ONE_DAY_MINUTES, 4 * ONE_DAY_MINUTES, 6 * ONE_DAY_MINUTES, 9 * ONE_DAY_MINUTES,
            13 * ONE_DAY_MINUTES};

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ReminderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ReminderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ReminderPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReminderPreferenceCompat(Context context) {
        super(context);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                click();
                return true;
            }
        });
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // The default value is an integer resource, so we should read it as an integer.
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // The 'defaultValue' is the value from the XML, which onGetDefaultValue now returns as an Integer.
        // We retrieve the persisted value, falling back to this default if no value has been persisted yet.
        lastMinutes = getPersistedInt(defaultValue != null ? (Integer) defaultValue : 0);
    }

    private void click() {
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

        LayoutInflater inflater = LayoutInflater.from(getContext());
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.pref_reminder, null);
        alert.setView(view);

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

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                save(true);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        bind();

        alert.create().show();
    }

    private void bind() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            picker.setHour(hour);
            picker.setMinute(minute);
        } else {
            picker.setCurrentHour(hour);
            picker.setCurrentMinute(minute);
        }
    }

    private void save(boolean positiveResult) {
        if (positiveResult) {
            int hour;
            int minute;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = picker.getHour();
                minute = picker.getMinute();
            } else {
                hour = picker.getCurrentHour();
                minute = picker.getCurrentMinute();
            }

            int dayBase = DAY_BASE_MINUTES[spinner.getSelectedItemPosition()];
            int dayTime = minute + hour * 60;

            lastMinutes = dayBase + ONE_DAY_MINUTES - dayTime;

            if (callChangeListener(lastMinutes)) {
                Log.d(Constants.TAG, "persist time: " + lastMinutes);

                persistInt(lastMinutes);
            }
        }
    }

}
