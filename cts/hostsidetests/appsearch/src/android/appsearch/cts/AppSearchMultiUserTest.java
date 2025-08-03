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

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * Test to cover multi-user interacting with AppSearch.
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
public class AppSearchMultiUserTest extends AppSearchHostTestBase {

    private static int sInitialUserId;
    private static int sSecondaryUserId;

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                testInfo.getDevice().isMultiUserSupported());

        sInitialUserId = testInfo.getDevice().getPrimaryUserId();
        sSecondaryUserId = testInfo.getDevice().createUser("Test_User");
        assertThat(testInfo.getDevice().startUser(sSecondaryUserId)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        if (!getDevice().isUserRunning(sSecondaryUserId)) {
            getDevice().startUser(sSecondaryUserId, /*waitFlag=*/true);
        }
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, sInitialUserId);
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, sSecondaryUserId);

        runDeviceTestAsUserInPkgA("clearTestData", sInitialUserId);
        runDeviceTestAsUserInPkgA("clearTestData", sSecondaryUserId);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (sSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(sSecondaryUserId);
        }
    }

    @Test
    public void testMultiUser_cantAccessOtherUsersData() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", sSecondaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", sSecondaryUserId);
        // Cannot get the document from another user.
        runDeviceTestAsUserInPkgA("testGetDocuments_nonexist", sInitialUserId);
    }

    @Test
    public void testMultiUser_canInteractAsAnotherUser() throws Exception {
        Map<String, String> args =
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId));

        // We can do the normal set of operations while pretending to be another user.
        runDeviceTestAsUserInPkgA("testPutDocumentsAsAnotherUser", sInitialUserId, args);
        runDeviceTestAsUserInPkgA("testGetDocumentsAsAnotherUser_exist", sInitialUserId, args);
    }

    @Test
    public void testCreateSessionInStoppedUser() throws Exception {
        Map<String, String> args =
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId));
        getDevice().stopUser(sSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        runDeviceTestAsUserInPkgA("createSessionInStoppedUser", sInitialUserId, args);
    }

    @Test
    public void testStopUser_persistData() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", sSecondaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", sSecondaryUserId);
        getDevice().stopUser(sSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        getDevice().startUser(sSecondaryUserId, /*waitFlag=*/true);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", sSecondaryUserId);
    }

    @Test
    public void testPackageUninstall_onLockedUser() throws Exception {
        installPackageAsUser(TARGET_APK_B, /* grantPermission= */true, sSecondaryUserId);
        // package A grants visibility to package B.
        runDeviceTestAsUserInPkgA("testPutDocuments", sSecondaryUserId);
        // query the document from another package.
        runDeviceTestAsUserInPkgB("testGlobalGetDocuments_exist", sSecondaryUserId);
        getDevice().stopUser(sSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        uninstallPackage(TARGET_PKG_A);
        getDevice().startUser(sSecondaryUserId, /*waitFlag=*/true);
        // Max waiting time is 5 second.
        for (int i = 0; i < 5; i++) {
            try {
                // query the document from another package, verify the document of package A is
                // removed
                runDeviceTestAsUserInPkgB("testGlobalGetDocuments_nonexist", sSecondaryUserId);
                // The test is passed.
                return;
            } catch (AssertionError e) {
                // The package hasn't uninstalled yet, sleeping 1s before polling again.
                Thread.sleep(1000);
            }
        }
        throw new AssertionError("Failed to prune package data after 5 seconds");
    }

    @Test
    public void testReadStorageInfoFromFile() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", sSecondaryUserId);

        getDevice().stopUser(sSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        getDevice().startUser(sSecondaryUserId, /*waitFlag=*/true);
        // Write user's storage info into file while initializing AppSearchImpl.
        runStorageAugmenterDeviceTestAsUserInPkgA("connectToAppSearch", sSecondaryUserId);

        // Disconnect user from AppSearch. While AppSearchImpl doesn't exist for the user, user's
        // storage info would be read from file.
        getDevice().stopUser(sSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        getDevice().startUser(sSecondaryUserId, /*waitFlag=*/true);

        runStorageAugmenterDeviceTestAsUserInPkgA("testReadStorageInfo", sSecondaryUserId);
    }
}
