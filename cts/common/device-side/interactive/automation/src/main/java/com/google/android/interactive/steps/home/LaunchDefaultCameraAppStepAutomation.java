
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

import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;
import android.util.Log;

@AutomationFor("com.google.android.interactive.steps.home.LaunchDefaultCameraAppStep")
public final class LaunchDefaultCameraAppStepAutomation implements Automation<Boolean> {
    private final Context sContext = TestApis.context().instrumentedContext();

    @Override
    public Boolean automate() throws Exception {
        sContext.startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage("com.google.android.GoogleCamera"));

        return true;
    }
}