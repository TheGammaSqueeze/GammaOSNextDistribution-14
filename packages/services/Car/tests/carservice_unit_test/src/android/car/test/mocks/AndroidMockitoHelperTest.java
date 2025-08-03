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

package android.car.test.mocks;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS;
import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockAmStartUserInBackground;
import static android.car.test.mocks.AndroidMockitoHelper.mockAmStartUserInBackgroundVisibleOnDisplay;
import static android.car.test.mocks.AndroidMockitoHelper.mockBinderGetCallingUserHandle;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetCarVersion;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarGetPlatformVersion;
import static android.car.test.mocks.AndroidMockitoHelper.mockCarIsApiVersionAtLeast;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextCheckCallingOrSelfPermission;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextCreateContextAsUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockContextGetService;
import static android.car.test.mocks.AndroidMockitoHelper.mockDpmLogoutUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockForceStopUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockForceStopUserThrows;
import static android.car.test.mocks.AndroidMockitoHelper.mockQueryService;
import static android.car.test.mocks.AndroidMockitoHelper.mockSmGetService;
import static android.car.test.mocks.AndroidMockitoHelper.mockStopUserWithDelayedLocking;
import static android.car.test.mocks.AndroidMockitoHelper.mockStopUserWithDelayedLockingThrows;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAliveUsers;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetSystemUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserHandles;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserInfo;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetVisibleUsers;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmHasUserRestrictionForUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsHeadlessSystemUserMode;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsUserRunning;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsUserUnlockingOrUnlocked;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsUserVisible;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsVisibleBackgroundUsersSupported;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmRemoveUserWhenPossible;
import static android.content.pm.PackageManager.PERMISSION_DENIED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.app.ActivityManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.CarVersion;
import android.car.PlatformVersion;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.test.util.UserTestingHelper;
import android.car.test.util.Visitor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

public final class AndroidMockitoHelperTest {

    private static final int TEST_USER_ID = 100;
    private static final int TEST_DISPLAY_ID = 100;

    private final UserHandle mTestUserHandle = UserHandle.of(TEST_USER_ID);

    //TODO(b/196179969): remove UserInfo
    private final UserInfo mTestUserInfo = new UserInfo(TEST_USER_ID, "testUser", "",
            UserInfo.FLAG_ADMIN, UserManager.USER_TYPE_FULL_SYSTEM);

    private StaticMockitoSession mMockSession;

    @Mock private UserManager mMockedUserManager;
    @Mock private DevicePolicyManager mMockedDevicePolicyManager;

