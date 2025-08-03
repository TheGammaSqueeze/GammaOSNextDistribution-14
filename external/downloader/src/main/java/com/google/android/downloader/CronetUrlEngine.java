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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import org.chromium.net.CallbackException;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlResponseInfo;

/**
 * {@link UrlEngine} implementation that uses Cronet for network connectivity.
 *
 * <p>Note: Internally this implementation allocates a 128kb direct byte buffer per request to
 * transfer bytes around. If memory use is sensitive, then the number of concurrent requests should
 * be limited.
 */
public final class CronetUrlEngine implements UrlEngine {
  private static final ImmutableSet<String> SCHEMES = ImmutableSet.of("http", "https");
  @VisibleForTesting static final int BUFFER_SIZE_BYTES = 128 * 1024; // 128kb

  private final CronetEngine cronetEngine;
  private final Executor callbackExecutor;

  /**
   * Creates a new Cronet-based {@link UrlEngine}.
   *
   * @param cronetEngine The pre-configured {@link CronetEngine} that will be used to implement HTTP
   *     connections.
   * @param callbackExecutor The {@link Executor} on which Cronet's callbacks will be executed. Note
   *     that this request factory implementation will perform I/O in the callbacks, so make sure
   *     the threads backing the executor can block safely (i.e. do not run on the UI thread!)
   */
  public CronetUrlEngine(CronetEngine cronetEngine, Executor callbackExecutor) {
    this.cronetEngine = cronetEngine;
    this.callbackExecutor = callbackExecutor;
  }

  @Override
  public UrlRequest.Builder createRequest(String url) {
    SettableFuture<UrlResponse> responseFuture = SettableFuture.create();
    CronetCallback callback = new CronetCallback(responseFuture);
    org.chromium.net.UrlRequest.Builder builder =
        cronetEngine.newUrlRequestBuilder(url, callback, callbackExecutor);
    return new CronetUrlRequestBuilder(builder, responseFuture);
  }

  @Override
  public Set<String> supportedSchemes() {
    return SCHEMES;
  }

  /** Cronet-specific implementation of {@link UrlRequest} */
  static class CronetUrlRequest implements UrlRequest {
    private final org.chromium.net.UrlRequest urlRequest;
    private final ListenableFuture<UrlResponse> responseFuture;

    CronetUrlRequest(CronetUrlRequestBuilder builder) {
      urlRequest = builder.requestBuilder.build();
      responseFuture = builder.responseFuture;

      responseFuture.addListener(
          () -> {
            if (responseFuture.isCancelled()) {
              urlRequest.cancel();
            }
          },
          directExecutor());
    }

    @Override
    public ListenableFuture<UrlResponse> send() {
      urlRequest.start();
      return responseFuture;
    }
  }

  /** Cronet-specific implementation of {@link UrlRequest.Builder} */
  static class CronetUrlRequestBuilder implements UrlRequest.Builder {
    private final org.chromium.net.UrlRequest.Builder requestBuilder;
    private final ListenableFuture<UrlResponse> responseFuture;

    CronetUrlRequestBuilder(
        org.chromium.net.UrlRequest.Builder requestBuilder,
        ListenableFuture<UrlResponse> responseFuture) {
      this.requestBuilder = requestBuilder;
      this.responseFuture = responseFuture;
    }

    @Override
    public UrlRequest.Builder addHeader(String key, String value) {
      requestBuilder.addHeader(key, value);
      return this;
    }

    @Override
    public UrlRequest build() {
      return new CronetUrlRequest(this);
    }
  }

  /**
   * Cronet-specific implementation of {@link UrlResponse}. Implements its functionality by using
   * Cronet's {@link org.chromium.net.UrlRequest} and {@link UrlResponseInfo} objects.
   */
  static class CronetResponse implements UrlResponse {
    private final org.chromium.net.UrlRequest urlRequest;
    private final UrlResponseInfo urlResponseInfo;
    private final SettableFuture<Long> completionFuture;
    private final CronetCallback callback;

    CronetResponse(
        org.chromium.net.UrlRequest urlRequest,
        UrlResponseInfo urlResponseInfo,
        SettableFuture<Long> completionFuture,
        CronetCallback callback) {
      this.urlRequest = urlRequest;
      this.urlResponseInfo = urlResponseInfo;
      this.completionFuture = completionFuture;
      this.callback = callback;
    }

    @Override
    public int getResponseCode() {
      return urlResponseInfo.getHttpStatusCode();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return urlResponseInfo.getAllHeaders();
    }

    @Override
    public ListenableFuture<Long> readResponseBody(WritableByteChannel destinationChannel) {
      IOUtil.validateChannel(destinationChannel);
      callback.destinationChannel = destinationChannel;
      urlRequest.read(ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES));
      return completionFuture;
    }

