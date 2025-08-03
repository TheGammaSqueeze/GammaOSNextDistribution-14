package com.android.tv.settings;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.tvsettings.TvSettingsEnums;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.settings.suggestions.Suggestion;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.suggestions.SuggestionControllerMixinCompat;
import com.android.settingslib.utils.IconCache;
import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.suggestions.SuggestionPreference;
import com.android.tv.settings.system.SecurityFragment;
import com.android.tv.settings.widget.SwitchWithSoundPreference;

import java.util.ArrayList;
import java.util.List;

public class SuggestionQuickSettingPrefsContainer  {
    @VisibleForTesting static final String KEY_QUICK_SETTINGS = "quick_settings";
    private static final String TAG = "SuggestionQuickSettingPrefsContainer";
    private static final String KEY_SUGGESTIONS_LIST = "suggestions";
    @VisibleForTesting PreferenceCategory mSuggestionsList;
    IconCache mIconCache;
    private SuggestionControllerMixinCompat mSuggestionControllerMixin;
    /** Controllers for the Quick Settings section. */
    private List<AbstractPreferenceController> mPreferenceControllers;

    private HotwordSwitchController mHotwordSwitchController;
    private TakeBugReportController mTakeBugReportController;
    private PreferenceCategory mQuickSettingsList;
    private SwitchWithSoundPreference mHotwordSwitch;
    private Preference mTakeBugReportPreference;
    private MainFragment mainFragment;

    SuggestionQuickSettingPrefsContainer(MainFragment mainFragment) {
        this.mainFragment = mainFragment;
    }

    void onCreate() {
        mIconCache = new IconCache(mainFragment.getContext());
        if (!isRestricted()) {
            ComponentName componentName = new ComponentName("com.android.settings.intelligence",
                    "com.android.settings.intelligence.suggestions.SuggestionService");
            mSuggestionControllerMixin =
                    new SuggestionControllerMixinCompat(mainFragment.getContext(),
                    mainFragment, mainFragment.getSettingsLifecycle(), componentName);
        }
    }

    void onDestroy() {
        if (mHotwordSwitchController != null) {
            mHotwordSwitchController.unregister();
        }
    }

    void showOrHideQuickSettings() {
        if (shouldShowQuickSettings()) {
            showQuickSettings();
        } else {
            hideQuickSettings();
        }
    }

    void onCreatePreferences() {
        if (mHotwordSwitchController != null) {
            mHotwordSwitchController.init(mainFragment);
        }
    }

    void onSuggestionReady(List<Suggestion> data) {
        // Suggestion category is handled differently in basic mode
        if (data == null || data.size() == 0
                || FlavorUtils.getFeatureFactory(mainFragment.getContext())
                .getBasicModeFeatureProvider()
                .isBasicMode(mainFragment.getContext())) {
            if (mSuggestionsList != null) {
                mainFragment.getPreferenceScreen().removePreference(mSuggestionsList);
                mSuggestionsList = null;
            }
            return;
        }

        if (mSuggestionsList == null) {
            mSuggestionsList = new PreferenceCategory(
                    mainFragment.getPreferenceManager().getContext());
            mSuggestionsList.setKey(KEY_SUGGESTIONS_LIST);
            mSuggestionsList.setTitle(R.string.header_category_suggestions);
            mSuggestionsList.setOrder(0); // always at top
            mainFragment.getPreferenceScreen().addPreference(mSuggestionsList);
        }
        updateSuggestionList(data);
    }

    @VisibleForTesting
    void updateSuggestionList(List<Suggestion> suggestions) {
        // Remove suggestions that are not in the new list.
        for (int i = 0; i < mSuggestionsList.getPreferenceCount(); i++) {
            SuggestionPreference pref = (SuggestionPreference) mSuggestionsList.getPreference(i);
            boolean isInNewSuggestionList = false;
            for (Suggestion suggestion : suggestions) {
                if (pref.getId().equals(suggestion.getId())) {
                    isInNewSuggestionList = true;
                    break;
                }
            }
            if (!isInNewSuggestionList) {
                mSuggestionsList.removePreference(pref);
            }
        }

        // Add suggestions that are not in the old list and update the existing suggestions.
        for (Suggestion suggestion : suggestions) {
            Preference curPref = mainFragment.findPreference(
                    SuggestionPreference.SUGGESTION_PREFERENCE_KEY + suggestion.getId());
            if (curPref == null) {
                SuggestionPreference newSuggPref =
                        new SuggestionPreference(suggestion,
                                mainFragment.getPreferenceManager().getContext(),
                                mSuggestionControllerMixin, mainFragment);
                newSuggPref.setIcon(mIconCache.getIcon(suggestion.getIcon()));
                newSuggPref.setTitle(suggestion.getTitle());
                newSuggPref.setSummary(suggestion.getSummary());
                mSuggestionsList.addPreference(newSuggPref);
            } else {
                // Even though the id of suggestion might not change, the details could change.
                // So we need to update icon, title and summary for the suggestions.
                curPref.setIcon(mIconCache.getIcon(suggestion.getIcon()));
                curPref.setTitle(suggestion.getTitle());
                curPref.setSummary(suggestion.getSummary());
            }
        }
    }

