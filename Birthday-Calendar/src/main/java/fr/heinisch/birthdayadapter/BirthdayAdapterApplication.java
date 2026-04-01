package fr.heinisch.birthdayadapter;

import android.app.Application;

public class BirthdayAdapterApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Dynamic colors are disabled to preserve the original brand green (#387002)
    }
}
