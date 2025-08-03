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

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to cover install and uninstall packages with AppSearch.
 *
 * <p>This test is split into two distinct parts: The first part is the test-apps that runs on the
 * device and interactive with AppSearch. This class is the second part that runs on the host and
 * triggers tests in the first part for different users.
 *
 * <p>To trigger a device test, call runDeviceTestAsUser with a specific the test name and specific
 * user.
 *
 * <p>Unlock your device when test locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchPackageTest extends AppSearchHostTestBase {

    private int mPrimaryUserId;

    @Before
    public void setUp() throws Exception {
        mPrimaryUserId = getDevice().getPrimaryUserId();
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mPrimaryUserId);
        installPackageAsUser(TARGET_APK_B, /* grantPermission= */true, mPrimaryUserId);
        runDeviceTestAsUserInPkgA("clearTestData", mPrimaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TARGET_PKG_A);
        uninstallPackage(TARGET_PKG_B);
    }

    @Test
    public void testPackageRemove() throws Exception {
        // package A grants visibility to package B.
        runDeviceTestAsUserInPkgA("testPutDocuments", mPrimaryUserId);
        // query the document from another package.
        runDeviceTestAsUserInPkgB("testGlobalGetDocuments_exist", mPrimaryUserId);
        // remove the package. Success will return Null, otherwise return error code.
        assertThat(uninstallPackage(TARGET_PKG_A)).isNull();

        // Max waiting time is 10 second.
        for (int i = 0; i < 10; i++) {
            try {
                // query the document from another package, verify the document of package A is
                // removed
                runDeviceTestAsUserInPkgB("testGlobalGetDocuments_nonexist", mPrimaryUserId);
                // The test is passed.
                return;
            } catch (AssertionError e) {
                // The package hasn't uninstalled yet, sleeping 1s before polling again.
                RunUtil.getDefault().sleep(1000);
            }
        }
        throw new AssertionError("Failed to prune package data after 5 seconds");
    }

    @Test
    public void testPackageUninstall_immediatelyReboot() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", mPrimaryUserId);
        runDeviceTestAsUserInPkgA("closeAndFlush", mPrimaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", mPrimaryUserId);
        // We won't be able to verify the uninstall is not done before we reboot the device.
        // This test is a safety that we could prune dead package data when we unlock the user. Any
        // flaky in the testGetDocuments_nonexist assert means we have a problem.
        // We have AppSearchMultiUserTest.testPackageUninstall_onLockedUser and unit test
        // AppSearchImplTest.testPrunePackageData() to cover the same functionality.
        uninstallPackage(TARGET_PKG_A);
        // When test locally, if your test device doesn't support rebootUserspace(), you need to
        // manually unlock your device screen after it got fully rebooted. Or remove your screen
        // lock pin before the test. Otherwise it will hang.
        rebootAndWaitUntilReady();
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mPrimaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_nonexist", mPrimaryUserId);
    }
}
