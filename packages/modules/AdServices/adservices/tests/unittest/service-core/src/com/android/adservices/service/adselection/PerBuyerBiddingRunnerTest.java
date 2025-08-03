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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFiltersFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class PerBuyerBiddingRunnerTest {

    private static final String TEST_BUYER = "buyer";
    private static final String AD_URI_PREFIX = "http://www.domain.com/adverts/123/";
    private static final String FAST_SUFFIX = "FAST";
    private static final String SLOW_SUFFIX = "SLOW";
    private static final long SHORT_SLEEP_MS = 1L;
    private static final long LONG_SLEEP_MS = 10000L;
    private static final long PER_BUYER_TIMEOUT_MS = 1000L;
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder().build();

    AdTechIdentifier mBuyer;
    List<DBCustomAudience> mDBCustomAudienceList;

    @Mock AdBidGenerator mAdBidGeneratorMock;
    @Mock TrustedBiddingDataFetcher mTrustedBiddingDataFetcherMock;
    @Mock AdBiddingOutcome mAdBiddingOutcome;

    private PerBuyerBiddingRunner mPerBuyerBiddingRunner;
    private ScheduledThreadPoolExecutor mScheduledExecutor = AdServicesExecutors.getScheduler();
    private ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    private ListeningExecutorService mExecutor =
            MoreExecutors.listeningDecorator((AdServicesExecutors.getLightWeightExecutor()));

    private ResponseMatcher mSlowResponseMatcher = new ResponseMatcher(SLOW_SUFFIX);
    private ResponseMatcher mFastResponseMatcher = new ResponseMatcher(FAST_SUFFIX);

    private Flags mFlags =
            new Flags() {
                @Override
                public int getAdSelectionMaxConcurrentBiddingCount() {
                    return 1;
                }
            };

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBuyer = AdTechIdentifier.fromString(TEST_BUYER);
        mDBCustomAudienceList = new ArrayList<>();
        mPerBuyerBiddingRunner =
                new PerBuyerBiddingRunner(
                        mAdBidGeneratorMock,
                        mTrustedBiddingDataFetcherMock,
                        mScheduledExecutor,
                        mBackgroundExecutorService,
                        mFlags);

        ExtendedMockito.doReturn(FluentFuture.from(Futures.immediateFuture(ImmutableMap.of())))
                .when(mTrustedBiddingDataFetcherMock)
                .getTrustedBiddingDataForBuyer(ExtendedMockito.any());

        ExtendedMockito.doReturn(createDelayedBiddingOutcome(SHORT_SLEEP_MS))
                .when(mAdBidGeneratorMock)
                .runAdBiddingPerCA(
                        ExtendedMockito.argThat(mFastResponseMatcher),
                        ExtendedMockito.anyMap(),
                        ExtendedMockito.any(AdSelectionSignals.class),
                        ExtendedMockito.any(AdSelectionSignals.class),
                        ExtendedMockito.isA(RunAdBiddingPerCAExecutionLogger.class));

        ExtendedMockito.doReturn(createDelayedBiddingOutcome(LONG_SLEEP_MS))
                .when(mAdBidGeneratorMock)
                .runAdBiddingPerCA(
                        ExtendedMockito.argThat(mSlowResponseMatcher),
                        ExtendedMockito.anyMap(),
                        ExtendedMockito.any(AdSelectionSignals.class),
                        ExtendedMockito.any(AdSelectionSignals.class),
                        ExtendedMockito.isA(RunAdBiddingPerCAExecutionLogger.class));
    }

    @Test
    public void testListPartitioning() {
        List<Integer> numbers_100 = generateList(100);
        Assert.assertEquals(5, mPerBuyerBiddingRunner.partitionList(numbers_100, 5).size());
        Assert.assertEquals(6, mPerBuyerBiddingRunner.partitionList(numbers_100, 6).size());

        List<Integer> numbers_10 = generateList(10);
        Assert.assertEquals(10, mPerBuyerBiddingRunner.partitionList(numbers_10, 12).size());
        Assert.assertEquals(10, mPerBuyerBiddingRunner.partitionList(numbers_10, 10).size());
        Assert.assertEquals(5, mPerBuyerBiddingRunner.partitionList(numbers_10, 6).size());
        Assert.assertEquals(5, mPerBuyerBiddingRunner.partitionList(numbers_10, -6).size());
        Assert.assertEquals(1, mPerBuyerBiddingRunner.partitionList(numbers_10, 0).size());
    }

    @Test
    public void testPerBuyerBidding_AllCASucceed() throws InterruptedException {
        int numSlowCustomAudiences = 0;
        int numFastCustomAudiences = 20;
        runAndValidatePerBuyerBidding(numSlowCustomAudiences, numFastCustomAudiences);
    }

    @Test
    public void testPerBuyerBidding_PartialCATimeout() throws InterruptedException {
        int numSlowCustomAudiences = 10;
        int numFastCustomAudiences = 10;
        runAndValidatePerBuyerBidding(numSlowCustomAudiences, numFastCustomAudiences);
    }

    @Test
    public void testPerBuyerBidding_AllCATimeout() throws InterruptedException {
        int numSlowCustomAudiences = 20;
        int numFastCustomAudiences = 0;
        runAndValidatePerBuyerBidding(numSlowCustomAudiences, numFastCustomAudiences);
    }

    private void runAndValidatePerBuyerBidding(
            int numSlowCustomAudiences, int numFastCustomAudiences) throws InterruptedException {
        List<DBCustomAudience> slowCustomAudiences =
                createCustomAudienceList(numSlowCustomAudiences, SLOW_SUFFIX);
        List<DBCustomAudience> fastCustomAudiences =
                createCustomAudienceList(numFastCustomAudiences, FAST_SUFFIX);

        List<DBCustomAudience> customAudienceList = new ArrayList<>();
        customAudienceList.addAll(fastCustomAudiences);
        customAudienceList.addAll(slowCustomAudiences);

        List<ListenableFuture<AdBiddingOutcome>> biddingOutcomes =
                mPerBuyerBiddingRunner.runBidding(
                        mBuyer, customAudienceList, PER_BUYER_TIMEOUT_MS, AD_SELECTION_CONFIG);

        Thread.sleep(2 * PER_BUYER_TIMEOUT_MS);

        int completedBids = 0;
        int cancelledIncompleteBids = 0;

        for (ListenableFuture<AdBiddingOutcome> bidOutcome : biddingOutcomes) {
            if (bidOutcome.isCancelled()) {
                cancelledIncompleteBids++;
            } else {
                completedBids++;
            }
        }

        Assert.assertEquals(
                "Number of timed out bids, does not match cancelled bids",
                numSlowCustomAudiences,
                cancelledIncompleteBids);
        Assert.assertEquals(
                "Number of completed bids, does not match successful bids",
                numFastCustomAudiences,
                completedBids);
    }

    private ListenableFuture<AdBiddingOutcome> createDelayedBiddingOutcome(long delayTime) {
        return mExecutor.submit(
                () -> {
                    Thread.sleep(delayTime);
                    return mAdBiddingOutcome;
                });
    }

    private List<DBCustomAudience> createCustomAudienceList(int count, String suffix) {
        List<DBCustomAudience> customAudienceList = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            customAudienceList.add(createDBCustomAudience(mBuyer, String.valueOf(i), suffix));
        }
        return customAudienceList;
    }

    /**
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer, final String namePrefix, final String nameSuffix) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();
        List<Double> bids = ImmutableList.of(1.0, 2.0, 3.0, 4.0);
        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            DBAdData.Builder builder =
                    new DBAdData.Builder()
                            .setRenderUri(Uri.parse(AD_URI_PREFIX + buyer + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}");

            switch (i % 4) {
                case 0:
                    builder.setAdCounterKeys(AdDataFixture.getAdCounterKeys());
                    break;
                case 1:
                    builder.setAdFilters(AdFiltersFixture.getValidAdFilters());
                    break;
                case 2:
                    builder.setAdCounterKeys(AdDataFixture.getAdCounterKeys());
                    builder.setAdFilters(AdFiltersFixture.getValidAdFilters());
                    break;
            }
            ads.add(builder.build());
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(
                        buyer.toString()
                                + namePrefix
                                + CustomAudienceFixture.VALID_NAME
                                + nameSuffix)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUri(Uri.parse("https://www.example.com/trusted"))
                                .setKeys(TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(Uri.parse("https://www.example.com/logic"))
                .setAds(ads)
                .build();
    }

    private List<Integer> generateList(int size) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    private static class ResponseMatcher implements ArgumentMatcher<DBCustomAudience> {
        private String mSuffix;

        ResponseMatcher(String suffix) {
            this.mSuffix = suffix;
        }

        @Override
        public boolean matches(DBCustomAudience right) {
            return right.getName().endsWith(mSuffix);
        }
    }
}
