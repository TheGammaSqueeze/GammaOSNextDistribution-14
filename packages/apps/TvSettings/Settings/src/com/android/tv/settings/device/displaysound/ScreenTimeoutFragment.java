package com.android.tv.settings.device.displaysound;

import static com.android.tv.settings.library.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.Keep;
import androidx.preference.PreferenceGroup;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.library.overlay.FlavorUtils;

/**
 * This Fragment is responsible for allowing the user to set screen timeout preferences.
 */
@Keep
public class ScreenTimeoutFragment extends SettingsPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.screen_timeout, rootKey);

        int currentTimeout = Settings.System.getInt(
            getContext().getContentResolver(),
            Settings.System.SCREEN_OFF_TIMEOUT,
            300000);  // Default to 5 minutes if no setting is found

        setupRadioPreference("timeout_30_seconds", 30000, currentTimeout);
        setupRadioPreference("timeout_1_minute", 60000, currentTimeout);
        setupRadioPreference("timeout_5_minutes", 300000, currentTimeout);
        setupRadioPreference("timeout_15_minutes", 900000, currentTimeout);
        setupRadioPreference("timeout_30_minutes", 1800000, currentTimeout);
        setupRadioPreference("timeout_1_hour", 3600000, currentTimeout);
        setupRadioPreference("timeout_2_hours", 7200000, currentTimeout);
    }

    private void setupRadioPreference(String key, int timeoutValue, int currentTimeout) {
        RadioPreference preference = (RadioPreference) findPreference(key);
        if (preference != null) {
            // Check the radio button if it matches the current setting
            preference.setChecked(timeoutValue == currentTimeout);

            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((Boolean) newValue) {
                    // Update the system setting
                    Settings.System.putInt(
                        getContext().getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        timeoutValue);

                    // Clear other checks
                    clearOtherChecks(key);
                }
                return true;
            });
        }
    }

    private void clearOtherChecks(String key) {
        String[] keys = {"timeout_30_seconds", "timeout_1_minute", "timeout_5_minutes", "timeout_15_minutes", "timeout_30_minutes", "timeout_1_hour", "timeout_2_hours"};
        for (String otherKey : keys) {
            if (!otherKey.equals(key)) {
                RadioPreference otherPref = (RadioPreference) findPreference(otherKey);
                if (otherPref != null) {
                    otherPref.setChecked(false);
                }
            }
        }
    }

    @Override
    public int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }
}
