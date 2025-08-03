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

package com.android.adservices.service.common.httpclient;

import static android.adservices.exceptions.RetryableAdServicesNetworkException.DEFAULT_RETRY_AFTER_VALUE;

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.EMPTY_BODY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.adservices.exceptions.AdServicesNetworkException;
import android.adservices.exceptions.RetryableAdServicesNetworkException;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

@SmallTest
public class AdServicesHttpsClientTest {
    @Spy private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String CACHE_HEADER = "Cache-Control: max-age=60";
    private static final String NO_CACHE_HEADER = "Cache-Control: no-cache";
    private static final String RESPONSE_HEADER_KEY = "fake_response_header_key";
    private static final String RESPONSE_HEADER_VALUE_1 = "fake_response_header_value_1";
    private static final String RESPONSE_HEADER_VALUE_2 = "fake_response_header_value_2";
    private static final String REQUEST_PROPERTY_KEY = "X_REQUEST_KEY";
    private static final String REQUEST_PROPERTY_VALUE = "Fake_Value";
    private static final long MAX_AGE_SECONDS = 120;
    private static final long MAX_ENTRIES = 20;
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();
    private static final DevContext DEV_CONTEXT_ENABLED =
            DevContext.builder().setDevOptionsEnabled(true).build();

    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mReportingPath = "/reporting/";
    private final String mFetchPayloadPath = "/fetchPayload/";
    private final String mFakeUrl = "https://fakeprivacysandboxdomain.never/this/is/a/fake";
    private final int mTimeoutDeltaMs = 1000;
    private final int mBytesPerPeriod = 1;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private AdServicesHttpsClient mClient;
    @Mock private AdServicesHttpsClient.UriConverter mUriConverterMock;
    @Mock private URL mUrlMock;
    @Mock private HttpsURLConnection mURLConnectionMock;
    @Mock private InputStream mInputStreamMock;
    private HttpCache mCache;
    private CacheEntryDao mCacheEntryDao;
    private String mData;