    @Override
    public void close() {
      urlRequest.cancel();
    }
  }

  /**
   * Implementation of {@link org.chromium.net.UrlRequest.Callback} to handle the lifecycle of a
   * Cronet url request. The operations of handling the response metadata returned by the server as
   * well as actually reading the response body happen here.
   */
  static class CronetCallback extends org.chromium.net.UrlRequest.Callback {
    private final SettableFuture<UrlResponse> responseFuture;
    private final SettableFuture<Long> completionFuture = SettableFuture.create();

    @Nullable private CronetResponse cronetResponse;
    @Nullable private WritableByteChannel destinationChannel;
    private long numBytesWritten;

    CronetCallback(SettableFuture<UrlResponse> responseFuture) {
      this.responseFuture = responseFuture;
    }

    @Override
    public void onRedirectReceived(
        org.chromium.net.UrlRequest urlRequest,
        UrlResponseInfo urlResponseInfo,
        String newLocationUrl) {
      // Just blindly follow redirects; that's pretty much always what you want to do.
      urlRequest.followRedirect();
    }

    @Override
    public void onResponseStarted(
        org.chromium.net.UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
      // We've received the response metadata from the server, so we have a status code and
      // response headers to examine. At this point we can create a response object and complete
      // the response future. If necessary, the body itself will be downloaded via a subsequent
      // call urlRequest.read inside the CronetResponse.writeResponseBody, which will trigger the
      // other lifecycle callbacks.
      int httpCode = urlResponseInfo.getHttpStatusCode();
      if (httpCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
        responseFuture.setException(
            new RequestException(
                ErrorDetails.createFromHttpErrorResponse(
                    httpCode,
                    urlResponseInfo.getAllHeaders(),
                    urlResponseInfo.getHttpStatusText())));
        urlRequest.cancel();
      } else {
        cronetResponse = new CronetResponse(urlRequest, urlResponseInfo, completionFuture, this);
        responseFuture.set(cronetResponse);
      }
    }

    @Override
    public void onReadCompleted(
        org.chromium.net.UrlRequest urlRequest,
        UrlResponseInfo urlResponseInfo,
        ByteBuffer byteBuffer)
        throws Exception {
      // If we're already done, just bail out.
      if (urlRequest.isDone()) {
        return;
      }

      // If the underlying future has been cancelled, cancel the request and abort.
      if (completionFuture.isCancelled()) {
        urlRequest.cancel();
        return;
      }

      // Flip the buffer to prepare for reading from it.
      byteBuffer.flip();

      // Write however many bytes are in our buffer to the underlying channel.
      numBytesWritten += IOUtil.blockingWrite(byteBuffer, checkNotNull(destinationChannel));

      // Reset the buffer to be reused on the next iteration.
      byteBuffer.clear();

      // Finally, request more bytes. This is necessary per the Cronet API.
      urlRequest.read(byteBuffer);
    }

    @Override
    public void onSucceeded(
        org.chromium.net.UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
      // The body has been successfully streamed. Close the underlying response object to free
      // up resources it holds, and resolve the pending future with the number of bytes written.
      closeResponse();
      completionFuture.set(numBytesWritten);
    }

    @Override
    public void onFailed(
        org.chromium.net.UrlRequest urlRequest,
        UrlResponseInfo urlResponseInfo,
        CronetException exception) {
      // There was some sort of error with the connection. Clean up and resolve the pending future
      // with the exception we encountered.
      closeResponse();

      ErrorDetails errorDetails;
      if (urlResponseInfo != null
          && urlResponseInfo.getHttpStatusCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
        errorDetails =
            ErrorDetails.createFromHttpErrorResponse(
                urlResponseInfo.getHttpStatusCode(),
                urlResponseInfo.getAllHeaders(),
                urlResponseInfo.getHttpStatusText());
      } else if (exception instanceof NetworkException) {
        NetworkException networkException = (NetworkException) exception;
        errorDetails =
            ErrorDetails.builder()
                .setInternalErrorCode(networkException.getCronetInternalErrorCode())
                .setErrorMessage(Strings.nullToEmpty(networkException.getMessage()))
                .build();
      } else {
        errorDetails =
            ErrorDetails.builder()
                .setErrorMessage(Strings.nullToEmpty(exception.getMessage()))
                .build();
      }

      RequestException requestException =
          new RequestException(errorDetails, unwrapException(exception));

      if (!responseFuture.isDone()) {
        responseFuture.setException(requestException);
      } else {
        // N.B: The completion future is available iff the response future is resolved, so
        // we don't need to resolve it with an exception here unless the response future is done.
        completionFuture.setException(requestException);
      }
    }

    @Override
    public void onCanceled(
        org.chromium.net.UrlRequest urlRequest, UrlResponseInfo urlResponseInfo) {
      // The request was cancelled. This only occurs when UrlRequest.cancel is called, which
      // in turn only happens when UrlResponse.close is called. Clean up internal state
      // and resolve the future with an error.
      closeResponse();
      completionFuture.setException(new RequestException("UrlRequest cancelled"));
    }

    /** Safely closes the current response object, if any. */
    private void closeResponse() {
      CronetResponse cronetResponse = this.cronetResponse;
      if (cronetResponse == null) {
        return;
      }
      cronetResponse.close();
    }
  }

  private static Throwable unwrapException(CronetException exception) {
    // CallbackExceptions aren't interesting, so unwrap them.
    if (exception instanceof CallbackException) {
      Throwable cause = exception.getCause();
      return cause == null ? exception : cause;
    }
    return exception;
  }
}
