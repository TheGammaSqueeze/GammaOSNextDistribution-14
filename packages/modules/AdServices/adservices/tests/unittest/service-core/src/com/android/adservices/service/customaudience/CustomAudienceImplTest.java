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

package com.android.adservices.service.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorImpl;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.devapi.DevContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceImplTest {
    private static final CustomAudience VALID_CUSTOM_AUDIENCE =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .build();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS =
            CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                            CommonFixture.VALID_BUYER_1, FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                    .build();

    private static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();

    private static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS =
            DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                    .build();

    private static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(
                            CommonFixture.VALID_BUYER_1)
                    .build();

    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);

    private static final DevContext DEV_OPTIONS_DISABLED = DevContext.createForDevOptionsDisabled();
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private CustomAudienceQuantityChecker mCustomAudienceQuantityCheckerMock;
    @Mock private Validator<CustomAudience> mCustomAudienceValidatorMock;
    @Mock private Clock mClockMock;

    private CustomAudienceImpl mImpl;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mImpl =
                new CustomAudienceImpl(
                        mCustomAudienceDaoMock,
                        mCustomAudienceQuantityCheckerMock,
                        mCustomAudienceValidatorMock,
                        mClockMock,
                        CommonFixture.FLAGS_FOR_TEST);
    }

    @Test
    public void testJoinCustomAudience_runNormally() {

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        mImpl.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE, CustomAudienceFixture.VALID_OWNER, DEV_OPTIONS_DISABLED);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        VALID_DB_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        false);
        verify(mClockMock).instant();
        verify(mCustomAudienceQuantityCheckerMock)
                .check(VALID_CUSTOM_AUDIENCE, CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceValidatorMock).validate(VALID_CUSTOM_AUDIENCE);
        verifyNoMoreInteractions(mClockMock, mCustomAudienceDaoMock, mCustomAudienceValidatorMock);
    }

    @Test
    public void testJoinCustomAudience_withDevOptionsEnabled() {
        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        mImpl.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE,
                CustomAudienceFixture.VALID_OWNER,
                DevContext.builder().setDevOptionsEnabled(true).build());

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        VALID_DB_CUSTOM_AUDIENCE,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        true);
        verify(mClockMock).instant();
        verify(mCustomAudienceQuantityCheckerMock)
                .check(VALID_CUSTOM_AUDIENCE, CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceValidatorMock).validate(VALID_CUSTOM_AUDIENCE);
        verifyNoMoreInteractions(mClockMock, mCustomAudienceDaoMock, mCustomAudienceValidatorMock);
    }

    @Test
    public void testJoinCustomAudienceWithServerAuctionFlags_runNormallyFlagEnabled() {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                new FlagsFactory.TestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                        return true;
                    }
                };

        CustomAudienceImpl customAudienceImpl =
                new CustomAudienceImpl(
                        mCustomAudienceDaoMock,
                        mCustomAudienceQuantityCheckerMock,
                        mCustomAudienceValidatorMock,
                        mClockMock,
                        flagsWithAuctionServerRequestFlagsEnabled);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        customAudienceImpl.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS,
                CustomAudienceFixture.VALID_OWNER,
                DEV_OPTIONS_DISABLED);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        VALID_DB_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        false);
        verify(mClockMock).instant();
        verify(mCustomAudienceQuantityCheckerMock)
                .check(
                        VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS,
                        CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceValidatorMock).validate(VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS);
        verifyNoMoreInteractions(mClockMock, mCustomAudienceDaoMock, mCustomAudienceValidatorMock);
    }

    @Test
    public void testJoinCustomAudienceWithServerAuctionFlags_runNormallyFlagDisabled() {
        Flags flagsWithAuctionServerRequestFlagsDisabled =
                new FlagsFactory.TestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                        return false;
                    }
                };

        CustomAudienceImpl customAudienceImpl =
                new CustomAudienceImpl(
                        mCustomAudienceDaoMock,
                        mCustomAudienceQuantityCheckerMock,
                        mCustomAudienceValidatorMock,
                        mClockMock,
                        flagsWithAuctionServerRequestFlagsDisabled);

        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);

        customAudienceImpl.joinCustomAudience(
                VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS,
                CustomAudienceFixture.VALID_OWNER,
                DEV_OPTIONS_DISABLED);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        false);
        verify(mClockMock).instant();
        verify(mCustomAudienceQuantityCheckerMock)
                .check(
                        VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS,
                        CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceValidatorMock).validate(VALID_CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS);
        verifyNoMoreInteractions(mClockMock, mCustomAudienceDaoMock, mCustomAudienceValidatorMock);
    }

    @Test
    public void testJoinCustomAudienceWithSubdomains_runNormally() {
        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        doReturn(
                        CustomAudienceStats.builder()
                                .setTotalCustomAudienceCount(1)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setPerOwnerCustomAudienceCount(1)
                                .setPerBuyerCustomAudienceCount(1)
                                .setTotalBuyerCount(1)
                                .setTotalOwnerCount(1)
                                .build())
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceStats(eq(CustomAudienceFixture.VALID_OWNER));

        CustomAudience customAudienceWithValidSubdomains =
                CustomAudienceFixture.getValidBuilderWithSubdomainsForBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .build();

        CustomAudienceImpl implWithRealValidators =
                new CustomAudienceImpl(
                        mCustomAudienceDaoMock,
                        new CustomAudienceQuantityChecker(
                                mCustomAudienceDaoMock, CommonFixture.FLAGS_FOR_TEST),
                        new CustomAudienceValidator(
                                mClockMock,
                                CommonFixture.FLAGS_FOR_TEST,
                                new FrequencyCapAdDataValidatorImpl(),
                                AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP),
                        mClockMock,
                        CommonFixture.FLAGS_FOR_TEST);

        implWithRealValidators.joinCustomAudience(
                customAudienceWithValidSubdomains,
                CustomAudienceFixture.VALID_OWNER,
                DEV_OPTIONS_DISABLED);

        DBCustomAudience expectedDbCustomAudience =
                DBCustomAudience.fromServiceObject(
                        customAudienceWithValidSubdomains,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        Duration.ofMillis(
                                CommonFixture.FLAGS_FOR_TEST
                                        .getFledgeCustomAudienceDefaultExpireInMs()),
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false);

        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        eq(expectedDbCustomAudience),
                        eq(customAudienceWithValidSubdomains.getDailyUpdateUri()),
                        /*debuggable=*/ eq(false));
        verify(mCustomAudienceDaoMock)
                .getCustomAudienceStats(eq(CustomAudienceFixture.VALID_OWNER));

        // Clock called in both CA size validator and on persistence into DB
        verify(mClockMock, times(2)).instant();

        verifyNoMoreInteractions(mClockMock, mCustomAudienceDaoMock);
    }

    @Test
    public void testLeaveCustomAudience_runNormally() {
        mImpl.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME);

        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceDataByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);

        verifyNoMoreInteractions(
                mClockMock,
                mCustomAudienceDaoMock,
                mCustomAudienceQuantityCheckerMock,
                mCustomAudienceValidatorMock);
    }
}
