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
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Simple container object that contains information about a request error. */
@AutoValue
public abstract class ErrorDetails {
  // Additional HTTP status codes not listed in HttpURLConnection.
  static final int HTTP_TOO_MANY_REQUESTS = 429;

  /**
   * Returns the underlying numerical error value associated with this error. The meaning of this
   * value depends on the network stack in use. Consult the network stack documentation to determine
   * the meaning of this value. By default this returns the value 0.
   */
  public abstract int getInternalErrorCode();

  /**
   * Returns the human-readable error message associated with this error. This message is for
   * debugging purposes only and should not be parsed programmatically. By default this returns the
   * empty string.
   */
  public abstract String getErrorMessage();

  /**
   * Returns the HTTP status value associated with this error, if any. If the request succeeded but
   * the server returned an error (e.g. server error 500), then this field can help classify the
   * problem. If no HTTP status value is associated with this error, then the value -1 will be
   * returned instead.
   */
  public abstract int getHttpStatusCode();

  /**
   * Returns whether the request that triggered the error is retryable as-is without further changes
   * to the request or state of the client.
   *
   * <p>Whether or not a request can be retried can depend on nuanced details of how an error
   * occurred, so individual URL engines may make local decisions on whether an error should be
   * retried. For example, TCP's connection reset error is often caused by a crash of the server
   * during processing. Resending the exact same request, perhaps after some delay, has a good
   * chance of hitting a different, healthy server and so in general this error is retryable. On the
   * other hand, it doesn't make sense to spend resources retrying a HTTP_NOT_FOUND/404 since it
   * isn't likely that the requested URL will spontaneously appear. A request that fails with
   * HTTP_UNAUTHORIZED/401 is also not retryable under this definition. Although it makes sense to
   * send an authenticated version of the failed request, this modification must happen at a higher
   * layer.
   */
  public abstract boolean isRetryableAsIs();

  /** Creates a new builder instance for constructing request errors. */
  public static Builder builder() {
    return new AutoValue_ErrorDetails.Builder()
        .setInternalErrorCode(0)
        .setErrorMessage("")
        .setHttpStatusCode(-1)
        .setRetryableAsIs(false);
  }

  /**
   * Create a new error instance for the given value and message. A convenience factory function for
   * the most common use case.
   */
  public static ErrorDetails create(@Nullable String message) {
    return builder().setErrorMessage(Strings.nullToEmpty(message)).build();
  }

  /**
   * Create a new error instance for an HTTP error response, as represented by the provided {@code
   * httpResponseCode} and {@code httpResponseHeaders}. The canonical error code and retryability
   * bit is computed based on the values of the response.
   */
  public static ErrorDetails createFromHttpErrorResponse(
      int httpResponseCode,
      Map<String, List<String>> httpResponseHeaders,
      @Nullable String message) {
    return builder()
        .setErrorMessage(Strings.nullToEmpty(message))
        .setRetryableAsIs(isRetryableHttpError(httpResponseCode, httpResponseHeaders))
        .setHttpStatusCode(httpResponseCode)
        .setInternalErrorCode(httpResponseCode)
        .build();
  }

  /**
   * Obtains a connection error from a {@link Throwable}. If the throwable is an instance of {@link
   * RequestException}, then it returns the error instance associated with that exception.
   * Otherwise, a new connection error is constructed with default values and an error message set
   * to the value returned by {@link Throwable#getMessage}.
   */
  public static ErrorDetails fromThrowable(Throwable throwable) {
    if (throwable instanceof RequestException) {
      RequestException requestException = (RequestException) throwable;
      return requestException.getErrorDetails();
    } else {
      return builder().setErrorMessage(Strings.nullToEmpty(throwable.getMessage())).build();
    }
  }

  /**
   * Determine if a given HTTP error, as represented by an HTTP response code and response headers,
   * is retryable. See the comment on {@link #isRetryableAsIs} for a longer explanation on how this
   * related to the canonical error code.
   */
  private static boolean isRetryableHttpError(
      int httpCode, Map<String, List<String>> responseHeaders) {
    switch (httpCode) {
      case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
        // Client timeout means some client-side timeout was encountered. Retrying is safe.
        return true;
      case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
        // Entity too large means the request was too large for the server to process. Retrying is
        // safe if the server provided the retry-after header.
        return responseHeaders.containsKey(HttpHeaders.RETRY_AFTER);
      case HTTP_TOO_MANY_REQUESTS:
        // Too many requests means the server is overloaded and is rejecting requests to temporarily
        // reduce load. See go/rfc/6585. Retrying is safe.
        return true;
      case HttpURLConnection.HTTP_UNAVAILABLE:
        // Unavailable means the server is currently unable to service the request. Retrying is
        // safe if the server provided the retry-after header.
        return responseHeaders.containsKey(HttpHeaders.RETRY_AFTER);
      case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
        // Gateway timeout means there was a server timeout somewhere. Retrying is safe.
        return true;
      default:
        // By default, assume any other HTTP error is not retryable.
        return false;
    }
  }

  /** Builder for creating instances of {@link ErrorDetails}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** Sets the error value. */
    public abstract Builder setInternalErrorCode(int internalErrorCode);

    /** Sets the error message. */
    public abstract Builder setErrorMessage(String errorMessage);

    /** Sets the http status value. */
    public abstract Builder setHttpStatusCode(int httpStatusCode);

    /** Sets whether the error is retryable as-is. */
    public abstract Builder setRetryableAsIs(boolean retryable);

    /** Builds the request error instance. */
    public abstract ErrorDetails build();
  }
}
