package fr.heinisch.birthdayadapter.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class Installation {
    private static String sID = null;
    private static final String INSTALLATION = "INSTALLATION";

    public synchronized static String id(Context context) {
        if (sID == null) {
            SharedPreferences prefs = context.getSharedPreferences(INSTALLATION, Context.MODE_PRIVATE);
            sID = prefs.getString(INSTALLATION, null);
            if (sID == null) {
                sID = UUID.randomUUID().toString();
                prefs.edit().putString(INSTALLATION, sID).apply();
            }
        }
        return sID;
    }
}
