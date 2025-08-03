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

package com.google.android.interactive.steps.enterprise.launcher;

import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;

public abstract class IsAppShownInPersonalAppsStepAutomation implements Automation<Boolean> {

    private final String mAppLabel;

    public IsAppShownInPersonalAppsStepAutomation(String appLabel) {
        mAppLabel = appLabel;
    }

    @Override
    public Boolean automate() throws Exception {
        // This works but is flaky - we need a way of scrolling to the top again without dismissing
        // launcher
        UiObject2 appList = TestApis.ui().device()
                .findObject(By.res("com.google.android.apps.nexuslauncher:id/apps_list_view"));

        while (!TestApis.ui().device().hasObject(By.text(mAppLabel))
                && appList.scroll(Direction.DOWN, 0.1f)) {
            // Continue until app found or bottom of list reached.
        }

        UiObject match = TestApis.ui().device().findObject(
                new UiSelector().resourceId(
                        "com.google.android.apps.nexuslauncher:id/apps_list_view"))
                .getChild(new UiSelector().text(mAppLabel));

        Log.e("InteractiveAutomation", "Looking for app " + mAppLabel + " in personal, found " + match + "(exists: " + match.exists() + ")");
        return match.exists();
    }
}
