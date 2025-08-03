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

package com.google.android.interactive.steps.enterprise.sharesheet;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

import java.util.Locale;

@AutomationFor("com.google.android.interactive.steps.enterprise.sharesheet"
        + ".DoesDialogShowWorkProfileNotEmptyTestAppStep")
public final class DoesDialogShowWorkProfileNotEmptyTestAppStepAutomation
        implements Automation<Boolean> {
    @Override
    public Boolean automate() throws Exception {
        UiDevice device = TestApis.ui().device();
        device.wait(Until.findObject(By.res("android:id/open_cross_profile")), 2000);
        String resolverTitle = device.findObject(
                By.res("android:id/open_cross_profile")).getText().toLowerCase(Locale.getDefault());
        return resolverTitle.contains("open") && resolverTitle.contains("notemptytestapp")
                && resolverTitle.contains("work");
    }
}
