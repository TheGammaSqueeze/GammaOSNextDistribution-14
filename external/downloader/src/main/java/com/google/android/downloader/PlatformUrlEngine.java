// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.downloader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * {@link UrlEngine} implementation that uses Java's built-in network stack (i.e. {@link
 * HttpURLConnection}).
 *
 * <p>Note that internally this engine allocates a 512kb direct byte buffer per request to transfer
 * bytes around. If memory usage is a concern, then the number of concurrent requests should be
 * limited.
 */
public final class PlatformUrlEngine implements UrlEngine {
  @VisibleForTesting static final int BUFFER_SIZE_BYTES = 512 * 1024;
  private static final ImmutableSet<String> SCHEMES = ImmutableSet.of("http", "https", "file");

  private final ListeningExecutorService requestExecutorService;
  private final int connectTimeoutsMs;
  private final int readTimeoutsMs;

  public PlatformUrlEngine(
      ListeningExecutorService requestExecutorService, int connectTimeoutMs, int readTimeoutMs) {
    this.requestExecutorService = requestExecutorService;
    this.connectTimeoutsMs = connectTimeoutMs;
    this.readTimeoutsMs = readTimeoutMs;
  }

  @Override
  public UrlRequest.Builder createRequest(String url) {
    return new PlatformUrlRequestBuilder(url);
  }

  @Override
  public Set<String> supportedSchemes() {
    return SCHEMES;
  }

  /** Implementation of {@link UrlRequest.Builder} for the built-in network stack. */
  class PlatformUrlRequestBuilder implements UrlRequest.Builder {
    private final String url;
    private final ImmutableMultimap.Builder<String, String> headers =
        new ImmutableMultimap.Builder<>();

    PlatformUrlRequestBuilder(String url) {
      this.url = url;
    }

    @Override
    public UrlRequest.Builder addHeader(String key, String value) {
      headers.put(key, value);
      return this;
    }

    @Override
    public UrlRequest build() {
      return new PlatformUrlRequest(url, headers.build());
    }
  }

  /**
   * Implementation of {@link UrlRequest} for the platform network stack.
   *
   * <p>The design of this class has some nuance in its design. Because HttpURLConnection isn't
   * thread-safe, this implementation holds on to a single thread for the entire duration of an
   * {@link HttpURLConnection} lifecycle - from connect until disconnect.
   */
  class PlatformUrlRequest implements UrlRequest {
    private final String url;
    private final ImmutableMultimap<String, String> headers;

    PlatformUrlRequest(String url, ImmutableMultimap<String, String> headers) {
      this.url = url;
      this.headers = headers;
    }

    @Override
    public ListenableFuture<UrlResponse> send() {
      return requestExecutorService.submit(
          () -> {
            URL url;

            try {
              url = new URL(this.url);
            } catch (MalformedURLException e) {
              throw new RequestException(e);
            }

            throwIfCancelled();

            URLConnection urlConnection = null;

            try {
              urlConnection = url.openConnection();
              urlConnection.setConnectTimeout(connectTimeoutsMs);
              urlConnection.setReadTimeout(readTimeoutsMs);

              for (String key : headers.keySet()) {
                for (String value : headers.get(key)) {
                  urlConnection.addRequestProperty(key, value);
                }
              }

              throwIfCancelled();

              urlConnection.connect();

              throwIfCancelled();

              int httpResponseCode = getResponseCode(urlConnection);

              throwIfCancelled();

              // We've successfully connected, so resolve the request to let client code decide
              // what to do next.
              PlatformUrlResponse response =
                  new PlatformUrlResponse(urlConnection, httpResponseCode);
              urlConnection = null;
              return response;
            } catch (RequestException e) {
              throw e;
            } catch (IOException e) {
              throw new RequestException(e);
            } finally {
              maybeDisconnect(urlConnection);
            }
          });
    }
  }

