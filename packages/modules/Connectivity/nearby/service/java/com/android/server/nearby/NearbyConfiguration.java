/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.nearby;

import android.os.Build;
import android.provider.DeviceConfig;

import androidx.annotation.NonNull;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.nearby.managers.DiscoveryProviderManager;

import java.util.concurrent.Executors;

/**
 * A utility class for encapsulating Nearby feature flag configurations.
 */
public class NearbyConfiguration {

    /**
     * Flag used to enable presence legacy broadcast.
     */
    public static final String NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY =
            "nearby_enable_presence_broadcast_legacy";
    /**
     * Flag used to for minimum nano app version to make Nearby CHRE scan work.
     */
    public static final String NEARBY_MAINLINE_NANO_APP_MIN_VERSION =
            "nearby_mainline_nano_app_min_version";

    /**
     * Flag used to allow test mode and customization.
     */
    public static final String NEARBY_SUPPORT_TEST_APP = "nearby_support_test_app";

    /**
     * Flag to control which version of DiscoveryProviderManager should be used.
     */
    public static final String NEARBY_REFACTOR_DISCOVERY_MANAGER =
            "nearby_refactor_discovery_manager";

    /**
     * Flag to guard enable BLE during Nearby Service init time.
     */
    public static final String NEARBY_ENABLE_BLE_IN_INIT = "nearby_enable_ble_in_init";

    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE);

    private final DeviceConfigListener mDeviceConfigListener = new DeviceConfigListener();
    private final Object mDeviceConfigLock = new Object();

    @GuardedBy("mDeviceConfigLock")
    private boolean mEnablePresenceBroadcastLegacy;
    @GuardedBy("mDeviceConfigLock")
    private int mNanoAppMinVersion;
    @GuardedBy("mDeviceConfigLock")
    private boolean mSupportTestApp;
    @GuardedBy("mDeviceConfigLock")
    private boolean mRefactorDiscoveryManager;
    @GuardedBy("mDeviceConfigLock")
    private boolean mEnableBleInInit;

    public NearbyConfiguration() {
        mDeviceConfigListener.start();
    }

    /**
     * Returns the DeviceConfig namespace for Nearby. The {@link DeviceConfig#NAMESPACE_NEARBY} was
     * added in UpsideDownCake, in Tiramisu, we use {@link DeviceConfig#NAMESPACE_TETHERING}.
     */
    public static String getNamespace() {
        if (SdkLevel.isAtLeastU()) {
            return DeviceConfig.NAMESPACE_NEARBY;
        }
        return DeviceConfig.NAMESPACE_TETHERING;
    }

    private static boolean getDeviceConfigBoolean(final String name, final boolean defaultValue) {
        final String value = getDeviceConfigProperty(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    private static int getDeviceConfigInt(final String name, final int defaultValue) {
        final String value = getDeviceConfigProperty(name);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static String getDeviceConfigProperty(String name) {
        return DeviceConfig.getProperty(getNamespace(), name);
    }

    /**
     * Returns whether broadcasting legacy presence spec is enabled.
     */
    public boolean isPresenceBroadcastLegacyEnabled() {
        synchronized (mDeviceConfigLock) {
            return mEnablePresenceBroadcastLegacy;
        }
    }

    public int getNanoAppMinVersion() {
        synchronized (mDeviceConfigLock) {
            return mNanoAppMinVersion;
        }
    }

    /**
     * @return {@code true} when in test mode and allows customization.
     */
    public boolean isTestAppSupported() {
        synchronized (mDeviceConfigLock) {
            return mSupportTestApp;
        }
    }

    /**
     * @return {@code true} if use {@link DiscoveryProviderManager} or use
     * DiscoveryProviderManagerLegacy if {@code false}.
     */
    public boolean refactorDiscoveryManager() {
        synchronized (mDeviceConfigLock) {
            return mRefactorDiscoveryManager;
        }
    }

    /**
     * @return {@code true} if enableBLE() is called during NearbyService init time.
     */
    public boolean enableBleInInit() {
        synchronized (mDeviceConfigLock) {
            return mEnableBleInInit;
        }
    }

    private class DeviceConfigListener implements DeviceConfig.OnPropertiesChangedListener {
        public void start() {
            DeviceConfig.addOnPropertiesChangedListener(getNamespace(),
                    Executors.newSingleThreadExecutor(), this);
            onPropertiesChanged(DeviceConfig.getProperties(getNamespace()));
        }

        @Override
        public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
            synchronized (mDeviceConfigLock) {
                mEnablePresenceBroadcastLegacy = getDeviceConfigBoolean(
                        NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY, false /* defaultValue */);
                mNanoAppMinVersion = getDeviceConfigInt(
                        NEARBY_MAINLINE_NANO_APP_MIN_VERSION, 0 /* defaultValue */);
                mSupportTestApp = !IS_USER_BUILD && getDeviceConfigBoolean(
                        NEARBY_SUPPORT_TEST_APP, false /* defaultValue */);
                mRefactorDiscoveryManager = getDeviceConfigBoolean(
                        NEARBY_REFACTOR_DISCOVERY_MANAGER, false /* defaultValue */);
                mEnableBleInInit = getDeviceConfigBoolean(
                        NEARBY_ENABLE_BLE_IN_INIT, true /* defaultValue */);
            }
        }
    }
}
