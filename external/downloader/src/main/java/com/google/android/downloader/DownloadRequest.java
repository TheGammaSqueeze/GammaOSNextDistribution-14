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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMultimap;
import java.net.URI;
import javax.annotation.Nullable;

/**
 * Buildable value class used to construct and represent an individual download request. Instances
 * are created via {@link Builder}, which in turn can be obtained via {@link
 * Downloader2#newRequestBuilder}. Requests are executed via {@link Downloader2#execute}.
 */
@AutoValue
public abstract class DownloadRequest {
  public abstract URI uri();

  public abstract ImmutableMultimap<String, String> headers();

  public abstract DownloadConstraints downloadConstraints();

  public abstract @Nullable OAuthTokenProvider oAuthTokenProvider();

  public abstract DownloadDestination destination();

  @VisibleForTesting // Package-private outside of testing use
  public static Builder newBuilder() {
    return new AutoValue_DownloadRequest.Builder();
  }

  /** Builder facility for constructing instances of an {@link DownloadRequest}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the {@link DownloadDestination} that the download result should be sent to. */
    public abstract Builder setDestination(DownloadDestination destination);

    /**
     * Sets the {@link URI} to download from. The URI scheme returned by {@link URI#getScheme} must
     * be supported by one of the {@link com.google.android.libraries.net.urlengine.UrlEngine}
     * instances attached to the downloader.
     */
    public abstract Builder setUri(URI uri);

    /**
     * Sets the URL to download from. This must be a valid URL string per RFC 2396; invalid strings
     * will result in an {@link IllegalArgumentException} to be thrown from this method.
     *
     * <p>Internally this has the same effect as calling {@code setUri(URI.create(string))}, and
     * exists here as a convenience method.
     */
    public Builder setUrl(String url) {
      return setUri(URI.create(url));
    }

    abstract ImmutableMultimap.Builder<String, String> headersBuilder();

    /**
     * Adds a request header to be sent as part of this request. Request headers are only used in
     * {@link com.google.android.libraries.net.urlengine.UrlEngine} instances where they make sense
     * (e.g. headers are attached to HTTP requests but ignored for Data URIs).
     */
    public Builder addHeader(String key, String value) {
      headersBuilder().put(key, value);
      return this;
    }

    /**
     * Specifies the {@link com.google.android.libraries.net.downloader2.DownloadConstraints} for
     * this request.
     *
     * <p>Note that the checking the download constraints for a request only occurs right before the
     * request is executed, including cases where the request is retried due to a network error.
     *
     * <p>The ability to detect changes in connectivity depends on the underlying implementation of
     * the {@link com.google.android.libraries.net.urlengine.UrlEngine} that processes this request.
     * Some network stacks may be able to switch network types without interrupting the connection
     * (e.g. seamless hand-off between WiFI and Cellular). The downloader relies on an interrupted
     * connection to detect network changes, so in those cases the download may silently continue on
     * the changed network.
     *
     * <p>The default value is {@link
     * com.google.android.libraries.net.downloader2.DownloadConstraints#NETWORK_CONNECTED}.
     */
    public abstract Builder setDownloadConstraints(DownloadConstraints downloadConstraints);

    /**
     * Sets an optional {@link OAuthTokenProvider} on the request. When set, the downloader will
     * consult the OAuthTokenProvider on this request and use it to attach "Authorization" headers
     * to outgoing HTTP requests.
     */
    public abstract Builder setOAuthTokenProvider(
        @Nullable OAuthTokenProvider oAuthTokenProvider);

    public abstract DownloadRequest build();
  }
}
