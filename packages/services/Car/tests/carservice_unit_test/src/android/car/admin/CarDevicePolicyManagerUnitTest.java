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
package android.car.admin;

import static android.car.testapi.CarMockitoHelper.mockHandleRemoteExceptionFromCarServiceWithDefaultValue;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.util.UserTestingHelper.UserInfoBuilder;
import android.car.user.UserCreationResult;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.car.internal.ResultCallbackImpl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class CarDevicePolicyManagerUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private Car mCar;

    @Mock
    private ICarDevicePolicyService mService;

    private CarDevicePolicyManager mMgr;

    public CarDevicePolicyManagerUnitTest() {
        super(CarDevicePolicyManager.TAG);
    }

    @Before
    public void setFixtures() {
        mMgr = new CarDevicePolicyManager(mCar, mService);
    }

    @Test
    public void testRemoveUser_success() throws Exception {
        int status = UserRemovalResult.STATUS_SUCCESSFUL;
        mockRemoveUser(100, status);

        RemoveUserResult result = mMgr.removeUser(UserHandle.of(100));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_SUCCESS);
    }

    @Test
    public void testRemoveUser_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).removeUser(eq(100), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        RemoveUserResult result = mMgr.removeUser(UserHandle.of(100));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RemoveUserResult.STATUS_FAILURE_GENERIC);
    }

    @Test
    public void testRemoveUser_securityException() throws Exception {
        doThrow(new SecurityException("D'OH!")).when(mService).removeUser(eq(100), notNull());

        assertThrows(SecurityException.class, () -> mMgr.removeUser(UserHandle.of(100)));
    }

    @Test
    public void testRemoveUser_nullUser() {
        assertThrows(NullPointerException.class, () -> mMgr.removeUser(null));
    }

    @Test
    public void testCreateUser_success() throws Exception {
        UserInfo user = new UserInfoBuilder(100).build();
        int status = UserCreationResult.STATUS_SUCCESSFUL;
        mockCreateUser("TheDude", user, status);

        CreateUserResult result = mMgr.createUser("TheDude", 100);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(CreateUserResult.STATUS_SUCCESS);
        assertThat(result.getUserHandle().getIdentifier()).isEqualTo(100);
    }

    @Test
    public void testCreateUser_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).createUser(eq("TheDude"), eq(100),
                notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        CreateUserResult result = mMgr.createUser("TheDude", 100);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(CreateUserResult.STATUS_FAILURE_GENERIC);
        assertThat(result.getUserHandle()).isNull();
    }

    @Test
    public void testCreateUser_securityException() throws Exception {
        doThrow(new SecurityException("D'OH!")).when(mService).createUser(eq("TheDude"), eq(100),
                notNull());

        assertThrows(SecurityException.class, () -> mMgr.createUser("TheDude", 100));
    }

    @Test
    public void testStartUserInBackground_success() throws Exception {
        mockStartUserInBackground(100, UserStartResult.STATUS_SUCCESSFUL);

        StartUserInBackgroundResult result = mMgr.startUserInBackground(UserHandle.of(100));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(StartUserInBackgroundResult.STATUS_SUCCESS);
    }

    @Test
    public void testStartUserInBackground_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!"))
                .when(mService).startUserInBackground(eq(100), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        StartUserInBackgroundResult result = mMgr.startUserInBackground(UserHandle.of(100));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus())
                .isEqualTo(StartUserInBackgroundResult.STATUS_FAILURE_GENERIC);
    }

    @Test
    public void testStartUserInBackground_nullUser() {
        assertThrows(NullPointerException.class, () -> mMgr.startUserInBackground(null));
    }

    @Test
    public void testStopUser_success() throws Exception {
        mockStopUser(100, UserStopResult.STATUS_SUCCESSFUL);

        StopUserResult result = mMgr.stopUser(UserHandle.of(100));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(StopUserResult.STATUS_SUCCESS);
    }

    @Test
    public void testStopUser_remoteException() throws Exception {
        doThrow(new RemoteException("D'OH!")).when(mService).stopUser(eq(100), notNull());
        mockHandleRemoteExceptionFromCarServiceWithDefaultValue(mCar);

        StopUserResult result = mMgr.stopUser(UserHandle.of(100));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(StopUserResult.STATUS_FAILURE_GENERIC);
    }

    @Test
    public void testStopUser_nullUser() {
        assertThrows(NullPointerException.class, () -> mMgr.stopUser(null));
    }

    private void mockRemoveUser(@UserIdInt int userId, int status) throws Exception {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserRemovalResult> resultResultCallbackImpl =
                    (ResultCallbackImpl<UserRemovalResult>) invocation.getArguments()[1];
            resultResultCallbackImpl.complete(
                    new UserRemovalResult(UserRemovalResult.STATUS_SUCCESSFUL));
            return null;
        }).when(mService).removeUser(eq(userId), notNull());
    }

    private void mockCreateUser(String name, @NonNull UserInfo user, int status) throws Exception {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            ResultCallbackImpl<UserCreationResult> resultCallbackImpl =
                    (ResultCallbackImpl<UserCreationResult>) invocation.getArguments()[2];
            resultCallbackImpl.complete(new UserCreationResult(status, user.getUserHandle()));
            return null;
        }).when(mService).createUser(eq(name), eq(user.id), notNull());
    }

    private void mockStartUserInBackground(@UserIdInt int userId, int status) throws Exception {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            AndroidFuture<UserStartResult> future =
                    (AndroidFuture<UserStartResult>) invocation.getArguments()[1];
            future.complete(new UserStartResult(status));
            return null;
        }).when(mService).startUserInBackground(eq(userId), notNull());
    }

    private void mockStopUser(@UserIdInt int userId, int status) throws Exception {
        doAnswer((invocation) -> {
            @SuppressWarnings("unchecked")
            AndroidFuture<UserStopResult> future =
                    (AndroidFuture<UserStopResult>) invocation.getArguments()[1];
            future.complete(new UserStopResult(status));
            return null;
        }).when(mService).stopUser(eq(userId), notNull());
    }
}
