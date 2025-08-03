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

package com.android.systemui.car.taskview;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarSystemUIProxy;
import android.car.app.CarTaskViewClient;
import android.car.app.CarTaskViewHost;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Process;
import android.util.Slog;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dump.DumpManager;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.taskview.TaskViewTransitions;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * This class provides a concrete implementation for {@link CarSystemUIProxy}. It hosts all the
 * system ui interaction that is required by other apps.
 */
@WMSingleton
public final class CarSystemUIProxyImpl
        implements CarSystemUIProxy, CarServiceProvider.CarServiceOnConnectedListener, Dumpable {
    private static final String TAG = CarSystemUIProxyImpl.class.getSimpleName();

    private final Context mContext;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final TaskViewTransitions mTaskViewTransitions;
    private final Set<RemoteCarTaskViewServerImpl> mRemoteCarTaskViewServerSet = new HashSet<>();
    private final DisplayManager mDisplayManager;

    private boolean mConnected;
    private CarActivityManager mCarActivityManager;

    /**
     * Returns true if {@link CarSystemUIProxyImpl} should be registered, false otherwise.
     * This could be false because of reasons like:
     * <ul>
     *     <li>Current user is not a system user.</li>
     *     <li>Or {@code config_registerCarSystemUIProxy} is disabled.</li>
     * </ul>
     */
    public static boolean shouldRegisterCarSystemUIProxy(Context context) {
        if (!Process.myUserHandle().isSystem()) {
            Slog.i(TAG, "Non system user.");
            return false;
        }
        if (!context.getResources().getBoolean(R.bool.config_registerCarSystemUIProxy)) {
            Slog.i(TAG, "config_registerCarSystemUIProxy disabled");
            return false;
        }
        return true;
    }

    @Inject
    CarSystemUIProxyImpl(
            Context context,
            CarServiceProvider carServiceProvider,
            SyncTransactionQueue syncTransactionQueue,
            ShellTaskOrganizer taskOrganizer,
            TaskViewTransitions taskViewTransitions,
            DumpManager dumpManager) {
        mContext = context;
        mTaskOrganizer = taskOrganizer;
        mSyncQueue = syncTransactionQueue;
        mTaskViewTransitions = taskViewTransitions;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        dumpManager.registerDumpable(this);

        if (!shouldRegisterCarSystemUIProxy(mContext)) {
            Slog.i(TAG, "Not registering CarSystemUIProxy.");
            return;
        }
        carServiceProvider.addListener(this);
    }

    boolean isLaunchRootTaskPresent(int displayId) {
        for (RemoteCarTaskViewServerImpl remoteCarTaskViewServer : mRemoteCarTaskViewServerSet) {
            if (remoteCarTaskViewServer.hasLaunchRootTaskOnDisplay(displayId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CarTaskViewHost createControlledCarTaskView(CarTaskViewClient carTaskViewClient) {
        return createCarTaskView(carTaskViewClient);
    }

    @Override
    public CarTaskViewHost createCarTaskView(CarTaskViewClient carTaskViewClient) {
        ensureManageSystemUIPermission(mContext);
        RemoteCarTaskViewServerImpl remoteCarTaskViewServerImpl =
                new RemoteCarTaskViewServerImpl(
                        mContext,
                        mTaskOrganizer,
                        mSyncQueue,
                        carTaskViewClient,
                        this,
                        mTaskViewTransitions, mCarActivityManager);
        mRemoteCarTaskViewServerSet.add(remoteCarTaskViewServerImpl);
        return remoteCarTaskViewServerImpl.getHostImpl();
    }

    void onCarTaskViewReleased(RemoteCarTaskViewServerImpl remoteCarTaskViewServer) {
        mRemoteCarTaskViewServerSet.remove(remoteCarTaskViewServer);
    }

    @Override
    public void onConnected(Car car) {
        mConnected = true;
        removeExistingTaskViewTasks();

        mCarActivityManager = car.getCarManager(CarActivityManager.class);
        mCarActivityManager.registerTaskMonitor();
        mCarActivityManager.registerCarSystemUIProxy(this);
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("  user:" + mContext.getUserId());
        pw.println("  shouldRegisterCarSystemUiProxy:" + shouldRegisterCarSystemUIProxy(mContext));
        pw.println("  mConnected:" + mConnected);
        pw.println("  mRemoteCarTaskViewServerSet size:" + mRemoteCarTaskViewServerSet.size());
        pw.println("  mRemoteCarTaskViewServerSet:");
        for (RemoteCarTaskViewServerImpl remoteCarTaskViewServer : mRemoteCarTaskViewServerSet) {
            pw.println("    " + remoteCarTaskViewServer);
        }
    }

    private void removeExistingTaskViewTasks() {
        Display[] displays = mDisplayManager.getDisplays();
        for (int i = 0; i < displays.length; i++) {
            List<ActivityManager.RunningTaskInfo> taskInfos =
                    mTaskOrganizer.getRunningTasks(displays[i].getDisplayId());
            removeMultiWindowTasks(taskInfos);
        }
    }

    private static void removeMultiWindowTasks(List<ActivityManager.RunningTaskInfo> taskInfos) {
        ActivityTaskManager atm = ActivityTaskManager.getInstance();
        for (ActivityManager.RunningTaskInfo taskInfo : taskInfos) {
            // In Auto, only TaskView tasks have WINDOWING_MODE_MULTI_WINDOW as of now.
            if (taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
                Slog.d(TAG, "Found a dangling task, removing: " + taskInfo.taskId);
                atm.removeTask(taskInfo.taskId);
            }
        }
    }

    static void ensureManageSystemUIPermission(Context context) {
        if (Binder.getCallingPid() == android.os.Process.myPid()) {
            // If called from within CarSystemUI, allow.
            return;
        }
        if (context.checkCallingPermission(Car.PERMISSION_MANAGE_CAR_SYSTEM_UI)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        throw new SecurityException("requires permission " + Car.PERMISSION_MANAGE_CAR_SYSTEM_UI);
    }
}
