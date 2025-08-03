/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.sdksandbox.disablede2e;

import android.Manifest;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.ConfigListener;
import android.app.sdksandbox.testutils.DeviceConfigUtils;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxManagerDisabledTest {

    @Rule
    public final ActivityScenarioRule<TestActivity> mRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private ActivityScenario<TestActivity> mScenario;

    private SdkSandboxManager mSdkSandboxManager;
    private ConfigListener mConfigListener;
    private DeviceConfigUtils mDeviceConfigUtils;
    private String mInitialDisableSdkSandboxValue;

    @Before
    public void setup() throws Exception {
        // Sandbox is enabled on emulators irrespective of the killswitch
        Assume.assumeFalse(isEmulator());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG);
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mScenario = mRule.getScenario();

        mConfigListener = new ConfigListener();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_ADSERVICES, context.getMainExecutor(), mConfigListener);
        mDeviceConfigUtils =
                new DeviceConfigUtils(mConfigListener, DeviceConfig.NAMESPACE_ADSERVICES);

        mInitialDisableSdkSandboxValue =
                DeviceConfig.getProperty(DeviceConfig.NAMESPACE_ADSERVICES, "disable_sdk_sandbox");
        mDeviceConfigUtils.deleteProperty("disable_sdk_sandbox");
    }

    @After
    public void tearDown() throws Exception {
        if (mDeviceConfigUtils != null) {
            mDeviceConfigUtils.resetToInitialValue(
                    "disable_sdk_sandbox", mInitialDisableSdkSandboxValue);
        }
    }

    @Test
    public void testSdkSandboxDisabledErrorCode() throws Exception {
        mDeviceConfigUtils.setProperty("disable_sdk_sandbox", "true");

        String sdkName = "com.android.ctssdkprovider";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
    }

    private static boolean isEmulator() {
        return SystemProperties.getBoolean("ro.boot.qemu", false);
    }
}
