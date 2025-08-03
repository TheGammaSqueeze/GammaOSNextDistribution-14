/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiContext;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/* Tracks persisted settings for Wi-Fi and airplane mode interaction */
public class WifiSettingsStore {
    /* Values used to track the current state of Wi-Fi
     * Key: Settings.Global.WIFI_ON
     * Values:
     *     WIFI_DISABLED
     *     WIFI_ENABLED
     *     WIFI_ENABLED_APM_OVERRIDE
     *     WIFI_DISABLED_APM_ON
     */
    @VisibleForTesting
    public static final int WIFI_DISABLED                      = 0;
    @VisibleForTesting
    public static final int WIFI_ENABLED                       = 1;
    /* Wifi enabled while in airplane mode */
    @VisibleForTesting
    public static final int WIFI_ENABLED_APM_OVERRIDE = 2;
    /* Wifi disabled due to airplane mode on */
    @VisibleForTesting
    public static final int WIFI_DISABLED_APM_ON = 3;

    /* Values used to track the current state of airplane mode
     * Key: Settings.Global.AIRPLANE_MODE_ON
     * Values:
     *     APM_DISABLED
     *     APM_ENABLED
     */
    @VisibleForTesting
    public static final int APM_DISABLED                      = 0;
    @VisibleForTesting
    public static final int APM_ENABLED                       = 1;

    /* Values used to track whether Wi-Fi should remain on in airplane mode
     * Key: Settings.Secure WIFI_APM_STATE
     * Values:
     *     WIFI_TURNS_OFF_IN_APM
     *     WIFI_REMAINS_ON_IN_APM
     */
    @VisibleForTesting
    public static final String WIFI_APM_STATE = "wifi_apm_state";
    @VisibleForTesting
    public static final int WIFI_TURNS_OFF_IN_APM = 0;
    @VisibleForTesting
    public static final int WIFI_REMAINS_ON_IN_APM = 1;

    /* Values used to track whether Bluetooth should remain on in airplane mode
     * Key: Settings.Secure BLUETOOTH_APM_STATE
     * Values:
     *     BT_TURNS_OFF_IN_APM
     *     BT_REMAINS_ON_IN_APM
     */
    @VisibleForTesting
    public static final String BLUETOOTH_APM_STATE = "bluetooth_apm_state";
    @VisibleForTesting
    public static final int BT_TURNS_OFF_IN_APM = 0;
    @VisibleForTesting
    public static final int BT_REMAINS_ON_IN_APM = 1;

    /* Values used to track whether a notification has been shown
     * Keys:
     *     Settings.Secure APM_WIFI_NOTIFICATION
     *     Settings.Secure APM_WIFI_ENABLED_NOTIFICATION
     * Values:
     *     NOTIFICATION_NOT_SHOWN
     *     NOTIFICATION_SHOWN
     */
    /* Track whether Wi-Fi remains on in airplane mode notification was shown */
    @VisibleForTesting
    public static final String APM_WIFI_NOTIFICATION = "apm_wifi_notification";
    /* Track whether Wi-Fi enabled in airplane mode notification was shown */
    @VisibleForTesting
    public static final String APM_WIFI_ENABLED_NOTIFICATION = "apm_wifi_enabled_notification";
    @VisibleForTesting
    public static final int NOTIFICATION_NOT_SHOWN = 0;
    @VisibleForTesting
    public static final int NOTIFICATION_SHOWN = 1;

    /**
     * @hide constant copied from {@link Settings.Global}
     * TODO(b/274636414): Migrate to official API in Android V.
     */
    static final String SETTINGS_SATELLITE_MODE_RADIOS = "satellite_mode_radios";
    /**
     * @hide constant copied from {@link Settings.Global}
     * TODO(b/274636414): Migrate to official API in Android V.
     */
    static final String SETTINGS_SATELLITE_MODE_ENABLED = "satellite_mode_enabled";

    /* Persisted state that tracks the wifi & airplane interaction from settings */
    private int mPersistWifiState = WIFI_DISABLED;

    /* Tracks current airplane mode state */
    private boolean mAirplaneModeOn = false;

