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

package com.android.interactive.steps.settings;

import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.settings.CanYouChangeWifiSettingsStep")
public final class CanYouChangeWifiSettingsStepAutomation implements Automation<Boolean> {

    private static final long WAIT_TIMEOUT = 1000;

    @Override
    public Boolean automate() {
        // Right now we just look for the admin dialog to indicate that it cannot be changed
        // TODO: We should find a "positive" way of checking if we can change it rather than this
        // negative one

        return !TestApis.ui().device().findObject(
                        new UiSelector().resourceId
                                ("com.android.settings:id/admin_support_dialog_title"))
                .waitForExists(WAIT_TIMEOUT);
    }
}
