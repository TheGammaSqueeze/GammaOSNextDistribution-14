/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.app.UiAutomation;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class FreezeRotationRule extends BeforeAfterRule {
    private final UiAutomation mUiAutomation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation();

    private final int mRotation;

    public FreezeRotationRule(int rotation) {
        mRotation = rotation;
    }

    public FreezeRotationRule() {
        this(UiAutomation.ROTATION_FREEZE_0);
    }

    @Override
    protected void onBefore(Statement base, Description description) throws Throwable {
        mUiAutomation.setRotation(mRotation);
    }

    @Override
    protected void onAfter(Statement base, Description description) throws Throwable {
        mUiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE);
    }
}
