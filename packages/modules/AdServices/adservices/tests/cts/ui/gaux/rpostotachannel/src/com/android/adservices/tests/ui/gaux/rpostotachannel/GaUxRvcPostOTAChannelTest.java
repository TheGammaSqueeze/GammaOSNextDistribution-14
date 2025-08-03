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
package com.android.adservices.tests.ui.gaux.graduationchannel;

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
import com.android.adservices.tests.ui.libs.UiConstants.UX;
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
public class GaUxRvcPostOTAChannelTest {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        UiUtils.resetAdServicesConsentData(sContext);

        UiUtils.enableNotificationPermission();
        UiUtils.enableGa();
        UiUtils.enableRvc();
        UiUtils.enableRvcNotification();
        UiUtils.disableNotificationFlowV2();
        UiUtils.disableOtaStrings();

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(sContext);

        // General purpose callback used for expected success calls.
        mCallback =
                new OutcomeReceiver<Boolean, Exception>() {
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

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /**
     * Verify that the R Post OTA notification is displayed after 1st U18 notification if msmt is
     * opt in.
     */
    @Test
    public void testU18ToGAForRPostOTA_optInMsmt() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        // R 18+ user receives 1st U18 notification
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Mock user is ota from Rvc by enabling consent manager ota debug mode
        UiUtils.setConsentManagerOtaDebugMode();
        // Enable consent manager debug mode to mock user opt-in msmt API
        UiUtils.setConsentManagerDebugMode();
        // Disable RVC UX to mock user is not eligible RVC UX post OTA
        UiUtils.disableRvc();
        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // User receive 2nd GA notification post R OTA
        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.GA_UX);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // Notifications should not be shown twice
        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
        mDevice.pressHome();

        // User should be able to open GA UX after notification
        UiUtils.resetConsentManagerDebugMode();
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                mDevice,
                UiConstants.UX.GA_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
    }

    /**
     * Verify that the R Post OTA notification is not displayed after 1st U18 notification if msmt
     * is opt out.
     */
    @Test
    public void testU18ToGAForRPostOTA_optOutMsmt() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        // R 18+ user receives 1st U18 notification
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Open settings page and opt out msmt api
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                mDevice,
                UiConstants.UX.RVC_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
        mDevice.pressHome();

        // Mock user is ota from Rvc by enabling consent manager ota debug mode
        UiUtils.setConsentManagerOtaDebugMode();
        // Disable RVC UX to mock user is not eligible RVC UX post OTA
        UiUtils.disableRvc();
        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
        mDevice.pressHome();

        // User should be able to open GA UX post OTA
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                mDevice,
                UiConstants.UX.GA_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
    }
}
