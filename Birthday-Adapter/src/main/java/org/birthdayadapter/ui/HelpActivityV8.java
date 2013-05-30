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

import java.io.IOException;
import java.io.InputStream;

import net.nightwhistler.htmlspanner.HtmlSpanner;
import net.nightwhistler.htmlspanner.JellyBeanSpanFixTextView;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Constants;
import org.birthdayadapter.util.Log;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class HelpActivityV8 extends FragmentActivity {

    /**
     * Instantiate View for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.help_activity_v8);

        // load html from html file from /res/raw
        InputStream inputStreamAbout = this.getResources().openRawResource(R.raw.about);
        InputStream inputStreamHelp = this.getResources().openRawResource(R.raw.help);

        TextView versionText = (TextView) findViewById(R.id.help_about_version);
        versionText.setText(getString(R.string.about_version) + " " + getVersion());

        JellyBeanSpanFixTextView aboutTextView = (JellyBeanSpanFixTextView) findViewById(R.id.help_about_text);
        JellyBeanSpanFixTextView helpTextView = (JellyBeanSpanFixTextView) findViewById(R.id.help_help_text);

        // load html into textview
        HtmlSpanner htmlSpanner = new HtmlSpanner();
        htmlSpanner.setStripExtraWhiteSpace(true);
        try {
            aboutTextView.setText(htmlSpanner.fromHtml(inputStreamAbout));
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error while reading raw resources as stream", e);
        }

        // make links work
        aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());

        // no flickering when clicking textview for Android < 4
        aboutTextView.setTextColor(getResources().getColor(
                android.R.color.secondary_text_dark_nodisable));

        // load html into textview
        try {
            helpTextView.setText(htmlSpanner.fromHtml(inputStreamHelp));
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error while reading raw resources as stream", e);
        }

        // make links work
        helpTextView.setMovementMethod(LinkMovementMethod.getInstance());

        // no flickering when clicking textview for Android < 4
        helpTextView.setTextColor(getResources().getColor(
                android.R.color.secondary_text_dark_nodisable));
    }

    /**
     * Get the current package version.
     *
     * @return The current version.
     */
    private String getVersion() {
        String result = "";
        try {
            PackageManager manager = this.getPackageManager();
            PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);

            result = String.format("%s (%s)", info.versionName, info.versionCode);
        } catch (NameNotFoundException e) {
            Log.w(Constants.TAG, "Unable to get application version", e);
            result = "Unable to get application version.";
        }

        return result;
    }

}
