/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.settings.inputmethod;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.applications.DefaultAppInfo;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.autofill.AutofillHelper;
import com.android.tv.settings.library.util.ThreadUtils;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.util.SliceUtils;
import com.android.tv.twopanelsettings.slices.SlicePreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fragment for managing IMEs, Autofills and physical keyboards
 */
@Keep
public class KeyboardFragment extends SettingsPreferenceFragment implements
        InputManager.InputDeviceListener {
    private static final String TAG = "KeyboardFragment";
    private static final boolean DEBUG = false;

    // Order of input methods, make sure they are inserted between 1 (currentKeyboard) and
    // 3 (manageKeyboards).
    private static final int INPUT_METHOD_PREFERENCE_ORDER = 2;

    // Order of physical keyboard setting, in the end
    private static final int PHYSICAL_KEYBOARD_PREFERENCE_ORDER = 5;

    @VisibleForTesting
    static final String KEY_KEYBOARD_CATEGORY = "keyboardCategory";

    @VisibleForTesting
    static final String KEY_CURRENT_KEYBOARD = "currentKeyboard";

    private static final String KEY_KEYBOARD_SETTINGS_PREFIX = "keyboardSettings:";

    private static final String KEY_PHYSICAL_KEYBOARD_SETTINGS_PREFIX = "physicalKeyboardSettings:";

    @VisibleForTesting
    static final String KEY_AUTOFILL_CATEGORY = "autofillCategory";

    @VisibleForTesting
    static final String KEY_CURRENT_AUTOFILL = "currentAutofill";

    private static final String KEY_AUTOFILL_SETTINGS_PREFIX = "autofillSettings:";

    private PackageManager mPm;

    private InputManager mIm;

    /**
     * @return New fragment instance
     */
    public static KeyboardFragment newInstance() {
        return new KeyboardFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPm = context.getPackageManager();
        mIm = Objects.requireNonNull(context.getSystemService(InputManager.class));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.keyboard, null);

        findPreference(KEY_CURRENT_KEYBOARD).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    logEntrySelected(TvSettingsEnums.SYSTEM_KEYBOARD_CURRENT_KEYBOARD);
                    InputMethodHelper.setDefaultInputMethodId(getContext(), (String) newValue);
                    return true;
                });

        updateUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
        mIm.registerInputDeviceListener(this, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        mIm.unregisterInputDeviceListener(this);
    }

    @VisibleForTesting
    void updateUi() {
        updateAutofill();
        updateKeyboards();
    }

    private void updateKeyboards() {
        updateCurrentKeyboardPreference(findPreference(KEY_CURRENT_KEYBOARD));
        updateKeyboardsSettings();
        scheduleUpdatePhysicalKeyboards(getPreferenceContext());
    }

    private void updateCurrentKeyboardPreference(ListPreference currentKeyboardPref) {
        List<InputMethodInfo> enabledInputMethodInfos = InputMethodHelper
                .getEnabledSystemInputMethodList(getContext());
        final List<CharSequence> entries = new ArrayList<>(enabledInputMethodInfos.size());
        final List<CharSequence> values = new ArrayList<>(enabledInputMethodInfos.size());

        int defaultIndex = 0;
        final String defaultId = InputMethodHelper.getDefaultInputMethodId(getContext());

        for (final InputMethodInfo info : enabledInputMethodInfos) {
            entries.add(info.loadLabel(mPm));
            final String id = info.getId();
            values.add(id);
            if (TextUtils.equals(id, defaultId)) {
                defaultIndex = values.size() - 1;
            }
        }

        currentKeyboardPref.setEntries(entries.toArray(new CharSequence[entries.size()]));
        currentKeyboardPref.setEntryValues(values.toArray(new CharSequence[values.size()]));
        if (entries.size() > 0) {
            currentKeyboardPref.setValueIndex(defaultIndex);
        }
    }

    Context getPreferenceContext() {
        return getPreferenceManager().getContext();
    }

    private void updateKeyboardsSettings() {
        final Context preferenceContext = getPreferenceContext();
        List<InputMethodInfo> enabledInputMethodInfos = InputMethodHelper
                .getEnabledSystemInputMethodList(getContext());
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final Set<String> enabledInputMethodKeys = new ArraySet<>(
                enabledInputMethodInfos.size());
        // Add per-IME settings
        for (final InputMethodInfo info : enabledInputMethodInfos) {
            final String uri = InputMethodHelper.getInputMethodsSettingsUri(getContext(), info);
            final Intent settingsIntent = InputMethodHelper.getInputMethodSettingsIntent(info);
            if (uri == null && settingsIntent == null) {
                continue;
            }
            final String key = KEY_KEYBOARD_SETTINGS_PREFIX + info.getId();

            Preference preference = preferenceScreen.findPreference(key);
            boolean useSlice = FlavorUtils.isTwoPanel(getContext()) && uri != null;
            if (preference == null) {
                if (useSlice) {
                    preference = new SlicePreference(preferenceContext);
                } else {
                    preference = new Preference(preferenceContext);
                }
                preference.setOrder(INPUT_METHOD_PREFERENCE_ORDER);
                preferenceScreen.addPreference(preference);
            }
            preference.setTitle(getContext().getString(R.string.title_settings,
                    info.loadLabel(mPm)));
            preference.setKey(key);
            if (useSlice) {
                ((SlicePreference) preference).setUri(uri);
                preference.setFragment(SliceUtils.PATH_SLICE_FRAGMENT);
            } else {
                preference.setIntent(settingsIntent);
            }
            enabledInputMethodKeys.add(key);
        }
        removeDisabledPreferencesFromScreen(preferenceScreen, enabledInputMethodKeys,
                KEY_KEYBOARD_SETTINGS_PREFIX);
    }

    void scheduleUpdatePhysicalKeyboards(Context context) {
        ThreadUtils.postOnBackgroundThread(() -> {
            final List<PhysicalKeyboardHelper.DeviceInfo> newPhysicalKeyboards =
                    PhysicalKeyboardHelper.getPhysicalKeyboards(context);
            ThreadUtils.postOnMainThread(() -> updatePhysicalKeyboards(newPhysicalKeyboards));
        });
    }

    private void updatePhysicalKeyboards(
            @NonNull List<PhysicalKeyboardHelper.DeviceInfo> newPhysicalKeyboards) {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (DEBUG) {
            Log.d(TAG, "updatePhysicalKeyboards: " + newPhysicalKeyboards.toString());
        }
        final Set<String> enabledPhysicalKeyboardKeys = new ArraySet<>(newPhysicalKeyboards.size());
        // Add a setting per physical keyboard device
        for (PhysicalKeyboardHelper.DeviceInfo deviceInfo :
                newPhysicalKeyboards) {
            String key = KEY_PHYSICAL_KEYBOARD_SETTINGS_PREFIX
                    + deviceInfo.mDeviceIdentifier.getDescriptor();
            Preference pref = preferenceScreen.findPreference(key);
            if (pref == null) {
                pref = new Preference(getPreferenceContext());
                pref.setOrder(PHYSICAL_KEYBOARD_PREFERENCE_ORDER);
                preferenceScreen.addPreference(pref);
            }
            pref.setKey(key);
            pref.setTitle(getPreferenceContext().getString(
                    com.android.settingslib.R.string.physical_keyboard_title));
            pref.setSummary(deviceInfo.getSummary());
            KeyboardLayoutSelectionFragment.prepareArgs(pref.getExtras(),
                    deviceInfo.mDeviceIdentifier,
                    deviceInfo.mDeviceName,
                    deviceInfo.mDeviceId,
                    deviceInfo.mCurrentLayoutDescriptor);
            pref.setFragment(KeyboardLayoutSelectionFragment.class.getName());
            enabledPhysicalKeyboardKeys.add(key);
        }
        removeDisabledPreferencesFromScreen(preferenceScreen, enabledPhysicalKeyboardKeys,
                KEY_PHYSICAL_KEYBOARD_SETTINGS_PREFIX);
    }

    /**
     * Removes all preferences which start with the key prefix and are not among the enabled keys
     * from the preference screen.
     *
     * @param preferenceScreen The preference screen.
     * @param enabledKeys The set of enabled keys.
     * @param keyPrefix The prefix for the keys to be removed.
     */
    private void removeDisabledPreferencesFromScreen(PreferenceScreen preferenceScreen,
            Set<String> enabledKeys, String keyPrefix) {
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); ) {
            final Preference preference = preferenceScreen.getPreference(i);
            final String key = preference.getKey();
            if (!TextUtils.isEmpty(key)
                    && key.startsWith(keyPrefix)
                    && !enabledKeys.contains(key)) {
                preferenceScreen.removePreference(preference);
            } else {
                i++;
            }
        }
    }

    /**
     * Update autofill related preferences.
     */
    private void updateAutofill() {
        final PreferenceCategory autofillCategory = findPreference(KEY_AUTOFILL_CATEGORY);
        List<DefaultAppInfo> candidates = getAutofillCandidates();
        if (candidates.isEmpty()) {
            // No need to show keyboard category and autofill category.
            // Keyboard only preference screen:
            findPreference(KEY_KEYBOARD_CATEGORY).setVisible(false);
            autofillCategory.setVisible(false);
            getPreferenceScreen().setTitle(R.string.system_keyboard);
        } else {
            // Show both keyboard category and autofill category in keyboard & autofill screen.
            findPreference(KEY_KEYBOARD_CATEGORY).setVisible(true);
            autofillCategory.setVisible(true);
            final Preference currentAutofillPref = findPreference(KEY_CURRENT_AUTOFILL);
            updateCurrentAutofillPreference(currentAutofillPref, candidates);
            updateAutofillSettings(candidates);
            getPreferenceScreen().setTitle(R.string.system_keyboard_autofill);
        }
    }

    private List<DefaultAppInfo> getAutofillCandidates() {
        return AutofillHelper.getAutofillCandidates(getContext(),
                mPm, UserHandle.myUserId());
    }

    private void updateCurrentAutofillPreference(Preference currentAutofillPref,
            List<DefaultAppInfo> candidates) {

        DefaultAppInfo app = AutofillHelper.getCurrentAutofill(getContext(), candidates);

        CharSequence summary = app == null ? getContext().getString(R.string.autofill_none)
                : app.loadLabel();
        currentAutofillPref.setSummary(summary);
    }

    private void updateAutofillSettings(List<DefaultAppInfo> candidates) {
        final Context preferenceContext = getPreferenceContext();

        final PreferenceCategory autofillCategory = findPreference(KEY_AUTOFILL_CATEGORY);

        final Set<String> autofillServicesKeys = new ArraySet<>(candidates.size());
        for (final DefaultAppInfo info : candidates) {
            final Intent settingsIntent = AutofillHelper.getAutofillSettingsIntent(getContext(),
                    mPm, info);
            if (settingsIntent == null) {
                continue;
            }
            final String key = KEY_AUTOFILL_SETTINGS_PREFIX + info.getKey();

            Preference preference = findPreference(key);
            if (preference == null) {
                preference = new Preference(preferenceContext);
                autofillCategory.addPreference(preference);
            }
            preference.setTitle(getContext().getString(R.string.title_settings, info.loadLabel()));
            preference.setKey(key);
            preference.setIntent(settingsIntent);
            autofillServicesKeys.add(key);
        }

        for (int i = 0; i < autofillCategory.getPreferenceCount(); ) {
            final Preference preference = autofillCategory.getPreference(i);
            final String key = preference.getKey();
            if (!TextUtils.isEmpty(key)
                    && key.startsWith(KEY_AUTOFILL_SETTINGS_PREFIX)
                    && !autofillServicesKeys.contains(key)) {
                autofillCategory.removePreference(preference);
            } else {
                i++;
            }
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_KEYBOARD;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        scheduleUpdatePhysicalKeyboards(getPreferenceContext());
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        scheduleUpdatePhysicalKeyboards(getPreferenceContext());
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        scheduleUpdatePhysicalKeyboards(getPreferenceContext());
    }
}
