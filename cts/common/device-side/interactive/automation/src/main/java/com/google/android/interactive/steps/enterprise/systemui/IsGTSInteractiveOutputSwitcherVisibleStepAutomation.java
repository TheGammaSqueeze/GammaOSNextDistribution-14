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

package com.google.android.interactive.steps.enterprise.systemui;

import androidx.test.uiautomator.UiObject2;

import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

import com.google.android.interactive.helpers.MediaControlsHelper;

/** Automation for IsGTSInteractiveOutputSwitcherVisibleStep. **/
@AutomationFor("com.google.android.interactive.steps.enterprise.systemui"
        + ".IsGTSInteractiveOutputSwitcherVisibleStep")
public class IsGTSInteractiveOutputSwitcherVisibleStepAutomation implements Automation<Boolean> {

    @Override
    public Boolean automate() throws Exception {
        UiObject2 player = MediaControlsHelper.findMediaControls();
        return player != null
                && MediaControlsHelper.findChildObject(player, "media_seamless") != null;
    }
}
