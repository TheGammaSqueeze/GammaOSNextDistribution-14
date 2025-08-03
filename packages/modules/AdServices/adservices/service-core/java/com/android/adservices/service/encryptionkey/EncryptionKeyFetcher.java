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

package com.android.adservices.service.encryptionkey;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType;
import com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

/** Fetch encryption keys. */
public final class EncryptionKeyFetcher {

    public static final String ENCRYPTION_KEY_ENDPOINT = "/.well-known/encryption-keys";
    public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final FetchJobType mFetchJobType;
    private boolean mIsFirstTimeFetch;

    public EncryptionKeyFetcher(@NonNull FetchJobType fetchJobType) {
        mAdServicesLogger = AdServicesLoggerImpl.getInstance();
        mFetchJobType = fetchJobType;
        mIsFirstTimeFetch = false;
    }

    @VisibleForTesting
    EncryptionKeyFetcher(@NonNull AdServicesLogger adServicesLogger) {
        mFetchJobType = FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB;
        mAdServicesLogger = adServicesLogger;
        mIsFirstTimeFetch = false;
    }

    /** Define encryption key endpoint JSON response parameters. */
    @VisibleForTesting
    interface JSONResponseContract {
        String ENCRYPTION_KEY = "encryption_key";
        String SIGNING_KEY = "signing_key";
        String PROTOCOL_TYPE = "protocol_type";
        String KEYS = "keys";
    }

    /** Define parameters for actual keys fields. */
    @VisibleForTesting
    interface KeyResponseContract {
        String ID = "id";
        String BODY = "body";
        String EXPIRY = "expiry";
    }

