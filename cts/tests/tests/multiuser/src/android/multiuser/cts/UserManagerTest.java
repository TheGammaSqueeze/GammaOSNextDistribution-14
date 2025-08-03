/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.multiuser.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MODIFY_QUIET_MODE;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.TestingUtils.getBooleanProperty;
import static android.multiuser.cts.TestingUtils.getContextForOtherUser;
import static android.multiuser.cts.TestingUtils.getContextForUser;
import static android.multiuser.cts.TestingUtils.sContext;
import static android.os.UserManager.USER_OPERATION_SUCCESS;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.nene.types.OptionalBoolean.FALSE;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.annotations.SystemUserOnly;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasPrivateProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrivateProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class UserManagerTest {

    private static final String TAG = UserManagerTest.class.getSimpleName();

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public TestRule chain = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(sDeviceState);

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UserManager mUserManager;

    private final String mAccountName = "test_account_name";
    private final String mAccountType = "test_account_type";

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();
    }

    @EnsureHasPermission(CREATE_USERS)
    private void removeUser(UserHandle userHandle) {
        if (userHandle == null) {
            return;
        }

        assertThat(mUserManager.removeUser(userHandle)).isTrue();
    }

    /**
     * Verify that the isUserAGoat() method always returns false for API level 30. This is
     * because apps targeting R no longer have access to package queries by default.
     */
    @Test
    public void testUserGoat_api30() {
        assertWithMessage("isUserAGoat()").that(mUserManager.isUserAGoat()).isFalse();
    }

    /**
     * Verify that isAdminUser() can be called without any permissions and returns true for the
     * initial user which is an admin user.
     */
    @Test
    @ApiTest(apis = {"android.os.UserManager#isAdminUser"})
    @RequireRunOnInitialUser
    public void testIsAdminUserOnInitialUser_noPermission() {
        assertTrue(mUserManager.isAdminUser());
    }

    /**
     * Verify that isAdminUser() throws SecurityException when called for a different user context
     * without any permission.
     */
    @Test
    @ApiTest(apis = {"android.os.UserManager#isAdminUser"})
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    public void testIsAdminUserForOtherUserContextFailsWithoutPermission() {
        UserReference additionalUser = sDeviceState.additionalUser();
        additionalUser.switchTo();
        Context userContext;
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            userContext = getContextForUser(additionalUser.id());
        }

        UserManager um = userContext.getSystemService(UserManager.class);
        assertThrows(SecurityException.class, () -> um.isAdminUser());
    }

    /**
     * Verify that isAdminUser() works fine when called for a different user context
     * with required permission.
     */
    @Test
    @ApiTest(apis = {"android.os.UserManager#isAdminUser"})
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    @EnsureHasPermission(CREATE_USERS)
    public void testIsAdminUserForOtherUserContextWithPermission() {
        UserReference additionalUser = sDeviceState.additionalUser();
        Context userContext;
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            userContext = getContextForUser(additionalUser.id());
        }

        UserManager um = userContext.getSystemService(UserManager.class);
        assertFalse(um.isAdminUser());
    }

    @Test
    public void testIsRemoveResultSuccessful() {
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_REMOVED))
                .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_DEFERRED))
                .isTrue();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED))
                        .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_UNKNOWN))
                .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_RESTRICTION))
                        .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_NOT_FOUND))
                        .isFalse();
        assertThat(
                UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_SYSTEM_USER))
                        .isFalse();
    }

    @Test
    public void testIsHeadlessSystemUserMode() throws Exception {
        boolean expected = getBooleanProperty(mInstrumentation,
                "ro.fw.mu.headless_system_user");
        assertWithMessage("isHeadlessSystemUserMode()")
                .that(UserManager.isHeadlessSystemUserMode()).isEqualTo(expected);
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnInitialUser
    public void testIsUserForeground_differentContext_noPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserForeground());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserForeground_differentContext_withPermission() throws Exception {
        Context userContext = getContextForOtherUser();
        UserManager um = userContext.getSystemService(UserManager.class);

        assertWithMessage("isUserForeground() for unknown user").that(um.isUserForeground())
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnInitialUser
    public void testIsUserForeground_currentUser() throws Exception {
        assertWithMessage("isUserForeground() for current user")
                .that(mUserManager.isUserForeground()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserForeground_backgroundUser() throws Exception {
        assertWithMessage("isUserForeground() for bg user (%s)", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnWorkProfile // TODO(b/239961027): should be @RequireRunOnProfile instead
    public void testIsUserForeground_profileOfCurrentUser() throws Exception {
        assertWithMessage("isUserForeground() for profile(%s) of current user", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    /**
     * Verify that isForegroundUserAdmin() returns true when called on the foreground initial
     * user, which is an admin user, regardless of whether there are other users.
     */
    @Test
    @ApiTest(apis = {"android.os.UserManager#isForegroundUserAdmin"})
    @RequiresFlagsEnabled(android.multiuser.Flags.FLAG_SUPPORT_COMMUNAL_PROFILE_NEXTGEN)
    @RequireRunOnInitialUser
    public void testIsForegroundUserAdminUser_admin() {
        assertTrue(mUserManager.isForegroundUserAdmin());
    }

    /**
     * Verify that isForegroundUserAdmin() returns the correct value when called on a
     * (foreground) secondary user, which generally will not be an admin, regardless of the Context.
     */
    @Test
    @ApiTest(apis = {"android.os.UserManager#isForegroundUserAdmin"})
    @RequiresFlagsEnabled(android.multiuser.Flags.FLAG_SUPPORT_COMMUNAL_PROFILE_NEXTGEN)
    @RequireRunOnInitialUser
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    public void testIsForegroundUserAdminUser_withAdditionalUser() {
        UserReference additionalUserRef = sDeviceState.additionalUser();
        final UserManager initialUm = mUserManager;
        Context additionalUserContext;
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            additionalUserContext = getContextForUser(additionalUserRef.id());
        }
        final UserManager additionalUm = additionalUserContext.getSystemService(UserManager.class);

        // The initial user is an admin, so we should get true (regardless of who is asking).
        assertTrue(initialUm.isForegroundUserAdmin());
        assertTrue(additionalUm.isForegroundUserAdmin());

        additionalUserRef.switchTo();

        // The additional user is most likely a non-admin, but just in case, let's be more general.
        final boolean isAdditionalUserAdmin = additionalUserRef.isAdmin();
        if (isAdditionalUserAdmin) {
            Log.i(TAG, "WARNING: the additional user " + additionalUserRef.id() + " is admin");
        }
        assertThat(initialUm.isForegroundUserAdmin()).isEqualTo(isAdditionalUserAdmin);
        assertThat(additionalUm.isForegroundUserAdmin()).isEqualTo(isAdditionalUserAdmin);
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserRunning"})
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserRunning()
    public void testIsUserRunning_stoppedProfileOfCurrentUser() {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserRunning() for stopped profile (id=%s) of current user",
                profile.id()).that(um.isUserRunning(profile.userHandle()))
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserRunning"})
    @EnsureHasAdditionalUser(switchedToUser = FALSE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserRunning()
    public void testIsUserRunning_stoppedSecondaryUser() {
        Log.d(TAG, "Stopping  user " + sDeviceState.additionalUser()
                + " (called from " + sContext.getUser() + ")");
        sDeviceState.additionalUser().stop();

        UserManager um =
                TestApis.context().instrumentedContext().getSystemService(UserManager.class);

        assertWithMessage("isUserRunning() for stopped secondary user (id=%s)",
                sDeviceState.additionalUser().id())
                .that(um.isUserRunning(sDeviceState.additionalUser().userHandle())).isFalse();
    }

    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile",
            "android.os.UserManager#getUserBadge"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testCloneProfile() throws Exception {
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle userHandle = null;

        // Need CREATE_USERS permission to create user in test
        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Clone profile", USER_TYPE_PROFILE_CLONE, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create clone profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final Context userContext = sContext.createPackageContextAsUser("system", 0,
                    userHandle);
            final UserManager cloneUserManager = userContext.getSystemService(UserManager.class);
            assertThat(cloneUserManager.isMediaSharedWithParent()).isTrue();
            assertThat(cloneUserManager.isCredentialSharableWithParent()).isTrue();
            assertThat(cloneUserManager.isCloneProfile()).isTrue();
            assertThat(cloneUserManager.isProfile()).isTrue();
            assertThat(cloneUserManager.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isTrue();
            assertThat(cloneUserManager.getUserBadge()).isNotNull();

            final List<UserInfo> list = mUserManager.getAliveUsers();
            final UserHandle finalUserHandle = userHandle;
            final List<UserInfo> cloneUsers = list.stream().filter(
                    user -> (user.id == finalUserHandle.getIdentifier()
                            && user.isCloneProfile()))
                    .collect(Collectors.toList());
            assertThat(cloneUsers.size()).isEqualTo(1);
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isCommunalProfile"})
    @RequiresFlagsEnabled(android.multiuser.Flags.FLAG_SUPPORT_COMMUNAL_PROFILE)
    @RequireMultiUserSupport
    @EnsureHasPermission({QUERY_USERS, CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testCommunalProfile() throws Exception {
        final UserHandle communalUser = mUserManager.getCommunalProfile();
        // Not all devices will have a communal profile on it. If they don't, bypass the test.
        assumeNotNull(communalUser);

        final UserManager umOfCommunal = sContext
                .createPackageContextAsUser("android", 0, communalUser)
                .getSystemService(UserManager.class);

        assertThat(umOfCommunal.isCommunalProfile()).isTrue();
        assertThat(umOfCommunal.isProfile()).isTrue();
        if (umOfCommunal.isUserRunning(communalUser)) {
            // Communal Profile should always be visible (if it exists and is running).
            assertThat(umOfCommunal.isUserVisible()).isTrue();
        }

        // Make sure that there can be only one.
        final List<UserInfo> communalUsers = mUserManager.getAliveUsers().stream()
                .filter(UserInfo::isCommunalProfile).toList();
        assertThat(communalUsers.size()).isEqualTo(1);
        assertThat(communalUsers.get(0).id).isEqualTo(communalUser.getIdentifier());
    }


    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasPermission(CREATE_USERS)
    public void testAddCloneProfile_shouldSendProfileAddedBroadcast() {
        assumeTrue(mUserManager.supportsMultipleUsers());
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_ADDED, /* checker= */null);
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Clone profile",
                        USER_TYPE_PROFILE_CLONE, new HashSet<>());
                assumeNotNull(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create clone profile", e);
            } finally {
                removeUser(userHandle);
            }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasNoWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateManagedProfile_shouldSendProfileAddedBroadcast() {
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_ADDED, /* checker= */null);
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Managed profile",
                        USER_TYPE_PROFILE_MANAGED, new HashSet<>());
                assumeNotNull(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create managed profile", e);
            } finally {
                removeUser(userHandle);
            }
    }

    @Test
    @EnsureHasNoWorkProfile
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveCloneProfile_shouldSendProfileRemovedBroadcast() {
        assumeTrue(mUserManager.supportsMultipleUsers());
        BlockingBroadcastReceiver broadcastReceiver = null;
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Clone profile",
                        USER_TYPE_PROFILE_CLONE, new HashSet<>());
                broadcastReceiver = sDeviceState
                        .registerBroadcastReceiver(
                                Intent.ACTION_PROFILE_REMOVED, userIsEqual(userHandle)
                );
                assumeNotNull(userHandle);
                removeUser(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create clone profile", e);
            }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#createProfile"})
    @AppModeFull
    @EnsureHasNoWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveManagedProfile_shouldSendProfileRemovedBroadcast() {
        BlockingBroadcastReceiver broadcastReceiver = null;
        UserHandle userHandle = null;
            try {
                userHandle = mUserManager.createProfile("Managed profile",
                        USER_TYPE_PROFILE_MANAGED, new HashSet<>());
                broadcastReceiver = sDeviceState
                        .registerBroadcastReceiver(
                                Intent.ACTION_PROFILE_REMOVED, userIsEqual(userHandle)
                        );
                assumeNotNull(userHandle);
                removeUser(userHandle);
                broadcastReceiver.awaitForBroadcastOrFail();
            } catch (UserManager.UserOperationException e) {
                assumeNoException("Couldn't create managed profile", e);
            }
    }

    @Test
    @AppModeFull
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasNoWorkProfile
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS, INTERACT_ACROSS_USERS})
    @ApiTest(apis = {
            "android.os.UserManager#createProfile",
            "android.os.UserManager#isManagedProfile",
            "android.os.UserManager#isProfile",
            "android.os.UserManager#isUserOfType",
            "android.os.UserManager#getUserBadge"})
    public void testManagedProfile() throws Exception {
        UserHandle userHandle = null;

        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Managed profile", UserManager.USER_TYPE_PROFILE_MANAGED, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create managed profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final UserManager umOfProfile = sContext
                    .createPackageContextAsUser("android", 0, userHandle)
                    .getSystemService(UserManager.class);

            assertThat(umOfProfile.isManagedProfile()).isTrue();
            assertThat(umOfProfile.isManagedProfile(userHandle.getIdentifier())).isTrue();
            assertThat(umOfProfile.isProfile()).isTrue();
            assertThat(umOfProfile.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isTrue();
            assertThat(umOfProfile.getUserBadge()).isNotNull();
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS, INTERACT_ACROSS_USERS})
    @RequireRunOnInitialUser
    @ApiTest(apis = {
            "android.os.UserManager#createProfile",
            "android.os.UserManager#isPrivateProfile",
            "android.os.UserManager#isProfile",
            "android.os.UserManager#isUserOfType",
            "android.os.UserManager#getUserBadge"})
    public void testPrivateProfile() throws Exception {
        UserHandle userHandle = null;
        assumeTrue(android.os.Flags.allowPrivateProfile());

        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Private profile", UserManager.USER_TYPE_PROFILE_PRIVATE, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create private profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final UserManager umOfProfile = sContext
                    .createPackageContextAsUser("android", 0, userHandle)
                    .getSystemService(UserManager.class);

            assertThat(umOfProfile.isPrivateProfile()).isTrue();
            assertThat(umOfProfile.isProfile()).isTrue();
            assertThat(umOfProfile.isUserOfType(UserManager.USER_TYPE_PROFILE_PRIVATE)).isTrue();
            final List<UserInfo> list = mUserManager.getAliveUsers();
            final UserHandle finalUserHandle = userHandle;
            final List<UserInfo> privateUsers = list.stream().filter(
                    user -> (user.id == finalUserHandle.getIdentifier()
                            && user.isPrivateProfile()))
                    .collect(Collectors.toList());
            assertThat(privateUsers.size()).isEqualTo(1);
            assertThat(umOfProfile.getUserBadge()).isNotNull();
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Secondary user profile is only available on headless")
    @ApiTest(apis = {"android.os.UserManager#removeUser"})
    @EnsureHasAdditionalUser
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(forUser = ADDITIONAL_USER)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveParentUser_withProfiles() {
        UserReference workProfile = sDeviceState.workProfile(/* forUser= */ ADDITIONAL_USER);
        UserReference parentUser = workProfile.parent();
        parentUser.remove();

        // Removing parent user will also remove its profile
        assertThat(parentUser.exists()).isFalse();
        assertThat(workProfile.exists()).isFalse();
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Secondary user profile is only available on headless")
    @ApiTest(apis = {"android.os.UserManager#removeUser"})
    @EnsureHasAdditionalUser
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(forUser = ADDITIONAL_USER)
    @EnsureHasPermission(CREATE_USERS)
    public void testRemoveUserOnlyProfile_ShouldNotRemoveAnyOtherUserInSameProfileGroupId() {
        UserHandle parentUser = null;

        try {
            UserReference workProfile = sDeviceState.workProfile(/* forUser= */ ADDITIONAL_USER);
            parentUser = workProfile.parent().userHandle();
            UserHandle workProfileUser = workProfile.userHandle();

            try (BlockingBroadcastReceiver receiver = BlockingBroadcastReceiver.create(sContext,
                    Intent.ACTION_USER_REMOVED, userIsEqual(workProfileUser)).register()) {
                removeUser(workProfileUser);
            }

            //Removing a profile will only remove the profile and not the parent user
            assertThat(hasUser(workProfileUser.getIdentifier())).isFalse();
            assertThat(hasUser(parentUser.getIdentifier())).isTrue();
        } finally {
            removeUser(parentUser);
        }
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "only headless can have main user as permanent admin.")
    @ApiTest(apis = {"android.os.UserManager#removeUserWhenPossible"})
    @EnsureHasAdditionalUser
    @EnsureHasPermission({CREATE_USERS})
    public void testRemoveMainUser_shouldNotRemoveMainUser() {
        assumeTrue("Main user is not permanent admin.", isMainUserPermanentAdmin());
        UserReference initialUser = sDeviceState.initialUser();
        UserReference additionalUser = sDeviceState.additionalUser();
        if (TestApis.users().current() != additionalUser) {
            additionalUser.switchTo();
        }

        assertThat(mUserManager.removeUserWhenPossible(initialUser.userHandle(), false))
                .isEqualTo(UserManager.REMOVE_RESULT_ERROR_MAIN_USER_PERMANENT_ADMIN);

        // Initial/main user should not be removed.
        assertThat(hasUser(initialUser.id())).isTrue();
    }

    @Test
    @EnsureHasPermission({QUERY_USERS})
    @ApiTest(apis = {
            "android.os.UserManager#isSystemUser",
            "android.os.UserManager#isUserOfType",
            "android.os.UserManager#isProfile",
            "android.os.UserManager#isManagedProfile",
            "android.os.UserManager#isCloneProfile"})
    public void testSystemUser() throws Exception {
        final UserManager umOfSys = sContext
                .createPackageContextAsUser("android", 0, UserHandle.SYSTEM)
                .getSystemService(UserManager.class);

        assertThat(umOfSys.isSystemUser()).isTrue();
        // We cannot demand what type of user SYSTEM is, but we can say some things it isn't.
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_FULL_GUEST)).isFalse();

        assertThat(umOfSys.isProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile(UserHandle.USER_SYSTEM)).isFalse();
        assertThat(umOfSys.isCloneProfile()).isFalse();
        if (android.multiuser.Flags.supportCommunalProfile()) {
            assertThat(umOfSys.isCommunalProfile()).isFalse();
        }
        if (android.os.Flags.allowPrivateProfile()) {
            assertThat(umOfSys.isPrivateProfile()).isFalse();
        }
    }

    @Test
    @EnsureHasNoWorkProfile
    @SystemUserOnly(reason = "Restricted users are only supported on system user.")
    @ApiTest(apis = {
            "android.os.UserManager#isRestrictedProfile",
            "android.os.UserManager#getRestrictedProfileParent",
            "android.os.UserManager#createRestrictedProfile"})
    @EnsureHasPermission(CREATE_USERS)
    public void testRestrictedUser() throws Exception {
        UserHandle user = null;
        try {
            // Check that the SYSTEM user is not restricted.
            assertThat(mUserManager.isRestrictedProfile()).isFalse();
            assertThat(mUserManager.isRestrictedProfile(UserHandle.SYSTEM)).isFalse();
            assertThat(mUserManager.getRestrictedProfileParent()).isNull();

            final UserInfo info = mUserManager.createRestrictedProfile("Restricted user");

            // If the device supports Restricted users, it must report it correctly.
            assumeTrue("Couldn't create a restricted profile", info != null);

            user = UserHandle.of(info.id);
            assertThat(mUserManager.isRestrictedProfile(user)).isTrue();

            final Context userContext = sContext.createPackageContextAsUser("system", 0, user);
            final UserManager userUm = userContext.getSystemService(UserManager.class);

            assertThat(userUm.isRestrictedProfile()).isTrue();
            assertThat(userUm.getRestrictedProfileParent().isSystem()).isTrue();
        } finally {
            removeUser(user);
        }
    }

    private NewUserRequest newUserRequest() {
        final PersistableBundle accountOptions = new PersistableBundle();
        accountOptions.putString("test_account_option_key", "test_account_option_value");

        return new NewUserRequest.Builder()
                .setName("test_user")
                .setUserType(USER_TYPE_FULL_SECONDARY)
                .setUserIcon(Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565))
                .setAccountName(mAccountName)
                .setAccountType(mAccountType)
                .setAccountOptions(accountOptions)
                .build();
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testSomeUserHasAccount() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try {
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
            user = mUserManager.createUser(newUserRequest()).getUser();
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isTrue();
        } finally {
            removeUser(user);
        }
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testSomeUserHasAccount_shouldIgnoreToBeRemovedUsers() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
            final NewUserResponse response = mUserManager.createUser(newUserRequest());
            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            mUserManager.removeUser(response.getUser());
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
    }

    @Test
    @ApiTest(apis = {
            "android.os.UserManager#supportsMultipleUsers",
            "android.os.UserManager#createUser",
            "android.os.UserManager#getUserName",
            "android.os.UserManager#isUserNameSet",
            "android.os.UserManager#getUserType",
            "android.os.UserManager#isUserOfType"})
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateUser_withNewUserRequest_shouldCreateUserWithCorrectProperties()
            throws PackageManager.NameNotFoundException {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try {
            final NewUserRequest request = newUserRequest();
            final NewUserResponse response = mUserManager.createUser(request);
            user = response.getUser();

            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response.isSuccessful()).isTrue();
            assertThat(user).isNotNull();

            UserManager userManagerOfNewUser = sContext
                    .createPackageContextAsUser("android", 0, user)
                    .getSystemService(UserManager.class);

            assertThat(userManagerOfNewUser.getUserName()).isEqualTo(request.getName());
            assertThat(userManagerOfNewUser.isUserNameSet()).isTrue();
            assertThat(userManagerOfNewUser.getUserType()).isEqualTo(request.getUserType());
            assertThat(userManagerOfNewUser.isUserOfType(request.getUserType())).isEqualTo(true);
            // We can not test userIcon and accountOptions,
            // because getters require MANAGE_USERS permission.
            // And we are already testing accountName and accountType
            // are set correctly in testSomeUserHasAccount method.
        } finally {
            removeUser(user);
        }
    }

    @Test
    @EnsureHasPermission(CREATE_USERS)
    public void testCreateUser_withNewUserRequest_shouldNotAllowDuplicateUserAccounts() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user1 = null;
        UserHandle user2 = null;

        try {
            final NewUserResponse response1 = mUserManager.createUser(newUserRequest());
            user1 = response1.getUser();

            assertThat(response1.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response1.isSuccessful()).isTrue();
            assertThat(user1).isNotNull();

            final NewUserResponse response2 = mUserManager.createUser(newUserRequest());
            user2 = response2.getUser();

            assertThat(response2.getOperationResult()).isEqualTo(
                    UserManager.USER_OPERATION_ERROR_USER_ACCOUNT_ALREADY_EXISTS);
            assertThat(response2.isSuccessful()).isFalse();
            assertThat(user2).isNull();
        } finally {
            removeUser(user1);
            removeUser(user2);
        }
    }

    @Test
    @AppModeFull
    @EnsureHasWorkProfile // TODO(b/239961027): should also check for other profiles
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsParent() {
        final UserReference parent = TestApis.users().instrumented();
        for (UserHandle profile : mUserManager.getUserProfiles()) {
            if (!profile.equals(parent.userHandle())) {
                assertThat(mUserManager.getProfileParent(profile)).isEqualTo(parent.userHandle());
            }
        }
    }

    @Test
    @AppModeFull
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsNullForNonProfile() {
        assertThat(mUserManager.getProfileParent(TestApis.users().system().userHandle())).isNull();
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableUserCount() {
        final int maxAllowedIterations = 15;
        final String userType = USER_TYPE_FULL_SECONDARY;
        final NewUserRequest request = new NewUserRequest.Builder().build();
        final ArrayDeque<UserHandle> usersCreated = new ArrayDeque<>();

        try {
            final int initialRemainingCount = mUserManager.getRemainingCreatableUserCount(userType);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                usersCreated.push(mUserManager.createUser(request).getUser());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(usersCreated.pop());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
        } finally {
            usersCreated.forEach(this::removeUser);
        }
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableProfileCount() {
        final int maxAllowedIterations = 15;
        final String type = USER_TYPE_PROFILE_MANAGED;
        final ArrayDeque<UserHandle> profilesCreated = new ArrayDeque<>();
        final Set<String> disallowedPackages = new HashSet<>();
        try {
            final int initialRemainingCount =
                    mUserManager.getRemainingCreatableProfileCount(type);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                profilesCreated.push(mUserManager.createProfile(null, type, disallowedPackages));
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(profilesCreated.pop());
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
        } catch (UserManager.UserOperationException e) {
            // Not all devices and user types support these profiles; skip if this one doesn't.
            assumeNoException("Couldn't create managed profile", e);
            return;
        } finally {
            profilesCreated.forEach(this::removeUser);
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_system() {
        final UserHandle fullUser = TestApis.users().instrumented().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(fullUser);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT));
        assertThat(properties.isMediaSharedWithParent()).isFalse();
        assertThat(properties.isCredentialShareableWithParent()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_managedProfile() {
        final UserHandle profile = sDeviceState.workProfile().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(profile);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT,
                UserProperties.SHOW_IN_LAUNCHER_SEPARATE));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isMainUser"})
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    public void testIsMainUser_trueForAtMostOneUser() {
        //Install instrumented test app on the SYSTEM user which is not covered in annotations.
        TestApis.packages().instrumented().installExisting(UserReference.of(UserHandle.SYSTEM));

        final List<UserHandle> userHandles = mUserManager.getUserHandles(false);
        final List<UserHandle> mainUsers = new ArrayList<>();

        for (UserHandle user : userHandles) {
            final Context userContext = getContextForUser(user.getIdentifier());
            final UserManager userManager = userContext.getSystemService(UserManager.class);
            if (userManager.isMainUser()) {
                mainUsers.add(user);
            }
        }
        assertWithMessage("main users (%s)", mainUsers).that(mainUsers.size()).isLessThan(2);
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getMainUser", "android.os.UserManager#isMainUser"})
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    @EnsureHasPermission({QUERY_USERS, INTERACT_ACROSS_USERS})
    public void testGetMainUser_returnsMainUser() {
        final UserHandle mainUser = mUserManager.getMainUser();
        if (mainUser != null) {
            final Context mainUserContext = getContextForUser(mainUser.getIdentifier());
            final UserManager mainUserManager = mainUserContext.getSystemService(UserManager.class);
            assertThat(mainUserManager.isMainUser()).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    @RequireRunOnInitialUser
    @EnsureHasNoAdditionalUser
    @EnsureHasPermission({QUERY_USERS})
    public void testGetPreviousForegroundUser_noAdditionalUser() {
        assertWithMessage("getPreviousUser() with no additional user")
                .that(mUserManager.getPreviousForegroundUser()).isNull();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    @RequireRunOnInitialUser
    @EnsureHasNoAdditionalUser
    @EnsureHasWorkProfile
    @EnsureHasPermission({QUERY_USERS})
    public void testGetPreviousForegroundUser_withWorkProfileButNoAdditionalUser() {
        assertWithMessage("getPreviousForegroundUser() with work profile but no additional user")
                .that(mUserManager.getPreviousForegroundUser()).isNull();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getPreviousForegroundUser"})
    @EnsureHasAdditionalUser
    @EnsureHasPermission({QUERY_USERS})
    public void testGetPreviousForegroundUser_switchingBetweenInitialAndAdditional() {
        UserReference initialUser = sDeviceState.initialUser();
        UserReference additionalUser = sDeviceState.additionalUser();

        if (TestApis.users().current() != initialUser) {
            initialUser.switchTo();
        }

        additionalUser.switchTo();
        assertThat(mUserManager.getPreviousForegroundUser()).isEqualTo(initialUser.userHandle());
        initialUser.switchTo();
        assertThat(mUserManager.getPreviousForegroundUser()).isEqualTo(additionalUser.userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#setBootUser"})
    @EnsureHasAdditionalUser
    @EnsureHasPermission({CREATE_USERS})
    public void setBootUser_providedUserIsSwitchable() {
        UserReference additionalUser = sDeviceState.additionalUser();
        mUserManager.setBootUser(additionalUser.userHandle());

        assertThat(mUserManager.getBootUser()).isEqualTo(additionalUser.userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#setBootUser"})
    @EnsureHasWorkProfile
    @EnsureHasAdditionalUser
    @EnsureHasPermission({CREATE_USERS})
    @RequireNotHeadlessSystemUserMode(reason = "Testing non-HSUM scenario")
    public void setBootUser_providedUserIsNotSwitchable_nonHsum() {
        UserReference additionalUser = sDeviceState.additionalUser();
        UserReference workProfile = sDeviceState.workProfile();
        mUserManager.setBootUser(workProfile.userHandle());

        // Switch to additional user to make sure there is a previous user that is not the
        // current user.
        additionalUser.switchTo();

        // Boot user will be the system user
        assertThat(mUserManager.getBootUser())
                .isEqualTo(sDeviceState.primaryUser().userHandle());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#setBootUser"})
    @EnsureHasWorkProfile
    @EnsureHasAdditionalUser
    @EnsureHasPermission({CREATE_USERS})
    @RequireHeadlessSystemUserMode(reason = "Testing HSUM scenario")
    public void setBootUser_providedUserIsNotSwitchable_Hsum() {
        UserReference additionalUser = sDeviceState.additionalUser();
        UserReference workProfile = sDeviceState.workProfile();
        mUserManager.setBootUser(workProfile.userHandle());

        // Switch to additional user to make sure there is a previous user that is not the
        // current user.
        additionalUser.switchTo();

        // Boot user will be most recent user
        assertThat(mUserManager.getBootUser())
                .isEqualTo(mUserManager.getPreviousForegroundUser());
    }

    @Test
    @EnsureHasPrivateProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    @AppModeFull
    // TODO(b/301574823) : Limit this test to only when private space is supported.
    public void testRequestQuietModeOnPrivateProfile_shouldSendProfileUnavailableBroadcast() {
        assumeTrue(android.os.Flags.allowPrivateProfile());
        final UserHandle profileHandle = sDeviceState.privateProfile().userHandle();
        presetQuietModeStatus(false, profileHandle);
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_UNAVAILABLE, /* checker= */null);
        mUserManager.requestQuietModeEnabled(true, profileHandle);
        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @EnsureHasPrivateProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    @AppModeFull
    // TODO(b/301574823) : Limit this test to only when private space is supported.
    public void testRequestQuietModeOnPrivateProfile_disableQuietMode_needUserCredentials() {
        assumeTrue(android.os.Flags.allowPrivateProfile());
        final UserHandle profileHandle = sDeviceState.privateProfile().userHandle();
        presetQuietModeStatus(true, profileHandle);
        assertThat(mUserManager.requestQuietModeEnabled(false, profileHandle))
                .isFalse();
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    public void testRequestQuietModeOnManaged_shouldSendProfileUnavailableBroadcast() {
        assumeTrue(android.os.Flags.allowPrivateProfile());
        final UserHandle profileHandle = sDeviceState.workProfile().userHandle();
        presetQuietModeStatus(false, profileHandle);
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_UNAVAILABLE, /* checker= */
                        null);
        mUserManager.requestQuietModeEnabled(true, profileHandle);
        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    public void testRequestQuietModeOnManaged_shouldSendProfileAvailableBroadcast() {
        assumeTrue(android.os.Flags.allowPrivateProfile());
        final UserHandle profileHandle = sDeviceState.workProfile().userHandle();
        presetQuietModeStatus(true, profileHandle);
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_PROFILE_AVAILABLE, /* checker= */
                        null);
        mUserManager.requestQuietModeEnabled(false, profileHandle);
        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    public void testRequestQuietModeOnManaged_shouldSendManagedProfileUnavailableBroadcast() {
        final UserHandle profileHandle = sDeviceState.workProfile().userHandle();
        presetQuietModeStatus(false, profileHandle);
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE, /* checker= */
                        null);
        mUserManager.requestQuietModeEnabled(true, profileHandle);
        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission({MODIFY_QUIET_MODE})
    public void testRequestQuietModeOnManaged_shouldSendManagedProfileAvailableBroadcast() {
        final UserHandle profileHandle = sDeviceState.workProfile().userHandle();
        presetQuietModeStatus(true, profileHandle);
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState
                .registerBroadcastReceiver(Intent.ACTION_MANAGED_PROFILE_AVAILABLE, /* checker= */
                        null);
        mUserManager.requestQuietModeEnabled(false, profileHandle);
        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @EnsureHasPrivateProfile
    @RequireRunOnPrivateProfile
    @ApiTest(apis = {"android.os.UserManager#getProfileLabel"})
    @EnsureHasPermission({CREATE_USERS, INTERACT_ACROSS_USERS})
    @RequiresFlagsEnabled(android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE)
    public void testPrivateProfileLabel_shouldNotBeNull() {
        final UserManager umOfProfile = sContext.getSystemService(UserManager.class);
        assert umOfProfile != null;
        assertThat(umOfProfile.getProfileLabel()).isNotNull();
    }

    private void presetQuietModeStatus(boolean enableQuietMode, UserHandle profileHandle) {
        if (mUserManager.isQuietModeEnabled(profileHandle) != enableQuietMode) {
            mUserManager.requestQuietModeEnabled(enableQuietMode, profileHandle);
        }
    }

    private Function<Intent, Boolean> userIsEqual(UserHandle userHandle) {
        try {
            return (intent) -> userHandle.equals(intent.getParcelableExtra(intent.EXTRA_USER));
        } catch (NullPointerException e) {
            assumeNoException("User handle is null", e);
        }
        return (intent) -> false;
    }

    @Nullable
    private UserInfo getUser(int id) {
        try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
            return  mUserManager.getUsers()
                    .stream().filter(user -> user.id == id).findFirst()
                    .orElse(null);
        }
    }

    private boolean hasUser(int id) {
        return getUser(id) != null;
    }

    private boolean isMainUserPermanentAdmin() {
        try {
            return sContext.getResources().getBoolean(
                    Resources.getSystem().getIdentifier("config_isMainUserPermanentAdmin",
                            "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume the main user is not permanent admin.
            return false;
        }
    }
}
