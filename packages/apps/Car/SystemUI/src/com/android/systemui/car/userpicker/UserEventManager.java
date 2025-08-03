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

package com.android.systemui.car.userpicker;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.lifecycleEventTypeToString;
import static android.os.UserHandle.USER_ALL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserManager.SWITCHABILITY_STATUS_OK;
import static android.os.UserManager.isHeadlessSystemUserMode;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.SyncResultCallback;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationResult;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserStartRequest;
import android.car.user.UserStartResponse;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.car.user.UserSwitchRequest;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Helper class for {@link UserManager}, this is meant to be used by builds that support
 * {@link UserManager#isVisibleBackgroundUsersEnabled() Multi-user model with Concurrent Multi
 * User Feature.}
 *
 * <p>This class handles user event such as creating, removing, unlocking, stopped, and so on.
 * Also, it provides methods for creating, stopping, starting users.
 */
@UserPickerScope
public final class UserEventManager {
    private static final String TAG = UserEventManager.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final long USER_TIMEOUT_MS = 10_000;

    private final UserLifecycleEventFilter mFilter = new UserLifecycleEventFilter.Builder()
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_CREATED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_REMOVED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();

    private final Context mContext;
    private final UserManager mUserManager;
    private final CarServiceMediator mCarServiceMediator;
    private final UserPickerSharedState mUserPickerSharedState;

    /**
     * {@link UserPickerController} is per-display object. It adds listener to UserEventManager to
     * update user information, and UserEventManager will call listeners whenever user event occurs.
     * mUpdateListeners is used only on main thread.
     */
    private final SparseArray<OnUpdateUsersListener> mUpdateListeners;

    private final Handler mMainHandler;

    /**
     * This is used to wait until previous user is in invisible state.
     * When changing user, previous user is stopped, and new user is started. But new user can not
     * be started if occupant zone is not unassigned for previous user yet, and occupant zone
     * unassignment is processed on user invisible event. In this reason, we should wait until
     * previous user is in invisible state for stable user starting. <b/275973135>
     */
    private final UserInvisibleWaiter mUserInvisibleWaiter = new UserInvisibleWaiter();

    /**
     * We don't use the main thread for UX responsiveness when handling user events.
     */
    private final ExecutorService mUserLifecycleReceiver;

