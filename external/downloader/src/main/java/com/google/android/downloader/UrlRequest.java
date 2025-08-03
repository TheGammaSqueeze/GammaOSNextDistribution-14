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

/**
 * Interface representing a request to a URL. Intended for use with a {@link UrlEngine}.
 *
 * <p>Note: Unless explicitly noted, instances of this object are designed to be thread-safe,
 * meaning that methods can be called from any thread. It is however not advisable to use instances
 * of this object as a lock, as internal logic may synchronize on the request instance. Also,
 * methods should not be called from the Android main/UI thread, as some I/O may occur.
 */
public interface UrlRequest {
  /** Builder interface for constructing URL requests. */
  interface Builder {
    /** Adds an individual HTTP header for this request. */
    default Builder addHeader(String key, String value) {
      return this;
    }

    /** Builds the URL request. */
    UrlRequest build();
  }

  /**
   * Executes this request by connecting to the URL represented by this request. Returns
   * immediately, with the {@link UrlResponse} becoming available asynchronously as a {@link
   * ListenableFuture}. May only be called once; multiple calls result in undefined behavior.
   *
   * <p>If the request fails due to I/O errors or due to an error response from the server (e.g.
   * http status codes in the 400s or 500s), then the future will resolve with either an {@link
   * RequestException} or an {@link java.io.IOException}. Any other type of exception (e.g. {@link
   * IllegalStateException}) indicates an unexpected error occurred, and such errors should likely
   * be reported at a severe level and possibly even crash the app.
   *
   * <p>To cancel an executed requested, call {@link ListenableFuture#cancel} on the future returned
   * by this method. Cancellation is opportunistic and best-effort; calling will not guarantee that
   * no more network activity will occur, as it is not possible to immediately stop a thread that is
   * transferring bytes from the network. Cancellation may fail internally; observe the resolution
   * of the future to capture such failures.
   *
   * <p>Note that although this method returns its result asynchronously and doesn't block on the
   * request, the operation may still involve performing some I/O (e.g. verifying the existence of a
   * file). Do not call this on the UI thread!
   */
  ListenableFuture<UrlResponse> send();
}
