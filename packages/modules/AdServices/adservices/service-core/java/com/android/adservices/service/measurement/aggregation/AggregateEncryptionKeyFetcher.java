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
package com.android.adservices.service.measurement.aggregation;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.measurement.MeasurementHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Download public encryption keys for aggregatable reports.
 *
 * @hide
 */
final class AggregateEncryptionKeyFetcher {
    private final MeasurementHttpClient mNetworkConnection;

    AggregateEncryptionKeyFetcher(Context context) {
        mNetworkConnection = new MeasurementHttpClient(context);
    }

    /**
     * Provides a testing hook.
     */
    public @NonNull URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
    }

    private static long getMaxAgeInSeconds(@NonNull Map<String, List<String>> headers) {
        String cacheControl = null;
        int cachedAge = 0;
        int remainingHeaders = 2;
        for (String key : headers.keySet()) {
            if (key != null) {
                if (key.equalsIgnoreCase("cache-control")) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        cacheControl = field.get(0).toLowerCase(Locale.ENGLISH);
                        remainingHeaders -= 1;
                    }
                } else if (key.equalsIgnoreCase("age")) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        try {
                            cachedAge = Integer.parseInt(field.get(0));
                        } catch (NumberFormatException e) {
                            LoggerFactory.getMeasurementLogger().e(e, "Error parsing age header");
                        }
                        remainingHeaders -= 1;
                    }
                }
            }
            if (remainingHeaders == 0) {
                break;
            }
        }
        if (cacheControl == null) {
            LoggerFactory.getMeasurementLogger().d("Cache-Control header or value is missing");
            return 0;
        }
        String[] tokens = cacheControl.split(",", 0);
        long maxAge = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.startsWith("max-age=")) {
                try {
                    maxAge = Long.parseLong(token.substring(8));
                } catch (NumberFormatException e) {
                    LoggerFactory.getMeasurementLogger().d(e, "Failed to parse max-age value");
                    return 0;
                }
            }
        }
        if (maxAge == 0) {
            LoggerFactory.getMeasurementLogger().d("max-age directive is missing");
            return 0;
        }
        return maxAge - cachedAge;
    }

    private static Optional<List<AggregateEncryptionKey>> parseResponse(
            @NonNull String responseBody,
            @NonNull Map<String, List<String>> headers,
            @NonNull long eventTime,
            @NonNull Uri coordinatorOrigin) {
        long maxAge = getMaxAgeInSeconds(headers);
        if (maxAge <= 0) {
            return Optional.empty();
        }
        try {
            JSONObject responseObj = new JSONObject(responseBody);
            JSONArray keys = responseObj.getJSONArray(ResponseContract.KEYS);
            long expiry = eventTime + TimeUnit.SECONDS.toMillis(maxAge);
            List<AggregateEncryptionKey> aggregateEncryptionKeys = new ArrayList<>();
            for (int i = 0; i < keys.length(); i++) {
                JSONObject keyObj = keys.getJSONObject(i);
                AggregateEncryptionKey.Builder keyBuilder = new AggregateEncryptionKey.Builder();
                keyBuilder.setKeyId(keyObj.getString(ResponseContract.KEYS_KEY_ID));
                keyBuilder.setPublicKey(keyObj.getString(ResponseContract.KEYS_PUBLIC_KEY));
                keyBuilder.setExpiry(expiry);
                keyBuilder.setAggregationCoordinatorOrigin(coordinatorOrigin);
                aggregateEncryptionKeys.add(keyBuilder.build());
            }
            return Optional.of(aggregateEncryptionKeys);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Invalid JSON");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }
    }

    /** Fetch public encryption keys for aggregatable reports. */
    public Optional<List<AggregateEncryptionKey>> fetch(
            @NonNull Uri coordinatorOrigin, @NonNull Uri target, @NonNull long eventTime) {
        // Require https.
        if (!target.getScheme().equals("https")) {
            return Optional.empty();
        }
        URL url;
        try {
            url = new URL(target.toString());
        } catch (MalformedURLException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Malformed coordinator target URL");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Failed to open coordinator target URL");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }
        try {
            urlConnection.setRequestMethod("GET");
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            if (responseCode != 200) {
                return Optional.empty();
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));

            String line;
            StringBuilder responseBody = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                responseBody.append(line);
            }
            bufferedReader.close();

            return parseResponse(responseBody.toString(), headers, eventTime, coordinatorOrigin);
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Failed to get coordinator response");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        } finally {
            urlConnection.disconnect();
        }
    }

    private interface ResponseContract {
        String KEYS = "keys";
        String KEYS_PUBLIC_KEY = "key";
        String KEYS_KEY_ID = "id";
    }
}
