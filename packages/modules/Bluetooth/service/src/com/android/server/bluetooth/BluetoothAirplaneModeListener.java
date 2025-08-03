/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.server.bluetooth;

import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.flags.FeatureFlags;
import com.android.internal.annotations.VisibleForTesting;

/**
 * The BluetoothAirplaneModeListener handles system airplane mode change callback and checks whether
 * we need to inform BluetoothManagerService on this change.
 *
 * <p>The information of airplane mode turns on would not be passed to the BluetoothManagerService
 * when Bluetooth is on and Bluetooth is in one of the following situations:
 *
 * <ul>
 *   <li>Bluetooth A2DP is connected.
 *   <li>Bluetooth Hearing Aid profile is connected.
 *   <li>Bluetooth LE Audio is connected
 * </ul>
 */
class BluetoothAirplaneModeListener extends Handler {
    private static final String TAG = BluetoothAirplaneModeListener.class.getSimpleName();

    @VisibleForTesting static final String TOAST_COUNT = "bluetooth_airplane_toast_count";

    // keeps track of whether wifi should remain on in airplane mode
    public static final String WIFI_APM_STATE = "wifi_apm_state";
    // keeps track of whether wifi and bt remains on notification was shown
    public static final String APM_WIFI_BT_NOTIFICATION = "apm_wifi_bt_notification";
    // keeps track of whether bt remains on notification was shown
    public static final String APM_BT_NOTIFICATION = "apm_bt_notification";
    // keeps track of whether airplane mode enhancement feature is enabled
    public static final String APM_ENHANCEMENT = "apm_enhancement_enabled";
    // keeps track of whether user changed bt state in airplane mode
    public static final String APM_USER_TOGGLED_BLUETOOTH = "apm_user_toggled_bluetooth";
    // keeps track of whether bt should remain on in airplane mode
    public static final String BLUETOOTH_APM_STATE = "bluetooth_apm_state";
    // keeps track of whether user enabling bt notification was shown
    public static final String APM_BT_ENABLED_NOTIFICATION = "apm_bt_enabled_notification";

    private static final int MSG_AIRPLANE_MODE_CHANGED = 0;
    public static final int NOTIFICATION_NOT_SHOWN = 0;
    public static final int NOTIFICATION_SHOWN = 1;
    public static final int UNUSED = 0;
    public static final int USED = 1;

    private static final int BLUETOOTH_OFF_APM = 0;
    private static final int BLUETOOTH_ON_APM = 1;

    @VisibleForTesting static final int MAX_TOAST_COUNT = 10; // 10 times

    /* Tracks the bluetooth state before entering airplane mode*/
    private boolean mIsBluetoothOnBeforeApmToggle = false;
    /* Tracks the bluetooth state after entering airplane mode*/
    private boolean mIsBluetoothOnAfterApmToggle = false;
    /* Tracks whether user toggled bluetooth in airplane mode */
    private boolean mUserToggledBluetoothDuringApm = false;
    /* Tracks whether user toggled bluetooth in airplane mode within one minute */
    private boolean mUserToggledBluetoothDuringApmWithinMinute = false;
    /* Tracks whether media profile was connected before entering airplane mode */
    private boolean mIsMediaProfileConnectedBeforeApmToggle = false;
    /* Tracks when airplane mode has been enabled */
    private long mApmEnabledTime = 0;

    private final BluetoothManagerService mBluetoothManager;
    private final FeatureFlags mFeatureFlags;
    private final Context mContext;
    private BluetoothModeChangeHelper mAirplaneHelper;
    private final BluetoothNotificationManager mNotificationManager;

    private boolean mIsAirplaneModeOn;

    @VisibleForTesting int mToastCount = 0;

