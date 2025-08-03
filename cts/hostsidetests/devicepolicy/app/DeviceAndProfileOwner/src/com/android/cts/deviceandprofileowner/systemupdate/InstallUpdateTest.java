/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner.systemupdate;

import static android.app.admin.DevicePolicyManager.InstallSystemUpdateCallback.UPDATE_ERROR_UPDATE_FILE_INVALID;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager.InstallSystemUpdateCallback;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test {@link android.app.admin.DevicePolicyManager#installSystemUpdate}
 */
public final class InstallUpdateTest extends BaseDeviceAdminTest {

    private static final String TAG = InstallUpdateTest.class.getSimpleName();
    private static final int BATTERY_STATE_CHANGE_TIMEOUT_MS = 5000;
    private static final int BATTERY_STATE_CHANGE_SLEEP_PER_CHECK_MS = 50;
    private static final int TEST_BATTERY_THRESHOLD = 10;
    private static final IntentFilter BATTERY_CHANGED_FILTER =
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private static final String AB_DEVICE_KEY = "ro.build.ab_update";

    public static final String TEST_SYSTEM_UPDATES_DIR =
            "/data/local/tmp/cts/deviceowner/";
    public static final int TIMEOUT = 5;

    private int callbackErrorCode;

    public void testInstallUpdate_failFileNotFound() throws InterruptedException {
        assertUpdateError(
                "random",
                InstallSystemUpdateCallback.UPDATE_ERROR_FILE_NOT_FOUND);
    }

    public void testInstallUpdate_failNoZipOtaFile() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setupBatteryState();
            assertUpdateError("notZip.zi", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            teardownBatteryState();
        }
    }

    public void testInstallUpdate_failWrongPayloadFile() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setupBatteryState();
            assertUpdateError("wrongPayload.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            teardownBatteryState();
        }
    }

    public void testInstallUpdate_failEmptyOtaFile() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setupBatteryState();
            assertUpdateError("empty.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            teardownBatteryState();
        }
    }

    public void testInstallUpdate_failWrongHash() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setupBatteryState();
            assertUpdateError("wrongHash.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            teardownBatteryState();
        }
    }

    public void testInstallUpdate_failWrongSize() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setupBatteryState();
            assertUpdateError("wrongSize.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            teardownBatteryState();
        }
    }

    public void testInstallUpdate_notCharging_belowThreshold_failsBatteryCheck() throws Exception {
        try {
            setNonChargingBatteryThreshold(TEST_BATTERY_THRESHOLD);
            setNonChargingBatteryLevelAndWait(TEST_BATTERY_THRESHOLD - 1);
            assertUpdateError("wrongSize.zip",
                    InstallSystemUpdateCallback.UPDATE_ERROR_BATTERY_LOW);
        } finally {
            resetBatteryState();
            resetDevicePolicyConstants();
        }
    }

    public void testInstallUpdate_notCharging_aboveThreshold_passesBatteryCheck() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setNonChargingBatteryThreshold(TEST_BATTERY_THRESHOLD);
            setNonChargingBatteryLevelAndWait(TEST_BATTERY_THRESHOLD);
            // Positive CTS tests aren't possible, so we verify that we get the file-related error
            // rather than the battery one.
            assertUpdateError("wrongSize.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            resetBatteryState();
            resetDevicePolicyConstants();
        }
    }

    public void testInstallUpdate_charging_belowThreshold_failsBatteryCheck() throws Exception {
        try {
            setChargingBatteryThreshold(TEST_BATTERY_THRESHOLD);
            setChargingBatteryLevelAndWait(TEST_BATTERY_THRESHOLD - 1);
            assertUpdateError("wrongSize.zip",
                    InstallSystemUpdateCallback.UPDATE_ERROR_BATTERY_LOW);
        } finally {
            resetBatteryState();
            resetDevicePolicyConstants();
        }
    }

    public void testInstallUpdate_charging_aboveThreshold_passesBatteryCheck() throws Exception {
        if (!isDeviceAB()) {
            return;
        }
        try {
            setChargingBatteryThreshold(TEST_BATTERY_THRESHOLD);
            setChargingBatteryLevelAndWait(TEST_BATTERY_THRESHOLD);
            // Positive CTS tests aren't possible, so we verify that we get the file-related error
            // rather than the battery one.
            assertUpdateError("wrongSize.zip", UPDATE_ERROR_UPDATE_FILE_INVALID);
        } finally {
            resetBatteryState();
            resetDevicePolicyConstants();
        }
    }

    private void assertUpdateError(String fileName, int expectedErrorCode) {
        Log.v(TAG, "assertUpdateError(" + fileName + ", " + expectedErrorCode + ")");
        AtomicInteger errorCode = new AtomicInteger();
        // Poll until the error code matches our expectation to deal with delays in propagation
        PollingCheck.waitFor(() -> {
            try {
                errorCode.set(getUpdateError(fileName));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return errorCode.get() == expectedErrorCode; });
        assertWithMessage("error code when updating %s", fileName).that(errorCode.get())
                .isEqualTo(expectedErrorCode);
    }

    private int getUpdateError(String fileName) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Uri uri = Uri.fromFile(new File(TEST_SYSTEM_UPDATES_DIR, fileName));
        Log.d(TAG, "Calling installSystemUpdate() on " + Process.myUserHandle() + " using " + uri);
        mDevicePolicyManager.installSystemUpdate(ADMIN_RECEIVER_COMPONENT, uri,
                Runnable::run, new InstallSystemUpdateCallback() {
                    @Override
                    public void onInstallUpdateError(int errorCode, String errorMessage) {
                        Log.d(TAG, "onInstallUpdateError(): errorCode=" + errorCode
                                    + ", errorMessage=" + errorMessage);
                        callbackErrorCode = errorCode;
                        latch.countDown();
                    }
                });
        Log.d(TAG, "Waiting " + TIMEOUT + " minutes for callback");
        assertWithMessage("onInstallUpdateError() not called in %s minutes", TIMEOUT)
                .that(latch.await(TIMEOUT, TimeUnit.MINUTES)).isTrue();
        return callbackErrorCode;
    }

    private void setNonChargingBatteryThreshold(int threshold) {
        runShellCommand(
                "settings put global device_policy_constants battery_threshold_not_charging="
                        + threshold);
    }

    private void setNonChargingBatteryLevelAndWait(int level) throws Exception {
        setBatteryStateAndWait(/* plugged= */ false, level);
    }

    private void setChargingBatteryThreshold(int threshold) {
        runShellCommand(
                "settings put global device_policy_constants battery_threshold_charging="
                        + threshold);
    }

    private void setChargingBatteryLevelAndWait(int level) throws Exception {
        setBatteryStateAndWait(/* plugged= */ true, level);
    }

    /** Should be paired with {@link #resetBatteryState()} in a {@code finally} block. */
    private void setBatteryStateAndWait(boolean plugged, int level) throws Exception {
        runShellCommand(plugged ? "cmd battery set ac 1" : "cmd battery unplug");
        runShellCommand("cmd battery set -f level " + level);
        long startTime = SystemClock.elapsedRealtime();
        while (!isBatteryState(plugged, level)
                && SystemClock.elapsedRealtime() <= startTime + BATTERY_STATE_CHANGE_TIMEOUT_MS) {
            Thread.sleep(BATTERY_STATE_CHANGE_SLEEP_PER_CHECK_MS);
        }
        assertWithMessage("battery state (plugged=%s, level=%s) after %sms", plugged, level,
                BATTERY_STATE_CHANGE_SLEEP_PER_CHECK_MS)
                        .that(isBatteryState(plugged, level)).isTrue();
    }

    private boolean isBatteryState(boolean plugged, int level) {
        final Intent batteryStatus =
                mContext.registerReceiver(/* receiver= */ null, BATTERY_CHANGED_FILTER);
        return isPluggedIn(batteryStatus) == plugged
                && calculateBatteryPercentage(batteryStatus) == level;
    }

    private boolean isPluggedIn(Intent batteryStatus) {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, /* defaultValue= */ -1) > 0;
    }

    private float calculateBatteryPercentage(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, /* defaultValue= */ -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, /* defaultValue= */ -1);
        return 100 * level / (float) scale;
    }

    private void resetBatteryState() {
        runShellCommand("dumpsys battery reset");
    }

    private void resetDevicePolicyConstants() {
        runShellCommand("settings delete global device_policy_constants");
    }

    private boolean isDeviceAB() {
        return "true".equalsIgnoreCase(SystemProperties.get(AB_DEVICE_KEY, ""));
    }

    private boolean deviceHasBattery() {
        final Intent batteryInfo = mContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return batteryInfo != null
               && batteryInfo.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    /**
     * This is just for batteryless device，as we know from above that remaining capacity
     * is 0 on Android 9 and higher. We need set battery status to meet the test conditions
     * of InstallUpdateTest for batteryless device.
     * For device has a battery, the test conditions follow the real status of the battery.
     */
    private void setupBatteryState() throws Exception {
        if (!deviceHasBattery()) {
            setChargingBatteryThreshold(TEST_BATTERY_THRESHOLD);
            setChargingBatteryLevelAndWait(TEST_BATTERY_THRESHOLD);
        }
    }

    private void teardownBatteryState() {
        if (!deviceHasBattery()) {
            resetBatteryState();
            resetDevicePolicyConstants();
        }
    }
}
