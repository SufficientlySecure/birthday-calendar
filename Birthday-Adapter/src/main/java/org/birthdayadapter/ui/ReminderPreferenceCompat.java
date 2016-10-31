package org.birthdayadapter.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

public class ReminderPreferenceCompat extends DialogPreference implements ReminderPreferenceFragmentDialog.OnTimeSelectedListener {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ReminderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
//        init(attrs);
    }

    public ReminderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        init(attrs);
    }

    public ReminderPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
//        init(attrs);
    }

    public ReminderPreferenceCompat(Context context) {
        super(context);
//        init(null);
    }



//    @Override
//    public void onDisplayPreferenceDialog(Preference preference) {
//        DialogFragment fragment;
//        if (preference instanceof LocationChooserDialog) {
//            fragment = LocationChooserFragmentCompat.newInstance(preference);
//            fragment.setTargetFragment(this, 0);
//            fragment.show(getFragmentManager(),
//                    "android.support.v7.preference.PreferenceFragment.DIALOG");
//        } else super.onDisplayPreferenceDialog(preference);
//    }

    @Override
    protected void onClick() {
        super.onClick();
        ReminderPreferenceFragmentDialog fragment = new ReminderPreferenceFragmentDialog();
        fragment.setListener(this);
//        fragment.show(getFragment(),
//                "android.support.v7.preference.PreferenceFragment.DIALOG");
//        fragment.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "reminder");
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

//        lastMinutes = time;
    }

    @Override
    public void onTimeSelected(int time) {
        persistInt(time);
    }
}
