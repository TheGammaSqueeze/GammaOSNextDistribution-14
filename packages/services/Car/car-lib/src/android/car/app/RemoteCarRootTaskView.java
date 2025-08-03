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

package android.car.app;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.car.Car;
import android.car.builtin.app.TaskInfoHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.ViewHelper;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.view.SurfaceControl;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A {@link RemoteCarRootTaskView} should be used when a given list of activities are required to
 * appear inside the bounds of a the given {@link android.view.View} in the form of a task stack.
 * This {@link RemoteCarTaskView} creates a root task and all the given activities will launch
 * inside that root task.
 *
 * <p>It serves these use-cases:
 * <ul>
 *     <li>Should be used when the apps that are meant to be in it can be started from anywhere
 *     in the system. i.e. when the host app has no control over their launching.</li>
 *     <li>Suitable for apps like Assistant or Setup-Wizard.</li>
 * </ul>
 *
 * @hide
 */
public final class RemoteCarRootTaskView extends RemoteCarTaskView {
    private static final String TAG = RemoteCarRootTaskView.class.getSimpleName();

    private final Executor mCallbackExecutor;
    private final RemoteCarRootTaskViewCallback mCallback;
    private final ICarActivityService mCarActivityService;
    private final CarTaskViewController mCarTaskViewController;
    private final Rect mTmpRect = new Rect();
    private final RootTaskStackManager mRootTaskStackManager = new RootTaskStackManager();
    private final Object mLock = new Object();
    private final int mDisplayId;
    /**
     * List of activities that appear in this {@link RemoteCarRootTaskView}. It's initialized
     * with the value from {@link RemoteCarRootTaskViewConfig#getAllowListedActivities()} and
     * can be updated by {@link #updateAllowListedActivities(List)}.
     */
    @GuardedBy("mLock")
    private final ArrayList<ComponentName> mAllowListedActivities;

    @GuardedBy("mLock")
    private ActivityManager.RunningTaskInfo mRootTask;

    final ICarTaskViewClient mICarTaskViewClient = new ICarTaskViewClient.Stub() {
        @Override
        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            synchronized (mLock) {
                if (mRootTask == null) {
                    mRootTask = taskInfo;
                    setPersistentActivitiesOnRootTask(mAllowListedActivities,
                            TaskInfoHelper.getToken(taskInfo));
                    // If onTaskAppeared() is called, it implicitly means that
                    // super.isInitialized() is true, as the root task is created only after
                    // initialization.
                    final long identity = Binder.clearCallingIdentity();
                    try {
                        mCallbackExecutor.execute(() -> {
                            // Check for isReleased() because the car task view might have
                            // already been released but this code path is executed later because
                            // the executor was busy.
                            if (isReleased()) {
                                Slogf.w(TAG, "car task view has already been released");
                                return;
                            }
                            mCallback.onTaskViewInitialized();
                        });
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }

                    if (taskInfo.taskDescription != null) {
                        ViewHelper.seResizeBackgroundColor(
                                RemoteCarRootTaskView.this,
                                taskInfo.taskDescription.getBackgroundColor());
                    }
                    updateWindowBounds();
                }
            }

            mRootTaskStackManager.taskAppeared(taskInfo, leash);
        }

