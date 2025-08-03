/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.interactive.steps;

import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Nothing;
import com.android.interactive.Step;


 /**
  * A {@link Step} where the system launches an activity via an intent.
 * If the intent cannot be started, the system will report a failure.
 * <p>This will present "Launch" button.
 */
public abstract class LaunchIntentStep extends Step<Nothing> {
    private static final String TAG = LaunchIntentStep.class.getSimpleName();

    protected final String mInstruction;
    protected final Intent mIntent;

    protected LaunchIntentStep(String instruction, Intent intent) {
        mInstruction = instruction;
        mIntent = intent;
    }

    @Override
    public void interact() {
        show(mInstruction);
        addButton("Launch", () -> {
            startActivity(mIntent);
            close();
        });
    }

    protected void startActivity(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            TestApis.context().instrumentedContext().startActivity(mIntent);
            pass();
        } catch (RuntimeException ex) {
            fail(ex.getMessage());
        }
    }
}
