package org.birthdayadapter.ui;

import org.birthdayadapter.R;
import org.birthdayadapter.util.Utils;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class HelpActivity extends Activity {
    Activity mActivity;
    TextView mHelpText;

    /**
     * Instantiate View for this Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.help_activity);

        mActivity = this;

        mHelpText = (TextView) findViewById(R.id.help_text);

        // load html from html file from /res/raw
        String helpText = Utils.readContentFromResource(mActivity, R.raw.help);

        // set text from resources with html markup
        mHelpText.setText(Html.fromHtml(helpText));
        // make links work
        mHelpText.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
