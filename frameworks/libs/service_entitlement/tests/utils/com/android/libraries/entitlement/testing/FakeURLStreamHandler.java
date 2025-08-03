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

package com.android.libraries.entitlement.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;

/**
 * A {@link URLStreamHandlerFactory} to return faked {@link URLConnection}, as {@link URL} is a
 * final class and {@link URL#openConnection} cannot be mocked using mockito.
 */
public class FakeURLStreamHandler extends URLStreamHandler implements URLStreamHandlerFactory {
    private Map<String, FakeResponse> mResponses = ImmutableMap.of();
    private List<FakeHttpsURLConnection> mConnections = new ArrayList<>();

    private static final String ACCESS_TOKEN = "8dGozfI6%2FEaSsE7LaTfJKwdy";
    private static final String LOCATION = "Location";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String RETRY_AFTER = "Retry-After";

    @Override
    public URLConnection openConnection(URL u) {
        FakeHttpsURLConnection connection =
                new FakeHttpsURLConnection(u, mResponses.get(u.toString()));
        mConnections.add(connection);
        return connection;
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return this;
    }

    /**
     * Prepares canned responses. Must be called before using this handler to open any {@link
     * URLConnection}.
     */
    public FakeURLStreamHandler stubResponse(Map<String, FakeResponse> response) {
        mResponses = response;
        mConnections = new ArrayList<>();
        return this;
    }

    /** Returns {@link URLConnection}s opened by this handler since last {@link #stubResponse}. */
    public ImmutableList<FakeHttpsURLConnection> getConnections() {
        return ImmutableList.copyOf(mConnections);
    }

    /** Faked {@link HttpsURLConnection} to avoid making any network connection. */
    public static class FakeHttpsURLConnection extends HttpsURLConnection {
        private final FakeResponse mResponse;
        private final ByteArrayOutputStream mOutputStream = new ByteArrayOutputStream();

        public FakeHttpsURLConnection(URL url, FakeResponse response) {
            super(url);
            mResponse = response;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (mResponse.hasException() && mResponse.responseBody().length == 0) {
                throw new IOException("stub exception");
            }
            return new ByteArrayInputStream(mResponse.responseBody());
        }

        @Override
        public OutputStream getOutputStream() {
            return mOutputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream("stub error".getBytes(UTF_8));
        }

        public byte[] getBytesWrittenToOutputStream() {
            return mOutputStream.toByteArray();
        }

        @Override
        public int getResponseCode() throws IOException {
            if (mResponse.hasException() && mResponse.responseCode() == 0) {
                throw new IOException("stub exception");
            }
            return mResponse.responseCode();
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            List<String> locationList = ImmutableList.of("access_token=" + ACCESS_TOKEN);
            return ImmutableMap.of("Location", locationList);
        }

        @Override
        public String getHeaderField(String name) {
            switch (name) {
                case LOCATION:
                    return "Location: " + mResponse.responseLocation();
                case CONTENT_TYPE:
                    return mResponse.contentType();
                case RETRY_AFTER:
                    return mResponse.retryAfter();
                default:
                    return null;
            }
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public String getCipherSuite() {
            return null;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return null;
        }

        @Override
        public Certificate[] getServerCertificates() {
            return null;
        }
    }

    @AutoValue
    public abstract static class FakeResponse {
        public abstract int responseCode();

        @Nullable
        public abstract String responseLocation();

        @SuppressWarnings("mutable") // For test only
        public abstract byte[] responseBody();

        public abstract String contentType();

        public abstract String retryAfter();

        abstract boolean hasException();

        public static Builder builder() {
            return new AutoValue_FakeURLStreamHandler_FakeResponse.Builder()
                    .setResponseBody(new byte[]{})
                    .setContentType("")
                    .setResponseCode(0)
                    .setResponseLocation("")
                    .setRetryAfter("")
                    .setHasException(false);
        }

        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder setResponseCode(int value);

            public abstract Builder setResponseLocation(String value);

            public abstract Builder setResponseBody(byte[] value);

            public abstract Builder setContentType(String contentType);

            public abstract Builder setRetryAfter(String retryAfter);

            public abstract Builder setHasException(boolean hasException);

            public abstract FakeResponse build();
        }
    }
}
