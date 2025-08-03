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

import com.android.interactive.ScreenshotUtil;
import com.android.interactive.Step;

/**
 * A {@link Step} where the user is asked to provide a screenshot to
 * ensure the test meets the specifications.
 *
 * <p>This will present "Pass Screenshot" and "Fail Screenshot" buttons.
 */
public abstract class ScreenshotValidationStep extends Step<Boolean> {

    private final String mInstruction;
    private final String mFilename;

    protected ScreenshotValidationStep(String instruction, String filename) {
        mInstruction = instruction;
        mFilename = filename;
    }

    @Override
    public void interact() {
        show(mInstruction);
        addButton("Pass & Take Screenshot", () -> {
            ScreenshotUtil.captureScreenshot(mFilename);
            pass(true);
        });
        addButton("Fail & Take Screenshot", () -> {
            pass(false);
        });
    }
}
