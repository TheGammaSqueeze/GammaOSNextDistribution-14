/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;

import com.google.android.cts.deviceowner.DeviceOwnerTest.BasicAdminReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Utility test to clear the device owner and active admin. */
@RunWith(AndroidJUnit4.class)
public final class ClearDeviceOwnerTest {

    private static final String TAG = ClearDeviceOwnerTest.class.getSimpleName();

    private final Context mContext = InstrumentationRegistry.getInstrumentation()
            .getTargetContext();

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "tearDown(): user=" + UserHandle.myUserId());

        removeProfileOwnerIfNeeeded();

        removeDeviceOwner();
    }

    private void removeProfileOwnerIfNeeeded() {
        if (!UserManager.isHeadlessSystemUserMode()) return;

        // On headless system user, need to remove profile owner from current user as well
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);

        boolean isPoBefore = dpm.isProfileOwnerApp(DeviceOwnerTest.PACKAGE_NAME);
        if (isPoBefore) {
            Log.d(TAG, "Clearing " + DeviceOwnerTest.PACKAGE_NAME + " as profile owner");
            dpm.clearProfileOwner(DeviceOwnerTest.RECEIVER_COMPONENT);
        }
        boolean isPo = dpm.isDeviceOwnerApp(DeviceOwnerTest.PACKAGE_NAME);
        boolean isActiveAdminBefore = dpm.isAdminActive(DeviceOwnerTest.RECEIVER_COMPONENT);
        Log.d(TAG, "is profile owner: " + isPo + ", is activeAdmin:" + isActiveAdminBefore);

        waitUntilActiveAdminIsRemoved(dpm, DeviceOwnerTest.RECEIVER_COMPONENT);
        assertWithMessage("%s is profile owner", DeviceOwnerTest.RECEIVER_COMPONENT).that(isPo)
                .isFalse();
        assertWithMessage("%s is active admin owner", DeviceOwnerTest.RECEIVER_COMPONENT)
                .that(dpm.isAdminActive(DeviceOwnerTest.RECEIVER_COMPONENT)).isFalse();
    }

    private void removeDeviceOwner() {
        DevicePolicyManager dpm = TestAppSystemServiceFactory.getDevicePolicyManager(mContext,
                BasicAdminReceiver.class, /* forDeviceOWner= */ true);
        Log.d(TAG, "removeDeviceOwner(): dpm=" + dpm);

        assertWithMessage("dpm for user %s", UserHandle.myUserId()).that(dpm).isNotNull();
        boolean isDoBefore = dpm
                .isDeviceOwnerApp(DeviceOwnerTest.PACKAGE_NAME);
        if (isDoBefore) {
            Log.d(TAG, "Clearing " + DeviceOwnerTest.PACKAGE_NAME + " as device owner");
            dpm.clearDeviceOwnerApp(DeviceOwnerTest.PACKAGE_NAME);
        }
        boolean isDo = dpm.isDeviceOwnerApp(DeviceOwnerTest.PACKAGE_NAME);
        boolean isActiveAdminBefore = dpm
                .isAdminActive(DeviceOwnerTest.RECEIVER_COMPONENT);
        Log.d(TAG, "is device owner=" + isDo + ", is activeAdmin=" + isActiveAdminBefore);

        waitUntilActiveAdminIsRemoved(dpm, DeviceOwnerTest.RECEIVER_COMPONENT);
        assertWithMessage("%s is device owner", DeviceOwnerTest.PACKAGE_NAME).that(isDo).isFalse();

        // Ignoring the fact that it might still be an active admin, as removing the admin
        // is flakey on old devices.
        assertWithMessage("%s is active admin owner", DeviceOwnerTest.RECEIVER_COMPONENT)
                .that(dpm.isAdminActive(DeviceOwnerTest.RECEIVER_COMPONENT)).isFalse();
    }

    /**
     * This test clears the device owner and active admin on tearDown(). To be called from the host
     * side test once a test case is finished.
     */
    @Test
    public void testClearDeviceOwner() {

    }

    private static void waitUntilActiveAdminIsRemoved(DevicePolicyManager dpm, ComponentName cn) {
        for (int i = 0; i < 1000 && dpm.isAdminActive(cn); i++) {
            Log.v(TAG, "Sleeping 100ms because " + cn.flattenToShortString()
                + " is not active admin");
            SystemClock.sleep(100);
            Log.v(TAG, "After waken up: " + dpm.isAdminActive(cn));
        }
    }
}
