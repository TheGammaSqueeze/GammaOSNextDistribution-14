/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.appcloning.contacts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;

import com.android.cts.appcloning.AppCloningBaseHostTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
@LargeTest
public class ManagedProfileContactsAccessTest extends AppCloningBaseHostTest  {

    private static final String LAUNCHABLE_CLONE_PROFILE_APP =
            "CtsAppCloningLaunchableCloneProfileApp.apk";
    private static final String CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST =
            "com.android.cts.launchable.cloneprofile.contacts.app";
    private static final String MANAGED_PROFILE_OWNER_APP =
            "CtsManagedProfileOwnerApp.apk";
    private static final String MANAGED_PROFILE_OWNER_APP_TEST_PKG =
            "com.android.cts.managedprofile.owner.app";
    private static final String MANAGED_PROFILE_OWNER_APP_TEST_CLASS =
            "ManagedProfileOwnerAppTest";
    protected static final String ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_OWNER_APP_TEST_PKG + ".ManagedProfileOwnerAppTest$BasicAdminReceiver";
    private static final String CONTACTS_SHARING_TEST_CLASS = "CloneContactsSharingTest";

    public static final String FEATURE_DEVICE_ADMIN = "android.software.device_admin";
    public static final String FEATURE_MANAGED_USERS = "android.software.managed_users";

    private static final String TEST_ACCOUNT_TYPE = "test.com";
    private static final String TEST_CONTACT_PHONE_NUMBER_MIMETYPE =
            "vnd.android.cursor.item/phone_v2";
    private static final String PARENT_USER_ID = "0";
    private static String sManagedProfileUserId = "";
    private static final int USER_ALL = -1; // user id to indicate all users on the device

    private static TestContactsDataManager.TestContact sManagedProfileTestContactDetails;
    private static TestContactsDataManager sTestContactsDataManager;

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        AppCloningBaseHostTest.setDevice(testInfo.getDevice());

        assumeTrue(isAtLeastU(testInfo.getDevice()));
        assumeTrue("Device does not support more than two users together",
                supportsMoreThanTwoUsers());
        assumeTrue("App cloning building block config is disabled on the device",
                isAppCloningBuildingBlockConfigEnabled(testInfo.getDevice()));
        assumeRequiredManagedProfileFeaturesSupported();

        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
        waitForBroadcastIdle();
        switchAppCloningBuildingBlocksFlag(true);

