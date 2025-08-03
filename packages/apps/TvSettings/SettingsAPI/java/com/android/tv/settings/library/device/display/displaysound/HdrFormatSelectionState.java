/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.device.display.displaysound;

import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;

import static com.android.tv.settings.library.ManagerUtil.STATE_HDR_FORMAT_SELECTION;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.LibUtils;
import com.android.tv.settings.library.util.PreferenceCompatUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * State to provide data for hdr format selection screen.
 */
public class HdrFormatSelectionState extends PreferenceControllerState {
    static final String KEY_HDR_FORMAT_SELECTION = "hdr_format_selection_option";
    static final String KEY_SUPPORTED_HDR_FORMATS = "supported_formats";
    static final String KEY_UNSUPPORTED_HDR_FORMATS = "unsupported_formats";
    static final String KEY_HDR_FORMAT_SELECTION_AUTO = "hdr_format_selection_auto";
    static final String KEY_HDR_FORMAT_SELECTION_MANUAL = "hdr_format_selection_manual";
    static final String KEY_HDR_FORMAT_PREFIX = "hdr_format_";
    static final String KEY_HDR_FORMAT_INFO_PREFIX = "hdr_format_info_";
    static final String KEY_FORMAT_INFO = "hdr_format_info";
    static final String KEY_FORMAT_INFO_ON_MANUAL = "hdr_format_info_on_manual";
    static final String KEY_SHOW_HIDE_FORMAT_INFO = "hdr_show_hide_format_info";
    static final String KEY_ENABLED_FORMATS = "enabled_formats";
    static final String KEY_DISABLED_FORMATS = "disabled_formats";

    static final int[] HDR_FORMATS_DISPLAY_ORDER = {
            HDR_TYPE_DOLBY_VISION, HDR_TYPE_HDR10, HDR_TYPE_HDR10_PLUS, HDR_TYPE_HLG
    };

    private PreferenceCompat mHdrFormatSelectionCategory;
    private PreferenceCompat mHdrFormatSelectionAuto;
    private PreferenceCompat mHdrFormatSelectionManual;
    private PreferenceCompat mFormatsInfoOnManualCategory;
    private PreferenceCompat mSupportedFormatsCategory;
    private PreferenceCompat mUnsupportedFormatsCategory;
    private PreferenceCompat mFormatsInfoCategory;
    private PreferenceCompat mShowHideFormatInfo;
    private PreferenceCompat mEnabledFormatsCategory;
    private PreferenceCompat mDisabledFormatsCategory;
    private Set<Integer> mDeviceHdrTypes;
    private Set<Integer> mDisplayReportedHdrTypes;
    private Set<Integer> mUserDisabledHdrTypes;
    private DisplayManager mDisplayManager;

    public HdrFormatSelectionState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onAttach() {
        super.onAttach();
        mDisplayManager = getDisplayManager();
        mDeviceHdrTypes = toSet(getDeviceSupportedHdrTypes());
        mUserDisabledHdrTypes = toSet(mDisplayManager.getUserDisabledHdrTypes());

        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        mDisplayReportedHdrTypes = toSet(display.getReportedHdrTypes());
    }

