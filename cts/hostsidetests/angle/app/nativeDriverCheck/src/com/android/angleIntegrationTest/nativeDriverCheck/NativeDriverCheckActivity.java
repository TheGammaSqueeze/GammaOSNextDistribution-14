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

package com.android.angleintegrationtest.nativedrivercheck;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.angleintegrationtest.common.GlesView;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NativeDriverCheckActivity {

    private static final String NATIVE_GL_RENDERER = "NATIVE_GL_RENDERER";

    /**
     * Metrics will be reported under the "status in progress" for test cases to be associated with
     * the running use cases.
     */
    private static final int INST_STATUS_IN_PROGRESS = 2;

    @Test
    public void checkNativeDriver() throws Exception {
        final GlesView glesView = new GlesView();

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Bundle returnBundle = new Bundle();
        returnBundle.putString(NATIVE_GL_RENDERER, glesView.getRenderer());
        instrumentation.sendStatus(INST_STATUS_IN_PROGRESS, returnBundle);
    }
}
