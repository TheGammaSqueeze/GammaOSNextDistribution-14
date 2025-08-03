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

package com.android.adservices.service.ui.enrollment;

import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import android.content.Context;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.ReconsentNotificationChannel;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

public class ReconsentNotificationChannelTest {
    private final ReconsentNotificationChannel mReconsentNotificationChannel =
            new ReconsentNotificationChannel();

    @Mock private Context mContext;
    @Mock private PrivacySandboxUxCollection mPrivacySandboxUxCollection;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;
    @Mock private AdServicesApiConsent mAdServicesApiConsent;
    private MockitoSession mStaticMockSession;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(AdServicesApiConsent.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        // Do not trigger real notifications.
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class), anyBoolean(), anyBoolean()));
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void isEligibleTest_nonBetaUser() {
        // Non-Beta users can't enroll through the reconsent mechanism.
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_nonManualOptOutBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_manualOptOutBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_unknownOptOutBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(UNKNOWN).when(mConsentManager).getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_nonManualOptInBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_manualOptInBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_unknownOptInBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(UNKNOWN).when(mConsentManager).getUserManualInteractionWithConsent();

        assertThat(
                        mReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection, mConsentManager, mUxStatesManager))
                .isTrue();
    }

    @Test
    public void enrollTest_adIdDisabledConsentNotification() {
        doReturn(false).when(mConsentManager).isAdIdEnabled();

        mReconsentNotificationChannel.enroll(mContext, mConsentManager);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));
    }

    @Test
    public void enrollTest_adIdEnabledConsentNotification() {
        doReturn(true).when(mConsentManager).isAdIdEnabled();

        mReconsentNotificationChannel.enroll(mContext, mConsentManager);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(true), eq(true)),
                times(1));
    }
}
