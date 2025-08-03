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

package android.car.cts.builtin.os;

import static android.Manifest.permission.CREATE_USERS;

import static org.junit.Assert.assertThrows;

import android.car.builtin.os.UserManagerHelper;
import android.car.cts.builtin.AbstractCarBuiltinTestCase;
import android.os.Binder;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * This class contains simple {@link UserManagerHelper} tests, i.e., they don't create user or do
 * any other heavy stuff - for that, please use {@link UserManagerHelperHeavyTest} instead.
 */
public final class UserManagerHelperLiteTest extends AbstractCarBuiltinTestCase {

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForCurrentUser() {
        // Current user should not be ephemeral or guest because test runs as secondary user.
        UserHandle currentUser = TestApis.users().current().userHandle();

        expectWithMessage("User %s isEphemeralUser", currentUser).that(
                UserManagerHelper.isEphemeralUser(mUserManager, currentUser)).isFalse();
        expectWithMessage("User %s isFullUser", currentUser).that(
                UserManagerHelper.isFullUser(mUserManager, currentUser)).isTrue();
        expectWithMessage("User %s isGuestUser", currentUser).that(
                UserManagerHelper.isGuestUser(mUserManager, currentUser)).isFalse();

        // Current user should be enabled.
        expectWithMessage("User %s isEnabledUser", currentUser).that(
                UserManagerHelper.isEnabledUser(mUserManager, currentUser)).isTrue();

        // Current should be initialized, otherwise test would be running
        expectWithMessage("User %s isInitializedUser", currentUser).that(
                UserManagerHelper.isInitializedUser(mUserManager, currentUser)).isTrue();

        // Current should be part of getUserHandles
        expectGetUserHandlesHasUser(currentUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @RequireNotHeadlessSystemUserMode(reason="Requires full system user")
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForFullSystemUser() {
        UserHandle systemUser = UserHandle.SYSTEM;

        expectWithMessage("System user isEphemeralUser").that(
                UserManagerHelper.isEphemeralUser(mUserManager, systemUser)).isFalse();
        expectWithMessage("System user isFullUser").that(
                UserManagerHelper.isFullUser(mUserManager, systemUser)).isTrue();
        expectWithMessage("System user isGuestUser").that(
                UserManagerHelper.isGuestUser(mUserManager, systemUser)).isFalse();
        expectWithMessage("System user isEnabledUser").that(
                UserManagerHelper.isEnabledUser(mUserManager, systemUser)).isTrue();
        expectWithMessage("System user isInitializedUser").that(
                UserManagerHelper.isInitializedUser(mUserManager, systemUser)).isTrue();
        expectGetUserHandlesHasUser(systemUser);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS) // needed to query user properties
    @RequireHeadlessSystemUserMode(reason = "Testing headless system user")
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#isEphemeralUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isFullUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isGuestUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isEnabledUser(UserManager, UserHandle)",
            "android.car.builtin.os.UserManagerHelper#isInitializedUser(UserManager, UserHandle)"
    })
    public void testMultiplePropertiesForHeadlessSystemUser() {
        UserHandle systemUser = UserHandle.SYSTEM;

        expectWithMessage("System user isEphemeralUser").that(
                UserManagerHelper.isEphemeralUser(mUserManager, systemUser)).isFalse();
        expectWithMessage("System user isFullUser").that(
                UserManagerHelper.isFullUser(mUserManager, systemUser)).isFalse();
        expectWithMessage("System user isGuestUser").that(
                UserManagerHelper.isGuestUser(mUserManager, systemUser)).isFalse();
        expectWithMessage("System user isEnabledUser").that(
                UserManagerHelper.isEnabledUser(mUserManager, systemUser)).isTrue();
        expectWithMessage("System user isInitializedUser").that(
                UserManagerHelper.isInitializedUser(mUserManager, systemUser)).isTrue();
        expectGetUserHandlesHasUser(systemUser);
    }