    @Before
    public void setUp() {
        mMockSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .spyStatic(UserManager.class)
                .spyStatic(ActivityManager.class)
                .spyStatic(ActivityManagerHelper.class)
                .spyStatic(ServiceManager.class)
                .spyStatic(Binder.class)
                .spyStatic(Car.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        try {
            mMockSession.finishMocking();
        } finally {
            // When using inline mock maker, clean up inline mocks to prevent OutOfMemory errors.
            // See https://github.com/mockito/mockito/issues/1614 and b/259280359.
            Mockito.framework().clearInlineMocks();
        }
    }

    @Test
    public void testMockAmGetCurrentUser() {
        mockAmGetCurrentUser(UserHandle.USER_NULL);

        assertThat(ActivityManager.getCurrentUser()).isEqualTo(UserHandle.USER_NULL);
    }

    @Test
    public void testMockAmStartUserInBackground_true() throws Exception {
        mockAmStartUserInBackground(TEST_USER_ID, true);

        assertThat(ActivityManagerHelper.startUserInBackground(TEST_USER_ID)).isTrue();
    }

    @Test
    public void testMockAmStartUserInBackground_false() throws Exception {
        mockAmStartUserInBackground(TEST_USER_ID, false);

        assertThat(ActivityManagerHelper.startUserInBackground(TEST_USER_ID)).isFalse();
    }

    @Test
    public void testMockAmStartUserInBackgroundVisibleOnDisplay_true() throws Exception {
        mockAmStartUserInBackgroundVisibleOnDisplay(TEST_USER_ID, TEST_DISPLAY_ID, true);

        assertThat(ActivityManagerHelper
                .startUserInBackgroundVisibleOnDisplay(TEST_USER_ID, TEST_DISPLAY_ID))
                .isTrue();
    }

    @Test
    public void testMockAmStartUserInBackgroundVisibleOnDisplay_false() throws Exception {
        mockAmStartUserInBackgroundVisibleOnDisplay(TEST_USER_ID, TEST_DISPLAY_ID, false);

        assertThat(ActivityManagerHelper
                .startUserInBackgroundVisibleOnDisplay(TEST_USER_ID, TEST_DISPLAY_ID))
                .isFalse();
    }

    @Test
    public void testMockForceStopUser() throws Exception {
        mockForceStopUser(TEST_USER_ID, 42);

        assertThat(ActivityManagerHelper.stopUser(TEST_USER_ID, /* force= */ true)).isEqualTo(42);
    }

    @Test
    public void testMockForceStopUserThrows() throws Exception {
        mockForceStopUserThrows(TEST_USER_ID, new IllegalStateException());

        assertThrows(IllegalStateException.class,
                () -> ActivityManagerHelper.stopUser(TEST_USER_ID, /* force= */ true));
    }

    @Test
    public void testMockStopUserWithDelayedLocking() throws Exception {
        mockStopUserWithDelayedLocking(TEST_USER_ID, 42);

        assertThat(ActivityManagerHelper
                .stopUserWithDelayedLocking(TEST_USER_ID, /* force= */ true))
                .isEqualTo(42);
    }

    @Test
    public void testMockStopUserWithDelayedLockingThrows() throws Exception {
        mockStopUserWithDelayedLockingThrows(TEST_USER_ID, new IllegalStateException());

        assertThrows(IllegalStateException.class,
                () -> ActivityManagerHelper
                        .stopUserWithDelayedLocking(TEST_USER_ID, /* force= */ true));
    }

    @Test
    public void testMockUmIsHeadlessSystemUserMode_true() {
        mockUmIsHeadlessSystemUserMode(/* mode= */ true);

        assertThat(UserManager.isHeadlessSystemUserMode()).isTrue();
    }

    @Test
    public void testMockUmIsHeadlessSystemUserMode_false() {
        mockUmIsHeadlessSystemUserMode(/* mode= */ false);

        assertThat(UserManager.isHeadlessSystemUserMode()).isFalse();
    }

    @Test
    public void testMockUmGetUserInfo() {
        mockUmGetUserInfo(mMockedUserManager, mTestUserInfo);

        assertThat(mMockedUserManager.getUserInfo(TEST_USER_ID)).isSameInstanceAs(mTestUserInfo);
    }

    @Test
    public void testMockUmGetSystemUser() {
        mockUmGetSystemUser(mMockedUserManager);

        UserInfo expectedUser = new UserTestingHelper.UserInfoBuilder(UserHandle.USER_SYSTEM)
                .setFlags(UserInfo.FLAG_SYSTEM).build();

        assertThat(
                mMockedUserManager.getUserInfo(UserHandle.USER_SYSTEM).getUserHandle()).isEqualTo(
                expectedUser.getUserHandle());
    }

    @Test
    public void testMockUmIsUserRunning_running() {
        mockUmIsUserRunning(mMockedUserManager, TEST_USER_ID, /* isRunning= */ true);

        assertThat(mMockedUserManager.isUserRunning(TEST_USER_ID)).isTrue();
    }

    @Test
    public void testMockUmIsUserRunning_notRunning() {
        mockUmIsUserRunning(mMockedUserManager, TEST_USER_ID, /* isRunning= */ false);

        assertThat(mMockedUserManager.isUserRunning(TEST_USER_ID)).isFalse();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsUserUnlockingOrUnlocked_true() {
        mockUmIsUserUnlockingOrUnlocked(mMockedUserManager, TEST_USER_ID, true);

        assertThat(mMockedUserManager.isUserUnlockingOrUnlocked(TEST_USER_ID)).isTrue();
        assertThat(mMockedUserManager.isUserUnlockingOrUnlocked(mTestUserHandle)).isTrue();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsUserUnlockingOrUnlocked_false() {
        mockUmIsUserUnlockingOrUnlocked(mMockedUserManager, TEST_USER_ID, false);

        assertThat(mMockedUserManager.isUserUnlockingOrUnlocked(TEST_USER_ID)).isFalse();
        assertThat(mMockedUserManager.isUserUnlockingOrUnlocked(mTestUserHandle)).isFalse();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsVisibleBackgroundUsersSupported_true() {
        mockUmIsVisibleBackgroundUsersSupported(mMockedUserManager, true);

        assertThat(mMockedUserManager.isVisibleBackgroundUsersSupported()).isTrue();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsVisibleBackgroundUsersSupported_false() {
        mockUmIsVisibleBackgroundUsersSupported(mMockedUserManager, false);

        assertThat(mMockedUserManager.isVisibleBackgroundUsersSupported()).isFalse();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported_true() {
        mockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported(mMockedUserManager, true);

        assertThat(mMockedUserManager.isVisibleBackgroundUsersOnDefaultDisplaySupported()).isTrue();
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported_false() {
        mockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported(mMockedUserManager, false);

        assertThat(mMockedUserManager.isVisibleBackgroundUsersOnDefaultDisplaySupported())
                .isFalse();
    }

    @Test
    public void testMockUmGetAliveUsers() {
        UserInfo user1 = mMockedUserManager.createUser("test1",
                UserManager.USER_TYPE_FULL_SECONDARY, UserInfo.FLAG_ADMIN);
        UserInfo user2 = mMockedUserManager.createUser("test2", UserManager.USER_TYPE_FULL_GUEST,
                UserInfo.FLAG_EPHEMERAL);

        mockUmGetAliveUsers(mMockedUserManager, user1, user2);

        assertThat(mMockedUserManager.getAliveUsers()).containsExactly(user1, user2);
    }

    @Test
    public void testMockUmGetUserHandles() {
        UserHandle user1 = UserHandle.of(100);
        UserHandle user2 = UserHandle.of(200);

        mockUmGetUserHandles(mMockedUserManager, true, 100, 200);

        assertThat(mMockedUserManager.getUserHandles(true)).containsExactly(user1, user2).inOrder();
    }

    @Test
    public void testMockUmHasUserRestrictionForUser() {
        mockUmHasUserRestrictionForUser(mMockedUserManager, mTestUserHandle, "no_Homers_club",
                /* value= */ true);

        assertThat(mMockedUserManager.hasUserRestrictionForUser("no_Homers_club", mTestUserHandle))
                .isTrue();
    }

    @Test
    public void testMockUmRemoveUserWhenPossible() {
        VisitorImpl<UserInfo> visitor = new VisitorImpl<>();

        mockUmRemoveUserWhenPossible(mMockedUserManager, mTestUserInfo,
                /* overrideDevicePolicy= */ true, /* result= */ 1, visitor);

        mMockedUserManager.removeUserWhenPossible(UserHandle.of(TEST_USER_ID),
                /* overrideDevicePolicy= */ true);

        assertThat(visitor.mVisited).isEqualTo(mTestUserInfo);
    }

    @Test
    public void testMockUmGetVisibleUsers() {
        UserHandle user1 = UserHandle.of(100);
        UserHandle user2 = UserHandle.of(200);

        mockUmGetVisibleUsers(mMockedUserManager, 100, 200);

        assertThat(mMockedUserManager.getVisibleUsers()).containsExactly(user1, user2);
    }

    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void testMockUmIsUserVisible() {
        mockUmIsUserVisible(mMockedUserManager, true);

        assertThat(mMockedUserManager.isUserVisible()).isTrue();
    }

    @Test
    public void testMockDpmLogoutUser() {
        mockDpmLogoutUser(mMockedDevicePolicyManager, 42);

        assertThat(mMockedDevicePolicyManager.logoutUser()).isEqualTo(42);
    }

    @Test
    public void testMockBinderGetCallingUserHandle() {
        mockBinderGetCallingUserHandle(TEST_USER_ID);

        assertThat(Binder.getCallingUserHandle()).isEqualTo(UserHandle.of(TEST_USER_ID));
    }

    @Test
    public void testMockSmGetService() {
        IBinder someBinder = mock(IBinder.class);
        mockSmGetService("someServiceName", someBinder);

        assertThat(ServiceManager.getService("someServiceName")).isEqualTo(someBinder);
    }

    @Test
    public void testMockQueryService() {
        IInterface someService = mock(IInterface.class);
        IBinder someBinder = mock(IBinder.class);

        mockQueryService("someServiceName", someBinder, someService);

        assertThat(ServiceManager.getService("someServiceName")).isEqualTo(someBinder);
        assertThat(someBinder.queryLocalInterface("anyString")).isEqualTo(someService);
    }

    private static final class VisitorImpl<T> implements Visitor<T> {

        T mVisited;

        @Override
        public void visit(T visited) {
            mVisited = visited;
        }
    }

    @Test
    public void testMockContextGetService() {
        Context context = mock(Context.class);
        Service someService = mock(Service.class);

        mockContextGetService(context, Service.class, someService);

        assertThat(context.getSystemService(Service.class)).isEqualTo(someService);
    }

    @Test
    public void testMockContextGetService_PackageManager() {
        Context context = mock(Context.class);
        PackageManager packageManager = mock(PackageManager.class);

        mockContextGetService(context, PackageManager.class, packageManager);

        assertThat(context.getPackageManager()).isEqualTo(packageManager);
    }

    @Test
    public void testMockCarGetCarVersion() {
        CarVersion carVersion = CarVersion.forMajorVersion(666);

        mockCarGetCarVersion(carVersion);

        assertThat(Car.getCarVersion()).isSameInstanceAs(carVersion);
    }

    @Test
    public void testMockCarGetPlatformVersion() {
        PlatformVersion platformVersion = PlatformVersion.forMajorVersion(666);

        mockCarGetPlatformVersion(platformVersion);

        assertThat(Car.getPlatformVersion()).isSameInstanceAs(platformVersion);
    }

    @Test
    public void testMockCarIsApiVersionAtLeast() {
        mockCarIsApiVersionAtLeast(66, 6, true);

        assertThat(Car.isApiVersionAtLeast(66, 6)).isTrue();
    }

    @Test
    public void mockContextCheckCallingOrSelfPermission_returnsPermissionDenied() {
        Context context = mock(Context.class);

        mockContextCheckCallingOrSelfPermission(context, PERMISSION_CAR_CONTROL_AUDIO_SETTINGS,
                PERMISSION_DENIED);

        assertThat(context.checkCallingOrSelfPermission(PERMISSION_CAR_CONTROL_AUDIO_SETTINGS))
                .isEqualTo(PERMISSION_DENIED);
    }

    @Test
    public void testMockContextCreateContextAsUser() {
        Context context = mock(Context.class);
        Context userContext = mock(Context.class);
        int userId = 1000;

        mockContextCreateContextAsUser(context, userContext, userId);

        assertThat(context.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)).isEqualTo(
                userContext);
    }
}
