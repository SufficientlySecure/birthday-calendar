package fr.heinisch.birthdayadapter.ui.onboarding;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.ui.OnboardingActivity;

public class OnboardingContactsFragment extends Fragment {

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (boolean isGranted : permissions.values()) {
                    if (!isGranted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    // Move to the next page in the ViewPager
                    if (getActivity() instanceof OnboardingActivity) {
                         ((OnboardingActivity) getActivity()).goToNextPage();
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.onboarding_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_onboarding_contacts, container, false);

        Button grantButton = view.findViewById(R.id.grant_contacts_button);
        grantButton.setOnClickListener(v -> requestPermissionLauncher.launch(new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.GET_ACCOUNTS
        }));

        return view;
    }
}
