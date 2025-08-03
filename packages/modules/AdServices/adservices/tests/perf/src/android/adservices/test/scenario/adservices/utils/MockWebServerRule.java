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

package android.adservices.test.scenario.adservices.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.net.Uri;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/** Instances of this class are not thread safe. */
public class MockWebServerRule implements TestRule {
    private static final int UNINITIALIZED = -1;
    private final InputStream mCertificateInputStream;
    private final char[] mKeyStorePassword;
    private int mPort = UNINITIALIZED;
    private MockWebServer mMockWebServer;

    private MockWebServerRule(InputStream inputStream, String keyStorePassword) {
        mCertificateInputStream = inputStream;
        mKeyStorePassword = keyStorePassword == null ? null : keyStorePassword.toCharArray();
    }

    public static MockWebServerRule forHttp() {
        return new MockWebServerRule(null, null);
    }

    /**
     * Builds an instance of the MockWebServerRule configured for HTTPS traffic.
     *
     * @param context The app context used to load the PKCS12 key store
     * @param assetName The name of the key store under the app assets folder
     * @param keyStorePassword The password of the keystore
     */
    public static MockWebServerRule forHttps(
            Context context, String assetName, String keyStorePassword) {
        try {
            return new MockWebServerRule(context.getAssets().open(assetName), keyStorePassword);
        } catch (IOException ioException) {
            throw new RuntimeException("Unable to initialize MockWebServerRule", ioException);
        }
    }

    /**
     * Builds an instance of the MockWebServerRule configured for HTTPS traffic.
     *
     * @param certificateInputStream An input stream to load the content of a PKCS12 key store
     * @param keyStorePassword The password of the keystore
     */
    public static MockWebServerRule forHttps(
            InputStream certificateInputStream, String keyStorePassword) {
        return new MockWebServerRule(certificateInputStream, keyStorePassword);
    }

    private boolean useHttps() {
        return Objects.nonNull(mCertificateInputStream);
    }

    public MockWebServer startMockWebServer(List<MockResponse> responses) throws Exception {
        if (mPort == UNINITIALIZED) {
            reserveServerListeningPort();
        }

        mMockWebServer = new MockWebServer();
        if (useHttps()) {
            mMockWebServer.useHttps(getTestingSslSocketFactory(), false);
        }
        for (MockResponse response : responses) {
            mMockWebServer.enqueue(response);
        }
        mMockWebServer.play(mPort);
        return mMockWebServer;
    }