  /** Implementation of {@link UrlResponse} for the platform network stack. */
  class PlatformUrlResponse implements UrlResponse {
    @GuardedBy("this")
    @Nullable
    private URLConnection urlConnection;

    private final int httpResponseCode;
    private final Map<String, List<String>> responseHeaders;

    PlatformUrlResponse(URLConnection urlConnection, int httpResponseCode) {
      this.urlConnection = urlConnection;
      this.httpResponseCode = httpResponseCode;
      this.responseHeaders = urlConnection.getHeaderFields();
    }

    @Nullable
    private synchronized URLConnection consumeConnection() {
      URLConnection urlConnection = this.urlConnection;
      this.urlConnection = null;
      return urlConnection;
    }

    @Override
    public int getResponseCode() {
      return httpResponseCode;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return responseHeaders;
    }

    @Override
    public ListenableFuture<Long> readResponseBody(WritableByteChannel destinationChannel) {
      IOUtil.validateChannel(destinationChannel);
      return requestExecutorService.submit(
          () -> {
            URLConnection urlConnection = consumeConnection();
            if (urlConnection == null) {
              throw new RequestException("URLConnection already closed");
            }

            try (ReadableByteChannel sourceChannel =
                Channels.newChannel(urlConnection.getInputStream())) {
              ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES);
              long written = 0;

              while (sourceChannel.read(buffer) != -1) {
                throwIfCancelled();
                buffer.flip();
                written += IOUtil.blockingWrite(buffer, destinationChannel);
                buffer.clear();
                throwIfCancelled();
              }

              return written;
            } catch (IOException e) {
              throw new RequestException(e);
            } finally {
              maybeDisconnect(urlConnection);
            }
          });
    }

    @Override
    public void close() throws IOException {
      URLConnection urlConnection = consumeConnection();
      if (urlConnection != null) {
        // At this point, both HttpURLConnection.getResponseCode and URLConnection.getHeaderFields
        // have been called, so the InputStream has already been implicitly created, and the call
        // to URLConnection.getInputStream will be cheap. Normally calling it can be pretty heavy-
        // weight and thus likely shouldn't happen in the close() method.
        urlConnection.getInputStream().close();
        maybeDisconnect(urlConnection);
      }
    }
  }

  private static void throwIfCancelled() throws RequestException {
    // ListeningExecutorService turns Future.cancel() into Thread.interrupt()
    if (Thread.interrupted()) {
      throw new RequestException("Request canceled");
    }
  }

  private static void maybeDisconnect(@Nullable URLConnection urlConnection) {
    if (urlConnection == null) {
      return;
    }

    if (urlConnection instanceof HttpURLConnection) {
      HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
      httpUrlConnection.disconnect();
    }
  }

  private static int getResponseCode(URLConnection urlConnection) throws IOException {
    if (urlConnection instanceof HttpURLConnection) {
      HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
      InputStream inputStream = getInputStream(httpUrlConnection);
      int httpResponseCode = httpUrlConnection.getResponseCode();
      if (httpResponseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
        String responseMessage = httpUrlConnection.getResponseMessage();
        Map<String, List<String>> responseHeaders = httpUrlConnection.getHeaderFields();
        if (inputStream != null) {
          inputStream.close();
        }
        throw new RequestException(
            ErrorDetails.createFromHttpErrorResponse(
                httpResponseCode, responseHeaders, responseMessage));
      }
      return httpResponseCode;
    } else {
      // Note: This happens for URLConnections that aren't over HTTP, e.g. to
      // file URLs instead (e.g. sun.net.www.protocol.file.FileURLConnection). The
      // code doesn't directly check for these classes because they aren't officially
      // part of the JDK.
      return HttpURLConnection.HTTP_OK;
    }
  }

  @Nullable
  private static InputStream getInputStream(HttpURLConnection httpURLConnection) {
    try {
      return httpURLConnection.getInputStream();
    } catch (IOException e) {
      return null;
    }
  }
}
