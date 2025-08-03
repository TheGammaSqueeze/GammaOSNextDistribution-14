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

package com.android.tests.sdksandbox.endtoend;

import static android.content.pm.Flags.FLAG_ALLOW_SDK_SANDBOX_QUERY_INTENT_ACTIVITIES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.ctssdkprovider.ICtsSdkProviderApi;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxCapabilitiesTest extends SandboxKillerBeforeTest {
    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule(order = 2)
    public final DeviceConfigStateChangerRule customizedSdkContextEnabledRule =
            new DeviceConfigStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    "sdksandbox_customized_sdk_context_enabled",
                    "true");

    @Rule(order = 3)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final SdkLifecycleHelper mSdkLifecycleHelper = new SdkLifecycleHelper(mContext);

    private SdkSandboxManager mSdkSandboxManager;
    private ICtsSdkProviderApi mSdk;

    @Before
    public void setup() {
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        activityScenarioRule.getScenario();
    }

    @After
    public void tearDown() {
        mSdkLifecycleHelper.unloadSdk(SDK_NAME_1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ALLOW_SDK_SANDBOX_QUERY_INTENT_ACTIVITIES)
    public void testQueryLauncherActivity() throws Exception {
        assumeTrue("Test is meant for V+ devices only", SdkLevel.isAtLeastV());

        loadSdk();

        assertThat(mSdk.getLauncherActivityCount()).isEqualTo(1);
    }

    private void loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdk = ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
