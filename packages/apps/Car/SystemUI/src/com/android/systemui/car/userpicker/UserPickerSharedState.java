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

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.lifecycleEventTypeToString;

import android.annotation.UserIdInt;
import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.annotations.GuardedBy;
import com.android.systemui.Dumpable;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarServiceProvider.CarServiceOnConnectedListener;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dump.DumpManager;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

/**
 * State of the UserPicker Activity.
 *
 * The instance is shared between all active UserPicker activities to prevent several users of doing
 * same actions at the same time (e.g. starting the same user)
 */
@SysUISingleton
public class UserPickerSharedState implements Dumpable {

    private static final String TAG = UserPickerSharedState.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final Object mLock = new Object();

    /**
     * Same user icon can be clicked from different displays, and then, different thread can be
     * started for user login with same user id. In this case, mUsersLoginStarted will block the
     * thread of the next clicked display.
     */
    @GuardedBy("mLock")
    private final SparseIntArray mUsersLoginStarted;

    /**
     * When changing the user to another one, the user will be stopped and new user is started.
     * In this situation, if we click the stopping user icon in user picker on another display,
     * that user can not login to secondary displays by the core logic for 'pending user start'.
     * (b/254526109) To avoid this, we track the stopping users to block the stopping user is
     * clicked until completely stopped.
     */
    @GuardedBy("mLock")
    private final Set<Integer> mStoppingUsers = new ArraySet<>();

    /**
     * When changing user, previous user is stopped, and user picker activity is also destroyed. If
     * user stopped event arrived after all user picker activities are destroyed, no one can remove
     * the user from stopping user list. This listener removes completely stopped users from the
     * list to handle that situation.
     */
    private final UserLifecycleListener mUserStoppedEventListener = event -> {
        int eventType = event.getEventType();
        int userId = event.getUserId();
        if (DEBUG) {
            Slog.d(TAG, "event=" + lifecycleEventTypeToString(eventType) + " userId=" + userId);
        }
        if (isStoppingUser(userId)) {
            removeStoppingUserId(userId);
        }
    };

    private final UserLifecycleEventFilter mFilter = new UserLifecycleEventFilter.Builder()
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();

    private final CarServiceOnConnectedListener mServiceOnConnectedListener = car -> {
        onConnected(car);
    };

    private final ExecutorService mUserStoppedEventReceiver;

    /**
     * Constructor for UserPickerSharedState
     */
    @Inject
    public UserPickerSharedState(CarServiceProvider carServiceProvider, DumpManager dumpManager) {
        mUsersLoginStarted = new SparseIntArray();
        mUserStoppedEventReceiver = Executors.newSingleThreadExecutor();
        carServiceProvider.addListener(mServiceOnConnectedListener);
        dumpManager.registerNormalDumpable(TAG, this);
    }

    @VisibleForTesting
    public UserPickerSharedState() {
        mUsersLoginStarted = new SparseIntArray();
        mUserStoppedEventReceiver = null;
    }

    private void onConnected(Car car) {
        CarUserManager carUserManager = car.getCarManager(CarUserManager.class);
        if (carUserManager != null) {
            carUserManager.addListener(mUserStoppedEventReceiver, mFilter,
                    mUserStoppedEventListener);
        }
    }

    /**
     * This method is to prevent repeated clicks on the same user icon on different displays.
     * It is called before starting user, and check the user id is in mUsersLoginStarted or not.
     * If the user id exists on there, it returns false, and worker thread which is responsible for
     * user start can not start the user. Otherwise, the user id is mapped with the display id, it
     * returns true, and worker can start the user.
     *
     * @param displayId user want to login
     * @param userId to login to the display
     * @return true if user can log in to the display, otherwise false.
     */
    boolean setUserLoginStarted(int displayId, @UserIdInt int userId) {
        if (DEBUG) {
            Slog.d(TAG, "setUserLoginStarted: userId=" + userId + " displayId=" + displayId);
        }
        synchronized (mLock) {
            for (int i = 0; i < mUsersLoginStarted.size(); i++) {
                int startedUser = mUsersLoginStarted.valueAt(i);
                if (startedUser == userId) {
                    Slog.w(TAG, "setUserLoginStarted: already started on display "
                            + mUsersLoginStarted.keyAt(i));
                    return false;
                }
            }
            mUsersLoginStarted.put(displayId, userId);
            return true;
        }
    }

    /**
     * This method is to release user id from mUsersLoginStarted. When the started user is unlocked
     * state, it is called, or it can be called if it succeeded in preoccupying the display by
     * adding the user id to the map, but failed to start the user in the subsequent process.
     */
    void resetUserLoginStarted(int displayId) {
        if (DEBUG) {
            Slog.d(TAG, "resetUserLoginStarted: displayId=" + displayId);
        }
        synchronized (mLock) {
            mUsersLoginStarted.put(displayId, INVALID_USER_ID);
        }
    }

    /**
     * Gets user id to be logging into the display.
     * It is used when the user is unlocked.
     *
     * @param displayId
     * @return user id to be logging into the display
     */
    int getUserLoginStarted(int displayId) {
        synchronized (mLock) {
            return mUsersLoginStarted.get(displayId, INVALID_USER_ID);
        }
    }

    /**
     * The user who is stopping can not start again on secondary displays now.<b/254526109>
     * So, we manage the list of stopping users, and block starting them again until they are
     * completely stopped. It is short term solution to solve the problem.
     */
    void addStoppingUserId(@UserIdInt int userId) {
        synchronized (mLock) {
            mStoppingUsers.add(userId);
        }
    }

    /**
     * Removes from the blocked list when completely stopped
     */
    void removeStoppingUserId(Integer userId) {
        synchronized (mLock) {
            mStoppingUsers.remove(userId);
        }
    }

    /**
     * check whether the user is on stopping.
     */
    boolean isStoppingUser(@UserIdInt int userId) {
        synchronized (mLock) {
            return mStoppingUsers.contains(userId);
        }
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        synchronized (mLock) {
            pw.println(TAG + " :");
            pw.print("  mUsersLoginStarted [userId-displayId] : ");
            for (int i = 0; i < mUsersLoginStarted.size(); i++) {
                int displayId = mUsersLoginStarted.keyAt(i);
                int userId = mUsersLoginStarted.valueAt(i);
                pw.printf("[%d-%d] ", userId, displayId);
            }
            pw.println();
            pw.print("  mStoppingUsers : ");
            if (mStoppingUsers.isEmpty()) {
                pw.print("None");
            } else {
                ArraySet<Integer> stoppingUsers = (ArraySet<Integer>) mStoppingUsers;
                for (int i = 0; i < stoppingUsers.size(); i++) {
                    pw.printf("%d ", stoppingUsers.valueAt(i));
                }
            }
            pw.println();
        }
    }
}
