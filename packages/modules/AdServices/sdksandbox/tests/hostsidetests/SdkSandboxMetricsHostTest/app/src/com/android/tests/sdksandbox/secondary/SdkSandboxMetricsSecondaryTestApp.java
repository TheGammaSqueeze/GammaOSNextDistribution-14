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

package com.android.tests.sdksandbox.secondary;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.tests.sdkprovider.crashtest.ICrashTestSdkApi;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SdkSandboxMetricsSecondaryTestApp {

    private SdkSandboxManager mSdkSandboxManager;
    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.crashtest";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
    }

    @Test
    public void startAndCrashSdkSandbox() throws Exception {
        mRule.getScenario();
        generateSdkSandboxCrash();
    }

    private void generateSdkSandboxCrash() throws Exception {
        final CountDownLatch deathLatch = new CountDownLatch(1);
        // Avoid being killed when sandbox crashes
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, deathLatch::countDown);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        // Start and crash the SDK sandbox
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final ICrashTestSdkApi sdk =
                ICrashTestSdkApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
        sdk.triggerCrash();
        assertThat(deathLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
