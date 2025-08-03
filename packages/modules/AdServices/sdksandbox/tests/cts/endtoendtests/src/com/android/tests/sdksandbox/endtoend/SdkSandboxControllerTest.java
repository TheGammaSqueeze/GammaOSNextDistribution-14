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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ctssdkprovider.ICtsSdkProviderApi;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** End-to-end tests of {@link SdkSandboxController} APIs. */
@RunWith(JUnit4.class)
public class SdkSandboxControllerTest extends SandboxKillerBeforeTest {

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 2)
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private static final String SDK_NAME = "com.android.ctssdkprovider";

    private ActivityScenario<TestActivity> mScenario;
    private SdkSandboxManager mSdkSandboxManager;
    private ICtsSdkProviderApi mSdk;
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() {
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        mScenario = activityScenarioRule.getScenario();
    }

    @After
    public void tearDown() {
        try {
            mSdkSandboxManager.unloadSdk(SDK_NAME);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testGetClientPackageName() throws Exception {
        loadSdk();
        assertThat(mSdk.getClientPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_CLIENT_IMPORTANCE_LISTENER)
    public void testSdkDetectsAppForegroundState() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        loadSdk();
        mSdk.createAndRegisterSdkSandboxClientImportanceListener();

        mSdk.waitForStateChangeDetection(
                /*expectedForegroundValue=*/ 0, /*expectedBackgroundValue=*/ 0);

        // Bring the app to the background by destroying the activity.
        mScenario.moveToState(Lifecycle.State.DESTROYED);
        mSdk.waitForStateChangeDetection(
                /*expectedForegroundValue=*/ 0, /*expectedBackgroundValue=*/ 1);

        // Bring the app to the foreground again by starting an activity.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Intent intent = new Intent(context, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        mSdk.waitForStateChangeDetection(
                /*expectedForegroundValue=*/ 1, /*expectedBackgroundValue=*/ 1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_CLIENT_IMPORTANCE_LISTENER)
    public void testUnregisterSdkSandboxClientImportanceListener() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        loadSdk();
        mSdk.createAndRegisterSdkSandboxClientImportanceListener();

        mSdk.waitForStateChangeDetection(
                /*expectedForegroundValue=*/ 0, /*expectedBackgroundValue=*/ 0);

        mSdk.unregisterSdkSandboxClientImportanceListener();

        // Bring the app to the background by destroying the activity.
        mScenario.moveToState(Lifecycle.State.DESTROYED);

        // Wait a bit to ensure that the sandbox does not detect any change.
        Thread.sleep(1000);
        mSdk.waitForStateChangeDetection(
                /*expectedForegroundValue=*/ 0, /*expectedBackgroundValue=*/ 0);
    }

    private void loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdk = ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
