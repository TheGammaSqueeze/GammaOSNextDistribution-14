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

package android.adservices.cts;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link android.adservices.customaudience.CustomAudience} */
public final class CustomAudienceTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    // TODO(b/291488819) - Remove SDK Level check if Fledge is enabled on R.
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Test
    public void testBuildValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        assertEquals(CommonFixture.VALID_BUYER_1, validCustomAudience.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, validCustomAudience.getName());
        assertEquals(
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                validCustomAudience.getActivationTime());
        assertEquals(
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                validCustomAudience.getExpirationTime());
        assertEquals(
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getDailyUpdateUri());
        assertEquals(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                validCustomAudience.getUserBiddingSignals());
        assertEquals(
                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                        CommonFixture.VALID_BUYER_1),
                validCustomAudience.getTrustedBiddingData());
        assertEquals(
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getBiddingLogicUri());
        assertEquals(
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getAds());
    }

    @Test
    public void testBuildValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertThat(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
        assertThat(validCustomAudience.getAuctionServerRequestFlags())
                .isEqualTo(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS);
    }

    @Test
    public void testBuildValidDelayedActivationCustomAudienceSuccess() {
        CustomAudience validDelayedActivationCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                        .build();

        assertThat(validDelayedActivationCustomAudience.getBuyer())
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(validDelayedActivationCustomAudience.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(validDelayedActivationCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validDelayedActivationCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(validDelayedActivationCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validDelayedActivationCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(validDelayedActivationCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testParcelValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(validCustomAudience, fromParcel);
    }

    @Test
    public void testParcelValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        Parcel p = Parcel.obtain();
        try {
            validCustomAudience.writeToParcel(p, 0);
            p.setDataPosition(0);
            CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

            assertEquals(validCustomAudience, fromParcel);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testParcelValidCustomAudienceWithNullValueSuccess() {
        CustomAudience validCustomAudienceWithNullValue =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(null)
                        .setTrustedBiddingData(null)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudienceWithNullValue.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(validCustomAudienceWithNullValue, fromParcel);
    }

    @Test
    public void testNonNullValueNotSetBuildFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // No buyer were set
                    new CustomAudience.Builder()
                            .setName(CustomAudienceFixture.VALID_NAME)
                            .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                            .setDailyUpdateUri(
                                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                            .setTrustedBiddingData(
                                    TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setBiddingLogicUri(
                                    CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setAds(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1))
                            .build();
                });
    }

    @Test
    public void testSetNullToNonNullValueFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // No buyer were set
                    new CustomAudience.Builder()
                            .setBuyer(null)
                            .build();
                });
    }

    @Test
    public void testBuildNullAdsCustomAudienceSuccess() {
        // Ads are not set, so the CustomAudience gets built with empty list.
        CustomAudience nullAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(null)
                        .build();

        assertThat(nullAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(nullAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(nullAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(nullAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(nullAdsCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(nullAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(nullAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(nullAdsCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(nullAdsCustomAudience.getAds()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testBuildEmptyAdsCustomAudienceSuccess() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<AdData> emptyAds = new ArrayList<>(Collections.emptyList());

        CustomAudience emptyAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(emptyAds)
                        .build();

        assertThat(emptyAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        assertThat(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(emptyAdsCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(emptyAdsCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        assertThat(emptyAdsCustomAudience.getAds()).isEqualTo(emptyAds);
    }

    @Test
    public void testCustomAudienceDescribeContent() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        assertThat(validCustomAudience.describeContents()).isEqualTo(0);
    }

    @Test
    @RequiresLowRamDevice
    public void testGetCustomAudienceService_lowRamDevice_throwsIllegalStateException() {
        AdvertisingCustomAudienceClient client =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(sCallbackExecutor)
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setDailyUpdateUri(Uri.parse("http://example.com"))
                        .setTrustedBiddingData(
                                new TrustedBiddingData.Builder()
                                        .setTrustedBiddingKeys(ImmutableList.of())
                                        .setTrustedBiddingUri(Uri.parse("http://example.com"))
                                        .build())
                        .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                        .setAds(List.of())
                        .setBiddingLogicUri(Uri.parse("http://example.com"))
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(Instant.now())
                        .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> client.joinCustomAudience(customAudience).get());
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception).hasMessageThat().contains("service is not available");
    }
}
