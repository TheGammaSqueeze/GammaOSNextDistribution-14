/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car;

import static com.android.car.internal.common.CommonConstants.INVALID_PID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.app.CarActivityManager;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.view.Display;

import com.android.car.internal.ICarServiceHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility wrapper to access {@code ICarServiceHelper} inside car service
 */
public final class CarServiceHelperWrapper {

    private static final String TAG = CarServiceHelperWrapper.class.getSimpleName();

    // If car service helper is not available for more than this time, we will throw exception.
    private static final long CAR_SERVICE_HELPER_WAIT_TIME_MS = 20_000;

    private static final String REMOTE_EXCEPTION_STR = "CarServiceHelper threw RemoteException";

    private final long mCarServiceHelperWaitTimeoutMs;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    @Nullable
    private ICarServiceHelper mICarServiceHelper;

    @GuardedBy("mLock")
    @Nullable
    private ArrayList<Runnable> mConnectionRunnables;

    /**
     * Factory method for {@code ICarImpl}.
     */
    public static CarServiceHelperWrapper create() {
        return create(CAR_SERVICE_HELPER_WAIT_TIME_MS);
    }

    /** Factory method to create with non-default wait timeout. This is for testing only. */
    @VisibleForTesting
    public static CarServiceHelperWrapper create(long carServiceHelperWaitTimeoutMs) {
        CarServiceHelperWrapper wrapper = new CarServiceHelperWrapper(
                carServiceHelperWaitTimeoutMs);
        CarLocalServices.addService(CarServiceHelperWrapper.class, wrapper);
        return wrapper;
    }

    /**
     * Notifies CarServiceHelper connection. Only for {@code ICarImpl}.
     */
    public void setCarServiceHelper(@NonNull ICarServiceHelper carServiceHelper) {
        Objects.requireNonNull(carServiceHelper);

        ArrayList<Runnable> connectionRunnables;
        synchronized (mLock) {
            mICarServiceHelper = carServiceHelper;
            // This is thread safe as mConnectionRunnables is no longer accessed after connection.
            connectionRunnables = mConnectionRunnables;
            mConnectionRunnables = null;
            mLock.notifyAll();
        }
        if (connectionRunnables != null) {
            for (int i = 0; i < connectionRunnables.size(); i++) {
                connectionRunnables.get(i).run();
            }
        }
    }

    /**
     * Returns a singleton instance.
     */
    public static CarServiceHelperWrapper getInstance() {
        return CarLocalServices.getService(CarServiceHelperWrapper.class);
    }

    /**
     * Runs the passed {@code Runnable} when {@code CarServiceHelper} is connected. If it is already
     * connected, it will run inside the call.
     */
    public void runOnConnection(Runnable r) {
        boolean alreadyConnected;
        synchronized (mLock) {
            alreadyConnected = mICarServiceHelper != null;
            if (!alreadyConnected) {
                if (mConnectionRunnables == null) {
                    mConnectionRunnables = new ArrayList<>();
                }
                mConnectionRunnables.add(r);
            }
        }
        if (alreadyConnected) {
            r.run();
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setDisplayAllowlistForUser(@UserIdInt int userId, int[] displayIds) {
        try {
            waitForCarServiceHelper().setDisplayAllowlistForUser(userId, displayIds);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setPassengerDisplays(int[] displayIds) {
        try {
            waitForCarServiceHelper().setPassengerDisplays(displayIds);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setSafetyMode(boolean safe) {
        try {
            waitForCarServiceHelper().setSafetyMode(safe);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    @Nullable
    public UserHandle createUserEvenWhenDisallowed(String name, String userType, int flags) {
        try {
            return waitForCarServiceHelper().createUserEvenWhenDisallowed(name, userType, flags);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return null;
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public int setPersistentActivity(ComponentName activity, int displayId, int featureId) {
        try {
            return waitForCarServiceHelper().setPersistentActivity(activity, displayId, featureId);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return CarActivityManager.RESULT_FAILURE;
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setPersistentActivitiesOnRootTask(List<ComponentName> activities,
            IBinder rootTaskToken) {
        try {
            waitForCarServiceHelper().setPersistentActivitiesOnRootTask(activities, rootTaskToken);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void sendInitialUser(UserHandle user) {
        try {
            waitForCarServiceHelper().sendInitialUser(user);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setProcessGroup(int pid, int group) {
        try {
            waitForCarServiceHelper().setProcessGroup(pid, group);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public int getProcessGroup(int pid) {
        try {
            return waitForCarServiceHelper().getProcessGroup(pid);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return -1; // invalid id
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public int getMainDisplayAssignedToUser(int userId) {
        try {
            return waitForCarServiceHelper().getMainDisplayAssignedToUser(userId);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return Display.INVALID_DISPLAY;
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public int getUserAssignedToDisplay(int displayId) {
        try {
            return waitForCarServiceHelper().getUserAssignedToDisplay(displayId);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return UserManagerHelper.USER_NULL;
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public boolean startUserInBackgroundVisibleOnDisplay(int userId, int displayId) {
        try {
            return waitForCarServiceHelper().startUserInBackgroundVisibleOnDisplay(userId,
                    displayId);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return false;
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public void setProcessProfile(int pid, int uid, @NonNull String profile) {
        try {
            waitForCarServiceHelper().setProcessProfile(pid, uid, profile);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
    }

    /**
     * See {@code ICarServiceHelper}.
     */
    public int fetchAidlVhalPid() {
        try {
            return waitForCarServiceHelper().fetchAidlVhalPid();
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return INVALID_PID;
    }

    /**
     * @return true if a package requires launching in automotive compatibility mode.
     */
    public boolean requiresDisplayCompat(String packageName) {
        try {
            return waitForCarServiceHelper().requiresDisplayCompat(packageName);
        } catch (RemoteException e) {
            Slogf.e(TAG, REMOTE_EXCEPTION_STR, e);
        }
        return false;
    }

    private CarServiceHelperWrapper(long carServiceHelperWaitTimeoutMs) {
        mCarServiceHelperWaitTimeoutMs = carServiceHelperWaitTimeoutMs;
    }

    @SuppressWarnings("WaitNotInLoop")
    private ICarServiceHelper waitForCarServiceHelper() {
        synchronized (mLock) {
            if (mICarServiceHelper == null) {
                try {
                    // This only wait once as timeout will crash the car service with exception.
                    mLock.wait(mCarServiceHelperWaitTimeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Slogf.w(TAG, "Thread was interrupted while waiting for CarServiceHelper",
                            e);
                    // just continue as we cannot return null instance.
                }
                if (mICarServiceHelper == null) {
                    throw new IllegalStateException("CarServiceHelper did not connect");
                }
            }
            return mICarServiceHelper;
        }
    }
}
