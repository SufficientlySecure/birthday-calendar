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
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.PreferencesHelper;

public class ExtendedPreferencesFragment extends PreferenceFragmentCompat {

    BaseActivity mActivity;
    private AccountHelper mAccountHelper;
    private Preference colorPref;

    private final int[] colors = new int[]{
            0xfff44336, 0xffe91e63, 0xff9c27b0, 0xff673ab7, 0xff3f51b5, 0xff2196f3, 0xff03a9f4, 0xff00bcd4,
            0xff009688, 0xff4caf50, 0xff8bc34a, 0xffcddc39, 0xffffeb3b, 0xffffc107, 0xffff9800, 0xffff5722,
            0xff795548, 0xff9e9e9e, 0xff607d8b
    };

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
                buyFull.setOnPreferenceClickListener(preference -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=" + Constants.FULL_PACKAGE_NAME)));
                    } catch (android.content.ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("http://play.google.com/store/apps/details?id="
                                        + Constants.FULL_PACKAGE_NAME)));
                    }
                    return false;
                });
            }
        }

        Preference forceSync = findPreference(getString(R.string.pref_force_sync_key));
        if (forceSync != null) {
            forceSync.setOnPreferenceClickListener(preference -> {
                mAccountHelper.manualSync();
                return false;
            });
        }

        colorPref = findPreference(getString(R.string.pref_color_key));
        if (colorPref != null) {
            updateColorPreferenceIcon();
            colorPref.setOnPreferenceClickListener(preference -> {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
                RecyclerView recyclerView = dialogView.findViewById(R.id.colorPicker);

                AlertDialog dialog = new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.pref_color)
                        .setView(dialogView)
                        .create();

                int numColumns = 4;
                ColorPickerAdapter adapter = new ColorPickerAdapter(colors, numColumns, color -> {
                    SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
                    editor.putInt(getString(R.string.pref_color_key), color);
                    editor.apply();
                    updateColorPreferenceIcon();
                    dialog.dismiss();
                });
                recyclerView.setLayoutManager(new GridLayoutManager(mActivity, numColumns));
                recyclerView.setAdapter(adapter);

                dialog.show();

                return true;
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
