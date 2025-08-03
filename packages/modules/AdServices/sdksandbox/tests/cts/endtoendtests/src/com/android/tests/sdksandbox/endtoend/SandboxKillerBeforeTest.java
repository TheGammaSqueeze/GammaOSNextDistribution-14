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

package com.android.tests.sdksandbox.endtoend;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallback;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Ensure to kill sandbox before tests to avoid race conditions. */
@RunWith(JUnit4.class)
public abstract class SandboxKillerBeforeTest {
    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void killSandboxBeforeTest() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        killSandboxIfExists();
    }

    // Returns true if the sandbox was already likely existing, false otherwise.
    protected boolean killSandboxIfExists() throws Exception {
        FakeSdkSandboxProcessDeathCallback callback = new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback);
        killSandbox();

        return callback.waitForSandboxDeath();
    }

    protected void killSandbox() throws Exception {
        // TODO(b/241542162): Avoid using reflection as a workaround once test apis can be run
        //  without issue.
        mSdkSandboxManager.getClass().getMethod("stopSdkSandbox").invoke(mSdkSandboxManager);
    }
}
