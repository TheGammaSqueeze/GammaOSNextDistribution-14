/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.wifi;

import static android.car.settings.CarSettings.Global.ENABLE_PERSISTENT_TETHERING;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_FAILED;

import static com.android.car.CarServiceUtils.getHandlerThread;

import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.wifi.ICarWifi;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.TetheringManager;
import android.net.TetheringManager.StartTetheringCallback;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.SoftApCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;

/**
 * CarWifiService manages Wi-Fi functionality.
 */
public final class CarWifiService extends ICarWifi.Stub implements CarServiceBase {
    private static final String TAG = CarLog.tagFor(CarWifiService.class);
    private static final String SHARED_PREF_NAME = "com.android.car.wifi.car_wifi_service";
    private static final String KEY_PERSIST_TETHERING_ENABLED_LAST =
            "persist_tethering_enabled_last";

    private final Object mLock = new Object();
    private final Context mContext;
    private final boolean mIsPersistTetheringCapabilitiesEnabled;
    private final boolean mIsPersistTetheringSettingEnabled;
    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarUserService mCarUserService;
    private final HandlerThread mHandlerThread =
            getHandlerThread(getClass().getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    private final ICarPowerStateListener mCarPowerStateListener =
            new ICarPowerStateListener.Stub() {
                @Override
                public void onStateChanged(int state, long expirationTimeMs) {
                    if (state == CarPowerManager.STATE_ON) {
                        onStateOn();
                    }
                }
            };

    private final SoftApCallback mSoftApCallback =
            new SoftApCallback() {
                @Override
                public void onStateChanged(int state, int failureReason) {
                    switch (state) {
                        case WIFI_AP_STATE_ENABLED -> {
                            Slogf.i(TAG, "AP enabled successfully");
                            synchronized (mLock) {
                                if (mSharedPreferences != null) {
                                    Slogf.i(TAG,
                                            "WIFI_AP_STATE_ENABLED received, saving state in "
                                                    + "SharedPreferences store");
                                    mSharedPreferences
                                            .edit()
                                            .putBoolean(
                                                    KEY_PERSIST_TETHERING_ENABLED_LAST, /* value= */
                                                    true)
                                            .apply();
                                }
                            }

                            // If the setting is enabled, tethering sessions should remain on even
                            // if no devices are connected to it.
                            if (mIsPersistTetheringCapabilitiesEnabled
                                    && mIsPersistTetheringSettingEnabled) {
                                setSoftApAutoShutdownEnabled(/* enable= */ false);
                            }
                        }
                        case WIFI_AP_STATE_DISABLED -> {
                            synchronized (mLock) {
                                if (mSharedPreferences != null) {
                                    Slogf.i(TAG,
                                            "WIFI_AP_STATE_DISABLED received, saving state in "
                                                    + "SharedPreferences store");
                                    mSharedPreferences
                                            .edit()
                                            .putBoolean(
                                                    KEY_PERSIST_TETHERING_ENABLED_LAST, /* value= */
                                                    false)
                                            .apply();
                                }
                            }
                        }
                        case WIFI_AP_STATE_FAILED -> {
                            Slogf.w(TAG, "WIFI_AP_STATE_FAILED state received");
                            // FAILED state can occur during enabling OR disabling, should keep
                            // previous setting within store.
                        }
                        default -> {
                        }
                    }
                }
            };

    private final ContentObserver mPersistTetheringObserver =
            new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    if (!mIsPersistTetheringCapabilitiesEnabled) {
                        Slogf.i(TAG, "Persist tethering capability is not enabled");
                        return;
                    }

                    Slogf.i(TAG, "%s setting has changed", ENABLE_PERSISTENT_TETHERING);
                    // If the persist tethering setting is turned off, auto shutdown must be
                    // re-enabled.
                    boolean persistTetheringSettingEnabled =
                            mFeatureFlags.persistApSettings() && TextUtils.equals("true",
                                    Settings.Global.getString(mContext.getContentResolver(),
                                            ENABLE_PERSISTENT_TETHERING));
                    setSoftApAutoShutdownEnabled(!persistTetheringSettingEnabled);
                }
            };

    @GuardedBy("mLock")
    private SharedPreferences mSharedPreferences;

    public CarWifiService(Context context, CarPowerManagementService powerManagementService,
            CarUserService userService) {
        mContext = context;
        mIsPersistTetheringCapabilitiesEnabled = context.getResources().getBoolean(
                R.bool.config_enablePersistTetheringCapabilities);
        mIsPersistTetheringSettingEnabled = mFeatureFlags.persistApSettings() && TextUtils.equals(
                "true",
                Settings.Global.getString(context.getContentResolver(),
                        ENABLE_PERSISTENT_TETHERING));
        mWifiManager = context.getSystemService(WifiManager.class);
        mTetheringManager = context.getSystemService(TetheringManager.class);
        mCarPowerManagementService = powerManagementService;
        mCarUserService = userService;
    }

    @Override
    public void init() {
        if (!mIsPersistTetheringCapabilitiesEnabled) {
            Slogf.w(TAG, "Persist tethering capability is not enabled");
            return;
        }

        // Listeners should be set if there's capability so tethering can be ready to turn on if
        // setting is enabled, on next boot.
        mWifiManager.registerSoftApCallback(mHandler::post, mSoftApCallback);
        mCarUserService.runOnUser0Unlock(this::onSystemUserUnlocked);
        mCarPowerManagementService.registerListener(mCarPowerStateListener);

        if (mFeatureFlags.persistApSettings()) {
            mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                            ENABLE_PERSISTENT_TETHERING), /* notifyForDescendants= */ false,
                    mPersistTetheringObserver);
        }
    }

    @Override
    public void release() {
        if (!mIsPersistTetheringCapabilitiesEnabled) {
            Slogf.w(TAG, "Persist tethering capability is not enabled");
            return;
        }

        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
        mCarPowerManagementService.unregisterListener(mCarPowerStateListener);

        if (mFeatureFlags.persistApSettings()) {
            mContext.getContentResolver().unregisterContentObserver(mPersistTetheringObserver);
        }
    }

    @Override
    public void dumpProto(ProtoOutputStream proto) {
        proto.write(CarWifiDumpProto.PERSIST_TETHERING_CAPABILITIES_ENABLED,
                mIsPersistTetheringCapabilitiesEnabled);
        proto.write(CarWifiDumpProto.PERSIST_TETHERING_SETTING_ENABLED,
                mIsPersistTetheringSettingEnabled);
        proto.write(CarWifiDumpProto.TETHERING_ENABLED, mWifiManager.isWifiApEnabled());
        proto.write(CarWifiDumpProto.AUTO_SHUTDOWN_ENABLED,
                mWifiManager.getSoftApConfiguration().isAutoShutdownEnabled());
    }

    @Override
    public void dump(IndentingPrintWriter writer) {
        writer.println("**CarWifiService**");
        writer.println();
        writer.println("Persist Tethering");
        writer.println("mIsPersistTetheringCapabilitiesEnabled: "
                + mIsPersistTetheringCapabilitiesEnabled);
        writer.println("mIsPersistTetheringSettingEnabled: " + mIsPersistTetheringSettingEnabled);
        writer.println("Tethering enabled: " + mWifiManager.isWifiApEnabled());
        writer.println("Auto shutdown enabled: "
                + mWifiManager.getSoftApConfiguration().isAutoShutdownEnabled());
    }

    /**
     * Returns {@code true} if the persist tethering settings are able to be changed.
     */
    @Override
    public boolean canControlPersistTetheringSettings() {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_READ_PERSIST_TETHERING_SETTINGS);
        return mIsPersistTetheringCapabilitiesEnabled;
    }

    /**
     * Starts tethering if it's currently being persisted and was on last.
     * Should only be called given that the SharedPreferences store is properly initialized.
     */
    private void startTethering() {
        if (!mIsPersistTetheringCapabilitiesEnabled || !mIsPersistTetheringSettingEnabled) {
            return;
        }

        if (mWifiManager.isWifiApEnabled()) {
            return;
        }

        synchronized (mLock) {
            if (mSharedPreferences == null || !mSharedPreferences.getBoolean(
                    KEY_PERSIST_TETHERING_ENABLED_LAST, /* defValue= */ false)) {
                Slogf.d(TAG, "Tethering was not enabled last");
                return;
            }
        }

        mTetheringManager.startTethering(TETHERING_WIFI, mHandler::post,
                new StartTetheringCallback() {
                    @Override
                    public void onTetheringFailed(int error) {
                        Slogf.e(TAG, "Starting tethering failed: %d", error);
                    }
                });
    }

    @GuardedBy("mLock")
    private void initSharedPreferencesLocked() {
        // SharedPreferences are shared among different users thus only need initialized once. They
        // should be initialized after user 0 is unlocked because SharedPreferences in
        // credential encrypted storage are not available until after user 0 is unlocked.
        if (mSharedPreferences != null) {
            Slogf.d(TAG, "SharedPreferences store has already been initialized");
            return;
        }

        mSharedPreferences = mContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    private void onStateOn() {
        synchronized (mLock) {
            if (mSharedPreferences == null) {
                Slogf.d(TAG, "SharedPreferences store has not been initialized");
                return;
            }
        }

        // User 0 has been unlocked, SharedPreferences is initialized and accessible.
        startTethering();
    }

    private void onSystemUserUnlocked() {
        synchronized (mLock) {
            initSharedPreferencesLocked();
        }

        if (mCarPowerManagementService.getPowerState() == CarPowerManager.STATE_ON) {
            startTethering();
        }
    }

    private void setSoftApAutoShutdownEnabled(boolean enable) {
        SoftApConfiguration config = new SoftApConfiguration.Builder(
                mWifiManager.getSoftApConfiguration())
                .setAutoShutdownEnabled(enable)
                .build();
        mWifiManager.setSoftApConfiguration(config);
    }
}
