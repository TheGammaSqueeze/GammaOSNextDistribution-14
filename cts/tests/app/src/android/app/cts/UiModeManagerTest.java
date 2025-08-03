/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.app.cts;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.app.UiModeManager.MODE_NIGHT_AUTO;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM_TYPE_BEDTIME;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM_TYPE_SCHEDULE;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM_TYPE_UNKNOWN;
import static android.app.UiModeManager.MODE_NIGHT_NO;
import static android.app.UiModeManager.MODE_NIGHT_YES;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.app.UiModeManager.ContrastChangeListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.UserSettings;

import com.google.common.util.concurrent.MoreExecutors;

import junit.framework.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class UiModeManagerTest extends AndroidTestCase {
    private static final String TAG = "UiModeManagerTest";
    private static final long MAX_WAIT_TIME_SECS = 2;
    private static final long MAX_WAIT_TIME_MS = MAX_WAIT_TIME_SECS * 1000;
    private static final long WAIT_TIME_INCR_MS = 100;

    private static final String CONTRAST_LEVEL = "contrast_level";

    private final UserSettings mSystemUserSettings = new UserSettings(UserHandle.USER_SYSTEM);
    private UiModeManager mUiModeManager;
    private boolean mHasModifiedNightModePermissionAcquired = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUiModeManager = (UiModeManager) getContext().getSystemService(Context.UI_MODE_SERVICE);
        assertNotNull(mUiModeManager);
        resetNightMode();
        // Make sure automotive projection is not set by this package at the beginning of the test.
        releaseAutomotiveProjection();
    }

    @Override
    protected void tearDown() throws Exception {
        resetNightMode();
        BatteryUtils.runDumpsysBatteryReset();
        BatteryUtils.resetBatterySaver();
    }

    private void resetNightMode() {
        try {
            if (!mHasModifiedNightModePermissionAcquired) {
                acquireModifyNightModePermission();
            }
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
            mUiModeManager.setNightModeActivatedForCustomMode(MODE_NIGHT_CUSTOM_TYPE_BEDTIME,
                    false /* active */);
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            mHasModifiedNightModePermissionAcquired = false;
        }
    }

    public void testUiMode() throws Exception {
        if (isAutomotive()) {
            Log.i(TAG, "testUiMode automotive");
            doTestUiModeForAutomotive();
        } else if (isTelevision()) {
            assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,
                    mUiModeManager.getCurrentModeType());
            doTestLockedUiMode();
        } else if (isWatch()) {
            assertEquals(Configuration.UI_MODE_TYPE_WATCH,
                    mUiModeManager.getCurrentModeType());
            doTestLockedUiMode();
        } else {
            Log.i(TAG, "testUiMode generic");
            doTestUiModeGeneric();
        }
    }

    public void testNightMode() throws Exception {
        if (isAutomotive()) {
            assertTrue(mUiModeManager.isNightModeLocked());
            doTestLockedNightMode();
        } else {
            if (mUiModeManager.isNightModeLocked()) {
                doTestLockedNightMode();
            } else {
                doTestUnlockedNightMode();
            }
        }
    }

    public void testSetAndGetCustomTimeStart() {
        LocalTime time = mUiModeManager.getCustomNightModeStart();
        // decrease time
        LocalTime timeNew = LocalTime.of(
                (time.getHour() + 1) % 12,
                (time.getMinute() + 30) % 60);
        setStartTime(timeNew);
        assertNotSame(time, timeNew);
        assertEquals(timeNew, mUiModeManager.getCustomNightModeStart());
    }

    public void testSetAndGetCustomTimeEnd() {
        LocalTime time = mUiModeManager.getCustomNightModeEnd();
        // decrease time
        LocalTime timeNew = LocalTime.of(
                (time.getHour() + 1) % 12,
                (time.getMinute() + 30) % 60);
        setEndTime(timeNew);
        assertNotSame(time, timeNew);
        assertEquals(timeNew, mUiModeManager.getCustomNightModeEnd());
    }

    public void testNightModeYesPersisted() throws InterruptedException {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeYesPersisted skipped: night mode is locked");
            return;
        }

        // Reset the mode to no if it is set to another value
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        setNightMode(UiModeManager.MODE_NIGHT_YES);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_YES);
    }

    public void testNightModeAutoPersisted() throws InterruptedException {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeAutoPersisted skipped: night mode is locked");
            return;
        }

        // Reset the mode to no if it is set to another value
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        setNightMode(UiModeManager.MODE_NIGHT_AUTO);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_AUTO);
    }

    public void testSetNightModeCustomType_noPermission_bedtime_shouldThrow() {
        assertThrows(SecurityException.class,
                () -> mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME));
    }

    public void testSetNightModeCustomType_customTypeUnknown_bedtime_shouldThrow() {
        acquireModifyNightModePermission();

        assertThrows(IllegalArgumentException.class,
                () -> mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_UNKNOWN));
    }

    public void testSetNightModeCustomType_bedtime_shouldPersist() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertThat(mUiModeManager.getNightMode()).isEqualTo(MODE_NIGHT_CUSTOM);
        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);
    }

    public void testSetNightModeCustomType_schedule_shouldPersist() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_SCHEDULE);

        assertThat(mUiModeManager.getNightMode()).isEqualTo(MODE_NIGHT_CUSTOM);
        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_SCHEDULE);
    }

    public void testGetNightModeCustomType_nightModeNo_shouldReturnUnknown() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_NO);

        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_UNKNOWN);
    }

    public void testGetNightModeCustomType_nightModeYes_shouldReturnUnknown() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_YES);

        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_UNKNOWN);
    }

    public void testGetNightModeCustomType_nightModeAuto_shouldReturnUnknown() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_AUTO);

        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_UNKNOWN);
    }

    public void testGetNightModeCustomType_nightModeCustom_shouldReturnScheduleAsDefault() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_CUSTOM);

        assertThat(mUiModeManager.getNightModeCustomType())
                .isEqualTo(MODE_NIGHT_CUSTOM_TYPE_SCHEDULE);
    }

    public void testSetNightModeCustomType_hasPermission_bedtime_shouldNotActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);
    }

    public void testSetNightModeActivatedForCustomMode_noPermission_customTypeBedtime_activateAtBedtime_shouldNotActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        mHasModifiedNightModePermissionAcquired = false;

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isFalse();

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);
    }

    public void testSetNightModeActivatedForCustomMode_customTypeBedtime_activateAtBedtime_shouldActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isTrue();

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testSetNightModeActivatedForCustomMode_customTypeBedtime_deactivateAtBedtime_shouldDeactivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, false /* active */)).isTrue();

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);
    }

    public void testSetNightModeActivatedForCustomMode_modeNo_activateAtBedtime_shouldNotActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_NO);

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isFalse();

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);
    }

    public void testSetNightModeActivatedForCustomMode_modeYes_activateAtBedtime_shouldKeepNightModeActivated() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_YES);

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isFalse();

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testSetNightModeActivatedForCustomMode_modeAuto_activateAtBedtime_shouldNotActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_AUTO);
        int uiMode = getContext().getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isFalse();

        assertVisibleNightModeInConfiguration(uiMode);
    }

    public void testSetNightModeActivatedForCustomMode_customTypeSchedule_activateAtBedtime_shouldNotActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_SCHEDULE);
        int uiMode = getContext().getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;

        assertThat(mUiModeManager.setNightModeActivatedForCustomMode(
                MODE_NIGHT_CUSTOM_TYPE_BEDTIME, true /* active */)).isFalse();

        assertVisibleNightModeInConfiguration(uiMode);
    }

    public void testSetNightModeActivatedForCustomMode_modeNo_activateAtBedtime_thenCustomTypeBedtime_shouldActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_NO);
        mUiModeManager.setNightModeActivatedForCustomMode(MODE_NIGHT_CUSTOM_TYPE_BEDTIME,
                true /* active */);

        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testSetNightModeActivatedForCustomMode_modeYes_activateAtBedtime_thenCustomTypeBedtime_shouldActiveNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_YES);
        mUiModeManager.setNightModeActivatedForCustomMode(MODE_NIGHT_CUSTOM_TYPE_BEDTIME,
                true /* active */);

        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testSetNightModeActivatedForCustomMode_modeAuto_activateAtBedtime_thenCustomTypeBedtime_shouldActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(MODE_NIGHT_AUTO);
        mUiModeManager.setNightModeActivatedForCustomMode(MODE_NIGHT_CUSTOM_TYPE_BEDTIME,
                true /* active */);

        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testSetNightModeActivatedForCustomMode_customTypeSchedule_activateAtBedtime_thenCustomTypeBedtime_shouldActivateNightMode() {
        acquireModifyNightModePermission();
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_SCHEDULE);
        mUiModeManager.setNightModeActivatedForCustomMode(MODE_NIGHT_CUSTOM_TYPE_BEDTIME,
                true /* active */);

        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);
    }

    public void testNightModeAutoNotPersistedCarMode() {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeAutoNotPersistedCarMode skipped: night mode is locked");
            return;
        }

        // Reset the mode to no if it is set to another value
        setNightMode(UiModeManager.MODE_NIGHT_NO);
        mUiModeManager.enableCarMode(0);

        setNightMode(UiModeManager.MODE_NIGHT_AUTO);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);
        mUiModeManager.disableCarMode(0);
    }

    public void testNightModeCustomTypeBedtimeNotPersistedInCarMode() throws InterruptedException {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeCustomTypeBedtimeNotPersistedInCarMode skipped: night mode is "
                    + "locked");
            return;
        }

        acquireModifyNightModePermission();
        mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
        mUiModeManager.enableCarMode(0);

        // We don't expect users modifing bedtime features when driving.
        mUiModeManager.setNightModeCustomType(MODE_NIGHT_CUSTOM_TYPE_BEDTIME);

        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);
        mUiModeManager.disableCarMode(0);
    }

    public void testNightModeInCarModeIsTransient() {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeInCarModeIsTransient skipped: night mode is locked");
            return;
        }

        assertNightModeChange(UiModeManager.MODE_NIGHT_NO);

        mUiModeManager.enableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());

        assertNightModeChange(UiModeManager.MODE_NIGHT_YES);

        mUiModeManager.disableCarMode(0);
        assertNotSame(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        assertEquals(UiModeManager.MODE_NIGHT_NO, mUiModeManager.getNightMode());
    }

    public void testNightModeToggleInCarModeDoesNotChangeSetting() {
        if (mUiModeManager.isNightModeLocked()) {
            Log.i(TAG, "testNightModeToggleInCarModeDoesNotChangeSetting skipped: "
                    + "night mode is locked");
            return;
        }

        assertNightModeChange(UiModeManager.MODE_NIGHT_NO);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);

        mUiModeManager.enableCarMode(0);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);

        assertNightModeChange(UiModeManager.MODE_NIGHT_YES);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);

        mUiModeManager.disableCarMode(0);
        assertStoredNightModeSetting(UiModeManager.MODE_NIGHT_NO);
    }

    public void testNightModeInCarModeOnPowerSaveIsTransient() throws Throwable {
        if (mUiModeManager.isNightModeLocked() || !BatteryUtils.isBatterySaverSupported()) {
            Log.i(TAG, "testNightModeInCarModeOnPowerSaveIsTransient skipped: "
                    + "night mode is locked or battery saver is not supported");
            return;
        }

        BatteryUtils.runDumpsysBatteryUnplug();

        // Turn off battery saver, disable night mode
        BatteryUtils.enableBatterySaver(false);
        mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
        assertEquals(UiModeManager.MODE_NIGHT_NO, mUiModeManager.getNightMode());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);

        // Then enable battery saver to check night mode is made visible
        BatteryUtils.enableBatterySaver(true);
        assertEquals(UiModeManager.MODE_NIGHT_NO, mUiModeManager.getNightMode());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);

        // Then disable it, enable car mode, and check night mode is not visible
        BatteryUtils.enableBatterySaver(false);
        mUiModeManager.enableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);

        // Enable battery saver, check that night mode is still not visible, overridden by car mode
        BatteryUtils.enableBatterySaver(true);
        assertEquals(UiModeManager.MODE_NIGHT_NO, mUiModeManager.getNightMode());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);

        // Disable car mode
        mUiModeManager.disableCarMode(0);

        // Toggle night mode to force propagation of uiMode update, since disabling car mode
        // is deferred to a broadcast.
        mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
        mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);

        // Check battery saver mode now shows night mode
        assertNotSame(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_YES);

        // Disable battery saver and check night mode back to not visible
        BatteryUtils.enableBatterySaver(false);
        assertEquals(UiModeManager.MODE_NIGHT_NO, mUiModeManager.getNightMode());
        assertVisibleNightModeInConfiguration(Configuration.UI_MODE_NIGHT_NO);
    }

    /**
     * Verifies that an app holding the ENTER_CAR_MODE_PRIORITIZED permission can enter car mode
     * while specifying a priority.
     */
    public void testEnterCarModePrioritized() {
        if (mUiModeManager.isUiModeLocked()) {
            return;
        }

        runWithShellPermissionIdentity(() -> mUiModeManager.enableCarMode(100, 0),
                Manifest.permission.ENTER_CAR_MODE_PRIORITIZED);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());

        runWithShellPermissionIdentity(() -> mUiModeManager.disableCarMode(0),
                Manifest.permission.ENTER_CAR_MODE_PRIORITIZED);
        assertEquals(Configuration.UI_MODE_TYPE_NORMAL, mUiModeManager.getCurrentModeType());
    }

    /**
     * Attempts to use the prioritized car mode API when the caller does not hold the correct
     * permission to use that API.
     */
    public void testEnterCarModePrioritizedDenied() {
        if (mUiModeManager.isUiModeLocked()) {
            return;
        }
        try {
            mUiModeManager.enableCarMode(100, 0);
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Verifies that an app holding the TOGGLE_AUTOMOTIVE_PROJECTION permission can request/release
     * automotive projection.
     */
    public void testToggleAutomotiveProjection() throws Exception {
        // If we didn't hold it in the first place, we didn't release it, so expect false.
        assertFalse(releaseAutomotiveProjection());
        assertTrue(requestAutomotiveProjection());
        // Multiple calls are OK.
        assertTrue(requestAutomotiveProjection());
        assertTrue(releaseAutomotiveProjection());
        // Once it's released, further calls return false since it was already released.
        assertFalse(releaseAutomotiveProjection());
    }

    /**
     * Verifies that the system can correctly read the projection state.
     */
    public void testReadProjectionState() throws Exception {
        assertEquals(UiModeManager.PROJECTION_TYPE_NONE, getActiveProjectionTypes());
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE).isEmpty());
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_ALL).isEmpty());
        requestAutomotiveProjection();
        assertEquals(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, getActiveProjectionTypes());
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE)
                .contains(getContext().getPackageName()));
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_ALL)
                .contains(getContext().getPackageName()));
        releaseAutomotiveProjection();
        assertEquals(UiModeManager.PROJECTION_TYPE_NONE, getActiveProjectionTypes());
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE).isEmpty());
        assertTrue(getProjectingPackages(UiModeManager.PROJECTION_TYPE_ALL).isEmpty());
    }

    /** Verifies that the system receives callbacks about the projection state at expected times. */
    public void testReadProjectionState_listener() throws Exception {
        // Use AtomicInteger so it can be effectively final.
        AtomicInteger activeProjectionTypes = new AtomicInteger();
        Set<String> projectingPackages = new ArraySet<>();
        AtomicInteger callbackInvocations = new AtomicInteger();
        UiModeManager.OnProjectionStateChangedListener listener = (t, pkgs) -> {
            Log.i(TAG, "onProjectionStateChanged(" + t + "," + pkgs + ")");
            activeProjectionTypes.set(t);
            projectingPackages.clear();
            projectingPackages.addAll(pkgs);
            callbackInvocations.incrementAndGet();
        };

        requestAutomotiveProjection();
        runWithShellPermissionIdentity(() -> mUiModeManager.addOnProjectionStateChangedListener(
                UiModeManager.PROJECTION_TYPE_ALL, MoreExecutors.directExecutor(), listener),
                Manifest.permission.READ_PROJECTION_STATE);

        // Should have called back immediately, but the call might not have gotten here yet.
        CommonTestUtils.waitUntil("Callback wasn't invoked on listener addition!",
                MAX_WAIT_TIME_SECS, () -> callbackInvocations.get() == 1);
        assertEquals(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, activeProjectionTypes.get());
        assertEquals(1, projectingPackages.size());
        assertTrue(projectingPackages.contains(getContext().getPackageName()));

        // Callback should not be invoked again.
        requestAutomotiveProjection();
        Thread.sleep(MAX_WAIT_TIME_MS);
        assertEquals(1, callbackInvocations.get());

        releaseAutomotiveProjection();
        CommonTestUtils.waitUntil("Callback wasn't invoked on projection release!",
                MAX_WAIT_TIME_SECS, () -> callbackInvocations.get() == 2);
        assertEquals(UiModeManager.PROJECTION_TYPE_NONE, activeProjectionTypes.get());
        assertEquals(0, projectingPackages.size());

        // Again, no callback for noop call.
        releaseAutomotiveProjection();
        Thread.sleep(MAX_WAIT_TIME_MS);
        assertEquals(2, callbackInvocations.get());

        // Test the case that isn't at time of registration.
        requestAutomotiveProjection();
        CommonTestUtils.waitUntil("Callback wasn't invoked on projection set!",
                MAX_WAIT_TIME_SECS, () -> callbackInvocations.get() == 3);
        assertEquals(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE, activeProjectionTypes.get());
        assertEquals(1, projectingPackages.size());
        assertTrue(projectingPackages.contains(getContext().getPackageName()));

        // Unregister and shouldn't receive further callbacks.
        runWithShellPermissionIdentity(() -> mUiModeManager.removeOnProjectionStateChangedListener(
                listener), Manifest.permission.READ_PROJECTION_STATE);

        releaseAutomotiveProjection();
        requestAutomotiveProjection();
        releaseAutomotiveProjection(); // Just to clean up.
        Thread.sleep(MAX_WAIT_TIME_MS);
        assertEquals(3, callbackInvocations.get());
    }

    private boolean requestAutomotiveProjection() throws Exception {
        return callWithShellPermissionIdentity(
                () -> mUiModeManager.requestProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE),
                Manifest.permission.TOGGLE_AUTOMOTIVE_PROJECTION,
                Manifest.permission.INTERACT_ACROSS_USERS);
    }

    private boolean releaseAutomotiveProjection() throws Exception {
        return callWithShellPermissionIdentity(
                () -> mUiModeManager.releaseProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE),
                Manifest.permission.TOGGLE_AUTOMOTIVE_PROJECTION,
                Manifest.permission.INTERACT_ACROSS_USERS);
    }

    private int getActiveProjectionTypes() throws Exception {
        return callWithShellPermissionIdentity(mUiModeManager::getActiveProjectionTypes,
                Manifest.permission.READ_PROJECTION_STATE);
    }

    private Set<String> getProjectingPackages(int projectionType) throws Exception {
        return callWithShellPermissionIdentity(
                () -> mUiModeManager.getProjectingPackages(projectionType),
                Manifest.permission.READ_PROJECTION_STATE);
    }

    /**
     * Attempts to request automotive projection without TOGGLE_AUTOMOTIVE_PROJECTION permission.
     */
    public void testRequestAutomotiveProjectionDenied() {
        try {
            mUiModeManager.requestProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE);
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Attempts to request automotive projection without TOGGLE_AUTOMOTIVE_PROJECTION permission.
     */
    public void testReleaseAutomotiveProjectionDenied() {
        try {
            mUiModeManager.releaseProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE);
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Attempts to request more than one projection type at once.
     */
    public void testRequestAllProjectionTypes() {
        try {
            mUiModeManager.requestProjection(UiModeManager.PROJECTION_TYPE_ALL);
        } catch (IllegalArgumentException iae) {
            // Expect exception.
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    /**
     * Attempts to release more than one projection type.
     */
    public void testReleaseAllProjectionTypes() {
        try {
            mUiModeManager.releaseProjection(UiModeManager.PROJECTION_TYPE_ALL);
        } catch (IllegalArgumentException iae) {
            // Expect exception.
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    /** Attempts to request no projection types. */
    public void testRequestNoProjectionTypes() {
        try {
            mUiModeManager.requestProjection(UiModeManager.PROJECTION_TYPE_NONE);
        } catch (IllegalArgumentException iae) {
            // Expect exception.
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    /** Attempts to release no projection types. */
    public void testReleaseNoProjectionTypes() {
        try {
            mUiModeManager.releaseProjection(UiModeManager.PROJECTION_TYPE_NONE);
        } catch (IllegalArgumentException iae) {
            // Expect exception.
            return;
        }
        fail("Expected IllegalArgumentException");
    }

    /** Attempts to call getActiveProjectionTypes without READ_PROJECTION_STATE permission. */
    public void testReadProjectionState_getActiveProjectionTypesDenied() {
        try {
            mUiModeManager.getActiveProjectionTypes();
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /** Attempts to call getProjectingPackages without READ_PROJECTION_STATE permission. */
    public void testReadProjectionState_getProjectingPackagesDenied() {
        try {
            mUiModeManager.getProjectingPackages(UiModeManager.PROJECTION_TYPE_ALL);
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Attempts to call addOnProjectionStateChangedListener without
     * READ_PROJECTION_STATE permission.
     */
    public void testReadProjectionState_addOnProjectionStateChangedListenerDenied() {
        try {
            mUiModeManager.addOnProjectionStateChangedListener(UiModeManager.PROJECTION_TYPE_ALL,
                    getContext().getMainExecutor(), (t, pkgs) -> { });
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Attempts to call removeOnProjectionStateChangedListener without
     * READ_PROJECTION_STATE permission.
     */
    public void testReadProjectionState_removeOnProjectionStateChangedListenerDenied() {
        UiModeManager.OnProjectionStateChangedListener listener = (t, pkgs) -> { };
        runWithShellPermissionIdentity(() -> mUiModeManager.addOnProjectionStateChangedListener(
                UiModeManager.PROJECTION_TYPE_ALL, getContext().getMainExecutor(), listener),
                Manifest.permission.READ_PROJECTION_STATE);
        try {
            mUiModeManager.removeOnProjectionStateChangedListener(listener);
        } catch (SecurityException se) {
            // Expect exception.
            return;
        }
        fail("Expected SecurityException");
    }

    public void testGetContrast() throws Exception {
        float initialContrast = mUiModeManager.getContrast();
        putContrastInSettings(0f);
        try {
            for (float testContrast : List.of(-1f, 0.5f)) {
                putContrastInSettings(testContrast);
                TestUtils.waitUntil("getContrast() should return the new contrast value",
                        (int) MAX_WAIT_TIME_SECS,
                        () -> Math.abs(mUiModeManager.getContrast() - testContrast) < 1e-10);
            }
        } finally {
            putContrastInSettings(initialContrast);
        }
    }

    public void testAddContrastChangeListener() {
        float initialContrast = mUiModeManager.getContrast();
        putContrastInSettings(0f);
        final Object waitObject = new Object();
        final List<ContrastChangeListener> listeners = new ArrayList<>();

        // store callback errors here to avoid crashing the whole test suite
        final AtomicReference<AssertionError> error = new AtomicReference<>(null);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        String handlerThreadName = "UiModeManagerTestBackgroundThread";
        final HandlerThread backgroundThread = new HandlerThread(handlerThreadName);
        backgroundThread.start();
        Handler backgroundHandler = new Handler(backgroundThread.getLooper());
        Executor backgroundExecutor = backgroundHandler::post;

        try {
            for (float testContrast : List.of(-1f, 0f)) {
                ContrastChangeListener listener = (float contrast) -> {
                    synchronized (waitObject) {
                        try {
                            assertEquals("Wrong value received by the color contrast listener",
                                    testContrast, contrast, 1e-10);
                            assertEquals("The executor should be used to invoke the callback",
                                    backgroundThread, Thread.currentThread());
                        } catch (AssertionError e) {
                            error.set(e);
                        }
                        atomicBoolean.set(true);
                        waitObject.notifyAll();
                    }
                };
                listeners.add(listener);
                mUiModeManager.addContrastChangeListener(backgroundExecutor, listener);
                putContrastInSettings(testContrast);
                waitForAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                        "The color contrast listener should be called when the setting changes");
                if (error.get() != null) throw error.get();
                mUiModeManager.removeContrastChangeListener(listener);
            }
        } finally {
            backgroundThread.quitSafely();
            putContrastInSettings(initialContrast);
            listeners.forEach(mUiModeManager::removeContrastChangeListener);
        }
    }

    public void testRemoveContrastChangeListener() {
        float initialContrast = mUiModeManager.getContrast();
        putContrastInSettings(0f);
        final Object waitObject = new Object();

        // store callback errors here to avoid crashing the whole test suite
        final AtomicReference<AssertionError> error = new AtomicReference<>(null);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        Executor mainExecutor = mContext.getMainExecutor();
        ContrastChangeListener listener = (float value) -> {
            synchronized (waitObject) {
                atomicBoolean.set(true);
                waitObject.notifyAll();
            }
        };

        ContrastChangeListener removedListener = (float contrast) -> error.set(new AssertionError(
                "The listener should not be invoked after being removed"));

        final List<ContrastChangeListener> listeners = List.of(listener, removedListener);
        try {
            for (float testContrast: List.of(0.5f, 1f)) {
                mUiModeManager.addContrastChangeListener(mainExecutor, removedListener);
                mUiModeManager.addContrastChangeListener(mainExecutor, listener);
                mUiModeManager.removeContrastChangeListener(removedListener);

                putContrastInSettings(testContrast);
                waitForAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                        "The color contrast listener should be called when the setting changes");
                if (error.get() != null) throw error.get();
                mUiModeManager.removeContrastChangeListener(listener);
            }
        } finally {
            putContrastInSettings(initialContrast);
            listeners.forEach(mUiModeManager::removeContrastChangeListener);
        }
    }

    private boolean isAutomotive() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean isTelevision() {
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private boolean isWatch() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);
    }

    private void doTestUiModeForAutomotive() throws Exception {
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        assertTrue(mUiModeManager.isUiModeLocked());
        doTestLockedUiMode();
    }

    private void doTestUiModeGeneric() throws Exception {
        if (mUiModeManager.isUiModeLocked()) {
            doTestLockedUiMode();
        } else {
            doTestUnlockedUiMode();
        }
    }

    private void doTestLockedUiMode() throws Exception {
        int originalMode = mUiModeManager.getCurrentModeType();
        mUiModeManager.enableCarMode(0);
        assertEquals(originalMode, mUiModeManager.getCurrentModeType());
        mUiModeManager.disableCarMode(0);
        assertEquals(originalMode, mUiModeManager.getCurrentModeType());
    }

    private void doTestUnlockedUiMode() throws Exception {
        mUiModeManager.enableCarMode(0);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
        mUiModeManager.disableCarMode(0);
        assertNotSame(Configuration.UI_MODE_TYPE_CAR, mUiModeManager.getCurrentModeType());
    }

    private void doTestLockedNightMode() throws Exception {
        int currentMode = mUiModeManager.getNightMode();
        if (currentMode == UiModeManager.MODE_NIGHT_YES) {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
            assertEquals(currentMode, mUiModeManager.getNightMode());
        } else {
            mUiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            assertEquals(currentMode, mUiModeManager.getNightMode());
        }
    }

    private void doTestUnlockedNightMode() throws Exception {
        // day night mode should be settable regardless of car mode.
        mUiModeManager.enableCarMode(0);
        doTestAllNightModes();
        mUiModeManager.disableCarMode(0);
        doTestAllNightModes();
    }

    private void doTestAllNightModes() {
        assertNightModeChange(UiModeManager.MODE_NIGHT_AUTO);
        assertNightModeChange(UiModeManager.MODE_NIGHT_YES);
        assertNightModeChange(UiModeManager.MODE_NIGHT_NO);
    }

    private void assertNightModeChange(int mode) {
        mUiModeManager.setNightMode(mode);
        assertEquals(mode, mUiModeManager.getNightMode());
    }

    private void assertVisibleNightModeInConfiguration(int mode) {
        int uiMode = getContext().getResources().getConfiguration().uiMode;
        int flags = uiMode & Configuration.UI_MODE_NIGHT_MASK;
        assertEquals(mode, flags);
    }

    private void assertStoredNightModeSetting(int mode) {
        int storedModeInt = -1;
        // Settings.Secure.UI_NIGHT_MODE
        for (int i = 0; i < MAX_WAIT_TIME_MS; i += WAIT_TIME_INCR_MS) {
            String storedMode = getUiNightModeFromSetting();
            if (!TextUtils.isEmpty(storedMode)) {
                storedModeInt = Integer.parseInt(storedMode);
            }
            if (mode == storedModeInt) break;
            try {
                Thread.sleep(WAIT_TIME_INCR_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assertEquals(mode, storedModeInt);
    }

    private void setNightMode(int mode) {
        String modeString = "unknown";
        switch (mode) {
            case UiModeManager.MODE_NIGHT_AUTO:
                modeString = "auto";
                break;
            case UiModeManager.MODE_NIGHT_NO:
                modeString = "no";
                break;
            case UiModeManager.MODE_NIGHT_YES:
                modeString = "yes";
                break;
        }
        final String command = " cmd uimode night " + modeString;
        applyCommand(command);
    }

    private void setStartTime(LocalTime t) {
        final String command = " cmd uimode time start " + t.toString();
        applyCommand(command);
    }

    private void setEndTime(LocalTime t) {
        final String command = " cmd uimode time end " + t.toString();
        applyCommand(command);
    }

    private void applyCommand(String command) {
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            Assert.assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) continue;
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        } catch (IOException e) {
            fail();
        } finally {
            uiAutomation.destroy();
        }
    }

    private String getUiNightModeFromSetting() {
        return mSystemUserSettings.get("ui_night_mode");
    }

    private void acquireModifyNightModePermission() {
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_DAY_NIGHT_MODE);
        mHasModifiedNightModePermissionAcquired = true;
    }

    private void waitForAtomicBooleanBecomes(AtomicBoolean atomicBoolean,
            boolean expectedValue, Object waitObject, String condition) {
        TestUtils.waitOn(waitObject, () -> atomicBoolean.get() == expectedValue,
                MAX_WAIT_TIME_MS, condition);
    }

    private void putContrastInSettings(float contrast) {
        runWithShellPermissionIdentity(() -> Settings.Secure.putFloat(
                mContext.getContentResolver(), CONTRAST_LEVEL, contrast), WRITE_SECURE_SETTINGS);
    }
}
