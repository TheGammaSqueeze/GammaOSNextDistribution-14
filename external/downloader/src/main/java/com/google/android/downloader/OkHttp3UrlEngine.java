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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;
import okio.Sink;

/** {@link UrlEngine} implementation that uses OkHttp3 for network connectivity. */
public class OkHttp3UrlEngine implements UrlEngine {
  private static final ImmutableSet<String> HTTP_SCHEMES = ImmutableSet.of("http", "https");

  private final OkHttpClient client;
  private final ListeningExecutorService transferExecutorService;

  /**
   * Constructs an instance of the OkHttp URL engine, for the given OkHttpClient instance.
   *
   * <p>Note that due to how OkHttp is implemented, reads from the network are blocking operations,
   * and thus threads in the provided {@link ListeningExecutorService} can be tied up for long
   * periods of time waiting on network responses. To mitigate, set {@link
   * OkHttpClient.Builder#readTimeout(long, java.util.concurrent.TimeUnit)} to a value that is
   * reasonable for your use case.
   *
   * @param transferExecutorService Executor on which the requests are synchronously executed.
   */
  public OkHttp3UrlEngine(OkHttpClient client, ListeningExecutorService transferExecutorService) {
    checkNotNull(client.dispatcher());
    this.client = client;
    this.transferExecutorService = transferExecutorService;
  }

  @Override
  public UrlRequest.Builder createRequest(String url) {
    return new OkHttpUrlRequestBuilder(url);
  }

  @Override
  public Set<String> supportedSchemes() {
    return HTTP_SCHEMES;
  }

  class OkHttpUrlRequestBuilder implements UrlRequest.Builder {
    private final String url;
    private final ImmutableMultimap.Builder<String, String> headers = ImmutableMultimap.builder();

    OkHttpUrlRequestBuilder(String url) {
      this.url = url;
    }

    @Override
    public UrlRequest.Builder addHeader(String key, String value) {
      headers.put(key, value);
      return this;
    }

    @Override
    public UrlRequest build() {
      return new OkHttpUrlRequest(url, headers.build());
    }
  }

  /**
   * Implementation of {@link UrlRequest} for OkHttp. Wraps OkHttp's {@link Call} to make network
   * requests.
   */
  class OkHttpUrlRequest implements UrlRequest {
    private final String url;
    private final ImmutableMultimap<String, String> headers;

    OkHttpUrlRequest(String url, ImmutableMultimap<String, String> headers) {
      this.url = url;
      this.headers = headers;
    }

    @Override
    public ListenableFuture<UrlResponse> send() {
      Request.Builder requestBuilder = new Request.Builder();

      try {
        requestBuilder.url(url);
      } catch (IllegalArgumentException e) {
        return Futures.immediateFailedFuture(new RequestException(e));
      }

      for (String key : headers.keys()) {
        for (String value : headers.get(key)) {
          requestBuilder.header(key, value);
        }
      }

      SettableFuture<UrlResponse> responseFuture = SettableFuture.create();
      Call call = client.newCall(requestBuilder.build());
      call.enqueue(
          new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
              if (response.isSuccessful()) {
                responseFuture.set(new OkHttpUrlResponse(response));
              } else {
                responseFuture.setException(
                    new RequestException(
                        ErrorDetails.createFromHttpErrorResponse(
                            response.code(), response.headers().toMultimap(), response.message())));
                response.close();
              }
            }

            @Override
            public void onFailure(Call call, IOException exception) {
              responseFuture.setException(new RequestException(exception));
            }
          });
      responseFuture.addListener(
          () -> {
            if (responseFuture.isCancelled()) {
              call.cancel();
            }
          },
          directExecutor());
      return responseFuture;
    }
  }

  /**
   * Implementation of {@link UrlResponse} for OkHttp. Wraps OkHttp's {@link Response} to complete
   * its operations.
   */
  class OkHttpUrlResponse implements UrlResponse {
    private final Response response;

    OkHttpUrlResponse(Response response) {
      this.response = response;
    }

    @Override
    public int getResponseCode() {
      return response.code();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return response.headers().toMultimap();
    }

    @Override
    public ListenableFuture<Long> readResponseBody(WritableByteChannel destinationChannel) {
      IOUtil.validateChannel(destinationChannel);
      return transferExecutorService.submit(
          () -> {
            try (ResponseBody body = checkNotNull(response.body())) {
              // Transfer the response body to the destination channel via OkHttp's Okio API.
              // Sadly this needs to operate on OutputStream instead of Channels, but at least
              // Okio manages buffers efficiently internally.
              OutputStream outputStream = Channels.newOutputStream(destinationChannel);
              Sink sink = Okio.sink(outputStream);
              return body.source().readAll(sink);
            } catch (IllegalStateException e) {
              // OkHttp throws an IllegalStateException if the stream is closed while
              // trying to write. Catch and rethrow.
              throw new RequestException(e);
            } catch (IOException e) {
              if (e instanceof RequestException) {
                throw e;
              } else {
                throw new RequestException(e);
              }
            } finally {
              response.close();
            }
          });
    }

    @Override
    public void close() {
      response.close();
    }
  }
}
