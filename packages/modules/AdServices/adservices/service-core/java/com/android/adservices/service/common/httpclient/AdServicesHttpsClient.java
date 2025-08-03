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

import android.adservices.exceptions.AdServicesNetworkException;
import android.adservices.exceptions.RetryableAdServicesNetworkException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.DBCacheEntry;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * This is an HTTPS client to be used by the PP API services. The primary uses of this client
 * include fetching payloads from ad tech-provided URIs and reporting on generated reporting URLs
 * through GET or POST calls.
 */
public class AdServicesHttpsClient {

    public static final long DEFAULT_MAX_BYTES = 1048576;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    // Setting default max content size to 1024 * 1024 which is ~ 1MB
    private static final String CONTENT_SIZE_ERROR = "Content size exceeds limit!";
    private static final String RETRY_AFTER_HEADER_FIELD = "Retry-After";
    private final int mConnectTimeoutMs;
    private final int mReadTimeoutMs;
    private final long mMaxBytes;
    private final ListeningExecutorService mExecutorService;
    private final UriConverter mUriConverter;
    private final HttpCache mCache;

    /**
     * Create an HTTPS client with the input {@link ExecutorService} and initial connect and read
     * timeouts (in milliseconds). Using this constructor does not provide any caching.
     *
     * @param executorService an {@link ExecutorService} that allows connection and fetching to be
     *     executed outside the main calling thread
     * @param connectTimeoutMs the timeout, in milliseconds, for opening an initial link with to a
     *     target resource using this client. If set to 0, this timeout is interpreted as infinite
     *     (see {@link URLConnection#setConnectTimeout(int)}).
     * @param readTimeoutMs the timeout, in milliseconds, for reading a response from a target
     *     address using this client. If set to 0, this timeout is interpreted as infinite (see
     *     {@link URLConnection#setReadTimeout(int)}).
     * @param maxBytes The maximum size of an HTTPS response in bytes.
     */
    public AdServicesHttpsClient(
            ExecutorService executorService,
            int connectTimeoutMs,
            int readTimeoutMs,
            long maxBytes) {
        this(
                executorService,
                connectTimeoutMs,
                readTimeoutMs,
                maxBytes,
                new UriConverter(),
                CacheProviderFactory.createNoOpCache());
    }

    /**
     * Create an HTTPS client with the input {@link ExecutorService} and default initial connect and
     * read timeouts. This will also contain the default size of an HTTPS response, 1 MB.
     *
     * @param executorService an {@link ExecutorService} that allows connection and fetching to be
     *     executed outside the main calling thread
     * @param cache A {@link HttpCache} that caches requests and response based on the use case
     */
    public AdServicesHttpsClient(
            @NonNull ExecutorService executorService, @NonNull HttpCache cache) {
        this(
                executorService,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MAX_BYTES,
                new UriConverter(),
                cache);
    }

    @VisibleForTesting
    AdServicesHttpsClient(
            ExecutorService executorService,
            int connectTimeoutMs,
            int readTimeoutMs,
            long maxBytes,
            UriConverter uriConverter,
            @NonNull HttpCache cache) {
        mConnectTimeoutMs = connectTimeoutMs;
        mReadTimeoutMs = readTimeoutMs;
        mExecutorService = MoreExecutors.listeningDecorator(executorService);
        mMaxBytes = maxBytes;
        mUriConverter = uriConverter;
        mCache = cache;
    }

    /** Opens the Url Connection */
    @NonNull
    private URLConnection openUrl(@NonNull URL url) throws IOException {
        Objects.requireNonNull(url);
        return url.openConnection();
    }

    @NonNull
    private HttpsURLConnection setupConnection(@NonNull URL url, @NonNull DevContext devContext)
            throws IOException {
        Objects.requireNonNull(url);
        Objects.requireNonNull(devContext);
        // We validated that the URL is https in toUrl
        HttpsURLConnection urlConnection = (HttpsURLConnection) openUrl(url);
        urlConnection.setConnectTimeout(mConnectTimeoutMs);
        urlConnection.setReadTimeout(mReadTimeoutMs);
        // Setting true explicitly to follow redirects
        Uri uri = Uri.parse(url.toString());
        if (WebAddresses.isLocalhost(uri) && devContext.getDevOptionsEnabled()) {
            LogUtil.v("Using unsafe HTTPS for url %s", url.toString());
            urlConnection.setSSLSocketFactory(getUnsafeSslSocketFactory());
        } else if (WebAddresses.isLocalhost(uri)) {
            LogUtil.v(
                    String.format(
                            "Using normal HTTPS without unsafe SSL socket factory for a localhost"
                                    + " address, DevOptionsEnabled: %s, CallingPackageName: %s",
                            devContext.getDevOptionsEnabled(),
                            devContext.getCallingAppPackageName()));
        }
        urlConnection.setInstanceFollowRedirects(true);
        return urlConnection;
    }