    /* Tracks the wifi state before entering airplane mode*/
    private boolean mIsWifiOnBeforeEnteringApm = false;

    /* Tracks the wifi state after entering airplane mode*/
    private boolean mIsWifiOnAfterEnteringApm = false;

    /* Tracks whether user toggled wifi in airplane mode */
    private boolean mUserToggledWifiDuringApm = false;

    /* Tracks whether user toggled wifi within one minute of entering airplane mode */
    private boolean mUserToggledWifiAfterEnteringApmWithinMinute = false;

    /* Tracks when airplane mode has been enabled in milliseconds since boot */
    private long mApmEnabledTimeSinceBootMillis = 0;

    private final String mApmEnhancementHelpLink;
    private final WifiContext mContext;
    private final WifiSettingsConfigStore mSettingsConfigStore;
    private final WifiThreadRunner mWifiThreadRunner;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiNotificationManager mNotificationManager;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final WifiMetrics mWifiMetrics;
    private final Clock mClock;
    private boolean mSatelliteModeOn;

    WifiSettingsStore(WifiContext context, WifiSettingsConfigStore sharedPreferences,
            WifiThreadRunner wifiThread, FrameworkFacade frameworkFacade,
            WifiNotificationManager notificationManager, DeviceConfigFacade deviceConfigFacade,
            WifiMetrics wifiMetrics, Clock clock) {
        mContext = context;
        mSettingsConfigStore = sharedPreferences;
        mWifiThreadRunner = wifiThread;
        mFrameworkFacade = frameworkFacade;
        mNotificationManager = notificationManager;
        mDeviceConfigFacade = deviceConfigFacade;
        mWifiMetrics = wifiMetrics;
        mClock = clock;
        mAirplaneModeOn = getPersistedAirplaneModeOn();
        mPersistWifiState = getPersistedWifiState();
        mApmEnhancementHelpLink = mContext.getString(R.string.config_wifiApmEnhancementHelpLink);
        mSatelliteModeOn = getPersistedSatelliteModeOn();
    }

    private int getUserSecureIntegerSetting(String name, int def) {
        Context userContext = mFrameworkFacade.getUserContext(mContext);
        return mFrameworkFacade.getSecureIntegerSetting(userContext, name, def);
    }

    private void setUserSecureIntegerSetting(String name, int value) {
        Context userContext = mFrameworkFacade.getUserContext(mContext);
        mFrameworkFacade.setSecureIntegerSetting(userContext, name, value);
    }

    public synchronized boolean isWifiToggleEnabled() {
        return mPersistWifiState == WIFI_ENABLED
                || mPersistWifiState == WIFI_ENABLED_APM_OVERRIDE;
    }

    /**
     * Returns true if airplane mode is currently on.
     * @return {@code true} if airplane mode is on.
     */
    public synchronized boolean isAirplaneModeOn() {
        return mAirplaneModeOn;
    }

    public synchronized boolean isScanAlwaysAvailableToggleEnabled() {
        return getPersistedScanAlwaysAvailable();
    }

    public synchronized boolean isScanAlwaysAvailable() {
        return !mAirplaneModeOn && getPersistedScanAlwaysAvailable();
    }

    public synchronized boolean isWifiScoringEnabled() {
        return getPersistedWifiScoringEnabled();
    }

    public synchronized boolean isWifiPasspointEnabled() {
        return getPersistedWifiPasspointEnabled();
    }

    public synchronized boolean isWifiScanThrottleEnabled() {
        return getPersistedWifiScanThrottleEnabled();
    }

    public synchronized int getWifiMultiInternetMode() {
        return getPersistedWifiMultiInternetMode();
    }

    public void setPersistWifiState(int state) {
        mPersistWifiState = state;
    }

