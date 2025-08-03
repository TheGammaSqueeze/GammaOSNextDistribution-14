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
 * limitations under the License
 */
package android.host.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.Presubmit;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test verifies that ephemeral users are removed after switched away and after reboot.
 *
 * Run: atest android.host.multiuser.EphemeralTest
 */
@LargeTest
@RunWith(DeviceJUnit4ClassRunner.class)
public class EphemeralTest extends BaseMultiUserTest {

    private static final String TEST_APP_PKG_NAME = "com.android.cts.multiuser";
    private static final String TEST_APP_PKG_APK = "CtsMultiuserApp.apk";

    private static final String FEATURE_DEVICE_ADMIN = "android.software.device_admin";

    // Values below were copied from UserManager
    private static final int REMOVE_RESULT_REMOVED = 0;
    private static final int REMOVE_RESULT_DEFERRED = 1;
    private static final int REMOVE_RESULT_ERROR_USER_RESTRICTION = -2;
    private static final String DISALLOW_REMOVE_USER = "no_remove_user";

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    /** Test to verify ephemeral user is removed after switch out to another user. */
    @Presubmit
    @Test
    public void testSwitchAndRemoveEphemeralUser() throws Exception {
        final int ephemeralUserId = createEphemeralUser();

        assertSwitchToUser(ephemeralUserId);
        assertSwitchToUser(mInitialUserId);
        waitForUserRemove(ephemeralUserId);
        assertUserNotPresent(ephemeralUserId);
    }

    /** Test to verify ephemeral user is removed after reboot. */
    @Presubmit
    @Test
    public void testRebootAndRemoveEphemeralUser() throws Exception {
        final int ephemeralUserId = createEphemeralUser();

        assertSwitchToUser(ephemeralUserId);
        getDevice().reboot();
        assertUserNotPresent(ephemeralUserId);
    }

    /**
     * Test to verify that an ephemeral user, with an account, is safely removed after rebooting
     * from it.
     */
    @Test
    public void testRebootAndRemoveEphemeralUser_withAccount() throws Exception {
        final int ephemeralUserId = createEphemeralUser();
        assertSwitchToUser(ephemeralUserId);

        installPackageAsUser(
                TEST_APP_PKG_APK, /* grantPermissions= */true, ephemeralUserId, /* options= */"-t");
        assertWithMessage("isPackageInstalled(app=%s, user=%s)", TEST_APP_PKG_APK, ephemeralUserId)
                .that(getDevice().isPackageInstalled(TEST_APP_PKG_NAME,
                        String.valueOf(ephemeralUserId)))
                .isTrue();

        final boolean appResult = runDeviceTests(getDevice(),
                TEST_APP_PKG_NAME,
                TEST_APP_PKG_NAME + ".AccountCreator",
                "addMockAccountForCurrentUser",
                ephemeralUserId,
                5 * 60 * 1000L /* ms */);
        assertWithMessage("Device-side test passing").that(appResult).isTrue();

        getDevice().reboot();
        assertUserNotPresent(ephemeralUserId);
    }

    /**
     * Test to verify that
     * {@link android.os.UserManager#removeUserWhenPossible(UserHandle, boolean)} immediately
     * removes a user that isn't running.
     * <p>
     * Indirectly executed by means of the --set-ephemeral-if-in-use flag
     */
    @Presubmit
    @Test
    public void testRemoveUserWhenPossible_nonRunningUserRemoved() throws Exception {
        final int userId = createUser();

        executeRemoveUserWhenPossible(userId, /* expectedResult= */ REMOVE_RESULT_REMOVED);

        assertUserNotPresent(userId);
    }

    /**
     * Test to verify that
     * {@link android.os.UserManager#removeUserWhenPossible(UserHandle, boolean)} sets the current
     * user to ephemeral and removes the user after user switch.
     * <p>
     * Indirectly executed by means of the --set-ephemeral-if-in-use flag
     */
    @Presubmit
    @Test
    public void testRemoveUserWhenPossible_currentUserSetEphemeral_removeAfterSwitch()
            throws Exception {
        final int userId = createUser();

        assertSwitchToUser(userId);
        executeRemoveUserWhenPossible(userId, /* expectedResult= */ REMOVE_RESULT_DEFERRED);
        assertUserEphemeral(userId);

        assertSwitchToUser(mInitialUserId);
        waitForUserRemove(userId);
        assertUserNotPresent(userId);
    }

