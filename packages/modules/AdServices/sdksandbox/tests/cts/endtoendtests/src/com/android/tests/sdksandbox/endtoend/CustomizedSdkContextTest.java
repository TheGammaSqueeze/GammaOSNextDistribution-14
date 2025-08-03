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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.SdkLifecycleHelper;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
public class CustomizedSdkContextTest extends SandboxKillerBeforeTest {
    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";
    private static final String SDK_NAME_2 = "com.android.emptysdkprovider";

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule(order = 2)
    public final DeviceConfigStateChangerRule customizedSdkContextEnabledRule =
            new DeviceConfigStateChangerRule(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .getTargetContext(),
                    DeviceConfig.NAMESPACE_ADSERVICES,
                    "sdksandbox_customized_sdk_context_enabled",
                    "true");

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
        mSdkLifecycleHelper.unloadSdk(SDK_NAME_2);
    }

    @Test
    public void testStoragePaths() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        mSdk.testStoragePaths();
    }

    @Test
    public void testSdkPermissions() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        // Collect list of permissions requested by sdk sandbox
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final PackageInfo sdkSandboxPackage =
                pm.getPackageInfo(
                        pm.getSdkSandboxPackageName(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        // Verify sdk context has the same permissions granted
        loadSdk();
        for (int i = 0; i < sdkSandboxPackage.requestedPermissions.length; i++) {
            for (int j = 0; j < 2; j++) {
                boolean useApplicationContext = (j == 0);
                final String permissionName = sdkSandboxPackage.requestedPermissions[i];

                boolean result = mSdk.isPermissionGranted(permissionName, useApplicationContext);
                assertWithMessage(
                                "Sdk does not have permission: "
                                        + permissionName
                                        + ". useApplicationContext: "
                                        + useApplicationContext)
                        .that(result)
                        .isTrue();
            }
        }
    }

    /** Test that sdk context instances are different while application context is same */
    @Test
    public void testSdkContextInstances() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadEmptySdk(); // So that sandbox does not die in the middle of test

        loadSdk();
        int contextHashCode = mSdk.getContextHashCode(false);
        int appContextHashCode = mSdk.getContextHashCode(true);

        mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        loadSdk();

        int contextHashCode2 = mSdk.getContextHashCode(false);
        int appContextHashCode2 = mSdk.getContextHashCode(true);

        // Ensure that sdk gets different instance of sdk context
        assertThat(contextHashCode).isNotEqualTo(contextHashCode2);
        // However, they have the same instance of application context
        assertThat(appContextHashCode).isEqualTo(appContextHashCode2);
    }

    /** Test that sdk context uses same userId as application context */
    @Test
    public void testSdkContextUserId() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        int contextUserId = mSdk.getContextUserId();

        assertThat(contextUserId).isEqualTo(mContext.getUserId());
    }

    @Test
    public void testClassloader() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        mSdk.checkClassloaders();
    }

    @Test
    public void testResourcesAndAssets() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        mSdk.checkResourcesAndAssets();
    }

    @Test
    public void testGetPackageName() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assertThat(mSdk.getPackageName()).isEqualTo(pm.getSdkSandboxPackageName());
    }

    @Test
    public void testGetOpPackageName() throws Exception {
        assumeTrue("Test is meant for U+ devices only", SdkLevel.isAtLeastU());

        loadSdk();
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assertThat(mSdk.getOpPackageName()).isEqualTo(pm.getSdkSandboxPackageName());
    }

    private void loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdk = ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    private void loadEmptySdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_2, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }
}
