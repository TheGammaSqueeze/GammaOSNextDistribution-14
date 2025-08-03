/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.device.displaysound;

import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;

import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.isHdrFormatSupported;
import static com.android.tv.settings.device.displaysound.HdrFormatSelectionInfo.AutoInfoFragment;
import static com.android.tv.settings.device.displaysound.HdrFormatSelectionInfo.DolbyVisionNotSupportedFragment;
import static com.android.tv.settings.device.displaysound.HdrFormatSelectionInfo.ManualInfoFragment;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Fragment is responsible for allowing the user enable or disable the Hdr types which are
 * supported by device.
 */
@Keep
public class HdrFormatSelectionFragment extends PreferenceControllerFragment {

    static final String KEY_HDR_FORMAT_SELECTION = "hdr_format_selection_option";
    static final String KEY_SUPPORTED_HDR_FORMATS = "supported_formats";
    static final String KEY_UNSUPPORTED_HDR_FORMATS = "unsupported_formats";
    static final String KEY_HDR_FORMAT_SELECTION_AUTO = "hdr_format_selection_auto";
    static final String KEY_HDR_FORMAT_SELECTION_MANUAL = "hdr_format_selection_manual";
    static final String KEY_HDR_FORMAT_PREFIX = "hdr_format_";
    static final String KEY_HDR_FORMAT_INFO_PREFIX = "hdr_format_info_";
    static final String KEY_FORMAT_INFO = "hdr_format_info";
    static final String KEY_SHOW_HIDE_FORMAT_INFO = "hdr_show_hide_format_info";
    static final String KEY_ENABLED_FORMATS = "enabled_formats";
    static final String KEY_DISABLED_FORMATS = "disabled_formats";

    static final int[] HDR_FORMATS_DISPLAY_ORDER = {
        HDR_TYPE_DOLBY_VISION, HDR_TYPE_HDR10, HDR_TYPE_HDR10_PLUS, HDR_TYPE_HLG
    };

    private PreferenceCategory mSupportedFormatsPreferenceCategory;
    private PreferenceCategory mUnsupportedFormatsPreferenceCategory;
    private PreferenceCategory mFormatsInfoPreferenceCategory;
    private PreferenceCategory mEnabledFormatsPreferenceCategory;
    private PreferenceCategory mDisabledFormatsPreferenceCategory;

    private List<AbstractPreferenceController> mPreferenceControllers;

    private Set<Integer> mDeviceHdrTypes;
    private Set<Integer> mDisplayReportedHdrTypes;
    private Set<Integer> mUserDisabledHdrTypes;

    private DisplayManager mDisplayManager;

    /** @return the new instance of the class */
    public static HdrFormatSelectionFragment newInstance() {
        return new HdrFormatSelectionFragment();
    }

    @Override
    public void onAttach(Context context) {
        mDisplayManager = getDisplayManager();
        mDeviceHdrTypes = toSet(getDeviceSupportedHdrTypes());
        mUserDisabledHdrTypes = toSet(mDisplayManager.getUserDisabledHdrTypes());
        mDisplayReportedHdrTypes = getDisplaySupportedHdrTypes();

        super.onAttach(context);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.hdr_format_selection;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.hdr_format_selection, null);

        createFormatInfoPreferences();
        createFormatPreferences();

