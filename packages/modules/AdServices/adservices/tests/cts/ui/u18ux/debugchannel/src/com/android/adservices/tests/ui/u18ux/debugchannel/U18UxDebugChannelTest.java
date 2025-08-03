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

package com.android.adservices.tests.ui.u18ux.debugchannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;


import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** CTS test for U18 users */
@RunWith(AndroidJUnit4.class)
@ScreenRecordRule.ScreenRecord
public class U18UxDebugChannelTest {

    private static AdServicesCommonManager sCommonManager;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private UiDevice mDevice;
    private String mTestName;
    private OutcomeReceiver<Boolean, Exception> mCallback;
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        UiUtils.resetAdServicesConsentData(sContext);

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiUtils.enableNotificationPermission();
        UiUtils.disableNotificationFlowV2();
        UiUtils.disableOtaStrings();

        sCommonManager = AdServicesCommonManager.get(sContext);

        UiUtils.enableConsentDebugMode();
        mCallback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isTrue();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Assert.fail();
                    }
                };

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        mDevice.pressHome();

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testEntrypointDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(false)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(false)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothFalseAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.enableU18();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setU18Account(false)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, false, false, UiConstants.UX.U18_UX);
    }
}
