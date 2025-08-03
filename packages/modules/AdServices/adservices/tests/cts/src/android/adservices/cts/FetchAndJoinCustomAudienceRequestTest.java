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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class FetchAndJoinCustomAudienceRequestTest {
    public static final Uri VALID_FETCH_URI_1 =
            CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_1, "1");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidRequest_all_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        assertThat(request.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildValidRequest_onlyFetchUri_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1).build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
    }

    @Test
    public void testBuildValidRequest_withName_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        assertThat(request.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
    }

    @Test
    public void testBuildValidRequest_withActivationTime_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        assertThat(request.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
    }

    @Test
    public void testBuildValidRequest_withExpirationTime_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        assertThat(request.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    @Test
    public void testBuildValidRequest_withUserBiddingSignals_success() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(request.getFetchUri()).isEqualTo(VALID_FETCH_URI_1);
        assertThat(request.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testBuildNullFetchUri_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FetchAndJoinCustomAudienceRequest.Builder(null)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .build());
    }

    @Test
    public void testEquals_identical() {
        final FetchAndJoinCustomAudienceRequest request1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        final FetchAndJoinCustomAudienceRequest request2 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(request1.equals(request2)).isTrue();
        assertThat(request2.equals(request1)).isTrue();
    }

    @Test
    public void testEquals_different() {
        final FetchAndJoinCustomAudienceRequest request1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        final FetchAndJoinCustomAudienceRequest request2 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME + "123")
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        assertThat(request1.equals(request2)).isFalse();
        assertThat(request2.equals(request1)).isFalse();
    }

    @Test
    public void testEquals_null() {
        final FetchAndJoinCustomAudienceRequest request1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        final FetchAndJoinCustomAudienceRequest request2 = null;

        assertThat(request1.equals(request2)).isFalse();
    }

    @Test
    public void testHashCode_identical() {
        final int hashCode1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build()
                        .hashCode();

        final int hashCode2 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build()
                        .hashCode();

        assertThat(hashCode1 == hashCode2).isTrue();
    }

    @Test
    public void testHashCode_different() {
        final int hashCode1 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build()
                        .hashCode();

        final int hashCode2 =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_OWNER)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build()
                        .hashCode();

        assertThat(hashCode1 == hashCode2).isFalse();
    }

    @Test
    public void testToString() {
        final FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(VALID_FETCH_URI_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        final String expected =
                String.format(
                        "FetchAndJoinCustomAudienceRequest{fetchUri=%s, name=%s,"
                                + " activationTime=%s, expirationTime=%s, userBiddingSignals=%s}",
                        VALID_FETCH_URI_1,
                        CustomAudienceFixture.VALID_NAME,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        assertThat(request.toString()).isEqualTo(expected);
    }
}
