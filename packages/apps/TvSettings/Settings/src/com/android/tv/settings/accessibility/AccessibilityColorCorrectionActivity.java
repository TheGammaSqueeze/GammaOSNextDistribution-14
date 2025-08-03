package com.android.tv.settings.accessibility;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.Fragment;

import com.android.tv.settings.TvSettingsActivity;
import com.android.tv.settings.overlay.FlavorUtils;

/**
 * Setup activity for accessibility color correction on flavor classic
 */
public class AccessibilityColorCorrectionActivity extends TvSettingsActivity {

    @Override
    protected Fragment createSettingsFragment() {
        if (FlavorUtils.isTwoPanel(getApplicationContext())) {
            return FlavorUtils.getFeatureFactory(this).getSettingsFragmentProvider()
                    .newSettingsFragment(
                    AccessibilityColorCorrectionPreferenceFragment.class.getName(), null);
        }
        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content,
                        AccessibilityColorCorrectionSettingsFragment.newInstance())
                .add(android.R.id.content,
                        AccessibilityColorCorrectionPreviewFragment.newInstance())
                .commitAllowingStateLoss();
        return null;
    }
}