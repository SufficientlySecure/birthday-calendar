package org.birthdayadapter.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.birthdayadapter.util.PreferencesHelper;
import org.jetbrains.annotations.Nullable;

public class PreferencesFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private AccountHelper mAccountHelper;

    private Preference mForceSyncPref;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAccountHelper = new AccountHelper(getActivity());
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.pref_preferences);

        // open contact app
        Preference openContactsPref = findPreference(getString(R.string.pref_contacts_key));
        openContactsPref.setOnPreferenceClickListener(this);

        mForceSyncPref = findPreference(getString(R.string.pref_force_sync_key));
        mForceSyncPref.setOnPreferenceClickListener(this);

        Preference colorPref = findPreference(getString(R.string.pref_color_key));
        colorPref.setOnPreferenceClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // register listener
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // unregister listener
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals(getString(R.string.pref_contacts_key))) {
            // open contacts here
            Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
            startActivity(intent);

            return true;
        } else if (preference.getKey().equals(getString(R.string.pref_force_sync_key))) {
            mAccountHelper.manualSync();
            return true;
        } else if (preference.getKey().equals(getString(R.string.pref_color_key))) {
            // open color picker
            ((ColorChangedListener) getActivity()).showColorPickerDialog(PreferencesHelper.getColor(getActivity()));
        }

        return false;
    }

    public interface ColorChangedListener {
        void showColorPickerDialog(int currentColor);
    }

}
