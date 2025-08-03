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
package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Common handling for Response Based Registration
 *
 * @hide
 */
public class FetcherUtil {
    static final Pattern HEX_PATTERN = Pattern.compile("\\p{XDigit}+");

    /**
     * Determine all redirects.
     *
     * <p>Generates a map of: (redirectType, List&lt;Uri&gt;)
     */
    static Map<AsyncRegistration.RedirectType, List<Uri>> parseRedirects(
            @NonNull Map<String, List<String>> headers) {
        Map<AsyncRegistration.RedirectType, List<Uri>> uriMap = new HashMap<>();
        uriMap.put(AsyncRegistration.RedirectType.LOCATION, parseLocationRedirects(headers));
        uriMap.put(AsyncRegistration.RedirectType.LIST, parseListRedirects(headers));
        return uriMap;
    }

    /**
     * Check HTTP response codes that indicate a redirect.
     */
    static boolean isRedirect(int responseCode) {
        return (responseCode / 100) == 3;
    }

    /**
     * Check HTTP response code for success.
     */
    static boolean isSuccess(int responseCode) {
        return (responseCode / 100) == 2;
    }

    /** Validates both string type and unsigned long parsing */
    public static Optional<UnsignedLong> extractUnsignedLong(JSONObject obj, String key) {
        try {
            Object maybeValue = obj.get(key);
            if (!(maybeValue instanceof String)) {
                return Optional.empty();
            }
            return Optional.of(new UnsignedLong((String) maybeValue));
        } catch (JSONException | NumberFormatException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "extractUnsignedLong: caught exception. Key: %s", key);
            return Optional.empty();
        }
    }

    /** Validates both string type and long parsing */
    public static Optional<Long> extractLongString(JSONObject obj, String key) {
        try {
            Object maybeValue = obj.get(key);
            if (!(maybeValue instanceof String)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong((String) maybeValue));
        } catch (JSONException | NumberFormatException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "extractLongString: caught exception. Key: %s", key);
            return Optional.empty();
        }
    }

    /** Validates an integral number */
    public static boolean is64BitInteger(Object obj) {
        return (obj instanceof Integer) || (obj instanceof Long);
    }

    /** Validates both number type and long parsing */
    public static Optional<Long> extractLong(JSONObject obj, String key) {
        try {
            Object maybeValue = obj.get(key);
            if (!is64BitInteger(maybeValue)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(String.valueOf(maybeValue)));
        } catch (JSONException | NumberFormatException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "extractLong: caught exception. Key: %s", key);
            return Optional.empty();
        }
    }

    private static Optional<Long> extractLookbackWindow(JSONObject obj) {
        try {
            long lookbackWindow = Long.parseLong(obj.optString(FilterMap.LOOKBACK_WINDOW));
            if (lookbackWindow <= 0) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "extractLookbackWindow: non positive lookback window found: %d",
                                lookbackWindow);
                return Optional.empty();
            }
            return Optional.of(lookbackWindow);
        } catch (NumberFormatException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            e,
                            "extractLookbackWindow: caught exception. Key: %s",
                            FilterMap.LOOKBACK_WINDOW);
            return Optional.empty();
        }
    }

    /**
     * Validate aggregate key ID.
     */
    static boolean isValidAggregateKeyId(String id) {
        return id != null
                && !id.isEmpty()
                && id.getBytes().length
                        <= FlagsFactory.getFlags()
                                .getMeasurementMaxBytesPerAttributionAggregateKeyId();
    }

    /** Validate aggregate deduplication key. */
    static boolean isValidAggregateDeduplicationKey(String deduplicationKey) {
        if (deduplicationKey == null || deduplicationKey.isEmpty()) {
            return false;
        }
        try {
            Long.parseUnsignedLong(deduplicationKey);
        } catch (NumberFormatException exception) {
            return false;
        }
        return true;
    }

    /**
     * Validate aggregate key-piece.
     */
    static boolean isValidAggregateKeyPiece(String keyPiece, Flags flags) {
        if (keyPiece == null || keyPiece.isEmpty()) {
            return false;
        }
        int length = keyPiece.getBytes().length;
        if (flags.getMeasurementEnableAraParsingAlignmentV1()) {
            if (!(keyPiece.startsWith("0x") || keyPiece.startsWith("0X"))) {
                return false;
            }
            // Key-piece is restricted to a maximum of 128 bits and the hex strings therefore have
            // at most 32 digits.
            if (length < 3 || length > 34) {
                return false;
            }
            if (!HEX_PATTERN.matcher(keyPiece.substring(2)).matches()) {
                return false;
            }
            return true;
        } else {
            // Key-piece is restricted to a maximum of 128 bits and the hex strings therefore have
            // at most 32 digits.
            return (keyPiece.startsWith("0x") || keyPiece.startsWith("0X"))
                    && 2 < length && length < 35;
        }
    }

    /** Validate attribution filters JSONArray. */
    static boolean areValidAttributionFilters(
            @NonNull JSONArray filterSet,
            Flags flags,
            boolean canIncludeLookbackWindow,
            boolean shouldCheckFilterSize) throws JSONException {
        if (filterSet.length()
                > FlagsFactory.getFlags().getMeasurementMaxFilterMapsPerFilterSet()) {
            return false;
        }
        for (int i = 0; i < filterSet.length(); i++) {
            if (!areValidAttributionFilters(
                    filterSet.optJSONObject(i),
                    flags,
                    canIncludeLookbackWindow,
                    shouldCheckFilterSize)) {
                return false;
            }
        }
        return true;
    }

    /** Validate attribution filters JSONObject. */
    static boolean areValidAttributionFilters(
            JSONObject filtersObj,
            Flags flags,
            boolean canIncludeLookbackWindow,
            boolean shouldCheckFilterSize) throws JSONException {
        if (filtersObj == null
                || filtersObj.length()
                        > FlagsFactory.getFlags().getMeasurementMaxAttributionFilters()) {
            return false;
        }
        Iterator<String> keys = filtersObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (shouldCheckFilterSize
                    && key.getBytes().length
                            > FlagsFactory.getFlags()
                                    .getMeasurementMaxBytesPerAttributionFilterString()) {
                return false;
            }
            // Process known reserved keys that start with underscore first, then invalidate on
            // catch-all.
            if (flags.getMeasurementEnableLookbackWindowFilter()
                    && FilterMap.LOOKBACK_WINDOW.equals(key)) {
                if (!canIncludeLookbackWindow || extractLookbackWindow(filtersObj).isEmpty()) {
                    return false;
                }
                continue;
            }
            // Invalidate catch-all reserved prefix.
            if (key.startsWith(FilterMap.RESERVED_PREFIX)) {
                return false;
            }
            JSONArray values = filtersObj.optJSONArray(key);
            if (values == null) {
                return false;
            }
            if (shouldCheckFilterSize
                    && values.length()
                            > FlagsFactory.getFlags()
                                    .getMeasurementMaxValuesPerAttributionFilter()) {
                return false;
            }
            for (int i = 0; i < values.length(); i++) {
                Object value = values.get(i);
                if (!(value instanceof String)) {
                    return false;
                }
                if (shouldCheckFilterSize
                        && ((String) value).getBytes().length
                                > FlagsFactory.getFlags()
                                        .getMeasurementMaxBytesPerAttributionFilterString()) {
                    return false;
                }
            }
        }
        return true;
    }

    static String getSourceRegistrantToLog(AsyncRegistration asyncRegistration) {
        if (asyncRegistration.isSourceRequest()) {
            return asyncRegistration.getRegistrant().toString();
        }

        return "";
    }

    static void emitHeaderMetrics(
            long headerSizeLimitBytes,
            AdServicesLogger logger,
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus) {
        long headerSize = asyncFetchStatus.getResponseSize();
        String adTechDomain = null;

        if (headerSize > headerSizeLimitBytes) {
            adTechDomain =
                    WebAddresses.topPrivateDomainAndScheme(asyncRegistration.getRegistrationUri())
                            .map(Uri::toString)
                            .orElse(null);
        }

        logger.logMeasurementRegistrationsResponseSize(
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                getRegistrationType(asyncRegistration),
                                headerSize,
                                getSourceType(asyncRegistration),
                                getSurfaceType(asyncRegistration),
                                getStatus(asyncFetchStatus),
                                getFailureType(asyncFetchStatus),
                                asyncFetchStatus.getRegistrationDelay(),
                                getSourceRegistrantToLog(asyncRegistration),
                                asyncFetchStatus.getRetryCount(),
                                asyncFetchStatus.isRedirectOnly())
                        .setAdTechDomain(adTechDomain)
                        .build());
    }

    private static List<Uri> parseListRedirects(Map<String, List<String>> headers) {
        List<Uri> redirects = new ArrayList<>();
        List<String> field = headers.get(AsyncRedirects.REDIRECT_LIST_HEADER_KEY);
        int maxRedirects = FlagsFactory.getFlags().getMeasurementMaxRegistrationRedirects();
        if (field != null) {
            for (int i = 0; i < Math.min(field.size(), maxRedirects); i++) {
                redirects.add(Uri.parse(field.get(i)));
            }
        }
        return redirects;
    }

    private static List<Uri> parseLocationRedirects(Map<String, List<String>> headers) {
        List<Uri> redirects = new ArrayList<>();
        List<String> field = headers.get(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY);
        if (field != null && !field.isEmpty()) {
            redirects.add(Uri.parse(field.get(0)));
            if (field.size() > 1) {
                LoggerFactory.getMeasurementLogger()
                        .d("Expected one Location redirect only, others ignored!");
            }
        }
        return redirects;
    }

    public static long calculateHeadersCharactersLength(Map<String, List<String>> headers) {
        long size = 0;
        for (String headerKey : headers.keySet()) {
            if (headerKey != null) {
                size = size + headerKey.length();
                List<String> headerValues = headers.get(headerKey);
                if (headerValues != null) {
                    for (String headerValue : headerValues) {
                        size = size + headerValue.length();
                    }
                }
            }
        }

        return size;
    }

    private static int getRegistrationType(AsyncRegistration asyncRegistration) {
        if (asyncRegistration.isSourceRequest()) {
            return RegistrationEnumsValues.TYPE_SOURCE;
        } else if (asyncRegistration.isTriggerRequest()) {
            return RegistrationEnumsValues.TYPE_TRIGGER;
        } else {
            return RegistrationEnumsValues.TYPE_UNKNOWN;
        }
    }

    private static int getSourceType(AsyncRegistration asyncRegistration) {
        if (asyncRegistration.getSourceType() == Source.SourceType.EVENT) {
            return RegistrationEnumsValues.SOURCE_TYPE_EVENT;
        } else if (asyncRegistration.getSourceType() == Source.SourceType.NAVIGATION) {
            return RegistrationEnumsValues.SOURCE_TYPE_NAVIGATION;
        } else {
            return RegistrationEnumsValues.SOURCE_TYPE_UNKNOWN;
        }
    }

    private static int getSurfaceType(AsyncRegistration asyncRegistration) {
        if (asyncRegistration.isAppRequest()) {
            return RegistrationEnumsValues.SURFACE_TYPE_APP;
        } else if (asyncRegistration.isWebRequest()) {
            return RegistrationEnumsValues.SURFACE_TYPE_WEB;
        } else {
            return RegistrationEnumsValues.SURFACE_TYPE_UNKNOWN;
        }
    }

    private static int getStatus(AsyncFetchStatus asyncFetchStatus) {
        if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.SUCCESS
                || (asyncFetchStatus.getResponseStatus() == AsyncFetchStatus.ResponseStatus.SUCCESS
                        && (asyncFetchStatus.getEntityStatus()
                                        == AsyncFetchStatus.EntityStatus.UNKNOWN
                                || asyncFetchStatus.getEntityStatus()
                                        == AsyncFetchStatus.EntityStatus.HEADER_MISSING))) {
            // successful source/trigger fetching/parsing and successful redirects (with no header)
            return RegistrationEnumsValues.STATUS_SUCCESS;
        } else if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.UNKNOWN
                && asyncFetchStatus.getResponseStatus()
                        == AsyncFetchStatus.ResponseStatus.UNKNOWN) {
            return RegistrationEnumsValues.STATUS_UNKNOWN;
        } else {
            return RegistrationEnumsValues.STATUS_FAILURE;
        }
    }

    private static int getFailureType(AsyncFetchStatus asyncFetchStatus) {
        if (asyncFetchStatus.getResponseStatus()
                        == AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE
                || asyncFetchStatus.getResponseStatus()
                        == AsyncFetchStatus.ResponseStatus.NETWORK_ERROR
                || asyncFetchStatus.getResponseStatus()
                        == AsyncFetchStatus.ResponseStatus.INVALID_URL) {
            return RegistrationEnumsValues.FAILURE_TYPE_NETWORK;
        } else if (asyncFetchStatus.getResponseStatus()
                == AsyncFetchStatus.ResponseStatus.HEADER_SIZE_LIMIT_EXCEEDED) {
            return RegistrationEnumsValues.FAILURE_TYPE_HEADER_SIZE_LIMIT_EXCEEDED;
        } else if (asyncFetchStatus.getEntityStatus()
                == AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT) {
            return RegistrationEnumsValues.FAILURE_TYPE_ENROLLMENT;
        } else if (asyncFetchStatus.getEntityStatus()
                        == AsyncFetchStatus.EntityStatus.VALIDATION_ERROR
                || asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.PARSING_ERROR
                || asyncFetchStatus.getEntityStatus()
                        == AsyncFetchStatus.EntityStatus.HEADER_ERROR) {
            return RegistrationEnumsValues.FAILURE_TYPE_PARSING;
        } else if (asyncFetchStatus.getEntityStatus()
                == AsyncFetchStatus.EntityStatus.STORAGE_ERROR) {
            return RegistrationEnumsValues.FAILURE_TYPE_STORAGE;
        } else if (asyncFetchStatus.isRedirectError()) {
            return RegistrationEnumsValues.FAILURE_TYPE_REDIRECT;
        } else {
            return RegistrationEnumsValues.FAILURE_TYPE_UNKNOWN;
        }
    }

    /** AdservicesMeasurementRegistrations atom enum values. */
    public interface RegistrationEnumsValues {
        int TYPE_UNKNOWN = 0;
        int TYPE_SOURCE = 1;
        int TYPE_TRIGGER = 2;
        int SOURCE_TYPE_UNKNOWN = 0;
        int SOURCE_TYPE_EVENT = 1;
        int SOURCE_TYPE_NAVIGATION = 2;
        int SURFACE_TYPE_UNKNOWN = 0;
        int SURFACE_TYPE_WEB = 1;
        int SURFACE_TYPE_APP = 2;
        int STATUS_UNKNOWN = 0;
        int STATUS_SUCCESS = 1;
        int STATUS_FAILURE = 2;
        int FAILURE_TYPE_UNKNOWN = 0;
        int FAILURE_TYPE_PARSING = 1;
        int FAILURE_TYPE_NETWORK = 2;
        int FAILURE_TYPE_ENROLLMENT = 3;
        int FAILURE_TYPE_REDIRECT = 4;
        int FAILURE_TYPE_STORAGE = 5;
        int FAILURE_TYPE_HEADER_SIZE_LIMIT_EXCEEDED = 7;
    }
}
