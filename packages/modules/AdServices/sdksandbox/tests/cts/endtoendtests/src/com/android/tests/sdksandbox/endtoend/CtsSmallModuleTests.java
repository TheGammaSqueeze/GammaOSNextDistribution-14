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

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class CtsSmallModuleTests extends SandboxKillerBeforeTest {
    private static final String SDK_NAME = "com.android.emptysdkprovider";

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule public final Expect mExpect = Expect.create();

    private SdkSandboxManager mSdkSandboxManager;
    private Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws Exception {
        assumeTrue("Device supports Small AdServices module", isSmallModuleSupported());
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        mActivityScenarioRule.getScenario();
    }

    @Test
    public void testLoadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();

        LoadSdkException loadSdkException = callback.getLoadSdkException();
        mExpect.that(loadSdkException.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
        mExpect.that(loadSdkException.getMessage()).isEqualTo("SDK sandbox is disabled");
    }

    private boolean isSmallModuleSupported() throws Exception {
        PackageManager pm = mContext.getPackageManager();

        // On small module, TOPIC_SERVICE is unavailable
        Intent serviceIntent = new Intent("android.adservices.TOPICS_SERVICE");
        List<ResolveInfo> resolvedInfos =
                pm.queryIntentServices(
                        serviceIntent,
                        PackageManager.GET_SERVICES
                                | PackageManager.MATCH_SYSTEM_ONLY
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);

        boolean serviceFound = resolvedInfos != null && !resolvedInfos.isEmpty();

        // service is missing on small module
        return !serviceFound;
    }
}