    @Test
    @ApiTest(apis = {
            "android.car.builtin.os.UserManagerHelper#getDefaultUserTypeForUserInfoFlags(int)"
    })
    public void testGetDefaultUserTypeForUserInfoFlags() {
        // Simple example.
        expectWithMessage("Default user type for FLAG_MANAGED_PROFILE").that(
                UserManagerHelper.getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_MANAGED_PROFILE)).isEqualTo(
                UserManager.USER_TYPE_PROFILE_MANAGED);

        // Type plus a non-type flag.
        expectWithMessage("Default user type for FLAG_GUEST | FLAG_EPHEMERAL").that(
                UserManagerHelper.getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_GUEST | UserManagerHelper.FLAG_EPHEMERAL)).isEqualTo(
                UserManager.USER_TYPE_FULL_GUEST);

        // Two types, which is illegal.
        assertThrows(IllegalArgumentException.class, () -> UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_MANAGED_PROFILE | UserManagerHelper.FLAG_GUEST));

        // No type, which defaults to {@link UserManager#USER_TYPE_FULL_SECONDARY}.
        expectWithMessage("Default user type for FLAG_EPHEMERAL").that(
                UserManagerHelper.getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_EPHEMERAL)).isEqualTo(
                UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getDefaultUserName(Context)"})
    public void testGetDefaultUserName() {
        expectWithMessage("Default user name").that(
                UserManagerHelper.getDefaultUserName(mContext)).isNotNull();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getMaxRunningUsers(Context)"})
    public void testGetMaxRunningUsers() {
        expectWithMessage("Max running users").that(
                UserManagerHelper.getMaxRunningUsers(mContext)).isGreaterThan(0);
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getUserId(int)"})
    public void testGetUserId() {
        expectWithMessage("User id").that(
                UserManagerHelper.getUserId(Binder.getCallingUid())).isEqualTo(
                Binder.getCallingUserHandle().getIdentifier());
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isVisibleBackgroundUsersSupported(UserManager)"})
    @RequireVisibleBackgroundUsers(reason = "Because test is testing exactly that")
    public void testIsVisibleBackgroundUsersSupported() {
        expectWithMessage("Users on secondary displays supported").that(
                UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isVisibleBackgroundUsersSupported(UserManager)"})
    @RequireNotVisibleBackgroundUsers(reason = "Because test is testing exactly that")
    public void testIsVisibleBackgroundUsersSupported_not() {
        expectWithMessage("Users on secondary displays not supported").that(
                UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isVisibleBackgroundUsersOnDefaultDisplaySupported(UserManager)"})
    @RequireVisibleBackgroundUsersOnDefaultDisplay(reason = "Because test is testing exactly that")
    public void testIsVisibleBackgroundUsersOnDefaultDisplaySupported() {
        expectWithMessage("Visible background Users on default display supported").that(
                UserManagerHelper.isVisibleBackgroundUsersOnDefaultDisplaySupported(mUserManager))
                    .isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#"
            + "isVisibleBackgroundUsersOnDefaultDisplaySupported(UserManager)"})
    @RequireNotVisibleBackgroundUsersOnDefaultDisplay(
            reason = "Because test is testing exactly that")
    public void testIsVisibleBackgroundUsersOnDefaultDisplaySupported_not() {
        expectWithMessage("Visible background Users on default display not supported").that(
                UserManagerHelper.isVisibleBackgroundUsersOnDefaultDisplaySupported(mUserManager))
                    .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.car.builtin.os.UserManagerHelper#getMaxSupportedUsers(Context)"})
    public void testGetMaxSupportedUsers() {
        expectWithMessage("Max supported users").that(
                UserManagerHelper.getMaxSupportedUsers(mContext)).isGreaterThan(0);
    }

    private void expectGetUserHandlesHasUser(UserHandle user) {
        List<UserHandle> allUsersHandles =
                UserManagerHelper.getUserHandles(mUserManager, /* excludeDying= */ false);
        expectWithMessage("allUsersHandles").that(allUsersHandles).contains(user);
    }
}
