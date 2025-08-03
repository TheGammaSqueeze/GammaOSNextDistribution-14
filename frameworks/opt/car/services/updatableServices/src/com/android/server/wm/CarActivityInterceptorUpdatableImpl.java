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

package com.android.server.wm;

import static android.view.Display.INVALID_DISPLAY;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ActivityOptions;
import android.car.builtin.util.Slogf;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link CarActivityInterceptorUpdatable}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class CarActivityInterceptorUpdatableImpl implements CarActivityInterceptorUpdatable {
    public static final String TAG = CarActivityInterceptorUpdatableImpl.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final ArrayMap<ComponentName, IBinder> mActivityToRootTaskMap = new ArrayMap<>();
    @GuardedBy("mLock")
    private final Set<IBinder> mKnownRootTasks = new ArraySet<>();
    @NonNull
    private final CarActivityInterceptorInterface mBuiltIn;
    @GuardedBy("mLock")
    @NonNull
    private final SparseArray<CarActivityInterceptorUpdatable> mInterceptors = new SparseArray<>();

    public CarActivityInterceptorUpdatableImpl(
            @NonNull CarActivityInterceptorInterface builtInInterface) {
        mBuiltIn = builtInInterface;
    }

    @Override
    public ActivityInterceptResultWrapper onInterceptActivityLaunch(
            ActivityInterceptorInfoWrapper info) {
        if (info.getIntent() == null) {
            return null;
        }
        ComponentName componentName = info.getIntent().getComponent();

        synchronized (mLock) {
            int keyIndex = mActivityToRootTaskMap.indexOfKey(componentName);
            if (keyIndex >= 0) {
                IBinder rootTaskToken = mActivityToRootTaskMap.valueAt(keyIndex);
                if (!isRootTaskUserSameAsActivityUser(rootTaskToken, info)) {
                    return null;
                }

                ActivityOptionsWrapper optionsWrapper = info.getCheckedOptions();
                if (optionsWrapper == null) {
                    optionsWrapper = ActivityOptionsWrapper.create(ActivityOptions.makeBasic());
                }

                // Even if the activity is assigned a root task to open in, the launch display ID
                // should take preference when opening the activity. More details in b/295893892.
                if (!isRootTaskDisplayIdSameAsLaunchDisplayId(rootTaskToken, optionsWrapper)) {
                    return null;
                }

                optionsWrapper.setLaunchRootTask(rootTaskToken);
                return ActivityInterceptResultWrapper.create(info.getIntent(),
                        optionsWrapper.getOptions());
            }

            for (int i = 0; i < mInterceptors.size(); i++) {
                CarActivityInterceptorUpdatable interceptor = mInterceptors.valueAt(i);
                ActivityInterceptResultWrapper result = interceptor.onInterceptActivityLaunch(info);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Register a {@link CarActivityInterceptorUpdatable} at the given index
     *
     * @param index the index at which the interceptor will get called.
     * lower numbers have higher priority. It cannot be negative.
     */
    public void registerInterceptor(int index,
            @NonNull CarActivityInterceptorUpdatable interceptor) {
        synchronized (mLock) {
            if (mInterceptors.contains(index)) {
                throw new IllegalArgumentException("Another interceptor is registered at index: "
                        + index);
            }
            mInterceptors.put(index, interceptor);
        }
    }

    /**
     * Unregisters a {@link CarActivityInterceptorUpdatable}
     */
    public void unregisterInterceptor(int index) {
        synchronized (mLock) {
            if (!mInterceptors.contains(index)) {
                throw new IllegalArgumentException(
                        "No interceptor is registered at index: " + index);
            }
            mInterceptors.remove(index);
        }
    }

    private boolean isRootTaskDisplayIdSameAsLaunchDisplayId(IBinder rootTaskToken,
            ActivityOptionsWrapper optionsWrapper) {
        int launchDisplayId = optionsWrapper.getOptions().getLaunchDisplayId();
        if (launchDisplayId == INVALID_DISPLAY) {
            if (DBG) {
                Slogf.d(TAG,
                        "The launch display Id of the activity is unset, let it open root task");
            }
            return true;
        }
        TaskWrapper rootTask = TaskWrapper.createFromToken(rootTaskToken);
        int rootTaskDisplayId = rootTask.getTaskDisplayArea().getDisplay().getDisplayId();
        if (launchDisplayId == rootTaskDisplayId) {
            if (DBG) {
                Slogf.d(TAG, "The launch display Id of the activity is (%d)", launchDisplayId);
            }
            return true;
        }
        if (DBG) {
            Slogf.d(TAG,
                    "The launch display Id (%d) of the activity doesn't match the display Id (%d)"
                            + " (which the root task is added in).",
                    launchDisplayId, rootTaskDisplayId);
        }
        return false;
    }

    private boolean isRootTaskUserSameAsActivityUser(IBinder rootTaskToken,
            ActivityInterceptorInfoWrapper activityInterceptorInfoWrapper) {
        TaskWrapper rootTask = TaskWrapper.createFromToken(rootTaskToken);
        if (rootTask == null) {
            Slogf.w(TAG, "Root task not found.");
            return false;
        }
        if (rootTask.getTaskDisplayArea() == null) {
            Slogf.w(TAG, "Root task's DisplayArea not found.");
            return false;
        }
        int userIdFromActivity = activityInterceptorInfoWrapper.getUserId();
        int userIdFromRootTask = mBuiltIn.getUserAssignedToDisplay(rootTask
                .getTaskDisplayArea().getDisplay().getDisplayId());
        if (userIdFromActivity == userIdFromRootTask) {
            return true;
        }
        if (DBG) {
            Slogf.d(TAG,
                    "The user id of launched activity (%d) doesn't match the user id which the "
                            + "display (which the root task is added in) is assigned to (%d).",
                    userIdFromActivity, userIdFromRootTask);
        }
        return false;
    }

    /**
     * Sets the given {@code activities} to be persistent on the root task corresponding to the
     * given {@code rootTaskToken}.
     * <p>
     * If {@code rootTaskToken} is {@code null}, then the earlier root task associations of the
     * given {@code activities} will be removed.
     *
     * @param activities    the list of activities which have to be persisted.
     * @param rootTaskToken the binder token of the root task which the activities have to be
     *                      persisted on.
     */
    public void setPersistentActivityOnRootTask(@NonNull List<ComponentName> activities,
            IBinder rootTaskToken) {
        synchronized (mLock) {
            if (rootTaskToken == null) {
                int activitiesNum = activities.size();
                for (int i = 0; i < activitiesNum; i++) {
                    mActivityToRootTaskMap.remove(activities.get(i));
                }
                return;
            }

            int activitiesNum = activities.size();
            for (int i = 0; i < activitiesNum; i++) {
                mActivityToRootTaskMap.put(activities.get(i), rootTaskToken);
            }
            if (!mKnownRootTasks.contains(rootTaskToken)) {
                // Seeing the token for the first time, set the listener
                removeRootTaskTokenOnDeath(rootTaskToken);
                mKnownRootTasks.add(rootTaskToken);
            }
        }
    }

    private void removeRootTaskTokenOnDeath(IBinder rootTaskToken) {
        try {
            rootTaskToken.linkToDeath(() -> removeRootTaskToken(rootTaskToken), /* flags= */ 0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeRootTaskToken(IBinder rootTaskToken) {
        synchronized (mLock) {
            mKnownRootTasks.remove(rootTaskToken);
            // remove all the persistent activities for this root task token from the map,
            // because the root task itself is removed.
            Iterator<Map.Entry<ComponentName, IBinder>> iterator =
                    mActivityToRootTaskMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ComponentName, IBinder> entry = iterator.next();
                if (entry.getValue().equals(rootTaskToken)) {
                    iterator.remove();
                }
            }
        }
    }

    @VisibleForTesting
    public Map<ComponentName, IBinder> getActivityToRootTaskMap() {
        synchronized (mLock) {
            return mActivityToRootTaskMap;
        }
    }

    /**
     * Dump {code CarActivityInterceptorUpdatableImpl#mActivityToRootTaskMap}
     */
    public void dump(IndentingPrintWriter writer) {
        writer.println(TAG);
        writer.increaseIndent();
        writer.println("Activity to root task map:");
        writer.increaseIndent();
        synchronized (mLock) {
            if (mActivityToRootTaskMap.size() == 0) {
                writer.println("No activity persisted on a root task");
            } else {
                for (int i = 0; i < mActivityToRootTaskMap.size(); i++) {
                    writer.println("Activity name: " + mActivityToRootTaskMap.keyAt(i)
                            + " - Binder object: " + mActivityToRootTaskMap.valueAt(i));
                }
            }
        }
        writer.decreaseIndent();
        writer.println("Activity interceptors:");
        writer.increaseIndent();
        synchronized (mLock) {
            if (mInterceptors.size() == 0) {
                writer.println("No registered activity interceptors.");
            } else {
                for (int i = 0; i < mInterceptors.size(); i++) {
                    CarActivityInterceptorUpdatable interceptor = mInterceptors.valueAt(i);
                    writer.println("Interceptor " + interceptor + " is registered at index " + i);
                }
            }
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }
}
