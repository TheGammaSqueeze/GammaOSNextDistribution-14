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
package com.android.adservices.tests.ui.rvcux.alreadyenrolledchannel;

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
public class RvcUxAlreadyEnrolledChannelTest {

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

    /** Verify that the U18 ROW notification is displayed for RVC_UX. */
    @Test
    public void testU18NotificationDisplayedForRvcUX_row() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        // isEuTest does not matter for RVC_UX
        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Notifications should not be shown twice.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.RVC_UX);
        mDevice.pressHome();

        // Verify msmt API should be opted-in
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                mDevice,
                UiConstants.UX.RVC_UX,
                /* isOptIn= */ false,
                /* isFlipConsent= */ false,
                /* assertOptIn= */ true);
    }

    @Test
    public void testU18NotificationDisplayedForRvcUX_eu() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsEuDevice();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Notifications should not be shown twice.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.RVC_UX);
        mDevice.pressHome();

        // Verify msmt API should be opted-in
        AdservicesWorkflows.testSettingsPageFlow(
                sContext,
                mDevice,
                UiConstants.UX.RVC_UX,
                /* isOptIn= */ false,
                /* isFlipConsent= */ false,
                /* assertOptIn= */ true);
    }
}
