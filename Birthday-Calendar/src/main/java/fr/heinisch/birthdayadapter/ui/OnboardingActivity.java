package fr.heinisch.birthdayadapter.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.heinisch.birthdayadapter.R;
import fr.heinisch.birthdayadapter.ui.onboarding.OnboardingCalendarFragment;
import fr.heinisch.birthdayadapter.ui.onboarding.OnboardingContactsFragment;
import fr.heinisch.birthdayadapter.ui.onboarding.OnboardingFinishFragment;
import fr.heinisch.birthdayadapter.ui.onboarding.OnboardingIntroFragment;
import fr.heinisch.birthdayadapter.util.AccountHelper;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button nextButton;
    private OnboardingAdapter adapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_onboarding);

        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        viewPager = findViewById(R.id.viewPager);
        nextButton = findViewById(R.id.nextButton);

        List<Fragment> onboardingFragments = createFragmentList();
        adapter = new OnboardingAdapter(this, onboardingFragments);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == adapter.getItemCount() - 1) {
                    nextButton.setText(R.string.finish);
                    // Activate the adapter as soon as the final page is visible
                    activateAdapterIfNeeded();
                } else {
                    nextButton.setText(R.string.next);
                }
            }
        });

        nextButton.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            } else {
                // By now, the adapter is already activated. Just finish the onboarding process.
                finishOnboarding();
            }
        });

        // Also handle the edge case where the finish screen is the only screen
        if (adapter.getItemCount() > 0 && viewPager.getCurrentItem() == adapter.getItemCount() - 1) {
            activateAdapterIfNeeded();
        }
    }

    private List<Fragment> createFragmentList() {
        List<Fragment> fragments = new ArrayList<>();

        // Screen 1: Intro
        fragments.add(new OnboardingIntroFragment());

        // Screen 2: Contacts Permission (only if needed)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            fragments.add(new OnboardingContactsFragment());
        }

        // Screen 3: Calendar Permission (only if needed)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            fragments.add(new OnboardingCalendarFragment());
        }

        // Screen 4: Final explanation
        fragments.add(new OnboardingFinishFragment());

        return fragments;
    }

    private void activateAdapterIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isEnabled = prefs.getBoolean(getString(R.string.pref_enabled_key), false);

        if (isEnabled) {
            return;
        }

        AccountHelper accountHelper = new AccountHelper(this);
        executorService.execute(accountHelper::addAccountAndSync);

        // Set the preference to reflect the activated state
        prefs.edit().putBoolean(getString(R.string.pref_enabled_key), true).apply();
    }

    private void finishOnboarding() {
        // Save that the user has completed the onboarding process
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("has_seen_onboarding", true).apply();

        // Go to the main activity
        Intent intent = new Intent(this, BaseActivity.class);
        startActivity(intent);
        finish();
    }

    public void goToNextPage() {
        int currentItem = viewPager.getCurrentItem();
        if (currentItem < adapter.getItemCount() - 1) {
            viewPager.setCurrentItem(currentItem + 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private static class OnboardingAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        public OnboardingAdapter(@NonNull AppCompatActivity activity, List<Fragment> fragments) {
            super(activity);
            this.fragments = fragments;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }
}
