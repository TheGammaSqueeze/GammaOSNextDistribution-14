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

package com.android.cts.devicepolicy;

import static org.junit.Assume.assumeTrue;

import org.junit.Test;

// TODO(b/174859111): move to device-side, automotive specific module
/**
 * Device owner tests specific for devices that use
 * {@link android.os.UserManager#isHeadlessSystemUserMode()}.
 */
public final class HeadlessSystemUserDeviceOwnerTest extends BaseDeviceOwnerTest {

    @Override
    public void setUp() throws Exception {
        assumeTrue("device is not headless system user mode", isHeadlessSystemUserMode());

        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        if (!isHeadlessSystemUserMode()) return;

        super.tearDown();
    }

    @Test
    public void testProfileOwnerIsSetOnCurrentUser() throws Exception {
        executeDeviceTest("testProfileOwnerIsSetOnCurrentUser");
    }

    @Test
    public void testProfileOwnerIsSetOnNewUser() throws Exception {
        assumeCanCreateAdditionalUsers(1);

        executeDeviceTest("testProfileOwnerIsSetOnNewUser");
    }

    private void executeDeviceTest(String testMethod) throws Exception {
        executeDeviceTestMethod(".HeadlessSystemUserTest", testMethod);
    }
}
