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
 * limitations under the License.
 */

package com.android.car.settings.profiles;

import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAliveUsers;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidAsyncFuture;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.settings.testutils.ShadowActivityManager;
import com.android.car.settings.testutils.ShadowUserIconProvider;
import com.android.car.settings.testutils.ShadowUserManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowProcess;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowUserManager.class, ShadowUserIconProvider.class})
public class ProfileHelperTest {

    private static final String DEFAULT_ADMIN_NAME = "default_admin";
    private static final String DEFAULT_GUEST_NAME = "default_guest";

    private Context mContext;
    private ProfileHelper mProfileHelper;

    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Resources mMockResources;
    @Mock
    private CarUserManager mMockCarUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mProfileHelper = new ProfileHelper(mMockUserManager, mMockResources,
                DEFAULT_ADMIN_NAME, DEFAULT_GUEST_NAME, mMockCarUserManager);

        when(mMockUserManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS))
                .thenReturn(false);
        when(mMockUserManager.isDemoUser()).thenReturn(false);
        when(mMockUserManager.isGuestUser()).thenReturn(false);

        ShadowUserManager.setIsHeadlessSystemUserMode(true);
        ShadowActivityManager.setCurrentUser(10);
    }

    @After
    public void tearDown() {
        ShadowActivityManager.reset();
        ShadowUserManager.reset();
    }

    @Test
    public void canCurrentProcessModifyAccounts_baseline_returnsTrue() {
        assertThat(mProfileHelper.canCurrentProcessModifyAccounts()).isTrue();
    }

    @Test
    public void canCurrentProcessModifyAccounts_hasDisallowModifyAccounts_returnsFalse() {
        when(mMockUserManager.hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS))
                .thenReturn(true);
        assertThat(mProfileHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void canCurrentProcessModifyAccounts_isDemoUser_returnsFalse() {
        when(mMockUserManager.isDemoUser()).thenReturn(true);
        assertThat(mProfileHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void canCurrentProcessModifyAccounts_isGuestUser_returnsFalse() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        assertThat(mProfileHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testGetAllsers() {
        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two admin, and two non-admin users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createNonAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        UserInfo user3 = createNonAdminUser(fgUserId + 2);
        UserInfo user4 = createAdminUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return all non-system users
        assertThat(mProfileHelper.getAllProfiles()).containsExactly(fgUser, user2, user3, user4);
    }

    @Test
    public void testGetAllUsers_notHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(false);

        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two admin, and two non-admin users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createNonAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        UserInfo user3 = createNonAdminUser(fgUserId + 2);
        UserInfo user4 = createAdminUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return all users
        assertThat(mProfileHelper.getAllProfiles())
                .containsExactly(systemUser, fgUser, user2, user3, user4);
    }

    @Test
    public void testGetAllSwitchableUsers() {
        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two non-foreground users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createAdminUser(fgUserId);
        UserInfo user1 = createAdminUser(fgUserId + 1);
        UserInfo user2 = createAdminUser(fgUserId + 2);

        mockGetUsers(systemUser, fgUser, user1, user2);

        // Should return all non-foreground users.
        assertThat(mProfileHelper.getAllSwitchableProfiles()).containsExactly(user1, user2);
    }

    @Test
    public void testGetAllSwitchableUsers_notHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(false);

        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two non-foreground users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createAdminUser(fgUserId);
        UserInfo user1 = createAdminUser(fgUserId + 1);
        UserInfo user2 = createAdminUser(fgUserId + 2);

        mockGetUsers(systemUser, fgUser, user1, user2);

        // Should return all non-foreground users.
        assertThat(mProfileHelper.getAllSwitchableProfiles()).containsExactly(systemUser, user1,
                user2);
    }

    @Test
    public void testGetAllPersistentUsers() {
        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two non-ephemeral users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        // Create two ephemeral users.
        UserInfo user3 = createEphemeralUser(fgUserId + 2);
        UserInfo user4 = createEphemeralUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return all non-ephemeral users.
        assertThat(mProfileHelper.getAllPersistentProfiles()).containsExactly(fgUser, user2);
    }

    @Test
    public void testGetAllPersistentUsers_notHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(false);

        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two non-ephemeral users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        // Create two ephemeral users.
        UserInfo user3 = createEphemeralUser(fgUserId + 2);
        UserInfo user4 = createEphemeralUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return all non-ephemeral users.
        assertThat(mProfileHelper.getAllPersistentProfiles()).containsExactly(systemUser, fgUser,
                user2);
    }

    @Test
    public void testGetAllAdminUsers() {
        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two admin, and two non-admin users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createNonAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        UserInfo user3 = createNonAdminUser(fgUserId + 2);
        UserInfo user4 = createAdminUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return only admin users.
        assertThat(mProfileHelper.getAllAdminProfiles()).containsExactly(user2, user4);
    }

    @Test
    public void testGetAllAdminUsers_notHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(false);

        // Add system user
        UserInfo systemUser = createAdminUser(UserHandle.USER_SYSTEM);

        // Create two admin, and two non-admin users.
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createNonAdminUser(fgUserId);
        UserInfo user2 = createAdminUser(fgUserId + 1);
        UserInfo user3 = createNonAdminUser(fgUserId + 2);
        UserInfo user4 = createAdminUser(fgUserId + 3);

        mockGetUsers(systemUser, fgUser, user2, user3, user4);

        // Should return only admin users.
        assertThat(mProfileHelper.getAllAdminProfiles()).containsExactly(systemUser, user2, user4);
    }

    @Test
    public void testRemoveUser_isAdminUser_cannotRemoveSystemUser() {
        UserInfo systemUser = new UserInfo(
                UserHandle.USER_SYSTEM,
                "Driver",
                /* iconPath= */ null,
                /* flags= */ UserInfo.FLAG_ADMIN | UserInfo.FLAG_SYSTEM,
                /* userType= */ USER_TYPE_SYSTEM_HEADLESS);

        assertThat(mProfileHelper.removeProfile(mContext, systemUser))
                .isEqualTo(ProfileHelper.REMOVE_PROFILE_RESULT_FAILED);
    }

    @Test
    public void testRemoveUser_isAdmin_canRemoveOtherUsers() {
        // Create admin user and non-admin user
        int fgUserId = ActivityManager.getCurrentUser();
        int nonAdminUserId = fgUserId + 1;
        UserInfo fgUser = createAdminUser(fgUserId);
        UserInfo nonAdminUser = createNonAdminUser(nonAdminUserId);

        mockGetUsers(fgUser, nonAdminUser);
        mockRemoveUser(nonAdminUserId, UserRemovalResult.STATUS_SUCCESSFUL);
        // If Admin is removing non-current, non-system user, simply calls removeUser.
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        assertThat(mProfileHelper.removeProfile(mContext, nonAdminUser))
                .isEqualTo(ProfileHelper.REMOVE_PROFILE_RESULT_SUCCESS);
        verify(mMockCarUserManager).removeUser(nonAdminUserId);
    }

    @Test
    public void testRemoveUser_isNonAdmin_cannotRemoveOtherUsers() {
        // Create two non-admin users
        int fgUserId = ActivityManager.getCurrentUser();
        UserInfo fgUser = createNonAdminUser(fgUserId);
        UserInfo user2 = createNonAdminUser(fgUserId + 1);
        mockGetUsers(fgUser, user2);

        // Make current user non-admin.
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        // Mock so that removeUser always pretends it's successful.
        mockRemoveUserSuccess();

        // If Non-Admin is trying to remove someone other than themselves, they should fail.
        assertThat(mProfileHelper.removeProfile(mContext, user2))
                .isEqualTo(ProfileHelper.REMOVE_PROFILE_RESULT_FAILED);
        verify(mMockCarUserManager, never()).removeUser(user2.id);
    }


    @Test
    public void testRemoveUser_removesLastAdminUser_createsAndSwitchesToNewAdminUser() {
        // Ensure admin status
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        // Create one admin and one non-admin
        int baseId = 10;
        UserInfo adminUser = createAdminUser(baseId);
        UserInfo nonAdminInfo = createNonAdminUser(baseId + 1);
        mockGetUsers(adminUser, nonAdminInfo);
        UserInfo newAdminInfo = createAdminUser(baseId + 2);
        mockRemoveUserSuccess();
        mockCreateUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN,
                UserCreationResult.STATUS_SUCCESSFUL, newAdminInfo);
        mockSwitchUserSuccess();

        assertThat(mProfileHelper.removeProfile(mContext, adminUser))
                .isEqualTo(ProfileHelper.REMOVE_PROFILE_RESULT_SUCCESS);

        verify(mMockCarUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        verify(mMockCarUserManager).switchUser(newAdminInfo.id);
        verify(mMockCarUserManager).removeUser(adminUser.id);
    }

    @Test
    public void testRemoveUser_removesLastAdminUserFailsCreateNewUser_doesNotRemoveOrSwitchUser() {
        // Ensure admin status
        when(mMockUserManager.isAdminUser()).thenReturn(true);

        // Create one admin and one non-admin
        int baseId = 10;
        UserInfo adminUser = createAdminUser(baseId);
        UserInfo nonAdminInfo = createNonAdminUser(baseId + 1);
        mockGetUsers(adminUser, nonAdminInfo);

        // Fail to create a new user to force a failure case
        mockCreateUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN,
                UserCreationResult.STATUS_ANDROID_FAILURE, null);

        assertThat(mProfileHelper.removeProfile(mContext, adminUser))
                .isEqualTo(ProfileHelper.REMOVE_PROFILE_RESULT_FAILED);
        verify(mMockCarUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        verify(mMockCarUserManager, never()).switchUser(anyInt());
        verify(mMockCarUserManager, never()).removeUser(adminUser.id);
    }

    @Test
    public void testRemoveUser_removeForegroundUser_callsSwitchToGuest() {
        // Create foreground user
        int baseId = 10;
        ShadowProcess.setUid(baseId * UserHandle.PER_USER_RANGE); // User 10
        UserInfo currentUser = createNonAdminUser(baseId);
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        mockGetUsers(currentUser);

        UserInfo guestUser = createGuestUser(baseId + 1);
        mockRemoveUserSuccess();
        mockCreateGuest(DEFAULT_GUEST_NAME, UserCreationResult.STATUS_SUCCESSFUL, guestUser);
        mockSwitchUserSuccess();

        assertUserRemoved(ProfileHelper.REMOVE_PROFILE_RESULT_SUCCESS, guestUser, currentUser);
    }

    @Test
    public void testRemoveUser_removeForegroundUser_switchFails_returnsSetEphemeralResult() {
        // Create foreground user
        int baseId = 10;
        ShadowProcess.setUid(baseId * UserHandle.PER_USER_RANGE); // User 10
        UserInfo currentUser = createNonAdminUser(baseId);
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        mockGetUsers(currentUser);

        UserInfo guestUser = createGuestUser(baseId + 1);
        mockRemoveUserSuccess();
        mockCreateGuest(DEFAULT_GUEST_NAME, UserCreationResult.STATUS_SUCCESSFUL, guestUser);
        mockSwitchUserFailure();

        assertUserRemoved(ProfileHelper.REMOVE_PROFILE_RESULT_SWITCH_FAILED, guestUser,
                currentUser);
    }

    private void assertUserRemoved(int expectedResult, UserInfo newUser, UserInfo removedUser) {
        assertThat(mProfileHelper.removeProfile(mContext, removedUser)).isEqualTo(expectedResult);
        verify(mMockCarUserManager).createGuest(newUser.name);
        verify(mMockCarUserManager).switchUser(newUser.id);
        verify(mMockCarUserManager).removeUser(removedUser.id);
    }

    @Test
    public void testGetMaxSupportedRealUsers_isHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(true);
        when(mMockUserManager.getMaxSupportedUsers()).thenReturn(7);

        // Create System user, two managed profiles, and two normal users.
        UserInfo user0 = createAdminUser(0);
        UserInfo user1 = createNonAdminUser(10);
        UserInfo user2 = createManagedProfile(11);
        UserInfo user3 = createNonAdminUser(13);
        UserInfo user4 = createManagedProfile(14);

        mockGetUsers(user0, user1, user2, user3, user4);

        // Max users - # managed profiles - headless system user.
        assertThat(mProfileHelper.getMaxSupportedRealProfiles()).isEqualTo(4);
    }

    @Test
    public void testGetMaxSupportedRealUsers_isNotHeadless() {
        ShadowUserManager.setIsHeadlessSystemUserMode(false);
        when(mMockUserManager.getMaxSupportedUsers()).thenReturn(7);

        // Create System user, two managed profiles, and two normal users.
        UserInfo user0 = createAdminUser(0);
        UserInfo user1 = createNonAdminUser(10);
        UserInfo user2 = createManagedProfile(11);
        UserInfo user3 = createNonAdminUser(13);
        UserInfo user4 = createManagedProfile(14);

        mockGetUsers(user0, user1, user2, user3, user4);

        // Max users - # managed profiles
        assertThat(mProfileHelper.getMaxSupportedRealProfiles()).isEqualTo(5);
    }

    @Test
    public void testCreateNewOrFindExistingGuest_ifGuestExists_returnsExistingGuest() {
        // Create two users and a guest user.
        UserInfo user1 = createAdminUser(10);
        UserInfo user2 = createNonAdminUser(12);
        UserInfo guestUser = createGuestUser(13);

        mockGetUsers(user1, user2, guestUser);
        mockCreateUserFail();

        when(mMockUserManager.findCurrentGuestUser()).thenReturn(guestUser);

        UserInfo guest = mProfileHelper.createNewOrFindExistingGuest(mContext);
        assertThat(guest).isEqualTo(guestUser);
    }

    @Test
    public void testCreateNewOrFindExistingGuest_ifNoGuest_createsNewGuest() {
        // Create two users.
        UserInfo user1 = createAdminUser(10);
        UserInfo user2 = createNonAdminUser(12);

        mockGetUsers(user1, user2);

        // Create a user for the "new guest" user.
        UserInfo guestInfo = createGuestUser(21);

        mockCreateGuest(DEFAULT_GUEST_NAME, UserCreationResult.STATUS_SUCCESSFUL, guestInfo);

        UserInfo guest = mProfileHelper.createNewOrFindExistingGuest(mContext);
        verify(mMockCarUserManager).createGuest(DEFAULT_GUEST_NAME);
        assertThat(guest).isEqualTo(guestInfo);
    }

    private UserInfo createAdminUser(int id) {
        return new UserInfo(id, null, UserInfo.FLAG_ADMIN);
    }

    private UserInfo createNonAdminUser(int id) {
        return new UserInfo(id, null, 0);
    }

    private UserInfo createEphemeralUser(int id) {
        return new UserInfo(id, null, UserInfo.FLAG_EPHEMERAL);
    }

    private UserInfo createManagedProfile(int id) {
        return new UserInfo(id, null, UserInfo.FLAG_MANAGED_PROFILE);
    }

    private UserInfo createGuestUser(int id) {
        return new UserInfo(id, null, UserInfo.FLAG_GUEST);
    }

    private void mockGetUsers(UserInfo... users) {
        mockUmGetAliveUsers(mMockUserManager, users);
    }

    private void mockRemoveUser(int userId, int status) {
        when(mMockCarUserManager.removeUser(userId)).thenReturn(new UserRemovalResult(status));
    }

    private void mockRemoveUserSuccess() {
        when(mMockCarUserManager.removeUser(anyInt()))
                .thenReturn(new UserRemovalResult(UserRemovalResult.STATUS_SUCCESSFUL));
    }

    private void mockCreateUserFail() {
        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
        future.complete(new UserCreationResult(UserCreationResult.STATUS_ANDROID_FAILURE,
                /* user= */ null));
        AndroidAsyncFuture<UserCreationResult> asyncFuture = new AndroidAsyncFuture<>(future);
        when(mMockCarUserManager.createUser(any(), anyInt())).thenReturn(asyncFuture);
        when(mMockCarUserManager.createGuest(any())).thenReturn(asyncFuture);
    }

    private void mockCreateUser(String name, int flag, int status, UserInfo userInfo) {
        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
        future.complete(new UserCreationResult(status, userInfo.getUserHandle()));
        when(mMockCarUserManager.createUser(name, flag))
                .thenReturn(new AndroidAsyncFuture<>(future));
    }

    private void mockCreateGuest(String name, int status, UserInfo userInfo) {
        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
        future.complete(new UserCreationResult(status, userInfo.getUserHandle()));
        when(mMockCarUserManager.createGuest(name)).thenReturn(new AndroidAsyncFuture<>(future));
    }

    private void mockSwitchUserSuccess() {
        AndroidFuture<UserSwitchResult> future = new AndroidFuture<>();
        future.complete(
                new UserSwitchResult(UserSwitchResult.STATUS_SUCCESSFUL, /* errorMessage= */null));
        when(mMockCarUserManager.switchUser(anyInt())).thenReturn(new AndroidAsyncFuture<>(future));
    }

    private void mockSwitchUserFailure() {
        AndroidFuture<UserSwitchResult> future = new AndroidFuture<>();
        future.complete(new UserSwitchResult(UserSwitchResult.STATUS_ANDROID_FAILURE,
                /* errorMessage= */null));
        when(mMockCarUserManager.switchUser(anyInt())).thenReturn(new AndroidAsyncFuture<>(future));
    }
}
