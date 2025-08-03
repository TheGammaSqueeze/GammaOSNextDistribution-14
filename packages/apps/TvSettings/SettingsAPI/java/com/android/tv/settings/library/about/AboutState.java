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

package com.android.tv.settings.library.about;

import static com.android.tv.settings.library.ManagerUtil.STATE_SYSTEM_ABOUT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SELinux;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.LibUtils;
import com.android.tv.settings.library.util.PreferenceCompatUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The "About" screen in TV settings.
 */
@Keep
public class AboutState implements State {
    private static final String TAG = "AboutFragment";

    private static final String KEY_MANUAL = "manual";
    private static final String KEY_REGULATORY_INFO = "regulatory_info";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String PROPERTY_URL_SAFETYLEGAL = "ro.url.safetylegal";
    private static final String PROPERTY_SELINUX_STATUS = "ro.build.selinux";
    private static final String KEY_KERNEL_VERSION = "kernel_version";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_SELINUX_STATUS = "selinux_status";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_FIRMWARE_VERSION = "firmware_version";
    private static final String KEY_SECURITY_PATCH = "security_patch";
    private static final String KEY_UPDATE_SETTING = "additional_system_update_settings";
    private static final String KEY_EQUIPMENT_ID = "fcc_equipment_id";
    private static final String PROPERTY_EQUIPMENT_ID = "ro.ril.fccid";
    private static final String KEY_DEVICE_FEEDBACK = "device_feedback";
    private static final String KEY_SAFETY_LEGAL = "safetylegal";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_TUTORIALS = "tutorials";
    private static final String KEY_RESET = "reset";
    private static final String KEY_RESET_OPTIONS = "reset_options";

    static final int TAPS_TO_BE_A_DEVELOPER = 7;

    long[] mHits = new long[3];
    int mDevHitCountdown;
    Toast mDevHitToast;

    private UserManager mUm;

