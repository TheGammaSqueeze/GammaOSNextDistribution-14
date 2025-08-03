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
package android.car.test.mocks;

import static android.car.test.mocks.CarArgumentMatchers.isUserHandle;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.CarVersion;
import android.car.PlatformVersion;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.test.util.UserTestingHelper;
import android.car.test.util.Visitor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PermissionResult;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.UserManager.RemoveResult;
import android.os.UserManager.UserSwitchabilityResult;
import android.util.Log;

import org.mockito.ArgumentMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Provides common Mockito calls for core Android classes.
 */
public final class AndroidMockitoHelper {

    private static final String TAG = AndroidMockitoHelper.class.getSimpleName();

    /**
     * Mocks a call to {@link ActivityManager#getCurrentUser()}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ActivityManager.class)}.
     *
     * @param userId result of such call
     */
    public static void mockAmGetCurrentUser(@UserIdInt int userId) {
        doReturn(userId).when(() -> ActivityManager.getCurrentUser());
    }

    /**
     * Mocks a call to {@link ActivityManager#switchUser(UserHandle)}.
     */
    public static void mockAmSwitchUser(ActivityManager am, UserHandle user,
            boolean result) {
        when(am.switchUser(user)).thenReturn(result);
    }

    /**
     * Mocks a call to {@link ActivityManagerHelper#startUserInBackground(int)}.
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockAmStartUserInBackground(@UserIdInt int userId, boolean result)
            throws Exception {
        doReturn(result).when(() -> ActivityManagerHelper.startUserInBackground(userId));
    }

    /**
     * Mocks a call to {@link ActivityManagerHelper#startUserInBackgroundVisibleOnDisplay(int, int)}
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockAmStartUserInBackgroundVisibleOnDisplay(
            @UserIdInt int userId, int displayId, boolean result) throws Exception {
        doReturn(result).when(()
                -> ActivityManagerHelper.startUserInBackgroundVisibleOnDisplay(userId, displayId));
    }

    /**
     * Mocks a call to {@link ActivityManagerHelper#stopUser(int, boolean)}.
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockForceStopUser(@UserIdInt int userId, int result) throws Exception {
        doReturn(result)
                .when(() -> ActivityManagerHelper.stopUser(userId, /* force= */ true));
    }

    /**
     * Mocks a throwing call to {@link ActivityManagerHelper#stopUser(int, boolean)}.
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockForceStopUserThrows(@UserIdInt int userId, Throwable throwable)
            throws Exception {
        doThrow(throwable).when(() -> ActivityManagerHelper.stopUser(userId, /* force= */ true));
    }

    /**
     * Mocks a call to {@link ActivityManagerHelper#stopUserWithDelayedLocking(int, boolean)}.
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockStopUserWithDelayedLocking(@UserIdInt int userId, int result)
            throws Exception {
        doReturn(result)
                .when(() -> ActivityManagerHelper.stopUserWithDelayedLocking(
                        userId, /* force= */ true));
    }

    /**
     * Mocks a throwing call to
     *     {@link ActivityManagerHelper#stopUserWithDelayedLocking(int, boolean)}.
     *
     * * <p><b>Note: </b>it must be made inside a
     *      * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     *      * {@code spyStatic(ActivityManagerHelper.class)}.
     */
    public static void mockStopUserWithDelayedLockingThrows(@UserIdInt int userId,
            Throwable throwable) throws Exception {
        doThrow(throwable).when(() -> ActivityManagerHelper.stopUserWithDelayedLocking(
                userId, /* force= */ true));
    }

    /**
     * Mocks a call to {@link DevicePolicyManager#logoutUser()}.
     */
    public static void mockDpmLogoutUser(DevicePolicyManager dpm, int result) {
        when(dpm.logoutUser()).thenReturn(result);
    }

    /**
     * Mocks a successful call to {@code UserManager#createUser(NewUserRequest)}
     */
    public static void mockUmCreateUser(UserManager um, @Nullable String name, String userType,
            @UserInfoFlag int flags, UserHandle user) {
        NewUserResponse response = new NewUserResponse(user, UserManager.USER_OPERATION_SUCCESS);
        when(um.createUser(isNewUserRequest(name, userType, flags))).thenReturn(response);
    }

    /**
     * Mocks a call to {@code UserManager#createUser(NewUserRequest)} that returns the given
     * response.
     */
    public static void mockUmCreateUser(UserManager um, @Nullable String name, String userType,
            @UserInfoFlag int flags, NewUserResponse response) {
        when(um.createUser(isNewUserRequest(name, userType, flags))).thenReturn(response);
    }

    /**
     * Mocks a call to {@code UserManager#createUser(NewUserRequest)} that throws the given
     * runtime exception.
     */
    public static void mockUmCreateUser(UserManager um, @Nullable String name, String userType,
            @UserInfoFlag int flags, RuntimeException e) {
        when(um.createUser(isNewUserRequest(name, userType, flags))).thenThrow(e);
    }

    /**
     * Mocks a successful call to {@code UserManager#createUser(NewUserRequest)}
     */
    public static void mockUmCreateGuest(UserManager um, @Nullable String name,
            @UserIdInt int userId) {
        NewUserResponse response = new NewUserResponse(UserHandle.of(userId),
                UserManager.USER_OPERATION_SUCCESS);
        when(um.createUser(
                isNewUserRequest(name, UserManager.USER_TYPE_FULL_GUEST, /* flags= */ 0)))
                .thenReturn(response);
    }

    @NonNull
    private static NewUserRequest isNewUserRequest(@Nullable String name,
            String userType, @UserInfoFlag int flags) {
        return argThat(new NewUserRequestMatcher(name, userType, flags));
    }

    /**
     * Mocks a call to {@link UserManager#isHeadlessSystemUserMode()}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@linkcom.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(UserManager.class)}.
     *
     * @param mode result of such call
     */
    public static void mockUmIsHeadlessSystemUserMode(boolean mode) {
        doReturn(mode).when(() -> UserManager.isHeadlessSystemUserMode());
    }

    /**
     * Mocks {@code UserManager#getUserInfo(userId)} to return a {@link UserInfo} with the given
     * {@code flags}.
     */
    @NonNull
    public static UserInfo mockUmGetUserInfo(UserManager um, @UserIdInt int userId,
            @UserInfoFlag int flags) {
        Objects.requireNonNull(um);
        UserInfo user = new UserTestingHelper.UserInfoBuilder(userId).setFlags(flags).build();
        mockUmGetUserInfo(um, user);
        return user;
    }

    /**
     * Mocks {@code UserManager.getUserInfo(userId)} to return the given {@link UserInfo}.
     */
    @NonNull
    public static void mockUmGetUserInfo(UserManager um, UserInfo user) {
        when(um.getUserInfo(user.id)).thenReturn(user);
    }

    /**
     * Mocks {@code UserManager#getUserInfo(userId)} when the {@code userId} is the system user's.
     */
    @NonNull
    public static void mockUmGetSystemUser(UserManager um) {
        UserInfo user = new UserTestingHelper.UserInfoBuilder(UserHandle.USER_SYSTEM)
                .setFlags(UserInfo.FLAG_SYSTEM).build();
        when(um.getUserInfo(UserHandle.USER_SYSTEM)).thenReturn(user);
    }

    /**
     * Mocks {@link UserManager#getAliveUsers()} to return the given users.
     */
    public static void mockUmGetAliveUsers(UserManager um, UserInfo... users) {
        Objects.requireNonNull(um);
        when(um.getAliveUsers()).thenReturn(UserTestingHelper.toList(users));
    }

    /**
     * Mocks {@link UserManager#getAliveUsers()} to return the simple users with the given ids.
     */
    public static void mockUmGetAliveUsers(UserManager um,
            @UserIdInt int... userIds) {
        mockUmGetUserHandles(um, true, userIds);
        List<UserInfo> users = UserTestingHelper.newUsers(userIds);
        when(um.getAliveUsers()).thenReturn(users);
    }

    /**
     * Mocks {@link UserManager#getUserHandles(boolean)} to return the given users.
     */
    public static void mockUmGetUserHandles(UserManager um, boolean excludeDying,
            UserHandle... users) {
        Objects.requireNonNull(users);
        mockUmGetUserHandles(um, excludeDying, UserTestingHelper.toList(users));
    }

    /**
     * Mocks {@link UserManager#getUserHandles(boolean)} to return the given users.
     */
    public static void mockUmGetUserHandles(UserManager um, boolean excludeDying,
            List<UserHandle> users) {
        Objects.requireNonNull(um);
        Objects.requireNonNull(users);
        when(um.getUserHandles(excludeDying)).thenReturn(users);
    }

    /**
     * Mocks {@link UserManager#getUserHandles(boolean)} to return the given users.
     */
    public static void mockUmGetUserHandles(UserManager um, boolean excludeDying,
            int... userIds) {
        mockUmGetUserHandles(um, excludeDying, UserTestingHelper.newUserHandles(userIds));
    }

    /** Mocks a call to {@link UserManager#getUserHandles(boolean)}. */
    public static void mockUmGetAllUsers(UserManager um, UserHandle... users) {
        mockUmGetUserHandles(um, /* excludeDying= */ false, users);
    }

    /**
     * Mocks a call to {@code UserManager#isUserRunning(userId)}.
     */
    public static void mockUmIsUserRunning(UserManager um, @UserIdInt int userId,
            boolean isRunning) {
        when(um.isUserRunning(userId)).thenReturn(isRunning);
        when(um.isUserRunning(UserHandle.of(userId))).thenReturn(isRunning);
    }

    /**
     * Mocks a call to {@code UserManager#isUserUnlockingOrUnlocked()}.
     */
    public static void mockUmIsUserUnlockingOrUnlocked(UserManager um, @UserIdInt int userId,
            boolean value) {
        when(um.isUserUnlockingOrUnlocked(isUserHandle(userId))).thenReturn(value);
        when(um.isUserUnlockingOrUnlocked(userId)).thenReturn(value);
    }

    /**
     * Mocks a successful call to {@code UserManager#removeUserWhenPossible(UserHandle, boolean)},
     * and notifies {@code listener} when it's called.
     */
    public static void mockUmRemoveUserWhenPossible(UserManager um,
            UserInfo user, boolean overrideDevicePolicy, @RemoveResult int result,
            @Nullable Visitor<UserInfo> listener) {
        when(um.removeUserWhenPossible(user.getUserHandle(), overrideDevicePolicy))
                .thenAnswer((inv) -> {
                    if (listener != null) {
                        Log.v(TAG, "mockUmRemoveUserWhenPossible(" + user + "): notifying "
                                + listener);
                        listener.visit(user);
                    }
                    return result;
                });
    }

    /**
     * Mocks a successful call to {@code UserManager#removeUserWhenPossible(UserHandle, boolean)},
     * and notifies {@code listener} when it's called.
     */
    public static void mockUmRemoveUserWhenPossible(UserManager um,
            UserHandle user, boolean overrideDevicePolicy, @RemoveResult int result,
            @Nullable Visitor<UserHandle> listener) {
        when(um.removeUserWhenPossible(user, overrideDevicePolicy)).thenAnswer((inv) -> {
            if (listener != null) {
                Log.v(TAG, "mockUmRemoveUserWhenPossible(" + user + "): notifying " + listener);
                listener.visit(user);
            }
            return result;
        });
    }

    /**
     * Mocks a call to {@code UserManager#hasUserRestrictionForUser(String, UserHandle)} that
     * returns {@code value}.
     */
    public static void mockUmHasUserRestrictionForUser(UserManager um,
            UserHandle user, String restrictionKey, boolean value) {
        when(um.hasUserRestrictionForUser(restrictionKey, user)).thenReturn(value);
    }

    /**
     * Mocks a call to {@code UserManager#getUserSwitchability(int)} that
     * returns {@code result}.
     */
    public static void mockUmGetUserSwitchability(UserManager um,
            @UserSwitchabilityResult int result) {
        when(um.getUserSwitchability()).thenReturn(result);
    }

    /**
     * Mocks a call to {@code UserManager#getVisibleUsers()} that
     * returns {@link UserHandle UserHandles} with the given {@code userIds}.
     */
    public static void mockUmGetVisibleUsers(UserManager um, @UserIdInt int...userIds) {
        Set<UserHandle> users = Arrays.stream(userIds).mapToObj(u -> UserHandle.of(u))
                .collect(Collectors.toSet());
        Log.v(TAG, "mockUmGetVisibleUsers(" + Arrays.toString(userIds) + ": returning "
                + users);
        when(um.getVisibleUsers()).thenReturn(users);
    }

    /**
     * Mocks a call to {@code UserManager#isUserVisible()} that returns {@code isVisible}.
     */
    public static void mockUmIsUserVisible(UserManager um, boolean isVisible) {
        when(um.isUserVisible()).thenReturn(isVisible);
    }

    /**
     * Mocks a call to {@code UserManager#isVisibleBackgroundUsersSupported()} that returns
     * {@code isVisibleBackgroundUsersSupported}.
     */
    public static void mockUmIsVisibleBackgroundUsersSupported(UserManager um,
            boolean isVisibleBackgroundUsersSupported) {
        when(um.isVisibleBackgroundUsersSupported()).thenReturn(
                isVisibleBackgroundUsersSupported);
    }

    /**
     * Mocks a call to {@code UserManager#isVisibleBackgroundUsersOnDefaultDisplaySupported()} that
     * returns {@code isVisibleBackgroundUsersOnDefaultDisplaySupported}.
     */
    public static void mockUmIsVisibleBackgroundUsersOnDefaultDisplaySupported(UserManager um,
            boolean supported) {
        when(um.isVisibleBackgroundUsersOnDefaultDisplaySupported()).thenReturn(supported);
    }

    /**
     * Mocks a call to {@link ServiceManager#getService(name)}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ServiceManager.class)}.
     *
     * @param name interface name of the service
     * @param binder result of such call
     */
    public static void mockSmGetService(String name, IBinder binder) {
        doReturn(binder).when(() -> ServiceManager.getService(name));
    }

    /**
     * Returns mocked binder implementation from the given interface name.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ServiceManager.class)}.
     *
     * @param name interface name of the service
     * @param binder mocked return of ServiceManager.getService
     * @param service binder implementation
     */
    public static <T extends IInterface> void mockQueryService(String name,
            IBinder binder, T service) {
        doReturn(binder).when(() -> ServiceManager.getService(name));
        doReturn(binder).when(() -> ServiceManager.checkService(name));
        when(binder.queryLocalInterface(anyString())).thenReturn(service);
    }

    /**
     * Mocks a call to {@link Binder.getCallingUserHandle()}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(Binder.class)}.
     *
     * @param userId identifier of the {@link UserHandle} that will be returned.
     */
    public static void mockBinderGetCallingUserHandle(@UserIdInt int userId) {
        doReturn(UserHandle.of(userId)).when(() -> Binder.getCallingUserHandle());
    }

    /**
     * Mocks a call to {@link Car#getCarVersion()
     */
    public static void mockCarGetCarVersion(CarVersion version) {
        Log.d(TAG, "mockCarGetCarVersion(): " + version);
        doReturn(version).when(() -> Car.getCarVersion());
    }

    /**
     * Mocks a call to {@link Car#getPlatformVersion()
     */
    public static void mockCarGetPlatformVersion(PlatformVersion version) {
        Log.d(TAG, "mockCarGetPlatformVersion(): " + version);
        doReturn(version).when(() -> Car.getPlatformVersion());
    }

    /**
     * Mocks a call to {@link Car#isApiVersionAtLeast()
     */
    public static void mockCarIsApiVersionAtLeast(int major, int minor, boolean isIt) {
        Log.d(TAG, "mockCarIsApiVersionAtLeast(" + major + ", " + minor + "): " + isIt);
        doReturn(isIt).when(() -> Car.isApiVersionAtLeast(major, minor));
    }

    /**
     * Mocks a call to {@link Context#getSystemService(Class)}.
     */
    public static <T> void mockContextGetService(Context context,
            Class<T> serviceClass, T service) {
        when(context.getSystemService(serviceClass)).thenReturn(service);
        if (serviceClass.equals(PackageManager.class)) {
            when(context.getPackageManager()).thenReturn(PackageManager.class.cast(service));
        }
    }

    /**
     * Mocks a call to {@link Context#checkCallingOrSelfPermission(String)}
     */
    public static void mockContextCheckCallingOrSelfPermission(Context context,
            String permission, @PermissionResult int permissionResults) {
        when(context.checkCallingOrSelfPermission(permission)).thenReturn(permissionResults);
    }

    /**
     * Mock a call to {@link Context#createContextAsUser(UserHandle, int)}}
     */
    public static void mockContextCreateContextAsUser(Context context, Context userContext,
            @UserIdInt int userId) {
        when(context.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)).thenReturn(
                userContext);
    }

    // TODO(b/192307581): add unit tests
    /**
     * Returns the result of the giving {@code callable} in the main thread, preparing the
     * {@link Looper} if needed and using a default timeout.
     */
    public static <T> T syncCallOnMainThread(Callable<T> c) throws Exception {
        return syncCallOnMainThread(JavaMockitoHelper.ASYNC_TIMEOUT_MS, c);
    }

    // TODO(b/192307581): add unit tests
    /**
     * Returns the result of the giving {@code callable} in the main thread, preparing the
     * {@link Looper} if needed.
     */
    public static <T> T syncCallOnMainThread(long timeoutMs, Callable<T> callable)
            throws Exception {
        boolean quitLooper = false;
        Looper looper = Looper.getMainLooper();
        if (looper == null) {
            Log.i(TAG, "preparing main looper");
            Looper.prepareMainLooper();
            looper = Looper.getMainLooper();
            assertWithMessage("Looper.getMainLooper()").that(looper).isNotNull();
            quitLooper = true;
        }
        Log.i(TAG, "looper: " + looper);
        AtomicReference<Exception> exception = new AtomicReference<>();
        AtomicReference<T> ref = new AtomicReference<>();
        try {
            Handler handler = new Handler(looper);
            CountDownLatch latch = new CountDownLatch(1);
            handler.post(() -> {
                T result = null;
                try {
                    result = callable.call();
                } catch (Exception e) {
                    exception.set(e);
                }
                ref.set(result);
                latch.countDown();
            });
            JavaMockitoHelper.await(latch, timeoutMs);
            Exception e = exception.get();
            if (e != null) throw e;
            return ref.get();
        } finally {
            if (quitLooper) {
                Log.i(TAG, "quitting looper: " + looper);
                looper.quitSafely();
            }
        }
    }

    // TODO(b/192307581): add unit tests
    /**
     * Runs the giving {@code runnable} in the activity's UI thread, using a default timeout.
     */
    public static void syncRunOnUiThread(Activity activity, Runnable runnable) throws Exception {
        syncRunOnUiThread(JavaMockitoHelper.ASYNC_TIMEOUT_MS, activity, runnable);
    }

    // TODO(b/192307581): add unit tests
    /**
     * Runs the giving {@code runnable} in the activity's UI thread.
     */
    public static void syncRunOnUiThread(long timeoutMs, Activity activity, Runnable runnable)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            runnable.run();
            latch.countDown();
        });
        JavaMockitoHelper.await(latch, timeoutMs);
    }

    private AndroidMockitoHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }

    static final class NewUserRequestMatcher implements
            ArgumentMatcher<NewUserRequest> {

        private final String mName;
        private final String mUserType;
        private final int mFlags;

        NewUserRequestMatcher(String name, String userType, int flags) {
            mName = name;
            mUserType = userType;
            mFlags = flags;
        }

        @Override
        public boolean matches(NewUserRequest request) {
            if (request.isAdmin()
                    && ((mFlags & UserManagerHelper.FLAG_ADMIN) != UserManagerHelper.FLAG_ADMIN)) {
                return false;
            }
            if (request.isEphemeral() && ((mFlags
                    & UserManagerHelper.FLAG_EPHEMERAL) != UserManagerHelper.FLAG_EPHEMERAL)) {
                return false;
            }

            return request.getUserType().equals(mUserType)
                    && Objects.equals(request.getName(), mName);
        }
    }
}
