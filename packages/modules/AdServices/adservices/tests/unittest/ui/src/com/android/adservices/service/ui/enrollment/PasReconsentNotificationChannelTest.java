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

import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.consent.AdServicesApiConsent.GIVEN;
import static com.android.adservices.service.consent.AdServicesApiConsent.REVOKED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import android.content.Context;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.PasReconsentNotificationChannel;
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

public class PasReconsentNotificationChannelTest {
    private PasReconsentNotificationChannel mPasReconsentNotificationChannel;

    @Mock private PrivacySandboxUxCollection mPrivacySandboxUxCollection;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;

    private MockitoSession mMockitoSession;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        // Do not trigger real notifications.
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class), anyBoolean(), anyBoolean()));
        doReturn(true).when(mUxStatesManager).getFlag(KEY_PAS_UX_ENABLED);

        mPasReconsentNotificationChannel = new PasReconsentNotificationChannel();
    }

    @After
    public void teardown() throws IOException {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void isEligibleTest_pasNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasPasNotificationDisplayed();

        assertThat(
                        mPasReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_NoManualInteraction_consentRevoked_pas() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasU18NotificationDisplayed();
        doReturn(false).when(mConsentManager).wasPasNotificationDisplayed();
        doReturn(ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();
        doReturn(REVOKED).when(mConsentManager).getConsent(any());

        assertThat(
                        mPasReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_hasManualInteraction_consentRevoked_pas() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasU18NotificationDisplayed();
        doReturn(false).when(mConsentManager).wasPasNotificationDisplayed();
        doReturn(ConsentManager.MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();
        doReturn(REVOKED).when(mConsentManager).getConsent(any());

        assertThat(
                        mPasReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_consentGiven_pas() {
        doReturn(false).when(mConsentManager).wasPasNotificationDisplayed();
        doReturn(GIVEN).when(mConsentManager).getConsent(any());

        assertThat(
                        mPasReconsentNotificationChannel.isEligible(
                                mPrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }
}
