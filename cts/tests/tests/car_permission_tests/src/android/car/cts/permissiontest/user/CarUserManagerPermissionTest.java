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

package android.car.cts.permissiontest.user;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_USERS;
import static android.car.Car.CAR_USER_SERVICE;
import static android.hardware.automotive.vehicle.UserIdentificationAssociationType.CUSTOM_1;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissions;
import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;
import android.car.feature.Flags;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserHandleSwitchUiCallback;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.CarUserManager.UserSwitchUiCallback;
import android.car.user.UserCreationRequest;
import android.car.user.UserRemovalRequest;
import android.car.user.UserSwitchRequest;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the {@link CarUserManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarUserManagerPermissionTest extends AbstractCarManagerPermissionTest {

    private CarUserManager mCarUserManager;

    @Before
    public void setUp() {
        super.connectCar();
        mCarUserManager = (CarUserManager) mCar.getCarManager(CAR_USER_SERVICE);
    }

    @Test
    public void testSwitchUser() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.switchUser(
                        new UserSwitchRequest.Builder(UserHandle.of(100)).build(), Runnable::run,
                        (response) -> {
                        }));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SWITCH_USER_IGNORING_UXR)
    public void testSwitchUserIgnoringUxRestriction() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.switchUserIgnoringUxRestriction(
                        new UserSwitchRequest.Builder(UserHandle.of(100)).build(), Runnable::run,
                        (response) -> {
                        }));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testSwitchUserId() throws Exception {
        Exception e = assertThrows(SecurityException.class, () -> mCarUserManager.switchUser(100));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testCreateUser() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.createUser(
                        new UserCreationRequest.Builder().setName("dude").build(), Runnable::run,
                        (response) -> {
                        }));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testCreateUserId() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.createUser(null, 0));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testCannotCreateAdminUserWithoutManageUsersPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> invokeMethodWithShellPermissions(mCarUserManager,
                        (um) -> um.createUser("Thanos", UserInfo.FLAG_ADMIN)));
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
        assertThat(e).hasMessageThat().contains("flags " + UserInfo.FLAG_ADMIN);
    }

    @Test
    public void testCannotCreateAdminUserWithTypeWithoutManageUsersPermission() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> invokeMethodWithShellPermissions(mCarUserManager,
                        (um) -> um.createUser("Thanos", UserInfo.FLAG_ADMIN)));
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
        assertThat(e).hasMessageThat().contains("flags " + UserInfo.FLAG_ADMIN);
    }

    @Test
    public void testRemoveUser() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.removeUser(
                        new UserRemovalRequest.Builder(UserHandle.of(100)).build(), Runnable::run,
                        (response) -> {
                        }));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testRemoveUserId() throws Exception {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.removeUser(100));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testAddListenerPermission() {
        UserLifecycleListener listener = (e) -> { };

        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.addListener(Runnable::run, listener));
        assertThat(e).hasMessageThat().contains(INTERACT_ACROSS_USERS);
        assertThat(e).hasMessageThat().contains(INTERACT_ACROSS_USERS_FULL);
    }

    @Test
    public void testRemoveListenerPermission() throws Exception {
        UserLifecycleListener listener = (e) -> { };
        invokeMethodWithShellPermissionsNoReturn(mCarUserManager,
                (um) -> um.addListener(Runnable::run, listener));

        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.removeListener(listener));
        assertThat(e).hasMessageThat().contains(INTERACT_ACROSS_USERS);
        assertThat(e).hasMessageThat().contains(INTERACT_ACROSS_USERS_FULL);
    }

    @Test
    public void testGetUserIdentificationAssociationPermission() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.getUserIdentificationAssociation(CUSTOM_1));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testSetUserIdentificationAssociationPermission() {
        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.setUserIdentificationAssociation(
                        new int[] {CUSTOM_1}, new int[] {42}));
        assertThat(e).hasMessageThat().contains(CREATE_USERS);
        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testIsValidUserId() {
        assertThrows(SecurityException.class, () -> mCarUserManager.isValidUser(42));
    }

    @Test
    public void testIsValidUser() {
        assertThrows(SecurityException.class,
                () -> mCarUserManager.isValidUser(UserHandle.of(42)));
    }

    @Test
    public void testSetUserIdSwitchUiCallback() {
        UserSwitchUiCallback callback = (u)-> {};

        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.setUserSwitchUiCallback(callback));

        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }

    @Test
    public void testSetUserSwitchUiCallback() {
        UserHandleSwitchUiCallback callback = (u)-> {};

        Exception e = assertThrows(SecurityException.class,
                () -> mCarUserManager.setUserSwitchUiCallback(Runnable::run, callback));

        assertThat(e).hasMessageThat().contains(MANAGE_USERS);
    }
}
