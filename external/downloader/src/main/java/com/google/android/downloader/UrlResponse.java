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

import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;

/**
 * Interface representing a response to a URL request.
 *
 * <p>Note that this extends from {@link Closeable}. Callers that successfully obtain an instance of
 * the response (via the future returned by {@link UrlRequest#send}) must take care to close the
 * response once done using it.
 */
public interface UrlResponse extends Closeable {
  /**
   * The HTTP status code returned by the server. Returns -1 if the response code can't be discerned
   * or doesn't make sense for this response implementation, as mentioned in the javadocs for {@link
   * java.net.HttpURLConnection#getResponseCode}
   */
  int getResponseCode();

  /** The multi-valued HTTP response headers returned by the server. */
  Map<String, List<String>> getResponseHeaders();

  /**
   * Writes the response body to the provided {@link WritableByteChannel}. The channel must be open
   * prior to calling this method.
   *
   * <p>This method may only be called once for a given response! Attempting to call this method
   * multiple times results in undefined behavior.
   *
   * @return future that resolves to the number of bytes written to the channel.
   */
  ListenableFuture<Long> readResponseBody(WritableByteChannel destinationChannel);
}
