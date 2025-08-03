/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.phone.callcomposer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.Part;

import com.google.common.net.MediaType;

import gov.nist.javax.sip.header.WWWAuthenticate;

import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class CallComposerPictureTransfer {
    private static final String TAG = CallComposerPictureTransfer.class.getSimpleName();
    private static final int HTTP_TIMEOUT_MILLIS = 20000;
    private static final int DEFAULT_BACKOFF_MILLIS = 1000;
    private static final String THREE_GPP_GBA = "3gpp-gba";

    private static final int ERROR_UNKNOWN = 0;
    private static final int ERROR_HTTP_TIMEOUT = 1;
    private static final int ERROR_NO_AUTH_REQUIRED = 2;
    private static final int ERROR_FORBIDDEN = 3;

    public interface Factory {
        default CallComposerPictureTransfer create(Context context, int subscriptionId, String url,
                ExecutorService executorService) {
            return new CallComposerPictureTransfer(context, subscriptionId, url, executorService);
        }
    }

    public interface PictureCallback {
        default void onError(@TelephonyManager.CallComposerException.CallComposerError int error) {}
        default void onRetryNeeded(boolean credentialRefresh, long backoffMillis) {}
        default void onUploadSuccessful(String serverUrl) {}
        default void onDownloadSuccessful(ImageData data) {}
    }

    private static class NetworkAccessException extends RuntimeException {
        final int errorCode;

        NetworkAccessException(int errorCode) {
            this.errorCode = errorCode;
        }
    }

    private final Context mContext;
    private final int mSubscriptionId;
    private final String mUrl;
    private final ExecutorService mExecutorService;

    private PictureCallback mCallback;

    private CallComposerPictureTransfer(Context context, int subscriptionId, String url,
            ExecutorService executorService) {
        mContext = context;
        mSubscriptionId = subscriptionId;
        mExecutorService = executorService;
        mUrl = url;
    }

    @VisibleForTesting
    public void setCallback(PictureCallback callback) {
        mCallback = callback;
    }

    public void uploadPicture(ImageData image,
            GbaCredentialsSupplier credentialsSupplier) {
        CompletableFuture<Network> networkFuture = getNetworkForCallComposer();
        CompletableFuture<WWWAuthenticate> authorizationHeaderFuture = networkFuture
                .thenApplyAsync((network) -> prepareInitialPost(network, mUrl), mExecutorService)
                .thenComposeAsync(this::obtainAuthenticateHeader, mExecutorService)
                .thenApplyAsync(DigestAuthUtils::parseAuthenticateHeader);
        CompletableFuture<GbaCredentials> credsFuture = authorizationHeaderFuture
                .thenComposeAsync((header) ->
                        credentialsSupplier.getCredentials(header.getRealm(), mExecutorService),
                        mExecutorService);

        CompletableFuture<String> authorizationFuture =
                authorizationHeaderFuture.thenCombineAsync(credsFuture,
                        (authHeader, credentials) ->
                                DigestAuthUtils.generateAuthorizationHeader(
                                        authHeader, credentials, "POST", mUrl),
                        mExecutorService)
                        .whenCompleteAsync(
                                (authorization, error) -> handleExceptionalCompletion(error),
                                mExecutorService);

        CompletableFuture<String> networkUrlFuture =
                networkFuture.thenCombineAsync(authorizationFuture,
                        (network, auth) -> sendActualImageUpload(network, auth, image),
                        mExecutorService);
        networkUrlFuture.thenAcceptAsync((result) -> {
            if (result != null) mCallback.onUploadSuccessful(result);
        }, mExecutorService).exceptionally((ex) -> {
            logException("Exception uploading image" , ex);
            return null;
        });
    }

    public void downloadPicture(GbaCredentialsSupplier credentialsSupplier) {
        CompletableFuture<Network> networkFuture = getNetworkForCallComposer();
        CompletableFuture<HttpURLConnection> getConnectionFuture =
                networkFuture.thenApplyAsync((network) ->
                        prepareImageDownloadRequest(network, mUrl), mExecutorService);

        CompletableFuture<ImageData> immediatelyDownloadableImage = getConnectionFuture
                .thenComposeAsync((conn) -> {
                    try {
                        if (conn.getResponseCode() != 200) {
                            return CompletableFuture.completedFuture(null);
                        }
                    } catch (IOException e) {
                        logException("IOException obtaining return code: ", e);
                        throw new NetworkAccessException(ERROR_HTTP_TIMEOUT);
                    }
                    return CompletableFuture.completedFuture(downloadImageFromConnection(conn));
                }, mExecutorService);

        CompletableFuture<ImageData> authRequiredImage = getConnectionFuture
                .thenComposeAsync((conn) -> {
                    try {
                        if (conn.getResponseCode() == 200) {
                            // handled by above case
                            return CompletableFuture.completedFuture(null);
                        }
                    } catch (IOException e) {
                        logException("IOException obtaining return code: ", e);
                        throw new NetworkAccessException(ERROR_HTTP_TIMEOUT);
                    }
                    CompletableFuture<WWWAuthenticate> authenticateHeaderFuture =
                            obtainAuthenticateHeader(conn)
                                    .thenApply(DigestAuthUtils::parseAuthenticateHeader);
                    CompletableFuture<GbaCredentials> credsFuture = authenticateHeaderFuture
                            .thenComposeAsync((header) ->
                                    credentialsSupplier.getCredentials(header.getRealm(),
                                            mExecutorService), mExecutorService);

                    CompletableFuture<String> authorizationFuture = authenticateHeaderFuture
                            .thenCombineAsync(credsFuture, (authHeader, credentials) ->
                                    DigestAuthUtils.generateAuthorizationHeader(
                                            authHeader, credentials, "GET", mUrl),
                                    mExecutorService)
                            .whenCompleteAsync((authorization, error) ->
                                    handleExceptionalCompletion(error), mExecutorService);

                    return networkFuture.thenCombineAsync(authorizationFuture,
                            this::downloadImageWithAuth, mExecutorService);
                }, mExecutorService);

        CompletableFuture.allOf(immediatelyDownloadableImage, authRequiredImage).thenRun(() -> {
            ImageData fromImmediate = immediatelyDownloadableImage.getNow(null);
            ImageData fromAuth = authRequiredImage.getNow(null);
            // If both of these are null, that means an error happened somewhere in the chain.
            // in that case, the error has already been transmitted to the callback, so ignore it.
            if (fromAuth == null && fromImmediate == null) {
                Log.w(TAG, "No result from download -- error happened sometime earlier");
            }
            if (fromAuth != null) mCallback.onDownloadSuccessful(fromAuth);
            mCallback.onDownloadSuccessful(fromImmediate);
        }).exceptionally((ex) -> {
            logException("Exception downloading image" , ex);
            return null;
        });
    }

    private CompletableFuture<Network> getNetworkForCallComposer() {
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest pictureNetworkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        CompletableFuture<Network> resultFuture = new CompletableFuture<>();
        connectivityManager.requestNetwork(pictureNetworkRequest,
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        resultFuture.complete(network);
                    }
                });
        return resultFuture;
    }

    private HttpURLConnection prepareInitialPost(Network network, String uploadUrl) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) network.openConnection(new URL(uploadUrl));
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(HTTP_TIMEOUT_MILLIS);
            connection.setReadTimeout(HTTP_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", getUserAgent());
            return connection;
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL: " + uploadUrl);
            throw new RuntimeException(e);
        } catch (IOException e) {
            logException("IOException opening network: ", e);
            throw new RuntimeException(e);
        }
    }

    private HttpURLConnection prepareImageDownloadRequest(Network network, String imageUrl) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) network.openConnection(new URL(imageUrl));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(HTTP_TIMEOUT_MILLIS);
            connection.setReadTimeout(HTTP_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", getUserAgent());
            return connection;
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL: " + imageUrl);
            throw new RuntimeException(e);
        } catch (IOException e) {
            logException("IOException opening network: ", e);
            throw new RuntimeException(e);
        }
    }

    // Attempts to connect via the supplied connection, expecting a HTTP 401 in response. Throws
    // an IOException if the connection times out.
    // After the response is received, returns the WWW-Authenticate header in the following form:
    // "WWW-Authenticate:<method> <params>"
    private CompletableFuture<String> obtainAuthenticateHeader(
            HttpURLConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            int responseCode;
            try {
                responseCode = connection.getResponseCode();
            } catch (IOException e) {
                logException("IOException obtaining auth header: ", e);
                throw new NetworkAccessException(ERROR_HTTP_TIMEOUT);
            }
            if (responseCode == 204) {
                throw new NetworkAccessException(ERROR_NO_AUTH_REQUIRED);
            } else if (responseCode == 403) {
                throw new NetworkAccessException(ERROR_FORBIDDEN);
            } else if (responseCode != 401) {
                Log.w(TAG, "Received unexpected response in auth request, code= "
                        + responseCode);
                throw new NetworkAccessException(ERROR_UNKNOWN);
            }

            return connection.getHeaderField(DigestAuthUtils.WWW_AUTHENTICATE);
        }, mExecutorService);
    }

    private ImageData downloadImageWithAuth(Network network, String authorization) {
        HttpURLConnection connection = prepareImageDownloadRequest(network, mUrl);
        connection.addRequestProperty("Authorization", authorization);
        return downloadImageFromConnection(connection);
    }

    private ImageData downloadImageFromConnection(HttpURLConnection conn) {
        try {
            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "Got response code " + conn.getResponseCode() + " when trying"
                        + " to download image");
                if (conn.getResponseCode() == 401) {
                    Log.i(TAG, "Got 401 even with auth -- key refresh needed?");
                    mCallback.onRetryNeeded(true, 0);
                }
                return null;
            }
        } catch (IOException e) {
            logException("IOException obtaining return code: ", e);
            throw new NetworkAccessException(ERROR_HTTP_TIMEOUT);
        }

        String contentType = conn.getContentType();
        ByteArrayOutputStream imageDataOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int numRead;
        try {
            InputStream is = conn.getInputStream();
            while (true) {
                numRead = is.read(buffer);
                if (numRead < 0) break;
                imageDataOut.write(buffer, 0, numRead);
            }
        } catch (IOException e) {
            logException("IOException reading from image body: ", e);
            return null;
        }

        return new ImageData(imageDataOut.toByteArray(), contentType, null);
    }

    private void handleExceptionalCompletion(Throwable error) {
        if (error != null) {
            if (error.getCause() instanceof NetworkAccessException) {
                int code = ((NetworkAccessException) error.getCause()).errorCode;
                if (code == ERROR_UNKNOWN || code == ERROR_HTTP_TIMEOUT) {
                    scheduleRetry();
                } else {
                    int failureCode;
                    if (code == ERROR_FORBIDDEN) {
                        failureCode = TelephonyManager.CallComposerException
                                .ERROR_AUTHENTICATION_FAILED;
                    } else {
                        failureCode = TelephonyManager.CallComposerException
                                .ERROR_UNKNOWN;
                    }
                    deliverFailure(failureCode);
                }
            } else {
                deliverFailure(TelephonyManager.CallComposerException.ERROR_UNKNOWN);
            }
        }
    }

    private void scheduleRetry() {
        mCallback.onRetryNeeded(false, DEFAULT_BACKOFF_MILLIS);
    }

    private void deliverFailure(int code) {
        mCallback.onError(code);
    }

    private static Part makeUploadPart(String name, String contentType, String filename,
            byte[] data) {
        return new Part() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public String getCharSet() {
                return null;
            }

            @Override
            public String getTransferEncoding() {
                return null;
            }

            @Override
            public void sendDispositionHeader(OutputStream out) throws IOException {
                super.sendDispositionHeader(out);
                if (filename != null) {
                    String fileNameSuffix = "; filename=\"" + filename + "\"";
                    out.write(fileNameSuffix.getBytes());
                }
            }

            @Override
            protected void sendData(OutputStream out) throws IOException {
                out.write(data);
            }

            @Override
            protected long lengthOfData() throws IOException {
                return data.length;
            }
        };
    }

    private String sendActualImageUpload(Network network, String authHeader, ImageData image) {
        Part transactionIdPart = makeUploadPart("tid", "text/plain",
                null, image.getId().getBytes());
        Part imageDataPart = makeUploadPart("File", image.getMimeType(),
                image.getId(), image.getImageBytes());

        MultipartEntity multipartEntity =
                new MultipartEntity(new Part[] {transactionIdPart, imageDataPart});

        HttpURLConnection connection = prepareInitialPost(network, mUrl);
        connection.setDoOutput(true);
        connection.addRequestProperty("Authorization", authHeader);
        connection.addRequestProperty("Content-Length",
                String.valueOf(multipartEntity.getContentLength()));
        connection.addRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
        connection.addRequestProperty("Accept-Encoding", "*");

        try (OutputStream requestBodyOut = connection.getOutputStream()) {
            multipartEntity.writeTo(requestBodyOut);
        } catch (IOException e) {
            logException("IOException making request to upload image: ", e);
            throw new RuntimeException(e);
        }

        try {
            int response = connection.getResponseCode();
            Log.i(TAG, "Received response code: " + response
                    + ", message=" + connection.getResponseMessage());
            if (response == 401 || response == 403) {
                deliverFailure(TelephonyManager.CallComposerException.ERROR_AUTHENTICATION_FAILED);
                return null;
            }
            if (response == 503) {
                // TODO: implement parsing of retry-after and schedule a retry with that time
                scheduleRetry();
                return null;
            }
            if (response != 200) {
                scheduleRetry();
                return null;
            }
            String responseBody = readResponseBody(connection);
            String parsedUrl = parseImageUploadResponseXmlForUrl(responseBody);
            Log.i(TAG, "Parsed URL as upload result: " + parsedUrl);
            return parsedUrl;
        } catch (IOException e) {
            logException("IOException getting response to image upload: ", e);
            deliverFailure(TelephonyManager.CallComposerException.ERROR_UNKNOWN);
            return null;
        }
    }

    private static String parseImageUploadResponseXmlForUrl(String xmlData) {
        NamespaceContext ns = new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return "urn:gsma:params:xml:ns:rcs:rcs:fthttp";
            }

            public String getPrefix(String uri) {
                throw new UnsupportedOperationException();
            }

            public Iterator getPrefixes(String uri) {
                throw new UnsupportedOperationException();
            }
        };

        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(ns);
        StringReader reader = new StringReader(xmlData);
        try {
            return (String) xPath.evaluate("/a:file/a:file-info[@type='file']/a:data/@url",
                    new InputSource(reader), XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            logException("Error parsing response XML:", e);
            return null;
        }
    }

    private static String readResponseBody(HttpURLConnection connection) {
        Charset charset = MediaType.parse(connection.getContentType())
                .charset().or(Charset.defaultCharset());
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = connection.getInputStream()) {
            String outLine;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
            while ((outLine = reader.readLine()) != null) {
                sb.append(outLine);
            }
        } catch (IOException e) {
            logException("IOException reading request body: ", e);
            return null;
        }
        return sb.toString();
    }

    private String getUserAgent() {
        String carrierName = mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubscriptionId)
                .getSimOperatorName();
        String buildId = Build.ID;
        String buildDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(Build.TIME));
        String buildVersion = Build.VERSION.RELEASE_OR_CODENAME;
        String deviceName = Build.DEVICE;
        return String.format("%s %s %s %s %s %s %s",
                carrierName, buildId, buildDate, "Android", buildVersion,
                deviceName, THREE_GPP_GBA);

    }

    private static void logException(String message, Throwable e) {
        StringWriter log = new StringWriter();
        log.append(message);
        log.append(":\n");
        log.append(e.getMessage());
        PrintWriter pw = new PrintWriter(log);
        e.printStackTrace(pw);
        Log.e(TAG, log.toString());
    }
}
