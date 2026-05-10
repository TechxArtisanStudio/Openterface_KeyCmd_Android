package com.openterface.keymod;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.openterface.keymod.fragments.AISettingsFragment;
import com.openterface.keymod.fragments.GeneralSettingsFragment;
import com.openterface.keymod.fragments.HistoryFragment;
import com.openterface.keymod.fragments.VoiceSettingsFragment;

/**
 * Settings Activity - Centralized settings management
 * Matches iOS SettingsView.swift functionality
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applyPersistedLocales(this);
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        setupWindowInsets();
        applyNonImmersiveSystemBars();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isTaskRoot()) {
                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
                finish();
            }
        });

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        viewPager.setAdapter(new SettingsPagerAdapter(this));

        String[] tabTitles = new String[]{
                getString(R.string.settings_tab_general),
                getString(R.string.settings_tab_voice),
                getString(R.string.settings_tab_ai),
                getString(R.string.settings_tab_history)
        };
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])).attach();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.settings_root);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyNonImmersiveSystemBars();
    }

    /**
     * Settings uses normal system bars (contrast with MainActivity immersive). On API 35+,
     * status/navigation bar colors are managed by edge-to-edge; we only adjust icon contrast here.
     */
    private void applyNonImmersiveSystemBars() {
        if (Build.VERSION.SDK_INT < 35) {
            int bg = ContextCompat.getColor(this, R.color.background_light);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }

        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decor);
        controller.show(WindowInsetsCompat.Type.systemBars());

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }

    private static class SettingsPagerAdapter extends FragmentStateAdapter {

        public SettingsPagerAdapter(@NonNull SettingsActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new GeneralSettingsFragment();
                case 1:
                    return new VoiceSettingsFragment();
                case 2:
                    return new AISettingsFragment();
                case 3:
                    return new HistoryFragment();
                default:
                    throw new IllegalArgumentException("Invalid settings page: " + position);
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
