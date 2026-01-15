package fr.heinisch.birthdayadapter.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
    private ColorStateList defaultButtonColor;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

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
        defaultButtonColor = nextButton.getBackgroundTintList();

        List<Fragment> onboardingFragments = createFragmentList();
        adapter = new OnboardingAdapter(this, onboardingFragments);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == adapter.getItemCount() - 1) {
                    updateFinishScreen();
                } else {
                    nextButton.setText(R.string.next);
                    nextButton.setBackgroundTintList(defaultButtonColor);
                }
            }
        });

        nextButton.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            } else {
                finishOnboarding();
            }
        });

        if (adapter.getItemCount() > 0 && viewPager.getCurrentItem() == adapter.getItemCount() - 1) {
            updateFinishScreen();
        }
    }

    private List<Fragment> createFragmentList() {
        List<Fragment> fragments = new ArrayList<>();

        fragments.add(new OnboardingIntroFragment());

        if (!areAllPermissionsGranted()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
                fragments.add(new OnboardingContactsFragment());
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                fragments.add(new OnboardingCalendarFragment());
            }
        }

        fragments.add(new OnboardingFinishFragment());

        return fragments;
    }

    private void updateFinishScreen() {
        OnboardingFinishFragment finishFragment = (OnboardingFinishFragment) adapter.fragments.get(adapter.getItemCount() - 1);
        if (areAllPermissionsGranted()) {
            finishFragment.setWarningMode(false);
            nextButton.setText(R.string.finish);
            nextButton.setBackgroundTintList(defaultButtonColor);
            activateAdapterIfNeeded();
        } else {
            finishFragment.setWarningMode(true);
            nextButton.setText(R.string.ignore);
            nextButton.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        }
    }

    private void activateAdapterIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isEnabled = prefs.getBoolean(getString(R.string.pref_enabled_key), false);

        if (isEnabled) {
            return;
        }

        AccountHelper accountHelper = new AccountHelper(this);
        executorService.execute(accountHelper::addAccountAndSync);

        prefs.edit().putBoolean(getString(R.string.pref_enabled_key), true).apply();
    }

    private void finishOnboarding() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("has_seen_onboarding", true).apply();

        Intent intent = new Intent(this, BaseActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean areAllPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
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
