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

package com.android.tests.sdksandbox;

import android.Manifest;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.AdServicesCommon;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxSmallModuleTestApp {

    private static final String TAG = "SdkSandboxSmallModuleTestApp";
    private static final String MODULE_PACKAGE_NAME = "com.android.adservices";
    private static final String[] AD_SERVICES = {
        AdServicesCommon.ACTION_TOPICS_SERVICE,
        AdServicesCommon.ACTION_CUSTOM_AUDIENCE_SERVICE,
        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
        AdServicesCommon.ACTION_MEASUREMENT_SERVICE,
        AdServicesCommon.ACTION_ADID_SERVICE,
        AdServicesCommon.ACTION_APPSETID_SERVICE,
        AdServicesCommon.ACTION_AD_SERVICES_COMMON_SERVICE,
    };

    @Rule public final Expect mExpect = Expect.create();

    private Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private static final String SDK_NAME = "com.android.emptysdkprovider";
    private SdkSandboxManager mSdkSandboxManager;

    /** This rule is defined to start an activity in the foreground to call the sandbox APIs */
    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(EmptyActivity.class);

    @Before
    public void setup() {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        mRule.getScenario();
    }

    @After
    public void tearDown() {}

    @Test
    public void installSmallModulePendingReboot() throws Exception {
        TestApp smallModule =
                new TestApp(
                        "SmallModule",
                        MODULE_PACKAGE_NAME,
                        1,
                        /*isApex=*/ true,
                        "com.android.adservices.smallmodule.apex");
        Install.single(smallModule).setStaged().commit();
    }

    @Test
    public void installFullModulePendingReboot() throws Exception {
        TestApp fullModule =
                new TestApp(
                        "FullModule",
                        MODULE_PACKAGE_NAME,
                        1,
                        /*isApex=*/ true,
                        "com.android.adservices.fullmodule.apex");
        Install.single(fullModule).setStaged().commit();
    }

    @Test
    public void testVerifyAdServicesAreAvailable_preSmallModuleInstall() throws Exception {
        // Before small module is installed, all ad services should be available
        for (String service : AD_SERVICES) {
            mExpect.withMessage("%s is available", service)
                    .that(isAdServiceAvailable(service))
                    .isTrue();
        }
    }

    @Test
    public void testVerifyAdServicesAreUnavailable_postSmallModuleInstall() throws Exception {
        // After small module is installed, all ad services should be unavailable
        for (String service : AD_SERVICES) {
            mExpect.withMessage("%s is available", service)
                    .that(isAdServiceAvailable(service))
                    .isFalse();
        }
    }

    @Test
    public void testLoadSdkWithAdServiceApk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testLoadSdkWithoutAdServiceApk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();

        LoadSdkException loadSdkException = callback.getLoadSdkException();
        mExpect.that(loadSdkException.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
        mExpect.that(loadSdkException.getMessage()).isEqualTo("SDK sandbox is disabled");
    }

    /** Query PackageManager for exported services from AdServices APK. */
    private boolean isAdServiceAvailable(String serviceName) throws Exception {
        PackageManager pm = mContext.getPackageManager();

        Intent serviceIntent = new Intent(serviceName);
        List<ResolveInfo> resolveInfos =
                pm.queryIntentServicesAsUser(
                        serviceIntent,
                        PackageManager.GET_SERVICES
                                | PackageManager.MATCH_SYSTEM_ONLY
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        UserHandle.SYSTEM);

        ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, serviceIntent.getAction());

        return serviceInfo != null;
    }
}
