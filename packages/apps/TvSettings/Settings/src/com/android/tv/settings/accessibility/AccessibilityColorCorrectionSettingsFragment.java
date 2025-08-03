package com.android.tv.settings.accessibility;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;

import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.R;

/**
 * Fragment instantiated on flavor classic for starting the
 * {@link AccessibilityColorCorrectionPreferenceFragment}
 */
public final class AccessibilityColorCorrectionSettingsFragment extends
                                            LeanbackSettingsFragmentCompat {

    public static AccessibilityColorCorrectionSettingsFragment newInstance() {
        return new AccessibilityColorCorrectionSettingsFragment();
    }

    @Override
    public void onPreferenceStartInitialScreen(){
        startPreferenceFragment(new AccessibilityColorCorrectionPreferenceFragment());
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        final Fragment fragment = new AccessibilityColorCorrectionPreferenceFragment();
        final Bundle args = new Bundle(1);
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        fragment.setArguments(args);
        startPreferenceFragment(fragment);
        return true;
    }
}