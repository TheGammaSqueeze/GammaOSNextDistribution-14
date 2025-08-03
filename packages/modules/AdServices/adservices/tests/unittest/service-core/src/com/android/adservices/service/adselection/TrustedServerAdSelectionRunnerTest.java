/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.service.adselection.TrustedServerAdSelectionRunner.GZIP;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.content.Context;
import android.net.Uri;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdSelectionRunner.AdSelectionOrchestrationResult;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.proto.SellerFrontEndGrpc;
import com.android.adservices.service.proto.SellerFrontEndGrpc.SellerFrontEndFutureStub;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdRequest;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdRequest.SelectWinningAdRawRequest;
import com.android.adservices.service.proto.SellerFrontendService.SelectWinningAdResponse;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;

public class TrustedServerAdSelectionRunnerTest {
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final Long AD_SELECTION_ID = 1234L;
    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_uris");
    private static final Uri TRUSTED_SIGNALS_URI =
            Uri.parse("https://developer.android.com/test/trusted_signals_uri");
    private static final int CALLER_UID = Process.myUid();
    private static final ListeningExecutorService sLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private static final ListeningExecutorService sBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    private static final ScheduledThreadPoolExecutor sScheduledExecutor =
            AdServicesExecutors.getScheduler();

    private static final AdSelectionConfig.Builder sAdSelectionConfigBuilder =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setSeller(SELLER_VALID)
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setDecisionLogicUri(DECISION_LOGIC_URI)
                    .setTrustedScoringSignalsUri(TRUSTED_SIGNALS_URI);
    private static final DBCustomAudience sDBCustomAudience = createDBCustomAudience(BUYER_1);

    private static final AdScoringOutcome sAdScoringOutcome =
            AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
    private static final SelectWinningAdResponse sSelectWinningAdResponse =
            SelectWinningAdResponse.newBuilder()
                    .setRawResponse(
                            SelectWinningAdResponse.SelectWinningAdRawResponse.newBuilder()
                                    .setAdRenderUrl("valid.example.com/testing/hello/test.com")
                                    .setScore(1)
                                    .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                                    .setBidPrice(1))
                    .build();
    private static final AdFilterer sAdFilterer = new AdFiltererNoOpImpl();
    private static final FrequencyCapAdDataValidator FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP =
            new FrequencyCapAdDataValidatorNoOpImpl();
    private static final AdCounterHistogramUpdater AD_COUNTER_HISTOGRAM_UPDATER_NO_OP =
            new AdCounterHistogramUpdaterNoOpImpl();

    private MockitoSession mStaticMockSession = null;
    private Context mContext = ApplicationProvider.getApplicationContext();
    private Flags mFlags =
            new Flags() {
                @Override
                public long getAdSelectionOverallTimeoutMs() {
                    return 300;
                }
            };
    private TrustedServerAdSelectionRunner mAdSelectionRunner;

    @Mock private Clock mClock;
    @Mock private AdServicesLogger mAdServicesLoggerSpy;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    @Mock private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionKeyDao mEncryptionKeyDaoMock;
    @Mock private JsFetcher mJsFetcher;
    @Mock private CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @Mock private AdSelectionIdGenerator mMockAdSelectionIdGenerator;
    @Mock private OkHttpChannelBuilder mChannelBuilder;
    @Mock private ManagedChannel mManagedChannel;
    @Mock private SellerFrontEndFutureStub mStub;
    private SellerFrontEndFutureStub mStubWithCompression =
            Mockito.mock(SellerFrontEndFutureStub.class, "mStubWithCompression");
    @Mock private AdSelectionExecutionLogger mAdSelectionExecutionLogger;

    @Mock AdSelectionServiceFilter mAdSelectionServiceFilter;

    @Mock private DebugReporting mDebugReportingMock;

