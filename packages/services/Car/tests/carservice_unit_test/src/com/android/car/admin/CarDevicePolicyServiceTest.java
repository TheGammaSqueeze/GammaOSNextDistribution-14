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
package com.android.car.admin;

import static android.app.admin.DevicePolicyManager.ACTION_SHOW_NEW_USER_DISCLAIMER;

import static com.android.car.admin.CarDevicePolicyService.NEW_USER_DISCLAIMER_STATUS_ACKED;
import static com.android.car.admin.CarDevicePolicyService.NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT;
import static com.android.car.admin.CarDevicePolicyService.NEW_USER_DISCLAIMER_STATUS_SHOWN;
import static com.android.car.admin.CarDevicePolicyService.newUserDisclaimerStatusToString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.SyncResultCallback;
import android.car.admin.CarDevicePolicyManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.car.BuiltinPackageDependency;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ResultCallbackImpl;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public final class CarDevicePolicyServiceTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarDevicePolicyServiceTest.class.getSimpleName();
    @Mock
    private CarUserService mCarUserService;

    @Mock
    private Context mContext;

    @Mock
    private Context mBuiltinPackageContext;

    @Mock
    private PackageManager mPackageManager;

    @Mock
    private DevicePolicyManager mDpm;

    @Mock
    private ClassLoader mClassLoader;

    @Mock
    private NotificationHelper mNotificationHelper;

    private CarDevicePolicyService mService;

    private final ResultCallbackImpl<UserRemovalResult> mUserRemovalResultCallbackImpl =
            new ResultCallbackImpl<>(Runnable::run, new SyncResultCallback<>());

    private final SyncResultCallback<UserCreationResult> mSyncResultCallbackForCreateUser =
            new SyncResultCallback<>();

    private final ResultCallbackImpl<UserCreationResult> mUserCreationResultCallbackImpl =
            new ResultCallbackImpl<>(Runnable::run, mSyncResultCallbackForCreateUser);

    private AndroidFuture<UserStartResult> mUserStartResult = new AndroidFuture<>();

    private AndroidFuture<UserStopResult> mUserStopResult = new AndroidFuture<>();

    public CarDevicePolicyServiceTest() {
        super(CarDevicePolicyService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(CarServiceUtils.class);
        session.spyStatic(ActivityManager.class);
        session.spyStatic(BuiltinPackageDependency.class);
    }

    @Before
    public void setFixtures() throws Exception {
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mBuiltinPackageContext.getClassLoader()).thenReturn(mClassLoader);
        // when . thenReturn does not work when returning class
        doReturn(NotificationHelper.class).when(mClassLoader).loadClass(any());
        doReturn(mNotificationHelper)
                .when(() -> BuiltinPackageDependency.createNotificationHelper(
                        mBuiltinPackageContext));

        mService = new CarDevicePolicyService(mContext, mBuiltinPackageContext, mCarUserService);
    }

    @Test
    public void testRemoveUser() {
        mService.removeUser(42, mUserRemovalResultCallbackImpl);

        verify(mCarUserService).removeUser(eq(42), /* hasCallerRestrictions= */ eq(true),
                eq(mUserRemovalResultCallbackImpl));
    }

    @Test
    public void testCreateUser_failure_invalidTypes() throws Exception {
        invalidCreateUserTypeTest(CarDevicePolicyManager.FIRST_USER_TYPE - 1);
        invalidCreateUserTypeTest(CarDevicePolicyManager.LAST_USER_TYPE + 1);
    }

    private void invalidCreateUserTypeTest(@CarDevicePolicyManager.UserType int type)
            throws Exception {
        mService.createUser("name", type, mUserCreationResultCallbackImpl);
        UserCreationResult result = mSyncResultCallbackForCreateUser.get();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_INVALID_REQUEST);
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void testCreateUser_ok_normalUser() {
        createUserOkTest(/* userInfoFlags=*/ 0, CarDevicePolicyManager.USER_TYPE_REGULAR,
                UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    public void testCreateUser_ok_admin() {
        createUserOkTest(UserInfo.FLAG_ADMIN, CarDevicePolicyManager.USER_TYPE_ADMIN,
                UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    public void testCreateUser_ok_guest() {
        createUserOkTest(/* userInfoFlags=*/ 0, CarDevicePolicyManager.USER_TYPE_GUEST,
                UserManager.USER_TYPE_FULL_GUEST);
    }

    private void createUserOkTest(@UserInfoFlag int flags,
            @CarDevicePolicyManager.UserType int carDpmUserType, @NonNull String userType) {
        mService.createUser("name", carDpmUserType, mUserCreationResultCallbackImpl);
        UserCreationRequest.Builder userCreationRequestBuilder =
                new UserCreationRequest.Builder().setName("name");
        switch(carDpmUserType) {
            case CarDevicePolicyManager.USER_TYPE_REGULAR:
                break;
            case CarDevicePolicyManager.USER_TYPE_ADMIN:
                userCreationRequestBuilder.setAdmin();
                break;
            case CarDevicePolicyManager.USER_TYPE_GUEST:
                userCreationRequestBuilder.setGuest();
                break;
            default:
                Log.d(TAG, "CarUserService.createUser(): invalid carDpmUserType (" + carDpmUserType
                        + ")");
                break;
        }
        UserCreationRequest userCreationRequest = userCreationRequestBuilder.build();

        ArgumentCaptor<UserCreationRequest> argument = ArgumentCaptor.forClass(
                UserCreationRequest.class);
        verify(mCarUserService).createUser(argument.capture(), /* timeoutMs= */
                anyInt(), eq(mUserCreationResultCallbackImpl));
        assertWithMessage("UserCreationRequest").that(argument.getValue().toString()).isEqualTo(
                userCreationRequest.toString());

    }

    @Test
    public void testStartUserInBackground() {
        mService.startUserInBackground(42, mUserStartResult);

        verify(mCarUserService).startUserInBackground(42, mUserStartResult);
    }

    @Test
    public void testStopUser() {
        mService.stopUser(42, mUserStopResult);

        verify(mCarUserService).stopUser(42, mUserStopResult);
    }

    @Test
    public void testShowDisclaimerWhenIntentReceived() {
        int userId = 100;
        doAnswer(inv -> userId).when(() -> ActivityManager.getCurrentUser());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN))
                .thenReturn(true);
        BroadcastReceiver receiver = callInit();

        sendShowNewUserDisclaimerBroadcast(receiver);

        verify(mNotificationHelper).showUserDisclaimerNotification(UserHandle.of(userId));
        assertStatusString(userId, NEW_USER_DISCLAIMER_STATUS_NOTIFICATION_SENT);
    }

    @Test
    public void testSetUserDisclaimerShown() {
        int userId = 100;
        mService.setUserDisclaimerShown(userId);

        assertStatusString(userId, NEW_USER_DISCLAIMER_STATUS_SHOWN);
    }

    @Test
    public void testSetUserDisclaimerAcknowledged() {
        int userId = 100;
        when(mContext.createContextAsUser(UserHandle.of(userId), 0)).thenReturn(mContext);

        mService.setUserDisclaimerAcknowledged(userId);

        assertStatusString(userId, NEW_USER_DISCLAIMER_STATUS_ACKED);
        verify(mNotificationHelper).cancelUserDisclaimerNotification(UserHandle.of(userId));
        verify(mDpm).acknowledgeNewUserDisclaimer();
    }

    private BroadcastReceiver callInit() {
        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);

        mService.init();

        verify(mContext).registerReceiverForAllUsers(
                captor.capture(), any(), any(), any(), anyInt());
        BroadcastReceiver receiver = captor.getValue();
        assertWithMessage("BroadcastReceiver captured on onCreate()")
                .that(receiver).isNotNull();

        return receiver;
    }

    private void sendShowNewUserDisclaimerBroadcast(BroadcastReceiver receiver) {
        receiver.onReceive(mContext, new Intent(ACTION_SHOW_NEW_USER_DISCLAIMER));
    }

    private void assertStatusString(int userId,
            @CarDevicePolicyService.NewUserDisclaimerStatus int expectedStatus) {
        int actualStatus = mService.getNewUserDisclaimerStatus(userId);
        assertWithMessage("newUserDisclaimerStatus (%s=%s, %s=%s)",
                expectedStatus, newUserDisclaimerStatusToString(expectedStatus),
                actualStatus, newUserDisclaimerStatusToString(actualStatus))
                .that(actualStatus).isEqualTo(expectedStatus);
    }
}