    boolean isRestricted() {
        return SecurityFragment.isRestrictedProfileInEffect(mainFragment.getContext());
    }

    void onSuggestionClosed(Preference preference) {
        if (mSuggestionsList == null || mSuggestionsList.getPreferenceCount() == 0) {
            return;
        } else if (mSuggestionsList.getPreferenceCount() == 1) {
            mainFragment.getPreferenceScreen().removePreference(mSuggestionsList);
        } else {
            mSuggestionsList.removePreference(preference);
        }
    }

    void onHotwordStateChanged() {
        if (mHotwordSwitch != null) {
            mHotwordSwitchController.updateState(mHotwordSwitch);
        }
        showOrHideQuickSettings();
    }

    void onHotwordEnable() {
        try {
            Intent intent = new Intent(HotwordSwitchController.ACTION_HOTWORD_ENABLE);
            intent.setPackage(HotwordSwitchController.ASSISTANT_PGK_NAME);
            mainFragment.startActivityForResult(intent, 0);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Unable to find hotwording activity.", e);
        }
    }

    void onHotwordDisable() {
        try {
            Intent intent = new Intent(HotwordSwitchController.ACTION_HOTWORD_DISABLE);
            intent.setPackage(HotwordSwitchController.ASSISTANT_PGK_NAME);
            mainFragment.startActivityForResult(intent, 0);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Unable to find hotwording activity.", e);
        }
    }

    private boolean quickSettingsEnabled() {
        return mainFragment.getContext().getResources().getBoolean(
                R.bool.config_quick_settings_enabled);
    }

    /** @return true if there is at least one available item in quick settings. */
    private boolean shouldShowQuickSettings() {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            if (controller.isAvailable()) {
                return true;
            }
        }
        return false;
    }


    /** Creates the quick settings category and its children. */
    private void showQuickSettings() {
        if (mQuickSettingsList != null) {
            return;
        }
        mQuickSettingsList = new PreferenceCategory(
                mainFragment.getPreferenceManager().getContext());
        mQuickSettingsList.setKey(KEY_QUICK_SETTINGS);
        mQuickSettingsList.setTitle(R.string.header_category_quick_settings);
        mQuickSettingsList.setOrder(1); // at top, but below suggested settings
        mainFragment.getPreferenceScreen().addPreference(mQuickSettingsList);
        if (mHotwordSwitchController != null && mHotwordSwitchController.isAvailable()) {
            mHotwordSwitch = new SwitchWithSoundPreference(
                    mainFragment.getPreferenceManager().getContext());
            mHotwordSwitch.setKey(HotwordSwitchController.KEY_HOTWORD_SWITCH);
            mHotwordSwitch.setOnPreferenceClickListener(preference -> {
                logEntrySelected(TvSettingsEnums.QUICK_SETTINGS);
                return false;
            });
            mHotwordSwitchController.updateState(mHotwordSwitch);
            mQuickSettingsList.addPreference(mHotwordSwitch);
        }
        if (mTakeBugReportController != null && mTakeBugReportController.isAvailable()) {
            mTakeBugReportPreference = new Preference(
                    mainFragment.getPreferenceManager().getContext());
            mTakeBugReportPreference.setKey(TakeBugReportController.KEY_TAKE_BUG_REPORT);
            mTakeBugReportPreference.setOnPreferenceClickListener(preference -> {
                logEntrySelected(TvSettingsEnums.QUICK_SETTINGS);
                return false;
            });
            mTakeBugReportController.updateState(mTakeBugReportPreference);
            mQuickSettingsList.addPreference(mTakeBugReportPreference);
        }
    }

    /** Removes the quick settings category and all its children. */
    private void hideQuickSettings() {
        Preference quickSettingsPref = mainFragment.findPreference(KEY_QUICK_SETTINGS);
        if (quickSettingsPref == null) {
            return;
        }
        mQuickSettingsList.removeAll();
        mainFragment.getPreferenceScreen().removePreference(mQuickSettingsList);
        mQuickSettingsList = null;
    }

    List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        mPreferenceControllers = new ArrayList<>(2);
        if (quickSettingsEnabled()) {
            mHotwordSwitchController = new HotwordSwitchController(context);
            mTakeBugReportController = new TakeBugReportController(context);
            mPreferenceControllers.add(mHotwordSwitchController);
            mPreferenceControllers.add(mTakeBugReportController);
        }
        return mPreferenceControllers;
    }

}
