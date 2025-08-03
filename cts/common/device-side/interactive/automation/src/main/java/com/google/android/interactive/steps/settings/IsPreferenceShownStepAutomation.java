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

package com.google.android.interactive.steps.settings;

import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;

/** Automation Step for checking if preference exist. **/
public abstract class IsPreferenceShownStepAutomation implements Automation<Boolean> {

    private static final String resListView = ":id/recycler_view";
    protected String mSettingsPackage = "com.android.settings";
    private final String mPreferenceTitle;

    public IsPreferenceShownStepAutomation(String title) {
        this.mPreferenceTitle = title;
    }

    @Override
    public Boolean automate() throws Exception {
        UiObject2 preferenceList = TestApis.ui().device()
                .findObject(By.res(mSettingsPackage + resListView));

        while (!TestApis.ui().device().hasObject(By.text(mPreferenceTitle))
                && preferenceList.scroll(Direction.DOWN, 0.1f)) {
            // Continue until app found or bottom of list reached.
        }

        UiObject match = TestApis.ui().device().findObject(
                        new UiSelector().resourceId(mSettingsPackage + resListView))
                .getChild(new UiSelector().text(mPreferenceTitle));

        Log.e("InteractiveAutomation", "Looking for " + mPreferenceTitle
                + " in settings, found " + match + "(exists: " + match.exists() + ")");
        return match.exists();
    }
}
