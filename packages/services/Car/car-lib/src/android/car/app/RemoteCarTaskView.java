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
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.SurfaceControlHelper;
import android.car.builtin.view.TouchableInsetsProvider;
import android.car.builtin.view.ViewHelper;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link SurfaceView} that can embed a Task inside of it. The task management is done remotely
 * in a process that has registered a TaskOrganizer with the system server.
 * Usually this process is the Car System UI.
 *
 * @hide
 */
public abstract class RemoteCarTaskView extends SurfaceView {
    private static final String TAG = RemoteCarTaskView.class.getSimpleName();

    private final TouchableInsetsProvider mTouchableInsetsProvider;
    private final SurfaceCallbackHandler mSurfaceCallbackHandler = new SurfaceCallbackHandler();
    private final Rect mTmpRect = new Rect();
    private final AtomicBoolean mReleased = new AtomicBoolean(false);
    private boolean mInitialized = false;
    boolean mSurfaceCreated = false;
    private Region mObscuredTouchRegion;
    private ICarTaskViewHost mICarTaskViewHost;

    RemoteCarTaskView(Context context) {
        super(context);
        mTouchableInsetsProvider = new TouchableInsetsProvider(this);
        getHolder().addCallback(mSurfaceCallbackHandler);
    }

    /** Brings the embedded task to the front. Does nothing if there is no task. */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @MainThread
    public void showEmbeddedTask() {
        try {
            mICarTaskViewHost.showEmbeddedTask();
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in showEmbeddedTask", e);
        }
    }

