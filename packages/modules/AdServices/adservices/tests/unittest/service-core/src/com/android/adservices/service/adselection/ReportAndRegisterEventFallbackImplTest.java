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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.DataHandlersFixture.AD_SELECTION_ID_2;
import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.adselection.EventReporter.INTERACTION_DATA_SIZE_MAX_EXCEEDED;
import static com.android.adservices.service.adselection.EventReporter.INTERACTION_KEY_SIZE_MAX_EXCEEDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.DataHandlersFixture;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportInteractionApiCalledStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportAndRegisterEventFallbackImplTest {
    private static final Instant ACTIVATION_TIME = Instant.now();
    private static final int MY_UID = Process.myUid();
    private static final String CALLER_SDK_NAME = "sdk.package.name";
    private static final AdTechIdentifier AD_TECH = AdTechIdentifier.fromString("localhost");
    private static final int BID = 6;
    private static final long AD_SELECTION_ID = 1;
    private static final String SELLER_INTERACTION_REPORTING_PATH = "/seller/interactionReporting/";
    private static final String BUYER_INTERACTION_REPORTING_PATH = "/buyer/interactionReporting/";
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");
    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final int SELLER_AND_BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER |
                    ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final String CLICK_EVENT = "click";
    private AdSelectionEntryDao mAdSelectionEntryDao;

    @Spy
    private final AdServicesHttpsClient mHttpClient =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private final ListeningExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private Flags mFlags = new ReportEventTestFlags();
    private static final Flags FLAGS_ENROLLMENT_CHECK =
            new ReportEventTestFlags() {
                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return false;
                }
            };
    private final long mMaxRegisteredAdBeaconsTotalCount =
            mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount();
    private final long mMaxRegisteredAdBeaconsPerDestination =
            mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount();

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private MeasurementImpl mMeasurementServiceMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private Context mContextMock;
    private ReportAndRegisterEventFallbackImpl mEventReporter;
    private DBAdSelection mDBAdSelection;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionSellerClick;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionBuyerClick;
    private String mEventData;
    private ReportInteractionInput.Builder mInputBuilder;
    private MockitoSession mStaticMockSession = null;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(ConsentManager.class)
                        .mockStatic(PermissionHelper.class)
                        .initMocks(this)
                        .startMocking();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mEventReporter = getReportAndRegisterEventFallbackImpl(mFlags);

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AD_TECH)
                        .build();

        String biddingLogicPath = "/buyer/bidding";
        mDBAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBuyerContextualSignals("{}")
                        .setBiddingLogicUri(mMockWebServerRule.uriForPath(biddingLogicPath))
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mDBRegisteredAdInteractionBuyerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(CLICK_EVENT)
                        .setDestination(BUYER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mDBRegisteredAdInteractionSellerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(CLICK_EVENT)
                        .setDestination(SELLER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();
        mEventData = new JSONObject().put("x", "10").put("y", "12").toString();

        mInputBuilder =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionData(mEventData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .setCallerSdkName(CALLER_SDK_NAME);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testImplSuccessfullyReportsRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    // TODO(b/298871007): Remove FlakyTest annotation after stabilizing flake.
    @Test
    @FlakyTest(bugId = 298871007)
    public void testImplDoesNotCrashAfterSellerReportingThrowsAnException() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail seller reporting.
        Uri reportingUri = mDBRegisteredAdInteractionSellerClick.getInteractionReportingUri();
        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw new IllegalStateException("Exception for test!");
                        },
                        mLightweightExecutorService);
        doReturn(failedFuture)
                .when(mHttpClient)
                .postPlainText(reportingUri, mEventData, DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled only buyer reporting requests with exact paths.
        assertEquals(
                BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    // TODO(b/298871007): Remove FlakyTest annotation after stabilizing flake.
    @Test
    @FlakyTest(bugId = 298871007)
    public void testImplDoesNotCrashAfterSellerReportingAndRegisteringThrowsAnException()
            throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail seller reporting and registering.
        Uri reportingUri = mDBRegisteredAdInteractionSellerClick.getInteractionReportingUri();
        doThrow(new IllegalStateException("Exception for test!"))
                .when(mMeasurementServiceMock)
                .registerEvent(eq(reportingUri), any(), any(), anyBoolean(), any(), any(), any());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled both buyer and seller reporting requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    // TODO(b/298871007): Remove FlakyTest annotation after stabilizing flake.
    @Test
    @FlakyTest(bugId = 298871007)
    public void testImplDoesNotCrashAfterBuyerReportingThrowsAnException() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail buyer reporting.
        Uri reportingUri = mDBRegisteredAdInteractionBuyerClick.getInteractionReportingUri();
        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw new IllegalStateException("Exception for test!");
                        },
                        mLightweightExecutorService);
        doReturn(failedFuture)
                .when(mHttpClient)
                .postPlainText(reportingUri, mEventData, DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled only seller reporting requests with exact paths.
        assertEquals(
                SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    // TODO(b/298871007): Remove FlakyTest annotation after stabilizing flake.
    @Test
    @FlakyTest(bugId = 298871007)
    public void testImplDoesNotCrashAfterBuyerReportingAndRegisteringThrowsAnException()
            throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Fail buyer reporting and registering.
        Uri reportingUri = mDBRegisteredAdInteractionBuyerClick.getInteractionReportingUri();
        doThrow(new IllegalStateException("Exception for test!"))
                .when(mMeasurementServiceMock)
                .registerEvent(eq(reportingUri), any(), any(), anyBoolean(), any(), any(), any());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath().contains(reportingUri.getPath())) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm failure caused by the exception thrown is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled both buyer and seller reporting requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImplOnlyReportsBuyersRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath()
                                        .equals(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT)) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only buyer reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input =
                mInputBuilder.setReportingDestinations(BUYER_DESTINATION).build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertEquals(
                BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    @Test
    public void testImplOnlyReportsSellerRegisteredEvents() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (request.getPath()
                                        .equals(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT)) {
                                    return new MockResponse();
                                } else {
                                    throw new IllegalStateException(
                                            "Only seller reporting can occur!");
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input =
                mInputBuilder.setReportingDestinations(SELLER_DESTINATION).build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertEquals(
                SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, server.takeRequest().getPath());
    }

    @Test
    public void testImplReturnsOnlyReportsUriThatPassesEnrollmentCheck() throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifacts();

        // Re-initialize event reporter.
        mEventReporter = getReportAndRegisterEventFallbackImpl(FLAGS_ENROLLMENT_CHECK);

        // Allow the first call and filter the second.
        doNothing()
                .doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        AD_TECH, AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            AtomicInteger mNumCalls = new AtomicInteger(0);

                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (mNumCalls.get() > 0) {
                                    throw new IllegalStateException(
                                            "Only first reporting URI has fledge enrollment!");
                                } else {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse();
                                }
                            }
                        });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertTrue(server.takeRequest().getPath().contains(CLICK_EVENT));

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(1);
    }

    @Test
    public void testImplReturnsSuccessButDoesNotDoReportingWhenBothFailEnrollmentCheck()
            throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifacts();

        // Re-initialize event reporter.
        mEventReporter = getReportAndRegisterEventFallbackImpl(FLAGS_ENROLLMENT_CHECK);

        // Filter the call.
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        AD_TECH, AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION);

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without fledge enrollment!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, /* shouldCountLog = */ true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(0);
    }

    @Test
    public void testImplFailsWithInvalidPackageName() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new FledgeAuthorizationFilter.CallerMismatchException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app package name invalid!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_UNAUTHORIZED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNAUTHORIZED),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenForegroundCheckFails() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(
                        new FilterException(
                                new AppImportanceFilter.WrongCallingApplicationStateException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in foreground!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_BACKGROUND_CALLER, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_BACKGROUND_CALLER),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenThrottled() throws Exception {
        enableARA();
        persistReportingArtifacts();

        ReportInteractionInput input = mInputBuilder.build();

        // Allow the first call and filter the second.
        doNothing()
                .doThrow(
                        new FilterException(
                                new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            AtomicInteger mNumCalls = new AtomicInteger(0);

                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (mNumCalls.get() > 2) {
                                    throw new IllegalStateException(
                                            "Only first 2 POST requests are not throttled!");
                                } else {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse();
                                }
                            }
                        });

        // First call should succeed.
        ReportEventTestCallback callbackFirstCall =
                callReportEvent(input, /* shouldCountLog= */ true);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, input);

        // Confirm success was reported to caller.
        assertTrue(callbackFirstCall.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);

        // Immediately made subsequent call should fail
        ReportEventTestCallback callbackSubsequentCall = callReportEvent(input);

        // Confirm failure was reported to caller.
        assertFalse(callbackSubsequentCall.mIsSuccess);
        assertEquals(
                STATUS_RATE_LIMIT_REACHED,
                callbackSubsequentCall.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                RATE_LIMIT_REACHED_ERROR_MESSAGE,
                callbackSubsequentCall.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenAppNotInAllowList() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new FledgeAllowListsFilter.AppNotAllowedException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when app not in allowlist!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testImplFailsSilentlyWithoutConsent() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Filter the call.
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        null,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        MY_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                        DevContext.createForDevOptionsDisabled());

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made without user consent!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testImplFailsWithUnknownAdSelectionId() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made with invalid adselection id!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.setAdSelectionId(AD_SELECTION_ID + 1).build();
        ReportEventTestCallback callback = callReportEvent(input);

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImplSucceedsWhenNotFindingRegisteredAdEvents() throws Exception {
        enableARA();

        // Only persisting the AdSelectionEntry. No events.
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made since ad interactions are not"
                                        + " registered!");
                    }
                });

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenEventDataExceedsMaxSize() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when the input is not valid!");
                    }
                });

        // Call report event with input.
        char[] largePayload = new char[65 * 1024]; // 65KB
        ReportInteractionInput input =
                mInputBuilder.setInteractionData(new String(largePayload)).build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                INTERACTION_DATA_SIZE_MAX_EXCEEDED,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImplFailsWhenInteractionKeyExceedsMaxSize() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        throw new IllegalStateException(
                                "No calls should be made when the input is not valid!");
                    }
                });

        // Instantiate flags with small max interaction data size.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public long
                            getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
                        return 1;
                    }
                };

        // Re-initialize event reporter with new flags.
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Confirm failure was reported to caller.
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                INTERACTION_KEY_SIZE_MAX_EXCEEDED, callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm failure caused by the input is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testImpl_onlyReportsEvent_measurementKillSwitchEnabled() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Instantiate flags with kill switch turned on.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getMeasurementApiRegisterSourceKillSwitch() {
                        return true;
                    }
                };

        // Re init interaction reporter with new flags
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_appIsNotInMeasurementAllowlisted() throws Exception {
        enableARA();
        persistReportingArtifacts();

        // Instantiate flags with no allow list.
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public String getMsmtApiAppAllowList() {
                        return AllowLists.ALLOW_NONE;
                    }
                };

        // Re-initialize event reporter with new flags.
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_measurementConsentRevoked() throws Exception {
        // Revoke consent for measurement.
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testImpl_onlyReportsEvent_noPermissionForARA() throws Exception {
        // Disable permission for ARA.
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportEventTestCallback callback = callReportEvent(input, true);

        // Verify registerEvent was never called.
        verify(mMeasurementServiceMock, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());
        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);
    }

    @Test
    public void testReportEventImplFailsWithUnknownAdSelectionId_serverAuctionEnabled()
            throws Exception {
        enableARA();
        persistReportingArtifacts();
        persistReportingArtifactsForServerAuction(AD_SELECTION_ID_2);

        // Mock server to handle fallback since measurement cannot report and register event.
        mMockWebServerRule.startMockWebServer(List.of(new MockResponse(), new MockResponse()));

        ReportInteractionInput inputParams =
                mInputBuilder.setAdSelectionId(AD_SELECTION_ID_2 + 1).build();

        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportEvent() {
                        return true;
                    }
                };

        // Re init interaction reporter
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);
        ReportAndRegisterEventFallbackImplTest.ReportEventTestCallback callback =
                callReportEvent(inputParams, true);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void test_idFoundInInitializationDb_registeredInteractionsReported() throws Exception {
        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<ReportInteractionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportInteractionApiCalledStats.class);

        enableARA();
        persistReportingArtifactsForServerAuction(AD_SELECTION_ID_2);
        Flags flags =
                new ReportEventTestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportEvent() {
                        return true;
                    }
                };

        // Re init interaction reporter
        mEventReporter = getReportAndRegisterEventFallbackImpl(flags);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        ReportInteractionInput inputParams =
                mInputBuilder
                        .setAdSelectionId(AD_SELECTION_ID_2)
                        .setCallerPackageName(DataHandlersFixture.TEST_PACKAGE_NAME_1)
                        .build();

        // Count down callback + log interaction.
        ReportAndRegisterEventFallbackImplTest.ReportEventTestCallback callback =
                callReportEvent(inputParams, true);

        assertTrue(callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(DataHandlersFixture.TEST_PACKAGE_NAME_1),
                        eq(STATUS_SUCCESS),
                        anyInt());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications)
                .containsExactly(
                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT,
                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT);

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT, inputParams);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT, inputParams);

        // Verifies ReportInteractionApiCalledStats get the correct values.
        Mockito.verify(mAdServicesLoggerMock, times(1))
                .logReportInteractionApiCalledStats(argumentCaptor.capture());
        ReportInteractionApiCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_AND_BUYER_DESTINATION);
        assertThat(stats.getNumMatchingUris()).isEqualTo(2);
    }

    private void persistReportingArtifactsForServerAuction(long adSelectionId) {
        RegisteredAdInteraction buyerClick =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        RegisteredAdInteraction sellerClick =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(CLICK_EVENT)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + CLICK_EVENT))
                        .build();

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                adSelectionId, DataHandlersFixture.AD_SELECTION_INITIALIZATION_1);
        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractionsForDestination(
                adSelectionId,
                BUYER_DESTINATION,
                List.of(buyerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination);

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractionsForDestination(
                adSelectionId,
                SELLER_DESTINATION,
                List.of(sellerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination);
    }

    private void persistReportingArtifacts() {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionBuyerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                BUYER_DESTINATION);

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionSellerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                SELLER_DESTINATION);
    }

    private void verifyRegisterEvent(String path, ReportInteractionInput input) {
        verify(mMeasurementServiceMock)
                .registerEvent(
                        mMockWebServerRule.uriForPath(path),
                        input.getCallerPackageName(),
                        input.getCallerSdkName(),
                        input.getAdId() != null,
                        mEventData,
                        input.getInputEvent(),
                        input.getAdId());
    }

    private void enableARA() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
    }

    private ReportAndRegisterEventFallbackImpl getReportAndRegisterEventFallbackImpl(Flags flags) {
        return new ReportAndRegisterEventFallbackImpl(
                mAdSelectionEntryDao,
                mHttpClient,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mAdServicesLoggerMock,
                flags,
                mAdSelectionServiceFilterMock,
                MY_UID,
                mFledgeAuthorizationFilterMock,
                DevContext.createForDevOptionsDisabled(),
                mMeasurementServiceMock,
                mConsentManagerMock,
                mContextMock,
                false);
    }

    private ReportEventTestCallback callReportEvent(ReportInteractionInput inputParams)
            throws Exception {
        return callReportEvent(inputParams, false);
    }

    /**
     * @param shouldCountLog if true, adds a latch to the log interaction as well.
     */
    private ReportEventTestCallback callReportEvent(
            ReportInteractionInput inputParams, boolean shouldCountLog) throws Exception {
        // Counted down in callback
        CountDownLatch resultLatch = new CountDownLatch(shouldCountLog ? 2 : 1);

        if (shouldCountLog) {
            // Wait for the logging call, which happens after the callback
            Answer<Void> countDownAnswer =
                    unused -> {
                        resultLatch.countDown();
                        return null;
                    };
            doAnswer(countDownAnswer)
                    .when(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        }

        ReportEventTestCallback callback = new ReportEventTestCallback(resultLatch);
        mEventReporter.reportInteraction(inputParams, callback);
        resultLatch.await();

        return callback;
    }

    static class ReportEventTestCallback extends ReportInteractionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportEventTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class ReportEventTestFlags implements Flags {
        @Override
        public boolean getMeasurementApiRegisterSourceKillSwitch() {
            return false;
        }

        @Override
        public String getMsmtApiAppAllowList() {
            return AllowLists.ALLOW_ALL;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return true;
        }
    }
}
