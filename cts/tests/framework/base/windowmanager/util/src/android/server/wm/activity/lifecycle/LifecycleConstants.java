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

package android.server.wm.activity.lifecycle;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Activity;
import android.content.ComponentName;

public final class LifecycleConstants {

    public static final String ON_CREATE = "ON_CREATE";
    public static final String ON_START = "ON_START";
    public static final String ON_RESUME = "ON_RESUME";
    public static final String ON_PAUSE = "ON_PAUSE";
    public static final String ON_STOP = "ON_STOP";
    public static final String ON_RESTART = "ON_RESTART";
    public static final String ON_DESTROY = "ON_DESTROY";
    public static final String ON_ACTIVITY_RESULT = "ON_ACTIVITY_RESULT";
    public static final String ON_POST_CREATE = "ON_POST_CREATE";
    public static final String ON_NEW_INTENT = "ON_NEW_INTENT";
    public static final String ON_MULTI_WINDOW_MODE_CHANGED = "ON_MULTI_WINDOW_MODE_CHANGED";
    public static final String ON_TOP_POSITION_GAINED = "ON_TOP_POSITION_GAINED";
    public static final String ON_TOP_POSITION_LOST = "ON_TOP_POSITION_LOST";
    public static final String ON_USER_LEAVE_HINT = "ON_USER_LEAVE_HINT";

    /**
     * Activity launch time is evaluated. It is expected to be less than 5 seconds. Otherwise, it's
     * likely there is a timeout.
     */
    static final long ACTIVITY_LAUNCH_TIMEOUT = 5 * 1000L * HW_TIMEOUT_MULTIPLIER;
    static final String EXTRA_RECREATE = "recreate";
    static final String EXTRA_FINISH_IN_ON_CREATE = "finish_in_on_create";
    static final String EXTRA_FINISH_IN_ON_START = "finish_in_on_start";
    static final String EXTRA_FINISH_IN_ON_RESUME = "finish_in_on_resume";
    static final String EXTRA_FINISH_IN_ON_PAUSE = "finish_in_on_pause";
    static final String EXTRA_FINISH_IN_ON_STOP = "finish_in_on_stop";
    static final String EXTRA_START_ACTIVITY_IN_ON_CREATE = "start_activity_in_on_create";
    static final String EXTRA_START_ACTIVITY_WHEN_IDLE = "start_activity_when_idle";
    static final String EXTRA_ACTIVITY_ON_USER_LEAVE_HINT = "activity_on_user_leave_hint";
    /**
     * Use this flag to skip recording top resumed state to avoid affecting verification.
     * @see ActivityLifecycleClientTestBase.Launcher#setSkipTopResumedStateCheck()
     */
    static final String EXTRA_SKIP_TOP_RESUMED_STATE = "skip_top_resumed_state";

    static ComponentName getComponentName(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }
}
