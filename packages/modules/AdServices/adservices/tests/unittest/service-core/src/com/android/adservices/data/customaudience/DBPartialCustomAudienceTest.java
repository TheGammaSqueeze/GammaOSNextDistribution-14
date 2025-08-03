/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class DBPartialCustomAudienceTest extends AdServicesUnitTestCase {

    private static final long VALID_UPDATE_ID = 1L;
    private static final String VALID_CA_NAME = "running_shoes";
    private static final Instant VALID_ACTIVATION_TIME = CommonFixture.FIXED_NOW;
    private static final Instant VALID_EXPIRATION_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final AdSelectionSignals VALID_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(SIGNALS_STRING);

    @Test
    public void testBuildDBPartialCustomAudience_BuilderSuccess() {
        DBPartialCustomAudience dbPartialCustomAudience =
                DBPartialCustomAudience.builder()
                        .setUpdateId(VALID_UPDATE_ID)
                        .setName(VALID_CA_NAME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();

        expect.withMessage("Partial Custom Audience Update Id")
                .that(dbPartialCustomAudience.getUpdateId())
                .isEqualTo(VALID_UPDATE_ID);
        expect.withMessage("Partial Custom Audience Name")
                .that(dbPartialCustomAudience.getName())
                .isEqualTo(VALID_CA_NAME);
        expect.withMessage("Partial Custom Audience Activation Time")
                .that(dbPartialCustomAudience.getActivationTime())
                .isEqualTo(VALID_ACTIVATION_TIME);
        expect.withMessage("Partial Custom Audience Expiration Time")
                .that(dbPartialCustomAudience.getExpirationTime())
                .isEqualTo(VALID_EXPIRATION_TIME);
        expect.withMessage("Partial Custom Audience User Bidding Signals")
                .that(dbPartialCustomAudience.getUserBiddingSignals())
                .isEqualTo(VALID_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildDBPartialCustomAudience_CreateSuccess() {
        DBPartialCustomAudience dbPartialCustomAudience =
                DBPartialCustomAudience.create(
                        VALID_UPDATE_ID,
                        VALID_CA_NAME,
                        VALID_ACTIVATION_TIME,
                        VALID_EXPIRATION_TIME,
                        VALID_BIDDING_SIGNALS);

        expect.withMessage("Partial Custom Audience Update Id")
                .that(dbPartialCustomAudience.getUpdateId())
                .isEqualTo(VALID_UPDATE_ID);
        expect.withMessage("Partial Custom Audience Name")
                .that(dbPartialCustomAudience.getName())
                .isEqualTo(VALID_CA_NAME);
        expect.withMessage("Partial Custom Audience Activation Time")
                .that(dbPartialCustomAudience.getActivationTime())
                .isEqualTo(VALID_ACTIVATION_TIME);
        expect.withMessage("Partial Custom Audience Expiration Time")
                .that(dbPartialCustomAudience.getExpirationTime())
                .isEqualTo(VALID_EXPIRATION_TIME);
        expect.withMessage("Partial Custom Audience User Bidding Signals")
                .that(dbPartialCustomAudience.getUserBiddingSignals())
                .isEqualTo(VALID_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildDBPartialCustomAudience_WithNullName_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBPartialCustomAudience.builder()
                                .setUpdateId(VALID_UPDATE_ID)
                                .setActivationTime(VALID_ACTIVATION_TIME)
                                .setExpirationTime(VALID_EXPIRATION_TIME)
                                .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                                .build());
    }

    @Test
    public void testBuildDBPartialCustomAudience_WithNullUpdateId_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBPartialCustomAudience.builder()
                                .setName(VALID_CA_NAME)
                                .setActivationTime(VALID_ACTIVATION_TIME)
                                .setExpirationTime(VALID_EXPIRATION_TIME)
                                .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                                .build());
    }
}
