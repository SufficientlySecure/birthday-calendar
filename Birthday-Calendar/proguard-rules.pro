# Most AndroidX libraries and other modern libraries include their own ProGuard rules.
# However, it's good practice to explicitly keep classes that are instantiated by the
# Android framework or other libraries via reflection.

# Keep the BirthdayWorker class and its constructor, as it's instantiated by WorkManager.
-keep public class fr.heinisch.birthdayadapter.service.BirthdayWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Google Play Billing Library
# Although the library ships with its own rules, adding these explicitly can prevent
# issues with aggressive optimization (proguard-android-optimize.txt).
-keep class com.android.billingclient.api.** { *; }

# Add any other rules below. For example, for data classes used with serialization libraries
# or for custom View classes.
#
# For example, if you use Gson to serialize a data class `com.example.MyData`:
# -keep class com.example.MyData { *; }