    public MockWebServer startMockWebServer(Function<RecordedRequest, MockResponse> lambda)
            throws Exception {
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return lambda.apply(request);
                    }
                };
        return startMockWebServer(dispatcher);
    }

    public MockWebServer startMockWebServer(Dispatcher dispatcher) throws Exception {
        if (mPort == UNINITIALIZED) {
            reserveServerListeningPort();
        }

        mMockWebServer = new MockWebServer();
        if (useHttps()) {
            mMockWebServer.useHttps(getTestingSslSocketFactory(), false);
        }
        mMockWebServer.setDispatcher(dispatcher);

        mMockWebServer.play(mPort);
        return mMockWebServer;
    }

    public MockWebServer createMockWebServer() throws Exception {
        if (mPort == UNINITIALIZED) {
            reserveServerListeningPort();
        }

        mMockWebServer = new MockWebServer();
        if (useHttps()) {
            mMockWebServer.useHttps(getTestingSslSocketFactory(), false);
        }
        return mMockWebServer;
    }

    public MockWebServer startCreatedMockWebServer(Dispatcher dispatcher) throws Exception {
        if (mMockWebServer == null || mPort == UNINITIALIZED) {
            throw new IllegalStateException(
                    "MockWebServer is not created or the port is not reserved.");
        }
        mMockWebServer.setDispatcher(dispatcher);

        mMockWebServer.play(mPort);
        return mMockWebServer;
    }

    /**
     * @return the mock web server for this rull and {@code null} if it hasn't been started yet by
     *     calling {@link #startMockWebServer(List)}.
     */
    public MockWebServer getMockWebServer() {
        return mMockWebServer;
    }

    /** @return the base address the mock web server will be listening to when started. */
    public String getServerBaseAddress() {
        return String.format("%s://localhost:%d", useHttps() ? "https" : "http", mPort);
    }

    /**
     * This method is equivalent to {@link MockWebServer#getUrl(String)} but it can be used before
     * you prepare and start the server if you need to prepare responses that will reference the
     * same test server.
     *
     * @return an Uri to use to reach the given {@code @path} on the mock web server.
     */
    public Uri uriForPath(String path) {
        return Uri.parse(
                String.format(
                        "%s%s%s", getServerBaseAddress(), path.startsWith("/") ? "" : "/", path));
    }

    private void reserveServerListeningPort() throws IOException {
        ServerSocket serverSocket = new ServerSocket(38383);
        serverSocket.setReuseAddress(true);
        mPort = serverSocket.getLocalPort();
        serverSocket.close();
    }

    /**
     * Provides the ability to define a port before starting the mock web server. Otherwise, if the
     * port has already been initialized it will throw an {@link IllegalStateException}
     *
     * @param port the port to be configured
     * @throws IOException if port already in used
     */
    public void reserveServerListeningPort(int port) throws IOException {
        if (mPort != UNINITIALIZED) {
            throw new IllegalStateException("Port has already been initialized");
        }

        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        mPort = serverSocket.getLocalPort();
        serverSocket.close();
    }

    private SSLSocketFactory getTestingSslSocketFactory()
            throws GeneralSecurityException, IOException {
        final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(mCertificateInputStream, mKeyStorePassword);
        keyManagerFactory.init(keyStore, mKeyStorePassword);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext.getSocketFactory();
    }

    /**
     * A utility that validates that the mock web server got the expected traffic.
     *
     * @param mockWebServer server instance used for making requests
     * @param expectedRequestCount the number of requests expected to be received by the server
     * @param expectedRequests the list of URLs that should have been requested, in case of repeat
     *     requests the size of expectedRequests list could be less than the expectedRequestCount
     * @param requestMatcher A custom matcher that dictates if the request meets the criteria of
     *     being hit or not. This allows tests to do partial match of URLs in case of params or
     *     other sub path of URL.
     */
    public void verifyMockServerRequests(
            final MockWebServer mockWebServer,
            final int expectedRequestCount,
            final List<String> expectedRequests,
            final RequestMatcher<String> requestMatcher) {

        assertEquals(
                "Number of expected requests does not match actual request count",
                expectedRequestCount,
                mockWebServer.getRequestCount());

        // For parallel executions requests should be checked agnostic of order
        final Set<String> actualRequests = new HashSet<>();
        for (int i = 0; i < expectedRequestCount; i++) {
            try {
                actualRequests.add(mockWebServer.takeRequest().getPath());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertFalse(
                String.format(
                        "Expected requests cannot be empty, actual requests <%s>", actualRequests),
                expectedRequestCount != 0 && expectedRequests.isEmpty());

        for (String request : expectedRequests) {
            Assert.assertTrue(
                    String.format(
                            "Actual requests <%s> do not contain request <%s>",
                            actualRequests, request),
                    wasPathRequested(actualRequests, request, requestMatcher));
        }
    }

    private boolean wasPathRequested(
            final Set<String> actualRequests,
            final String request,
            final RequestMatcher<String> requestMatcher) {
        for (String actualRequest : actualRequests) {
            // Passing a custom comparator allows tests to do partial match of URLs in case of
            // params or other sub path of URL
            if (requestMatcher.wasRequestMade(actualRequest, request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                reserveServerListeningPort();
                try {
                    base.evaluate();
                } finally {
                    if (mMockWebServer != null) {
                        mMockWebServer.shutdown();
                    }
                }
            }
        };
    }

    public interface RequestMatcher<T> {
        boolean wasRequestMade(T actualRequest, T expectedRequest);
    }
}
