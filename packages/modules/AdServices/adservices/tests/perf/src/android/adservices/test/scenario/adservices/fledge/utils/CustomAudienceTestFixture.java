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

package android.adservices.test.scenario.adservices.fledge.utils;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TODO(b/289346282) : Use this class for CA management in all CB tests
public class CustomAudienceTestFixture {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    protected static final int API_RESPONSE_TIMEOUT_SECONDS = 100;

    protected static final AdvertisingCustomAudienceClient CUSTOM_AUDIENCE_CLIENT =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    public static void leaveCustomAudience(List<CustomAudience> cas) throws Exception {
        for (CustomAudience ca : cas) {
            CUSTOM_AUDIENCE_CLIENT
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    public static ImmutableList<CustomAudience> readCustomAudiences(String fileName)
            throws Exception {
        ImmutableList.Builder<CustomAudience> customAudienceBuilder = ImmutableList.builder();
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        JSONArray customAudiencesJson = new JSONArray(readString(is));
        is.close();

        for (int i = 0; i < customAudiencesJson.length(); i++) {
            JSONObject caJson = customAudiencesJson.getJSONObject(i);
            JSONObject trustedBiddingDataJson = caJson.getJSONObject("trustedBiddingData");
            JSONArray trustedBiddingKeysJson =
                    trustedBiddingDataJson.getJSONArray("trustedBiddingKeys");
            JSONArray adsJson = caJson.getJSONArray("ads");

            ImmutableList.Builder<String> biddingKeys = ImmutableList.builder();
            for (int index = 0; index < trustedBiddingKeysJson.length(); index++) {
                biddingKeys.add(trustedBiddingKeysJson.getString(index));
            }

            ImmutableList.Builder<AdData> adDatas = ImmutableList.builder();
            for (int index = 0; index < adsJson.length(); index++) {
                JSONObject adJson = adsJson.getJSONObject(index);

                AdData.Builder adDataBuilder =
                        new AdData.Builder()
                                .setRenderUri(Uri.parse(adJson.getString("render_uri")))
                                .setMetadata(adJson.getString("metadata"));

                try {
                    adDataBuilder.setAdRenderId(adJson.getString("ad_render_id"));
                } catch (JSONException e) {
                    // do nothing
                }

                adDatas.add(adDataBuilder.build());
            }

            customAudienceBuilder.add(
                    new CustomAudience.Builder()
                            .setBuyer(AdTechIdentifier.fromString(caJson.getString("buyer")))
                            .setName(caJson.getString("name"))
                            .setActivationTime(Instant.now())
                            .setExpirationTime(Instant.now().plus(90000, ChronoUnit.SECONDS))
                            .setDailyUpdateUri(Uri.parse(caJson.getString("dailyUpdateUri")))
                            .setUserBiddingSignals(
                                    AdSelectionSignals.fromString(
                                            caJson.getString("userBiddingSignals")))
                            .setTrustedBiddingData(
                                    new TrustedBiddingData.Builder()
                                            .setTrustedBiddingKeys(biddingKeys.build())
                                            .setTrustedBiddingUri(
                                                    Uri.parse(
                                                            trustedBiddingDataJson.getString(
                                                                    "trustedBiddingUri")))
                                            .build())
                            .setBiddingLogicUri(Uri.parse(caJson.getString("biddingLogicUri")))
                            .setAds(adDatas.build())
                            .build());
        }
        return customAudienceBuilder.build();
    }

    public static void joinCustomAudiences(List<CustomAudience> customAudiences) throws Exception {
        for (CustomAudience ca : customAudiences) {
            CUSTOM_AUDIENCE_CLIENT
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static String readString(InputStream inputStream) throws IOException {
        // readAllBytes() was added in API level 33. As a result, when this test executes on S-, we
        // will need a workaround to process the InputStream.
        return SdkLevel.isAtLeastT()
                ? new String(inputStream.readAllBytes())
                : CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