    @Before
    public void setup() throws Exception {
        mCacheEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        mCache = new FledgeHttpCache(mCacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mClient = new AdServicesHttpsClient(mExecutorService, mCache);
        mData = new JSONObject().put("key", "value").toString();
    }

    @Test
    public void testGetAndReadNothingSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED)).isNull();
    }

    @Test
    public void testGetAndReadNothingSuccessfulResponse_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED)).isNull();
    }

    @Test
    public void testGetAndReadNothingCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testGetAndReadNothingFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testGetAndReadNothingDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> getAndReadNothing(Uri.parse(mFakeUrl), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testGetAndReadNothingThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                getAndReadNothing(
                                        Uri.parse("http://google.com"), DEV_CONTEXT_DISABLED));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFetchPayloadSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        assertEquals(mJsScript, result.getResponseBody());
    }

    @Test
    public void testFetchPayloadSuccessfulResponse_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED);
        assertEquals(mJsScript, result.getResponseBody());
    }

    @Test
    public void testFetchPayloadCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);
        fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testFetchPayloadFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testFetchPayloadDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(mFakeUrl), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testThrowsIOExceptionWhenConnectionTimesOut() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                new MockResponse()
                                        .setBody(mJsScript)
                                        .throttleBody(
                                                mBytesPerPeriod,
                                                mClient.getConnectTimeoutMs()
                                                        + mClient.getReadTimeoutMs()
                                                        + mTimeoutDeltaMs,
                                                TimeUnit.MILLISECONDS)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testFetchPayloadThrowsExceptionIfUsingPlainTextHttp() {
        Exception wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse("http://google.com"), DEV_CONTEXT_DISABLED));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testInputStreamToStringThrowsExceptionWhenExceedingMaxSize() throws Exception {
        // Creating a client with a max byte size of 5;
        int defaultTimeoutMs = 5000;
        mClient =
                new AdServicesHttpsClient(mExecutorService, defaultTimeoutMs, defaultTimeoutMs, 5);

        // Setting a response of size 6
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody("123456")));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testHttpsClientFreesResourcesWhenCancelled() throws Exception {
        // Creating a client with large default limits
        int defaultTimeoutMs = 5000;
        int defaultMaxSizeBytes = 5000;
        int delayMs = 4000;
        long waitForEventualCompletionMs = delayMs * 4L;
        mClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        defaultTimeoutMs,
                        defaultTimeoutMs,
                        defaultMaxSizeBytes,
                        mUriConverterMock,
                        mCache);

        doReturn(mUrlMock).when(mUriConverterMock).toUrl(any(Uri.class));
        doReturn(mURLConnectionMock).when(mUrlMock).openConnection();
        doReturn(mInputStreamMock).when(mURLConnectionMock).getInputStream();
        doAnswer(new AnswersWithDelay(delayMs, new Returns(202)))
                .when(mURLConnectionMock)
                .getResponseCode();

        ListenableFuture<AdServicesHttpClientResponse> futureResponse =
                mClient.fetchPayload(Uri.parse((mFakeUrl)), DEV_CONTEXT_DISABLED);

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // We cancel the future while the request is going on
        assertTrue(
                "The request should have been ongoing, until being force-cancelled now",
                futureResponse.cancel(true));
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
    }

    @Test
    public void testHttpsClientFreesResourcesInNormalFlow() throws Exception {
        // Creating a client with large default limits
        int defaultTimeoutMs = 5000;
        int defaultMaxSizeBytes = 5000;
        int delayMs = 2000;
        long waitForEventualCompletionMs = delayMs * 4L;
        mClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        defaultTimeoutMs,
                        defaultTimeoutMs,
                        defaultMaxSizeBytes,
                        mUriConverterMock,
                        mCache);

        doReturn(mUrlMock).when(mUriConverterMock).toUrl(any(Uri.class));
        doReturn(mURLConnectionMock).when(mUrlMock).openConnection();
        doReturn(mInputStreamMock).when(mURLConnectionMock).getInputStream();
        doReturn(202).when(mURLConnectionMock).getResponseCode();

        ListenableFuture<AdServicesHttpClientResponse> futureResponse =
                mClient.fetchPayload(Uri.parse((mFakeUrl)), DEV_CONTEXT_DISABLED);

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
        assertTrue(
                "The future response for fetchPayload should have been completed",
                futureResponse.isDone());
    }

    @Test
    public void testFetchPayloadResponsesSkipsHeaderIfAbsent() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse().setBody(mJsScript);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        AdServicesHttpClientResponse response =
                mClient.fetchPayload(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(false)
                                        .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build())
                        .get();
        assertEquals(mJsScript, response.getResponseBody());
        assertEquals(
                "No header should have been returned", 0, response.getResponseHeaders().size());
    }

    @Test
    public void testFetchPayloadContainsRequestProperties() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                assertEquals(
                                        "Request header mismatch",
                                        REQUEST_PROPERTY_VALUE,
                                        request.getHeader(REQUEST_PROPERTY_KEY));
                                return new MockResponse().setBody(mJsScript);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        mClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(Uri.parse(url.toString()))
                                .setUseCache(false)
                                .setRequestProperties(
                                        ImmutableMap.of(
                                                REQUEST_PROPERTY_KEY, REQUEST_PROPERTY_VALUE))
                                .setDevContext(DEV_CONTEXT_DISABLED)
                                .build())
                .get();
    }

    @Test
    public void testAdServiceRequestResponseDefault_Empty() {
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.EMPTY)
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .build();

        assertEquals(request.getRequestProperties(), ImmutableMap.of());
        assertEquals(request.getResponseHeaderKeys(), ImmutableSet.of());
        assertFalse(request.getUseCache());

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody("").build();

        assertEquals(response.getResponseHeaders(), ImmutableMap.of());
    }

    @Test
    public void testCreateAdServicesRequestResponse_Success() {
        final Uri uri = Uri.parse("www.google.com");
        final ImmutableMap requestProperties = ImmutableMap.of("key", "value");
        final ImmutableSet responseHeaderKeys = ImmutableSet.of("entry1", "entry2");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.create(
                        uri,
                        requestProperties,
                        responseHeaderKeys,
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.GET,
                        EMPTY_BODY);

        assertEquals(uri, request.getUri());
        assertEquals(requestProperties, request.getRequestProperties());
        assertEquals(responseHeaderKeys, request.getResponseHeaderKeys());
        assertFalse(request.getUseCache());

        final String body = "Fake response body";
        final ImmutableMap responseHeaders = ImmutableMap.of("key", List.of("value1", "value2"));
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.create(body, responseHeaders);

        assertEquals(body, response.getResponseBody());
        assertEquals(responseHeaders, response.getResponseHeaders());
    }

    @Test
    public void testCreateAdServicesRequestResponse_Success_DevOptionsEnabled() {
        final Uri uri = Uri.parse("www.google.com");
        final ImmutableMap requestProperties = ImmutableMap.of("key", "value");
        final ImmutableSet responseHeaderKeys = ImmutableSet.of("entry1", "entry2");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.create(
                        uri,
                        requestProperties,
                        responseHeaderKeys,
                        false,
                        DEV_CONTEXT_ENABLED,
                        AdServicesHttpUtil.HttpMethodType.GET,
                        EMPTY_BODY);

        assertEquals(uri, request.getUri());
        assertEquals(requestProperties, request.getRequestProperties());
        assertEquals(responseHeaderKeys, request.getResponseHeaderKeys());
        assertFalse(request.getUseCache());

        final String body = "Fake response body";
        final ImmutableMap responseHeaders = ImmutableMap.of("key", List.of("value1", "value2"));
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.create(body, responseHeaders);

        assertEquals(body, response.getResponseBody());
        assertEquals(responseHeaders, response.getResponseHeaders());
    }

    @Test
    public void testFetchPayloadResponsesUsesCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER)
                                        .addHeader(RESPONSE_HEADER_KEY, RESPONSE_HEADER_VALUE_1)
                                        .addHeader(RESPONSE_HEADER_KEY, RESPONSE_HEADER_VALUE_2);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        mClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(Uri.parse(url.toString()))
                                .setUseCache(true)
                                .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                .setDevContext(DEV_CONTEXT_DISABLED)
                                .build())
                .get();
        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());
        Thread.sleep(500);
        // Flake proofing : In rare but possible scenario where the cache is not done persisting, we
        // will get cache miss, no point asserting further
        assumeTrue(mCache.getCachedEntriesCount() == 1);
        AdServicesHttpClientResponse response =
                mClient.fetchPayload(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(true)
                                        .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build())
                        .get();
        assertEquals(mJsScript, response.getResponseBody());
        assertTrue(
                response.getResponseHeaders()
                        .get(RESPONSE_HEADER_KEY)
                        .contains(RESPONSE_HEADER_VALUE_1));
        assertTrue(
                response.getResponseHeaders()
                        .get(RESPONSE_HEADER_KEY)
                        .contains(RESPONSE_HEADER_VALUE_2));
        assertEquals(
                "Only one header should have been cached", 1, response.getResponseHeaders().size());
        assertEquals("This call should have been cached", 1, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadResponsesDefaultSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        AdServicesHttpClientResponse response =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        assertEquals(mJsScript, response.getResponseBody());
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadResponsesNoCacheHeaderSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(NO_CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        AdServicesHttpClientResponse response =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        assertEquals(mJsScript, response.getResponseBody());
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadCacheDisabledSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        Flags disableCacheFlags =
                new Flags() {
                    @Override
                    public boolean getFledgeHttpCachingEnabled() {
                        return false;
                    }
                };
        HttpCache cache = CacheProviderFactory.create(CONTEXT, disableCacheFlags);
        AdServicesHttpsClient client = new AdServicesHttpsClient(mExecutorService, cache);

        client.fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.parse(url.toString()))
                        .setUseCache(true)
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .build());

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        AdServicesHttpClientResponse response =
                client.fetchPayload(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(true)
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build())
                        .get();
        assertEquals(mJsScript, response.getResponseBody());
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testPostJsonSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        assertThat(postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED)).isNull();
    }

    @Test
    public void testPostJsonCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("POST", request1.getMethod());
    }

    @Test
    public void testPostJsonCorrectPath_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_ENABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("POST", request1.getMethod());
    }

    @Test
    public void testPostJsonCorrectData() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        assertEquals("POST", request1.getMethod());
        assertEquals(mData.toString(), request1.getUtf8Body());
    }

    @Test
    public void testPostJsonFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testPostJsonDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> postJson(Uri.parse(mFakeUrl), mData, DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testPostJsonThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                postJson(
                                        Uri.parse("http://google.com"),
                                        mData,
                                        DEV_CONTEXT_DISABLED));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFailedResponseWithStatusCode() throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause()).isInstanceOf(AdServicesNetworkException.class);

        // Assert the expected AdServicesNetworkException is thrown.
        AdServicesNetworkException exception =
                (AdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
    }

    @Test
    public void testFailedResponseWithStatusCodeAndRetryAfter() throws Exception {
        MockResponse response =
                new MockResponse().setResponseCode(429).setHeader("Retry-After", 1000);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause())
                .isInstanceOf(RetryableAdServicesNetworkException.class);

        // Assert the expected RetryableAdServicesNetworkException is thrown.
        RetryableAdServicesNetworkException exception =
                (RetryableAdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    public void testFailedResponseWithStatusCodeAndRetryAfterWithNoRetryHeader() throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause())
                .isInstanceOf(RetryableAdServicesNetworkException.class);

        // Assert the expected RetryableAdServicesNetworkException is thrown.
        RetryableAdServicesNetworkException exception =
                (RetryableAdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfter()).isEqualTo(DEFAULT_RETRY_AFTER_VALUE);
    }

    @Test
    public void testFetchPayloadDomainIsLocalhost_DevOptionsDisabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        // Verify we are pinging a local domain.
        assertThat(WebAddresses.isLocalhost(Uri.parse(url.toString()))).isTrue();
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testFetchPayloadDomainIsLocalhost_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(NO_CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse response =
                mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED).get();

        // Verify we are pinging a local domain.
        assertThat(WebAddresses.isLocalhost(Uri.parse(url.toString()))).isTrue();
        assertEquals(mJsScript, response.getResponseBody());
    }

    @Test
    public void testperformRequestAndGetResponseInBytes_postsCorrectData() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                        .build();

        mClient.performRequestGetResponseInBase64String(request).get();

        RecordedRequest recordedRequest1 = server.takeRequest();
        assertThat(recordedRequest1.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(recordedRequest1.getBody()).isEqualTo(postedBody);
    }

    @Test
    public void performRequestGetResponseBytes_getRequestNonEmptyBody_requestBodyShouldBeEmpty()
            throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        mClient.performRequestGetResponseInBase64String(request).get();

        RecordedRequest recordedRequest = server.takeRequest();
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.GET.name());
        assertThat(recordedRequest.getBody()).isEqualTo(EMPTY_BODY);
    }

    @Test
    public void testperformRequestAndGetResponseInBytes_shouldReturnResponseInBytes()
            throws Exception {
        byte[] byteResponse = {1, 2, 3, 54};
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(byteResponse)));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBodyInBytes = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBodyInBytes)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        AdServicesHttpClientResponse response =
                mClient.performRequestGetResponseInBase64String(request).get();

        String expectedResponseString = BaseEncoding.base64().encode(byteResponse);
        assertThat(response.getResponseBody()).isEqualTo(expectedResponseString);
    }

    @Test
    public void performRequestGetResponseBytes_failedStatusCode_shouldThrowErrorWithCorrectCode()
            throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(429)));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> mClient.performRequestGetResponseInBase64String(request).get());
        assertThat(wrapperException.getCause()).isInstanceOf(AdServicesNetworkException.class);

        // Assert the expected AdServicesNetworkException is thrown.
        AdServicesNetworkException exception =
                (AdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
    }

    @Test
    public void testPerformRequestAndGetResponseInString_shouldReturnResponseString()
            throws Exception {
        String stringResponse = "This is a plain String response which could also be a JSON String";
        byte[] postedBodyInBytes = "{[1,2,3]}".getBytes("UTF-8");

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        assertEquals(new String(postedBodyInBytes), new String(request.getBody()));
                        return new MockResponse().setBody(stringResponse);
                    }
                };
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);
        URL url = server.getUrl(mReportingPath);

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(
                        "Content-Type", "application/json",
                        "Accept", "application/json");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBodyInBytes)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                        .build();

        AdServicesHttpClientResponse response =
                mClient.performRequestGetResponseInPlainString(request).get();

        assertEquals(1, server.getRequestCount());
        assertThat(response.getResponseBody()).isEqualTo(stringResponse);
    }

    private AdServicesHttpClientResponse fetchPayload(Uri uri, DevContext devContext)
            throws Exception {
        return mClient.fetchPayload(uri, devContext).get();
    }

    private Void getAndReadNothing(Uri uri, DevContext devContext) throws Exception {
        return mClient.getAndReadNothing(uri, devContext).get();
    }

    private Void postJson(Uri uri, String data, DevContext devContext) throws Exception {
        return mClient.postPlainText(uri, data, devContext).get();
    }
}