    @Mock private DebugReportSenderStrategy mDebugReportSenderMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Before
    public void setUp() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(OkHttpChannelBuilder.class)
                        .mockStatic(SellerFrontEndGrpc.class)
                        .initMocks(this)
                        .startMocking();
    }

    private static DBCustomAudience createDBCustomAudience(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyer(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    @Test
    public void testRunAdSelectionSuccess() {
        doReturn(mChannelBuilder)
                .when(() -> OkHttpChannelBuilder.forAddress(anyString(), anyInt()));
        doReturn(mManagedChannel).when(mChannelBuilder).build();
        doReturn(mStub).when(() -> SellerFrontEndGrpc.newFutureStub(mManagedChannel));
        doReturn(mStubWithCompression).when(mStub).withCompression(GZIP);
        doReturn(Futures.immediateFuture(sSelectWinningAdResponse))
                .when(mStubWithCompression)
                .selectWinningAd(any(SelectWinningAdRequest.class));

        AdSelectionConfig adSelectionConfig = sAdSelectionConfigBuilder.build();
        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        FluentFuture<String> js = FluentFuture.from(Futures.immediateFuture("js"));
        when(mJsFetcher.getBiddingLogic(any(), any(), any(), any(), any())).thenReturn(js);

        mAdSelectionRunner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        sLightweightExecutorService,
                        sBackgroundExecutorService,
                        sScheduledExecutor,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilter,
                        sAdFilterer,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        AD_COUNTER_HISTOGRAM_UPDATER_NO_OP,
                        mJsFetcher,
                        mAdSelectionExecutionLogger,
                        mDebugReportingMock);
        AdSelectionOrchestrationResult adSelectionOrchestrationResult =
                invokeRunAdSelection(
                        mAdSelectionRunner,
                        adSelectionConfig,
                        MY_APP_PACKAGE_NAME,
                        ImmutableList.of(sDBCustomAudience));

        Uri expectedWinningAdRenderUri =
                sAdScoringOutcome.getAdWithScore().getAdWithBid().getAdData().getRenderUri();
        double expectedWinningAdBid = sAdScoringOutcome.getAdWithScore().getAdWithBid().getBid();

        // Set adSelectionId/timestamp to anything to be able to build the object; an error is
        // thrown if the fields aren't set.
        adSelectionOrchestrationResult.mDbAdSelectionBuilder.setAdSelectionId(AD_SELECTION_ID);
        adSelectionOrchestrationResult.mDbAdSelectionBuilder.setCreationTimestamp(Instant.now());
        DBAdSelection dbAdSelection = adSelectionOrchestrationResult.mDbAdSelectionBuilder.build();

        assertEquals(expectedWinningAdRenderUri, dbAdSelection.getWinningAdRenderUri());
        assertEquals(expectedWinningAdBid, dbAdSelection.getWinningAdBid(), 0);

        SellerFrontEndFutureStub unused = verify(mStub).withCompression(GZIP);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void verifyNoCANameInBiddingSignalKeys() {
        doReturn(mChannelBuilder)
                .when(() -> OkHttpChannelBuilder.forAddress(anyString(), anyInt()));
        doReturn(mManagedChannel).when(mChannelBuilder).build();
        doReturn(mStub).when(() -> SellerFrontEndGrpc.newFutureStub(mManagedChannel));
        doReturn(mStubWithCompression).when(mStub).withCompression(GZIP);
        doReturn(Futures.immediateFuture(sSelectWinningAdResponse))
                .when(mStubWithCompression)
                .selectWinningAd(any(SelectWinningAdRequest.class));

        AdSelectionConfig adSelectionConfig = sAdSelectionConfigBuilder.build();
        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        FluentFuture<String> js = FluentFuture.from(Futures.immediateFuture("js"));
        when(mJsFetcher.getBiddingLogic(any(), any(), any(), any(), any())).thenReturn(js);

        mAdSelectionRunner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        sLightweightExecutorService,
                        sBackgroundExecutorService,
                        sScheduledExecutor,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilter,
                        sAdFilterer,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        AD_COUNTER_HISTOGRAM_UPDATER_NO_OP,
                        mJsFetcher,
                        mAdSelectionExecutionLogger,
                        mDebugReportingMock);

        // Add CA name to bidding data keys and later verify we don't send it to the server.
        DBCustomAudience customAudience = createDBCustomAudience(BUYER_1);
        customAudience.getTrustedBiddingData().getKeys().add(customAudience.getName());

        ArgumentCaptor<SelectWinningAdRequest> captor =
                ArgumentCaptor.forClass(SelectWinningAdRequest.class);

        invokeRunAdSelection(
                mAdSelectionRunner,
                adSelectionConfig,
                MY_APP_PACKAGE_NAME,
                ImmutableList.of(customAudience));

        // Verify the bidding signal keys list does *not* contain the CA name.
        verify(mStubWithCompression).selectWinningAd(captor.capture());
        SelectWinningAdRawRequest req = captor.getValue().getRawRequest();
        List<String> biddingSignalKeys =
                req.getRawBuyerInputMap()
                        .get(customAudience.getBuyer().toString())
                        .getCustomAudiences(0)
                        .getBiddingSignalsKeysList();
        assertThat(biddingSignalKeys).doesNotContain(customAudience.getName());
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void verifyEmptyBiddingSignalKeys() {
        doReturn(mChannelBuilder)
                .when(() -> OkHttpChannelBuilder.forAddress(anyString(), anyInt()));
        doReturn(mManagedChannel).when(mChannelBuilder).build();
        doReturn(mStub).when(() -> SellerFrontEndGrpc.newFutureStub(mManagedChannel));
        doReturn(mStubWithCompression).when(mStub).withCompression(GZIP);
        doReturn(Futures.immediateFuture(sSelectWinningAdResponse))
                .when(mStubWithCompression)
                .selectWinningAd(any(SelectWinningAdRequest.class));

        AdSelectionConfig adSelectionConfig = sAdSelectionConfigBuilder.build();
        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        FluentFuture<String> js = FluentFuture.from(Futures.immediateFuture("js"));
        when(mJsFetcher.getBiddingLogic(any(), any(), any(), any(), any())).thenReturn(js);

        mAdSelectionRunner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        sLightweightExecutorService,
                        sBackgroundExecutorService,
                        sScheduledExecutor,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilter,
                        sAdFilterer,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        AD_COUNTER_HISTOGRAM_UPDATER_NO_OP,
                        mJsFetcher,
                        mAdSelectionExecutionLogger,
                        mDebugReportingMock);

        // Add CA name to bidding data keys and later verify we don't send it to the server.
        DBCustomAudience customAudience = createDBCustomAudience(BUYER_1);
        customAudience.getTrustedBiddingData().getKeys().clear();
        customAudience.getTrustedBiddingData().getKeys().add(customAudience.getName());

        ArgumentCaptor<SelectWinningAdRequest> captor =
                ArgumentCaptor.forClass(SelectWinningAdRequest.class);

        invokeRunAdSelection(
                mAdSelectionRunner,
                adSelectionConfig,
                MY_APP_PACKAGE_NAME,
                ImmutableList.of(customAudience));

        // Verify the bidding signal keys list does *not* contain the CA name.
        verify(mStubWithCompression).selectWinningAd(captor.capture());
        SelectWinningAdRawRequest req = captor.getValue().getRawRequest();
        List<String> biddingSignalKeys =
                req.getRawBuyerInputMap()
                        .get(customAudience.getBuyer().toString())
                        .getCustomAudiences(0)
                        .getBiddingSignalsKeysList();
        assertThat(biddingSignalKeys).isEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void verifyRuntimeExceptionOnBuyerJsFetchFail() {
        doReturn(mChannelBuilder)
                .when(() -> OkHttpChannelBuilder.forAddress(anyString(), anyInt()));
        doReturn(mManagedChannel).when(mChannelBuilder).build();
        doReturn(mStub).when(() -> SellerFrontEndGrpc.newFutureStub(mManagedChannel));
        doReturn(mStubWithCompression).when(mStub).withCompression(GZIP);
        doReturn(Futures.immediateFuture(sSelectWinningAdResponse))
                .when(mStubWithCompression)
                .selectWinningAd(any(SelectWinningAdRequest.class));

        AdSelectionConfig adSelectionConfig = sAdSelectionConfigBuilder.build();
        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        doThrow(new IllegalStateException())
                .when(mJsFetcher)
                .getBiddingLogic(
                        sDBCustomAudience.getBiddingLogicUri(),
                        mCustomAudienceDevOverridesHelper,
                        sDBCustomAudience.getOwner(),
                        sDBCustomAudience.getBuyer(),
                        sDBCustomAudience.getName());

        mAdSelectionRunner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        sLightweightExecutorService,
                        sBackgroundExecutorService,
                        sScheduledExecutor,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilter,
                        sAdFilterer,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        AD_COUNTER_HISTOGRAM_UPDATER_NO_OP,
                        mJsFetcher,
                        mAdSelectionExecutionLogger,
                        mDebugReportingMock);

        invokeRunAdSelection(
                mAdSelectionRunner,
                adSelectionConfig,
                MY_APP_PACKAGE_NAME,
                ImmutableList.of(sDBCustomAudience));
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void verifyNoRequestCompressionWhenFlagDisabled() {
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getAdSelectionOffDeviceRequestCompressionEnabled() {
                        return false;
                    }
                };

        doReturn(mChannelBuilder)
                .when(() -> OkHttpChannelBuilder.forAddress(anyString(), anyInt()));
        doReturn(mManagedChannel).when(mChannelBuilder).build();
        doReturn(mStub).when(() -> SellerFrontEndGrpc.newFutureStub(mManagedChannel));
        doReturn(Futures.immediateFuture(sSelectWinningAdResponse))
                .when(mStub)
                .selectWinningAd(any(SelectWinningAdRequest.class));

        AdSelectionConfig adSelectionConfig = sAdSelectionConfigBuilder.build();
        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        FluentFuture<String> js = FluentFuture.from(Futures.immediateFuture("js"));
        when(mJsFetcher.getBiddingLogic(any(), any(), any(), any(), any())).thenReturn(js);

        mAdSelectionRunner =
                new TrustedServerAdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        sLightweightExecutorService,
                        sBackgroundExecutorService,
                        sScheduledExecutor,
                        mMockAdSelectionIdGenerator,
                        mClock,
                        mAdServicesLoggerSpy,
                        flags,
                        CALLER_UID,
                        mAdSelectionServiceFilter,
                        sAdFilterer,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        AD_COUNTER_HISTOGRAM_UPDATER_NO_OP,
                        mJsFetcher,
                        mAdSelectionExecutionLogger,
                        mDebugReportingMock);
        invokeRunAdSelection(
                mAdSelectionRunner,
                adSelectionConfig,
                MY_APP_PACKAGE_NAME,
                ImmutableList.of(sDBCustomAudience));

        SellerFrontEndFutureStub unused = verify(mStub, times(0)).withCompression(GZIP);
    }

    private AdSelectionOrchestrationResult invokeRunAdSelection(
            TrustedServerAdSelectionRunner adSelectionRunner,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName,
            List<DBCustomAudience> buyerCustomAudience) {

        try {
            ListenableFuture<AdSelectionOrchestrationResult> dbAdSelection =
                    adSelectionRunner.orchestrateAdSelection(
                            adSelectionConfig,
                            callerPackageName,
                            Futures.immediateFuture(buyerCustomAudience));
            return dbAdSelection.get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }
}
