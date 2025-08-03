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

package android.car.app;


import static android.Manifest.permission.INTERACT_ACROSS_USERS;

import android.annotation.IntDef;
import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.UiContext;
import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.user.CarUserManager;
import android.car.view.MirroredSurfaceView;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * API to manage {@link android.app.Activity} in Car.
 *
 * @hide
 */
@SystemApi
public final class CarActivityManager extends CarManagerBase {
    private static final String TAG = CarActivityManager.class.getSimpleName();

    /** Indicates that the operation was successful. */
    public static final int RESULT_SUCCESS = 0;
    /** Indicates that the operation was failed with the unknown reason. */
    public static final int RESULT_FAILURE = -1;
    /**
     * Indicates that the operation was failed because the requester isn't the current user or
     * the system user
     */
    public static final int RESULT_INVALID_USER = -2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "RESULT_", value = {
            RESULT_SUCCESS,
            RESULT_FAILURE,
            RESULT_INVALID_USER,
    })
    @Target({ElementType.TYPE_USE})
    public @interface ResultTypeEnum {}

    /**
     * Internal error code for throwing {@link ActivityNotFoundException} from service.
     * @hide
     */
    public static final int ERROR_CODE_ACTIVITY_NOT_FOUND = -101;

    private final ICarActivityService mService;
    private IBinder mTaskMonitorToken;
    private CarTaskViewControllerSupervisor mCarTaskViewControllerSupervisor;

    /**
     * @hide
     */
    public CarActivityManager(@NonNull Car car, @NonNull IBinder service) {
        this(car, ICarActivityService.Stub.asInterface(service));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public CarActivityManager(@NonNull Car car, @NonNull ICarActivityService service) {
        super(car);
        mService = service;
    }

    /**
     * Designates the given {@code activity} to be launched in {@code TaskDisplayArea} of
     * {@code featureId} in the display of {@code displayId}.
     * <p>Note: this will not affect the existing {@link Activity}.
     * Note: You can map assign {@code Activity} to one {@code TaskDisplayArea} only. If
     * you assign it to the multiple {@code TaskDisplayArea}s, then the last one wins.
     * Note: The requester should be the current user or the system user, if not, the operation will
     * be failed with {@code RESULT_INVALID_USER}.
     *
     * @param activity {@link Activity} to designate
     * @param displayId {@code Display} where {@code TaskDisplayArea} is located in
     * @param featureId {@code TaskDisplayArea} where {@link Activity} is launched in, if it is
     *         {@code DisplayAreaOrganizer.FEATURE_UNDEFINED}, then it'll remove the existing one.
     * @return {@code ResultTypeEnum}. {@code RESULT_SUCCESS} if the operation is successful,
     *         otherwise, {@code RESULT_XXX} depending on the type of the error.
     * @throws {@link IllegalArgumentException} if {@code displayId} or {@code featureId} is
     *         invalid. {@link ActivityNotFoundException} if {@code activity} is not found
     *         when it tries to remove.
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    @ResultTypeEnum
    public int setPersistentActivity(
            @NonNull ComponentName activity, int displayId, int featureId) {
        try {
            return mService.setPersistentActivity(activity, displayId, featureId);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            throw e;
        } catch (ServiceSpecificException e) {
            return handleServiceSpecificFromCarService(e);
        } catch (RemoteException | RuntimeException e) {
            return handleExceptionFromCarService(e, RESULT_FAILURE);
        }
    }

    /**
     * Designates the given {@code activities} to be launched in the root task associated with the
     * given {@code rootTaskToken}.
     * <p>Note: If an activity is already persisted on a root task, it will be overridden by the
     * {@code rootTaskToken} supplied in the latest call.
     * <p>Note: If {@code rootTaskToken} is null, the designation will be removed and the given
     * activities will follow default behavior.
     *
     * @param activities list of {@link ComponentName} of activities to be designated on the
     *                   root task.
     * @param rootTaskToken the binder token of the root task.
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    public void setPersistentActivitiesOnRootTask(@NonNull List<ComponentName> activities,
            @Nullable IBinder rootTaskToken) {
        try {
            mService.setPersistentActivitiesOnRootTask(activities, rootTaskToken);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            throw e;
        }  catch (RemoteException | RuntimeException e) {
            handleExceptionFromCarService(e, RESULT_FAILURE);
        }
    }

    /** @hide */
    @Override
    protected void onCarDisconnected() {
        mTaskMonitorToken = null;
    }

    private int handleServiceSpecificFromCarService(ServiceSpecificException e)
            throws ActivityNotFoundException {
        if (e.errorCode == ERROR_CODE_ACTIVITY_NOT_FOUND) {
            throw new ActivityNotFoundException(e.getMessage());
        }
        // don't know what this is
        throw new IllegalStateException(e);
    }

    /**
     * Registers the caller as TaskMonitor, which can provide Task lifecycle events to CarService.
     * The caller should provide a binder token, which is used to check if the given TaskMonitor is
     * live and the reported events are from the legitimate TaskMonitor.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public boolean registerTaskMonitor() {
        Preconditions.checkState(
                mTaskMonitorToken == null, "Can't register the multiple TaskMonitors");
        IBinder token = new Binder();
        try {
            mService.registerTaskMonitor(token);
            mTaskMonitorToken = token;
            return true;
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return false;
    }

    /**
     * Reports that a Task is created.
     * @deprecated Use {@link #onTaskAppeared(ActivityManager.RunningTaskInfo, SurfaceControl)}
     * @hide
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
        onTaskAppearedInternal(taskInfo, null);
    }

    /**
     * Reports that a Task is created.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                @Nullable SurfaceControl leash) {
        onTaskAppearedInternal(taskInfo, leash);
    }

    private void onTaskAppearedInternal(
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskAppeared(mTaskMonitorToken, taskInfo, leash);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Reports that a Task is vanished.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskVanished(mTaskMonitorToken, taskInfo);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Reports that some Task's states are changed.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskInfoChanged(mTaskMonitorToken, taskInfo);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Unregisters the caller from TaskMonitor.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void unregisterTaskMonitor() {
        if (!hasValidToken()) return;
        try {
            mService.unregisterTaskMonitor(mTaskMonitorToken);
            mTaskMonitorToken = null;
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns all the visible tasks in the all displays. The order is not guaranteed.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @NonNull
    public List<ActivityManager.RunningTaskInfo> getVisibleTasks() {
        try {
            return mService.getVisibleTasks(Display.INVALID_DISPLAY);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return Collections.emptyList();
    }

    /**
     * Returns all the visible tasks in the given display. The order is not guaranteed.
     *
     * @param displayId the id of {@link Display} to retrieve the tasks,
     *         {@link Display.INVALID_DISPLAY} to retrieve the tasks in the all displays.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @NonNull
    public List<ActivityManager.RunningTaskInfo> getVisibleTasks(int displayId) {
        try {
            return mService.getVisibleTasks(displayId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return Collections.emptyList();
    }

    /**
     * Starts user picker UI (=user selection UI) to the given display.
     *
     * <p>User picker UI will run as {@link android.os.UserHandle#SYSTEM} user.
     */
    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void startUserPickerOnDisplay(int displayId) {
        try {
            mService.startUserPickerOnDisplay(displayId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Creates the mirroring token of the given Task.
     *
     * @param taskId The Task to mirror.
     * @return A token to access the Task Surface. The token is used to identify the target
     *     Task's Surface for {@link MirroredSurfaceView}.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @Nullable
    public IBinder createTaskMirroringToken(int taskId) {
        try {
            return mService.createTaskMirroringToken(taskId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, /* returnValue= */ null);
        }
    }

    /**
     * Creates the mirroring token of the given Display.
     *
     * @param displayId The Display to mirror.
     * @return A token to access the Display Surface. The token is used to identify the target
     *     Display's Surface for {@link MirroredSurfaceView}.
     */
    @RequiresPermission(Car.PERMISSION_MIRROR_DISPLAY)
    @Nullable
    public IBinder createDisplayMirroringToken(int displayId) {
        try {
            return mService.createDisplayMirroringToken(displayId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, /* returnValue= */ null);
        }
    }

    /**
     * Gets a mirrored {@link SurfaceControl} of the Task identified by the given Token.
     *
     * @param token  The token to access the Surface.
     * @return A Pair of {@link SurfaceControl} and the bounds of the mirrored Task,
     *     or {code null} if it can't find the target Surface to mirror.
     *
     * @hide Used by {@link MirroredSurfaceView} only.
     */
    @RequiresPermission(Car.PERMISSION_ACCESS_MIRRORRED_SURFACE)
    @Nullable
    public Pair<SurfaceControl, Rect> getMirroredSurface(@NonNull IBinder token) {
        try {
            Rect outBounds = new Rect();
            // SurfaceControl constructor is hidden api, so we can get it by the return value.
            SurfaceControl sc = mService.getMirroredSurface(token, outBounds);
            if (sc == null) {
                return null;
            }
            return Pair.create(sc, outBounds);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, /* returnValue= */ null);
        }
    }

    /**
     * Registers a system ui proxy which will be used by the client apps to interact with the
     * system-ui for things like creating task views, getting notified about immersive mode
     * request, etc.
     *
     * <p>This is meant to be called only by the SystemUI.
     *
     * @param carSystemUIProxy the implementation of the {@link CarSystemUIProxy}.
     * @throws UnsupportedOperationException when called more than once for the same SystemUi
     *         process.
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_REGISTER_CAR_SYSTEM_UI_PROXY)
    public void registerCarSystemUIProxy(@NonNull CarSystemUIProxy carSystemUIProxy) {
        try {
            mService.registerCarSystemUIProxy(new CarSystemUIProxyAidlWrapper(carSystemUIProxy));
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns true if the {@link CarSystemUIProxy} is registered, false otherwise.
     *
     * @hide
     */
    @SystemApi
    public boolean isCarSystemUIProxyRegistered() {
        try {
            return mService.isCarSystemUIProxyRegistered();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return false;
        }
    }

    /**
     * Gets the {@link CarTaskViewController} using the {@code carTaskViewControllerCallback}.
     *
     * This method is expected to be called from the {@link Activity#onCreate(Bundle)}. It will
     * take care of freeing up the held resources when activity is destroyed. If an activity is
     * recreated, it should be called again in the next {@link Activity#onCreate(Bundle)}.
     *
     * @param carTaskViewControllerCallback the callback which the client can use to monitor the
     *                                      lifecycle of the {@link CarTaskViewController}.
     * @param hostActivity the activity that will host the taskviews.
     * @hide
     */
    @SystemApi
    @RequiresPermission(allOf = {Car.PERMISSION_MANAGE_CAR_SYSTEM_UI, INTERACT_ACROSS_USERS})
    @MainThread
    public void getCarTaskViewController(
            @NonNull Activity hostActivity,
            @NonNull Executor callbackExecutor,
            @NonNull CarTaskViewControllerCallback carTaskViewControllerCallback) {
        getCarTaskViewController(
                hostActivity,
                CarTaskViewControllerHostLifecycleFactory.forActivity(hostActivity),
                callbackExecutor,
                carTaskViewControllerCallback);
    }

    /**
     * Gets the {@link CarTaskViewController} using the {@code carTaskViewControllerCallback}.
     *
     * This method is expected to be called when the container (host) is created. It will
     * take care of freeing up the held resources when container is destroyed. If the container is
     * recreated, this method should be called again after it gets created again.
     *
     * @param carTaskViewControllerCallback the callback which the client can use to monitor the
     *                                      lifecycle of the {@link CarTaskViewController}.
     * @param hostContext the visual hostContext which the container (host) is associated with.
     * @param callbackExecutor the executor which the {@code carTaskViewControllerCallback} will be
     *                         executed on.
     * @param carTaskViewControllerHostLifecycle the lifecycle of the container (host).
     * @hide
     */
    // TODO(b/293297847): Expose this as system API
    @RequiresPermission(allOf = {Car.PERMISSION_MANAGE_CAR_SYSTEM_UI, INTERACT_ACROSS_USERS})
    @MainThread
    public void getCarTaskViewController(
            @UiContext @NonNull Context hostContext,
            @NonNull CarTaskViewControllerHostLifecycle carTaskViewControllerHostLifecycle,
            @NonNull Executor callbackExecutor,
            @NonNull CarTaskViewControllerCallback carTaskViewControllerCallback) {
        try {
            if (mCarTaskViewControllerSupervisor == null) {
                // Same supervisor is used for multiple activities.
                mCarTaskViewControllerSupervisor = new CarTaskViewControllerSupervisor(mService,
                        getContext().getMainExecutor(), mCar.getCarManager(CarUserManager.class));
            }
            mCarTaskViewControllerSupervisor.createCarTaskViewController(
                    hostContext,
                    carTaskViewControllerHostLifecycle,
                    callbackExecutor,
                    carTaskViewControllerCallback);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Moves the given {@code RootTask} with its child {@code Activties} to the specified
     * {@code Display}.
     * @param taskId the id of the target {@code RootTask} to move
     * @param displayId the displayId to move the {@code RootTask} to
     * @throws IllegalArgumentException if the given {@code taskId} or {@code displayId} is invalid
     * @throws IllegalArgumentException if the given {@code RootTask} is already in the given
     *     {@code Display}
     * Note: the operation can be failed if the given {@code Display} doesn't allow for the type of
     * the given {@code RootTask} to be launched.
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    public void moveRootTaskToDisplay(int taskId, int displayId) {
        try {
            mService.moveRootTaskToDisplay(taskId, displayId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    private boolean hasValidToken() {
        boolean valid = mTaskMonitorToken != null;
        if (!valid) {
            Log.w(TAG, "Has invalid token, skip the operation: "
                    + new Throwable().getStackTrace()[1].getMethodName());
        }
        return valid;
    }
}