    /**
     * Test to verify that
     * {@link android.os.UserManager#removeUserWhenPossible(UserHandle, boolean)} sets the current
     * user to ephemeral and removes that user after reboot.
     * <p>
     * Indirectly executed by means of the --set-ephemeral-if-in-use flag
     */
    @Presubmit
    @Test
    public void testRemoveUserWhenPossible_currentUserSetEphemeral_removeAfterReboot()
            throws Exception {
        final int userId = createUser();

        assertSwitchToUser(userId);
        executeRemoveUserWhenPossible(userId, /* expectedResult= */ REMOVE_RESULT_DEFERRED);
        assertUserEphemeral(userId);

        getDevice().reboot();
        assertUserNotPresent(userId);
    }

    /**
     * Test to verify that
     * {@link android.os.UserManager#removeUserWhenPossible(UserHandle, boolean)} works correctly
     * when a DPC set the {@code no_remove_user} restriction in the current user.
     */
    @Presubmit
    @Test
    public void testRemoveUserWhenPossible_devicePolicyIsSet() throws Exception {
        assumeTrue("Test requires device with device admin support",
                getDevice().hasFeature(FEATURE_DEVICE_ADMIN));
        installPackage(DpcCommander.PKG_APK, /* options= */ "-t");
        assertWithMessage("isPackageInstalled(%s)", DpcCommander.PKG_APK)
                .that(getDevice().isPackageInstalled(DpcCommander.PKG_NAME))
                .isTrue();

        final DpcCommander dpc = DpcCommander.forCurrentUser(getDevice());
        final int userId = createUser();

        dpc.setProfileOwner();
        try {
            dpc.addUserRestriction(DISALLOW_REMOVE_USER);
            try {
                executeRemoveUserWhenPossible(userId, /* overrideDevicePolicy= */ false,
                        /* expectedResult= */ REMOVE_RESULT_ERROR_USER_RESTRICTION);
                assertUserPresent(userId);

                executeRemoveUserWhenPossible(userId, /* overrideDevicePolicy= */ true,
                        /* expectedResult= */ REMOVE_RESULT_REMOVED);
                assertUserNotPresent(userId);
            } finally {
                dpc.clearUserRestriction(DISALLOW_REMOVE_USER);
            }
        } finally {
            dpc.removeActiveAdmin();
        }
    }

    private void executeRemoveUserWhenPossible(int userId, int expectedResult) throws Exception {
        executeRemoveUserWhenPossible(userId, /* overrideDevicePolicy= */ false, expectedResult);
    }

    private void executeRemoveUserWhenPossible(int userId, boolean overrideDevicePolicy,
            int expectedResult) throws Exception {
        installPackage(TEST_APP_PKG_APK, /* options= */"-t");
        assertWithMessage("isPackageInstalled(%s)", TEST_APP_PKG_APK)
                .that(getDevice().isPackageInstalled(TEST_APP_PKG_NAME))
                .isTrue();

        DeviceTestRunOptions options = new DeviceTestRunOptions(TEST_APP_PKG_NAME)
                .setDevice(getDevice())
                .setTestClassName(TEST_APP_PKG_NAME + ".UserOperationsTest")
                .setTestMethodName("removeUserWhenPossibleDeviceSide")
                .addInstrumentationArg("userId", String.valueOf(userId))
                .addInstrumentationArg("overrideDevicePolicy", String.valueOf(overrideDevicePolicy))
                .addInstrumentationArg("expectedResult", String.valueOf(expectedResult));
        final boolean appResult = runDeviceTests(options);
        assertWithMessage("Device-side test passing").that(appResult).isTrue();
    }

    private void assertUserEphemeral(int userId) throws Exception {
        assertUserPresent(userId);
        assertWithMessage("User ID %s should be flagged as ephemeral", userId)
                .that(getDevice().getUserInfos().get(userId).isEphemeral()).isTrue();
    }

    private int createUser() throws Exception {
        return createUser(/* isGuest= */ false, /* isEphemeral= */ false);
    }

    private int createEphemeralUser() throws Exception {
        return createUser(/* isGuest= */ false, /* isEphemeral= */ true);
    }

    private int createUser(boolean isGuest, boolean isEphemeral) throws Exception {
        final String name = "TestUser_" + System.currentTimeMillis();
        try {
            return getDevice().createUser(name, isGuest, isEphemeral);
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "Failed to create user (name=%s, isGuest=%s, isEphemeral=%s)",
                    name, isGuest, isEphemeral), e);
        }
    }
}