    @NonNull
    private HttpsURLConnection setupPostConnectionWithPlainText(
            @NonNull URL url, @NonNull DevContext devContext) throws IOException {
        Objects.requireNonNull(url);
        Objects.requireNonNull(devContext);
        HttpsURLConnection urlConnection = setupConnection(url, devContext);
        urlConnection.setRequestMethod("POST");
        urlConnection.setRequestProperty("Content-Type", "text/plain");
        urlConnection.setDoOutput(true);
        return urlConnection;
    }

    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    private static SSLSocketFactory getUnsafeSslSocketFactory() {
        try {
            TrustManager[] bypassTrustManagers =
                    new TrustManager[] {
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }

                            public void checkClientTrusted(
                                    X509Certificate[] chain, String authType) {}

                            public void checkServerTrusted(
                                    X509Certificate[] chain, String authType) {}
                        }
                    };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, bypassTrustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            LogUtil.e(e, "getUnsafeSslSocketFactory caught exception");
            return null;
        }
    }

    /**
     * Performs a GET request on the given URI in order to fetch a payload.
     *
     * @param uri a {@link Uri} pointing to a target server, converted to a URL for fetching
     * @return a string containing the fetched payload
     */
    @NonNull
    public ListenableFuture<AdServicesHttpClientResponse> fetchPayload(
            @NonNull Uri uri, @NonNull DevContext devContext) {
        LogUtil.v("Fetching payload from uri: " + uri);
        return fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setUri(uri)
                        .setDevContext(devContext)
                        .build());
    }

    /**
     * Performs a GET request on the given URI in order to fetch a payload.
     *
     * @param uri a {@link Uri} pointing to a target server, converted to a URL for fetching
     * @param headers keys of the headers we want in the response
     * @return a string containing the fetched payload
     */
    @NonNull
    public ListenableFuture<AdServicesHttpClientResponse> fetchPayload(
            @NonNull Uri uri,
            @NonNull ImmutableSet<String> headers,
            @NonNull DevContext devContext) {
        LogUtil.v("Fetching payload from uri: " + uri + " with headers: " + headers.toString());
        return fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setUri(uri)
                        .setResponseHeaderKeys(headers)
                        .setDevContext(devContext)
                        .build());
    }

    /**
     * Performs a GET request on the given URI in order to fetch a payload.
     *
     * @param request of type {@link AdServicesHttpClientRequest}
     * @return a string containing the fetched payload
     */
    @NonNull
    public ListenableFuture<AdServicesHttpClientResponse> fetchPayload(
            @NonNull AdServicesHttpClientRequest request) {
        Objects.requireNonNull(request.getUri());

        StringBuilder logBuilder =
                new StringBuilder(
                        "Fetching payload for request: uri: "
                                + request.getUri()
                                + " use cache: "
                                + request.getUseCache()
                                + " dev context: "
                                + request.getDevContext().getDevOptionsEnabled());
        if (request.getRequestProperties() != null) {
            logBuilder
                    .append(" request properties: ")
                    .append(request.getRequestProperties().toString());
        }
        if (request.getResponseHeaderKeys() != null) {
            logBuilder
                    .append(" response headers keys to be read in response: ")
                    .append(request.getResponseHeaderKeys().toString());
        }

        LogUtil.v(logBuilder.toString());
        return ClosingFuture.from(
                        mExecutorService.submit(() -> mUriConverter.toUrl(request.getUri())))
                .transformAsync(
                        (closer, url) ->
                                ClosingFuture.from(
                                        mExecutorService.submit(
                                                () -> doFetchPayload(url, closer, request))),
                        mExecutorService)
                .finishToFuture();
    }

    private AdServicesHttpClientResponse doFetchPayload(
            @NonNull URL url,
            @NonNull ClosingFuture.DeferredCloser closer,
            AdServicesHttpClientRequest request)
            throws IOException, AdServicesNetworkException {
        int traceCookie = Tracing.beginAsyncSection(Tracing.FETCH_PAYLOAD);
        LogUtil.v("Downloading payload from: \"%s\"", url.toString());
        if (request.getUseCache()) {
            AdServicesHttpClientResponse cachedResponse = getResultsFromCache(url);
            if (cachedResponse != null) {
                return cachedResponse;
            }
            LogUtil.v("Cache miss for url: %s", url.toString());
        }
        int httpTraceCookie = Tracing.beginAsyncSection(Tracing.HTTP_REQUEST);
        HttpsURLConnection urlConnection;
        try {
            urlConnection = setupConnection(url, request.getDevContext());
        } catch (IOException e) {
            LogUtil.d(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        InputStream inputStream = null;
        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            for (Map.Entry<String, String> entry : request.getRequestProperties().entrySet()) {
                urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            closer.eventuallyClose(new CloseableConnectionWrapper(urlConnection), mExecutorService);
            Map<String, List<String>> requestPropertiesMap = urlConnection.getRequestProperties();
            int responseCode = urlConnection.getResponseCode();
            LogUtil.v("Received %s response status code.", responseCode);
            if (isSuccessfulResponse(responseCode)) {
                inputStream = new BufferedInputStream(urlConnection.getInputStream());
                String responseBody =
                        fromInputStream(inputStream, urlConnection.getContentLengthLong());
                Map<String, List<String>> responseHeadersMap =
                        pickRequiredHeaderFields(
                                urlConnection.getHeaderFields(), request.getResponseHeaderKeys());
                if (request.getUseCache()) {
                    LogUtil.v("Putting data in cache for url: %s", url);
                    mCache.put(url, responseBody, requestPropertiesMap, responseHeadersMap);
                }
                AdServicesHttpClientResponse response =
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseBody)
                                .setResponseHeaders(
                                        ImmutableMap.<String, List<String>>builder()
                                                .putAll(responseHeadersMap.entrySet())
                                                .build())
                                .build();
                return response;
            } else {
                throwError(urlConnection, responseCode);
                return null;
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
            maybeClose(inputStream);
            Tracing.endAsyncSection(Tracing.HTTP_REQUEST, httpTraceCookie);
            Tracing.endAsyncSection(Tracing.FETCH_PAYLOAD, traceCookie);
        }
    }

    private Map<String, List<String>> pickRequiredHeaderFields(
            Map<String, List<String>> allHeaderFields, ImmutableSet<String> requiredHeaderKeys) {
        HashMap<String, List<String>> result = new HashMap<>();
        for (String headerKey : requiredHeaderKeys) {
            if (allHeaderFields.containsKey(headerKey)) {
                List<String> headerValues = new ArrayList<>(allHeaderFields.get(headerKey));
                LogUtil.v(
                        String.format(
                                "Found header: %s in response headers with value as %s",
                                headerKey, String.join(", ", headerValues)));
                result.put(headerKey, headerValues);
            }
        }
        LogUtil.v("requiredHeaderFields: " + result);
        return result;
    }

    private AdServicesHttpClientResponse getResultsFromCache(URL url) {
        DBCacheEntry cachedEntry = mCache.get(url);
        if (cachedEntry != null) {
            LogUtil.v("Cache hit for url: %s", url.toString());
            return AdServicesHttpClientResponse.builder()
                    .setResponseBody(cachedEntry.getResponseBody())
                    .setResponseHeaders(
                            ImmutableMap.<String, List<String>>builder()
                                    .putAll(cachedEntry.getResponseHeaders().entrySet())
                                    .build())
                    .build();
        }
        return null;
    }

    /**
     * Performs a GET request on a Uri without reading the response.
     *
     * @param uri The URI to perform the GET request on.
     */
    public ListenableFuture<Void> getAndReadNothing(
            @NonNull Uri uri, @NonNull DevContext devContext) {
        Objects.requireNonNull(uri);

        return ClosingFuture.from(mExecutorService.submit(() -> mUriConverter.toUrl(uri)))
                .transformAsync(
                        (closer, url) ->
                                ClosingFuture.from(
                                        mExecutorService.submit(
                                                () ->
                                                        doGetAndReadNothing(
                                                                url, closer, devContext))),
                        mExecutorService)
                .finishToFuture();
    }

    private Void doGetAndReadNothing(
            @NonNull URL url,
            @NonNull ClosingFuture.DeferredCloser closer,
            @NonNull DevContext devContext)
            throws IOException, AdServicesNetworkException {
        LogUtil.v(
                "doGetAndReadNothing to: \"%s\", dev context: %s",
                url.toString(), devContext.getDevOptionsEnabled());
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupConnection(url, devContext);
        } catch (IOException e) {
            LogUtil.d(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            closer.eventuallyClose(new CloseableConnectionWrapper(urlConnection), mExecutorService);
            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d("GET request succeeded for URL: " + url);
            } else {
                LogUtil.d("GET request failed for URL: " + url);
                throwError(urlConnection, responseCode);
            }
            return null;
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    /**
     * Performs a POST request on a Uri and attaches {@code String} to the request
     *
     * @param uri to do the POST request on
     * @param requestBody Attached to the POST request.
     */
    public ListenableFuture<Void> postPlainText(
            @NonNull Uri uri, @NonNull String requestBody, @NonNull DevContext devContext) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(requestBody);

        return ClosingFuture.from(mExecutorService.submit(() -> mUriConverter.toUrl(uri)))
                .transformAsync(
                        (closer, url) ->
                                ClosingFuture.from(
                                        mExecutorService.submit(
                                                () ->
                                                        doPostPlainText(
                                                                url,
                                                                requestBody,
                                                                closer,
                                                                devContext))),
                        mExecutorService)
                .finishToFuture();
    }

    private Void doPostPlainText(
            URL url, String data, ClosingFuture.DeferredCloser closer, DevContext devContext)
            throws IOException, AdServicesNetworkException {
        LogUtil.v("Reporting to: \"%s\"", url.toString());
        HttpsURLConnection urlConnection;

        try {
            urlConnection = setupPostConnectionWithPlainText(url, devContext);

        } catch (IOException e) {
            LogUtil.d(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            closer.eventuallyClose(new CloseableConnectionWrapper(urlConnection), mExecutorService);

            OutputStream os = urlConnection.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write(data);
            osw.flush();
            osw.close();

            int responseCode = urlConnection.getResponseCode();
            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d("POST request succeeded for URL: " + url);
            } else {
                LogUtil.d("POST request failed for URL: " + url);
                throwError(urlConnection, responseCode);
            }
            return null;
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
        }
    }

    /**
     * Performs an HTTP request according to the request object and returns the response in byte
     * array.
     */
    public ListenableFuture<AdServicesHttpClientResponse> performRequestGetResponseInBase64String(
            @NonNull AdServicesHttpClientRequest request) {
        Objects.requireNonNull(request.getUri());
        return ClosingFuture.from(
                        mExecutorService.submit(() -> mUriConverter.toUrl(request.getUri())))
                .transformAsync(
                        (closer, url) ->
                                ClosingFuture.from(
                                        mExecutorService.submit(
                                                () ->
                                                        doPerformRequestAndGetResponse(
                                                                url,
                                                                closer,
                                                                request,
                                                                ResponseBodyType
                                                                        .BASE64_ENCODED_STRING))),
                        mExecutorService)
                .finishToFuture();
    }

    /**
     * Performs an HTTP request according to the request object and returns the response in plain
     * String
     */
    public ListenableFuture<AdServicesHttpClientResponse> performRequestGetResponseInPlainString(
            @NonNull AdServicesHttpClientRequest request) {
        Objects.requireNonNull(request.getUri());
        LogUtil.d("Making request expecting a response in plain string");
        return ClosingFuture.from(
                        mExecutorService.submit(() -> mUriConverter.toUrl(request.getUri())))
                .transformAsync(
                        (closer, url) ->
                                ClosingFuture.from(
                                        mExecutorService.submit(
                                                () ->
                                                        doPerformRequestAndGetResponse(
                                                                url,
                                                                closer,
                                                                request,
                                                                ResponseBodyType
                                                                        .PLAIN_TEXT_STRING))),
                        mExecutorService)
                .finishToFuture();
    }

    private AdServicesHttpClientResponse doPerformRequestAndGetResponse(
            @NonNull URL url,
            @NonNull ClosingFuture.DeferredCloser closer,
            AdServicesHttpClientRequest request,
            ResponseBodyType responseType)
            throws IOException, AdServicesNetworkException {
        HttpsURLConnection urlConnection;
        try {
            urlConnection = setupConnection(url, request.getDevContext());
            urlConnection.setRequestMethod(request.getHttpMethodType().name());
        } catch (IOException e) {
            LogUtil.e(e, "Failed to open URL");
            throw new IllegalArgumentException("Failed to open URL!");
        }

        InputStream inputStream = null;
        try {
            // TODO(b/237342352): Both connect and read timeouts are kludged in this method and if
            //  necessary need to be separated
            for (Map.Entry<String, String> entry : request.getRequestProperties().entrySet()) {
                urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (request.getHttpMethodType() == AdServicesHttpUtil.HttpMethodType.POST
                    && request.getBodyInBytes() != null
                    && request.getBodyInBytes().length > 0) {
                urlConnection.setDoOutput(true);
                try (BufferedOutputStream out =
                        new BufferedOutputStream(urlConnection.getOutputStream())) {
                    out.write(request.getBodyInBytes());
                }
            }

            closer.eventuallyClose(new CloseableConnectionWrapper(urlConnection), mExecutorService);
            int responseCode = urlConnection.getResponseCode();
            LogUtil.v("Received %s response status code.", responseCode);

            if (isSuccessfulResponse(responseCode)) {
                LogUtil.d(" request succeeded for URL: " + url);
                Map<String, List<String>> responseHeadersMap =
                        pickRequiredHeaderFields(
                                urlConnection.getHeaderFields(), request.getResponseHeaderKeys());
                inputStream = new BufferedInputStream(urlConnection.getInputStream());
                String responseBody;
                if (responseType == ResponseBodyType.BASE64_ENCODED_STRING) {
                    responseBody =
                            BaseEncoding.base64()
                                    .encode(
                                            getByteArray(
                                                    inputStream,
                                                    urlConnection.getContentLengthLong()));
                } else {
                    responseBody =
                            fromInputStream(inputStream, urlConnection.getContentLengthLong());
                }
                return AdServicesHttpClientResponse.builder()
                        .setResponseBody(responseBody)
                        .setResponseHeaders(
                                ImmutableMap.<String, List<String>>builder()
                                        .putAll(responseHeadersMap.entrySet())
                                        .build())
                        .build();
            } else {
                LogUtil.d(" request failed for URL: " + url);
                throwError(urlConnection, responseCode);
                return null;
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out while reading response!", e);
        } finally {
            maybeDisconnect(urlConnection);
            maybeClose(inputStream);
        }
    }

    private byte[] getByteArray(@Nullable InputStream in, long contentLength) throws IOException {
        if (contentLength == 0) {
            return new byte[0];
        }
        try {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private void throwError(final HttpsURLConnection urlConnection, int responseCode)
            throws AdServicesNetworkException {
        LogUtil.v("Error occurred while executing HTTP request.");

        // Default values for AdServiceNetworkException fields.
        @AdServicesNetworkException.ErrorCode
        int errorCode = AdServicesNetworkException.ERROR_OTHER;
        Duration retryAfterDuration = RetryableAdServicesNetworkException.UNSET_RETRY_AFTER_VALUE;

        // Assign a relevant error code to the HTTP response code.
        switch (responseCode / 100) {
            case 3:
                errorCode = AdServicesNetworkException.ERROR_REDIRECTION;
                break;
            case 4:
                // If an HTTP 429 response code was received, extract the retry-after duration
                if (responseCode == 429) {
                    errorCode = AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS;
                    String headerValue = urlConnection.getHeaderField(RETRY_AFTER_HEADER_FIELD);
                    if (headerValue != null) {
                        // TODO(b/282017541): Add a maximum allowed retry-after duration.
                        retryAfterDuration = Duration.ofMillis(Long.parseLong(headerValue));
                    } else {
                        retryAfterDuration = DEFAULT_RETRY_AFTER_VALUE;
                    }
                } else {
                    errorCode = AdServicesNetworkException.ERROR_CLIENT;
                }
                break;
            case 5:
                errorCode = AdServicesNetworkException.ERROR_SERVER;
        }
        LogUtil.v("Received %s error status code.", responseCode);

        // Throw the appropriate exception.
        AdServicesNetworkException exception;
        if (retryAfterDuration.compareTo(
                        RetryableAdServicesNetworkException.UNSET_RETRY_AFTER_VALUE)
                <= 0) {
            exception = new AdServicesNetworkException(errorCode);
        } else {
            exception = new RetryableAdServicesNetworkException(errorCode, retryAfterDuration);
        }
        LogUtil.e("Throwing %s.", exception.toString());
        throw exception;
    }

    private static void maybeDisconnect(@Nullable URLConnection urlConnection) {
        if (urlConnection == null) {
            return;
        }

        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
            httpUrlConnection.disconnect();
        } else {
            LogUtil.d("Not closing URLConnection of type %s", urlConnection.getClass());
        }
    }

    private static void maybeClose(@Nullable InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return;
        } else {
            inputStream.close();
        }
    }

    /**
     * @return the connection timeout, in milliseconds, when opening an initial link to a target
     *     address with this client
     */
    public int getConnectTimeoutMs() {
        return mConnectTimeoutMs;
    }

    /**
     * @return the read timeout, in milliseconds, when reading the response from a target address
     *     with this client
     */
    public int getReadTimeoutMs() {
        return mReadTimeoutMs;
    }

    /**
     * @return true if responseCode matches 2.*, i.e. 200, 204, 206
     */
    public static boolean isSuccessfulResponse(int responseCode) {
        return (responseCode / 100) == 2;
    }

    /**
     * Reads a {@link InputStream} and returns a {@code String}. To enforce content size limits, we
     * employ the following strategy: 1. If {@link URLConnection} cannot determine the content size,
     * we invoke {@code manualStreamToString(InputStream)} where we manually apply the content
     * restriction. 2. Otherwise, we invoke {@code streamToString(InputStream, long)}.
     *
     * @throws IOException if content size limit of is exceeded
     */
    @NonNull
    private String fromInputStream(@NonNull InputStream in, long size) throws IOException {
        Objects.requireNonNull(in);
        if (size == 0) {
            return "";
        } else if (size < 0) {
            return manualStreamToString(in);
        } else {
            return streamToString(in, size);
        }
    }

    @NonNull
    private String streamToString(@NonNull InputStream in, long size) throws IOException {
        Objects.requireNonNull(in);
        if (size > mMaxBytes) {
            throw new IOException(CONTENT_SIZE_ERROR);
        }
        return new String(ByteStreams.toByteArray(in), Charsets.UTF_8);
    }

    @NonNull
    private String manualStreamToString(@NonNull InputStream in) throws IOException {
        Objects.requireNonNull(in);
        ByteArrayOutputStream into = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        long total = 0;
        for (int n; 0 < (n = in.read(buf)); ) {
            total += n;
            if (total <= mMaxBytes) {
                into.write(buf, 0, n);
            } else {
                into.close();
                throw new IOException(CONTENT_SIZE_ERROR);
            }
        }
        into.close();
        return into.toString("UTF-8");
    }

    private static class CloseableConnectionWrapper implements Closeable {
        @Nullable final HttpsURLConnection mURLConnection;

        private CloseableConnectionWrapper(HttpsURLConnection urlConnection) {
            mURLConnection = urlConnection;
        }

        @Override
        public void close() throws IOException {
            maybeClose(mURLConnection.getInputStream());
            maybeClose(mURLConnection.getErrorStream());
            maybeDisconnect(mURLConnection);
        }
    }

    /** A light-weight class to convert Uri to URL */
    public static final class UriConverter {

        @NonNull
        URL toUrl(@NonNull Uri uri) {
            Objects.requireNonNull(uri);
            Preconditions.checkArgument(
                    ValidatorUtil.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()),
                    "URI \"%s\" must use HTTPS",
                    uri.toString());

            URL url;
            try {
                url = new URL(uri.toString());
            } catch (MalformedURLException e) {
                LogUtil.d(e, "Uri is malformed! ");
                throw new IllegalArgumentException("Uri is malformed!");
            }
            return url;
        }
    }

    /**
     * @return the cache associated with this instance of client
     */
    public HttpCache getAssociatedCache() {
        return mCache;
    }

    enum ResponseBodyType {
        BASE64_ENCODED_STRING,
        PLAIN_TEXT_STRING
    }
}