    private void showNotification(int titleId, int messageId) {
        String settingsPackage = mFrameworkFacade.getSettingsPackageName(mContext);
        if (settingsPackage == null) return;

        Intent openLinkIntent = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(mApmEnhancementHelpLink))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tapPendingIntent = mFrameworkFacade.getActivity(mContext, 0, openLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = mContext.getResources().getString(titleId);
        String message = mContext.getResources().getString(messageId);
        Notification.Builder builder = mFrameworkFacade.makeNotificationBuilder(mContext,
                        WifiService.NOTIFICATION_APM_ALERTS)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(tapPendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                        R.drawable.ic_wifi_settings));
        mNotificationManager.notify(SystemMessage.NOTE_WIFI_APM_NOTIFICATION, builder.build());
    }

    public synchronized boolean handleWifiToggled(boolean wifiEnabled) {
        // Can Wi-Fi be toggled in airplane mode ?
        if (mAirplaneModeOn && !isAirplaneToggleable()) {
            return false;
        }
        if (wifiEnabled) {
            if (mAirplaneModeOn) {
                persistWifiState(WIFI_ENABLED_APM_OVERRIDE);
                if (mDeviceConfigFacade.isApmEnhancementEnabled()) {
                    setUserSecureIntegerSetting(WIFI_APM_STATE, WIFI_REMAINS_ON_IN_APM);
                    if (getUserSecureIntegerSetting(APM_WIFI_ENABLED_NOTIFICATION,
                            NOTIFICATION_NOT_SHOWN) == NOTIFICATION_NOT_SHOWN) {
                        mWifiThreadRunner.post(
                                () -> showNotification(R.string.wifi_enabled_apm_first_time_title,
                                        R.string.wifi_enabled_apm_first_time_message));
                        setUserSecureIntegerSetting(
                                APM_WIFI_ENABLED_NOTIFICATION, NOTIFICATION_SHOWN);
                    }
                }
            } else {
                persistWifiState(WIFI_ENABLED);
            }
        } else {
            // When wifi state is disabled, we do not care
            // if airplane mode is on or not. The scenario of
            // wifi being disabled due to airplane mode being turned on
            // is handled handleAirplaneModeToggled()
            persistWifiState(WIFI_DISABLED);
            if (mDeviceConfigFacade.isApmEnhancementEnabled() && mAirplaneModeOn) {
                setUserSecureIntegerSetting(WIFI_APM_STATE, WIFI_TURNS_OFF_IN_APM);
            }
        }
        if (mAirplaneModeOn) {
            if (!mUserToggledWifiDuringApm) {
                mUserToggledWifiAfterEnteringApmWithinMinute =
                        mClock.getElapsedSinceBootMillis() - mApmEnabledTimeSinceBootMillis
                                < 60_000;
            }
            mUserToggledWifiDuringApm = true;
        }
        return true;
    }

    synchronized boolean updateAirplaneModeTracker() {
        // Is Wi-Fi sensitive to airplane mode changes ?
        if (!isAirplaneSensitive()) {
            return false;
        }

        mAirplaneModeOn = getPersistedAirplaneModeOn();
        return true;
    }

    synchronized void handleAirplaneModeToggled() {
        if (mAirplaneModeOn) {
            mApmEnabledTimeSinceBootMillis = mClock.getElapsedSinceBootMillis();
            mIsWifiOnBeforeEnteringApm = mPersistWifiState == WIFI_ENABLED;
            if (mPersistWifiState == WIFI_ENABLED) {
                if (mDeviceConfigFacade.isApmEnhancementEnabled()
                        && getUserSecureIntegerSetting(WIFI_APM_STATE, WIFI_TURNS_OFF_IN_APM)
                        == WIFI_REMAINS_ON_IN_APM) {
                    persistWifiState(WIFI_ENABLED_APM_OVERRIDE);
                    if (getUserSecureIntegerSetting(APM_WIFI_NOTIFICATION, NOTIFICATION_NOT_SHOWN)
                            == NOTIFICATION_NOT_SHOWN
                            && !isBluetoothEnabledOnApm()) {
                        mWifiThreadRunner.post(
                                () -> showNotification(R.string.apm_enabled_first_time_title,
                                        R.string.apm_enabled_first_time_message));
                        setUserSecureIntegerSetting(APM_WIFI_NOTIFICATION, NOTIFICATION_SHOWN);
                    }
                } else {
                    // Wifi disabled due to airplane on
                    persistWifiState(WIFI_DISABLED_APM_ON);
                }
            }
            mIsWifiOnAfterEnteringApm = mPersistWifiState == WIFI_ENABLED_APM_OVERRIDE;
        } else {
            mWifiMetrics.reportAirplaneModeSession(mIsWifiOnBeforeEnteringApm,
                    mIsWifiOnAfterEnteringApm,
                    mPersistWifiState == WIFI_ENABLED_APM_OVERRIDE,
                    getUserSecureIntegerSetting(APM_WIFI_ENABLED_NOTIFICATION,
                            NOTIFICATION_NOT_SHOWN) == NOTIFICATION_SHOWN,
                    mUserToggledWifiDuringApm, mUserToggledWifiAfterEnteringApmWithinMinute);
            mUserToggledWifiDuringApm = false;
            mUserToggledWifiAfterEnteringApmWithinMinute = false;

            /* On airplane mode disable, restore wifi state if necessary */
            if (mPersistWifiState == WIFI_ENABLED_APM_OVERRIDE
                    || mPersistWifiState == WIFI_DISABLED_APM_ON) {
                persistWifiState(WIFI_ENABLED);
            }
        }
    }

    synchronized void handleWifiScanAlwaysAvailableToggled(boolean isAvailable) {
        persistScanAlwaysAvailableState(isAvailable);
    }

    synchronized boolean handleWifiScoringEnabled(boolean enabled) {
        persistWifiScoringEnabledState(enabled);
        return true;
    }

    /**
     * Handle the Wifi Passpoint enable/disable status change.
     */
    public synchronized void handleWifiPasspointEnabled(boolean enabled) {
        persistWifiPasspointEnabledState(enabled);
    }

    /**
     * Handle the Wifi Multi Internet state change.
     */
    public synchronized void handleWifiMultiInternetMode(int mode) {
        persistWifiMultiInternetMode(mode);
    }

    /**
     * Indicate whether Wi-Fi should remain on when airplane mode is enabled
     */
    public boolean shouldWifiRemainEnabledWhenApmEnabled() {
        return mDeviceConfigFacade.isApmEnhancementEnabled()
                && isWifiToggleEnabled()
                && (getUserSecureIntegerSetting(WIFI_APM_STATE,
                WIFI_TURNS_OFF_IN_APM) == WIFI_REMAINS_ON_IN_APM);
    }

    private boolean isBluetoothEnabledOnApm() {
        return mFrameworkFacade.getIntegerSetting(mContext.getContentResolver(),
                Settings.Global.BLUETOOTH_ON, 0) != 0
                && getUserSecureIntegerSetting(BLUETOOTH_APM_STATE, BT_TURNS_OFF_IN_APM)
                == BT_REMAINS_ON_IN_APM;
    }

    synchronized void updateSatelliteModeTracker() {
        mSatelliteModeOn = getPersistedSatelliteModeOn();
    }

    public boolean isSatelliteModeOn() {
        return mSatelliteModeOn;
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiState " + getPersistedWifiState());
        pw.println("AirplaneModeOn " + getPersistedAirplaneModeOn());
        pw.println("ScanAlwaysAvailable " + getPersistedScanAlwaysAvailable());
        pw.println("WifiScoringState " + getPersistedWifiScoringEnabled());
        pw.println("WifiPasspointState " + getPersistedWifiPasspointEnabled());
        pw.println("WifiMultiInternetMode " + getPersistedWifiMultiInternetMode());
        pw.println("WifiStateApm " + (getUserSecureIntegerSetting(WIFI_APM_STATE,
                WIFI_TURNS_OFF_IN_APM) == WIFI_REMAINS_ON_IN_APM));
        pw.println("WifiStateBt " + isBluetoothEnabledOnApm());
        pw.println("WifiStateUser " + ActivityManager.getCurrentUser());
        pw.println("AirplaneModeEnhancementEnabled "
                + mDeviceConfigFacade.isApmEnhancementEnabled());
        if (mAirplaneModeOn) {
            pw.println("WifiOnBeforeEnteringApm " + mIsWifiOnBeforeEnteringApm);
            pw.println("WifiOnAfterEnteringApm " + mIsWifiOnAfterEnteringApm);
            pw.println("UserToggledWifiDuringApm " + mUserToggledWifiDuringApm);
            pw.println("UserToggledWifiAfterEnteringApmWithinMinute "
                    + mUserToggledWifiAfterEnteringApmWithinMinute);
        }
        pw.println("SatelliteModeOn " + mSatelliteModeOn);
    }

    private void persistWifiState(int state) {
        final ContentResolver cr = mContext.getContentResolver();
        mPersistWifiState = state;
        mFrameworkFacade.setIntegerSetting(cr, Settings.Global.WIFI_ON, state);
    }

    private void persistScanAlwaysAvailableState(boolean isAvailable) {
        mSettingsConfigStore.put(
                WifiSettingsConfigStore.WIFI_SCAN_ALWAYS_AVAILABLE, isAvailable);
    }

    private void persistWifiScoringEnabledState(boolean enabled) {
        mSettingsConfigStore.put(
                WifiSettingsConfigStore.WIFI_SCORING_ENABLED, enabled);
    }

    private void persistWifiPasspointEnabledState(boolean enabled) {
        mSettingsConfigStore.put(
                WifiSettingsConfigStore.WIFI_PASSPOINT_ENABLED, enabled);
    }

    private void persistWifiMultiInternetMode(int mode) {
        mSettingsConfigStore.put(
                WifiSettingsConfigStore.WIFI_MULTI_INTERNET_MODE, mode);
    }

    /* Does Wi-Fi need to be disabled when airplane mode is on ? */
    private boolean isAirplaneSensitive() {
        String airplaneModeRadios = mFrameworkFacade.getStringSetting(mContext,
                Settings.Global.AIRPLANE_MODE_RADIOS);
        return airplaneModeRadios == null
                || airplaneModeRadios.contains(Settings.Global.RADIO_WIFI);
    }

    /* Is Wi-Fi allowed to be re-enabled while airplane mode is on ? */
    private boolean isAirplaneToggleable() {
        String toggleableRadios = mFrameworkFacade.getStringSetting(mContext,
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        return toggleableRadios != null
                && toggleableRadios.contains(Settings.Global.RADIO_WIFI);
    }

    private int getPersistedWifiState() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            return mFrameworkFacade.getIntegerSetting(cr, Settings.Global.WIFI_ON);
        } catch (Settings.SettingNotFoundException e) {
            mFrameworkFacade.setIntegerSetting(cr, Settings.Global.WIFI_ON, WIFI_DISABLED);
            return WIFI_DISABLED;
        }
    }

    private boolean getPersistedAirplaneModeOn() {
        return mFrameworkFacade.getIntegerSetting(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, APM_DISABLED) == APM_ENABLED;
    }

    private boolean getPersistedScanAlwaysAvailable() {
        return mSettingsConfigStore.get(
                WifiSettingsConfigStore.WIFI_SCAN_ALWAYS_AVAILABLE);
    }

    private boolean getPersistedWifiScoringEnabled() {
        return mSettingsConfigStore.get(
                WifiSettingsConfigStore.WIFI_SCORING_ENABLED);
    }

    private boolean getPersistedWifiPasspointEnabled() {
        return mSettingsConfigStore.get(
                WifiSettingsConfigStore.WIFI_PASSPOINT_ENABLED);
    }

    private boolean getPersistedWifiScanThrottleEnabled() {
        return mSettingsConfigStore.get(
                WifiSettingsConfigStore.WIFI_SCAN_THROTTLE_ENABLED);
    }

    private int getPersistedWifiMultiInternetMode() {
        return mSettingsConfigStore.get(
                WifiSettingsConfigStore.WIFI_MULTI_INTERNET_MODE);
    }

    private boolean getPersistedIsSatelliteModeSensitive() {
        String satelliteRadios = mFrameworkFacade.getStringSetting(mContext,
                SETTINGS_SATELLITE_MODE_RADIOS);
        return satelliteRadios != null
                && satelliteRadios.contains(Settings.Global.RADIO_WIFI);
    }

    /** Returns true if satellite mode is turned on. */
    private boolean getPersistedSatelliteModeOn() {
        if (!getPersistedIsSatelliteModeSensitive()) return false;
        return  mFrameworkFacade.getIntegerSetting(
                mContext, SETTINGS_SATELLITE_MODE_ENABLED, 0) == 1;
    }
}
