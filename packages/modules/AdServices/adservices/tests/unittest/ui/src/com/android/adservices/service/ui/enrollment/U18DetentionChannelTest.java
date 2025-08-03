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

import static com.android.adservices.service.FlagsConstants.KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

import android.content.Context;

import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.U18DetentionChannel;
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

public class U18DetentionChannelTest {

    private U18DetentionChannel mU18DetentionChannel;
    @Mock private Context mContext;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private ConsentManager mConsentManager;
    private MockitoSession mStaticMockSession;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
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
        doReturn(true).when(mUxStatesManager).getFlag(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED);

        mU18DetentionChannel = new U18DetentionChannel();
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void isEligibleTest_U18UxDetentionChannelDisabled() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED);

        assertThat(
                        mU18DetentionChannel.isEligible(
                                PrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_detainedGaUser() {
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                        mU18DetentionChannel.isEligible(
                                PrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_detainedBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();

        assertThat(
                        mU18DetentionChannel.isEligible(
                                PrivacySandboxUxCollection.U18_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_nonU18User() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                        mU18DetentionChannel.isEligible(
                                PrivacySandboxUxCollection.BETA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_nonBetUxCheck() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                        mU18DetentionChannel.isEligible(
                                PrivacySandboxUxCollection.GA_UX,
                                mConsentManager,
                                mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_noReenrollmentNotification() {
        mU18DetentionChannel.enroll(mContext, mConsentManager);

        verify(
                () -> ConsentNotificationJobService.schedule(any(), anyBoolean(), anyBoolean()),
                never());
    }

    @Test
    public void enrollTest_targetingApisDisabled() {
        mU18DetentionChannel.enroll(mContext, mConsentManager);

        verify(mConsentManager).disable(mContext, AdServicesApiType.FLEDGE);
        verify(mConsentManager).disable(mContext, AdServicesApiType.TOPICS);
        verify(mConsentManager, never()).disable(mContext, AdServicesApiType.MEASUREMENTS);
    }
}
