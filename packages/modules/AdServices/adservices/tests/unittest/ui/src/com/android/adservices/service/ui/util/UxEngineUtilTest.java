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

package com.android.adservices.service.ui.util;

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.UNKNOWN;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.UNSUPPORTED_UX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;
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
import java.util.UUID;

public class UxEngineUtilTest {
    @Mock
    private UxStatesManager mUxStatesManager;
    @Mock
    private ConsentManager mConsentManager;
    @Mock
    private AdServicesApiConsent mAdServicesApiConsent;
    @Mock
    private Context mContext;
    @Mock
    private Flags mFlags;
    @Mock
    private SharedPreferences mSharedPreferences;
    @Mock
    private SharedPreferences.Editor mEditor;

    private MockitoSession mStaticMockSession;
    private UxEngineUtil mUxEngineUtil;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(AdServicesApiConsent.class)
                        .spyStatic(PackageChangedReceiver.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        // Default states for testing supported UXs.
        doReturn(true).when(mConsentManager).isEntryPointEnabled();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED);

        mUxEngineUtil = UxEngineUtil.getInstance();

        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());

        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);

        // No real background task invocations.
        ExtendedMockito.doReturn(true).when(
                () ->
                        PackageChangedReceiver.enableReceiver(any(), any()));
        ExtendedMockito.doNothing().when(
                () ->
                        BackgroundJobsManager.scheduleAllBackgroundJobs(any()));
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    // ====================================================================
    // getEligibleUxCollectionTest
    // ====================================================================
    @Test
    public void getEligibleUxCollectionTest_adServicesDisabled() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(UNSUPPORTED_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_entryPointDisabled() {
        doReturn(false).when(mConsentManager).isEntryPointEnabled();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(UNSUPPORTED_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_adultUserGaFlagOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(GA_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_adultUserGaFlagOff() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(PrivacySandboxUxCollection.BETA_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_u18UserU18FlagOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mConsentManager).isU18Account();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(PrivacySandboxUxCollection.U18_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_u18UserU18FlagOff() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mConsentManager).isU18Account();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(UNSUPPORTED_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_gaAndU18Eligible() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();
        doReturn(true).when(mConsentManager).isU18Account();

        // U18 UX should have higher priority than adult UX.
        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(PrivacySandboxUxCollection.U18_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_betaAndU18Eligible() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        doReturn(true).when(mConsentManager).isAdultAccount();
        doReturn(true).when(mConsentManager).isU18Account();

        // U18 UX should have higher priority than adult UX.
        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(PrivacySandboxUxCollection.U18_UX);
    }

    @Test
    public void getEligibleUxCollectionTest_nonAdultAndNonU18() {
        doReturn(false).when(mConsentManager).isAdultAccount();
        doReturn(false).when(mConsentManager).isU18Account();

        assertThat(mUxEngineUtil.getEligibleUxCollection(mConsentManager, mUxStatesManager))
                .isEqualTo(UNSUPPORTED_UX);
    }

    // ====================================================================
    // getEligibleEnrollmentChannelTest_gaUx
    // ====================================================================
    @Test
    public void getEligibleEnrollmentChannelTest_gaUxConsentDebugModeOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE);

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxConsentResetTokenPresent() {
        doReturn(UUID.randomUUID().toString()).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(true).when(mEditor).commit();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.GA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxNotificationNeverDisplayed() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxManaulOptInBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxNoManaulOptInBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxUnknownOptInBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(true).when(mAdServicesApiConsent).isGiven();
        doReturn(UNKNOWN).when(mConsentManager).getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxManaulOptOutBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isNull();
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxNoManaulOptOutBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(NO_MANUAL_INTERACTIONS_RECORDED)
                .when(mConsentManager)
                .getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isEqualTo(GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxUnknownOptOutBetaUser() {
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(mAdServicesApiConsent).when(mConsentManager).getConsent();
        doReturn(false).when(mAdServicesApiConsent).isGiven();
        doReturn(UNKNOWN).when(mConsentManager).getUserManualInteractionWithConsent();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isNull();
    }

    @Test
    public void getEligibleEnrollmentChannelTest_gaUxGraduationDisabled() {
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        GA_UX, mConsentManager, mUxStatesManager))
                .isNull();
    }

    // ====================================================================
    // getEligibleEnrollmentChannelTest_betaUx
    // ====================================================================
    @Test
    public void getEligibleEnrollmentChannelTest_betaUxConsentDebugModeOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE);

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.BETA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_betaUxConsentResetTokenPresent() {
        doReturn(UUID.randomUUID().toString()).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(true).when(mEditor).commit();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.BETA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_betaUxNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.BETA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_betaUxNotificationNeverDisplayed() {
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.BETA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_betaUxU18User() {
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.BETA_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isNull();
    }

    // ====================================================================
    // getEligibleEnrollmentChannelTest_U18Ux
    // ====================================================================
    @Test
    public void getEligibleEnrollmentChannelTest_u18UxConsentDebugModeOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE);

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_u18UxConsentResetTokenPresent() {
        doReturn(UUID.randomUUID().toString()).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(true).when(mEditor).commit();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_RESET_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_u18UxNotificationAlreadyDisplayed() {
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_u18UxNotificationNeverDisplayed() {
        doReturn(false).when(mConsentManager).wasU18NotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_u18UxDetention() {
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isEqualTo(U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL);
    }

    @Test
    public void getEligibleEnrollmentChannelTest_u18UxBetaUser() {
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        PrivacySandboxUxCollection.U18_UX,
                        mConsentManager,
                        mUxStatesManager))
                .isNull();
    }

    // =============================================================================================
    // getEligibleEnrollmentChannelTest_unsupportedUx
    // =============================================================================================
    @Test
    public void getEligibleEnrollmentChannelTest_unsupportedUxConsentDebugModeOn() {
        doReturn(true).when(mUxStatesManager).getFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE);

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        UNSUPPORTED_UX, mConsentManager, mUxStatesManager))
                .isNull();
    }

    @Test
    public void getEligibleEnrollmentChannelTest_unsupportedUxNotificationNeverDisplayed() {
        doReturn(false).when(mConsentManager).wasU18NotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();

        assertThat(
                mUxEngineUtil.getEligibleEnrollmentChannelCollection(
                        UNSUPPORTED_UX, mConsentManager, mUxStatesManager))
                .isNull();
    }

    // =============================================================================================
    // startBackgroundTasksUponConsent
    // =============================================================================================
    @Test
    public void startBackgroundTasksUponConsentTest_consentNotGiven() {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent();

        mUxEngineUtil.startBackgroundTasksUponConsent(UNSUPPORTED_UX, mContext, mFlags);

        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags), never()
        );

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), never()
        );
    }

    @Test
    public void startBackgroundTasksUponConsentTest_consentGiven() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();

        mUxEngineUtil.startBackgroundTasksUponConsent(GA_UX, mContext, mFlags);

        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags), times(1)
        );

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), times(1)
        );
    }

    @Test
    public void startBackgroundTasksUponConsentTest_u18UxConsentGiven() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(any(AdServicesApiType.class));
        mUxEngineUtil.startBackgroundTasksUponConsent(
                PrivacySandboxUxCollection.U18_UX, mContext, mFlags);

        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags), times(1));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mContext), times(1));
    }

    @Test
    public void startBackgroundTasksUponConsentTest_rvcUxConsentGiven() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(any(AdServicesApiType.class));
        mUxEngineUtil.startBackgroundTasksUponConsent(RVC_UX, mContext, mFlags);

        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags), times(1));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mContext), times(1));
    }
}
