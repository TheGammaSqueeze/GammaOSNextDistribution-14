/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Test to cover multi-user CP2 contacts indexing into AppSearch.
 *
 * <p>Unlock your device when testing locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@Ignore("b/285969557 Re-enable tests after figuring out a better way to wait for lifecycle "
        + "events to complete")
public class ContactsIndexerMultiUserTest extends AppSearchHostTestBase {

    private static int sSecondaryUserId;

    // Current default value for contacts_indexer_enabled
    private static String sPrevContactsIndexerEnabled;

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                testInfo.getDevice().isMultiUserSupported());

        sSecondaryUserId = testInfo.getDevice().createUser("Test User #1");
        assertThat(testInfo.getDevice().startUser(sSecondaryUserId)).isTrue();
        sPrevContactsIndexerEnabled = testInfo.getDevice().executeShellCommand(
                "device_config get appsearch contacts_indexer_enabled").trim();
    }

    @Before
    public void setUp() throws Exception {
        startUserAndInstallPackage();
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (sSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(sSecondaryUserId);
        }

        String currContactsIndexerEnabled = testInfo.getDevice().executeShellCommand(
                "device_config get appsearch contacts_indexer_enabled").trim();
        // Do not try to set flag if current flag value is already equal to the desired value.
        if (sPrevContactsIndexerEnabled != null
                && !sPrevContactsIndexerEnabled.equals(currContactsIndexerEnabled)) {
            if (sPrevContactsIndexerEnabled.equals("null")) {
                deleteCiFlag(testInfo.getDevice());
            } else {
                setCiEnabled(testInfo.getDevice(),
                        Boolean.parseBoolean(sPrevContactsIndexerEnabled));
            }
        }
    }

    @Test
    public void testMultiUser_scheduleMultipleFullUpdateJobs() throws Exception {
        runContactsIndexerDeviceTestAsUserInPkgA("testFullUpdateJobIsScheduled",
                sSecondaryUserId,
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId)));
    }

    @Test
    public void testMultiUser_CiDisabled_cancelsFullUpdateJobs() throws Exception {
        setCiEnabled(getDevice(), false);
        startUserAndInstallPackage();
        runContactsIndexerDeviceTestAsUserInPkgA("testFullUpdateJobIsNotScheduled",
                sSecondaryUserId,
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId)));
    }

    @Test
    public void testMultiUser_CiDisabledAndThenEnabled_schedulesFullUpdateJobs() throws Exception {
        setCiEnabled(getDevice(), false);
        setCiEnabled(getDevice(), true);
        startUserAndInstallPackage();
        runContactsIndexerDeviceTestAsUserInPkgA("testFullUpdateJobIsScheduled",
                sSecondaryUserId,
                Collections.singletonMap(USER_ID_KEY, String.valueOf(sSecondaryUserId)));
    }

    private static void setCiEnabled(ITestDevice device,
            boolean ciEnabled) throws Exception {
        device.executeShellCommand(
                "device_config put appsearch contacts_indexer_enabled "
                        + ciEnabled);
        assertThat(device.executeShellCommand(
                "device_config get appsearch contacts_indexer_enabled").trim())
                .isEqualTo(String.valueOf(ciEnabled));
        rebootAndWaitUntilReady(device);
    }

    private static void deleteCiFlag(ITestDevice device) throws Exception {
        device.executeShellCommand(
                "device_config delete appsearch contacts_indexer_enabled");
        assertThat(device.executeShellCommand(
                "device_config get appsearch contacts_indexer_enabled").trim())
                .isEqualTo("null");
        rebootAndWaitUntilReady(device);
    }

    private void startUserAndInstallPackage() throws Exception {
        if (!getDevice().isUserRunning(sSecondaryUserId)) {
            getDevice().startUser(sSecondaryUserId, /*waitFlag=*/ true);
        }
        installPackageAsUser(TARGET_APK_A, /*grantPermission=*/ true, sSecondaryUserId);
    }
}
