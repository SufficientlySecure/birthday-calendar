package org.birthdayadapter.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
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

public class ReminderPreferenceFragmentDialog extends DialogFragment {

    OnTimeSelectedListener listener;

    public interface OnTimeSelectedListener {
        void onTimeSelected(int time);
    }

    private int lastMinutes = 0;
    private TimePicker picker = null;
    private Spinner spinner = null;

    private static final int ONE_DAY_MINUTES = 24 * 60;
    private static final int[] DAY_BASE_MINUTES = {-ONE_DAY_MINUTES, 0, ONE_DAY_MINUTES,
            2 * ONE_DAY_MINUTES, 4 * ONE_DAY_MINUTES, 6 * ONE_DAY_MINUTES, 9 * ONE_DAY_MINUTES,
            13 * ONE_DAY_MINUTES};

    public ReminderPreferenceFragmentDialog() {
        super();
    }

    public void setListener(OnTimeSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
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

        return alert.show();
    }


    protected void bind() {
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

    protected void save(boolean positiveResult) {

        if (positiveResult) {
            int dayBase = DAY_BASE_MINUTES[spinner.getSelectedItemPosition()];
            int dayTime = picker.getCurrentMinute() + picker.getCurrentHour() * 60;

            lastMinutes = dayBase + ONE_DAY_MINUTES - dayTime;

            listener.onTimeSelected(lastMinutes);

//            if (callChangeListener(lastMinutes)) {
//                Log.d(Constants.TAG, "persist time: " + lastMinutes);
//
//                persistInt(lastMinutes);
//            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
    }


}