    BluetoothAirplaneModeListener(
            BluetoothManagerService service,
            Looper looper,
            Context context,
            BluetoothNotificationManager notificationManager,
            FeatureFlags featureFlags) {
        super(looper);

        mBluetoothManager = service;
        mFeatureFlags = featureFlags;
        mNotificationManager = notificationManager;
        mContext = context;

        String airplaneModeRadios =
                Settings.Global.getString(
                        mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_RADIOS);
        if (airplaneModeRadios != null
                && !airplaneModeRadios.contains(Settings.Global.RADIO_BLUETOOTH)) {
            Log.w(TAG, "BluetoothAirplaneModeListener: blocked by AIRPLANE_MODE_RADIOS");
            mIsAirplaneModeOn = false;
            return;
        }

        mIsAirplaneModeOn = isGlobalAirplaneModeOn(mContext);

        mContext.getContentResolver()
                .registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                        true,
                        new ContentObserver(this) {
                            @Override
                            public void onChange(boolean selfChange) {
                                // This is called on the looper and doesn't need a lock
                                boolean isGlobalAirplaneModeOn = isGlobalAirplaneModeOn(mContext);
                                if (mIsAirplaneModeOn == isGlobalAirplaneModeOn) {
                                    Log.d(
                                            TAG,
                                            "Ignore airplane mode change:"
                                                    + (" mIsAirplaneModeOn=" + mIsAirplaneModeOn));
                                    return;
                                }
                                mIsAirplaneModeOn = isGlobalAirplaneModeOn;
                                handleAirplaneModeChange(mIsAirplaneModeOn);
                            }
                        });
    }

    /** Do not use outside of this class to avoid async issues */
    private static boolean isGlobalAirplaneModeOn(Context ctx) {
        return BluetoothServerProxy.getInstance()
                        .settingsGlobalGetInt(
                                ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                == 1;
    }

    /** return true if airplaneMode is currently On */
    boolean isAirplaneModeOn() {
        return mIsAirplaneModeOn;
    }

    /** Call after boot complete */
    @VisibleForTesting
    void start(BluetoothModeChangeHelper helper) {
        Log.i(TAG, "start");
        mAirplaneHelper = helper;
        mToastCount = mAirplaneHelper.getSettingsInt(TOAST_COUNT);
    }

    @VisibleForTesting
    boolean shouldPopToast() {
        if (mToastCount >= MAX_TOAST_COUNT) {
            return false;
        }
        mToastCount++;
        mAirplaneHelper.setSettingsInt(TOAST_COUNT, mToastCount);
        return true;
    }

    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    void handleAirplaneModeChange(boolean isAirplaneModeOn) {
        if (mAirplaneHelper == null) {
            return;
        }
        if (isAirplaneModeOn) {
            mApmEnabledTime = SystemClock.elapsedRealtime();
            mIsBluetoothOnBeforeApmToggle = mAirplaneHelper.isBluetoothOn();
            mIsMediaProfileConnectedBeforeApmToggle = mBluetoothManager.isMediaProfileConnected();
            mIsBluetoothOnAfterApmToggle =
                    shouldSkipAirplaneModeChange(mIsMediaProfileConnectedBeforeApmToggle);
            if (mIsBluetoothOnAfterApmToggle) {
                Log.i(TAG, "Ignore airplane mode change");
                // Airplane mode enabled when Bluetooth is being used for audio/hearing aid.
                // Bluetooth is not disabled in such case, only state is changed to
                // BLUETOOTH_ON_AIRPLANE mode.
                mAirplaneHelper.setSettingsInt(
                        Settings.Global.BLUETOOTH_ON,
                        BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
                displayUserNotificationIfNeeded();
                return;
            }
        } else {
            BluetoothStatsLog.write(
                    BluetoothStatsLog.AIRPLANE_MODE_SESSION_REPORTED,
                    BluetoothStatsLog.AIRPLANE_MODE_SESSION_REPORTED__PACKAGE_NAME__BLUETOOTH,
                    mIsBluetoothOnBeforeApmToggle,
                    mIsBluetoothOnAfterApmToggle,
                    mAirplaneHelper.isBluetoothOn(),
                    isBluetoothToggledOnApm(),
                    mUserToggledBluetoothDuringApm,
                    mUserToggledBluetoothDuringApmWithinMinute,
                    mIsMediaProfileConnectedBeforeApmToggle);
            mUserToggledBluetoothDuringApm = false;
            mUserToggledBluetoothDuringApmWithinMinute = false;
        }
        mBluetoothManager.onAirplaneModeChanged(isAirplaneModeOn);
    }

    private void displayUserNotificationIfNeeded() {
        if (!isApmEnhancementEnabled() || !isBluetoothToggledOnApm()) {
            if (shouldPopToast()) {
                mAirplaneHelper.showToastMessage();
            }
            return;
        } else {
            if (mFeatureFlags.airplaneRessourcesInApp()) {
                if (isWifiEnabledOnApm()) {
                    mBluetoothManager.sendToggleNotification(APM_WIFI_BT_NOTIFICATION);
                } else {
                    mBluetoothManager.sendToggleNotification(APM_BT_NOTIFICATION);
                }
                return;
            }
            if (isWifiEnabledOnApm() && isFirstTimeNotification(APM_WIFI_BT_NOTIFICATION)) {
                try {
                    sendApmNotification(
                            "bluetooth_and_wifi_stays_on_title",
                            "bluetooth_and_wifi_stays_on_message",
                            APM_WIFI_BT_NOTIFICATION);
                } catch (Exception e) {
                    Log.e(TAG, "APM enhancement BT and Wi-Fi stays on notification not shown");
                }
            } else if (!isWifiEnabledOnApm() && isFirstTimeNotification(APM_BT_NOTIFICATION)) {
                try {
                    sendApmNotification(
                            "bluetooth_stays_on_title",
                            "bluetooth_stays_on_message",
                            APM_BT_NOTIFICATION);
                } catch (Exception e) {
                    Log.e(TAG, "APM enhancement BT stays on notification not shown");
                }
            }
        }
    }

    @VisibleForTesting
    boolean shouldSkipAirplaneModeChange(boolean isMediaProfileConnected) {
        boolean apmEnhancementUsed = isApmEnhancementEnabled() && isBluetoothToggledOnApm();

        // APM feature disabled or user has not used the feature yet by changing BT state in APM
        // BT will only remain on in APM when media profile is connected
        if (!apmEnhancementUsed && mAirplaneHelper.isBluetoothOn() && isMediaProfileConnected) {
            return true;
        }
        // APM feature enabled and user has used the feature by changing BT state in APM
        // BT will only remain on in APM based on user's last action in APM
        if (apmEnhancementUsed
                && mAirplaneHelper.isBluetoothOn()
                && mAirplaneHelper.isBluetoothOnAPM()) {
            return true;
        }
        // APM feature enabled and user has not used the feature yet by changing BT state in APM
        // BT will only remain on in APM if the default value is set to on
        if (isApmEnhancementEnabled()
                && !isBluetoothToggledOnApm()
                && mAirplaneHelper.isBluetoothOn()
                && mAirplaneHelper.isBluetoothOnAPM()) {
            return true;
        }
        return false;
    }

    private boolean isApmEnhancementEnabled() {
        return mAirplaneHelper.getSettingsInt(APM_ENHANCEMENT) == 1;
    }

    private boolean isBluetoothToggledOnApm() {
        return mAirplaneHelper.getSettingsSecureInt(APM_USER_TOGGLED_BLUETOOTH, UNUSED) == USED;
    }

    private boolean isWifiEnabledOnApm() {
        return mAirplaneHelper.getSettingsInt(Settings.Global.WIFI_ON) != 0
                && mAirplaneHelper.getSettingsSecureInt(WIFI_APM_STATE, 0) == 1;
    }

    /** Helper method to send APM notification */
    public void sendApmNotification(String titleId, String messageId, String notificationState)
            throws PackageManager.NameNotFoundException {
        String btPackageName = mAirplaneHelper.getBluetoothPackageName();
        if (btPackageName == null) {
            Log.e(
                    TAG,
                    "Unable to find Bluetooth package name with " + "APM notification resources");
            return;
        }
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(btPackageName);
        int title = resources.getIdentifier(titleId, "string", btPackageName);
        int message = resources.getIdentifier(messageId, "string", btPackageName);
        mNotificationManager.sendApmNotification(
                resources.getString(title), resources.getString(message));
        mAirplaneHelper.setSettingsSecureInt(notificationState, NOTIFICATION_SHOWN);
    }

    /** Helper method to update whether user toggled Bluetooth in airplane mode */
    public void notifyUserToggledBluetooth(boolean isOn) {
        if (!mIsAirplaneModeOn) {
            // User not in Airplane mode, discard event
            return;
        }
        if (!mUserToggledBluetoothDuringApm) {
            mUserToggledBluetoothDuringApmWithinMinute =
                    SystemClock.elapsedRealtime() - mApmEnabledTime < 60000;
        }
        mUserToggledBluetoothDuringApm = true;
        if (isApmEnhancementEnabled()) {
            setSettingsSecureInt(BLUETOOTH_APM_STATE, isOn ? BLUETOOTH_ON_APM : BLUETOOTH_OFF_APM);
            setSettingsSecureInt(APM_USER_TOGGLED_BLUETOOTH, USED);
            if (mFeatureFlags.airplaneRessourcesInApp()) {
                if (isOn) {
                    mBluetoothManager.sendToggleNotification(APM_BT_ENABLED_NOTIFICATION);
                }
                return;
            }
            if (isOn && isFirstTimeNotification(APM_BT_ENABLED_NOTIFICATION)) {
                // waive WRITE_SECURE_SETTINGS permission check
                final long callingIdentity = Binder.clearCallingIdentity();
                try {
                    sendApmNotification(
                            "bluetooth_enabled_apm_title",
                            "bluetooth_enabled_apm_message",
                            APM_BT_ENABLED_NOTIFICATION);
                } catch (Exception e) {
                    Log.e(TAG, "APM enhancement BT enabled notification not shown");
                } finally {
                    Binder.restoreCallingIdentity(callingIdentity);
                }
            }
        }
    }

    /** Return whether APM notification has been shown */
    private boolean isFirstTimeNotification(String name) {
        // waive WRITE_SECURE_SETTINGS permission check
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            Context userContext =
                    mContext.createContextAsUser(
                            UserHandle.of(ActivityManager.getCurrentUser()), 0);
            return mAirplaneHelper.getSettingsSecureInt(name, NOTIFICATION_NOT_SHOWN)
                    == NOTIFICATION_NOT_SHOWN;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /** Set the Settings Secure Int value for foreground user */
    private void setSettingsSecureInt(String name, int value) {
        // waive WRITE_SECURE_SETTINGS permission check
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            Context userContext =
                    mContext.createContextAsUser(
                            UserHandle.of(ActivityManager.getCurrentUser()), 0);
            Settings.Secure.putInt(userContext.getContentResolver(), name, value);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }
}
