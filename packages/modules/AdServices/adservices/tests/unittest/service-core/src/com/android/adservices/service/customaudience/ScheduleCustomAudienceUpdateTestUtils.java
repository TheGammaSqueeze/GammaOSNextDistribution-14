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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addAuctionServerRequestFlags;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.LEAVE_CUSTOM_AUDIENCE_KEY;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateCallback;
import android.os.RemoteException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Utility that helps test scheduleCustomAudienceUpdate() API */
public class ScheduleCustomAudienceUpdateTestUtils {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final Long UPDATE_ID = 1L;
    public static final String PARTIAL_CA_1 = "partial_ca_1";
    public static final String PARTIAL_CA_2 = "partial_ca_2";
    public static final String LEAVE_CA_1 = "leave_ca_1";
    public static final String LEAVE_CA_2 = "leave_ca_2";
    public static final Instant ACTIVATION_TIME = CommonFixture.FIXED_NOW;
    public static final Instant EXPIRATION_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    public static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    public static final AdSelectionSignals VALID_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(SIGNALS_STRING);
    public static final DBPartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_1 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_1)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    public static final DBPartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_2 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_2)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    /** Creates a JSON response that is expected to be returned from the server for update */
    public static JSONObject createJsonResponsePayload(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames,
            boolean auctionServerRequestFlagsEnabled)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            if (auctionServerRequestFlagsEnabled) {
                // Add auction server request flags
                generatedCa =
                        addAuctionServerRequestFlags(
                                generatedCa,
                                ImmutableList.of(CustomAudienceBlob.OMIT_ADS_VALUE),
                                false);
            }
            joinCustomAudienceArray.put(i, generatedCa);
        }

        JSONArray leaveCustomAudienceArray = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            leaveCustomAudienceArray.put(i, leaveCustomAudienceNames.get(i));
        }

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, leaveCustomAudienceArray);

        return responseJson;
    }

    private static JSONObject generateCustomAudienceWithName(
            AdTechIdentifier buyer, String owner, String name) throws JSONException {

        CustomAudience ca =
                CustomAudienceFixture.getValidBuilderForBuyer(buyer).setName(name).build();
        return CustomAudienceBlobFixture.asJSONObject(
                owner,
                ca.getBuyer(),
                name,
                ca.getActivationTime(),
                ca.getExpirationTime(),
                ca.getDailyUpdateUri(),
                ca.getBiddingLogicUri(),
                AdSelectionSignals.EMPTY.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                Collections.emptyList(),
                false);
    }

    /**
     * Extracts the Partial Custom Audience objects sent in the update request. Helps validate that
     * the request to server had expected payload.
     */
    public static List<CustomAudienceBlob> extractPartialCustomAudiencesFromRequest(
            byte[] requestBody) {
        String requestBodyString = new String(requestBody);
        List<CustomAudienceBlob> overrideCustomAudienceBlobs = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(requestBodyString);

            for (int i = 0; i < jsonArray.length(); i++) {
                CustomAudienceBlob blob = new CustomAudienceBlob();
                blob.overrideFromJSONObject(jsonArray.getJSONObject(i));
                overrideCustomAudienceBlobs.add(blob);
            }
        } catch (JSONException e) {
            sLogger.e(e, "Unable to extract partial CAs from request");
        }
        return overrideCustomAudienceBlobs;
    }

    /**
     * Test-callback that ensures latch is unlatched before we check for API request completion.
     * Also provides success or failure status of an API call.
     */
    public static class ScheduleUpdateTestCallback
            extends ScheduleCustomAudienceUpdateCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public ScheduleUpdateTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public boolean isSuccess() {
            return mIsSuccess;
        }

        @Override
        public void onSuccess() {
            LoggerFactory.getFledgeLogger().v("Reporting success to Schedule CA Update.");
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            LoggerFactory.getFledgeLogger().v("Reporting failure to Schedule CA Update.");
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
