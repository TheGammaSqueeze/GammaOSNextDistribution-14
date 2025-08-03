/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.deviceandprofileowner;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.PollingCheck;

/**
 * Test class that is meant to be driven from the host and can't be run alone, which is required
 * for tests that include rebooting or other connection-breaking steps. For this reason, this class
 * does not override tearDown and setUp just initializes the test state, changing nothing in the
 * device. Therefore, the host is responsible for making sure the tests leave the device in a clean
 * state after running.
 */
public class LockTaskHostDrivenTest extends BaseDeviceAdminTest {

    private static final String TAG = LockTaskHostDrivenTest.class.getName();
    private static final int ACTIVITY_RESUMED_TIMEOUT_MILLIS = 20000;  // 20 seconds
    private static final int LOCK_TASK_STATE_CHANGE_TIMEOUT_MILLIS = 10000;  // 10 seconds
    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";
    private static final String LOCK_TASK_ACTIVITY
            = LockTaskUtilityActivityIfAllowed.class.getName();

    private UiDevice mUiDevice;
    private Context mContext;
    private PackageManager mPackageManager;
    private ActivityManager mActivityManager;
    private DevicePolicyManager mDevicePolicyManager;

    public void setUp() {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getContext();
        mPackageManager = mContext.getPackageManager();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
    }

    public void testStartLockTask_noAsserts() throws Exception {
        Log.d(TAG, "testStartLockTask_noAsserts on host-driven test (no cleanup)");
        setLockTaskPackages(mContext.getPackageName());
        setDefaultHomeIntentReceiver();
        launchLockTaskActivity();
        mUiDevice.waitForIdle();
    }

    public void testCleanupLockTask_noAsserts() {
        Log.d(TAG, "testCleanupLockTask_noAsserts on host-driven test");
        mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                ADMIN_RECEIVER_COMPONENT,
                mContext.getPackageName());
        setLockTaskPackages();
        mDevicePolicyManager.setLockTaskFeatures(ADMIN_RECEIVER_COMPONENT, 0);
        // In case some activity is still in foreground
        mUiDevice.pressHome();
    }

    public void testLockTaskIsActive() throws Exception {
        Log.d(TAG, "testLockTaskIsActive on host-driven test");
        waitAndCheckLockedActivityIsResumed();
        checkLockedActivityIsRunning();
    }

    /**
     * On low-RAM devices, this test can take too long to finish, so the test runner can incorrectly
     * assume it's finished. Therefore, only use it once in a given test.
     *
     * <p>Does not test that the locked activity is initially in the foreground, since running this
     * test in instrumentation can immediately kill the locked activity (while maintaining lock task
     * mode).
     */
    public void testLockTaskIsActiveAndCantBeInterrupted() throws Exception {
        Log.d(TAG, "testLockTaskIsActiveAndCantBeInterrupted on host-driven test");
        waitAndEnsureLockTaskUtilityActivityIsRunning();
        checkLockedActivityIsRunning();

        mUiDevice.pressHome();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.pressRecentApps();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.pressBack();
        mUiDevice.waitForIdle();
        checkLockedActivityIsRunning();

        mUiDevice.waitForIdle();
    }

    private void checkLockedActivityIsRunning() {
        String activityName =
                mActivityManager.getAppTasks().get(0).getTaskInfo().topActivity.getClassName();
        assertEquals(LOCK_TASK_ACTIVITY, activityName);

        PollingCheck.waitFor(
                () -> (mActivityManager.getLockTaskModeState()
                        == ActivityManager.LOCK_TASK_MODE_LOCKED));
    }

    /**
     * Ensures the locked activity is resumed or otherwise launches it but without starting lock
     * task if it is not already in that mode.
     */
    private void waitAndEnsureLockTaskUtilityActivityIsRunning() throws Exception {
        mUiDevice.waitForIdle();
        final boolean lockedActivityIsResumed =
                LockTaskUtilityActivity.waitUntilActivityResumed(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
        if (!lockedActivityIsResumed) {
            launchLockTaskUtilityActivityWithoutStartingLockTask();
            waitAndCheckLockedActivityIsResumed();
        }
    }

    private void waitAndCheckLockedActivityIsResumed() throws Exception {
        mUiDevice.waitForIdle();
        assertTrue(
                LockTaskUtilityActivity.waitUntilActivityResumed(ACTIVITY_RESUMED_TIMEOUT_MILLIS));
    }

    private void launchLockTaskActivity() {
        Intent intent = new Intent(mContext, LockTaskUtilityActivityIfAllowed.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LockTaskUtilityActivity.START_LOCK_TASK, true);
        mContext.startActivity(intent);
    }

    private void launchLockTaskUtilityActivityWithoutStartingLockTask() {
        final Intent intent = new Intent(mContext, LockTaskUtilityActivityIfAllowed.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void setLockTaskPackages(String... packages) {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_RECEIVER_COMPONENT, packages);
    }

    private void setDefaultHomeIntentReceiver() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        mDevicePolicyManager.addPersistentPreferredActivity(
                ADMIN_RECEIVER_COMPONENT, intentFilter,
                new ComponentName(mContext.getPackageName(), LOCK_TASK_ACTIVITY));
    }
}
