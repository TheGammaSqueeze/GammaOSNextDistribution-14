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

package android.platform.test.rule;

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This rule removes all the activities associated with the given package name
 *  from the activity manager stack.
 */
public class RemoveAppFromStackRule extends TestWatcher {

    private static final String TAG = RemoveAppFromStackRule.class.getSimpleName();

    private final Pattern mAppActivityRecordPattern;

    @VisibleForTesting
    static final String REMOVE_APP_FROM_AM_STACK = "rm-app-am-stack";

    public RemoveAppFromStackRule() throws InitializationError {
        throw new InitializationError("Must supply an application pkg name to"
                + "remove from am stack.");
    }

    public RemoveAppFromStackRule(String appPackageName) {
        mAppActivityRecordPattern =
                Pattern.compile(
                        "ActivityRecord\\{.*"
                                + appPackageName.replace(".", "\\.")
                                + "/.*\\st([0-9]+)\\}");
    }

    @Override
    protected void starting(Description description) {
        // Check if removing the app from am stack is chosen or not.
        boolean removeAppFromAm = Boolean
                .parseBoolean(getArguments().getString(REMOVE_APP_FROM_AM_STACK, "true"));
        if (!removeAppFromAm) {
            return;
        }

        // Remove the stack of the app.
        final Matcher appActivityMatcher = mAppActivityRecordPattern.matcher(
                executeShellCommand("dumpsys activity activities"));
        if (appActivityMatcher.find()) {
            executeShellCommand("am stack remove " + appActivityMatcher.group(1));
            Log.v(TAG, "Removed the app from AM stack");
        } else {
            Log.e(TAG, "No activities stack associated with the package name is found.");
        }

        super.starting(description);
    }
}