    /**
     * Send HTTP GET request to Adtech encryption key endpoint, parse JSON response, convert it to
     * EncryptionKey objects.
     *
     * @param encryptionKey the existing encryption key in adservices_shared.db.
     * @param enrollmentData the enrollment data that we needed to fetch keys.
     * @param isFirstTimeFetch whether it is the first time key fetch for the enrollment data.
     * @return a list of encryption keys or Optional.empty() when no key can be fetched.
     */
    public Optional<List<EncryptionKey>> fetchEncryptionKeys(
            @Nullable EncryptionKey encryptionKey,
            @NonNull EnrollmentData enrollmentData,
            boolean isFirstTimeFetch) {
        mIsFirstTimeFetch = isFirstTimeFetch;

        String encryptionKeyUrl = constructEncryptionKeyUrl(enrollmentData);
        if (encryptionKeyUrl == null) {
            logEncryptionKeyFetchedStats(FetchStatus.NULL_ENDPOINT, enrollmentData, null);
            return Optional.empty();
        }
        if (!isEncryptionKeyUrlValid(encryptionKeyUrl)) {
            logEncryptionKeyFetchedStats(
                    FetchStatus.INVALID_ENDPOINT, enrollmentData, encryptionKeyUrl);
            return Optional.empty();
        }

        URL url;
        try {
            url = new URL(encryptionKeyUrl);
        } catch (MalformedURLException e) {
            LoggerFactory.getLogger().d(e, "Malformed encryption key URL.");
            logEncryptionKeyFetchedStats(
                    FetchStatus.INVALID_ENDPOINT, enrollmentData, encryptionKeyUrl);
            return Optional.empty();
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) setUpURLConnection(url);
        } catch (IOException e) {
            LoggerFactory.getLogger().e(e, "Failed to open encryption key URL");
            logEncryptionKeyFetchedStats(
                    FetchStatus.IO_EXCEPTION, enrollmentData, encryptionKeyUrl);
            return Optional.empty();
        }
        try {
            urlConnection.setRequestMethod("GET");
            if (!isFirstTimeFetch
                    && encryptionKey != null
                    && encryptionKey.getLastFetchTime() != 0L) {
                // Re-fetch to update or revoke keys, use "If-Modified-Since" header with time diff
                // set to `last_fetch_time` field saved in EncryptionKey object.
                urlConnection.setRequestProperty(
                        IF_MODIFIED_SINCE_HEADER,
                        constructHttpDate(encryptionKey.getLastFetchTime()));
            }
            int responseCode = urlConnection.getResponseCode();
            if (!isFirstTimeFetch && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                LoggerFactory.getLogger()
                        .d(
                                "Re-fetch encryption key response code = "
                                        + responseCode
                                        + "no change made to previous keys.");
                logEncryptionKeyFetchedStats(
                        FetchStatus.KEY_NOT_MODIFIED, enrollmentData, encryptionKeyUrl);
                return Optional.empty();
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LoggerFactory.getLogger().v("Fetch encryption key response code = " + responseCode);
                logEncryptionKeyFetchedStats(
                        FetchStatus.BAD_REQUEST_EXCEPTION, enrollmentData, encryptionKeyUrl);
                return Optional.empty();
            }
            JSONObject jsonResponse =
                    getJsonResponse(urlConnection, enrollmentData, encryptionKeyUrl);
            if (jsonResponse == null) {
                return Optional.empty();
            }
            return parseEncryptionKeyJSONResponse(enrollmentData, encryptionKeyUrl, jsonResponse);
        } catch (IOException e) {
            LoggerFactory.getLogger().e(e, "Failed to get encryption key response.");
            logEncryptionKeyFetchedStats(
                    FetchStatus.IO_EXCEPTION, enrollmentData, encryptionKeyUrl);
            return Optional.empty();
        } finally {
            urlConnection.disconnect();
        }
    }

    @Nullable
    private static String constructEncryptionKeyUrl(EnrollmentData enrollmentData) {
        // We use encryption key url field in DB to store the Site, and append suffix to construct
        // the actual encryption key url.
        if (enrollmentData.getEncryptionKeyUrl() == null
                || enrollmentData.getEncryptionKeyUrl().trim().isEmpty()) {
            LoggerFactory.getLogger().d("No encryption key url in enrollment data.");
            return null;
        } else {
            return enrollmentData.getEncryptionKeyUrl() + ENCRYPTION_KEY_ENDPOINT;
        }
    }

    private boolean isEncryptionKeyUrlValid(String encryptionKeyUrl) {
        if (Uri.parse(encryptionKeyUrl).getScheme() == null
                || !Uri.parse(encryptionKeyUrl).getScheme().equalsIgnoreCase("https")) {
            LoggerFactory.getLogger().d("Encryption key url doesn't start with https.");
            return false;
        }
        return true;
    }

    @Nullable
    private JSONObject getJsonResponse(
            HttpURLConnection urlConnection,
            EnrollmentData enrollmentData,
            String encryptionKeyUrl) {
        try {
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = bufferedReader.readLine()) != null) {
                response.append(inputLine);
            }
            bufferedReader.close();
            return new JSONObject(response.toString());
        } catch (IOException | JSONException e) {
            logEncryptionKeyFetchedStats(
                    FetchStatus.BAD_REQUEST_EXCEPTION, enrollmentData, encryptionKeyUrl);
            return null;
        }
    }

    /**
     * Parse encryption key endpoint JSONResponse to an EncryptionKey object.
     *
     * @param enrollmentData the enrollment data contains the encryption key url.
     * @param jsonObject JSONResponse received by calling encryption key endpoint.
     * @return a list of encryption keys.
     */
    private Optional<List<EncryptionKey>> parseEncryptionKeyJSONResponse(
            @NonNull EnrollmentData enrollmentData,
            String encryptionKeyUrl,
            @NonNull JSONObject jsonObject) {
        try {
            List<EncryptionKey> encryptionKeys = new ArrayList<>();
            if (jsonObject.has(JSONResponseContract.ENCRYPTION_KEY)) {
                JSONObject encryptionObject =
                        jsonObject.getJSONObject(JSONResponseContract.ENCRYPTION_KEY);
                encryptionKeys.addAll(
                        buildEncryptionKeys(
                                enrollmentData,
                                encryptionObject,
                                EncryptionKey.KeyType.ENCRYPTION));
            }
            if (jsonObject.has(JSONResponseContract.SIGNING_KEY)) {
                JSONObject signingObject =
                        jsonObject.getJSONObject(JSONResponseContract.SIGNING_KEY);
                encryptionKeys.addAll(
                        buildEncryptionKeys(
                                enrollmentData, signingObject, EncryptionKey.KeyType.SIGNING));
            }
            logEncryptionKeyFetchedStats(FetchStatus.SUCCESS, enrollmentData, encryptionKeyUrl);
            return Optional.of(encryptionKeys);
        } catch (JSONException e) {
            LoggerFactory.getLogger().e(e, "Parse json response to encryption key exception.");
            logEncryptionKeyFetchedStats(
                    FetchStatus.BAD_REQUEST_EXCEPTION, enrollmentData, encryptionKeyUrl);
            return Optional.empty();
        }
    }

    private List<EncryptionKey> buildEncryptionKeys(
            EnrollmentData enrollmentData, JSONObject jsonObject, EncryptionKey.KeyType keyType) {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        String encryptionKeyUrl = null;
        Uri reportingOrigin = null;
        if (enrollmentData.getEncryptionKeyUrl() != null) {
            encryptionKeyUrl = enrollmentData.getEncryptionKeyUrl() + ENCRYPTION_KEY_ENDPOINT;
            reportingOrigin = Uri.parse(enrollmentData.getEncryptionKeyUrl());
        }

        EncryptionKey.ProtocolType protocolType;
        try {
            if (jsonObject.has(JSONResponseContract.PROTOCOL_TYPE)) {
                protocolType =
                        EncryptionKey.ProtocolType.valueOf(
                                jsonObject.getString(JSONResponseContract.PROTOCOL_TYPE));
            } else {
                // Set default protocol_type as HPKE for now since HPKE is the only encryption algo.
                protocolType = EncryptionKey.ProtocolType.HPKE;
            }
            if (jsonObject.has(JSONResponseContract.KEYS)) {
                JSONArray keyArray = jsonObject.getJSONArray(JSONResponseContract.KEYS);
                for (int i = 0; i < keyArray.length(); i++) {
                    EncryptionKey.Builder builder = new EncryptionKey.Builder();
                    builder.setId(UUID.randomUUID().toString());
                    builder.setKeyType(keyType);
                    builder.setEnrollmentId(enrollmentData.getEnrollmentId());
                    builder.setReportingOrigin(reportingOrigin);
                    builder.setEncryptionKeyUrl(encryptionKeyUrl);
                    builder.setProtocolType(protocolType);

                    JSONObject keyObject = keyArray.getJSONObject(i);
                    if (keyObject.has(KeyResponseContract.ID)) {
                        builder.setKeyCommitmentId(keyObject.getInt(KeyResponseContract.ID));
                    }
                    if (keyObject.has(KeyResponseContract.BODY)) {
                        builder.setBody(keyObject.getString(KeyResponseContract.BODY));
                    }
                    if (keyObject.has(KeyResponseContract.EXPIRY)) {
                        builder.setExpiration(
                                Long.parseLong(keyObject.getString(KeyResponseContract.EXPIRY)));
                    }
                    builder.setLastFetchTime(System.currentTimeMillis());
                    encryptionKeyList.add(builder.build());
                }
            }
        } catch (JSONException e) {
            LoggerFactory.getLogger()
                    .e(
                            e,
                            "Failed to build encryption key from json object exception."
                                    + jsonObject);
            logEncryptionKeyFetchedStats(
                    FetchStatus.BAD_REQUEST_EXCEPTION, enrollmentData, encryptionKeyUrl);
        }
        return encryptionKeyList;
    }

    /** Open a {@link URLConnection} and sets the network connection and read timeout. */
    @NonNull
    public URLConnection setUpURLConnection(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

        final Flags flags = FlagsFactory.getFlags();
        urlConnection.setConnectTimeout(flags.getEncryptionKeyNetworkConnectTimeoutMs());
        urlConnection.setReadTimeout(flags.getEncryptionKeyNetworkReadTimeoutMs());
        return urlConnection;
    }

    /** Construct a HttpDate string for time diff set to last_fetch_time. */
    private String constructHttpDate(long lastFetchTime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        dateFormat.setLenient(false);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date(lastFetchTime));
    }

    private void logEncryptionKeyFetchedStats(
            FetchStatus fetchStatus,
            EnrollmentData enrollmentData,
            @Nullable String encryptionKeyUrl) {
        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(mFetchJobType)
                        .setFetchStatus(fetchStatus)
                        .setIsFirstTimeFetch(mIsFirstTimeFetch)
                        .setAdtechEnrollmentId(enrollmentData.getEnrollmentId())
                        .setEncryptionKeyUrl(encryptionKeyUrl)
                        .build();
        mAdServicesLogger.logEncryptionKeyFetchedStats(stats);
    }
}
