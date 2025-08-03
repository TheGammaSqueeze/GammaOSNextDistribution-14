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

package com.android.sdksandbox.shared.app2;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxTestSharedApp2  {

    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME = "com.android.testinterface";
    private static final String SDK_PACKAGE_NAME = "com.android.testcode";

    private SdkSandboxManager mSdkSandboxManager;

    @Rule
    public final ActivityScenarioRule<SdkSandboxEmptyActivity> mRule =
            new ActivityScenarioRule<>(SdkSandboxEmptyActivity.class);

    @Before
    public void setup() {
        Context sContext = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = sContext.getSystemService(
                SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
    }

    @Test
    public void testLoadSdkIsSuccessful() {
        mRule.getScenario();

        Bundle params = new Bundle();
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE_NAME, params, Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testRegisterAppOwedSdkSandboxInterfacesBeforeAppDeath() {
        mRule.getScenario();

        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()));
        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).hasSize(1);
    }

    @Test
    public void testGetAppOwedSdkSandboxInterfacesOnAppDeath() {
        mRule.getScenario();

        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).hasSize(0);
    }
}
