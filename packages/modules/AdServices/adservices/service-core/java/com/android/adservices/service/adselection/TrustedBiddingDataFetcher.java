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

import static com.android.adservices.data.customaudience.DBTrustedBiddingData.QUERY_PARAM_KEYS;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Data fetcher of Trusted Bidding data grouped by base URI. */
public class TrustedBiddingDataFetcher {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final DevContext mDevContext;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final ExecutorService mLightweightExecutorService;
    private final boolean mDataVersionHeaderEnabled;

    public TrustedBiddingDataFetcher(
            AdServicesHttpsClient adServicesHttpsClient,
            DevContext devContext,
            CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            ExecutorService lightweightExecutorService,
            boolean dataVersionHeaderEnabled) {
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContext = devContext;
        mCustomAudienceDevOverridesHelper = customAudienceDevOverridesHelper;
        mLightweightExecutorService = lightweightExecutorService;
        mDataVersionHeaderEnabled = dataVersionHeaderEnabled;
    }

    /**
     * Extract key value pairs from a combined fetched trusted bidding data.
     *
     * @param combinedFetchedKeyValues the key values pairs with a certain base uri
     * @param keys the keys required from the combined fetched data
     * @return JSON string of the key values being requested.
     * @throws JSONException not suppose to be thrown as the JSON extraction is checking if a number
     *     is valid while the number is actually from another JSON object.
     */
    public static AdSelectionSignals extractKeys(
            JSONObject combinedFetchedKeyValues, List<String> keys)
            throws JSONException, IOException {
        if (combinedFetchedKeyValues == null) {
            throw new IOException("No such key value server address.");
        }
        return AdSelectionSignals.fromString(
                new JSONObject(combinedFetchedKeyValues, keys.toArray(new String[0])).toString());
    }

    /**
     * Get the trusted bidding data for a group of custom audience.
     *
     * <ol>
     *   <li>Filter out custom audiences with dev override, if dev option enabled.
     *   <li>Group custom audiences with the same base trusted bidding data uri.
     *   <li>Combine trusted bidding keys in each group.
     *   <li>Make 1 server call per base uri.
     *   <li>Return key value pairs per base uri.
     *       <ol/>
     *
     * @param customAudiences the custom audiences from the same buyer.
     * @return trusted bidding data per base uri.
     */
    public FluentFuture<Map<Uri, TrustedBiddingResponse>> getTrustedBiddingDataForBuyer(
            List<DBCustomAudience> customAudiences) {
        List<DBCustomAudience> customAudiencesWithoutOverride;
        if (mDevContext.getDevOptionsEnabled()) {
            sLogger.v("Filtering CA with dev override.");
            customAudiencesWithoutOverride =
                    customAudiences.stream()
                            .filter(
                                    customAudience ->
                                            mCustomAudienceDevOverridesHelper
                                                            .getTrustedBiddingSignalsOverride(
                                                                    customAudience.getOwner(),
                                                                    customAudience.getBuyer(),
                                                                    customAudience.getName())
                                                    == null)
                            .collect(Collectors.toList());
        } else {
            customAudiencesWithoutOverride = customAudiences;
        }

        return FluentFuture.from(
                        Futures.successfulAsList(
                                customAudiencesWithoutOverride.stream()
                                        .map(DBCustomAudience::getTrustedBiddingData)
                                        .filter(Objects::nonNull)
                                        .collect(
                                                Collectors.groupingBy(DBTrustedBiddingData::getUri))
                                        .entrySet()
                                        .stream()
                                        .map(
                                                entry -> {
                                                    Uri baseUri = entry.getKey();
                                                    Set<String> allKeys =
                                                            entry.getValue().stream()
                                                                    .map(
                                                                            DBTrustedBiddingData
                                                                                    ::getKeys)
                                                                    .flatMap(List::stream)
                                                                    .collect(Collectors.toSet());
                                                    return getTrustedBiddingDataByBatch(
                                                                    baseUri, allKeys)
                                                            .transform(
                                                                    s -> new Pair<>(baseUri, s),
                                                                    mLightweightExecutorService);
                                                })
                                        .collect(Collectors.toList())))
                .transform(
                        pairs ->
                                pairs.stream()
                                        .filter(Objects::nonNull)
                                        .filter(pair -> pair.second != null)
                                        .collect(Collectors.toMap(p -> p.first, p -> p.second)),
                        mLightweightExecutorService);
    }

    private FluentFuture<TrustedBiddingResponse> getTrustedBiddingDataByBatch(
            final Uri trustedBiddingUrl, final Set<String> keys) {
        Uri trustedBiddingUriWithKeys = getTrustedBiddingUriWithKeys(trustedBiddingUrl, keys);
        return FluentFuture.from(fetchTrustedBiddingData(trustedBiddingUriWithKeys))
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.v(
                                    "Error fetching trusted bidding data for %s: %s",
                                    trustedBiddingUriWithKeys, e);
                            return null;
                        },
                        mLightweightExecutorService)
                .transform(
                        TrustedBiddingDataFetcher::extractTrustedBiddingResponseIntoJSON,
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.v(
                                    "Error parsing trusted bidding data for %s: %s",
                                    trustedBiddingUriWithKeys, e);
                            return null;
                        },
                        mLightweightExecutorService);
    }

    private ListenableFuture<AdServicesHttpClientResponse> fetchTrustedBiddingData(Uri uri) {
        if (mDataVersionHeaderEnabled) {
            return mAdServicesHttpsClient.fetchPayload(
                    uri, ImmutableSet.of(DATA_VERSION_HEADER_BIDDING_KEY), mDevContext);
        } else {
            return mAdServicesHttpsClient.fetchPayload(uri, mDevContext);
        }
    }

    private Uri getTrustedBiddingUriWithKeys(
            Uri trustedBiddingUri, final Set<String> trustedBiddingKeys) {
        final String keysQueryParams = String.join(",", trustedBiddingKeys);
        return Uri.parse(trustedBiddingUri.toString())
                .buildUpon()
                .appendQueryParameter(QUERY_PARAM_KEYS, keysQueryParams)
                .build();
    }

    private static TrustedBiddingResponse extractTrustedBiddingResponseIntoJSON(
            AdServicesHttpClientResponse response) {
        JSONObject trustedBiddingResponse =
                Optional.ofNullable(response.getResponseBody())
                        .map(
                                r -> {
                                    try {
                                        sLogger.v("Keys are: %s", r);
                                        return new JSONObject(r);
                                    } catch (JSONException e) {
                                        return null;
                                    }
                                })
                        .orElse(null);

        JSONObject trustedBiddingHeaders =
                Optional.ofNullable(response.getResponseHeaders())
                        .map(
                                h -> {
                                    sLogger.v("Headers are: %s", h);
                                    return new JSONObject(h);
                                })
                        .orElse(null);

        return TrustedBiddingResponse.builder()
                .setBody(trustedBiddingResponse)
                .setHeaders(trustedBiddingHeaders)
                .build();
    }
}
