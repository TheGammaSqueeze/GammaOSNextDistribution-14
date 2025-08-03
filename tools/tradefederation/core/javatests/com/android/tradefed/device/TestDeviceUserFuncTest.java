/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

/** Functional tests for the {@link ITestDevice} user management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TestDeviceUserFuncTest implements IDeviceTest {
    private TestDevice mTestDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();
    }

    /**
     * Tests several of the user creation, listing, and deletion methods
     *
     * <p>Specifically this tests {@link ITestDevice#createUser(String, boolean, boolean)}, {@link
     * ITestDevice#listUsers()}, and {@link ITestDevice#getUserInfos()}.
     */
    @Test
    public void testUserLifecycle() throws Exception {
        int userId = -1;
        try {
            final String userName = "Google";
            userId = mTestDevice.createUser(userName, false, false);
            assertTrue(userId > 0);

            List<Integer> listedIDs = mTestDevice.listUsers();
            boolean containsUserId = listedIDs.contains(userId);
            assertEquals(true, containsUserId);

            Map<Integer, UserInfo> userInfos = mTestDevice.getUserInfos();
            boolean userInfoContainsUserId = userInfos.containsKey(userId);
            assertEquals(true, userInfoContainsUserId);
            UserInfo info = userInfos.get(userId);
            assertNotNull(info);
            assertEquals(userName, info.userName());
        } finally {
            if (userId != -1) {
                mTestDevice.removeUser(userId);
            }
        }
    }

    /** Tries creating, starting, and stopping a user. */
    @Test
    public void testStartUser_IsRunningUser_StopUser() throws Exception {
        int userId = -1;
        try {
            final String username = "Google";
            userId = mTestDevice.createUser(username, false, false);
            assertTrue(userId > 0);

            mTestDevice.startUser(userId);

            assertTrue(mTestDevice.isUserRunning(userId));

            mTestDevice.stopUser(userId, true, false);

            assertFalse(mTestDevice.isUserRunning(userId));
        } finally {
            if (userId != -1) {
                mTestDevice.removeUser(userId);
            }
        }
    }

    /**
     * Tests the isUserRunning method semi-independently
     *
     * <p>Assuming the device is left in a default state after other tests, the default user should
     * be running, which means we can test our method. If this test fails, it means either a)
     * another test isn't cleaning up properly or b) our method is broken. Either result is worth
     * knowing.
     */
    @Test
    public void testIsUserRunning() throws Exception {
        final int DEFAULT_USER = 0;
        assertTrue(mTestDevice.isUserRunning(DEFAULT_USER));
    }
}
