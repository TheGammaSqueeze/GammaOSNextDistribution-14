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

import com.google.android.downloader.DataUrl.DataUrlException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link UrlEngine} implementation for handling data URLs. */
public final class DataUrlEngine implements UrlEngine {
  private static final ImmutableSet<String> DATA_SCHEME = ImmutableSet.of("data");

  private final ListeningExecutorService transferExecutorService;

  public DataUrlEngine(ListeningExecutorService transferExecutorService) {
    this.transferExecutorService = transferExecutorService;
  }

  @Override
  public UrlRequest.Builder createRequest(String url) {
    return new DataUrlRequestBuilder(url);
  }

  @Override
  public Set<String> supportedSchemes() {
    return DATA_SCHEME;
  }

  /** Implementation of {@link UrlRequest.Builder} for Data URLs. */
  class DataUrlRequestBuilder implements UrlRequest.Builder {
    private final String url;

    DataUrlRequestBuilder(String url) {
      this.url = url;
    }

    @Override
    public UrlRequest build() {
      return new DataUrlRequest(url);
    }
  }

  /**
   * Implementation of {@link UrlRequest} for data URLs. Note that this is pretty trivial because no
   * network connection has to happen. There's also no meaningful way to cancel a data URL request,
   * so cancelling the future returned by {@link #send} has no effect on processing the data URL.
   */
  class DataUrlRequest implements UrlRequest {
    private final String url;

    DataUrlRequest(String url) {
      this.url = url;
    }

    @Override
    public ListenableFuture<UrlResponse> send() {
      return transferExecutorService.submit(
          () -> {
            try {
              return new DataUrlResponse(DataUrl.parseFromString(url));
            } catch (DataUrlException e) {
              throw new RequestException(e);
            }
          });
    }
  }

  /**
   * Implementation of {@link DataUrlResponse} for data URLs. Emulates network-specific APIs for
   * data URLs, which means that this always return HTTP_OK/200 as a response code, and an empty set
   * of response headers.
   *
   * <p>The response body is written by decoding the data URL string and writing the decoded bytes
   * to the destination channel.
   */
  class DataUrlResponse implements UrlResponse {
    private final DataUrl dataUrl;

    DataUrlResponse(DataUrl dataUrl) {
      this.dataUrl = dataUrl;
    }

    @Override
    public int getResponseCode() {
      return HttpURLConnection.HTTP_OK;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return ImmutableMap.of(HttpHeaders.CONTENT_TYPE, ImmutableList.of(dataUrl.mimeType()));
    }

    @Override
    public ListenableFuture<Long> readResponseBody(WritableByteChannel destinationChannel) {
      IOUtil.validateChannel(destinationChannel);
      return transferExecutorService.submit(
          () -> {
            try {
              return IOUtil.blockingWrite(ByteBuffer.wrap(dataUrl.data()), destinationChannel);
            } catch (IOException e) {
              throw new RequestException(e);
            }
          });
    }

    @Override
    public void close() throws IOException {
      // Nothing to close for a data URL response.
    }
  }
}
