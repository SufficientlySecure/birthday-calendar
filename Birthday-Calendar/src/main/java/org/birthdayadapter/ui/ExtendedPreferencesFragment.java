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

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorPalette;
import com.afollestad.materialdialogs.color.DialogColorChooserExtKt;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;

import kotlin.Unit;

public class ExtendedPreferencesFragment extends PreferenceFragmentCompat {

    BaseActivity mActivity;
    private AccountHelper mAccountHelper;
    private Preference colorPref;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // Only load preferences here. Do not touch the Activity or Views.
        getPreferenceManager().setSharedPreferencesName(Constants.PREFS_NAME);
        addPreferencesFromResource(R.xml.pref_preferences);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // All logic that requires a context/activity must go here, AFTER the view is created.
        mActivity = (BaseActivity) getActivity();
        if (mActivity == null) {
            // This should not happen, but as a safeguard.
            return;
        }

        mAccountHelper = new AccountHelper(mActivity);

        if (!BuildConfig.FULL_VERSION) {
            Preference buyFull = findPreference(getString(R.string.pref_buy_full_key));
            if (buyFull != null) {
                buyFull.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=" + Constants.FULL_PACKAGE_NAME)));
                        } catch (android.content.ActivityNotFoundException e) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("http://play.google.com/store/apps/details?id="
                                            + Constants.FULL_PACKAGE_NAME)));
                        }
                        return false;
                    }
                });
            }
        }

        Preference forceSync = findPreference(getString(R.string.pref_force_sync_key));
        if (forceSync != null) {
            forceSync.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mAccountHelper.manualSync();
                    return false;
                }
            });
        }

        colorPref = findPreference(getString(R.string.pref_color_key));
        if (colorPref != null) {
            updateColorPreferenceIcon();
            colorPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MaterialDialog dialog = new MaterialDialog(mActivity, MaterialDialog.getDEFAULT_BEHAVIOR());
                    dialog.title(R.string.pref_color, null);

                    DialogColorChooserExtKt.colorChooser(
                            dialog,
                            ColorPalette.INSTANCE.getPrimary(),
                            null,
                            PreferencesHelper.getColor(mActivity),
                            true,
                            true,
                            false,
                            false,
                            (d, color) -> {
                                SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
                                editor.putInt(getString(R.string.pref_color_key), color);
                                editor.apply();
                                updateColorPreferenceIcon();
                                return Unit.INSTANCE;
                            }
                    );
                    dialog.show();

                    return true;
                }
            });
        }
    }

    private void updateColorPreferenceIcon() {
        if (colorPref != null) {
            int color = PreferencesHelper.getColor(mActivity);
            colorPref.setIcon(createColorDrawable(color));
        }
    }

    private Drawable createColorDrawable(int color) {
        ShapeDrawable coloredCircle = new ShapeDrawable(new OvalShape());
        coloredCircle.getPaint().setColor(color);
        coloredCircle.setIntrinsicWidth(72);
        coloredCircle.setIntrinsicHeight(72);
        return coloredCircle;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        if (mActivity != null && mActivity.mySharedPreferenceChangeListener != null) {
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(
                    mActivity.mySharedPreferenceChangeListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        if (mActivity != null && mActivity.mySharedPreferenceChangeListener != null) {
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                    mActivity.mySharedPreferenceChangeListener);
        }
    }

}
