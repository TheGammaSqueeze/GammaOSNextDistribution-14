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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.WebUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

/**
 * Unit tests for {@link AggregateEncryptionKeyFetcher}
 */
@SmallTest
public final class AggregateEncryptionKeyFetcherTest {
    @Spy
    AggregateEncryptionKeyFetcher mFetcher =
            new AggregateEncryptionKeyFetcher(ApplicationProvider.getApplicationContext());

    @Mock HttpsURLConnection mUrlConnection;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).mockStatic(ErrorLogUtil.class).build();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicAggregateEncryptionKeyRequest() throws Exception {
        AggregateEncryptionKeyTestUtil.prepareMockAggregateEncryptionKeyFetcher(
                mFetcher, mUrlConnection, AggregateEncryptionKeyTestUtil.getDefaultResponseBody());
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();
        assertEquals(2, result.size());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID,
                result.get(0).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY,
                result.get(0).getPublicKey());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY,
                result.get(0).getExpiry());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID,
                result.get(1).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY,
                result.get(1).getPublicKey());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY,
                result.get(1).getExpiry());
        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testBadSourceUrl() throws Exception {
        Uri badTarget = WebUtil.validUri("bad-schema://foo.test");
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        badTarget,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testMalformedUrl() throws Exception {
        Uri invalidPort = WebUtil.validUri("https://foo.test:-1");
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        invalidPort,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(MalformedURLException.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)),
                times(1));
    }

    @Test
    public void testBadConnection() throws Exception {
        doThrow(new IOException("Bad internet things")).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(IOException.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)),
                times(1));
    }

    @Test
    public void testServerTimeout() throws Exception {
        doReturn(mUrlConnection)
                .when(mFetcher)
                .openUrl(new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of());
        doThrow(new IOException("timeout")).when(mUrlConnection).getResponseCode();

        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(IOException.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)),
                times(1));
    }

    @Test
    public void testInvalidResponseBodyJson() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                ("{" + AggregateEncryptionKeyTestUtil.getDefaultResponseBody()).getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE)));
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(JSONException.class),
                                eq(
                                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)),
                times(1));
    }

    @Test
    public void testMissingCacheControlHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(new HashMap());
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testMissingAgeHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        long expectedExpiry = AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY
                + Long.parseLong(AggregateEncryptionKeyTestUtil.DEFAULT_CACHED_AGE) * 1000;
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE)));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();
        assertEquals(2, result.size());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID,
                result.get(0).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY,
                result.get(0).getPublicKey());
        assertEquals(expectedExpiry, result.get(0).getExpiry());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID,
                result.get(1).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY,
                result.get(1).getPublicKey());
        assertEquals(expectedExpiry, result.get(1).getExpiry());
        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testBrokenAgeHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        long expectedExpiry = AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY
                + Long.parseLong(AggregateEncryptionKeyTestUtil.DEFAULT_CACHED_AGE) * 1000;
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE),
                "age", List.of("not an int")));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();
        assertEquals(2, result.size());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID,
                result.get(0).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY,
                result.get(0).getPublicKey());
        assertEquals(expectedExpiry, result.get(0).getExpiry());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID,
                result.get(1).getKeyId());
        assertEquals(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY,
                result.get(1).getPublicKey());
        assertEquals(expectedExpiry, result.get(1).getExpiry());
        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testCachedAgeGreaterThanMaxAge() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE),
                "age", List.of("604801")));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testNotOverHttps() throws Exception {
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        WebUtil.validUri("http://foo.test"),
                        WebUtil.validUri("http://foo.test"),
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }
}