        sTestContactsDataManager = new TestContactsDataManager(testInfo.getDevice());
        createAndStartManagedProfile();
        insertManagedProfileContact();
    }

    @AfterClassWithInfo
    public static void afterClass(TestInformation testInfo) throws Exception {
        if (supportsMoreThanTwoUsers()
                && doesDeviceSupportCloneContactSharing(testInfo.getDevice())
                && areRequiredManagedProfileFeaturesSupported()) {
            removeManagedProfile();
            switchAppCloningBuildingBlocksFlag(false);
            AppCloningBaseHostTest.baseHostTeardown();
        }
    }

    private static boolean areRequiredManagedProfileFeaturesSupported()
            throws DeviceNotAvailableException {
        return doesDeviceHaveFeature(FEATURE_DEVICE_ADMIN)
                && doesDeviceHaveFeature(FEATURE_MANAGED_USERS);
    }

    private static void assumeRequiredManagedProfileFeaturesSupported()
            throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_DEVICE_ADMIN);
        assumeHasDeviceFeature(FEATURE_MANAGED_USERS);
    }

    private static boolean doesDeviceSupportCloneContactSharing(ITestDevice testDevice)
            throws Exception {
        return isAtLeastU(testDevice)
                && isAppCloningBuildingBlockConfigEnabled(testDevice)
                && isAppCloningSupportedOnDevice();
    }

    private static void createAndStartManagedProfile() throws Exception {
        sManagedProfileUserId = String.valueOf(createManagedProfile(PARENT_USER_ID));
        startUserAndWait(sManagedProfileUserId);
        waitForBroadcastIdle();
    }

    private static void removeManagedProfile() throws Exception {
        removeUser(sManagedProfileUserId);
    }

    protected static int createManagedProfile(String parentUserId)
            throws DeviceNotAvailableException {
        String commandOutput = getCreateManagedProfileCommandOutput(parentUserId);
        return getUserIdFromCreateUserCommandOutput(commandOutput);
    }

    private static int getUserIdFromCreateUserCommandOutput(String commandOutput) {
        // Extract the id of the new user.
        String[] tokens = commandOutput.split("\\s+");
        assertWithMessage(commandOutput + " expected to have format \"Success: {USER_ID}\"")
                .that(tokens.length > 0).isTrue();
        assertWithMessage(commandOutput + " expected to have format \"Success: {USER_ID}\"")
                .that("Success:").isEqualTo(tokens[0]);
        return Integer.parseInt(tokens[tokens.length - 1]);
    }

    private static String getCreateManagedProfileCommandOutput(String parentUserId)
            throws DeviceNotAvailableException {
        String command = "pm create-user --profileOf " + parentUserId + " --managed "
                + "TestProfile_" + System.currentTimeMillis();
        String commandOutput = sDevice.executeShellCommand(command);
        LogUtil.CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput;
    }

    private boolean setProfileOwner(String componentName, int userId)
            throws DeviceNotAvailableException {
        String command = "dpm set-profile-owner --user " + userId + " '" + componentName + "'";
        String commandOutput = getDevice().executeShellCommand(command);
        boolean success = commandOutput.startsWith("Success:");
        if (!success) {
            LogUtil.CLog.e("Command Failed " + command);
        }
        return success;
    }

    private static void insertManagedProfileContact() throws DeviceNotAvailableException {
        sManagedProfileTestContactDetails =
                sTestContactsDataManager.insertTestContactForManagedProfile(sManagedProfileUserId,
                        TEST_ACCOUNT_TYPE);
        assertThat(sManagedProfileTestContactDetails.rawContact).isNotNull();
        assertThat(sManagedProfileTestContactDetails.rawContactDataList).isNotNull();
        assertThat(sManagedProfileTestContactDetails.rawContactDataList.length).isNotEqualTo(0);
    }

    /**
     * Enables/Disables the contact sharing feature flag
     * @throws Exception
     */
    private static void switchAppCloningBuildingBlocksFlag(boolean value) throws Exception {
        setFeatureFlagValue("app_cloning", "enable_app_cloning_building_blocks",
                String.valueOf(value));
    }

    @Test
    public void testClonedAppsAccessManagedProfileContacts_contactReadSuccessfully()
            throws Exception {
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        TestContactsDataManager.TestRawContact rawContactDetails =
                sManagedProfileTestContactDetails.rawContact;
        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", rawContactDetails.accountType);
        args.put("test_contact_account_name", rawContactDetails.accountName);
        args.put("test_contact_custom_ringtone", rawContactDetails.customRingtone);

        for (TestContactsDataManager.TestRawContactData testContactData:
                sManagedProfileTestContactDetails.rawContactDataList) {
            if (testContactData.mimeType.equals(TEST_CONTACT_PHONE_NUMBER_MIMETYPE)) {
                args.put("test_contact_phone_number", testContactData.data1);
            }
        }

        // Check that cross-profile reads are allowed for cloned app with a launch-able activity
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testAccessManagedProfileContacts_contactReadSuccessfully",
                Integer.parseInt(sCloneUserId), args);
    }

    @Test
    public void testClonedAppsAccessManagedProfileContacts_contactReadsBlocked()
            throws Exception {
        // Install the APK on both primary and profile user in one single transaction.
        installPackage(MANAGED_PROFILE_OWNER_APP, "--user " + USER_ALL);

        // Set profile owner for managed profile
        boolean isSetOwnerSuccessful = setProfileOwner(
                MANAGED_PROFILE_OWNER_APP_TEST_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                Integer.parseInt(sManagedProfileUserId));
        assertWithMessage("Setting profile owner for managed profile failed!")
                .that(isSetOwnerSuccessful).isTrue();

        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));

        TestContactsDataManager.TestRawContact rawContactDetails =
                sManagedProfileTestContactDetails.rawContact;
        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", rawContactDetails.accountType);

        // Disable cross-profile contact reads for managed profile contacts through profile-owner
        runDeviceTestAsUser(MANAGED_PROFILE_OWNER_APP_TEST_PKG,
                MANAGED_PROFILE_OWNER_APP_TEST_PKG + "." + MANAGED_PROFILE_OWNER_APP_TEST_CLASS,
                "testSetDisallowWorkContactsAccessPolicy",
                Integer.parseInt(sManagedProfileUserId), new HashMap<>());

        // Test that the managed profile contact reads from cloned app are blocked
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testAccessManagedProfileContacts_contactsReadBlocked",
                Integer.parseInt(sCloneUserId), args);

        // Enable cross-profile contact reads for managed profile contacts again
        runDeviceTestAsUser(MANAGED_PROFILE_OWNER_APP_TEST_PKG,
                MANAGED_PROFILE_OWNER_APP_TEST_PKG + "." + MANAGED_PROFILE_OWNER_APP_TEST_CLASS,
                "testEnableWorkContactsAccess",
                Integer.parseInt(sManagedProfileUserId), new HashMap<>());
    }
}
