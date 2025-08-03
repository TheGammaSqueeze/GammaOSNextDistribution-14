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
package android.platform.test.rule;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * This rule can be used to kill specific apps by process name upon test failure. This differs from
 * KillAppsRule in that these apps are killed at the end of the test in preparation for a
 * state-dependent subsequent test, rather than before a test to ensure a clean state.
 */
public class KillAppsOnFailureRule extends TestWatcher {
    private static final String TAG = KillAppsOnFailureRule.class.getSimpleName();

    @VisibleForTesting static final String KILL_CMD = "am force-stop %s";
    @VisibleForTesting static final String APPS_OPTION = "kill-apps-on-failure";

    private ArrayList<String> mAppsToKill;

    @Override
    protected void starting(Description description) {
        String commaSeparatedApps = getArguments().getString(APPS_OPTION, null);
        if (commaSeparatedApps == null) {
            return;
        }
        mAppsToKill = new ArrayList(Arrays.asList(commaSeparatedApps.split(",")));
    }

    @Override
    protected void failed(Throwable e, Description description) {
        if (mAppsToKill == null) {
            return;
        }
        for (String app : mAppsToKill) {
            Log.v(TAG, "Killing app %s due to test failure.");
            executeShellCommand(String.format(KILL_CMD, app));
        }
    }
}
