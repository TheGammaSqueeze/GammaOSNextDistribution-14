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


import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.car.app.CarActivityManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.taskview.TaskViewBase;
import com.android.wm.shell.taskview.TaskViewTaskController;

import org.junit.Test;
import org.junit.runner.RunWith;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public final class RootTaskMediatorTest extends SysuiTestCase {
    private RootTaskMediator mMediator;
    private final ShellTaskOrganizer mShellTaskOrganizer = mock(ShellTaskOrganizer.class);
    private final TaskViewTaskController mTaskViewTaskController = mock(
            TaskViewTaskController.class);
    private final TaskViewBase mTaskViewClientPart = mock(TaskViewBase.class);
    private final SyncTransactionQueue mSyncQueue = mock(SyncTransactionQueue.class);
    private final CarActivityManager mCarActivityManager = mock(CarActivityManager.class);

    private ActivityManager.RunningTaskInfo createTask(int taskId) {
        ActivityManager.RunningTaskInfo taskInfo =
                new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_MULTI_WINDOW);
        taskInfo.parentTaskId = INVALID_TASK_ID;
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.isVisible = true;
        return taskInfo;
    }

    @Test
    public void createActivityArray_generatesCorrectArray() {
        int[] expectedActivityTypes = {
                WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                WindowConfiguration.ACTIVITY_TYPE_HOME,
                WindowConfiguration.ACTIVITY_TYPE_RECENTS,
                WindowConfiguration.ACTIVITY_TYPE_ASSISTANT
        };

        int[] actualActivityTypes = RootTaskMediator.createActivityArray(/* embedHomeTask= */
                true, /* embedRecentsTask= */  true, /* embedAssistantTask= */ true);

        assertThat(expectedActivityTypes).isEqualTo(actualActivityTypes);
    }

    @Test
    public void onTaskAppeared_setsRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        assertThat(mMediator.getRootTask()).isEqualTo(taskInfo);
    }

    @Test
    public void onTaskAppeared_withIsLaunchRoot_setsLaunchRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTaskInfo = createTask(/* taskId= */ 1);

        mMediator.onTaskAppeared(rootTaskInfo, null);

        assertThat(mMediator.getRootTask()).isEqualTo(rootTaskInfo);
    }

    @Test
    public void onTaskAppeared_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo launchRootTask = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(launchRootTask, null);

        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(taskInfo, null);

        verify(mCarActivityManager).onTaskAppeared(eq(taskInfo), isNull());
    }

    @Test
    public void onTaskAppeared_rootTaskExists_updatesTaskStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTask = new ActivityManager.RunningTaskInfo();
        rootTask.taskId = 1;
        mMediator.onTaskAppeared(rootTask, null);

        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 2;
        mMediator.onTaskAppeared(taskInfo, null);

        assertThat(mMediator.getTaskStack()).containsKey(taskInfo.taskId);
    }

    @Test
    public void onTaskInfoChanged_forRootTask_updatesShellPart() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskInfoChanged(taskInfo);

        verify(mTaskViewTaskController).onTaskInfoChanged(eq(taskInfo));
    }

    @Test
    public void onTaskInfoChanged_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 1;

        mMediator.onTaskInfoChanged(taskInfo);

        verify(mCarActivityManager).onTaskInfoChanged(taskInfo);
    }

    @Test
    public void onTaskInfoChanged_multipleExisting_taskVisible_movesToFrontOfStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);
        ActivityManager.RunningTaskInfo task2 = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task2, null);

        mMediator.onTaskInfoChanged(task1);

        assertThat(mMediator.getTaskStack().values()).containsExactly(task1, task2);
        verify(mTaskViewClientPart).onTaskInfoChanged(task1);
    }

    @Test
    public void onTaskVanished_forRootTask_updatesShellPart() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo taskInfo = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskVanished(taskInfo);

        verify(mTaskViewTaskController).onTaskVanished(eq(taskInfo));
    }

    @Test
    public void onTaskVanished_withIsLaunchRoot_callsCarActivityManager() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ true, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = 1;
        mMediator.onTaskAppeared(taskInfo, null);

        mMediator.onTaskVanished(taskInfo);

        verify(mCarActivityManager).onTaskVanished(taskInfo);
    }

    @Test
    public void onTaskVanished_multipleExistingTasks_removesFromTaskStack() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */true, false, false,
                true, mShellTaskOrganizer, mTaskViewTaskController, mTaskViewClientPart, mSyncQueue,
                mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 99);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task1 = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(task1, null);
        ActivityManager.RunningTaskInfo task2 = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task2, null);

        mMediator.onTaskVanished(task1);

        assertThat(mMediator.getTaskStack().values()).containsExactly(task2);
        assertThat(mMediator.getTaskStack().values()).containsExactly(task2);
        verify(mTaskViewClientPart).onTaskVanished(task1);
    }

    @Test
    public void release_clearsRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTask = new ActivityManager.RunningTaskInfo();
        rootTask.taskId = 1;
        mMediator.onTaskAppeared(rootTask, null);

        mMediator.release();

        assertThat(mMediator.getRootTask()).isNull();
    }

    @Test
    public void release_withIsLaunchRoot_clearsLaunchRootTask() {
        mMediator = new RootTaskMediator(1, /* isLaunchRoot= */ false, /* embedHomeTask= */ false,
                /* embedRecentsTask= */ false, /* embedAssistantTask= */ true, mShellTaskOrganizer,
                mTaskViewTaskController, mTaskViewClientPart, mSyncQueue, mCarActivityManager);
        ActivityManager.RunningTaskInfo rootTask = createTask(/* taskId= */ 1);
        mMediator.onTaskAppeared(rootTask, null);
        ActivityManager.RunningTaskInfo task = createTask(/* taskId= */ 2);
        mMediator.onTaskAppeared(task, null);

        mMediator.release();

        assertThat(mMediator.getRootTask()).isNull();
        assertThat(mMediator.getTaskStack()).isEmpty();
    }
}
