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

package com.android.bedstead.nene.users;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.S;

import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Display;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class UserReferenceTest {
    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    public static final UserHandle USER_HANDLE = new UserHandle(USER_ID);
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);
    private static final String PASSWORD = "1234";
    private static final String PIN = "1234";
    private static final String PATTERN = "1234";
    private static final String DIFFERENT_PASSWORD = "2345";
    private static final String DIFFERENT_PIN = "2345";
    private static final String DIFFERENT_PATTERN = "2345";


    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void of_returnsUserReferenceWithValidId() {
        assertThat(UserReference.of(USER_HANDLE).id()).isEqualTo(USER_ID);
    }

    @Test
    public void id_returnsId() {
        assertThat(TestApis.users().find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void userHandle_referencesId() {
        assertThat(TestApis.users().find(USER_ID).userHandle().getIdentifier()).isEqualTo(USER_ID);
    }

    @Test
    public void exists_doesNotExist_returnsFalse() {
        assertThat(TestApis.users().find(NON_EXISTING_USER_ID).exists()).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser
    public void exists_doesExist_returnsTrue() {
        assertThat(sDeviceState.secondaryUser().exists()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    public void remove_userExists_removesUser() {
        UserReference user = sDeviceState.additionalUser();

        user.remove();

        assertThat(TestApis.users().all()).doesNotContain(user);
    }

    @Test
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    public void remove_copeUser_removeUser() {
        sDeviceState.workProfile().remove();

        assertThat(sDeviceState.workProfile().exists()).isFalse();
    }

    @Test
    public void start_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().find(NON_EXISTING_USER_ID).start());
    }

    @Test
    @EnsureHasAdditionalUser
    public void start_userNotStarted_userIsUnlocked() {
        sDeviceState.additionalUser().stop();

        sDeviceState.additionalUser().start();

        assertThat(sDeviceState.additionalUser().isUnlocked()).isTrue();
    }

    @Test
    @EnsureHasSecondaryUser
    public void start_userAlreadyStarted_doesNothing() {
        sDeviceState.secondaryUser().start();

        sDeviceState.secondaryUser().start();

        assertThat(sDeviceState.secondaryUser().isUnlocked()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireNotVisibleBackgroundUsers(reason = "because otherwise it wouldn't throw")
    public void start_onDisplay_notSupported_throwsException() {
        UserReference user = sDeviceState.additionalUser().stop();

        assertThrows(UnsupportedOperationException.class,
                () -> user.startVisibleOnDisplay(Display.DEFAULT_DISPLAY));

        assertWithMessage("%s is visible", user).that(user.isVisible()).isFalse();
        assertWithMessage("%s is running", user).that(user.isRunning()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireVisibleBackgroundUsers(reason = "because that's what being tested")
    public void start_onDisplay_success() {
        UserReference user = sDeviceState.additionalUser().stop();
        int displayId = getDisplayIdForStartingVisibleBackgroundUser();

        user.startVisibleOnDisplay(displayId);

        try {
            assertWithMessage("%s is visible", user).that(user.isVisible()).isTrue();
            assertWithMessage("%s is running", user).that(user.isRunning()).isTrue();
        } finally {
            // Need to explicitly stop it, otherwise the display won't be available on failure
            user.stop();
        }
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireVisibleBackgroundUsers(reason = "because that's what being tested")
    public void start_onDisplay_userAlreadyStarted_doesNothing() {
        UserReference user = sDeviceState.additionalUser().stop();
        int displayId = getDisplayIdForStartingVisibleBackgroundUser();

        user.startVisibleOnDisplay(displayId);

        try {
            user.startVisibleOnDisplay(displayId);
            assertWithMessage("%s is visible", user).that(user.isVisible()).isTrue();
            assertWithMessage("%s is running", user).that(user.isRunning()).isTrue();
        } finally {
            // Need to explicitly stop it, otherwise the display won't be available on failure
            user.stop();
        }
    }

    @Test
    public void stop_userDoesNotExist_doesNothing() {
        TestApis.users().find(NON_EXISTING_USER_ID).stop();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void stop_userStarted_userIsStopped() {
        sDeviceState.secondaryUser().stop();

        assertThat(sDeviceState.secondaryUser().isRunning()).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void stop_userNotStarted_doesNothing() {
        sDeviceState.secondaryUser().stop();

        sDeviceState.secondaryUser().stop();

        assertThat(sDeviceState.secondaryUser().isRunning()).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @RequireSdkVersion(min = Q)
    public void switchTo_userIsSwitched() {
        sDeviceState.secondaryUser().switchTo();

        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    @RequireRunOnWorkProfile(switchedToParentUser = FALSE)
    public void switchTo_profile_switchesToParent() {
        sDeviceState.workProfile().switchTo();

        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void stop_isWorkProfileOfCurrentUser_stops() {
        sDeviceState.workProfile().stop();

        assertThat(sDeviceState.workProfile().isRunning()).isFalse();
    }

    @Test
    public void serialNo_returnsSerialNo() {
        UserReference user = TestApis.users().instrumented();

        assertThat(user.serialNo())
                .isEqualTo(sUserManager.getSerialNumberForUser(Process.myUserHandle()));
    }

    @Test
    public void serialNo_userDoesNotExist_throwsException() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThrows(NeneException.class, user::serialNo);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    @RequireSdkVersion(min = S, reason = "getUserName is only available on S+")
    public void name_returnsName() {
        UserReference user = TestApis.users().instrumented();

        assertThat(user.name()).isEqualTo(sUserManager.getUserName());
    }

    @Test
    public void name_userDoesNotExist_throwsException() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThrows(NeneException.class, user::name);
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    @RequireSdkVersion(min = S, reason = "getUserType is only available on S+")
    public void type_returnsType() {
        UserReference user = TestApis.users().instrumented();

        assertThat(user.type().name()).isEqualTo(sUserManager.getUserType());
    }

    @Test
    public void type_userDoesNotExist_throwsException() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThrows(NeneException.class, user::type);
    }

    @Test
    @RequireRunOnPrimaryUser
    public void isPrimary_isPrimary_returnsTrue() {
        UserReference user = TestApis.users().instrumented();

        assertThat(user.isPrimary()).isTrue();
    }

    @Test
    @EnsureHasSecondaryUser
    public void isPrimary_isNotPrimary_returnsFalse() {
        UserReference user = sDeviceState.secondaryUser();

        assertThat(user.isPrimary()).isFalse();
    }

    @Test
    public void isPrimary_userDoesNotExist_throwsException() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThrows(NeneException.class, user::isPrimary);
    }

    @Test
    @EnsureHasAdditionalUser
    public void isRunning_userNotStarted_returnsFalse() {
        sDeviceState.additionalUser().stop();

        assertThat(sDeviceState.additionalUser().isRunning()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isRunning_userIsRunning_returnsTrue() {
        sDeviceState.additionalUser().start();

        assertThat(sDeviceState.additionalUser().isRunning()).isTrue();
    }

    @Test
    public void isRunning_userDoesNotExist_returnsFalse() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThat(user.isRunning()).isFalse();
    }

    @Test
    public void isVisible_currentUser_returnsTrue() {
        UserReference user = TestApis.users().current();

        assertWithMessage("%s is visible", user).that(user.isVisible()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireVisibleBackgroundUsers(reason = "because that's what being tested")
    public void isVisible_visibleBgUser_returnsTrue() {
        int displayId = getDisplayIdForStartingVisibleBackgroundUser();

        UserReference user = sDeviceState.additionalUser().startVisibleOnDisplay(displayId);

        try {
            assertWithMessage("%s is visible", user).that(user.isVisible()).isTrue();
        } finally {
            // Need to explicitly stop it, otherwise the display won't be available on failure
            user.stop();
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void isVisible_nonStartedUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().stop();

        assertWithMessage("%s is visible", user).that(user.isVisible()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isVisible_bgUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().start();

        assertWithMessage("%s is visible", user).that(user.isVisible()).isFalse();
    }

    @Test
    public void isVisible_userDoesNotExist_returnsFalse() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertWithMessage("%s is visible", user).that(user.isVisible()).isFalse();
    }

    @Test
    public void isVisibleBagroundNonProfileUser_currentUser_returnsTrue() {
        UserReference user = TestApis.users().current();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireVisibleBackgroundUsers(reason = "because that's what being tested")
    public void isVisibleBagroundNonProfileUser_visibleBgUser_returnsTrue() {
        int displayId = getDisplayIdForStartingVisibleBackgroundUser();

        UserReference user = sDeviceState.additionalUser().startVisibleOnDisplay(displayId);

        try {
            assertWithMessage("%s is visible bg user", user)
                    .that(user.isVisibleBagroundNonProfileUser()).isTrue();
        } finally {
            // Need to explicitly stop it, otherwise the display won't be available on failure
            user.stop();
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void isVisibleBagroundNonProfileUser_nonStartedUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().stop();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isVisibleBagroundNonProfileUser_bgUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().start();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    @Test
    public void isVisibleBagroundNonProfileUser_userDoesNotExist_returnsFalse() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @Test
    @EnsureHasWorkProfile
    public void isVisibleBagroundNonProfileUser_profileUser_returnsFalse() {
        UserReference user = sDeviceState.workProfile().start();

        assertWithMessage("%s is visible bg user", user)
                .that(user.isVisibleBagroundNonProfileUser()).isFalse();
    }

    @Test
    public void isForeground_currentUser_returnsTrue() {
        UserReference user = TestApis.users().current();

        assertWithMessage("%s is foreground", user).that(user.isForeground()).isTrue();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isForeground_nonStartedUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().stop();

        assertWithMessage("%s is foreground", user).that(user.isForeground()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isForeground_bgUser_returnsFalse() {
        UserReference user = sDeviceState.additionalUser().start();

        assertWithMessage("%s is foreground", user).that(user.isForeground()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    @RequireVisibleBackgroundUsers(reason = "because that's what being tested")
    public void isForeground_visibleBgUser_returnsFalse() {
        int displayId = getDisplayIdForStartingVisibleBackgroundUser();

        UserReference user = sDeviceState.additionalUser().startVisibleOnDisplay(displayId);

        try {
            assertWithMessage("%s is foreground", user).that(user.isForeground()).isFalse();
        } finally {
            // Need to explicitly stop it, otherwise the display won't be available on failure
            user.stop();
        }
    }

    @Test
    public void isForeground_userDoesNotExist_returnsFalse() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertWithMessage("%s is foreground", user).that(user.isForeground()).isFalse();
    }

    @Test
    @EnsureHasAdditionalUser
    public void isUnlocked_userIsUnlocked_returnsTrue() {
        sDeviceState.additionalUser().start();

        assertThat(sDeviceState.additionalUser().isUnlocked()).isTrue();
    }

    // TODO(b/203542772): add tests for locked state

    @Test
    public void isUnlocked_userDoesNotExist_returnsFalse() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThat(user.isUnlocked()).isFalse();
    }

    @Test
    @EnsureHasWorkProfile
    public void parent_returnsParent() {
        assertThat(sDeviceState.workProfile().parent()).isNotNull();
    }

    @Test
    @RequireRunOnInitialUser
    public void parent_noParent_returnsNull() {
        UserReference user = TestApis.users().instrumented();

        assertThat(user.parent()).isNull();
    }

    @Test
    public void parent_userDoesNotExist_throwsException() {
        UserReference user = TestApis.users().find(NON_EXISTING_USER_ID);

        assertThrows(NeneException.class, user::parent);
    }

    @Test
    @EnsureHasAdditionalUser
    public void autoclose_removesUser() {
        UserReference additionalUser = sDeviceState.additionalUser();

        try (UserReference user = additionalUser) {
            // We intentionally don't do anything here, just rely on the auto-close behaviour
        }

        assertThat(TestApis.users().all()).doesNotContain(additionalUser);
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPassword_hasLockCredential() {
        try {
            TestApis.users().instrumented().setPassword(PASSWORD);

            assertThat(TestApis.users().instrumented().hasLockCredential()).isTrue();
        } finally {
            TestApis.users().instrumented().clearPassword(PASSWORD);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPin_hasLockCredential() {
        try {
            TestApis.users().instrumented().setPin(PIN);

            assertThat(TestApis.users().instrumented().hasLockCredential()).isTrue();
        } finally {
            TestApis.users().instrumented().clearPin(PIN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPattern_hasLockCredential() {
        try {
            TestApis.users().instrumented().setPattern(PATTERN);

            assertThat(TestApis.users().instrumented().hasLockCredential()).isTrue();
        } finally {
            TestApis.users().instrumented().clearPattern(PATTERN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPassword_hasLockCredential_returnsFalse() {
        TestApis.users().instrumented().setPassword(PASSWORD);
        TestApis.users().instrumented().clearPassword(PASSWORD);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPin_hasLockCredential_returnsFalse() {
        TestApis.users().instrumented().setPin(PIN);
        TestApis.users().instrumented().clearPin(PIN);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPattern_hasLockCredential_returnsFalse() {
        TestApis.users().instrumented().setPattern(PATTERN);
        TestApis.users().instrumented().clearPattern(PATTERN);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    public void clearPassword_doesNotHavePassword_doesNothing() {
        TestApis.users().instrumented().clearPassword(PASSWORD);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    public void clearPin_doesNotHavePin_doesNothing() {
        TestApis.users().instrumented().clearPin(PIN);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    public void clearPattern_doesNotHavePattern_doesNothing() {
        TestApis.users().instrumented().clearPattern(PATTERN);

        assertThat(TestApis.users().instrumented().hasLockCredential()).isFalse();
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPassword_incorrectOldPassword_throwsException() {
        try {
            TestApis.users().instrumented().setPassword(PASSWORD);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPassword(DIFFERENT_PASSWORD));
        } finally {
            TestApis.users().instrumented().clearPassword(PASSWORD);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPin_incorrectOldPin_throwsException() {
        try {
            TestApis.users().instrumented().setPin(PIN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPin(DIFFERENT_PIN));
        } finally {
            TestApis.users().instrumented().clearPin(PIN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void clearPattern_incorrectOldPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPattern(PATTERN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPattern(DIFFERENT_PATTERN));
        } finally {
            TestApis.users().instrumented().clearPattern(PATTERN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    @Ignore // This is no longer correct - as long as nene knows the existing password it will
    // replace - we need to change the password outside of nene to simulate the actual case
    public void setPassword_alreadyHasPassword_throwsException() {
        try {
            TestApis.users().instrumented().setPassword(PASSWORD);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().setPassword(DIFFERENT_PASSWORD));
        } finally {
            TestApis.users().instrumented().clearPassword(PASSWORD);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    @Ignore // This is no longer correct - as long as nene knows the existing password it will
    // replace - we need to change the password outside of nene to simulate the actual case
    public void setPin_alreadyHasPin_throwsException() {
        try {
            TestApis.users().instrumented().setPin(PIN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().setPin(DIFFERENT_PIN));
        } finally {
            TestApis.users().instrumented().clearPin(PIN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    @Ignore // This is no longer correct - as long as nene knows the existing password it will
    // replace - we need to change the password outside of nene to simulate the actual case
    public void setPattern_alreadyHasPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPattern(PATTERN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().setPattern(DIFFERENT_PATTERN));
        } finally {
            TestApis.users().instrumented().clearPattern(PATTERN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPassword_clearAsPinAndPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPassword(PASSWORD);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPin(PASSWORD));

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPattern(PASSWORD));
        } finally {
            TestApis.users().instrumented().clearPassword(PASSWORD);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPin_clearAsPasswordAndPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPin(PIN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPassword(PIN));

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPattern(PIN));
        } finally {
            TestApis.users().instrumented().clearPin(PIN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPattern_clearAsPasswordAndPin_throwsException() {
        try {
            TestApis.users().instrumented().setPattern(PATTERN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPassword(PATTERN));

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().clearPin(PATTERN));
        } finally {
            TestApis.users().instrumented().clearPattern(PATTERN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPassword_getAsPinAndPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPassword(PASSWORD);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().pin());

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().pattern());
        } finally {
            TestApis.users().instrumented().clearPassword(PASSWORD);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPin_getAsPasswordAndPattern_throwsException() {
        try {
            TestApis.users().instrumented().setPin(PIN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().password());

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().pattern());
        } finally {
            TestApis.users().instrumented().clearPin(PIN);
        }
    }

    @Test
    @EnsurePasswordNotSet
    @RequireNotHeadlessSystemUserMode(reason = "b/248248444")
    public void setPattern_getAsPasswordAndPin_throwsException() {
        try {
            TestApis.users().instrumented().setPattern(PATTERN);

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().password());

            assertThrows(NeneException.class,
                    () -> TestApis.users().instrumented().pin());
        } finally {
            TestApis.users().instrumented().clearPattern(PATTERN);
        }
    }

    // TODO(b/271153404): create new TestApis / users() API for this
    private int getDisplayIdForStartingVisibleBackgroundUser() {
        int[] displayIds;
        try (PermissionContext p =
                TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
            displayIds = sContext.getSystemService(ActivityManager.class)
                .getDisplayIdsForStartingVisibleBackgroundUsers();
        }
        assertWithMessage("available displays").that(displayIds).isNotNull();
        assertWithMessage("# of available displays").that(displayIds.length).isAtLeast(1);
        return displayIds[0];
    }

    @Test
    public void remove_instrumentedUser_throwsException() {
        assertThrows(NeneException.class, ()->TestApis.users().instrumented().remove());
    }
}
