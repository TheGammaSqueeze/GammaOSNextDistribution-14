/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * These tests cover:
 * 1) general enterprise access to AppSearch data through EnterpriseGlobalSearchSession, and
 * 2) enterprise fields restrictions applied to the Person schema
 *
 * <p>These tests do not cover:
 * 1) the enterprise transformation applied to Person documents, since that only applies to
 * AppSearch's actual contacts corpus, and these tests run using the local AppSearch database
 * 2) the managed profile device policy check for AppSearch's actual contacts corpus as we cannot
 * set the policy in CTS tests
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class EnterpriseContactsMultiUserTest extends AppSearchHostTestBase {
    private static int sPrimaryUserId;
    private static int sSecondaryUserId;
    private static int sEnterpriseProfileUserId;
    private static boolean sIsTemporaryEnterpriseProfile;
    private static ITestDevice sDevice;

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                testInfo.getDevice().isMultiUserSupported());
        ITestDevice device = testInfo.getDevice();
        sPrimaryUserId = device.getPrimaryUserId();
        sSecondaryUserId = createSecondaryUser(device);
        sEnterpriseProfileUserId = getOrCreateEnterpriseProfile(testInfo.getDevice());
        sDevice = device;
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (sSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(sSecondaryUserId);
        }
        if (sIsTemporaryEnterpriseProfile) {
            testInfo.getDevice().removeUser(sEnterpriseProfileUserId);
        }
    }

    /** Creates a test user and returns the user id. */
    private static int createSecondaryUser(ITestDevice device) throws DeviceNotAvailableException {
        int profileId = device.createUser("Test User #1");
        assertThat(device.startUser(profileId)).isTrue();
        return profileId;
    }

    /** Gets or creates an enterprise profile and returns the user id. */
    private static int getOrCreateEnterpriseProfile(ITestDevice device)
            throws DeviceNotAvailableException {
        // Search for a managed profile
        for (UserInfo userInfo : device.getUserInfos().values()) {
            if (userInfo.isManagedProfile()) {
                return userInfo.userId();
            }
        }
        // If no managed profile, set up a temporary one
        int parentProfile = device.getCurrentUser();
        // Create a managed profile "work" under the current profile which should be the main user
        String createUserOutput = device.executeShellCommand(
                "pm create-user --profileOf " + parentProfile + " --managed work");
        int profileId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        assertThat(device.startUser(profileId, /*waitFlag=*/ true)).isTrue();
        sIsTemporaryEnterpriseProfile = true;
        return profileId;
    }

    @Before
    public void setUp() throws Exception {
        installPackageAsUser(TARGET_APK_A, /*grantPermission=*/ true, sPrimaryUserId);
        installPackageAsUser(TARGET_APK_A, /*grantPermission=*/ true, sEnterpriseProfileUserId);
    }

    /** As setup, we need the enterprise user to first create some contacts locally. */
    private void setUpEnterpriseContacts() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA("setUpEnterpriseContacts",
                sEnterpriseProfileUserId,
                Collections.emptyMap());
    }

    private void setUpEnterpriseContactsWithoutEnterprisePermissions() throws Exception {
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "setUpEnterpriseContactsWithoutEnterprisePermissions",
                sEnterpriseProfileUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_hasEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testHasEnterpriseAccess",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessIfEnterpriseProfileIsStopped()
            throws Exception {
        setUpEnterpriseContacts();
        try {
            assertThat(sDevice.stopUser(sEnterpriseProfileUserId, /*waitFlag=*/ true, /*forceFlag=*/
                    true)).isTrue();
            runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                    sPrimaryUserId,
                    Collections.emptyMap());
        } finally {
            assertThat(sDevice.startUser(sEnterpriseProfileUserId, /*waitFlag=*/ true)).isTrue();
        }
    }

    @Test
    public void testMainUser_doesNotHaveEnterpriseAccessToNonEnterpriseSchema() throws Exception {
        setUpEnterpriseContactsWithoutEnterprisePermissions();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testWorkProfile_doesNotHaveEnterpriseAccess() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sEnterpriseProfileUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSecondaryUser_doesNotHaveEnterpriseAccess() throws Exception {
        installPackageAsUser(TARGET_APK_A, /*grantPermission=*/ true, sSecondaryUserId);
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA("testDoesNotHaveEnterpriseAccess",
                sSecondaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testGetEnterpriseContact_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testGetEnterpriseContact_withProjection",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withProjection() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withProjection",
                sPrimaryUserId,
                Collections.emptyMap());
    }

    @Test
    public void testSearchEnterpriseContacts_withFilter() throws Exception {
        setUpEnterpriseContacts();
        runEnterpriseContactsDeviceTestAsUserInPkgA(
                "testSearchEnterpriseContacts_withFilter",
                sPrimaryUserId,
                Collections.emptyMap());
    }
}
