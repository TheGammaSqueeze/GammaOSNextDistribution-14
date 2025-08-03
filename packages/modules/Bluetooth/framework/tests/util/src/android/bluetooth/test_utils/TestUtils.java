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

package android.bluetooth.test_utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.errorprone.annotations.InlineMe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Utility class for Bluetooth CTS test. */
public class TestUtils {
    protected static final String TAG = TestUtils.class.getSimpleName();

    /**
     * Checks whether this device has Bluetooth feature
     *
     * @return true if this device has Bluetooth feature
     */
    public static boolean hasBluetooth() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    /**
     * Adopt shell UID's permission via {@link android.app.UiAutomation}
     *
     * @param permission permission to adopt
     */
    public static void adoptPermissionAsShellUid(@Nullable String... permission) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(permission);
    }

    /** Drop all permissions adopted as shell UID */
    public static void dropPermissionAsShellUid() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * @return permissions adopted from Shell on this process
     */
    public static Set<String> getAdoptedShellPermissions() {
        return InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .getAdoptedShellPermissions();
    }

    /**
     * Utility method to call hidden ScanRecord.parseFromBytes method.
     *
     * @param bytes Raw bytes from BLE payload
     * @return parsed {@link ScanRecord}, null if parsing failed
     */
    public static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass.getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }

    /**
     * Get current location mode settings.
     *
     * @param context current running context
     * @return values among {@link Settings.Secure#LOCATION_MODE_OFF}, {@link
     *     Settings.Secure#LOCATION_MODE_ON}, {@link Settings.Secure#LOCATION_MODE_SENSORS_ONLY},
     *     {@link Settings.Secure#LOCATION_MODE_HIGH_ACCURACY}, {@link
     *     Settings.Secure#LOCATION_MODE_BATTERY_SAVING}
     */
    public static int getLocationMode(Context context) {
        return Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Set location settings mode.
     *
     * @param context current running context
     * @param mode a value for {@link Settings.Secure#LOCATION_MODE} among {@link
     *     Settings.Secure#LOCATION_MODE_OFF}, {@link Settings.Secure#LOCATION_MODE_ON}, {@link
     *     Settings.Secure#LOCATION_MODE_SENSORS_ONLY}, {@link
     *     Settings.Secure#LOCATION_MODE_HIGH_ACCURACY}, {@link
     *     Settings.Secure#LOCATION_MODE_BATTERY_SAVING}
     */
    public static void setLocationMode(Context context, int mode) {
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE, mode);
    }

    /**
     * Return true if location is on.
     *
     * @param context current running context
     * @return true if location mode is in one of the enabled value
     */
    public static boolean isLocationOn(Context context) {
        return getLocationMode(context) != Settings.Secure.LOCATION_MODE_OFF;
    }

    /** Enable location and set the mode to GPS only. */
    public static void enableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
    }

    /**
     * Disable location by setting is to {@link Settings.Secure#LOCATION_MODE_OFF}
     *
     * @param context current running context
     */
    public static void disableLocation(Context context) {
        setLocationMode(context, Settings.Secure.LOCATION_MODE_OFF);
    }

    /**
     * Check if BLE is supported by this platform
     *
     * @param context current device context
     * @return true if BLE is supported, false otherwise
     */
    public static boolean isBleSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Check if this is an automotive device
     *
     * @param context current device context
     * @return true if this Android device is an automotive device, false otherwise
     */
    public static boolean isAutomotive(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Check if this is a watch device
     *
     * @param context current device context
     * @return true if this Android device is a watch device, false otherwise
     */
    public static boolean isWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * Check if this is a TV device
     *
     * @param context current device context
     * @return true if this Android device is a TV device, false otherwise
     */
    public static boolean isTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /** Boilerplate class for profile listener */
    public static class BluetoothCtsServiceConnector {
        // Timeout for Proxy Connect
        private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
        private BluetoothProfile mProfileProxy = null;
        private boolean mIsProfileReady = false;
        private boolean mIsProfileConnecting = false;
        private final Condition mConditionProfileConnection;
        private final ReentrantLock mProfileConnectionLock;
        private final String mLogTag;
        private final int mProfileId;
        private final BluetoothAdapter mAdapter;
        private final Context mContext;

        public BluetoothCtsServiceConnector(
                String logTag, int profileId, BluetoothAdapter adapter, Context context) {
            mLogTag = Objects.requireNonNull(logTag);
            mProfileId = profileId;
            mAdapter = Objects.requireNonNull(adapter);
            mContext = Objects.requireNonNull(context);
            mProfileConnectionLock = new ReentrantLock();
            mConditionProfileConnection = mProfileConnectionLock.newCondition();
        }

        public BluetoothProfile getProfileProxy() {
            return mProfileProxy;
        }

        /** Close profile proxy */
        public void closeProfileProxy() {
            if (mProfileProxy != null) {
                mAdapter.closeProfileProxy(mProfileId, mProfileProxy);
                mProfileProxy = null;
                mIsProfileReady = false;
            }
        }

        /**
         * Open profile proxy
         *
         * @return true if the profile proxy is opened successfully
         */
        public boolean openProfileProxyAsync() {
            mIsProfileConnecting = mAdapter.getProfileProxy(mContext, mServiceListener, mProfileId);
            return mIsProfileConnecting;
        }

        /**
         * Wait for profile service to connect
         *
         * @return true if the service is connected on time
         */
        public boolean waitForProfileConnect() {
            return waitForProfileConnect(PROXY_CONNECTION_TIMEOUT);
        }

        /**
         * Wait for profile service to connect with timeouts
         *
         * @param timeoutMs duration of the timeout in milliseconds
         * @return true if the service is connected on time
         * @deprecated Please use {@link #waitForProfileConnect(Duration)} instead
         */
        @Deprecated
        @InlineMe(
                replacement = "this.waitForProfileConnect(Duration.ofMillis(timeoutMs))",
                imports = "java.time.Duration")
        public final boolean waitForProfileConnect(int timeoutMs) {
            return waitForProfileConnect(Duration.ofMillis(timeoutMs));
        }

        /**
         * Wait for profile service to connect with timeouts
         *
         * @param timeout duration of the timeout
         * @return true if the service is connected on time
         */
        public boolean waitForProfileConnect(Duration timeout) {
            if (!mIsProfileConnecting) {
                mIsProfileConnecting =
                        mAdapter.getProfileProxy(mContext, mServiceListener, mProfileId);
            }
            if (!mIsProfileConnecting) {
                return false;
            }
            mProfileConnectionLock.lock();
            try {
                // Wait for the Adapter to be disabled
                while (!mIsProfileReady) {
                    if (!mConditionProfileConnection.await(
                            timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        // Timeout
                        Log.e(mLogTag, "Timeout while waiting for Profile Connect");
                        break;
                    } // else spurious wake-ups
                }
            } catch (InterruptedException e) {
                Log.e(mLogTag, "waitForProfileConnect: interrupted");
            } finally {
                mProfileConnectionLock.unlock();
            }
            mIsProfileConnecting = false;
            return mIsProfileReady;
        }

        private final BluetoothProfile.ServiceListener mServiceListener =
                new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        mProfileConnectionLock.lock();
                        try {
                            mProfileProxy = proxy;
                            mIsProfileReady = true;
                            mConditionProfileConnection.signal();
                        } finally {
                            mProfileConnectionLock.unlock();
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        mProfileConnectionLock.lock();
                        try {
                            mIsProfileReady = false;
                            mConditionProfileConnection.signal();
                        } finally {
                            mProfileConnectionLock.unlock();
                        }
                    }
                };
    }
}
