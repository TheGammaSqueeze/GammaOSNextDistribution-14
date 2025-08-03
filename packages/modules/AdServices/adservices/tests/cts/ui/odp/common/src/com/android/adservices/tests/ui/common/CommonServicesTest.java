/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.tests.ui.common;

import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.AdServicesStates;
import android.adservices.common.ConsentStatus;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Test for get adservices common states. */
@RunWith(AndroidJUnit4.class)
@ScreenRecordRule.ScreenRecord
public class CommonServicesTest {
    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String TEST_PACKAGE_NAME = "com.android.adservices.tests.ui.common";
    private static final String INVALID_PACKAGE_NAME = "invalidPackage";

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());
        UiUtils.resetAdServicesConsentData(sContext);
        UiUtils.enableNotificationPermission();
        UiUtils.enableGa();
        UiUtils.setFlipFlow(true);
        UiUtils.disableOtaStrings();
        UiUtils.setGetAdservicesCommonStatesServiceEnable(true);
        UiUtils.setGetAdservicesCommonStatesAllowList(TEST_PACKAGE_NAME);

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

    /** Verify that for GA, ROW devices get adservices common states of opt-in consent. */
    @Test
    @Ignore
    // TODO: b/327682322
    public void testGetAdservicesCommonStatesOptin() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.testClickNotificationFlow(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* ux type */ UX.GA_UX,
                /* isFlipFlow */ true,
                /* consent opt-in */ true);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.GIVEN);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.GIVEN);
    }

    /** Verify that for GA devices get adservices common states of opt-out consent. */
    @Test
    @Ignore
    // TODO: b/327682322
    public void testGetAdservicesCommonStatesOptOut() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();
        UiUtils.setFlipFlow(true);
        UiUtils.setAsRowDevice();
        UiUtils.setGetAdservicesCommonStatesServiceEnable(true);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.testClickNotificationFlow(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* ux type */ UX.GA_UX,
                /* isFlipFlow */ true,
                /* consent opt-in */ false);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.REVOKED);
    }

    /** Verify that for GA, ROW devices get adservices common states of opt-in consent. */
    @Test
    public void testGetAdservicesCommonStatesNotEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.setGetAdservicesCommonStatesServiceEnable(false);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.testClickNotificationFlow(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* ux type */ UX.GA_UX,
                /* isFlipFlow */ true,
                /* consent opt-in */ true);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
    }

    @Test
    public void testGetAdservicesCommonStatesNotAllowed() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.setGetAdservicesCommonStatesServiceEnable(false);
        UiUtils.setGetAdservicesCommonStatesAllowList(INVALID_PACKAGE_NAME);
        AdservicesTestHelper.killAdservicesProcess(sContext);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        try {
            AdServicesCommonStatesResponse commonStatesResponse =
                    adServicesCommonStatesResponse.get();
            assertThat(false).isTrue();
        } catch (Exception e) {
            assertThat("class " + e.getMessage())
                    .isEqualTo(
                            e.getCause().getClass()
                                    + ": "
                                    + SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
        }
    }

    private ListenableFuture<AdServicesCommonStatesResponse> getAdservicesCommonStates() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.getAdservicesCommonStates(
                            CALLBACK_EXECUTOR,
                            new AdServicesOutcomeReceiver<
                                    AdServicesCommonStatesResponse, Exception>() {
                                @Override
                                public void onResult(AdServicesCommonStatesResponse result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }
}
