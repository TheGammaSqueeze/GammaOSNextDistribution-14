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

package android.mediapc.cts;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The following class connects to HTTP server, posts requests and returns the response
 */
public final class Post {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_TRIES = 5;
    private static final String TAG = "WVPostRequest";

    public static final class Response {

        public final int code;
        public final byte[] body;

        public Response(int code, byte[] body) {
            this.code = code;
            this.body = body;
        }
    }

    private static final byte[] EMPTY_BODY = new byte[0];

    private final String mUrl;
    private final byte[] mData;
    private final boolean mExpectOutput;

    private final Map<String, String> mProperties = new HashMap<>();

    public Post(String url, byte[] data) {

        mUrl = url;

        mData = data == null ?
                EMPTY_BODY :
                Arrays.copyOf(data, data.length);

        mExpectOutput = data != null;
    }

    public void setProperty(String key, String value) {
        mProperties.put(key, value);
    }

    public Response send() throws IOException, InterruptedException {

        int tries = 1;
        boolean needRetry = true;
        Response response = null;

        while (needRetry) {
            HttpURLConnection connection = null;
            needRetry = false;

            try {
                connection = (HttpURLConnection) new URL(mUrl).openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(mExpectOutput);
                connection.setDoInput(true);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setChunkedStreamingMode(0);

                for (final Map.Entry<String, String> property : mProperties.entrySet()) {
                    connection.setRequestProperty(
                            property.getKey(),
                            property.getValue());
                }

                try (BufferedOutputStream out =
                        new BufferedOutputStream(connection.getOutputStream())) {
                    out.write(mData);
                }

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                    try (BufferedInputStream inputStream = connection.getResponseCode() < 400
                            ? new BufferedInputStream(connection.getInputStream())
                            : new BufferedInputStream(connection.getErrorStream())) {
                        connectStreams(inputStream, outputStream);
                    }

                    response =  new Response(
                            connection.getResponseCode(),
                            outputStream.toByteArray());
                }

            } catch (SocketTimeoutException | FileNotFoundException ex) {

                if (tries == MAX_TRIES) {
                    Log.e(TAG, "Aborting after receiving an Exception connecting to server on try "
                            + tries);
                    throw ex;
                }

                Log.w(TAG, "Retrying after receiving an Exception connecting to server on try "
                        + tries);
                tries++;
                needRetry = true;

                // Let the gap between retries grow slightly with each retry.
                Thread.sleep(tries * 500L);

            } catch (Exception ex) {

                Log.e(TAG, "Unexpected failure in response / request.", ex);
                throw ex;

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return response;
    }

    private static void connectStreams(BufferedInputStream in, ByteArrayOutputStream out)
            throws IOException {

        final byte[] scratch = new byte[1024];

        int read; /* declare this here so that the for loop can be aligned */

        while ((read = in.read(scratch)) != -1) {
            out.write(scratch, 0, read);
        }
    }
}
