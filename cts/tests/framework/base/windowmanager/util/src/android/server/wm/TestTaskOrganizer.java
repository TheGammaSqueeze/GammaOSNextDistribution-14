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

package android.server.wm;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TaskAppearedInfo;
import android.window.TaskOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class TestTaskOrganizer extends TaskOrganizer {
    private static final String TAG = TestTaskOrganizer.class.getSimpleName();
    public static final int INVALID_TASK_ID = -1;

    private boolean mRegistered;
    private ActivityManager.RunningTaskInfo mRootPrimary;
    private ActivityManager.RunningTaskInfo mRootSecondary;
    @Nullable private ActivityManager.RunningTaskInfo mPrePrimarySplitTaskInfo;
    @Nullable private ActivityManager.RunningTaskInfo mPreSecondarySplitTaskInfo;
    private IBinder mPrimaryCookie;
    private IBinder mSecondaryCookie;
    private final HashMap<Integer, ActivityManager.RunningTaskInfo> mKnownTasks = new HashMap<>();
    private final HashMap<Integer, SurfaceControl> mTaskLeashes = new HashMap<>();
    private final ArraySet<Integer> mPrimaryChildrenTaskIds = new ArraySet<>();
    private final ArraySet<Integer> mSecondaryChildrenTaskIds = new ArraySet<>();
    private final Rect mPrimaryBounds = new Rect();
    private final Rect mSecondaryBounds = new Rect();

    private static final int[] CONTROLLED_ACTIVITY_TYPES = {
            ACTIVITY_TYPE_STANDARD,
            ACTIVITY_TYPE_HOME,
            ACTIVITY_TYPE_RECENTS,
            ACTIVITY_TYPE_UNDEFINED
    };
    private static final int[] CONTROLLED_WINDOWING_MODES = {
            WINDOWING_MODE_FULLSCREEN,
            WINDOWING_MODE_MULTI_WINDOW,
            WINDOWING_MODE_UNDEFINED
    };

    @Override
    public List<TaskAppearedInfo> registerOrganizer() {
        final Context context = getInstrumentation().getContext();
        final Rect bounds = context.createDisplayContext(
                context.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY))
                .createWindowContext(TYPE_APPLICATION, null /* options */)
                .getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();

        final boolean isLandscape = bounds.width() > bounds.height();
        if (isLandscape) {
            bounds.splitVertically(mPrimaryBounds, mSecondaryBounds);
        } else {
            bounds.splitHorizontally(mPrimaryBounds, mSecondaryBounds);
        }
        Log.i(TAG, "registerOrganizer with PrimaryBounds=" + mPrimaryBounds
                + " SecondaryBounds=" + mSecondaryBounds);

        synchronized (this) {
            final List<TaskAppearedInfo> taskInfos = super.registerOrganizer();
            for (int i = 0; i < taskInfos.size(); i++) {
                final TaskAppearedInfo info = taskInfos.get(i);
                onTaskAppeared(info.getTaskInfo(), info.getLeash());
            }
            createRootTasksIfNeeded();
            return taskInfos;
        }
    }

    private void createRootTasksIfNeeded() {
        synchronized (this) {
            if (mPrimaryCookie != null) return;
            mPrimaryCookie = new Binder();
            mSecondaryCookie = new Binder();

            createRootTask(DEFAULT_DISPLAY, WINDOWING_MODE_MULTI_WINDOW, mPrimaryCookie);
            createRootTask(DEFAULT_DISPLAY, WINDOWING_MODE_MULTI_WINDOW, mSecondaryCookie);

            waitForAndAssert(o -> mRootPrimary != null && mRootSecondary != null,
                    "Failed to get root tasks");
            Log.e(TAG, "createRootTasksIfNeeded primary=" + mRootPrimary.taskId
                    + " secondary=" + mRootSecondary.taskId);

            // Set the roots as adjacent to each other.
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setAdjacentRoots(mRootPrimary.getToken(), mRootSecondary.getToken());
            wct.setLaunchAdjacentFlagRoot(mRootSecondary.getToken());
            applyTransaction(wct);
        }
    }

    private void waitForAndAssert(Predicate<Object> condition, String failureMessage) {
        waitFor(condition);
        if (!condition.test(this)) {
            Assert.fail(failureMessage);
        }
    }

    private void waitFor(Predicate<Object> condition) {
        final long waitTillTime = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(5);
        while (!condition.test(this)
                && SystemClock.elapsedRealtime() < waitTillTime) {
            try {
                wait(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyOnEnd(Runnable r) {
        r.run();
        notifyAll();
    }

    public void registerOrganizerIfNeeded() {
        synchronized (this) {
            if (mRegistered) return;

            NestedShellPermission.run(() -> {
                registerOrganizer();
            });
            mRegistered = true;
        }
    }

    public void unregisterOrganizerIfNeeded() {
        synchronized (this) {
            if (!mRegistered) return;
            mRegistered = false;

            NestedShellPermission.run(() -> {
                dismissSplitScreen();

                deleteRootTask(mRootPrimary.getToken());
                mRootPrimary = null;
                mPrimaryCookie = null;
                mPrimaryChildrenTaskIds.clear();
                deleteRootTask(mRootSecondary.getToken());
                mRootSecondary = null;
                mSecondaryCookie = null;
                mSecondaryChildrenTaskIds.clear();
                mPrePrimarySplitTaskInfo = null;
                mPreSecondarySplitTaskInfo = null;

                super.unregisterOrganizer();
            });
        }
    }

    public void putTaskInSplitPrimary(int taskId) {
        NestedShellPermission.run(() -> {
            synchronized (this) {
                registerOrganizerIfNeeded();
                ActivityManager.RunningTaskInfo taskInfo = getTaskInfo(taskId);
                final WindowContainerTransaction t = new WindowContainerTransaction()
                        .setBounds(mRootPrimary.getToken(), mPrimaryBounds)
                        .setBounds(taskInfo.getToken(), null)
                        .setWindowingMode(taskInfo.getToken(), WINDOWING_MODE_UNDEFINED)
                        .reparent(taskInfo.getToken(), mRootPrimary.getToken(), true /* onTop */)
                        .reorder(mRootPrimary.getToken(), true /* onTop */);
                applyTransaction(t);
                mPrePrimarySplitTaskInfo = taskInfo;

                waitForAndAssert(
                        o -> mPrimaryChildrenTaskIds.contains(taskId),
                        "Can't put putTaskInSplitPrimary taskId=" + taskId);

                Log.e(TAG, "putTaskInSplitPrimary taskId=" + taskId);
            }
        });
    }

    public void putTaskInSplitSecondary(int taskId) {
        NestedShellPermission.run(() -> {
            synchronized (this) {
                registerOrganizerIfNeeded();
                ActivityManager.RunningTaskInfo taskInfo = getTaskInfo(taskId);
                final WindowContainerTransaction t = new WindowContainerTransaction()
                        .setBounds(mRootSecondary.getToken(), mSecondaryBounds)
                        .setBounds(taskInfo.getToken(), null)
                        .setWindowingMode(taskInfo.getToken(), WINDOWING_MODE_UNDEFINED)
                        .reparent(taskInfo.getToken(), mRootSecondary.getToken(), true /* onTop */)
                        .reorder(mRootSecondary.getToken(), true /* onTop */);
                applyTransaction(t);
                mPreSecondarySplitTaskInfo = taskInfo;

                waitForAndAssert(
                        o -> mSecondaryChildrenTaskIds.contains(taskId),
                        "Can't put putTaskInSplitSecondary taskId=" + taskId);

                Log.e(TAG, "putTaskInSplitSecondary taskId=" + taskId);
            }
        });
    }

    public void setLaunchRoot(int taskId) {
        NestedShellPermission.run(() -> {
            synchronized (this) {
                final WindowContainerTransaction t = new WindowContainerTransaction()
                        .setLaunchRoot(mKnownTasks.get(taskId).getToken(),
                                CONTROLLED_WINDOWING_MODES, CONTROLLED_ACTIVITY_TYPES);
                applyTransaction(t);
            }
        });
    }

    void dismissSplitScreen() {
        dismissSplitScreen(false /* primaryOnTop */);
    }

    public void dismissSplitScreen(boolean primaryOnTop) {
        dismissSplitScreen(new WindowContainerTransaction(), primaryOnTop);
    }

    public void dismissSplitScreen(WindowContainerTransaction t, boolean primaryOnTop) {
        synchronized (this) {
            NestedShellPermission.run(() -> {
                t.setLaunchRoot(mRootPrimary.getToken(), null, null)
                        .setLaunchRoot(
                                mRootSecondary.getToken(),
                                null,
                                null)
                        .reparentTasks(
                                primaryOnTop ? mRootSecondary.getToken() : mRootPrimary.getToken(),
                                null /* newParent */,
                                CONTROLLED_WINDOWING_MODES,
                                CONTROLLED_ACTIVITY_TYPES,
                                true /* onTop */)
                        .reparentTasks(
                                primaryOnTop ? mRootPrimary.getToken() : mRootSecondary.getToken(),
                                null /* newParent */,
                                CONTROLLED_WINDOWING_MODES,
                                CONTROLLED_ACTIVITY_TYPES,
                                true /* onTop */);
                applyPreSplitTaskInfo(t, mPrePrimarySplitTaskInfo);
                applyPreSplitTaskInfo(t, mPreSecondarySplitTaskInfo);
                applyTransaction(t);
            });
        }
    }

    private void applyPreSplitTaskInfo(WindowContainerTransaction t,
                                       @Nullable ActivityManager.RunningTaskInfo preSplitTaskInfo) {
        if (preSplitTaskInfo == null) {
            return;
        }
        final int restoreWindowingMode =
                preSplitTaskInfo.getConfiguration().windowConfiguration.getWindowingMode();
        // TODO(b/215556258): We should also consider restoring WINDOWING_MODE_FREEFORM and its
        //  bounds. For now, restoring freeform when dismissing split seems to mess up activity
        //  lifecycle especially with shell transitions enabled.
        if (restoreWindowingMode == WINDOWING_MODE_FULLSCREEN) {
            t.setWindowingMode(preSplitTaskInfo.getToken(), restoreWindowingMode);
            t.setBounds(preSplitTaskInfo.getToken(), null);
        }
    }

    public void setRootPrimaryTaskBounds(Rect bounds) {
        setTaskBounds(mRootPrimary.getToken(), bounds);
    }

    void setRootSecondaryTaskBounds(Rect bounds) {
        setTaskBounds(mRootSecondary.getToken(), bounds);
    }

    public Rect getPrimaryTaskBounds() {
        return mPrimaryBounds;
    }

    public Rect getSecondaryTaskBounds() {
        return mSecondaryBounds;
    }

    private void setTaskBounds(WindowContainerToken container, Rect bounds) {
        synchronized (this) {
            NestedShellPermission.run(() -> {
                final WindowContainerTransaction t = new WindowContainerTransaction()
                        .setBounds(container, bounds);
                applyTransaction(t);
            });
        }
    }

    public int getPrimarySplitTaskCount() {
        return mPrimaryChildrenTaskIds.size();
    }

    public int getSecondarySplitTaskCount() {
        return mSecondaryChildrenTaskIds.size();
    }

    public int getPrimarySplitTaskId() {
        return mRootPrimary != null ? mRootPrimary.taskId : INVALID_TASK_ID;
    }

    public int getSecondarySplitTaskId() {
        return mRootSecondary != null ? mRootSecondary.taskId : INVALID_TASK_ID;
    }

    public ActivityManager.RunningTaskInfo getTaskInfo(int taskId) {
        synchronized (this) {
            ActivityManager.RunningTaskInfo taskInfo = mKnownTasks.get(taskId);
            if (taskInfo != null) return taskInfo;

            final List<ActivityManager.RunningTaskInfo> rootTasks = getRootTasks(DEFAULT_DISPLAY,
                    null);
            for (ActivityManager.RunningTaskInfo info : rootTasks) {
                addTask(info);
            }

            return mKnownTasks.get(taskId);
        }
    }

    /** Waits and asserts that given Task to appear. */
    public void waitForAndAssertTaskAppeared(int taskId) {
        synchronized (this) {
            waitForAndAssert(o -> mKnownTasks.containsKey(taskId), "Can't find task=" + taskId);
        }
    }

    @Override
    public void onTaskAppeared(
            @NonNull ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        synchronized (this) {
            notifyOnEnd(() -> {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.setVisibility(leash, true /* visible */);
                addTask(taskInfo, leash, t);
                t.apply();
            });
        }
    }

    @Override
    public void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        synchronized (this) {
            removeTask(taskInfo);
        }
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        synchronized (this) {
            notifyOnEnd(() -> {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                addTask(taskInfo, null /* leash */, t);
                t.apply();
            });
        }
    }

    private void addTask(ActivityManager.RunningTaskInfo taskInfo) {
        addTask(taskInfo, null /* SurfaceControl */, null /* Transaction */);
    }

    private void addTask(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            SurfaceControl.Transaction t) {
        mKnownTasks.put(taskInfo.taskId, taskInfo);
        if (leash != null) {
            mTaskLeashes.put(taskInfo.taskId, leash);
        } else {
            leash = mTaskLeashes.get(taskInfo.taskId);
        }
        if (taskInfo.hasParentTask()) {
            Rect sourceCrop = null;
            if (mRootPrimary != null
                    && mRootPrimary.taskId == taskInfo.getParentTaskId()) {
                sourceCrop = new Rect(mPrimaryBounds);
                mPrimaryChildrenTaskIds.add(taskInfo.taskId);
            } else if (mRootSecondary != null
                    && mRootSecondary.taskId == taskInfo.getParentTaskId()) {
                sourceCrop = new Rect(mSecondaryBounds);
                mSecondaryChildrenTaskIds.add(taskInfo.taskId);
            }
            if (t != null && leash != null && sourceCrop != null) {
                sourceCrop.offsetTo(0, 0);
                t.setGeometry(leash, sourceCrop, sourceCrop, Surface.ROTATION_0);
            }
            return;
        }

        if (mRootPrimary == null
                && mPrimaryCookie != null
                && taskInfo.containsLaunchCookie(mPrimaryCookie)) {
            mRootPrimary = taskInfo;
            if (t != null && leash != null) {
                Rect sourceCrop = new Rect(mPrimaryBounds);
                sourceCrop.offsetTo(0, 0);
                t.setGeometry(leash, sourceCrop, mPrimaryBounds, Surface.ROTATION_0);
            }
            return;
        }

        if (mRootSecondary == null
                && mSecondaryCookie != null
                && taskInfo.containsLaunchCookie(mSecondaryCookie)) {
            mRootSecondary = taskInfo;
            if (t != null && leash != null) {
                Rect sourceCrop = new Rect(mSecondaryBounds);
                sourceCrop.offsetTo(0, 0);
                t.setGeometry(leash, sourceCrop, mSecondaryBounds, Surface.ROTATION_0);
            }
            return;
        }

        if (t == null || leash == null) {
            return;
        }
        WindowConfiguration config = taskInfo.getConfiguration().windowConfiguration;
        Rect bounds = config.getBounds();
        Rect sourceCrop = null;
        if (config.getWindowingMode() != WINDOWING_MODE_FULLSCREEN) {
            sourceCrop = new Rect(bounds);
            sourceCrop.offsetTo(0, 0);
        }
        t.setGeometry(leash, sourceCrop, bounds, Surface.ROTATION_0);
    }

    private void removeTask(ActivityManager.RunningTaskInfo taskInfo) {
        final int taskId = taskInfo.taskId;
        // ignores cleanup on duplicated removal request
        if (mKnownTasks.remove(taskId) == null) {
            return;
        }
        mTaskLeashes.remove(taskId);
        mPrimaryChildrenTaskIds.remove(taskId);
        mSecondaryChildrenTaskIds.remove(taskId);

        if ((mRootPrimary != null && taskId == mRootPrimary.taskId)
                || (mRootSecondary != null && taskId == mRootSecondary.taskId)) {
            unregisterOrganizerIfNeeded();
        }
    }
}
