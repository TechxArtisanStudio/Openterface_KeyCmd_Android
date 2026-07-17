package com.openterface.terminal;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.openterface.keymod.AppLocaleManager;
import com.openterface.keymod.R;
import com.openterface.keymod.ThemeManager;

/**
 * Standalone Activity for managing SSH credential profiles.
 * Launched from the main header or terminal connection dialog.
 */
public class CredentialActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT_PROFILE_ID = "extra_edit_profile_id";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppLocaleManager.applyPersistedLocales(this);
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_credential);
        setupWindowInsets();
        applyNonImmersiveSystemBars();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        if (savedInstanceState == null) {
            CredentialSettingsFragment frag = new CredentialSettingsFragment();
            String editProfileId = getIntent().getStringExtra(EXTRA_EDIT_PROFILE_ID);
            if (editProfileId != null) {
                Bundle args = new Bundle();
                args.putString(CredentialSettingsFragment.ARG_EDIT_PROFILE_ID, editProfileId);
                frag.setArguments(args);
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.credential_fragment_container, frag)
                    .commit();
        }
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.credential_root);
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
}
