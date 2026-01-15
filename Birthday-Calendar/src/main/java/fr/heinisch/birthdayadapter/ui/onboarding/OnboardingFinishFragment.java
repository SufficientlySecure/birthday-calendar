package fr.heinisch.birthdayadapter.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import fr.heinisch.birthdayadapter.R;

public class OnboardingFinishFragment extends Fragment {

    private TextView titleTextView;
    private TextView textTextView;
    private boolean warningMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_onboarding_finish, container, false);
        titleTextView = view.findViewById(R.id.finish_title);
        textTextView = view.findViewById(R.id.finish_text);
        updateTexts();
        return view;
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
        } else {
            titleTextView.setText(R.string.onboarding_finish_title);
            textTextView.setText(R.string.onboarding_finish_text);
        }
    }
}