    /**
     * Sets the visibility of the embedded task.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @MainThread
    public void setTaskVisibility(boolean visibility) {
        try {
            mICarTaskViewHost.setTaskVisibility(visibility);
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in setTaskVisibility", e);
        }
    }

    /**
     * Reorders the embedded task.
     *
     * @hide
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @MainThread
    public void reorderTask(boolean onTop) {
        try {
            mICarTaskViewHost.reorderTask(onTop);
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in reorderTask for task", e);
        }
    }

    /**
     * Updates the WM bounds for the underlying task as per the current view bounds. Does nothing
     * if there is no task.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @MainThread
    public void updateWindowBounds() {
        ViewHelper.getBoundsOnScreen(RemoteCarTaskView.this, mTmpRect);
        try {
            mICarTaskViewHost.setWindowBounds(mTmpRect);
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in setWindowBounds", e);
        }
    }

    /**
     * Sets the bounds of the window for the underlying Task.
     *
     * @param bounds the new bounds in screen coordinates.
     *
     * @hide
     */
    public void setWindowBounds(Rect bounds) {
        try {
            mICarTaskViewHost.setWindowBounds(bounds);
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in setWindowBounds", e);
        }
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRect the obscured region of the view.
     */
    @MainThread
    public void setObscuredTouchRect(@NonNull Rect obscuredRect) {
        mObscuredTouchRegion = obscuredRect != null ? new Region(obscuredRect) : null;
        mTouchableInsetsProvider.setObscuredTouchRegion(mObscuredTouchRegion);
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRegion the obscured region of the view.
     */
    @MainThread
    public void setObscuredTouchRegion(@NonNull Region obscuredRegion) {
        mObscuredTouchRegion = obscuredRegion;
        mTouchableInsetsProvider.setObscuredTouchRegion(mObscuredTouchRegion);
    }

    /**
     * @return the {@link android.app.ActivityManager.RunningTaskInfo} of the task currently
     * running in the TaskView.
     */
    @MainThread
    @Nullable public abstract ActivityManager.RunningTaskInfo getTaskInfo();

    /**
     * @return true, if the task view is initialized.
     */
    @MainThread
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * @return true, if the task view is released.
     *
     * @hide
     */
    @MainThread
    public boolean isReleased() {
        return mReleased.get();
    }

    /**
     * Adds the given insets on the Task.
     *
     * The given frame for the insets type are applied to the underlying task right away.
     * If a rectangle for an insets type was added previously, it will be replaced with the
     * new value.
     * If a rectangle for a insets type was already added, but is not specified currently in
     * {@code insets}, it will remain applied to the task. Clients should explicitly call
     * {@link #removeInsets(int, int)} to remove the rectangle for that insets type from
     * the underlying task.
     *
     * @param index An owner might add multiple insets sources with the same type.
     *              This identifies them.
     * @param type  The insets type of the insets source.
     * @param frame The rectangle area of the insets source.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    @MainThread
    public void addInsets(int index, int type, @NonNull Rect frame) {
        try {
            mICarTaskViewHost.addInsets(index, type, frame);
        } catch (RemoteException e) {
            Log.e(TAG, "exception in addInsets", e);
        }
    }

    /**
     * Removes the given insets from the Task.
     *
     * Note: This will only remove the insets that were added using
     * {@link #addInsets(int, int, Rect)}
     *
     * @param index An owner might add multiple insets sources with the same type.
     *              This identifies them.
     * @param type  The insets type of the insets source. This doesn't accept the composite types.
     */
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    public void removeInsets(int index, int type) {
        try {
            mICarTaskViewHost.removeInsets(index, type);
        } catch (RemoteException e) {
            Log.e(TAG, "exception in removeInsets", e);
        }
    }

    void setRemoteHost(@NonNull ICarTaskViewHost carTaskViewHost) {
        mICarTaskViewHost = carTaskViewHost;

        if (mSurfaceCreated) {
            if (!mInitialized) {
                onInitialized();
                mInitialized = true;
            }
        }
    }

    /**
     * Starts the activity from the given {@code PendingIntent}
     *
     * @param pendingIntent Intent used to launch an activity.
     * @param fillInIntent Additional Intent data, see {@link Intent#fillIn Intent.fillIn()}
     * @param options options for the activity.
     * @param launchBounds the bounds (window size and position) that the activity should be
     *                      launched in, in pixels and in screen coordinates.
     */
    void startActivity(
            @NonNull PendingIntent pendingIntent,
            @Nullable Intent fillInIntent,
            @NonNull ActivityOptions options,
            @Nullable Rect launchBounds) {
        try {
            mICarTaskViewHost.startActivity(
                    pendingIntent, fillInIntent, options.toBundle(), launchBounds);
        } catch (RemoteException exception) {
            Slogf.e(TAG, "exception in startActivity", exception);
        }
    }

    void createRootTask(int displayId) {
        try {
            mICarTaskViewHost.createRootTask(displayId);
        } catch (RemoteException exception) {
            Slogf.e(TAG, "exception in createRootTask", exception);
        }
    }

    void createLaunchRootTask(int displayId, boolean embedHomeTask, boolean embedRecentsTask,
            boolean embedAssistantTask) {
        try {
            mICarTaskViewHost.createLaunchRootTask(displayId, embedHomeTask, embedRecentsTask,
                    embedAssistantTask);
        } catch (RemoteException exception) {
            Slogf.e(TAG, "exception in createRootTask", exception);
        }
    }

    /** Release the resources associated with this task view. */
    @MainThread
    public void release() {
        getHolder().removeCallback(mSurfaceCallbackHandler);
        try {
            mReleased.set(true);
            mICarTaskViewHost.release();
        } catch (DeadObjectException e) {
            Slogf.w(TAG, "TaskView's host has already died", e);
        } catch (RemoteException e) {
            Slogf.e(TAG, "exception in release", e);
        }
        onReleased();
    }

    /**
     * Called when the task view is initialized. It is called only once for the lifetime of
     * taskview.
     */
    abstract void onInitialized();

    /**
     * Called when the task view is released. It is only called once for the lifetime of task view.
     */
    abstract void onReleased();

    /**
     * Called when the task has appeared in the taskview.
     *
     * @param taskInfo the taskInfo of the task that has appeared.
     * @param leash the suface control for the task surface.
     */
    void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
    }

    /**
     * Called when the task's info has changed.
     *
     * @param taskInfo the taskInfo of the task that has a change in info.
     */
    void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
    }

    /**
     * Called when the task has vanished.
     *
     * @param taskInfo the taskInfo of the task that has vanished.
     */
    void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTouchableInsetsProvider.addToViewTreeObserver();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mTouchableInsetsProvider.removeFromViewTreeObserver();
    }

    private class SurfaceCallbackHandler implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (mICarTaskViewHost != null) {
                if (!mInitialized) {
                    onInitialized();
                    mInitialized = true;
                }
            }
            mSurfaceCreated = true;
            try {
                mICarTaskViewHost.notifySurfaceCreated(
                        SurfaceControlHelper.copy(getSurfaceControl()));
            } catch (RemoteException e) {
                Slogf.e(TAG, "exception in notifySurfaceCreated", e);
            }
        }

        @Override
        public void surfaceChanged(
                @NonNull SurfaceHolder holder, int format, int width, int height) {
            try {
                ViewHelper.getBoundsOnScreen(RemoteCarTaskView.this, mTmpRect);
                mICarTaskViewHost.setWindowBounds(mTmpRect);
            } catch (RemoteException e) {
                Slogf.e(TAG, "exception in setWindowBounds", e);
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mSurfaceCreated = false;
            try {
                mICarTaskViewHost.notifySurfaceDestroyed();
            } catch (RemoteException e) {
                Slogf.e(TAG, "exception in notifySurfaceDestroyed", e);
            }
        }
    }
}
