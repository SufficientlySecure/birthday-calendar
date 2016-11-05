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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import org.birthdayadapter.R;
import org.birthdayadapter.util.PreferencesHelper;

public class InstallWorkaroundDialogFragment extends DialogFragment {

    /**
     * Creates new instance of this dialog fragment
     */
    public static InstallWorkaroundDialogFragment newInstance() {
        return new InstallWorkaroundDialogFragment();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Make the textview clickable. Must be called after show()
        TextView messageTextView = ((TextView) getDialog().findViewById(android.R.id.message));
        if (messageTextView != null) {
            messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    /**
     * Creates dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Linkify the message
        final SpannableString message = new SpannableString(getString(R.string.workaround_dialog_message));
        Linkify.addLinks(message, Linkify.ALL);

        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle(R.string.workaround_dialog_title);
        alert.setMessage(message);
        alert.setCancelable(true);
        alert.setIcon(android.R.drawable.ic_dialog_info);

        alert.setNegativeButton(R.string.workaround_dialog_close_button, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dismiss();
            }
        });
        alert.setNeutralButton(R.string.workaround_dialog_dont_show_again_button, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                PreferencesHelper.setShowWorkaroundDialog(getActivity(), false);
                dismiss();
            }
        });
        alert.setPositiveButton(R.string.workaround_dialog_install_button, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String workaroundName = "org.birthdayadapter.jb.workaround";
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + workaroundName)));
                } catch (ActivityNotFoundException anfe) {
                    // No Google Play installed? Weird! Try with browser!
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + workaroundName)));
                }
            }
        });
        return alert.create();
    }

}
