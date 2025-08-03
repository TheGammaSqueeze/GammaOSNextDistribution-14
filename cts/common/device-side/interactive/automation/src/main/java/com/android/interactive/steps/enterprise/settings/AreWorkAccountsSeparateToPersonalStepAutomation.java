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

package com.android.interactive.steps.enterprise.settings;

import android.util.Log;
import android.widget.TextView;

import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.AreWorkAccountsSeparateToPersonalStep")
public final class AreWorkAccountsSeparateToPersonalStepAutomation implements Automation<Boolean> {

    private static final long WAIT_TIMEOUT = 10000;

    @Override
    public Boolean automate() {
        // TODO: Can we verify more than just the text of the tabs?
        boolean personalTabExists =
                TestApis.ui().device().findObject(
                        new UiSelector().text("Personal").className(
                                TextView.class)).waitForExists(WAIT_TIMEOUT);
        boolean workTabExists =
                TestApis.ui().device().findObject(new UiSelector().text("Work").className(
                        TextView.class)).waitForExists(WAIT_TIMEOUT);

        Log.i("InteractiveAutomation", "Work tab exists: " + workTabExists
                + ", Personal tab exists: " + personalTabExists);
        return personalTabExists && workTabExists;
    }
}
