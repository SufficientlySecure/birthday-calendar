package fr.heinisch.birthdayadapter.ui.onboarding;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Calendar;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.ui.OnboardingActivity;

public class OnboardingFinishFragment extends Fragment {

    private TextView titleTextView;
    private TextView textTextView;
    private Button ignoreButton;
    private Button openCalendarButton;
    private boolean warningMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_onboarding_finish, container, false);
        titleTextView = view.findViewById(R.id.finish_title);
        textTextView = view.findViewById(R.id.finish_text);
        ignoreButton = view.findViewById(R.id.ignore_button);
        openCalendarButton = view.findViewById(R.id.open_calendar_button);

        ignoreButton.setOnClickListener(v -> {
            if (getActivity() instanceof OnboardingActivity) {
                ((OnboardingActivity) getActivity()).finishOnboarding();
            }
        });

        openCalendarButton.setOnClickListener(v -> openCalendar());

        updateTexts();
        return view;
    }

    private void openCalendar() {
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, Calendar.getInstance().getTimeInMillis());
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireActivity(), "No calendar app found", Toast.LENGTH_SHORT).show();
        }
    }

    public void setWarningMode(boolean warningMode) {
        this.warningMode = warningMode;
        if (isAdded()) {
            updateTexts();
        }
    }

    private void updateTexts() {
        if (warningMode) {
            titleTextView.setText(R.string.onboarding_finish_warning_title);
            textTextView.setText(R.string.onboarding_finish_warning_text);
            ignoreButton.setVisibility(View.VISIBLE);
            openCalendarButton.setVisibility(View.GONE);
        } else {
            titleTextView.setText(R.string.onboarding_finish_title);
            setCalendarSpecificText();
            ignoreButton.setVisibility(View.GONE);
            openCalendarButton.setVisibility(View.VISIBLE);
        }
    }

    private void setCalendarSpecificText() {
        PackageManager pm = requireActivity().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_CALENDAR);
        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null) {
            String packageName = resolveInfo.activityInfo.packageName;
            if ("com.google.android.calendar".equals(packageName)) {
                textTextView.setText(R.string.onboarding_finish_text_google_calendar);
            } else if ("com.samsung.android.calendar".equals(packageName)) {
                textTextView.setText(R.string.onboarding_finish_text_samsung_calendar);
            } else {
                textTextView.setText(R.string.onboarding_finish_text);
            }
        } else {
            textTextView.setText(R.string.onboarding_finish_text);
        }
    }
}
