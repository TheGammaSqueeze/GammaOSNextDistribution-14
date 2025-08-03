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

package android.platform.test.rule;

import androidx.annotation.VisibleForTesting;

import org.junit.runner.Description;

/** This rule will Set the Battery Percentage to be visible or invisible based on the input */
public class SetBatteryPercentageVisibleRule extends TestWatcher {

    static final String SHOW_BATTERY_PERCENT_SETTING = "status_bar_show_battery_percent";

    @VisibleForTesting static final String VISIBLE_OPTION = "visible";

    private boolean mVisible;

    public SetBatteryPercentageVisibleRule(Boolean visible) {
        mVisible = visible;
    }

    @Override
    protected void starting(Description description) {
        Boolean visible = getArguments().getBoolean(VISIBLE_OPTION);
        if (visible == null) {
            return;
        }
        mVisible = visible;

        executeShellCommand(
                "settings put system " + SHOW_BATTERY_PERCENT_SETTING + " " + (mVisible ? 1 : 0));
    }
}
