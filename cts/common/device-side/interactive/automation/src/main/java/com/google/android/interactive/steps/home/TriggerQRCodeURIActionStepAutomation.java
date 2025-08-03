
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

package com.google.android.interactive.steps.home;

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;
import android.util.Log;

@AutomationFor("com.google.android.interactive.steps.home.TriggerQRCodeURIActionStep")
public final class TriggerQRCodeURIActionStepAutomation implements Automation<Boolean> {
    private static final UiDevice mUiDevice = UiDevice.getInstance(InstrumentationRegistry
            .getInstrumentation());
    private static final String BUTTON_TEXT = "Tap to set up";
    private static final int UI_TIMEOUT = 60000;

    @Override
    public Boolean automate() throws Exception {

        // Automates selecting "tap to set up" button if it exists
        UiObject2 tapToSetUpButton = mUiDevice.wait(Until.findObject(By.text(BUTTON_TEXT)
                .enabled(true)), UI_TIMEOUT);

        if (tapToSetUpButton != null) {
            tapToSetUpButton.clickAndWait(Until.newWindow(), UI_TIMEOUT);
            return true;
        } else {
            return false;
        }
    }
}