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

package com.android.adservices.service.ui;

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_POST_OTA_NOTIFICATION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RVC_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.BETA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.U18_UX;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.UNSUPPORTED_UX;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStates;
import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.common.PackageChangedReceiver;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.RvcUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;
import com.android.adservices.service.ui.util.UxEngineUtil;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;

public class UxEngineTest {

    @Mock
    private UxStatesManager mUxStatesManager;
    @Mock
    private ConsentManager mConsentManager;
    @Mock
    private Context mContext;
    @Mock
    private Flags mFlags;

    private UxEngine mUxEngine;

    private MockitoSession mStaticMockSession;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(UiStatsLogger.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(PackageChangedReceiver.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());

        ExtendedMockito.doReturn(mFlags)
                .when(FlagsFactory::getFlags);

        // Do not trigger real notifications.
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(), anyBoolean(), anyBoolean()));

        // No real background task invocations.
        ExtendedMockito.doReturn(true).when(
                () ->
                        PackageChangedReceiver.enableReceiver(any(), any()));
        ExtendedMockito.doNothing().when(
                () ->
                        BackgroundJobsManager.scheduleAllBackgroundJobs(any()));
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(any()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logEntryPointClicked());

        doReturn(true).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED);
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManager)
                .getConsent(any(AdServicesApiType.class));

        mUxEngine =
                new UxEngine(
                        mContext, mConsentManager, mUxStatesManager, UxEngineUtil.getInstance());
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    // Unsupported UX selected due to top level UX flag disabled, which results in no enrollment
    // actions and no more UX checks.
    @Test
    public void startTest_uxDisabled() {
        doReturn(false).when(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager, never()).isEntryPointEnabled();

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    // Unsupported UX selected due to entry point disabled, which results in no enrollment
    // actions and no more UX checks.
    @Test
    public void startTest_entryPointDisabled() {
        boolean entryPointEnabled = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(false).when(mConsentManager).isEntryPointEnabled();

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager, never()).getFlag(KEY_U18_UX_ENABLED);

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)),
                times(0));
    }

    // U18 UX not selected due to feature flag disabled, which results in no U18 enrollment
    // action and failed U18 UX check.
    @Test
    public void startTest_u18FeatureDisabled() {
        boolean entryPointEnabled = true;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(false).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager, never()).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);
    }

    // U18 UX not selected due to ineligible account type, which results in no U18 enrollment
    // action and failed U18 UX check.
    @Test
    public void startTest_isNotU18Account() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);
    }

    // U18 UX selected, which results in U18 enrollment action and no more UX checks.
    @Test
    public void startTest_u18UxEligible() {
        boolean entryPointEnabled = true;
        boolean isU18Account = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(true)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager, never()).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        verify(mConsentManager).setUx(U18_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        U18_UX,
                        U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)));
        ExtendedMockito.verify(() -> PackageChangedReceiver.enableReceiver(mContext, mFlags));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), never());
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mContext));
    }

    // RVC UX selected.
    @Test
    public void startTest_rvcSelected() {
        boolean entryPointEnabled = true;
        boolean adIdEnabled = true;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_RVC_UX_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // RVC UX logic.
        verify(mUxStatesManager).getFlag(KEY_RVC_UX_ENABLED);

        // GA UX logic.
        verify(mUxStatesManager, never()).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);

        verify(mConsentManager).setUx(RVC_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        RVC_UX,
                        RvcUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)));
        ExtendedMockito.verify(() -> PackageChangedReceiver.enableReceiver(mContext, mFlags));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), never());
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mContext));
    }

    // GA UX not selected due to feature flag being disabled, which results in no GA UX
    // enrollment action and failed GA UX check.
    @Test
    public void startTest_gaUxFeatureDisabled() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic, twice since Beta UX was also checked.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        verify(mConsentManager).setUx(BETA_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        BETA_UX,
                        BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);
    }

    // GA UX not selected due to not being an adult account, which results in no GA UX
    // enrollment action and failed GA UX check.
    @Test
    public void startTest_gaUxNotAdultAccount() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic, twice since Beta UX was also checked.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        verify(mConsentManager).isAdultAccount();

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);
    }

    // GA UX selected, which results in GA UX enrollment action and no more UX checks.
    @Test
    public void startTest_gaUxEligible() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        verify(mConsentManager).isAdultAccount();

        verify(mConsentManager).setUx(GA_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        GA_UX, GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)));
        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext)
        );
    }

    // GA UX not selected due to not being an adult account, which results in no GA UX
    // enrollment action and failed GA UX check.
    @Test
    public void startTest_betaUxNotAdultAccount() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic, twice since Beta UX was also checked.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        // Only called once since GA feature has to be off so the account check in GA UX can't
        // take place.
        verify(mConsentManager).isAdultAccount();

        // UX and channel are not set unless a valid channel exists.
        verify(mConsentManager, never()).setUx(UNSUPPORTED_UX);
        verify(mConsentManager, never()).setEnrollmentChannel(UNSUPPORTED_UX, null);

        ExtendedMockito.verify(
                () -> ConsentNotificationJobService.schedule(any(), anyBoolean(), anyBoolean()),
                never());
    }

    // Beta UX selected, which results in Beta UX enrollment action and no more UX checks.
    @Test
    public void startTest_betaUxEligible() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        verify(mConsentManager).isAdultAccount();

        verify(mConsentManager).setUx(BETA_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        BETA_UX,
                        BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)));
        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags));

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext)
        );
    }

    // Beta UX selected, which results in Beta UX enrollment action and no more UX checks. But
    // the call is the result of an entry point request and no enrollment can happen.
    @Test
    public void startTest_betaUxEligible_entryPointEnabled() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(true)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(false).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // GA UX logic.
        verify(mUxStatesManager, times(2)).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        verify(mConsentManager).isAdultAccount();

        verify(mConsentManager).setUx(BETA_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        BETA_UX,
                        BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL);

        ExtendedMockito.verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(adIdEnabled), eq(false)),
                never());
        ExtendedMockito.verify(
                () -> PackageChangedReceiver.enableReceiver(mContext, mFlags), never());

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), never());
    }

    // Test the flow in which user is eligible for GA graduation.
    @Test
    public void startTest_gaGraduation() {
        boolean entryPointEnabled = true;
        boolean isU18Account = false;
        boolean isAdultAccount = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        // U18 notice was already displayed.
        doReturn(true).when(mConsentManager).wasU18NotificationDisplayed();

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // RVC UX logic where flag is checked once in RVC_UX.
        verify(mUxStatesManager).getFlag(KEY_RVC_UX_ENABLED);

        // RVC UX logic where flag is checked once in RvcPostOTAChannel.
        verify(mUxStatesManager).getFlag(KEY_RVC_POST_OTA_NOTIFICATION_ENABLED);

        // The UX can not be updated due to the fact that graduation channel is currently disabled.
        verify(mConsentManager, never()).setUx(any());
        verify(mConsentManager, never()).setEnrollmentChannel(any(), any());
    }

    // Test the flow in which user is eligible for U18 detention.
    @Test
    public void startTest_u18Detention() {
        boolean entryPointEnabled = true;
        boolean isU18Account = true;
        boolean isAdultAccount = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        // GA notice was already displayed.
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // U18 UX is set as the detention channel is available.
        verify(mConsentManager).setUx(U18_UX);
        verify(mConsentManager)
                .setEnrollmentChannel(
                        U18_UX, U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL);
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(mContext), never());
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mContext));
    }

    // Test the flow in which user is eligible for U18 detention.
    @Test
    public void startTest_u18DetentionDisabled() {
        boolean entryPointEnabled = true;
        boolean isU18Account = true;
        boolean isAdultAccount = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdultAccount)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        doReturn(adIdEnabled).when(mConsentManager).isAdIdEnabled();
        doReturn(entryPointEnabled).when(mConsentManager).isEntryPointEnabled();
        doReturn(isAdultAccount).when(mConsentManager).isAdultAccount();
        doReturn(isU18Account).when(mConsentManager).isU18Account();
        doReturn(false).when(mUxStatesManager).getFlag(KEY_IS_U18_UX_DETENTION_CHANNEL_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        doReturn(true).when(mUxStatesManager).getFlag(KEY_GA_UX_FEATURE_ENABLED);
        // GA notice was already displayed.
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();

        mUxEngine.start(adServicesStates);

        verify(mUxStatesManager).persistAdServicesStates(adServicesStates);

        // Unsupported UX logic.
        verify(mUxStatesManager).getFlag(KEY_ADSERVICES_ENABLED);
        verify(mConsentManager).isEntryPointEnabled();

        // U18 UX logic.
        verify(mUxStatesManager).getFlag(KEY_U18_UX_ENABLED);
        verify(mConsentManager).isU18Account();

        // Detention channel can not be selected as the channel flag is disabled.
        verify(mConsentManager, never()).setUx(U18_UX);
        verify(mConsentManager, never())
                .setEnrollmentChannel(
                        U18_UX, U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL);
    }
}
