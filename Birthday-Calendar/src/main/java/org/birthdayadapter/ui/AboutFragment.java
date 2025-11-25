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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.birthdayadapter.BuildConfig;
import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import java.io.InputStream;
import java.util.Objects;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_fragment, container, false);

        TextView versionText = view.findViewById(R.id.about_version);
        versionText.setText(String.format("%s %s", getString(R.string.about_version), getVersion()));

        TextView aboutTextView = view.findViewById(R.id.about_text);
        
        // Load HTML from raw resource
        try {
            InputStream in = getResources().openRawResource(R.raw.about);
            byte[] buffer = new byte[in.available()];
            in.read(buffer);
            in.close();
            aboutTextView.setText(Html.fromHtml(new String(buffer), Html.FROM_HTML_MODE_LEGACY));
            aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error loading about.html", e);
        }

        return view;
    }

    /**
     * Get the current package version.
     *
     * @return The current version.
     */
    private String getVersion() {
        String result;
        try {
            PackageManager manager = requireActivity().getPackageManager();
            PackageInfo info = manager.getPackageInfo(requireActivity().getPackageName(), 0);

            String commitHash = org.birthdayadapter.BuildConfig.GIT_COMMIT_HASH;
            result = String.format("%s (%s)", info.versionName, commitHash);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(Constants.TAG, "Unable to get application version", e);
            result = "Unable to get application version.";
        }

        return result;
    }

}