    private final BroadcastReceiver mDeviceNameReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDeviceName();
        }
    };

    private final Context context;
    private final UIUpdateCallback uiUpdateCallback;
    private PreferenceCompat mFirmwareVersionPref;
    private PreferenceCompat mDeviceTutorialsPref;
    private PreferenceCompat mMDeviceTutorialsPref;

    public AboutState(Context context, UIUpdateCallback uiUpdateCallback) {
        this.context = context;
        this.uiUpdateCallback = uiUpdateCallback;
    }

    private PreferenceCompatManager mPreferenceCompatManager;

    @Override
    public void onAttach() {
        // no-op
    }

    @Override
    public void onCreate(Bundle extra) {
        mUm = UserManager.get(context);

        mPreferenceCompatManager = new PreferenceCompatManager();
        refreshDeviceName();
        final PreferenceCompat deviceNamePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_DEVICE_NAME);
        PreferenceCompatUtils.resolveSystemActivityOrRemove(context, preferenceCompats,
                deviceNamePref, 0);

        mFirmwareVersionPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_FIRMWARE_VERSION);
        mFirmwareVersionPref.setSummary(Build.VERSION.RELEASE_OR_CODENAME);
        mFirmwareVersionPref.setEnabled(true);

        final PreferenceCompat securityPatchPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SECURITY_PATCH);
        final String patch = DeviceInfoUtils.getSecurityPatch();
        if (!TextUtils.isEmpty(patch)) {
            securityPatchPref.setSummary(patch);
        } else {
            removePreference(securityPatchPref);
        }

        mPreferenceCompatManager.getOrCreatePrefCompat(KEY_DEVICE_MODEL).setSummary(
                Build.MODEL + DeviceInfoUtils.getMsvSuffix());
        mPreferenceCompatManager.getOrCreatePrefCompat(KEY_EQUIPMENT_ID)
                .setSummary(getSystemPropertySummary(PROPERTY_EQUIPMENT_ID));

        final PreferenceCompat buildNumberPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_BUILD_NUMBER);
        buildNumberPref.setSummary(Build.DISPLAY);
        buildNumberPref.setEnabled(true);
        mPreferenceCompatManager.getOrCreatePrefCompat(KEY_KERNEL_VERSION)
                .setSummary(DeviceInfoUtils.getFormattedKernelVersion(context));

        final PreferenceCompat selinuxPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SELINUX_STATUS);
        if (!SELinux.isSELinuxEnabled()) {
            selinuxPref.setSummary(ResourcesUtil.getString(context, "selinux_status_disabled"));
        } else if (!SELinux.isSELinuxEnforced()) {
            selinuxPref.setSummary(ResourcesUtil.getString(context, "selinux_status_permissive"));
        }

        // Remove selinux information if property is not present
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_SELINUX_STATUS))) {
            removePreference(selinuxPref);
        }

        // Remove Safety information preference if PROPERTY_URL_SAFETYLEGAL is not set
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_URL_SAFETYLEGAL))) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_SAFETY_LEGAL));
        }

        // Remove Equipment id preference if FCC ID is not set by RIL
        if (TextUtils.isEmpty(SystemProperties.get(PROPERTY_EQUIPMENT_ID))) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_EQUIPMENT_ID));
        }

        // Remove Baseband version if wifi-only device
        if (Utils.isWifiOnly(context)) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_BASEBAND_VERSION));
        }

        // Don't show feedback option if there is no reporter.
        if (TextUtils.isEmpty(DeviceInfoUtils.getFeedbackReporterPackage(context))) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_DEVICE_FEEDBACK));
        }

        final PreferenceCompat resetPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_RESET);
        resetPreference.setContentDescription(
                ResourcesUtil.getString(context, "factory_reset_content_description"));

        // Don't show the reset options if factory reset is restricted
        final PreferenceCompat resetOptionsPreference =
                mPreferenceCompatManager.getOrCreatePrefCompat(KEY_RESET_OPTIONS);
        if (resetOptionsPreference != null
                && RestrictedLockUtilsInternal.checkIfRestrictionEnforced(context,
                UserManager.DISALLOW_FACTORY_RESET, UserHandle.myUserId()) != null) {
            // TODO (b/194102677): Handle setFragment(null);
//            resetOptionsPreference.setFragment(null);
        }

        final PreferenceCompat updateSettingsPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_SYSTEM_UPDATE_SETTINGS);
        updateSettingsPref.setContentDescription(
                ResourcesUtil.getString(context, "system_update_content_description"));

        if (mUm.isAdminUser()) {
            final Intent systemUpdateIntent = new Intent(Settings.ACTION_SYSTEM_UPDATE_SETTINGS);
            final ResolveInfo info =
                    LibUtils.systemIntentIsHandled(context, systemUpdateIntent);
            if (info == null) {
                removePreference(updateSettingsPref);
            } else {
                updateSettingsPref.setTitle(info.loadLabel(context.getPackageManager()).toString());
            }
        } else if (updateSettingsPref != null) {
            // Remove for secondary users
            removePreference(updateSettingsPref);
        }

        // Read platform settings for additional system update setting
        if (!ResourcesUtil.getBoolean(context, "config_additional_system_update_setting_enable")) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_UPDATE_SETTING));
        }

        // Remove manual entry if none present.
        if (!ResourcesUtil.getBoolean(context, "config_show_manual")) {
            removePreference(mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MANUAL));
        }

        // Remove regulatory information if none present.
        final PreferenceCompat regulatoryPref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_REGULATORY_INFO);
        PreferenceCompatUtils.resolveSystemActivityOrRemove(context, preferenceCompats,
                regulatoryPref, 0);

        if (uiUpdateCallback != null) {
            uiUpdateCallback.notifyUpdateAll(getStateIdentifier(), preferenceCompats);
        }
    }

    List<PreferenceCompat> preferenceCompats = new ArrayList<>();

    private void removePreference(@Nullable PreferenceCompat preference) {
        if (preference != null) {
            preferenceCompats.remove(preference);
        }
    }

    @Override
    public void onStart() {
        refreshDeviceName();

        context.registerReceiver(mDeviceNameReceiver,
                new IntentFilter(DeviceManager.ACTION_DEVICE_NAME_UPDATE),
                Context.RECEIVER_EXPORTED_UNAUDITED);
    }

    @Override
    public void onResume() {
        mDevHitCountdown = DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context)
                ? -1 : TAPS_TO_BE_A_DEVELOPER;
        mDevHitToast = null;
        updateTutorials();
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onStop() {
        context.unregisterReceiver(mDeviceNameReceiver);
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onDetach() {

    }

    private void refreshDeviceName() {
        final PreferenceCompat deviceNamePref = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_DEVICE_NAME);
        if (deviceNamePref != null) {
            deviceNamePref.setSummary(DeviceManager.getDeviceName(context));
        }
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        boolean handled = true;
        switch (key[0]) {
            case KEY_FIRMWARE_VERSION:
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                if (mHits[0] >= (SystemClock.uptimeMillis() - 500)) {
                    if (mUm.hasUserRestriction(UserManager.DISALLOW_FUN)) {
                        final RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal
                                .checkIfRestrictionEnforced(context, UserManager.DISALLOW_FUN,
                                        UserHandle.myUserId());
                        if (admin != null) {
                            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(context,
                                    admin);
                        }

                        Log.d(TAG, "Sorry, no fun for you!");
                        return false;
                    }

                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("android",
                            "PlatLogoActivity");
                    try {
                        context.startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to start activity " + intent.toString());
                    }
                }
                break;
            case KEY_BUILD_NUMBER:
//                logEntrySelected(TvSettingsEnums.SYSTEM_ABOUT_BUILD);
                // Don't enable developer options for secondary users.
                if (!mUm.isAdminUser()) {
                    return true;
                }

                if (mUm.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)) {
                    final RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal
                            .checkIfRestrictionEnforced(context,
                                    UserManager.DISALLOW_DEBUGGING_FEATURES, UserHandle.myUserId());
                    if (admin != null) {
                        RestrictedLockUtils.sendShowAdminSupportDetailsIntent(context, admin);
                    }
                    return true;
                }

                if (mDevHitCountdown > 0) {
                    mDevHitCountdown--;
                    if (mDevHitCountdown == 0) {
                        DevelopmentSettingsEnabler
                                .setDevelopmentSettingsEnabled(context, true);
                        if (mDevHitToast != null) {
                            mDevHitToast.cancel();
                        }
                        mDevHitToast = Toast.makeText(context,
                                ResourcesUtil.getString(context, "show_dev_on"),
                                Toast.LENGTH_LONG);
                        mDevHitToast.show();
                        // This is good time to index the Developer Options
//                    Index.getInstance(
//                            getActivity().getApplicationContext()).updateFromClassNameResource(
//                            DevelopmentSettings.class.getName(), true, true);
                    } else if (mDevHitCountdown > 0
                            && mDevHitCountdown < (TAPS_TO_BE_A_DEVELOPER - 2)) {
                        if (mDevHitToast != null) {
                            mDevHitToast.cancel();
                        }
                        mDevHitToast = Toast
                                .makeText(context, ResourcesUtil.getQuantityString(
                                        context, "show_dev_countdown", mDevHitCountdown,
                                        mDevHitCountdown),
                                        Toast.LENGTH_SHORT);
                        mDevHitToast.show();
                    }
                } else if (mDevHitCountdown < 0) {
                    if (mDevHitToast != null) {
                        mDevHitToast.cancel();
                    }
                    mDevHitToast = Toast.makeText(context,
                            ResourcesUtil.getString(context, "show_dev_already"),
                            Toast.LENGTH_LONG);
                    mDevHitToast.show();
                }
                break;
            case KEY_DEVICE_FEEDBACK:
                sendFeedback();
                break;
            case KEY_SYSTEM_UPDATE_SETTINGS:
//                logEntrySelected(TvSettingsEnums.SYSTEM_ABOUT_SYSTEM_UPDATE);
                CarrierConfigManager configManager = (CarrierConfigManager)
                        context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
                PersistableBundle b = configManager.getConfig();
                if (b != null &&
                        b.getBoolean(CarrierConfigManager.KEY_CI_ACTION_ON_SYS_UPDATE_BOOL)) {
                    ciActionOnSysUpdate(b);
                }
                context.startActivity(new Intent(Settings.ACTION_SYSTEM_UPDATE_SETTINGS));
                break;
            case KEY_DEVICE_NAME:
//                logEntrySelected(TvSettingsEnums.SYSTEM_ABOUT_DEVICE_NAME);
                break;
            case KEY_RESET:
//                logEntrySelected(TvSettingsEnums.SYSTEM_ABOUT_FACTORY_RESET);
                Intent factoryResetIntent = new Intent();
                factoryResetIntent.setClassName(
                        "com.android.tv.settings",
                        "com.android.tv.settings.device.storage.ResetActivity");
                context.startActivity(factoryResetIntent);
                break;
            default:
                handled = false;
        }
        return handled;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_SYSTEM_ABOUT;
    }

    /**
     * Trigger client initiated action (send intent) on system update
     */
    private void ciActionOnSysUpdate(PersistableBundle b) {
        String intentStr = b.getString(CarrierConfigManager.
                KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING);
        if (!TextUtils.isEmpty(intentStr)) {
            String extra = b.getString(CarrierConfigManager.
                    KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING);
            String extraVal = b.getString(CarrierConfigManager.
                    KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING);

            Intent intent = new Intent(intentStr);
            if (!TextUtils.isEmpty(extra)) {
                intent.putExtra(extra, extraVal);
            }
            Log.d(TAG, "ciActionOnSysUpdate: broadcasting intent " + intentStr +
                    " with extra " + extra + ", " + extraVal);
            context.getApplicationContext().sendBroadcast(intent);
        }
    }

    private String getSystemPropertySummary(String property) {
        return SystemProperties.get(property,
                ResourcesUtil.getString(context, "device_info_default"));
    }

    private void sendFeedback() {
        String reporterPackage = DeviceInfoUtils.getFeedbackReporterPackage(context);
        if (TextUtils.isEmpty(reporterPackage)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_BUG_REPORT);
        intent.setPackage(reporterPackage);
        context.startActivity(intent);
    }

    private void updateTutorials() {
        mMDeviceTutorialsPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_TUTORIALS);
        if (mMDeviceTutorialsPref != null) {
            final ResolveInfo info = LibUtils.systemIntentIsHandled(context,
                    mMDeviceTutorialsPref.getIntent());
            mMDeviceTutorialsPref.setVisible(info != null);
            if (info != null) {
                mMDeviceTutorialsPref.setTitle(
                        info.loadLabel(context.getPackageManager()).toString());
            }
        }
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {

    }
}
