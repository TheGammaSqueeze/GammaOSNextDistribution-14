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

package android.taskfpscallback.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.Context;
import android.view.WindowManager;
import android.window.TaskFpsCallback;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TaskFpsCallbackCtsTest {
    private static final String TAG = "TaskFpsCallbackCtsTest";

    private TaskFpsCallbackCtsActivity mActivity;
    private Context mContext;
    private WindowManager mWindowManager;

    @Rule
    public ActivityScenarioRule<TaskFpsCallbackCtsActivity> mActivityRule =
            new ActivityScenarioRule<>(TaskFpsCallbackCtsActivity.class);

    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
        });

        final Instrumentation instrumentation = getInstrumentation();
        mContext = instrumentation.getContext();
        mWindowManager = mContext.getSystemService(WindowManager.class);
    }

    @Test
    public void testRegister() throws Exception {
        final TaskFpsCallback callback = new TaskFpsCallback() {
            @Override
            public void onFpsReported(float fps) {
                // Ignore
            }
        };

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mWindowManager,
                (windowManager) -> windowManager.registerTaskFpsCallback(
                        mActivity.getTaskId(), Runnable::run, callback),
                "android.permission.ACCESS_FPS_COUNTER");

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mWindowManager,
                (windowManager) -> windowManager.unregisterTaskFpsCallback(callback),
                "android.permission.ACCESS_FPS_COUNTER");
    }

    @Test
    public void testRegisterWithoutPermission() {
        final TaskFpsCallback callback = new TaskFpsCallback() {
            @Override
            public void onFpsReported(float fps) {
                // Ignore
            }
        };
        assertThrows(SecurityException.class, () -> mWindowManager.registerTaskFpsCallback(
                mActivity.getTaskId(), Runnable::run, callback));
    }
}
