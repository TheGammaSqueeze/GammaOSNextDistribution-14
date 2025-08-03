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

package android.car.cts.builtin.app;

import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.Instrumentation;
import android.app.TaskInfo;
import android.car.builtin.app.TaskInfoHelper;
import android.car.cts.builtin.activity.TaskInfoTestActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.server.wm.ActivityManagerTestBase;
import android.util.Log;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public final class TaskInfoHelperTest extends ActivityManagerTestBase {

    private static final String TAG = TaskInfoHelperTest.class.getSimpleName();
    private static final String NULL_STRING = "null";
    private static final String TO_STRING_PREFIX = "TaskInfo{";
    private static final Pattern TASK_ID_FIELD_PATTERN =
            Pattern.compile("taskId=[0-9]+");

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mTargetContext = mInstrumentation.getTargetContext();

    private TaskInfoTestActivity mTestActivity = null;
    private ActivityManager mAm = null;

    @Rule public final Expect expect = Expect.create();

    @Before
    public void setUp() throws Exception {
        // Home was launched in ActivityManagerTestBase#setUp, wait until it is stable,
        // in order not to mix the event of its TaskView Activity with the TestActivity.
        mWmState.waitForHomeActivityVisible();

        mAm = mTargetContext.getSystemService(ActivityManager.class);
        mTestActivity = launchTestActivity();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestActivity != null) {
            mTestActivity.finish();
        }
    }

    @Test
    public void testDisplayIdAndUserId() throws Exception {
        // setup
        int taskId = mTestActivity.getTaskId();
        TaskInfo taskInfo = getTaskInfo(taskId);

        // execution and assert
        assertThat(taskInfo).isNotNull();
        assertThat(TaskInfoHelper.getDisplayId(taskInfo)).isEqualTo(mTestActivity.getDisplayId());
        assertThat(TaskInfoHelper.getUserId(taskInfo)).isEqualTo(mTestActivity.getUserId());
    }

    @Test
    public void testTaskVisibility() throws Exception {
        // setup
        int taskId = mTestActivity.getTaskId();
        TaskInfo taskInfo = getTaskInfo(taskId);

        // execution and assert
        assertThat(TaskInfoHelper.isVisible(taskInfo)).isEqualTo(mTestActivity.isVisible());

        // start a new TestActivity in a different task so that the previous task is
        // in background and invisible
        TaskInfoTestActivity secondActivity = launchTestActivity();
        assertThat(taskId).isNotEqualTo(secondActivity.getTaskId());
        taskInfo = getTaskInfo(taskId);
        assertThat(TaskInfoHelper.isVisible(taskInfo)).isFalse();
        secondActivity.finish();
    }

    @Test
    public void testToString() throws Exception {
        // setup
        TaskInfo taskInfo = getTaskInfo(mTestActivity.getTaskId());
        String taskInfoString = taskInfo.toString();
        Log.d(TAG, taskInfoString);

        // execution and assert
        assertThat(TaskInfoHelper.toString(null)).isEqualTo(NULL_STRING);

        String helperString = TaskInfoHelper.toString(taskInfo);
        assertThat(helperString).startsWith(TO_STRING_PREFIX);

        Matcher fieldMatcher = TASK_ID_FIELD_PATTERN.matcher(helperString);
        assertThat(fieldMatcher.find()).isTrue();
        assertThat(taskInfoString).contains(fieldMatcher.group());
    }

    @Test
    public void testGetBounds() throws Exception {
        TaskInfo taskInfo = getTaskInfo(mTestActivity.getTaskId());

        Rect bounds = TaskInfoHelper.getBounds(taskInfo);

        View testActivityDecorView = mTestActivity.getWindow().getDecorView();
        assertThat(bounds).isNotNull();
        expect.that(bounds.width()).isEqualTo(testActivityDecorView.getWidth());
        expect.that(bounds.height()).isEqualTo(testActivityDecorView.getHeight());
    }

    private TaskInfo getTaskInfo(int taskId) {
        List<AppTask> appTasks = mAm.getAppTasks();
        for (AppTask task : appTasks) {
            TaskInfo taskInfo = task.getTaskInfo();
            if (taskInfo.taskId == taskId) {
                return taskInfo;
            }
        }
        return null;
    }

    private TaskInfoTestActivity launchTestActivity() {
        Intent startIntent = new Intent(mTargetContext, TaskInfoTestActivity.class)
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK);

        TaskInfoTestActivity testActivity = (TaskInfoTestActivity) mInstrumentation
                .startActivitySync(startIntent, /* options= */null);

        ComponentName testActivityName = testActivity.getComponentName();
        waitAndAssertTopResumedActivity(testActivityName, DEFAULT_DISPLAY,
                "Activity must be resumed");

        return testActivity;
    }
}