        String currentPreferenceKey;
        if (mDisplayManager.areUserDisabledHdrTypesAllowed()) {
            currentPreferenceKey = KEY_HDR_FORMAT_SELECTION_AUTO;
            hideFormatPreferences();
        } else {
            currentPreferenceKey = KEY_HDR_FORMAT_SELECTION_MANUAL;
            showFormatPreferences();
        }
        selectRadioPreference(findPreference(currentPreferenceKey));

        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        mPreferenceControllers = new ArrayList<>();
        for (int hdrType : mDeviceHdrTypes) {
            if (mDisplayReportedHdrTypes.contains(hdrType)) {
                mPreferenceControllers.add(
                        new HdrFormatPreferenceController(getContext(), hdrType, mDisplayManager));
            }
        }
        return mPreferenceControllers;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();

        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }

        if (preference instanceof RadioPreference) {
            selectRadioPreference(preference);

            switch (key) {
                case KEY_HDR_FORMAT_SELECTION_AUTO: {
                    logEntrySelected(
                            TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_AUTO);
                    mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
                    hideFormatPreferences();
                    break;
                }
                case KEY_HDR_FORMAT_SELECTION_MANUAL: {
                    logEntrySelected(
                            TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_MANUAL);
                    mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
                    showFormatPreferences();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown hdr type selection pref value"
                            + ": " + key);
            }
        }

        if (key.equals(KEY_SHOW_HIDE_FORMAT_INFO)) {
            if (preference.getTitle().equals(
                    getContext().getString(R.string.hdr_hide_formats))) {
                hideFormatInfoPreferences();
                preference.setTitle(R.string.hdr_show_formats);
            } else {
                showFormatInfoPreferences();
                preference.setTitle(R.string.hdr_hide_formats);
            }
        }

        if (key.contains(KEY_HDR_FORMAT_INFO_PREFIX)) {
            if (preference.getParent() == mEnabledFormatsPreferenceCategory) {
                showToast(R.string.hdr_enabled_format_info_clicked);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    private PreferenceGroup getPreferenceGroup() {
        return (PreferenceGroup) findPreference(KEY_HDR_FORMAT_SELECTION);
    }

    @VisibleForTesting
    DisplayManager getDisplayManager() {
        return getContext().getSystemService(DisplayManager.class);
    }

    @VisibleForTesting
    int[] getDeviceSupportedHdrTypes() {
        return getContext().getResources().getIntArray(R.array.config_deviceSupportedHdrFormats);
    }

    private Set<Integer> getDisplaySupportedHdrTypes() {
        Set<Integer> hdrTypes = new HashSet<>();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Arrays.stream(display.getSupportedModes())
                .map(Display.Mode::getSupportedHdrTypes)
                .forEach(types -> Arrays.stream(types).forEach(hdrTypes::add));
        return hdrTypes;
    }

    private void selectRadioPreference(Preference preference) {
        final RadioPreference radioPreference = (RadioPreference) preference;
        radioPreference.setChecked(true);
        radioPreference.clearOtherRadioPreferences(getPreferenceGroup());
    }

    private void createFormatPreferences() {
        mSupportedFormatsPreferenceCategory = createPreferenceCategory(
                R.string.hdr_format_supported_title,
                KEY_SUPPORTED_HDR_FORMATS);
        getPreferenceScreen().addPreference(mSupportedFormatsPreferenceCategory);
        mUnsupportedFormatsPreferenceCategory = createPreferenceCategory(
                R.string.hdr_format_unsupported_title,
                KEY_UNSUPPORTED_HDR_FORMATS);
        getPreferenceScreen().addPreference(mUnsupportedFormatsPreferenceCategory);

        for (int hdrType : HDR_FORMATS_DISPLAY_ORDER) {
            if (mDeviceHdrTypes.contains(hdrType)) {
                int titleId = getFormatPreferenceTitleId(hdrType);
                if (titleId == -1) {
                    continue;
                }
                if (mDisplayReportedHdrTypes.contains(hdrType)) {
                    boolean enabled = !mUserDisabledHdrTypes.contains(hdrType);
                    mSupportedFormatsPreferenceCategory.addPreference(
                            createSupportedFormatPreference(titleId, hdrType, enabled));
                } else {
                    mUnsupportedFormatsPreferenceCategory.addPreference(
                            createUnsupportedFormatPreference(titleId, hdrType));
                }
            }
        }
    }

    /** Creates titles and preferences for each hdr format. */
    private void createFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory = createPreferenceCategory(
                R.string.hdr_format_info, KEY_FORMAT_INFO);
        getPreferenceScreen().addPreference(mFormatsInfoPreferenceCategory);

        Preference pref = createPreference(
                R.string.hdr_show_formats, KEY_SHOW_HIDE_FORMAT_INFO);
        mFormatsInfoPreferenceCategory.addPreference(pref);

        mEnabledFormatsPreferenceCategory = createPreferenceCategory(
                R.string.hdr_enabled_formats, KEY_ENABLED_FORMATS);
        mFormatsInfoPreferenceCategory.addPreference(mEnabledFormatsPreferenceCategory);

        mDisabledFormatsPreferenceCategory = createPreferenceCategory(
                R.string.hdr_disabled_formats, KEY_DISABLED_FORMATS);
        mFormatsInfoPreferenceCategory.addPreference(mDisabledFormatsPreferenceCategory);

        for (int hdrType : HDR_FORMATS_DISPLAY_ORDER) {
            if (mDeviceHdrTypes.contains(hdrType)) {
                int titleId = getFormatPreferenceTitleId(hdrType);
                if (titleId == -1) {
                    continue;
                }

                pref = createPreference(titleId, KEY_HDR_FORMAT_INFO_PREFIX + hdrType);
                if (mDisplayReportedHdrTypes.contains(hdrType)) {
                    mEnabledFormatsPreferenceCategory.addPreference(pref);
                } else {
                    mDisabledFormatsPreferenceCategory.addPreference(pref);
                }
            }
        }
        hideFormatInfoPreferences();
    }


    /** Returns a switch preference for each supported HDR format. */
    private Preference createSupportedFormatPreference(int titleId, int hdrType, boolean enabled) {
        final SwitchPreference pref = new SwitchPreference(getContext()) {
            @Override
            public void onBindViewHolder(PreferenceViewHolder holder) {
                super.onBindViewHolder(holder);
                holder.itemView.setEnabled(true);
            }
        };
        pref.setTitle(titleId);
        pref.setKey(KEY_HDR_FORMAT_PREFIX + hdrType);
        Display.Mode mode = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getMode();
        if (!isHdrFormatSupported(mode, hdrType)) {
            if (hdrType == HDR_TYPE_DOLBY_VISION) {
                pref.setFragment(DolbyVisionNotSupportedFragment.class.getName());
            }
            enabled = false;
        }
        pref.setChecked(enabled);
        if (getLogEntryId(hdrType) != -1) {
            pref.setOnPreferenceClickListener(
                    preference -> {
                        logToggleInteracted(getLogEntryId(hdrType), pref.isChecked());
                        return false;
                    });
        }
        return pref;
    }

    /** Returns a non-switch preference for each unsupported HDR format. */
    private Preference createUnsupportedFormatPreference(int titleId, int hdrType) {
        Preference pref = new Preference(getContext());
        pref.setTitle(titleId);
        pref.setKey(KEY_HDR_FORMAT_PREFIX + hdrType);
        return pref;
    }

    private void showFormatPreferences() {
        getPreferenceScreen().addPreference(mSupportedFormatsPreferenceCategory);
        getPreferenceScreen().addPreference(mUnsupportedFormatsPreferenceCategory);
        updateFormatPreferencesStates();
        // hide the formats info section.
        getPreferenceScreen().removePreference(mFormatsInfoPreferenceCategory);
    }

    private void hideFormatPreferences() {
        getPreferenceScreen().removePreference(mSupportedFormatsPreferenceCategory);
        getPreferenceScreen().removePreference(mUnsupportedFormatsPreferenceCategory);
        updateFormatPreferencesStates();
        // show the formats info section.
        getPreferenceScreen().addPreference(mFormatsInfoPreferenceCategory);
    }

    private void showFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory.addPreference(mEnabledFormatsPreferenceCategory);
        mFormatsInfoPreferenceCategory.addPreference(mDisabledFormatsPreferenceCategory);
    }

    private void hideFormatInfoPreferences() {
        mFormatsInfoPreferenceCategory.removePreference(mEnabledFormatsPreferenceCategory);
        mFormatsInfoPreferenceCategory.removePreference(mDisabledFormatsPreferenceCategory);
    }

    private void showToast(int resId) {
        Toast.makeText(getContext(), getContext().getString(resId), Toast.LENGTH_SHORT)
                .show();
    }

    private PreferenceCategory createPreferenceCategory(int titleResourceId, String key) {
        PreferenceCategory preferenceCategory = new PreferenceCategory(getContext());
        preferenceCategory.setTitle(titleResourceId);
        preferenceCategory.setKey(key);
        return preferenceCategory;
    }

    private Preference createPreference(int titleResourceId, String key) {
        Preference preference = new Preference(getContext());
        preference.setTitle(titleResourceId);
        preference.setKey(key);
        return preference;
    }

    private void updateFormatPreferencesStates() {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            Preference preference = findPreference(controller.getPreferenceKey());
            if (preference != null && preference instanceof SwitchPreference) {
                controller.updateState(preference);
            }
        }
    }

    /**
     * @return the display id for each hdr type.
     */
    private int getFormatPreferenceTitleId(int hdrType) {
        switch (hdrType) {
            case HDR_TYPE_DOLBY_VISION:
                return R.string.hdr_format_dolby_vision;
            case HDR_TYPE_HDR10:
                return R.string.hdr_format_hdr10;
            case HDR_TYPE_HLG:
                return R.string.hdr_format_hlg;
            case HDR_TYPE_HDR10_PLUS:
                return R.string.hdr_format_hdr10plus;
            default:
                return -1;
        }
    }

    private int getLogEntryId(int hdrType) {
        switch(hdrType) {
            case HDR_TYPE_DOLBY_VISION:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_DOLBY_VISION;
            case HDR_TYPE_HDR10:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_HDR10;
            case HDR_TYPE_HLG:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_HLG;
            case HDR_TYPE_HDR10_PLUS:
                return TvSettingsEnums.DISPLAY_SOUND_ADVANCED_DISPLAY_FORMAT_SELECTION_HDR10_PLUS;
            default:
                return -1;
        }
    }

    private Set<Integer> toSet(int[] array) {
        return Arrays.stream(array).boxed().collect(Collectors.toSet());
    }

    private void createInfoFragments() {
        Preference autoPreference = findPreference(KEY_HDR_FORMAT_SELECTION_AUTO);
        autoPreference.setFragment(AutoInfoFragment.class.getName());

        Preference manualPreference = findPreference(KEY_HDR_FORMAT_SELECTION_MANUAL);
        manualPreference.setFragment(ManualInfoFragment.class.getName());
    }
}
