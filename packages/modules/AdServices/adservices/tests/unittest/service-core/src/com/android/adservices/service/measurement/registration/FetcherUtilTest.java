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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Unit tests for {@link FetcherUtil} */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public final class FetcherUtilTest {
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://foo.test");
    private static final Uri REGISTRANT_URI = WebUtil.validUri("https://bar.test");
    private static final String KEY = "key";
    public static final int UNKNOWN_SOURCE_TYPE = 0;
    public static final int UNKNOWN_REGISTRATION_SURFACE_TYPE = 0;
    public static final int APP_REGISTRATION_SURFACE_TYPE = 2;
    public static final int UNKNOWN_STATUS = 0;
    public static final int UNKNOWN_REGISTRATION_FAILURE_TYPE = 0;

    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.WARN)
                    .build();

    @Before
    public void setup() {
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
    }

    @Test
    public void testIsSuccess() {
        assertTrue(FetcherUtil.isSuccess(200));
        assertTrue(FetcherUtil.isSuccess(201));
        assertTrue(FetcherUtil.isSuccess(202));
        assertTrue(FetcherUtil.isSuccess(204));
        assertFalse(FetcherUtil.isSuccess(404));
        assertFalse(FetcherUtil.isSuccess(500));
        assertFalse(FetcherUtil.isSuccess(0));
    }

    @Test
    public void testIsRedirect() {
        assertTrue(FetcherUtil.isRedirect(301));
        assertTrue(FetcherUtil.isRedirect(302));
        assertTrue(FetcherUtil.isRedirect(303));
        assertTrue(FetcherUtil.isRedirect(307));
        assertTrue(FetcherUtil.isRedirect(308));
        assertFalse(FetcherUtil.isRedirect(200));
        assertFalse(FetcherUtil.isRedirect(404));
        assertFalse(FetcherUtil.isRedirect(500));
        assertFalse(FetcherUtil.isRedirect(0));
    }

    @Test
    public void parseRedirects_noRedirectHeaders_returnsEmpty() {
        Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
                FetcherUtil.parseRedirects(Map.of());
        assertEquals(2, redirectMap.size());
        assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LIST).isEmpty());
        assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LOCATION).isEmpty());
    }

    @Test
    public void parseRedirects_bothHeaderTypes() {
        Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
                FetcherUtil.parseRedirects(
                        Map.of(
                                "Attribution-Reporting-Redirect", List.of("foo.test", "bar.test"),
                                "Location", List.of("baz.test")));
        assertEquals(2, redirectMap.size());
        // Verify List Redirects
        List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LIST);
        assertEquals(2, redirects.size());
        assertEquals(Uri.parse("foo.test"), redirects.get(0));
        assertEquals(Uri.parse("bar.test"), redirects.get(1));
        // Verify Location Redirect
        redirects = redirectMap.get(AsyncRegistration.RedirectType.LOCATION);
        assertEquals(1, redirects.size());
        assertEquals(Uri.parse("baz.test"), redirects.get(0));
    }

    @Test
    public void parseRedirects_locationHeaderOnly() {
        Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
                FetcherUtil.parseRedirects(Map.of("Location", List.of("baz.test")));
        assertEquals(2, redirectMap.size());
        List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LOCATION);
        assertEquals(1, redirects.size());
        assertEquals(Uri.parse("baz.test"), redirects.get(0));
        assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LIST).isEmpty());
    }

    @Test
    public void parseRedirects_lsitHeaderOnly() {
        Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
                FetcherUtil.parseRedirects(
                        Map.of("Attribution-Reporting-Redirect", List.of("foo.test", "bar.test")));
        assertEquals(2, redirectMap.size());
        // Verify List Redirects
        List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LIST);
        assertEquals(2, redirects.size());
        assertEquals(Uri.parse("foo.test"), redirects.get(0));
        assertEquals(Uri.parse("bar.test"), redirects.get(1));
        assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LOCATION).isEmpty());
    }

    @Test
    public void extractUnsignedLong_maxValue_success() throws JSONException {
        String unsignedLongString = "18446744073709551615";
        JSONObject obj = new JSONObject().put(KEY, unsignedLongString);
        assertEquals(
                Optional.of(new UnsignedLong(unsignedLongString)),
                FetcherUtil.extractUnsignedLong(obj, KEY));
    }

    @Test
    public void extractUnsignedLong_negative_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "-123");
        assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
    }

    @Test
    public void extractUnsignedLong_notAString_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, 123);
        assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
    }

    @Test
    public void extractUnsignedLong_tooLarge_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "18446744073709551616");
        assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
    }

    @Test
    public void extractUnsignedLong_notAnInt_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "123p");
        assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
    }

    @Test
    public void is64BitInteger_various() {
        assertTrue(FetcherUtil.is64BitInteger(Integer.valueOf(64)));
        assertTrue(FetcherUtil.is64BitInteger(Integer.valueOf(-664)));
        assertTrue(FetcherUtil.is64BitInteger(Integer.MAX_VALUE));
        assertTrue(FetcherUtil.is64BitInteger(Long.valueOf(-140737488355328L)));
        assertTrue(FetcherUtil.is64BitInteger(Long.MAX_VALUE));
        assertFalse(FetcherUtil.is64BitInteger(Double.valueOf(45.33D)));
        assertFalse(FetcherUtil.is64BitInteger(Float.valueOf(-456.335F)));
        assertFalse(FetcherUtil.is64BitInteger("4567"));
        assertFalse(FetcherUtil.is64BitInteger(new JSONObject()));
    }

    @Test
    public void extractLongString_maxValue_success() throws JSONException {
        String longString = "9223372036854775807";
        JSONObject obj = new JSONObject().put(KEY, longString);
        assertEquals(
                Optional.of(Long.parseLong(longString)),
                FetcherUtil.extractLongString(obj, KEY));
    }

    @Test
    public void extractLongString_negative_success() throws JSONException {
        String longString = "-935";
        JSONObject obj = new JSONObject().put(KEY, longString);
        assertEquals(
                Optional.of(Long.parseLong(longString)),
                FetcherUtil.extractLongString(obj, KEY));
    }

    @Test
    public void extractLongString_notAString_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, 123);
        assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
    }

    @Test
    public void extractLongString_tooLarge_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "9223372036854775808");
        assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
    }

    @Test
    public void extractLongString_notAnInt_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "123p");
        assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
    }

    @Test
    public void extractLong_numericMaxValue_success() throws JSONException {
        long longMax = 9223372036854775807L;
        JSONObject obj = new JSONObject().put(KEY, longMax);
        assertEquals(
                Optional.of(Long.valueOf(longMax)),
                FetcherUtil.extractLong(obj, KEY));
    }

    @Test
    public void extractLong_numericNegative_success() throws JSONException {
        long longNegative = -935L;
        JSONObject obj = new JSONObject().put(KEY, longNegative);
        assertEquals(
                Optional.of(Long.valueOf(longNegative)),
                FetcherUtil.extractLong(obj, KEY));
    }

    @Test
    public void extractLong_numericNotANumber_returnsEmpty() throws JSONException {
        JSONObject obj = new JSONObject().put(KEY, "123");
        assertEquals(Optional.empty(), FetcherUtil.extractLong(obj, KEY));
    }

    @Test
    public void testIsValidAggregateKeyId_valid() {
        assertTrue(FetcherUtil.isValidAggregateKeyId("abcd"));
    }

    @Test
    public void testIsValidAggregateKeyId_null_returnsFalse() {
        assertFalse(FetcherUtil.isValidAggregateKeyId(null));
    }

    @Test
    public void testIsValidAggregateKeyId_empty_returnsFalse() {
        assertFalse(FetcherUtil.isValidAggregateKeyId(""));
    }

    @Test
    public void testIsValidAggregateKeyId_tooLong() {
        StringBuilder keyId = new StringBuilder("");
        for (int i = 0;
                i < Flags.DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID + 1;
                i++) {
            keyId.append("a");
        }
        assertFalse(FetcherUtil.isValidAggregateKeyId(keyId.toString()));
    }

    @Test
    public void testIsValidAggregateKeyPiece_valid() {
        assertTrue(FetcherUtil.isValidAggregateKeyPiece("0x15A", mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_validWithUpperCasePrefix() {
        assertTrue(FetcherUtil.isValidAggregateKeyPiece("0X15A", mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_null() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece(null, mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_emptyString() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece("", mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_missingPrefix() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece("1234", mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_tooShort() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece("0x", mFlags));
    }

    @Test
    public void testIsValidAggregateKeyPiece_tooLong() {
        StringBuilder keyPiece = new StringBuilder("0x");
        for (int i = 0; i < 33; i++) {
            keyPiece.append("1");
        }
        assertFalse(FetcherUtil.isValidAggregateKeyPiece(keyPiece.toString(), mFlags));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_valid() throws JSONException {
        String json = "[{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}]";
        JSONArray filters = new JSONArray(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_removeSizeConstraints_valid() throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_valid() throws JSONException {
        String json = "{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void areValidAttributionFilters_lookbackWindowAllowedAndValid_returnsTrue()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void areValidAttributionFilters_validlLookbackWindowA_removeSizeConstrains_returnsTrue()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void areValidAttributionFilters_lookbackWindowAllowedButInvalid_returnsFalse()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": abcd"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void
            areValidAttributionFilters_invalidLookbackWindow_removeSizeConstraints_returnsFalse()
                    throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": abcd"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void areValidAttributionFilters_lookbackWindowDisllowed_returnsFalse()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void
            areValidAttributionFilters_lookbackWindowDisllowed_removeSizeConstraints_returnsFalse()
                    throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void areValidAttributionFilters_zeroLookbackWindow_returnsFalse() throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 0"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void areValidAttributionFilters_zeroLookbackWindow_removeSizeConstraints_returnsFalse()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": 0"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void areValidAttributionFilters_negativeLookbackWindow_returnsFalse()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": -123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void
            areValidAttributionFilters_negativeLookbackWindow_removeSizeConstraints_returnsFalse()
                    throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
                        + "\"_lookback_window\": -123"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_null() throws JSONException {
        JSONObject nullFilterMap = null;
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        nullFilterMap,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMapNull_removeSizeConstraints_returnFalse()
            throws JSONException {
        JSONObject nullFilterMap = null;
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        nullFilterMap,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_tooManyFilters() throws JSONException {
        StringBuilder json = new StringBuilder("[{");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        json.append("}]");
        JSONArray filters = new JSONArray(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_tooManyFilters_removeSizeConstraints()
            throws JSONException {
        StringBuilder json = new StringBuilder("[{");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        json.append("}]");
        JSONArray filters = new JSONArray(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_tooManyFilters() throws JSONException {
        StringBuilder json = new StringBuilder("{");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        json.append("}");
        JSONObject filters = new JSONObject(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_tooManyFilters_removeSizeConstraints()
            throws JSONException {
        StringBuilder json = new StringBuilder("{");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        json.append("}");
        JSONObject filters = new JSONObject(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_keyTooLong() throws JSONException {
        String json = "[{"
                + "\"" + LONG_FILTER_STRING + "\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}]";
        JSONArray filters = new JSONArray(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_longKeyWithoutSizeConstraints_returnsTrue()
            throws JSONException {
        String json =
                "[{"
                        + "\""
                        + LONG_FILTER_STRING
                        + "\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_keyTooLong() throws JSONException {
        String json = "{"
                + "\"" + LONG_FILTER_STRING + "\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_longKeyWithoutSizeConstraints_returnsTrue()
            throws JSONException {
        String json =
                "{"
                        + "\""
                        + LONG_FILTER_STRING
                        + "\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                        + "}";
        JSONObject filters = new JSONObject(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_tooManyFilterMaps() throws JSONException {
        StringBuilder json = new StringBuilder("[");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1)
                        .mapToObj(
                                i ->
                                        "{\"filter-string-1\": [\"filter-value-1\"],"
                                                + "\"filter-string-2\": [\"filter-value-"
                                                + i
                                                + "\"]}")
                        .collect(Collectors.joining(",")));
        json.append("]");
        JSONArray filters = new JSONArray(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_removeFilterMapNumberConstraints()
            throws JSONException {
        StringBuilder json = new StringBuilder("[");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1)
                        .mapToObj(
                                i ->
                                        "{\"filter-string-1\": [\"filter-value-1\"],"
                                                + "\"filter-string-2\": [\"filter-value-"
                                                + i
                                                + "\"]}")
                        .collect(Collectors.joining(",")));
        json.append("]");
        JSONArray filters = new JSONArray(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_tooManyValues() throws JSONException {
        StringBuilder json = new StringBuilder("[{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        json.append("]}]");
        JSONArray filters = new JSONArray(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_removeMaxValueLimit()
            throws JSONException {
        StringBuilder json =
                new StringBuilder(
                        "[{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        json.append("]}]");
        JSONArray filters = new JSONArray(json.toString());
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_tooManyValues() throws JSONException {
        StringBuilder json = new StringBuilder("{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        json.append("]}");
        JSONObject filters = new JSONObject(json.toString());
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_removeMaxValueLimit()
            throws JSONException {
        StringBuilder json =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        json.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        json.append("]}");
        JSONObject filters = new JSONObject(json.toString());
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_valueTooLong() throws JSONException {
        String json = "[{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"" + LONG_FILTER_STRING + "\"]"
                + "}]";
        JSONArray filters = new JSONArray(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSet_removeValueLengthLimit()
            throws JSONException {
        String json =
                "[{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \""
                        + LONG_FILTER_STRING
                        + "\"]"
                        + "}]";
        JSONArray filters = new JSONArray(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_valueTooLong() throws JSONException {
        String json = "{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"" + LONG_FILTER_STRING + "\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterMap_removeValueLengthLimit()
            throws JSONException {
        String json =
                "{"
                        + "\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-2\", \""
                        + LONG_FILTER_STRING
                        + "\"]"
                        + "}";
        JSONObject filters = new JSONObject(json);
        assertTrue(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ false));
    }

    @Test
    public void testAreValidAttributionFilters_filterSetIncludesNullValue_returnsFalse()
            throws JSONException {
        String json = "[{"
                + "\"filter-string-1\": [\"filter-value-1\", null],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}]";
        JSONArray filters = new JSONArray(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void testAreValidAttributionFilters_filterSetIncludesNumericValue_returnsFalse()
            throws JSONException {
        String json = "[{"
                + "\"filter-string-1\": [\"filter-value-1\", 37],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}]";
        JSONArray filters = new JSONArray(json);
        assertFalse(
                FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true));
    }

    @Test
    public void emitHeaderMetrics_headersSizeLessThanMaxAllowed_doesNotLogAdTechDomain() {
        // Setup
        int registrationType = 1;
        long maxAllowedHeadersSize = 30;
        Map<String, List<String>> headersMap = createHeadersMap();
        int headersMapSize = 28;

        // Execution
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setRegistrant(REGISTRANT_URI)
                        .build();

        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

        FetcherUtil.emitHeaderMetrics(
                maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus);

        // Verify
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                registrationType,
                                                headersMapSize,
                                                UNKNOWN_SOURCE_TYPE,
                                                APP_REGISTRATION_SURFACE_TYPE,
                                                UNKNOWN_STATUS,
                                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                                0,
                                                REGISTRANT_URI.toString(),
                                                0,
                                                false)
                                        .setAdTechDomain(null)
                                        .build()));
    }

    @Test
    public void emitHeaderMetrics_headersSizeExceedsMaxAllowed_logsAdTechDomain() {
        // Setup
        int registrationType = 1;
        long maxAllowedHeadersSize = 25;
        Map<String, List<String>> headersMap = createHeadersMap();
        int headersMapSize = 28;

        // Execution
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setRegistrant(REGISTRANT_URI)
                        .build();

        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

        FetcherUtil.emitHeaderMetrics(
                maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus);

        // Verify
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                registrationType,
                                                headersMapSize,
                                                UNKNOWN_SOURCE_TYPE,
                                                APP_REGISTRATION_SURFACE_TYPE,
                                                UNKNOWN_STATUS,
                                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                                0,
                                                REGISTRANT_URI.toString(),
                                                0,
                                                false)
                                        .setAdTechDomain(REGISTRATION_URI.toString())
                                        .build()));
    }

    @Test
    public void emitHeaderMetrics_headersWithNullValues_success() {
        // Setup
        int registrationType = 1;
        long maxAllowedHeadersSize = 25;

        Map<String, List<String>> headersMap = new HashMap<>();
        headersMap.put("key1", Arrays.asList("val11", "val12"));
        headersMap.put("key2", null);
        int headersMapSize = 18;

        // Execution
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setRegistrant(REGISTRANT_URI)
                        .build();

        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

        FetcherUtil.emitHeaderMetrics(
                maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus);

        // Verify
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                registrationType,
                                                headersMapSize,
                                                UNKNOWN_SOURCE_TYPE,
                                                APP_REGISTRATION_SURFACE_TYPE,
                                                UNKNOWN_STATUS,
                                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                                0,
                                                REGISTRANT_URI.toString(),
                                                0,
                                                false)
                                        .setAdTechDomain(null)
                                        .build()));
    }

    @Test
    public void isValidAggregateDeduplicationKey_negativeValue() {
        assertFalse(FetcherUtil.isValidAggregateDeduplicationKey("-1"));
    }

    @Test
    public void isValidAggregateDeduplicationKey_nonNumericalValue() {
        assertFalse(FetcherUtil.isValidAggregateDeduplicationKey("w"));
    }

    @Test
    public void isValidAggregateDeduplicationKey_success() {
        assertTrue(FetcherUtil.isValidAggregateDeduplicationKey("18446744073709551615"));
        assertTrue(FetcherUtil.isValidAggregateDeduplicationKey("0"));
    }

    @Test
    public void isValidAggregateDeduplicationKey_nullValue_returnsFalse() {
        assertFalse(FetcherUtil.isValidAggregateDeduplicationKey(null));
    }

    @Test
    public void isValidAggregateDeduplicationKey_empty_returnsFalse() {
        assertFalse(FetcherUtil.isValidAggregateDeduplicationKey(""));
    }

    private Map<String, List<String>> createHeadersMap() {
        return new ImmutableMap.Builder<String, List<String>>()
                .put("key1", Arrays.asList("val11", "val12"))
                .put("key2", Arrays.asList("val21", "val22"))
                .build();
    }
}
