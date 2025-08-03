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

package android.adservices.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.time.Instant;

public class PartialCustomAudienceTest extends AdServicesUnitTestCase {

    private static final String VALID_CA_NAME = "running_shoes";
    private static final Instant VALID_ACTIVATION_TIME = CommonFixture.FIXED_NOW;
    private static final Instant VALID_EXPIRATION_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final AdSelectionSignals VALID_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(SIGNALS_STRING);
    public static PartialCustomAudience VALID_PARTIAL_CA =
            new PartialCustomAudience.Builder(VALID_CA_NAME)
                    .setExpirationTime(VALID_EXPIRATION_TIME)
                    .setActivationTime(VALID_ACTIVATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    @Test
    public void testBuildValidPartialCARequest_AllSetters_Success() {
        PartialCustomAudience partialCA =
                new PartialCustomAudience.Builder(VALID_CA_NAME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();
        expect.that(VALID_PARTIAL_CA.getActivationTime()).isEqualTo(VALID_ACTIVATION_TIME);
        expect.that(VALID_PARTIAL_CA.getExpirationTime()).isEqualTo(VALID_EXPIRATION_TIME);
        expect.that(VALID_PARTIAL_CA.getUserBiddingSignals()).isEqualTo(VALID_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildValidPartialCARequestParcel_Success() {
        PartialCustomAudience ca = VALID_PARTIAL_CA;
        expect.that(VALID_PARTIAL_CA.getActivationTime()).isNotNull();
        expect.that(VALID_PARTIAL_CA.getExpirationTime()).isNotNull();
        expect.that(VALID_PARTIAL_CA.getUserBiddingSignals()).isNotNull();

        Parcel p = Parcel.obtain();
        ca.writeToParcel(p, 0);
        p.setDataPosition(0);
        PartialCustomAudience fromParcel = PartialCustomAudience.CREATOR.createFromParcel(p);

        expect.that(ca.getName()).isEqualTo(fromParcel.getName());
        expect.that(ca.getActivationTime().getEpochSecond())
                .isEqualTo(fromParcel.getActivationTime().getEpochSecond());
        expect.that(ca.getExpirationTime().getEpochSecond())
                .isEqualTo(fromParcel.getExpirationTime().getEpochSecond());
        expect.that(ca.getUserBiddingSignals()).isEqualTo(fromParcel.getUserBiddingSignals());
    }

    @Test
    public void testEquals_Same() {
        PartialCustomAudience partialCa1 =
                new PartialCustomAudience.Builder(VALID_CA_NAME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();

        PartialCustomAudience partialCa2 =
                new PartialCustomAudience.Builder(VALID_CA_NAME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();

        expectObjectsAreEqual(partialCa1, partialCa2);
    }

    @Test
    public void testHashCodeSame() {
        int partialCa1Hash =
                new PartialCustomAudience.Builder(VALID_CA_NAME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build()
                        .hashCode();

        int partialCa2Hash =
                new PartialCustomAudience.Builder(VALID_CA_NAME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build()
                        .hashCode();
        expect.that(VALID_PARTIAL_CA.getActivationTime()).isEqualTo(VALID_ACTIVATION_TIME);
        expect.that(VALID_PARTIAL_CA.getExpirationTime()).isEqualTo(VALID_EXPIRATION_TIME);
        expect.that(VALID_PARTIAL_CA.getUserBiddingSignals()).isEqualTo(VALID_BIDDING_SIGNALS);

        expectObjectsAreEqual(partialCa1Hash, partialCa2Hash);
    }

    @Test
    public void testToString() {
        PartialCustomAudience ca = VALID_PARTIAL_CA;

        String expected =
                String.format(
                        "PartialCustomAudience {name=running_shoes, "
                                + "activationTime=%s, expirationTime=%s"
                                + ", userBiddingSignals={\"a\":\"b\"}}",
                        VALID_ACTIVATION_TIME, VALID_EXPIRATION_TIME);
        expect.that(expected).isEqualTo(ca.toString());
    }
}
