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
 * limitations under the License.
 */
package com.android.car.user;

import static android.car.testapi.CarMockitoHelper.mockHandleRemoteExceptionFromCarServiceWithDefaultValue;
import static android.car.testapi.CarTestingHelper.getResult;
import static android.os.UserHandle.SYSTEM;
import static android.os.UserHandle.USER_SYSTEM;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.ICarUserService;
import android.car.SyncResultCallback;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.util.UserTestingHelper;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserHandleSwitchUiCallback;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.CarUserManager.UserSwitchUiCallback;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalRequest;
import android.car.user.UserRemovalResult;
import android.car.user.UserSwitchRequest;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.hardware.automotive.vehicle.UserIdentificationAssociationSetValue;
import android.hardware.automotive.vehicle.UserIdentificationAssociationType;
import android.hardware.automotive.vehicle.UserIdentificationAssociationValue;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.internal.ResultCallbackImpl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CarUserManagerUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private Car mCar;
    @Mock
    private UserManager mUserManager;
    @Mock
    private ICarUserService mService;
    @Mock
    private Context mMockContext;

    private CarUserManager mMgr;

    public CarUserManagerUnitTest() {
        super(CarUserManager.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(UserManager.class);
    }

    @Before
    public void setFixtures() {
        mMgr = new CarUserManager(mCar, mService, mUserManager);
        when(mCar.getContext()).thenReturn(mMockContext);
    }

    @Test
    public void testUserIdentificationAssociationType() {
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_KEY_FOB)
                .isEqualTo(UserIdentificationAssociationType.KEY_FOB);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_1)
                .isEqualTo(UserIdentificationAssociationType.CUSTOM_1);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_2)
                .isEqualTo(UserIdentificationAssociationType.CUSTOM_2);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_3)
                .isEqualTo(UserIdentificationAssociationType.CUSTOM_3);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_TYPE_CUSTOM_4)
                .isEqualTo(UserIdentificationAssociationType.CUSTOM_4);
    }

    @Test
    public void testUserIdentificationAssociationSetValue() {
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_ASSOCIATE_CURRENT_USER)
                .isEqualTo(UserIdentificationAssociationSetValue.ASSOCIATE_CURRENT_USER);
        assertThat(
                CarUserManager.USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_CURRENT_USER)
                        .isEqualTo(UserIdentificationAssociationSetValue.DISASSOCIATE_CURRENT_USER);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_SET_VALUE_DISASSOCIATE_ALL_USERS)
                .isEqualTo(UserIdentificationAssociationSetValue.DISASSOCIATE_ALL_USERS);
    }

    @Test
    public void testUserIdentificationAssociationValue() {
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_VALUE_UNKNOWN)
                .isEqualTo(UserIdentificationAssociationValue.UNKNOWN);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATE_CURRENT_USER)
                .isEqualTo(UserIdentificationAssociationValue.ASSOCIATED_CURRENT_USER);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_VALUE_ASSOCIATED_ANOTHER_USER)
                .isEqualTo(UserIdentificationAssociationValue.ASSOCIATED_ANOTHER_USER);
        assertThat(CarUserManager.USER_IDENTIFICATION_ASSOCIATION_VALUE_NOT_ASSOCIATED_ANY_USER)
                .isEqualTo(UserIdentificationAssociationValue.NOT_ASSOCIATED_ANY_USER);
    }

    @Test
    public void testIsValidUserId_headlessSystemUser() {
        mockIsHeadlessSystemUserMode(true);
        setExistingUsers(USER_SYSTEM);

        assertThat(mMgr.isValidUser(USER_SYSTEM)).isFalse();
    }

    @Test
    public void testIsValidUser_headlessSystemUser() {
        mockIsHeadlessSystemUserMode(true);
        setExistingUsers(USER_SYSTEM);

        assertThat(mMgr.isValidUser(SYSTEM)).isFalse();
    }

    @Test
    public void testIsValidUserId_nonHeadlessSystemUser() {
        mockIsHeadlessSystemUserMode(false);
        setExistingUsers(USER_SYSTEM);

        assertThat(mMgr.isValidUser(USER_SYSTEM)).isTrue();
    }

    @Test
    public void testIsValidUser_nonHeadlessSystemUser() {
        mockIsHeadlessSystemUserMode(false);
        setExistingUsers(USER_SYSTEM);

        assertThat(mMgr.isValidUser(SYSTEM)).isTrue();
    }

    @Test
    public void testIsValidUserId_found() {
        setExistingUsers(1, 2, 3);

        expectThat(mMgr.isValidUser(1)).isTrue();
        expectThat(mMgr.isValidUser(2)).isTrue();
        expectThat(mMgr.isValidUser(3)).isTrue();
    }

    @Test
    public void testIsValidUser_found() {
        setExistingUsers(1, 2, 3);

        expectThat(mMgr.isValidUser(UserHandle.of(1))).isTrue();
        expectThat(mMgr.isValidUser(UserHandle.of(2))).isTrue();
        expectThat(mMgr.isValidUser(UserHandle.of(3))).isTrue();
    }

    @Test
    public void testIsValidUserId_notFound() {
        setExistingUsers(1, 2, 3);

        assertThat(mMgr.isValidUser(4)).isFalse();
    }

    @Test
    public void testIsValidUser_notFound() {
        setExistingUsers(1, 2, 3);

        assertThat(mMgr.isValidUser(UserHandle.of(4))).isFalse();
    }

    @Test
    public void testIsValidUserId_emptyUsers() {
        assertThat(mMgr.isValidUser(666)).isFalse();
    }

    @Test
    public void testIsValidUser_emptyUsers() {
        assertThat(mMgr.isValidUser(UserHandle.of(666))).isFalse();
    }

    @Test
    public void testAddListener_nullExecutor() {
        assertThrows(NullPointerException.class, () -> mMgr.addListener(null, (e) -> { }));
    }

    @Test
    public void testAddListener_nullListener() {
        assertThrows(NullPointerException.class, () -> mMgr.addListener(Runnable::run, null));
    }

    @Test
    public void testAddListener_nullFilter() {
        assertThrows(NullPointerException.class,
                () -> mMgr.addListener(Runnable::run, /* filter= */null, (e) -> {}));
    }

    @Test
    public void testAddListener_sameListenerAddedTwice() {
        UserLifecycleListener listener = (e) -> { };

        mMgr.addListener(Runnable::run, listener);
        assertThrows(IllegalStateException.class, () -> mMgr.addListener(Runnable::run, listener));
    }

    @Test
    public void testAddListener_differentListenersAddedTwice() throws Exception {
        mMgr.addListener(Runnable::run, (e) -> { });
        mMgr.addListener(Runnable::run, (e) -> { });

        verify(mService, times(2)).setLifecycleListenerForApp(any(), isNull(), any());
    }

    @Test
    public void testAddListener_differentListenersWithFilter() throws Exception {
        UserLifecycleEventFilter filter1 = new UserLifecycleEventFilter.Builder()
                .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING).build();
        mMgr.addListener(Runnable::run, filter1, (e) -> { });
        UserLifecycleEventFilter filter2 = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.CURRENT).build();
        mMgr.addListener(Runnable::run, filter2, (e) -> { });

        verify(mService).setLifecycleListenerForApp(any(), eq(filter1), any());
        verify(mService).setLifecycleListenerForApp(any(), eq(filter2), any());
    }

    @Test
    public void testRemoveListener_nullListener() {
        assertThrows(NullPointerException.class, () -> mMgr.removeListener(null));
    }

    @Test
    public void testRemoveListener_notAddedBefore() {
        UserLifecycleListener listener = (e) -> { };

        assertThrows(IllegalStateException.class, () -> mMgr.removeListener(listener));
    }

    @Test
    public void testRemoveListener_addAndRemove() {
        UserLifecycleListener listener = (e) -> { };

        mMgr.addListener(Runnable::run, listener);
        mMgr.removeListener(listener);

        // Make sure it was removed
        assertThrows(IllegalStateException.class, () -> mMgr.removeListener(listener));
    }

    @Test
    public void testSwitchUser_success() throws Exception {
        expectServiceSwitchUserSucceeds(11, UserSwitchResult.STATUS_SUCCESSFUL);

        mMgr.switchUser(new UserSwitchRequest.Builder(
                        UserHandle.of(11)).build(), Runnable::run, response -> {
                    assertThat(response.getStatus()).isEqualTo(UserSwitchResult.STATUS_SUCCESSFUL);
                    assertThat(response.getErrorMessage()).isNull();
                }
        );

    }

    @Test
    public void testSwitchUserId_success() throws Exception {
        expectServiceSwitchUserSucceeds(11, UserSwitchResult.STATUS_SUCCESSFUL);

        AsyncFuture<UserSwitchResult> future = mMgr.switchUser(11);

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_SUCCESSFUL);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testSwitchUser_remoteException() throws Exception {
        expectServiceSwitchUserFails(11, new RemoteException("D'OH!"));
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        mMgr.switchUser(new UserSwitchRequest.Builder(
                        UserHandle.of(11)).build(), Runnable::run, response -> {
                    assertThat(response.getStatus()).isEqualTo(
                            UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
                    assertThat(response.getErrorMessage()).isNull();
                }
        );
    }

    @Test
    public void testSwitchUserId_remoteException() throws Exception {
        expectServiceSwitchUserFails(11, new RemoteException("D'OH!"));
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserSwitchResult> future = mMgr.switchUser(11);

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testSwitchUser_runtimeException() throws Exception {
        expectServiceSwitchUserFails(11, new RuntimeException("D'OH!"));

        mMgr.switchUser(new UserSwitchRequest.Builder(
                        UserHandle.of(11)).build(), Runnable::run, response -> {
                    assertThat(response.getStatus()).isEqualTo(
                            UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
                    assertThat(response.getErrorMessage()).isNull();
                }
        );
    }

    @Test
    public void testSwitchUserId_runtimeException() throws Exception {
        expectServiceSwitchUserFails(11, new RuntimeException("D'OH!"));

        AsyncFuture<UserSwitchResult> future = mMgr.switchUser(11);

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testLogoutUser_success() throws Exception {
        expectServiceLogoutUserSucceeds(UserSwitchResult.STATUS_SUCCESSFUL);

        AsyncFuture<UserSwitchResult> future = mMgr.logoutUser();

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_SUCCESSFUL);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testLogoutUser_remoteException() throws Exception {
        expectServiceLogoutUserFails(new RemoteException("D'OH!"));
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserSwitchResult> future = mMgr.logoutUser();

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testLogoutUser_runtimeException() throws Exception {
        expectServiceLogoutUserFails(new RuntimeException("D'OH!"));

        AsyncFuture<UserSwitchResult> future = mMgr.logoutUser();

        assertThat(future).isNotNull();
        UserSwitchResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testRemoveUser_success() throws Exception {
        expectServiceRemoveUserSucceeds(100);

        mMgr.removeUser(new UserRemovalRequest.Builder(
                UserHandle.of(100)).build(), Runnable::run, response ->
                assertThat(response.getStatus()).isEqualTo(UserRemovalResult.STATUS_SUCCESSFUL)
        );
    }

    @Test
    public void testRemoveUserId_success() throws Exception {
        expectServiceRemoveUserSucceeds(100);

        UserRemovalResult result = mMgr.removeUser(100);

        assertThat(result.getStatus()).isEqualTo(UserRemovalResult.STATUS_SUCCESSFUL);
    }

    @Test
    public void testRemoveUser_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).removeUser(eq(100), any());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        mMgr.removeUser(new UserRemovalRequest.Builder(
                UserHandle.of(100)).build(), Runnable::run, response ->
                assertThat(response.getStatus()).isEqualTo(UserRemovalResult.STATUS_ANDROID_FAILURE)
        );
    }

    @Test
    public void testRemoveUserId_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).removeUser(eq(100), any());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        UserRemovalResult result = mMgr.removeUser(100);

        assertThat(result.getStatus()).isEqualTo(UserRemovalResult.STATUS_ANDROID_FAILURE);
    }

    @Test
    public void testRemoveUser_runtimeException() throws Exception {
        doThrow(new RuntimeException("D'OH!")).when(mService).removeUser(eq(100), any());

        mMgr.removeUser(new UserRemovalRequest.Builder(
                UserHandle.of(100)).build(), Runnable::run, response ->
                assertThat(response.getStatus()).isEqualTo(UserRemovalResult.STATUS_ANDROID_FAILURE)
        );
    }

    @Test
    public void testRemoveUserId_runtimeException() throws Exception {
        doThrow(new RuntimeException("D'OH!")).when(mService).removeUser(eq(100), any());

        UserRemovalResult result = mMgr.removeUser(100);

        assertThat(result.getStatus()).isEqualTo(UserRemovalResult.STATUS_ANDROID_FAILURE);
    }

    @Test
    public void testSetSwitchUserIdUICallback_success() throws Exception {
        UserSwitchUiCallback callback = (u)-> {};

        mMgr.setUserSwitchUiCallback(callback);

        verify(mService).setUserSwitchUiCallback(any());
    }

    @Test
    public void testSetSwitchUserUICallback_nullCallback() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mMgr.setUserSwitchUiCallback(null));
    }

    @Test
    public void testSetSwitchUserUICallback_success() throws Exception {
        UserHandleSwitchUiCallback callback = (u)-> {};

        mMgr.setUserSwitchUiCallback(Runnable::run, callback);

        verify(mService).setUserSwitchUiCallback(any());
    }

    @Test
    public void testCreateUser_success() throws Exception {
        expectServiceCreateUserSucceeds("dude", UserManager.USER_TYPE_FULL_SECONDARY, 42,
                UserCreationResult.STATUS_SUCCESSFUL);
        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();

        mMgr.createUser(new UserCreationRequest.Builder().setName("dude").build(), Runnable::run,
                userCreationResultCallback);

        UserCreationResult result = userCreationResultCallback.get();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();

        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(108);
    }

    @Test
    public void testCreateUserId_success() throws Exception {
        expectServiceCreateUserSucceeds("dude", UserManager.USER_TYPE_FULL_SECONDARY, 42,
                UserCreationResult.STATUS_SUCCESSFUL);

        AsyncFuture<UserCreationResult> future = mMgr.createUser("dude", 42);
        assertThat(future).isNotNull();

        UserCreationResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();

        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(108);
    }

    @Test
    public void testCreateUser_remoteException() throws Exception {
        expectServiceCreateUserFails("dude", UserManager.USER_TYPE_FULL_SECONDARY, 42);
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);
        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();

        mMgr.createUser(new UserCreationRequest.Builder().setName("dude").build(), Runnable::run,
                userCreationResultCallback);

        UserCreationResult result = userCreationResultCallback.get();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testCreateUserId_remoteException() throws Exception {
        expectServiceCreateUserFails("dude", UserManager.USER_TYPE_FULL_SECONDARY, 42);
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserCreationResult> future = mMgr.createUser("dude", 42);
        assertThat(future).isNotNull();

        UserCreationResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testCreateUser_runtimeException() throws Exception {
        doThrow(new RuntimeException("D'OH!")).when(mService).createUser(notNull(), anyInt(),
                notNull());
        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();

        mMgr.createUser(new UserCreationRequest.Builder().setName("dude").build(), Runnable::run,
                userCreationResultCallback);

        UserCreationResult result = userCreationResultCallback.get();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testCreateUserId_runtimeException() throws Exception {
        doThrow(new RuntimeException("D'OH!")).when(mService).createUser(notNull(), anyInt(),
                notNull());

        AsyncFuture<UserCreationResult> future = mMgr.createUser("dude", 42);
        assertThat(future).isNotNull();

        UserCreationResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testCreateGuest_success() throws Exception {
        expectServiceCreateUserSucceeds("dudeGuest", UserManager.USER_TYPE_FULL_GUEST, 0,
                UserCreationResult.STATUS_SUCCESSFUL);

        AsyncFuture<UserCreationResult> future = mMgr.createGuest("dudeGuest");
        assertThat(future).isNotNull();

        UserCreationResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();

        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(108);
    }

    @Test
    public void testCreateGuest_remoteException() throws Exception {
        expectServiceCreateUserFails("dudeGuest", UserManager.USER_TYPE_FULL_GUEST, 0);
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserCreationResult> future = mMgr.createGuest("dudeGuest");
        assertThat(future).isNotNull();

        UserCreationResult result = getResult(future);
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testGetUserIdentificationAssociation_nullTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.getUserIdentificationAssociation(null));
    }

    @Test
    public void testGetUserIdentificationAssociation_emptyTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.getUserIdentificationAssociation(new int[] {}));
    }

    @Test
    public void testGetUserIdentificationAssociation_remoteException() throws Exception {
        int[] types = new int[] {1};
        when(mService.getUserIdentificationAssociation(types))
                .thenThrow(new RemoteException("D'OH!"));
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        UserIdentificationAssociationResponse response =
                mMgr.getUserIdentificationAssociation(types);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    public void testGetUserIdentificationAssociation_runtimeException() throws Exception {
        int[] types = new int[] {1};
        when(mService.getUserIdentificationAssociation(types))
                .thenThrow(new RuntimeException("D'OH!"));
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        UserIdentificationAssociationResponse response =
                mMgr.getUserIdentificationAssociation(types);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    public void testGetUserIdentificationAssociation_ok() throws Exception {
        int[] types = new int[] { 4, 8, 15, 16, 23, 42 };
        UserIdentificationAssociationResponse expectedResponse =
                UserIdentificationAssociationResponse.forSuccess(types);
        when(mService.getUserIdentificationAssociation(types)).thenReturn(expectedResponse);

        UserIdentificationAssociationResponse actualResponse =
                mMgr.getUserIdentificationAssociation(types);

        assertThat(actualResponse).isSameInstanceAs(expectedResponse);
    }

    @Test
    public void testSetUserIdentificationAssociation_nullTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.setUserIdentificationAssociation(null, new int[] {42}));
    }

    @Test
    public void testSetUserIdentificationAssociation_emptyTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.setUserIdentificationAssociation(new int[0], new int[] {42}));
    }

    @Test
    public void testSetUserIdentificationAssociation_nullValues() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.setUserIdentificationAssociation(new int[] {42}, null));
    }

    @Test
    public void testSetUserIdentificationAssociation_emptyValues() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.setUserIdentificationAssociation(new int[] {42}, new int[0]));
    }

    @Test
    public void testSetUserIdentificationAssociation_sizeMismatch() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mMgr.setUserIdentificationAssociation(new int[] {1}, new int[] {2, 3}));
    }

    @Test
    public void testSetUserIdentificationAssociation_remoteException() throws Exception {
        int[] types = new int[] {1};
        int[] values = new int[] {2};
        doThrow(new RemoteException("D'OH!")).when(mService)
                .setUserIdentificationAssociation(anyInt(), same(types), same(values), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserIdentificationAssociationResponse> future =
                mMgr.setUserIdentificationAssociation(types, values);

        assertThat(future).isNotNull();
        UserIdentificationAssociationResponse result = getResult(future);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getValues()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testSetUserIdentificationAssociation_runtimeException() throws Exception {
        int[] types = new int[] {1};
        int[] values = new int[] {2};
        doThrow(new RuntimeException("D'OH!")).when(mService)
                .setUserIdentificationAssociation(anyInt(), same(types), same(values), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserIdentificationAssociationResponse> future =
                mMgr.setUserIdentificationAssociation(types, values);

        assertThat(future).isNotNull();
        UserIdentificationAssociationResponse result = getResult(future);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getValues()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    public void testSetUserIdentificationAssociation_ok() throws Exception {
        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        doAnswer((inv) -> {
            @SuppressWarnings("unchecked")
            AndroidFuture<UserIdentificationAssociationResponse> future =
                    (AndroidFuture<UserIdentificationAssociationResponse>) inv
                            .getArguments()[3];
            UserIdentificationAssociationResponse response = UserIdentificationAssociationResponse
                    .forSuccess(values, "D'OH!");
            future.complete(response);
            return null;
        }).when(mService)
                .setUserIdentificationAssociation(anyInt(), same(types), same(values), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        AsyncFuture<UserIdentificationAssociationResponse> future =
                mMgr.setUserIdentificationAssociation(types, values);

        assertThat(future).isNotNull();
        UserIdentificationAssociationResponse result = getResult(future);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValues()).asList().containsAtLeast(10, 20, 30).inOrder();
        assertThat(result.getErrorMessage()).isEqualTo("D'OH!");
    }

    @Test
    public void testIsUserHalUserAssociation() throws Exception {
        when(mService.isUserHalUserAssociationSupported()).thenReturn(false).thenReturn(true);

        assertThat(mMgr.isUserHalUserAssociationSupported()).isFalse();
        assertThat(mMgr.isUserHalUserAssociationSupported()).isTrue();
    }

    @Test
    public void testIsUserHalUserAssociation_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).isUserHalUserAssociationSupported();
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        assertThat(mMgr.isUserHalUserAssociationSupported()).isFalse();
    }

    @Test
    public void testIsUserHalUserAssociation_runtimeException() throws Exception {
        doThrow(new RuntimeException("D'OH!")).when(mService).isUserHalUserAssociationSupported();

        assertThat(mMgr.isUserHalUserAssociationSupported()).isFalse();
    }

    private void expectServiceSwitchUserSucceeds(@UserIdInt int userId,
            @UserSwitchResult.Status int status) throws RemoteException {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserSwitchResult> resultCallbackImpl =
                    (ResultCallbackImpl<UserSwitchResult>) invocation.getArguments()[2];
            resultCallbackImpl.complete(new UserSwitchResult(status, /* errorMessage= */ null));
            return null;
        }).when(mService).switchUser(eq(userId), anyInt(), notNull(), anyBoolean());
    }

    private void expectServiceSwitchUserFails(@UserIdInt int userId, Exception e) throws Exception {
        doThrow(e).when(mService).switchUser(eq(userId), anyInt(), notNull(), anyBoolean());
    }

    private void expectServiceLogoutUserSucceeds(@UserSwitchResult.Status int status)
            throws RemoteException {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserSwitchResult> resultCallbackImpl =
                    (ResultCallbackImpl<UserSwitchResult>) invocation.getArguments()[1];
            resultCallbackImpl.complete(new UserSwitchResult(status, /* errorMessage= */ null));
            return null;
        }).when(mService).logoutUser(anyInt(), notNull());
    }

    private void expectServiceLogoutUserFails(Exception e) throws Exception {
        doThrow(e).when(mService).logoutUser(anyInt(), notNull());
    }

    private void expectServiceRemoveUserSucceeds(@UserIdInt int userId) throws RemoteException {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserRemovalResult> resultResultCallbackImpl =
                    (ResultCallbackImpl<UserRemovalResult>) invocation.getArguments()[1];
            resultResultCallbackImpl.complete(
                    new UserRemovalResult(UserRemovalResult.STATUS_SUCCESSFUL));
            return null;
        }).when(mService).removeUser(eq(userId), notNull());
    }

    private void expectServiceCreateUserSucceeds(@Nullable String name,
            @NonNull String userType, @UserInfoFlag int flags,
            @UserCreationResult.Status int status) throws RemoteException {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            UserInfo newUser = new UserTestingHelper.UserInfoBuilder(108)
                    .setName(name).setType(userType).setFlags(flags).build();
            ResultCallbackImpl<UserCreationResult> resultCallbackImpl =
                    (ResultCallbackImpl<UserCreationResult>) invocation.getArguments()[2];
            resultCallbackImpl.complete(new UserCreationResult(status, newUser.getUserHandle()));
            return null;
        }).when(mService).createUser(notNull(), anyInt(), notNull());
    }

    private void expectServiceCreateUserFails(@Nullable String name,
            @NonNull String userType, @UserInfoFlag int flags) throws RemoteException {
        doThrow(new RemoteException("D'OH!")).when(mService).createUser(notNull(), anyInt(),
                notNull());
    }

    private void setExistingUsers(int... userIds) {
        // TODO(b/197184481): move this logic to helper classes (UserTestingHelper.java &
        // AndroidMockitoHelper.java)
        List<UserHandle> userHandles =  Arrays.stream(userIds)
                .mapToObj(id -> UserHandle.of(id))
                .collect(Collectors.toList());
        when(mUserManager.getUserHandles(/* excludeDying= */ true)).thenReturn(userHandles);
    }
}
