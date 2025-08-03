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
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.SettableFuture;

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
public class GaUxGraduationChannelTest {

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

        // Reset consent and thereby AdServices data before each test.
        UiUtils.refreshConsentResetToken();

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<Boolean, Exception>() {
                    @Override
                    public void onResult(Boolean result) {
                        responseFuture.set(result);
                    }

                    @Override
                    public void onError(Exception exception) {
                        responseFuture.setException(exception);
                    }
                });

        Boolean response = responseFuture.get();
        assertThat(response).isTrue();

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    public void testRowU18ToGaAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableU18();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates u18States =
                new AdServicesStates.Builder()
                        .setU18Account(true)
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(u18States, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.U18_UX);

        UiUtils.enableGa();
        AdservicesTestHelper.killAdservicesProcess(sContext);
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // No notifications should be shown as graduation channel is disabled.
        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
    }

    /**
     * Verify that for beta, ROW devices with non zeroed-out AdId, the beta ROW notification is
     * displayed.
     */
    @Test
    public void testRowU18ToBetaAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableU18();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates u18States =
                new AdServicesStates.Builder()
                        .setU18Account(true)
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(u18States, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.U18_UX);

        UiUtils.enableBeta();
        AdservicesTestHelper.killAdservicesProcess(sContext);
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // No notifications should be shown as there is no enrollment channel from U18 to Beta UX.
        AdservicesWorkflows.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.BETA_UX);
    }
}
