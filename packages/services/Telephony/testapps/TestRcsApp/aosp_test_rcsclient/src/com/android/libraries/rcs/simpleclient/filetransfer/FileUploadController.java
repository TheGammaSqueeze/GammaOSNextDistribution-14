/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.filetransfer;

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.internal.http.multipart.FilePart;
import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.Part;
import com.android.internal.http.multipart.StringPart;
import com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor.HttpRequestExecutor;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

/** File upload functionality. */
final class FileUploadController {

    private static final String TAG = "FileUploadController";
    private static final String ATTRIBUTE_PREEMPTIVE_AUTH = "preemptive-auth";
    private static final String PARAM_NONCE = "nonce";
    private static final String PARAM_REALM = "realm";
    private static final String FILE_PART_NAME = "File";
    private static final String TRANSFER_ID_PART_NAME = "tid";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String THREE_GPP_GBA = "3gpp-gba";
    private static final int HTTPS_PORT = 443;

    private final HttpRequestExecutor requestExecutor;
    private final String contentServerUri;
    private final ListeningExecutorService executor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));
    private String mCarrierName;

    FileUploadController(HttpRequestExecutor requestExecutor, String contentServerUri,
            String carrierName) {
        this.requestExecutor = requestExecutor;
        this.contentServerUri = contentServerUri;
        this.mCarrierName = carrierName;
    }

    public ListenableFuture<String> uploadFile(
            String transactionId, File file) {
        DefaultHttpClient httpClient = getSecureHttpClient();

        Log.i(TAG, "sendEmptyPost");
        // Send an empty post.
        ListenableFuture<HttpResponse> initialResponseFuture = sendEmptyPost(httpClient);

        BasicHttpContext httpContext = new BasicHttpContext();
        ListenableFuture<AuthScheme> prepareAuthFuture =
                Futures.transform(
                        initialResponseFuture,
                        initialResponse -> {
                            Log.i(TAG, "Response for the empty post: "
                                    + initialResponse.getStatusLine());
                            if (initialResponse.getStatusLine().getStatusCode()
                                    != HttpURLConnection.HTTP_UNAUTHORIZED) {
                                throw new IllegalArgumentException(
                                        "Expected HTTP_UNAUTHORIZED, but got "
                                                + initialResponse.getStatusLine());
                            }
                            try {
                                initialResponse.getEntity().consumeContent();
                            } catch (IOException e) {
                                throw new IllegalArgumentException(e);
                            }

                            // Override nonce and realm in the HTTP context.
                            RFC2617Scheme authScheme = createAuthScheme(initialResponse);
                            httpContext.setAttribute(ATTRIBUTE_PREEMPTIVE_AUTH, authScheme);
                            return authScheme;
                        },
                        executor);

        // Executing the post with credentials.
        return Futures.transformAsync(
                prepareAuthFuture,
                authScheme ->
                        executeAuthenticatedPost(
                                httpClient, httpContext, authScheme, transactionId, file),
                executor);
    }

    private RFC2617Scheme createAuthScheme(HttpResponse initialResponse) {
        if (!initialResponse.containsHeader(AUTH.WWW_AUTH)) {
            throw new IllegalArgumentException(
                    AUTH.WWW_AUTH + " header not found in the original response.");
        }

        Header authHeader = initialResponse.getFirstHeader(AUTH.WWW_AUTH);
        String scheme = authHeader.getValue();

        if (scheme.contains(AuthPolicy.DIGEST)) {
            DigestScheme digestScheme = new DigestScheme();
            try {
                digestScheme.processChallenge(authHeader);
            } catch (MalformedChallengeException e) {
                throw new IllegalArgumentException(e);
            }
            return digestScheme;
        } else {
            throw new IllegalArgumentException("Unable to create authentication scheme " + scheme);
        }
    }

    private DefaultHttpClient getSecureHttpClient() {
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        Uri uri = Uri.parse(contentServerUri);
        int port = uri.getPort();
        if (port <= 0) {
            port = HTTPS_PORT;
        }

        Scheme scheme = new Scheme("https", socketFactory, port);
        DefaultHttpClient httpClient = new DefaultHttpClient();
        ClientConnectionManager manager = httpClient.getConnectionManager();
        SchemeRegistry registry = manager.getSchemeRegistry();
        registry.register(scheme);

        return httpClient;
    }

    private ListenableFuture<HttpResponse> sendEmptyPost(HttpClient httpClient) {
        Log.i(TAG, "Sending an empty post: ");
        HttpPost emptyPost = new HttpPost(contentServerUri);
        emptyPost.setHeader("User-Agent", getUserAgent());
        return executor.submit(() -> httpClient.execute(emptyPost));
    }

    private ListenableFuture<String> executeAuthenticatedPost(
            DefaultHttpClient httpClient,
            HttpContext context,
            AuthScheme authScheme,
            String transactionId,
            File file)
            throws IOException {

        Part[] parts = {
                new StringPart(TRANSFER_ID_PART_NAME, transactionId),
                new FilePart(FILE_PART_NAME, file)
        };
        MultipartEntity entity = new MultipartEntity(parts);

        HttpPost postRequest = new HttpPost(contentServerUri);
        postRequest.setHeader("User-Agent", getUserAgent());
        postRequest.setEntity(entity);
        Log.i(TAG, "Created file upload POST:" + contentServerUri);

        ListenableFuture<HttpResponse> responseFuture =
                requestExecutor.executeAuthenticatedRequest(httpClient, context, postRequest,
                        authScheme);

        Futures.addCallback(
                responseFuture,
                new FutureCallback<HttpResponse>() {
                    @Override
                    public void onSuccess(HttpResponse response) {
                        Log.i(TAG, "onSuccess:" + response.toString());
                        Log.i(TAG, "statusLine:" + response.getStatusLine());
                        Log.i(TAG, "statusCode:" + response.getStatusLine().getStatusCode());
                        Log.i(TAG, "contentLentgh:" + response.getEntity().getContentLength());
                        Log.i(TAG, "contentType:" + response.getEntity().getContentType());
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "onFailure", t);
                        throw new IllegalArgumentException(t);
                    }
                },
                executor);

        return Futures.transform(
                responseFuture,
                response -> {
                    try {
                        return consumeResponse(response);
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                },
                executor);
    }

    public String consumeResponse(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw new IllegalArgumentException(
                    "Server responded with error code " + statusCode + "!");
        }
        HttpEntity responseEntity = response.getEntity();

        if (responseEntity == null) {
            throw new IOException("Did not receive a response body.");
        }

        return readResponseData(responseEntity.getContent());
    }

    public String readResponseData(InputStream inputStream) throws IOException {
        Log.i(TAG, "readResponseData");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteStreams.copy(inputStream, data);

        data.flush();
        Log.i(TAG, "Parsed HTTP POST response: " + data.toString());

        return data.toString();
    }

    private String getUserAgent() {
        String buildId = Build.ID;
        String buildDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(Build.TIME));
        String buildVersion = Build.VERSION.RELEASE_OR_CODENAME;
        String deviceName = Build.DEVICE;
        String userAgent = String.format("%s %s %s %s %s %s %s",
                mCarrierName, buildId, buildDate, "Android", buildVersion,
                deviceName, THREE_GPP_GBA);
        Log.i(TAG, "UserAgent:" + userAgent);
        return userAgent;
    }
}
