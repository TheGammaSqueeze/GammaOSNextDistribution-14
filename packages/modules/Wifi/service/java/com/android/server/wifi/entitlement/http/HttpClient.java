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

package com.android.server.wifi.entitlement.http;

import static com.android.libraries.entitlement.ServiceEntitlementException.ERROR_HTTP_STATUS_NOT_SUCCESS;
import static com.android.libraries.entitlement.ServiceEntitlementException.ERROR_MALFORMED_HTTP_RESPONSE;
import static com.android.libraries.entitlement.ServiceEntitlementException.ERROR_SERVER_NOT_CONNECTABLE;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.NonNull;
import android.net.Network;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpConstants.ContentType;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

/** Implement the HTTP request method according to TS.43 specification. */
public class HttpClient {
    private static final String POST = "POST";
    @VisibleForTesting
    static final String GZIP = "gzip";

    /**
     * Starts a {@link HttpRequest} and gets the {@link HttpResponse}
     * @throws ServiceEntitlementException if there is any connection error.
     */
    @WorkerThread
    public static HttpResponse request(@NonNull HttpRequest request)
            throws ServiceEntitlementException {
        HttpsURLConnection connection = createConnection(request);
        try {
            if (POST.equals(request.requestMethod())) {
                try (OutputStream out = new DataOutputStream(connection.getOutputStream())) {
                    JSONObject postDataJsonObject = request.postData();
                    String postData;
                    // Some servers support JsonObject format of post data. But some servers support
                    //JsonArray format. If JsonObject post data is empty, we will use the JsonArray
                    //post data. Otherwise, use JsonObject post data.
                    if (postDataJsonObject.length() == 0) {
                        postData = request.postDataJsonArray().toString();
                    } else {
                        postData = postDataJsonObject.toString();
                    }
                    // Android JSON toString() escapes forward-slash with back-slash. It's not
                    // supported by some vendor and not mandatory in JSON spec. Undo escaping.
                    postData = postData.replace("\\/", "/");
                    ImmutableList<String> list = request.requestProperties().get(CONTENT_ENCODING);
                    if ((list.size() > 0) && GZIP.equalsIgnoreCase(list.get(0))) {
                        out.write(toGzipBytes(postData));
                    } else {
                        out.write(postData.getBytes(UTF_8));
                    }
                }
            }
            connection.connect(); // This is to trigger SocketTimeoutException early
            return getHttpResponse(connection);
        } catch (IOException ioe) {
            String error;
            try (InputStream in = connection.getErrorStream()) {
                error = StreamUtils.inputStreamToStringSafe(in);
            } catch (IOException e) {
                error = "";
            }
            throw new ServiceEntitlementException(
                    ERROR_HTTP_STATUS_NOT_SUCCESS,
                    "Connection error stream: " + error  + " IOException: " + ioe, ioe);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpsURLConnection createConnection(@NonNull HttpRequest request)
            throws ServiceEntitlementException {
        try {
            HttpsURLConnection connection;
            final URL url = new URL(request.url());
            final Network network = request.network();
            if (network == null) {
                connection = (HttpsURLConnection) url.openConnection();
            } else {
                connection = (HttpsURLConnection) network.openConnection(url);
            }

            // add HTTP headers
            for (Map.Entry<String, String> entry : request.requestProperties().entries()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }

            // set parameters
            connection.setRequestMethod(request.requestMethod());
            connection.setConnectTimeout((int) SECONDS.toMillis(request.timeoutInSec()));
            connection.setReadTimeout((int) SECONDS.toMillis(request.timeoutInSec()));
            if (POST.equals(request.requestMethod())) {
                connection.setDoOutput(true);
            }
            return connection;
        } catch (IOException ioe) {
            throw new ServiceEntitlementException(
                    ERROR_SERVER_NOT_CONNECTABLE, "Configure connection failed!", ioe);
        }
    }

    @NonNull
    private static HttpResponse getHttpResponse(@NonNull HttpsURLConnection connection)
            throws ServiceEntitlementException {
        HttpResponse.Builder responseBuilder = HttpResponse.builder();
        responseBuilder.setContentType(getContentType(connection));
        try {
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new ServiceEntitlementException(ERROR_HTTP_STATUS_NOT_SUCCESS, responseCode,
                        connection.getHeaderField(HttpHeaders.RETRY_AFTER),
                        "Invalid connection response");
            }
            responseBuilder.setResponseCode(responseCode);
            responseBuilder.setResponseMessage(nullToEmpty(connection.getResponseMessage()));
        } catch (IOException e) {
            throw new ServiceEntitlementException(
                    ERROR_HTTP_STATUS_NOT_SUCCESS, "Read response code failed!", e);
        }
        try {
            responseBuilder.setBody(readResponse(connection));
        } catch (IOException e) {
            throw new ServiceEntitlementException(
                    ERROR_MALFORMED_HTTP_RESPONSE, "Read response body/message failed!", e);
        }
        return responseBuilder.build();
    }

    @NonNull
    private static String readResponse(@NonNull URLConnection connection) throws IOException {
        try (InputStream in = connection.getInputStream()) {
            // By default, this implementation of HttpsURLConnection requests that servers use gzip
            // compression, and it automatically decompresses the data for callers of
            // URLConnection.getInputStream(). In case the caller sets the Accept-Encoding request
            // header explicitly to disable automatic decompression, the InputStream is decompressed
            // here.
            if (GZIP.equalsIgnoreCase(connection.getHeaderField(CONTENT_ENCODING))) {
                return StreamUtils.inputStreamToGunzipString(in);
            }
            return StreamUtils.inputStreamToStringSafe(in);
        }
    }

    private static int getContentType(@NonNull URLConnection connection) {
        String contentType = connection.getHeaderField(ContentType.NAME);
        if (TextUtils.isEmpty(contentType)) {
            return ContentType.UNKNOWN;
        }
        return HttpConstants.getContentType(contentType);
    }

    /**
     * Converts the input string to UTF_8 byte array and gzip it.
     */
    @VisibleForTesting
    @NonNull
    public static byte[] toGzipBytes(@NonNull String data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            gzip.write(data.getBytes(UTF_8));
        }
        return outputStream.toByteArray();
    }
}
