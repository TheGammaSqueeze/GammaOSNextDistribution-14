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

package com.android.car.carlauncher.recents;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;

import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_FREEFORM;
import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SINGLE;
import static com.android.wm.shell.util.GroupedRecentTaskInfo.TYPE_SPLIT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;


public class RecentTasksProviderTest {
    private static final int RECENT_TASKS_LENGTH = 20;
    private static final int SPLIT_RECENT_TASKS_LENGTH = 2;
    private static final int FREEFORM_RECENT_TASKS_LENGTH = 3;

    private RecentTasksProvider mRecentTasksProvider;
    private GroupedRecentTaskInfo[] mGroupedRecentTaskInfo;

    @Mock
    private IRecentTasks mRecentTaskProxy;
    @Mock
    private ActivityManagerWrapper mActivityManagerWrapper;
    @Mock
    private PackageManagerWrapper mPackageManagerWrapper;
    @Mock
    private RecentTasksProviderInterface.RecentsDataChangeListener mRecentsDataChangeListenerMock;
    @Mock
    private Task mTask;
    @Mock
    private Task.TaskKey mTaskKey;
    @Mock
    private ActivityManager.TaskDescription mTaskDescription;
    @Mock
    private Bitmap mIconBitmap;
    @Mock
    private Drawable mIconDrawable;
    @Mock
    private Drawable mDefaultIconDrawable;
    @Mock
    private ComponentName mComponent;
    @Mock
    private ActivityInfo mActivityInfo;
    @Mock
    private Intent mBaseIntent;
    @Mock
    private Handler mHandler;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        @Override
        public Context createApplicationContext(ApplicationInfo application, int flags) {
            return this;
        }
    };

    @Before
    public void setup() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        initRecentTaskList();
        when(mRecentTaskProxy.getRecentTasks(anyInt(), eq(RECENT_IGNORE_UNAVAILABLE),
                anyInt())).thenReturn(mGroupedRecentTaskInfo);
        RecentTasksProvider.setExecutor(MoreExecutors.directExecutor());
        when(mHandler.post(any(Runnable.class))).thenAnswer((Answer<Runnable>) invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        RecentTasksProvider.setHandler(mHandler);
        mRecentTasksProvider = RecentTasksProvider.getInstance();
        mRecentTasksProvider.setActivityManagerWrapper(mActivityManagerWrapper);
        mRecentTasksProvider.setPackageManagerWrapper(mPackageManagerWrapper);
        mRecentTasksProvider.init(mContext, mRecentTaskProxy);
    }

    @After
    public void cleanup() {
        mRecentTasksProvider.mRecentTaskIdToTaskMap.clear();
    }

    @Test
    public void getRecentTasksAsync_triggers_recentTasksFetched() throws InterruptedException {
        mRecentTasksProvider.setRecentsDataChangeListener(mRecentsDataChangeListenerMock);

        mRecentTasksProvider.getRecentTasksAsync();

        verify(mRecentsDataChangeListenerMock).recentTasksFetched();
    }

    @Test
    public void getRecentTasksAsync_trigger_recentTaskThumbnailChange_forAllTasks() {
        mRecentTasksProvider.setRecentsDataChangeListener(mRecentsDataChangeListenerMock);

        mRecentTasksProvider.getRecentTasksAsync();

        for (int i = 0; i < RECENT_TASKS_LENGTH; i++) {
            verify(mRecentsDataChangeListenerMock).recentTaskThumbnailChange(i);
        }
    }

    @Test
    public void getRecentTasksAsync_trigger_recentTaskIconChange_forAllTasks() {
        mRecentTasksProvider.setRecentsDataChangeListener(mRecentsDataChangeListenerMock);

        mRecentTasksProvider.getRecentTasksAsync();

        for (int i = 0; i < RECENT_TASKS_LENGTH; i++) {
            verify(mRecentsDataChangeListenerMock).recentTaskIconChange(i);
        }
    }

    @Test
    public void getRecentTasksAsync_getRecentTaskIds_returnsAllInOrder_fetchedTaskIds() {
        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTasksFetched() {
                        List<Integer> ret = mRecentTasksProvider.getRecentTaskIds();

                        assertThat(ret).isNotNull();
                        assertThat(ret.size()).isEqualTo(RECENT_TASKS_LENGTH);
                        for (int i = 0; i < RECENT_TASKS_LENGTH; i++) {
                            assertThat(ret.get(i)).isEqualTo(i);
                        }
                    }
                });

        mRecentTasksProvider.getRecentTasksAsync();
    }

    @Test
    public void getRecentTasksAsync_getRecentTaskIds_filters_TYPE_SPLIT() throws
            RemoteException {
        initRecentTaskList(/* addTypeSplit= */ true, /* addTypeFreeform= */ false);
        assertThat(mGroupedRecentTaskInfo.length).isEqualTo(
                RECENT_TASKS_LENGTH + SPLIT_RECENT_TASKS_LENGTH);
        when(mRecentTaskProxy.getRecentTasks(anyInt(), eq(RECENT_IGNORE_UNAVAILABLE),
                anyInt())).thenReturn(mGroupedRecentTaskInfo);

        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTasksFetched() {
                        List<Integer> ret = mRecentTasksProvider.getRecentTaskIds();

                        assertThat(ret).isNotNull();
                        assertThat(ret.size()).isEqualTo(RECENT_TASKS_LENGTH);
                    }
                });

        mRecentTasksProvider.getRecentTasksAsync();
    }

    @Test
    public void getRecentTasksAsync_getRecentTaskIds_filters_TYPE_FREEFORM() throws
            RemoteException {
        initRecentTaskList(/* addTypeSplit= */ false, /* addTypeFreeform= */ true);
        assertThat(mGroupedRecentTaskInfo.length).isEqualTo(
                RECENT_TASKS_LENGTH + FREEFORM_RECENT_TASKS_LENGTH);
        when(mRecentTaskProxy.getRecentTasks(anyInt(), eq(RECENT_IGNORE_UNAVAILABLE),
                anyInt())).thenReturn(mGroupedRecentTaskInfo);

        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTasksFetched() {
                        List<Integer> ret = mRecentTasksProvider.getRecentTaskIds();

                        assertThat(ret).isNotNull();
                        assertThat(ret.size()).isEqualTo(RECENT_TASKS_LENGTH);
                    }
                });

        mRecentTasksProvider.getRecentTasksAsync();
    }

    @Test
    public void getRecentTaskIconAsync_sets_iconFromTaskDescription() {
        int taskId = 500;
        when(mTaskDescription.getInMemoryIcon()).thenReturn(mIconBitmap);
        mTask.taskDescription = mTaskDescription;
        when(mTaskKey.getComponent()).thenReturn(mComponent);
        mTask.key = mTaskKey;
        mRecentTasksProvider.mRecentTaskIdToTaskMap.put(taskId, mTask);

        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTaskIconChange(int taskId) {
                        Drawable d = mRecentTasksProvider.getRecentTaskIcon(taskId);

                        assertThat(d instanceof BitmapDrawable).isTrue();
                        assertThat(((BitmapDrawable) d).getBitmap()).isEqualTo(mIconBitmap);
                    }
                });

        mRecentTasksProvider.getRecentTaskIconAsync(taskId);
    }

    @Test
    public void getRecentTaskIconAsync_sets_iconFromPackageManager() {
        int taskId = 500;
        when(mTaskDescription.getInMemoryIcon()).thenReturn(null);
        mTask.taskDescription = mTaskDescription;
        when(mTaskKey.getComponent()).thenReturn(mComponent);
        mTask.key = mTaskKey;
        when(mActivityInfo.loadIcon(any(PackageManager.class))).thenReturn(mIconDrawable);
        when(mPackageManagerWrapper.getActivityInfo(eq(mComponent), anyInt())).thenReturn(
                mActivityInfo);
        mRecentTasksProvider.mRecentTaskIdToTaskMap.put(taskId, mTask);

        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTaskIconChange(int taskId) {
                        Drawable d = mRecentTasksProvider.getRecentTaskIcon(taskId);

                        assertThat(d).isEqualTo(mIconDrawable);
                    }
                });

        mRecentTasksProvider.getRecentTaskIconAsync(taskId);
    }

    @Test
    public void getRecentTaskIconAsync_sets_defaultIcon() {
        int taskId = 500;
        when(mTaskDescription.getInMemoryIcon()).thenReturn(null);
        mTask.taskDescription = mTaskDescription;
        when(mTaskKey.getComponent()).thenReturn(mComponent);
        mTask.key = mTaskKey;
        when(mPackageManagerWrapper.getActivityInfo(eq(mComponent), anyInt())).thenReturn(null);
        mRecentTasksProvider.mRecentTaskIdToTaskMap.put(taskId, mTask);
        mRecentTasksProvider.setDefaultIcon(mDefaultIconDrawable);

        mRecentTasksProvider.setRecentsDataChangeListener(
                new ConvenienceRecentsDataChangeListener() {
                    @Override
                    public void recentTaskIconChange(int taskId) {
                        Drawable d = mRecentTasksProvider.getRecentTaskIcon(taskId);

                        assertThat(d).isEqualTo(mDefaultIconDrawable);
                    }
                });

        mRecentTasksProvider.getRecentTaskIconAsync(taskId);
    }

    @Test
    public void openTopRunningTask_openRunningTaskAfterRecentsActivity() {
        int displayId = 0;
        int tasksBeforeRecents = 2;
        int tasksAfterRecents = 2;
        ActivityManager.RunningTaskInfo[] infos = createRunningTaskList(
                tasksBeforeRecents, /* addRecentsClass= */ true, tasksAfterRecents,
                /* recentsClazz= */ RECENTS_ACTIVITY.class.getName());
        when(mActivityManagerWrapper.getRunningTasks(anyBoolean(), eq(displayId)))
                .thenReturn(infos);
        ActivityManager.RunningTaskInfo taskAfterRecents = infos[tasksBeforeRecents + 1];

        mRecentTasksProvider.openTopRunningTask(RECENTS_ACTIVITY.class, displayId);

        verify(mActivityManagerWrapper).startActivityFromRecents(eq(taskAfterRecents.taskId),
                nullable(ActivityOptions.class));
    }

    @Test
    public void openTopRunningTask_recentsActivityNotFound_noOp() {
        int displayId = 0;
        int tasksBeforeRecents = 2;
        ActivityManager.RunningTaskInfo[] infos = createRunningTaskList(
                tasksBeforeRecents, /* addRecentsClass= */ false, /* tasksAfterRecents= */ 0,
                /* recentsClazz= */ RECENTS_ACTIVITY.class.getName());
        when(mActivityManagerWrapper.getRunningTasks(anyBoolean(), eq(displayId)))
                .thenReturn(infos);

        mRecentTasksProvider.openTopRunningTask(RECENTS_ACTIVITY.class, displayId);

        verify(mActivityManagerWrapper, never()).startActivityFromRecents(anyInt(),
                nullable(ActivityOptions.class));
    }

    @Test
    public void openTopRunningTask_recentsActivityNotFound_returnsFalse() {
        int displayId = 0;
        int tasksBeforeRecents = 2;
        ActivityManager.RunningTaskInfo[] infos = createRunningTaskList(
                tasksBeforeRecents, /* addRecentsClass= */ false, /* tasksAfterRecents= */ 0,
                /* recentsClazz= */ RECENTS_ACTIVITY.class.getName());
        when(mActivityManagerWrapper.getRunningTasks(anyBoolean(), eq(displayId)))
                .thenReturn(infos);

        boolean ret = mRecentTasksProvider.openTopRunningTask(RECENTS_ACTIVITY.class, displayId);

        assertThat(ret).isFalse();
    }

    private void initRecentTaskList() {
        initRecentTaskList(/* addTypeSplit= */ false, /* addTypeFreeform= */ false);
    }

    private void initRecentTaskList(boolean addTypeSplit, boolean addTypeFreeform) {
        List<GroupedRecentTaskInfo> groupedRecentTaskInfos = new ArrayList<>();
        for (int i = 0; i < RECENT_TASKS_LENGTH; i++) {
            groupedRecentTaskInfos.add(
                    createGroupedRecentTaskInfo(createRecentTaskInfo(i), TYPE_SINGLE));
        }
        if (addTypeSplit) {
            for (int i = 0; i < SPLIT_RECENT_TASKS_LENGTH; i++) {
                groupedRecentTaskInfos.add(
                        createGroupedRecentTaskInfo(createRecentTaskInfo(i), TYPE_SPLIT));
            }
        }
        if (addTypeFreeform) {
            for (int i = 0; i < FREEFORM_RECENT_TASKS_LENGTH; i++) {
                groupedRecentTaskInfos.add(
                        createGroupedRecentTaskInfo(createRecentTaskInfo(i), TYPE_FREEFORM));
            }
        }
        mGroupedRecentTaskInfo = groupedRecentTaskInfos.toArray(GroupedRecentTaskInfo[]::new);
    }

    private GroupedRecentTaskInfo createGroupedRecentTaskInfo(ActivityManager.RecentTaskInfo info,
            int type) {
        GroupedRecentTaskInfo groupedRecentTaskInfo = mock(GroupedRecentTaskInfo.class);
        when(groupedRecentTaskInfo.getType()).thenReturn(type);
        when(groupedRecentTaskInfo.getTaskInfo1()).thenReturn(info);
        return groupedRecentTaskInfo;
    }

    private ActivityManager.RecentTaskInfo createRecentTaskInfo(int taskId) {
        when(mBaseIntent.getComponent()).thenReturn(mComponent);
        ActivityManager.RecentTaskInfo recentTaskInfo = new ActivityManager.RecentTaskInfo();
        recentTaskInfo.taskId = taskId;
        recentTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);
        recentTaskInfo.baseIntent = mBaseIntent;
        return recentTaskInfo;
    }

    private ActivityManager.RunningTaskInfo[] createRunningTaskList(int tasksBeforeRecents,
            boolean addRecentsClass, int tasksAfterRecents, String recentsClazz) {
        int length =
                addRecentsClass ? tasksBeforeRecents + 1 + tasksAfterRecents : tasksBeforeRecents;
        ActivityManager.RunningTaskInfo[] infos = new ActivityManager.RunningTaskInfo[length];
        for (int i = 0; i < length; i++) {
            ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
            info.taskId = i;
            if (i == tasksBeforeRecents) {
                info.topActivity = new ComponentName("pkg-" + i, recentsClazz);
            } else {
                info.topActivity = new ComponentName("pkg-" + i, "class-" + i);
            }
            infos[i] = info;
        }
        return infos;
    }

    private abstract static class ConvenienceRecentsDataChangeListener implements
            RecentTasksProviderInterface.RecentsDataChangeListener {
        @Override
        public void recentTasksFetched() {
            // no-op
        }

        @Override
        public void recentTaskThumbnailChange(int taskId) {
            // no-op
        }

        @Override
        public void recentTaskIconChange(int taskId) {
            // no-op
        }
    }

    private static class RECENTS_ACTIVITY extends Activity {
    }
}
