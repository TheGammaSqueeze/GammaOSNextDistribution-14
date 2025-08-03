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
package com.google.android.utils.chre;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A class to get or set settings parameters.
 */
public class SettingsUtil {
    private final Context mContext;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private final LocationManager mLocationManager;

    public class LocationUpdateListener {
        public CountDownLatch mLocationLatch = new CountDownLatch(1);

        public BroadcastReceiver mLocationSettingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (LocationManager.MODE_CHANGED_ACTION.equals(intent.getAction())) {
                    mLocationLatch.countDown();
                }
            }
        };
    }

    private class AirplaneModeListener {
        protected CountDownLatch mAirplaneModeLatch = new CountDownLatch(1);

        protected BroadcastReceiver mAirplaneModeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                    mAirplaneModeLatch.countDown();
                }
            }
        };
    }

    public SettingsUtil(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Assert.assertTrue(mLocationManager != null);
    }

    /**
     * @param enable true to enable always WiFi scanning.
     */
    public void setWifiScanningSettings(boolean enable) {
        String value = enable ? "1" : "0";
        ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings put global wifi_scan_always_enabled " + value);

        Assert.assertTrue(isWifiScanningAlwaysEnabled() == enable);
    }

    /**
     * @param enable true to enable WiFi service.
     */
    public void setWifi(boolean enable) {
        String value = enable ? "enable" : "disable";
        ChreTestUtil.executeShellCommand(mInstrumentation, "svc wifi " + value);

        Assert.assertTrue(isWifiEnabled() == enable);
    }

    /**
     * @return true if the WiFi scanning is always enabled.
     */
    public boolean isWifiScanningAlwaysEnabled() {
        String out = ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings get global wifi_scan_always_enabled");
        return ChreTestUtil.convertToIntegerOrFail(out) > 0;
    }

    /**
     * @return true if the WiFi service is enabled.
     */
    public boolean isWifiEnabled() {
        String out = ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings get global wifi_on");
        return ChreTestUtil.convertToIntegerOrFail(out) > 0;
    }

    /**
     * Sets the location mode on the device.
     * @param enable True to enable location, false to disable it.
     * @param timeoutSeconds The maximum amount of time in seconds to wait.
     */
    public void setLocationMode(boolean enable, long timeoutSeconds) throws InterruptedException {
        if (isLocationEnabled() != enable) {
            LocationUpdateListener listener = new LocationUpdateListener();

            mContext.registerReceiver(
                    listener.mLocationSettingReceiver,
                    new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
            mLocationManager.setLocationEnabledForUser(enable, UserHandle.CURRENT);

            boolean success = listener.mLocationLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            Assert.assertTrue("Timeout waiting for signal: set location mode", success);

            // Wait 1 additional second to make sure setting gets propagated to CHRE
            Thread.sleep(1000);

            Assert.assertTrue(isLocationEnabled() == enable);

            mContext.unregisterReceiver(listener.mLocationSettingReceiver);
        }
    }

    /**
     * @return True if location is enabled.
     */
    public boolean isLocationEnabled() {
        return mLocationManager.isLocationEnabledForUser(UserHandle.CURRENT);
    }

    /**
     * Sets the bluetooth mode to be on or off
     *
     * @param enable        if true, turn bluetooth on; otherwise, turn bluetooth off
     */
    public void setBluetooth(boolean enable) {
        String value = enable ? "enable" : "disable";
        ChreTestUtil.executeShellCommand(mInstrumentation, "svc bluetooth " + value);
    }

    /**
     * @param enable true to enable always bluetooth scanning.
     */
    public void setBluetoothScanningSettings(boolean enable) {
        String value = enable ? "1" : "0";
        ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings put global ble_scan_always_enabled " + value);
    }

    /**
     * @return true if bluetooth is enabled, false otherwise
     */
    public boolean isBluetoothEnabled() {
        String out = ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings get global bluetooth_on");
        return ChreTestUtil.convertToIntegerOrFail(out) == 1;
    }

    /**
     * @return true if the bluetooth scanning is always enabled.
     */
    public boolean isBluetoothScanningAlwaysEnabled() {
        String out = ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings get global ble_scan_always_enabled");

        // by default, this setting returns null, which is equivalent to 0 or disabled
        return ChreTestUtil.convertToIntegerOrReturnZero(out) > 0;
    }

    /**
     * Sets the airplane mode on the device.
     * @param enable True to enable airplane mode, false to disable it.
     */
    public void setAirplaneMode(boolean enable) throws InterruptedException {
        if (isAirplaneModeOn() != enable) {
            AirplaneModeListener listener = new AirplaneModeListener();
            mContext.registerReceiver(
                    listener.mAirplaneModeReceiver,
                    new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));

            String value = enable ? "enable" : "disable";
            ChreTestUtil.executeShellCommand(
                    mInstrumentation, "cmd connectivity airplane-mode " + value);

            boolean success = listener.mAirplaneModeLatch.await(10, TimeUnit.SECONDS);
            Assert.assertTrue("Timeout waiting for signal: set airplane mode", success);
            // Wait 1 additional second to make sure setting gets propagated to CHRE
            Thread.sleep(1000);
            Assert.assertTrue(isAirplaneModeOn() == enable);
            mContext.unregisterReceiver(listener.mAirplaneModeReceiver);
        }
    }

    /**
     * @return true if the airplane mode is currently enabled.
     */
    public boolean isAirplaneModeOn() {
        String out = ChreTestUtil.executeShellCommand(
                mInstrumentation, "settings get global airplane_mode_on");
        return ChreTestUtil.convertToIntegerOrFail(out) > 0;
    }
}