    @Override
    public void onCreate(Bundle extras) {
        mHdrFormatSelectionCategory = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_HDR_FORMAT_SELECTION);
        mHdrFormatSelectionCategory.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);

        mHdrFormatSelectionAuto = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_HDR_FORMAT_SELECTION, KEY_HDR_FORMAT_SELECTION_AUTO}
        );
        mHdrFormatSelectionAuto.setTitle(
                ResourcesUtil.getString(mContext, "hdr_format_selection_auto_title"));
        mHdrFormatSelectionAuto.setSummary(
                ResourcesUtil.getString(mContext, "hdr_format_selection_auto_desc"));
        mHdrFormatSelectionAuto.setType(PreferenceCompat.TYPE_RADIO);
        mHdrFormatSelectionAuto.setRadioGroup(KEY_HDR_FORMAT_SELECTION);

        mHdrFormatSelectionManual = mPreferenceCompatManager.getOrCreatePrefCompat(
                new String[]{KEY_HDR_FORMAT_SELECTION, KEY_HDR_FORMAT_SELECTION_MANUAL}
        );
        mHdrFormatSelectionManual.setTitle(
                ResourcesUtil.getString(mContext, "hdr_format_selection_manual_title"));
        mHdrFormatSelectionManual.setSummary(
                ResourcesUtil.getString(mContext, "hdr_format_selection_manual_desc"));
        mHdrFormatSelectionManual.setType(PreferenceCompat.TYPE_RADIO);
        mHdrFormatSelectionManual.setRadioGroup(KEY_HDR_FORMAT_SELECTION);
        mHdrFormatSelectionCategory.addChildPrefCompat(mHdrFormatSelectionAuto);
        mHdrFormatSelectionCategory.addChildPrefCompat(mHdrFormatSelectionManual);

        createFormatInfo();
        createFormatInfoOnManual();

        PreferenceCompat currentSelection;
        if (mDisplayManager.areUserDisabledHdrTypesAllowed()) {
            currentSelection = mHdrFormatSelectionAuto;
            hideFormatInfoOnManual();
        } else {
            currentSelection = mHdrFormatSelectionManual;
            showFormatInfoOnManual();
        }
        selectRadioPreference(currentSelection);
    }

    /** Creates titles and preferences for each hdr format. */
    private void createFormatInfo() {
        mFormatsInfoCategory = createPreferenceCategory(
                ResourcesUtil.getString(mContext, "hdr_format_info"),
                new String[]{KEY_FORMAT_INFO});

        mShowHideFormatInfo = createPreference("hdr_show_formats",
                new String[]{KEY_FORMAT_INFO, KEY_SHOW_HIDE_FORMAT_INFO});
        mFormatsInfoCategory.addChildPrefCompat(mShowHideFormatInfo);

        mEnabledFormatsCategory = createPreferenceCategory(
                "hdr_enabled_formats",
                new String[]{KEY_FORMAT_INFO, KEY_ENABLED_FORMATS});
        mFormatsInfoCategory.addChildPrefCompat(mEnabledFormatsCategory);

        mDisabledFormatsCategory = createPreferenceCategory(
                "hdr_disabled_formats",
                new String[]{KEY_FORMAT_INFO, KEY_DISABLED_FORMATS});
        mFormatsInfoCategory.addChildPrefCompat(mDisabledFormatsCategory);

        for (int hdrType : HDR_FORMATS_DISPLAY_ORDER) {
            if (mDeviceHdrTypes.contains(hdrType)) {
                String title = getFormatPreferenceTitle(mContext, hdrType);
                if (TextUtils.isEmpty(title)) {
                    continue;
                }

                String key = KEY_HDR_FORMAT_INFO_PREFIX + hdrType;
                String[] compoundKey = mDisplayReportedHdrTypes.contains(hdrType)
                        ? new String[]{KEY_FORMAT_INFO, KEY_ENABLED_FORMATS, key}
                        : new String[]{KEY_FORMAT_INFO, KEY_DISABLED_FORMATS, key};
                PreferenceCompat pref = createPreference(title, compoundKey);
                pref.setTitle(title);
                if (mDisplayReportedHdrTypes.contains(hdrType)) {
                    mEnabledFormatsCategory.addChildPrefCompat(pref);
                } else {
                    mDisabledFormatsCategory.addChildPrefCompat(pref);
                }
            }
        }
        hideFormatInfo();
    }

    private void createFormatInfoOnManual() {
        mFormatsInfoOnManualCategory = createPreferenceCategory(
                "", new String[]{KEY_FORMAT_INFO_ON_MANUAL}
        );
        mSupportedFormatsCategory = createPreferenceCategory(
                "hdr_format_supported_title",
                new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_SUPPORTED_HDR_FORMATS});
        mUnsupportedFormatsCategory = createPreferenceCategory(
                "hdr_format_unsupported_title",
                new String[]{KEY_FORMAT_INFO_ON_MANUAL, KEY_UNSUPPORTED_HDR_FORMATS});

        mFormatsInfoOnManualCategory.addChildPrefCompat(mSupportedFormatsCategory);
        mFormatsInfoOnManualCategory.addChildPrefCompat(mUnsupportedFormatsCategory);
        List<AbstractPreferenceController> preferenceControllers = new ArrayList<>();
        for (int hdrType : HDR_FORMATS_DISPLAY_ORDER) {
            if (mDeviceHdrTypes.contains(hdrType)) {
                HdrFormatPreferenceController hdrFormatPC = new HdrFormatPreferenceController(
                        mContext, mUIUpdateCallback, getStateIdentifier(),
                        mPreferenceCompatManager, hdrType, mDisplayManager,
                        mDisplayReportedHdrTypes, mUserDisabledHdrTypes
                );
                preferenceControllers.add(hdrFormatPC);
                hdrFormatPC.init();
                if (hdrFormatPC.isAvailable()) {
                    if (mDisplayReportedHdrTypes.contains(hdrType)) {
                        mSupportedFormatsCategory.addChildPrefCompat(
                                hdrFormatPC.getPrefCompat());
                    } else {
                        mUnsupportedFormatsCategory.addChildPrefCompat(
                                hdrFormatPC.getPrefCompat());
                    }
                }
            }
        }
        mPreferenceControllers.addAll(preferenceControllers);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        PreferenceCompat pref = mPreferenceCompatManager.getPrefCompat(key);
        if (pref == null) {
            return false;
        }
        if (pref.getType() == PreferenceCompat.TYPE_RADIO) {
            selectRadioPreference(pref);

            if (pref.getKey().length != 2) {
                return false;
            }
            switch (key[1]) {
                case KEY_HDR_FORMAT_SELECTION_AUTO: {
                    mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
                    hideFormatInfoOnManual();
                    break;
                }
                case KEY_HDR_FORMAT_SELECTION_MANUAL: {
                    mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
                    showFormatInfoOnManual();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown surround sound pref value: "
                            + key);
            }
            updateFormatStates();
            return true;
        }

        if (key.length == 2 && key[0].equals(KEY_FORMAT_INFO) && key[1].equals(
                KEY_SHOW_HIDE_FORMAT_INFO)) {
            if (pref.getTitle().equals(
                    ResourcesUtil.getString(mContext, "hdr_hide_formats"))) {
                pref.setTitle(ResourcesUtil.getString(mContext, "hdr_show_formats"));
                hideFormatInfo();
            } else {
                pref.setTitle(ResourcesUtil.getString(mContext, "hdr_hide_formats"));
                showFormatInfo();
            }
            return true;
        }

        if (key.length == 3 && key[0].equals(KEY_FORMAT_INFO) && key[2].contains(
                KEY_HDR_FORMAT_INFO_PREFIX)) {
            if (PreferenceCompatUtils.isParent(pref, mEnabledFormatsCategory)) {
                LibUtils.showToast(mContext, "surround_sound_enabled_format_info_clicked");
            }
            return true;
        }

        return super.onPreferenceTreeClick(key, status);
    }

    private void showFormatInfoOnManual() {
        mSupportedFormatsCategory.setVisible(true);
        mUnsupportedFormatsCategory.setVisible(true);
        updateFormatStates();
        // hide the formats info section.
        mFormatsInfoCategory.setVisible(false);
        notifyUpdateFormatInfo();
    }

    private void hideFormatInfoOnManual() {
        mSupportedFormatsCategory.setVisible(false);
        mUnsupportedFormatsCategory.setVisible(false);
        updateFormatStates();
        // show the formats info section.
        mFormatsInfoCategory.setVisible(true);
        notifyUpdateFormatInfo();
    }

    private void showFormatInfo() {
        mEnabledFormatsCategory.setVisible(true);
        mDisabledFormatsCategory.setVisible(true);
        notifyUpdateFormatInfo();
    }

    private void hideFormatInfo() {
        mEnabledFormatsCategory.setVisible(false);
        mDisabledFormatsCategory.setVisible(false);
        notifyUpdateFormatInfo();
    }

    private void updateFormatStates() {
        for (AbstractPreferenceController controller : mPreferenceControllers) {
            if (controller instanceof HdrFormatPreferenceController) {
                ((HdrFormatPreferenceController) controller).update();
            }
        }
    }

    private void notifyUpdateFormatInfo() {
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(),
                mFormatsInfoOnManualCategory);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mFormatsInfoCategory);
    }

    /**
     * @return the display id for each hdr type.
     */
    static String getFormatPreferenceTitle(Context context, int hdrType) {
        switch (hdrType) {
            case HDR_TYPE_DOLBY_VISION:
                return ResourcesUtil.getString(context, "hdr_format_dolby_vision");
            case HDR_TYPE_HDR10:
                return ResourcesUtil.getString(context, "hdr_format_hdr10");
            case HDR_TYPE_HLG:
                return ResourcesUtil.getString(context, "hdr_format_hlg");
            case HDR_TYPE_HDR10_PLUS:
                return ResourcesUtil.getString(context, "hdr_format_hdr10plus");
            default:
                return "";
        }
    }

    private PreferenceCompat createPreferenceCategory(String valueName, String[] key) {
        PreferenceCompat preferenceCategory = mPreferenceCompatManager.getOrCreatePrefCompat(key);
        preferenceCategory.setType(PreferenceCompat.TYPE_PREFERENCE_CATEGORY);
        preferenceCategory.setTitle(ResourcesUtil.getString(mContext, valueName));
        return preferenceCategory;
    }


    private PreferenceCompat createPreference(String titleResourceId, String[] key) {
        PreferenceCompat preference = mPreferenceCompatManager.getOrCreatePrefCompat(key);
        preference.setTitle(ResourcesUtil.getString(mContext, titleResourceId));
        return preference;
    }

    private void selectRadioPreference(PreferenceCompat preferenceCompat) {
        preferenceCompat.setChecked(true);
        mHdrFormatSelectionCategory.getChildPrefCompats().stream()
                .filter(radioPref -> radioPref != preferenceCompat)
                .forEach(radioPref -> radioPref.setChecked(false));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mHdrFormatSelectionCategory);
    }

    private DisplayManager getDisplayManager() {
        return mContext.getSystemService(DisplayManager.class);
    }

    private int[] getDeviceSupportedHdrTypes() {
        return ResourcesUtil.getIntArray(mContext, "config_deviceSupportedHdrFormats");
    }

    private Set<Integer> toSet(int[] array) {
        return Arrays.stream(array).boxed().collect(Collectors.toSet());
    }

    @Override
    public int getStateIdentifier() {
        return STATE_HDR_FORMAT_SELECTION;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}
