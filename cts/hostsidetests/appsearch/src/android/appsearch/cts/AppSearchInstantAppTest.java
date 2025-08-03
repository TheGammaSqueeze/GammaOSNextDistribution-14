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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Nonnull;

/**
 * Test to cover instant app behavior with AppSearch.
 *
 * <p>This test is split into two distinct parts: The first part is the test-apps that runs on the
 * device and interactive with AppSearch. This class is the second part that runs on the host and
 * triggers tests in the first part for different users.
 *
 * <p>To trigger a device test, call runDeviceTestAsUser with a specific the test name and specific
 * user.
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchInstantAppTest extends BaseHostJUnit4Test {
    private static final String TARGET_APK_A = "CtsAppSearchHostTestHelperA.apk";
    private static final String TARGET_PKG_A = "android.appsearch.app.helper_a";
    private static final String TEST_CLASS_A = TARGET_PKG_A + ".AppSearchInstantAppTest";
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    private int mPrimaryUserId;

    private void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId)
            throws Exception {
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(getDevice(), TARGET_PKG_A, TEST_CLASS_A, testMethod, userId,
                        DEFAULT_INSTRUMENTATION_TIMEOUT_MS)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        mPrimaryUserId = getDevice().getPrimaryUserId();
        uninstallPackage(TARGET_PKG_A);
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mPrimaryUserId, "--instant");
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TARGET_PKG_A);
    }

    @Test
    public void testInstantAppDoesntHaveAccess() throws Exception {
        runDeviceTestAsUserInPkgA("testInstantAppDoesntHaveAccess", mPrimaryUserId);
    }
}
