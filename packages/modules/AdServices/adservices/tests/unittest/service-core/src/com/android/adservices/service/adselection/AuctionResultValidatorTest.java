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

import static com.android.adservices.service.adselection.AuctionResultValidator.BUYER_EMPTY;
import static com.android.adservices.service.adselection.AuctionResultValidator.BUYER_ENROLLMENT;
import static com.android.adservices.service.adselection.AuctionResultValidator.NEGATIVE_BID;
import static com.android.adservices.service.adselection.AuctionResultValidator.NEGATIVE_SCORE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ValidatorTestUtil;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collection;

public class AuctionResultValidatorTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private static final AdTechIdentifier WINNER_BUYER = CommonFixture.VALID_BUYER_1;
    private static final DBAdData VALID_AD =
            DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(WINNER_BUYER).get(0);
    private static final Uri VALID_AD_RENDER_URI = VALID_AD.getRenderUri();
    private static final float VALID_BID = 20.0F;
    private static final float VALID_SCORE = 30.0F;
    private static final String CUSTOM_AUDIENCE_NAME = "test-name";

    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;

    private AuctionResultValidator mAuctionResultValidator;

    private static AuctionResult.Builder getValidAuctionResultBuilder() {
        return AuctionResult.newBuilder()
                .setAdRenderUrl(VALID_AD_RENDER_URI.toString())
                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME)
                .setCustomAudienceOwner(CommonFixture.VALID_BUYER_1.toString())
                .setIsChaff(false)
                .setBuyer(CommonFixture.VALID_BUYER_1.toString())
                .setScore(VALID_SCORE)
                .setBid(VALID_BID);
    }

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mAuctionResultValidator =
                new AuctionResultValidator(
                        mFledgeAuthorizationFilterMock, false
                        /** disableFledgeEnrollmentCheck */
                        );
    }

    @Test
    public void testValidate_noError() {
        mAuctionResultValidator.validate(getValidAuctionResultBuilder().build());
    }

    @Test
    public void testValidate_negativeBid() {
        float negativeBid = -1.2F;
        Collection<String> violations =
                mAuctionResultValidator.getValidationViolations(
                        getValidAuctionResultBuilder().setBid(negativeBid).build());
        ValidatorTestUtil.assertViolationContainsOnly(
                violations, String.format(String.format(NEGATIVE_BID, negativeBid)));
    }

    @Test
    public void testValidate_negativeScore() {
        float negativeScore = -1.2F;
        Collection<String> violations =
                mAuctionResultValidator.getValidationViolations(
                        getValidAuctionResultBuilder().setScore(negativeScore).build());
        ValidatorTestUtil.assertViolationContainsOnly(
                violations, String.format(String.format(NEGATIVE_SCORE, negativeScore)));
    }

    @Test
    public void testValidate_buyerEmpty() {
        Collection<String> violations =
                mAuctionResultValidator.getValidationViolations(
                        getValidAuctionResultBuilder().setBuyer("").build());
        ValidatorTestUtil.assertViolationContainsOnly(violations, BUYER_EMPTY);
    }

    @Test
    public void testValidate_buyerNotEnrolled() {
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(any(AdTechIdentifier.class), anyInt());
        Collection<String> violations =
                mAuctionResultValidator.getValidationViolations(
                        getValidAuctionResultBuilder().build());
        ValidatorTestUtil.assertViolationContainsOnly(
                violations,
                String.format(
                        String.format(BUYER_ENROLLMENT, CommonFixture.VALID_BUYER_1.toString())));
    }

    @Test
    public void testValidate_enrollmentCheckDisabled_buyerNotEnrolled_noViolations() {
        AuctionResultValidator validationWithNoEnrollmentCheck =
                new AuctionResultValidator(mFledgeAuthorizationFilterMock, true);
        validationWithNoEnrollmentCheck.validate(getValidAuctionResultBuilder().build());
    }
}