    @VisibleForTesting
    final UserLifecycleListener mUserLifecycleListener = event -> {
        onUserEvent(event);
    };

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runUpdateUsersOnMainThread();
        }
    };

    @Inject
    UserEventManager(Context context, CarServiceMediator carServiceMediator,
            UserPickerSharedState userPickerSharedState) {
        mUpdateListeners = new SparseArray<>();
        mContext = context.getApplicationContext();
        mUserLifecycleReceiver = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        mUserManager = mContext.getSystemService(UserManager.class);
        mUserPickerSharedState = userPickerSharedState;
        mCarServiceMediator = carServiceMediator;
        mCarServiceMediator.registerUserChangeEventsListener(mUserLifecycleReceiver, mFilter,
                mUserLifecycleListener);
        registerUserInfoChangedReceiver();
    }

    /**
     * Unregisters all the listeners when the owners is being destroyed
     */
    void onDestroy() {
        mCarServiceMediator.onDestroy();
        mContext.unregisterReceiver(mUserUpdateReceiver);
    }

    private void onUserEvent(CarUserManager.UserLifecycleEvent event) {
        int eventType = event.getEventType();
        int userId = event.getUserId();
        if (DEBUG) {
            Slog.d(TAG, "event=" + lifecycleEventTypeToString(eventType) + " userId=" + userId);
        }
        if (eventType == USER_LIFECYCLE_EVENT_TYPE_STOPPING) {
            mUserPickerSharedState.addStoppingUserId(userId);
        } else if (eventType == USER_LIFECYCLE_EVENT_TYPE_STOPPED) {
            if (mUserPickerSharedState.isStoppingUser(userId)) {
                mUserPickerSharedState.removeStoppingUserId(userId);
            }
        } else if (eventType == USER_LIFECYCLE_EVENT_TYPE_INVISIBLE) {
            mUserInvisibleWaiter.onUserInvisible(userId);
        }
        runUpdateUsersOnMainThread(userId, eventType);
    }

    private void registerUserInfoChangedReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mUserUpdateReceiver, UserHandle.ALL, filter, null, null);
    }

    void registerOnUpdateUsersListener(OnUpdateUsersListener listener, int displayId) {
        if (listener == null) {
            return;
        }
        mUpdateListeners.put(displayId, listener);
    }

    void unregisterOnUpdateUsersListener(int displayId) {
        mUpdateListeners.remove(displayId);
    }

    @MainThread
    private void updateUsers(@UserIdInt int userId, int userEvent) {
        for (int i = 0; i < mUpdateListeners.size(); i++) {
            OnUpdateUsersListener listener = mUpdateListeners.valueAt(i);
            if (listener != null) {
                listener.onUpdateUsers(userId, userEvent);
            }
        }
    }

    void runUpdateUsersOnMainThread() {
        runUpdateUsersOnMainThread(USER_ALL, 0);
    }

    void runUpdateUsersOnMainThread(@UserIdInt int userId, int userEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> updateUsers(userId, userEvent));
        } else {
            updateUsers(userId, userEvent);
        }
    }

    static int getMaxSupportedUsers() {
        int maxSupportedUsers = UserManager.getMaxSupportedUsers();
        if (isHeadlessSystemUserMode()) {
            maxSupportedUsers -= 1;
        }
        return maxSupportedUsers;
    }

    UserInfo getUserInfo(@UserIdInt int userId) {
        return mUserManager.getUserInfo(userId);
    }

    UserInfo getCurrentForegroundUserInfo() {
        return mUserManager.getUserInfo(ActivityManager.getCurrentUser());
    }

    /**
     * Gets alive users from user manager except guest users to create user records.
     * If it is headless system user mode, removes system user info from the list by
     * {@link UserManager#getAliveUsers}.
     *
     * @return the list of users that were created except guest users.
     */
    List<UserInfo> getAliveUsers() {
        List<UserInfo> aliveUsers = mUserManager.getAliveUsers();
        for (int i = aliveUsers.size() - 1; i >= 0; i--) {
            UserInfo userInfo = aliveUsers.get(i);
            if ((isHeadlessSystemUserMode() && userInfo.id == USER_SYSTEM)
                    || userInfo.isGuest()) {
                aliveUsers.remove(i);
            }
        }
        return aliveUsers;
    }

    boolean isUserLimitReached() {
        int countNonGuestUsers = getAliveUsers().size();
        int maxSupportedUsers = getMaxSupportedUsers();

        if (countNonGuestUsers > maxSupportedUsers) {
            Slog.e(TAG, "There are more users on the device than allowed.");
            return true;
        }
        return countNonGuestUsers == maxSupportedUsers;
    }

    boolean canForegroundUserAddUsers() {
        return !mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_ADD_USER,
                UserHandle.of(ActivityManager.getCurrentUser()));
    }

    boolean isForegroundUserNotSwitchable(UserHandle fgUserHandle) {
        return mUserManager.getUserSwitchability(fgUserHandle) != SWITCHABILITY_STATUS_OK;
    }

    @Nullable
    UserCreationResult createNewUser() {
        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        AsyncFuture<UserCreationResult> future = carUserManager.createUser(
                mContext.getString(R.string.car_new_user), 0);
        return getUserCreationResult(future);
    }

    @Nullable
    UserCreationResult createGuest() {
        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        AsyncFuture<UserCreationResult> future = carUserManager.createGuest(
                mContext.getString(R.string.car_guest));
        return getUserCreationResult(future);
    }

    @Nullable
    private UserCreationResult getUserCreationResult(AsyncFuture<UserCreationResult> future) {
        UserCreationResult result = null;
        try {
            result = future.get(USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                Slog.e(TAG, "Timed out creating guest after " + USER_TIMEOUT_MS + "ms...");
                return null;
            }
        } catch (InterruptedException e) {
            Slog.w(TAG, "Interrupted waiting for future " + future, e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            Slog.w(TAG, "Exception getting future " + future, e);
            return null;
        }

        return result;
    }

    boolean isUserRunningUnlocked(@UserIdInt int userId) {
        return mUserManager.isUserRunning(userId) && mUserManager.isUserUnlocked(userId);
    }

    boolean isUserRunning(@UserIdInt int userId) {
        return mUserManager.isUserRunning(userId);
    }

    boolean startUserForDisplay(@UserIdInt int prevCurrentUser, @UserIdInt int userId,
            int displayId, boolean isFgUserStart) {
        if (DEBUG) {
            Slog.d(TAG, "switchToUserForDisplay " + userId + " State :  Running "
                    + mUserManager.isUserRunning(userId) + " Unlocked "
                    + mUserManager.isUserUnlocked(userId) + " displayId=" + displayId
                    + " prevCurrentUser=" + prevCurrentUser + " isFgUserStart=" + isFgUserStart);
        }
        UserHandle userHandle = UserHandle.of(userId);
        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        if (carUserManager == null) {
            Slog.w(TAG, "car user manager is not available when starting user " + userId);
            return false;
        }
        if (isFgUserStart) {
            // Old user will be stopped by {@link UserController} after user switching
            // completed. In the case of user switching, to avoid clicking stopping user, we can
            // block previous current user immediately here by adding to the list of stopping
            // users.
            mUserPickerSharedState.addStoppingUserId(prevCurrentUser);
            try {
                SyncResultCallback<UserSwitchResult> userSwitchCallback =
                        new SyncResultCallback<>();
                carUserManager.switchUser(new UserSwitchRequest.Builder(
                        userHandle).build(), Runnable::run, userSwitchCallback);
                UserSwitchResult userSwitchResult =
                        userSwitchCallback.get(USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (userSwitchResult.isSuccess()) {
                    Slog.i(TAG, "Successful switchUser from " + prevCurrentUser + " to " + userId
                            + ". Result: " + userSwitchResult);
                    return true;
                }
                Slog.w(TAG, "Failed to switchUser from " + prevCurrentUser + " to " + userId
                        + ". Result: " + userSwitchResult);
            } catch (Exception e) {
                Slog.e(TAG, "Exception during switchUser from " + prevCurrentUser + " to "
                        + userId, e);
                return false;
            }
        }

        try {
            SyncResultCallback<UserStartResponse> userStartCallback = new SyncResultCallback<>();
            carUserManager.startUser(
                    new UserStartRequest.Builder(UserHandle.of(userId))
                            .setDisplayId(displayId).build(),
                    Runnable::run, userStartCallback);
            UserStartResponse userStartResponse =
                    userStartCallback.get(USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (userStartResponse.isSuccess()) {
                Slog.i(TAG, "Successful startUser for user " + userId + " on display "
                        + displayId + ". Result: " + userStartResponse);
                return true;
            }
            Slog.w(TAG, "startUser failed for " + userId + " on display " + displayId
                    + ". Result: " + userStartResponse);
        } catch (Exception e) {
            Slog.e(TAG, "Exception during startUser for user " + userId + " on display "
                    + displayId, e);
        }

        return false;
    }

    boolean stopUserUnchecked(@UserIdInt int userId, int displayId) {
        if (DEBUG) {
            Slog.d(TAG, "stop user:" + userId);
        }

        mUserPickerSharedState.addStoppingUserId(userId);

        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        if (carUserManager == null) {
            Slog.w(TAG, "car user manager is not available when stopping user " + userId);
            return false;
        }

        // We do not need to unassign the user from the occupant zone, because it is handled by
        // CarUserService#onUserInvisible().
        try {
            mUserInvisibleWaiter.init(userId);
            SyncResultCallback<UserStopResponse> userStopCallback = new SyncResultCallback<>();
            carUserManager.stopUser(new UserStopRequest.Builder(UserHandle.of(userId)).build(),
                    Runnable::run, userStopCallback);
            UserStopResponse userStopResponse =
                    userStopCallback.get(USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (userStopResponse.isSuccess()) {
                Slog.i(TAG, "Successful stopUser for user " + userId + " on display " + displayId
                        + ". Result: " + userStopResponse);
                return mUserInvisibleWaiter.waitUserInvisible();
            }
            Slog.w(TAG, "stopUser failed for user " + userId + " on display " + displayId
                    + ". Result: " + userStopResponse);
        } catch (Exception e) {
            Slog.e(TAG, "Exception during stopUser for user " + userId + " on display "
                    + displayId, e);
        }

        mUserPickerSharedState.removeStoppingUserId(userId);
        return false;
    }

    private static class UserInvisibleWaiter {
        private @UserIdInt int mUserId;
        private CountDownLatch mWaiter;

        void init(@UserIdInt int userId) {
            mUserId = userId;
            mWaiter = new CountDownLatch(1);
        }

        boolean waitUserInvisible() {
            if (mWaiter != null) {
                try {
                    // This method returns false when timeout occurs so that user can re-try to
                    // login. A timeout means that stopUser() has been called successfully, but
                    // the user hasn't changed to invisible yet.
                    return mWaiter.await(USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    mWaiter = null;
                }
            }
            return true;
        }

        void onUserInvisible(@UserIdInt int userId) {
            if (userId == mUserId && mWaiter != null) {
                mWaiter.countDown();
                mWaiter = null;
            }
        }
    }

    /**
     * Interface for listeners that want to register for receiving updates to changes to the users
     * on the system including removing and adding users, and changing user info.
     */
    public interface OnUpdateUsersListener {
        /**
         * Method that will get called when users list has been changed.
         */
        void onUpdateUsers(@UserIdInt int userId, int userEvent);
    }
}
