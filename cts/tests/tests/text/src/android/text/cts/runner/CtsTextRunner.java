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

package android.text.cts.runner;

import android.content.Intent;
import android.os.Bundle;
import android.support.test.uiautomator.UiDevice;

import androidx.test.runner.AndroidJUnitRunner;

/**
 * TestRunner for clearing system dialogs.
 *
 * Copied from UiRenderingRunner.
 */
public class CtsTextRunner extends AndroidJUnitRunner {

    @Override
    protected void waitForActivitiesToComplete() {
        // No.
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        final UiDevice device = UiDevice.getInstance(this);
        try {
            device.wakeUp();
            device.executeShellCommand("wm dismiss-keyguard");
            getContext().sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception e) {
        }
    }

    @Override
    public void onDestroy() {
        // Ok now wait if necessary
        super.waitForActivitiesToComplete();

        super.onDestroy();
    }
}