        @Override
        public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
            synchronized (mLock) {
                if (mRootTask == null) {
                    return;
                }
                if (mRootTask.taskId == taskInfo.taskId && taskInfo.taskDescription != null) {
                    ViewHelper.seResizeBackgroundColor(
                            RemoteCarRootTaskView.this,
                            taskInfo.taskDescription.getBackgroundColor());
                }
            }
            mRootTaskStackManager.taskInfoChanged(taskInfo);
        }

        @Override
        public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            synchronized (mLock) {
                if (mRootTask == null) {
                    return;
                }
                if (mRootTask.taskId == taskInfo.taskId) {
                    mRootTask = null;
                }
            }
            mRootTaskStackManager.taskVanished(taskInfo);
        }

        @Override
        public void setResizeBackgroundColor(SurfaceControl.Transaction t, int color) {
            ViewHelper.seResizeBackgroundColor(RemoteCarRootTaskView.this, color);
        }

        @Override
        public Rect getCurrentBoundsOnScreen() {
            ViewHelper.getBoundsOnScreen(RemoteCarRootTaskView.this, mTmpRect);
            return mTmpRect;
        }
    };

    RemoteCarRootTaskView(
            @NonNull Context context,
            RemoteCarRootTaskViewConfig config,
            @NonNull Executor callbackExecutor,
            @NonNull RemoteCarRootTaskViewCallback callback,
            CarTaskViewController carTaskViewController,
            @NonNull ICarActivityService carActivityService) {
        super(context);
        mCallbackExecutor = callbackExecutor;
        mCallback = callback;
        mCarTaskViewController = carTaskViewController;
        mCarActivityService = carActivityService;
        synchronized (mLock) {
            mAllowListedActivities = new ArrayList<>(config.getAllowListedActivities());
        }
        mDisplayId = config.getDisplayId();

        mCallbackExecutor.execute(() -> mCallback.onTaskViewCreated(this));
    }

    /**
     * Returns the task info of the top task running in the root task embedded in this task view.
     *
     * @return task info object of the top task.
     */
    @Nullable
    public ActivityManager.RunningTaskInfo getTopTaskInfo() {
        return mRootTaskStackManager.getTopTask();
    }

    @Override
    void onInitialized() {
        // A signal when the surface and host-side are ready. This task view initialization
        // completes after root task has been created.
        createRootTask(mDisplayId);
    }

    @Override
    public boolean isInitialized() {
        synchronized (mLock) {
            return super.isInitialized() && mRootTask != null;
        }
    }

    @Override
    void onReleased() {
        mCallbackExecutor.execute(() -> mCallback.onTaskViewReleased());
        mCarTaskViewController.onRemoteCarTaskViewReleased(this);
    }

    @Nullable
    @Override
    public ActivityManager.RunningTaskInfo getTaskInfo() {
        synchronized (mLock) {
            return mRootTask;
        }
    }

    @Override
    public String toString() {
        return toString(/* withBounds= */ false);
    }

    /**
     * Updates the list of activities that appear inside this {@link RemoteCarRootTaskView}.
     *
     * <p>Note:
     * If an activity is already associated with another {@link RemoteCarRootTaskView}, its
     * designation will be overridden.
     *
     * @param list list of {@link ComponentName} of activities to be designated to this
     *                   {@link RemoteCarRootTaskView}
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    @MainThread
    public void updateAllowListedActivities(List<ComponentName> list) {
        synchronized (mLock) {
            if (mRootTask != null) {
                List<ComponentName> activitiesToRemove = findDifferences(mAllowListedActivities,
                        list);
                List<ComponentName> activitiesToAdd = findDifferences(list, mAllowListedActivities);
                setPersistentActivitiesOnRootTask(activitiesToRemove, /* launchCookie= */ null);
                setPersistentActivitiesOnRootTask(activitiesToAdd,
                        TaskInfoHelper.getToken(mRootTask));
            }

            mAllowListedActivities.clear();
            mAllowListedActivities.addAll(list);
        }
    }

    /**
     * Returns all the {@link ComponentName}s in {@code firstList} which are not in
     * {@code secondList}.
     */
    private List<ComponentName> findDifferences(List<ComponentName> firstList,
            List<ComponentName> secondList) {
        ArrayList<ComponentName> result = new ArrayList<>(firstList);
        result.removeAll(secondList);
        return result;
    }

    private void setPersistentActivitiesOnRootTask(List<ComponentName> activities,
            IBinder launchCookie) {
        try {
            mCarActivityService.setPersistentActivitiesOnRootTask(activities, launchCookie);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            throw e;
        } catch (ServiceSpecificException e) {
            throw new IllegalStateException(
                "Car service looks crashed on ServiceSpecificException " + e);
        } catch (RemoteException | RuntimeException e) {
            throw new IllegalStateException("Car service looks crashed on RemoteException " + e);
        }
    }

    String toString(boolean withBounds) {
        if (withBounds) {
            ViewHelper.getBoundsOnScreen(this, mTmpRect);
        }

        StringBuilder b = new StringBuilder(TAG).append(" {\n");

        b.append("  mDisplayId=").append(mDisplayId);
        b.append("\n");

        b.append("  taskId=").append((getTaskInfo() == null ? "null" : getTaskInfo().taskId));
        b.append("\n");

        if (withBounds) {
            b.append("  boundsOnScreen=").append(mTmpRect);
            b.append("\n");
        }

        b.append("  mAllowListedActivities= [");
        synchronized (mLock) {
            for (ComponentName componentName : mAllowListedActivities) {
                b.append("\n    ").append(componentName);
            }
        }
        b.append("  ]}\n");

        return b.toString();
    }
}
