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
package com.android.adservices.tests.ui.gaux.debugchannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.filters.FlakyTest;
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

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@RunWith(AndroidJUnit4.class)
@ScreenRecordRule.ScreenRecord
public class GaUxDebugChannelTest {

    private AdServicesCommonManager mCommonManager;

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

        mCommonManager = AdServicesCommonManager.get(sContext);

        // consent debug mode is turned on for this test class as we only care about the
        // first trigger (API call).
        UiUtils.enableConsentDebugMode();
        UiUtils.disableNotificationFlowV2();
        UiUtils.disableOtaStrings();

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

    /** Verify that the API returns false when API is disabled. */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testApiDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.turnOffEnableAdsServicesAPI();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isFalse();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Assert.fail();
                    }
                });

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.GA_UX);

        UiUtils.turnOnEnableAdsServicesAPI();
    }

    /** Verify that entry point disabled can not trigger consent notification. */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testEntryPointDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(false)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false, /* isGa */
                UiConstants.UX.GA_UX);
    }

    /** Verify that when request sent from entry point, we won't trigger notification. */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testFromEntryPointRequest() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsEuDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                true,
                UiConstants.UX.GA_UX);
    }

    /** Verify that non-adult account can not trigger consent notification. */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testNonAdultAccount() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.GA_UX);
    }

    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testGaRowAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.GA_UX);
    }

    /**
     * Verify that for GA, ROW devices with zeroed-out AdId, the GA EU notification is displayed.
     */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testGaRowAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                true,
                UiConstants.UX.GA_UX);
    }

    /**
     * Verify that for GA, EU devices with non zeroed-out AdId, the GA EU notification is displayed.
     */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testGaEuAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsEuDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                true,
                UiConstants.UX.GA_UX);
    }

    /** Verify that for GA, EU devices with zeroed-out AdId, the EU notification is displayed. */
    @Test
    @FlakyTest(bugId = 297347345)
    public void testGaEuAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsEuDevice();
        UiUtils.enableGa();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                true,
                UiConstants.UX.GA_UX);
    }
}
