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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionHistogramInfo;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdSelectionE2ETest.AdSelectionTestCallback;
import com.android.adservices.service.adselection.UpdateAdCounterHistogramWorkerTest.FlagsOverridingAdFiltering;
import com.android.adservices.service.adselection.UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
public final class FrequencyCapFilteringE2ETest extends AdServicesExtendedMockitoTestCase {
    private static final int CALLBACK_WAIT_MS = 500;
    private static final int SELECT_ADS_CALLBACK_WAIT_MS = 10_000;
    private static final long AD_SELECTION_ID_BUYER_1 = 20;
    private static final long AD_SELECTION_ID_BUYER_2 = 21;

    private static final DBAdSelection EXISTING_PREVIOUS_AD_SELECTION_BUYER_1 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_BUYER_1)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setBuyerContextualSignals(AdSelectionSignals.EMPTY.toString())
                    .setBiddingLogicUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/bidding"))
                    .setWinningAdRenderUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/ad1"))
                    .setWinningAdBid(0.5)
                    .setCreationTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                    .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys())
                    .build();

    private static final DBAdSelection EXISTING_PREVIOUS_AD_SELECTION_BUYER_2 =
            new DBAdSelection.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_BUYER_2)
                    .setCustomAudienceSignals(
                            CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                                    .setBuyer(CommonFixture.VALID_BUYER_2)
                                    .build())
                    .setBuyerContextualSignals(AdSelectionSignals.EMPTY.toString())
                    .setBiddingLogicUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_2, "/bidding"))
                    .setWinningAdRenderUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_2, "/ad1"))
                    .setWinningAdBid(0.5)
                    .setCreationTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                    .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys())
                    .build();

    private static final ImmutableList<KeyedFrequencyCap> CLICK_FILTERS =
            ImmutableList.of(
                    new KeyedFrequencyCap.Builder(
                                    KeyedFrequencyCapFixture.KEY1,
                                    /* maxCount= */ 1,
                                    Duration.ofSeconds(5))
                            .build());

    private static final DBAdData AD_WITH_FILTER =
            DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                    .setMetadata("{\"result\":1}")
                    .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                    .setAdFilters(
                            new AdFilters.Builder()
                                    .setFrequencyCapFilters(
                                            new FrequencyCapFilters.Builder()
                                                    .setKeyedFrequencyCapsForClickEvents(
                                                            CLICK_FILTERS)
                                                    .build())
                                    .build())
                    .build();
    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;
    @Mock private HttpCache mHttpCacheMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private AdSelectionServiceFilter mServiceFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private CallerMetadata mCallerMetadataMock;
    @Mock private File mAdSelectionDbFileMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private FledgeAllowListsFilter mFledgeAllowListsFilterMock;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDaoSpy;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EnrollmentDao mEnrollmentDao;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;

    private FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    private AdSelectionServiceImpl mAdSelectionServiceImpl;
    private UpdateAdCounterHistogramInput mInputParams;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy =
            MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
        mFrequencyCapDaoSpy =
                Mockito.spy(
                        Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                                .build()
                                .frequencyCapDao());

        Flags flagsEnablingAdFiltering = new FlagsOverridingAdFiltering(true);
        doReturn(flagsEnablingAdFiltering).when(FlagsFactory::getFlags);

        mEncryptionKeyDao = EncryptionKeyDao.getInstance(mSpyContext);
        mEnrollmentDao = EnrollmentDao.getInstance(mSpyContext);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();

        mFledgeAuthorizationFilterSpy =
                ExtendedMockito.spy(
                        new FledgeAuthorizationFilter(
                                mSpyContext.getPackageManager(),
                                new EnrollmentDao(
                                        mSpyContext,
                                        DbTestUtil.getSharedDbHelperForTest(),
                                        flagsEnablingAdFiltering),
                                mAdServicesLoggerMock));

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(
                        mAppInstallDao, mFrequencyCapDaoSpy, flagsEnablingAdFiltering);

        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();

        mAdSelectionServiceImpl =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsEnablingAdFiltering,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        mInputParams =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID_BUYER_1,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        // Required stub for Custom Audience DB persistence
        extendedMockito.mockGetFlags(flagsEnablingAdFiltering);

        // Required stub for Ad Selection call
        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();

        // Required stubs for Ad Selection loggers
        doReturn(Clock.getInstance().elapsedRealtime() - 100)
                .when(mCallerMetadataMock)
                .getBinderElapsedTimestamp();
        doReturn(mAdSelectionDbFileMock).when(mSpyContext).getDatabasePath(any());
        doReturn(10L).when(mAdSelectionDbFileMock).length();

        // Required stubs for Ad Selection signals/logic fetching
        doReturn(mHttpCacheMock).when(mAdServicesHttpsClientMock).getAssociatedCache();
        doReturn(
                        // Bidding signals
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("{}")
                                        .build()))
                .when(mAdServicesHttpsClientMock)
                .fetchPayload(any(Uri.class), any(ImmutableSet.class), any(DevContext.class));
        doReturn(
                        // Scoring signals
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("{}")
                                        .build()))
                .when(mAdServicesHttpsClientMock)
                .fetchPayload(any(Uri.class), any(DevContext.class));
        doReturn(
                        // Bidding logic
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(
                                                AdSelectionE2ETest.READ_BID_FROM_AD_METADATA_JS)
                                        .build()))
                .doReturn(
                        // Scoring logic
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(AdSelectionE2ETest.USE_BID_AS_SCORE_JS)
                                        .build()))
                .when(mAdServicesHttpsClientMock)
                .fetchPayload(any(AdServicesHttpClientRequest.class));
    }

    @Test
    public void testUpdateHistogramMissingAdSelectionDoesNothing() throws InterruptedException {
        UpdateAdCounterHistogramTestCallback callback = callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", callback).that(callback.mIsSuccess).isTrue();

        verifyNoMoreInteractions(mFrequencyCapDaoSpy);
    }

    @Test
    public void testUpdateHistogramForAdSelectionAddsHistogramEvents() throws InterruptedException {
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);

        UpdateAdCounterHistogramTestCallback callback = callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", callback).that(callback.mIsSuccess).isTrue();

        verify(mFrequencyCapDaoSpy, times(AdDataFixture.getAdCounterKeys().size()))
                .insertHistogramEvent(any(), anyInt(), anyInt(), anyInt(), anyInt());

        for (Integer key : AdDataFixture.getAdCounterKeys()) {
            assertThat(
                            mFrequencyCapDaoSpy.getNumEventsForBuyerAfterTime(
                                    key,
                                    CommonFixture.VALID_BUYER_1,
                                    mInputParams.getAdEventType(),
                                    CommonFixture.FIXED_EARLIER_ONE_DAY))
                    .isEqualTo(1);
        }
    }

    @Test
    public void testUpdateHistogramForAdSelectionFromOtherAppDoesNotAddHistogramEvents()
            throws InterruptedException {
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredAnyPermission(
                        mSpyContext,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM,
                        AdSelectionServiceImpl.PERMISSIONS_SET);

        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);

        // Caller does not match previous ad selection
        UpdateAdCounterHistogramInput inputParamsOtherPackage =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID_BUYER_1,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                CommonFixture.TEST_PACKAGE_NAME_1)
                        .build();

        UpdateAdCounterHistogramTestCallback callback =
                callUpdateAdCounterHistogram(inputParamsOtherPackage);

        assertWithMessage("Callback failed, was: %s", callback).that(callback.mIsSuccess).isTrue();

        verifyNoMoreInteractions(mFrequencyCapDaoSpy);

        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredAnyPermission(
                        mSpyContext,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM,
                        AdSelectionServiceImpl.PERMISSIONS_SET);
    }

    @Test
    public void testUpdateHistogramDisabledFeatureFlagNotifiesError() throws InterruptedException {
        Flags flagsWithDisabledAdFiltering = new FlagsOverridingAdFiltering(false);

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(
                        mAppInstallDao, mFrequencyCapDaoSpy, flagsWithDisabledAdFiltering);

        mAdSelectionServiceImpl =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDisabledAdFiltering,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        UpdateAdCounterHistogramTestCallback callback = callUpdateAdCounterHistogram(mInputParams);

        assertThat(callback.mIsSuccess).isFalse();
        assertThat(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);

        verifyNoMoreInteractions(mFrequencyCapDaoSpy);
    }

    @Test
    public void testUpdateHistogramExceedingRateLimitNotifiesError() throws InterruptedException {
        class FlagsWithLowRateLimit implements Flags {
            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public float getSdkRequestPermitsPerSecond() {
                return 1f;
            }
        }

        Throttler.destroyExistingThrottler();

        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAdTechAllowed(any(), any(), any(), anyInt(), anyInt());

        try {
            Flags flagsWithLowRateLimit = new FlagsWithLowRateLimit();

            mAdFilteringFeatureFactory =
                    new AdFilteringFeatureFactory(
                            mAppInstallDao, mFrequencyCapDaoSpy, flagsWithLowRateLimit);

            mAdSelectionServiceImpl =
                    new AdSelectionServiceImpl(
                            mAdSelectionEntryDao,
                            mAppInstallDao,
                            mCustomAudienceDao,
                            mEncodedPayloadDao,
                            mFrequencyCapDaoSpy,
                            mEncryptionKeyDao,
                            mEnrollmentDao,
                            mAdServicesHttpsClientMock,
                            mDevContextFilterMock,
                            mLightweightExecutorService,
                            mBackgroundExecutorService,
                            mScheduledExecutor,
                            mSpyContext,
                            mAdServicesLoggerMock,
                            flagsWithLowRateLimit,
                            CallingAppUidSupplierProcessImpl.create(),
                            mFledgeAuthorizationFilterSpy,
                            new AdSelectionServiceFilter(
                                    mSpyContext,
                                    mConsentManagerMock,
                                    flagsWithLowRateLimit,
                                    mAppImportanceFilterMock,
                                    mFledgeAuthorizationFilterSpy,
                                    mFledgeAllowListsFilterMock,
                                    Throttler.getInstance(flagsWithLowRateLimit)),
                            mAdFilteringFeatureFactory,
                            mConsentManagerMock,
                            mMultiCloudSupportStrategy,
                            mAdSelectionDebugReportDao,
                            mAdIdFetcher,
                            mUnusedKAnonSignJoinFactory,
                            false,
                            mRetryStrategyFactory);

            UpdateAdCounterHistogramTestCallback callback =
                    callUpdateAdCounterHistogram(mInputParams);

            assertWithMessage("Callback failed, was: %s", callback)
                    .that(callback.mIsSuccess)
                    .isTrue();

            // Call again within the rate limit
            callback = callUpdateAdCounterHistogram(mInputParams);

            assertThat(callback.mIsSuccess).isFalse();
            assertThat(callback.mFledgeErrorResponse.getStatusCode())
                    .isEqualTo(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED);

            verifyNoMoreInteractions(mFrequencyCapDaoSpy);
        } finally {
            Throttler.destroyExistingThrottler();
        }
    }

    @Test
    public void testAdSelectionPersistsAdCounterKeys() throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Collections.singletonList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback failed, was: %s", adSelectionCallback)
                .that(adSelectionCallback.mIsSuccess)
                .isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        adSelectionCallback.mAdSelectionResponse)
                .that(adSelectionCallback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());

        DBAdSelectionHistogramInfo histogramInfo =
                mAdSelectionEntryDao.getAdSelectionHistogramInfoInOnDeviceTable(
                        adSelectionCallback.mAdSelectionResponse.getAdSelectionId(),
                        CommonFixture.TEST_PACKAGE_NAME);
        assertThat(histogramInfo).isNotNull();
        assertThat(histogramInfo.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(histogramInfo.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testEmptyHistogramDoesNotFilterAds() throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback callback = callSelectAds();

        assertWithMessage("Callback failed, was: %s", callback).that(callback.mIsSuccess).isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        callback.mAdSelectionResponse)
                .that(callback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());
    }

    @Test
    public void testUpdatedHistogramFiltersAdsForBuyerWithinInterval() throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        // Persist histogram events
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);

        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Run ad selection for buyer
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback succeeded unexpectedly")
                .that(adSelectionCallback.mIsSuccess)
                .isFalse();
        assertWithMessage(
                        "Unexpected error response, ad selection responded with: %s",
                        adSelectionCallback.mFledgeErrorResponse)
                .that(adSelectionCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        assertWithMessage(
                        "Unexpected error response, ad selection responded with: %s",
                        adSelectionCallback.mFledgeErrorResponse)
                .that(adSelectionCallback.mFledgeErrorResponse.getErrorMessage())
                .contains("No valid bids");
    }

    @Test
    public void testUpdatedHistogramDoesNotFilterAdsForBuyerOutsideInterval()
            throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        // Persist histogram events
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);

        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Frequency cap intervals are truncated to seconds, so the test must wait so that the
        // ad filter no longer matches the events in the histogram table
        Thread.sleep(6000);

        // Run ad selection for buyer
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback failed, was: %s", adSelectionCallback)
                .that(adSelectionCallback.mIsSuccess)
                .isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        adSelectionCallback.mAdSelectionResponse)
                .that(adSelectionCallback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());
    }

    @Test
    public void testUpdatedHistogramDoesNotFilterAdsForOtherBuyer() throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        // Persist histogram events for BUYER_1
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);

        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Run ad selection for BUYER_2
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_2)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_2, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback failed, was: %s", adSelectionCallback)
                .that(adSelectionCallback.mIsSuccess)
                .isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        adSelectionCallback.mAdSelectionResponse)
                .that(adSelectionCallback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());
    }

    @Test
    public void testUpdateHistogramBeyondMaxTotalEventCountDoesNotFilterAds()
            throws InterruptedException {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        class FlagsWithLowEventCounts extends FlagsOverridingAdFiltering implements Flags {
            @Override
            public boolean getEnforceIsolateMaxHeapSize() {
                return false;
            }

            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public int getFledgeAdCounterHistogramAbsoluteMaxTotalEventCount() {
                return 5;
            }

            @Override
            public int getFledgeAdCounterHistogramLowerMaxTotalEventCount() {
                return 1;
            }
        }

        mAdSelectionServiceImpl =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        new FlagsWithLowEventCounts(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Persist ad selections
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_2);

        // Update for BUYER_1 and verify ads are filtered
        // T0 - BUYER_1 events (4 events entered)
        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback succeeded unexpectedly")
                .that(adSelectionCallback.mIsSuccess)
                .isFalse();

        // Sleep for ensured separation of timestamps
        Thread.sleep(200);

        // Update events for BUYER_2 to fill the event table and evict the first entries for BUYER_1
        // T1 - BUYER_2 events trigger table eviction of the oldest events (which are for BUYER_1)
        UpdateAdCounterHistogramInput inputParamsForBuyer2 =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID_BUYER_2,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_2,
                                CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        updateHistogramCallback = callUpdateAdCounterHistogram(inputParamsForBuyer2);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Verify that the events for BUYER_1 were evicted and the ad for BUYER_1 should now win
        adSelectionCallback = callSelectAds();

        assertWithMessage("Ad selection callback failed, was: %s", adSelectionCallback)
                .that(adSelectionCallback.mIsSuccess)
                .isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        adSelectionCallback.mAdSelectionResponse)
                .that(adSelectionCallback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());
    }

    @Test
    public void testUpdateHistogramBeyondMaxPerBuyerEventCountDoesNotFilterAds() throws Exception {
        // The JS Sandbox availability depends on an external component (the system webview) being
        // higher than a certain minimum version.
        Assume.assumeTrue(
                "JS Sandbox is not available",
                JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        final class FlagsWithLowPerBuyerEventCounts extends FlagsOverridingAdFiltering
                implements Flags {
            @Override
            public boolean getEnforceIsolateMaxHeapSize() {
                return false;
            }

            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }

            @Override
            public int getFledgeAdCounterHistogramAbsoluteMaxPerBuyerEventCount() {
                return 5;
            }

            @Override
            public int getFledgeAdCounterHistogramLowerMaxPerBuyerEventCount() {
                return 1;
            }
        }

        mAdSelectionServiceImpl =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        new FlagsWithLowPerBuyerEventCounts(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Persist ad selections
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_1);
        mAdSelectionEntryDao.persistAdSelection(EXISTING_PREVIOUS_AD_SELECTION_BUYER_2);

        // Update for BUYER_1 and verify ads are filtered
        // T0 - BUYER_1 events (4 events entered)
        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                callUpdateAdCounterHistogram(mInputParams);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_WITH_FILTER))
                        .build(),
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/update"),
                false);

        AdSelectionTestCallback adSelectionCallback = callSelectAds();

        assertWithMessage("Callback succeeded unexpectedly")
                .that(adSelectionCallback.mIsSuccess)
                .isFalse();

        // Sleep for ensured separation of timestamps
        Thread.sleep(200);

        // Update events for BUYER_2 to fill the event table which will not evict the first
        // entries for BUYER_1
        // T1 - BUYER_2 events do not trigger table eviction of the oldest events (which are
        // for BUYER_1)
        UpdateAdCounterHistogramInput inputParamsForBuyer2 =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID_BUYER_2,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_2,
                                CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        updateHistogramCallback = callUpdateAdCounterHistogram(inputParamsForBuyer2);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Verify that the events for BUYER_1 were not evicted and the ad for BUYER_1 should not win
        adSelectionCallback = callSelectAds();

        assertWithMessage("Callback succeeded unexpectedly")
                .that(adSelectionCallback.mIsSuccess)
                .isFalse();

        // Update event for BUYER_1 to fill the event table for BUYER_1
        // T2 - BUYER_1 events trigger table eviction of the oldest events (which are for BUYER_1)
        UpdateAdCounterHistogramInput inputParamsForBuyer1 =
                new UpdateAdCounterHistogramInput.Builder(
                                AD_SELECTION_ID_BUYER_1,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        updateHistogramCallback = callUpdateAdCounterHistogram(inputParamsForBuyer1);

        assertWithMessage("Callback failed, was: %s", updateHistogramCallback)
                .that(updateHistogramCallback.mIsSuccess)
                .isTrue();

        // Verify that the events for BUYER_1 were evicted and the ad for BUYER_1 should now win
        // since the only event left is the new VIEW event
        adSelectionCallback = callSelectAds();

        assertWithMessage("Ad selection callback failed, was: %s", adSelectionCallback)
                .that(adSelectionCallback.mIsSuccess)
                .isTrue();
        assertWithMessage(
                        "Unexpected winning ad, ad selection responded with: %s",
                        adSelectionCallback.mAdSelectionResponse)
                .that(adSelectionCallback.mAdSelectionResponse.getRenderUri())
                .isEqualTo(AD_WITH_FILTER.getRenderUri());
    }

    private UpdateAdCounterHistogramTestCallback callUpdateAdCounterHistogram(
            UpdateAdCounterHistogramInput inputParams) throws InterruptedException {
        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(callbackLatch);

        mAdSelectionServiceImpl.updateAdCounterHistogram(inputParams, callback);

        assertThat(callbackLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();

        return callback;
    }

    private AdSelectionTestCallback callSelectAds() throws InterruptedException {
        AdSelectionConfig config =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                Arrays.asList(
                                        CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2))
                        .build();

        CountDownLatch callbackLatch = new CountDownLatch(1);
        AdSelectionTestCallback callback = new AdSelectionTestCallback(callbackLatch);

        mAdSelectionServiceImpl.selectAds(
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(config)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build(),
                mCallerMetadataMock,
                callback);

        assertThat(callbackLatch.await(SELECT_ADS_CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        return callback;
    }
}
