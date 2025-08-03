// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import android.net.http.HttpException;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import android.net.http.UrlRequest;
import android.net.http.UrlResponseInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Utility class for creating simple, convenient {@code UrlRequest.Callback} implementations for
 * reading common types of responses.
 *
 * <p>Note that the convenience callbacks store the entire response body in memory. We do not
 * recommend using them if it's possible to stream the response body, or if the response body sizes
 * can cause strain on the on-device resources.
 *
 * <p>The helper callbacks come in two flavors - either the caller provides a callback to be invoked
 * when the request finishes (successfully or not), or the caller is given a {@link Future} which
 * completes when the HTTP stack finishes processing the request.
 */
public class UrlRequestCallbacks {
    public static ByteArrayCallback forByteArrayBody(
            RedirectHandler redirectHandler, RequestCompletionListener<byte[]> listener) {
        return newByteArrayCallback(redirectHandler).addCompletionListener(listener);
    }

    public static CallbackAndResponseFuturePair<byte[], ByteArrayCallback> forByteArrayBody(
            RedirectHandler redirectHandler) {
        ByteArrayCallback callback = newByteArrayCallback(redirectHandler);
        Future<HttpResponse<byte[]>> future = addResponseFutureListener(callback);
        return new CallbackAndResponseFuturePair<>(future, callback);
    }

    public static StringCallback forStringBody(
            RedirectHandler redirectHandler, RequestCompletionListener<String> listener) {
        return newStringCallback(redirectHandler).addCompletionListener(listener);
    }

    public static CallbackAndResponseFuturePair<String, StringCallback> forStringBody(
            RedirectHandler redirectHandler) {
        StringCallback callback = newStringCallback(redirectHandler);
        Future<HttpResponse<String>> future = addResponseFutureListener(callback);
        return new CallbackAndResponseFuturePair<>(future, callback);
    }

    public static JsonCallback forJsonBody(
            RedirectHandler redirectHandler, RequestCompletionListener<JSONObject> listener) {
        return newJsonCallback(redirectHandler).addCompletionListener(listener);
    }

    public static CallbackAndResponseFuturePair<JSONObject, JsonCallback> forJsonBody(
            RedirectHandler redirectHandler) {
        JsonCallback callback = newJsonCallback(redirectHandler);
        Future<HttpResponse<JSONObject>> future = addResponseFutureListener(callback);
        return new CallbackAndResponseFuturePair<>(future, callback);
    }

    private static ByteArrayCallback newByteArrayCallback(RedirectHandler redirectHandler) {
        return new ByteArrayCallback() {
            @Override
            protected boolean shouldFollowRedirect(UrlResponseInfo info, String newLocationUrl)
                    throws Exception {
                return redirectHandler.shouldFollowRedirect(info, newLocationUrl);
            }
        };
    }

    private static StringCallback newStringCallback(RedirectHandler redirectHandler) {
        return new StringCallback() {
            @Override
            protected boolean shouldFollowRedirect(UrlResponseInfo info, String newLocationUrl)
                    throws Exception {
                return redirectHandler.shouldFollowRedirect(info, newLocationUrl);
            }
        };
    }

    private static JsonCallback newJsonCallback(RedirectHandler redirectHandler) {
        return new JsonCallback() {
            @Override
            protected boolean shouldFollowRedirect(UrlResponseInfo info, String newLocationUrl)
                    throws Exception {
                return redirectHandler.shouldFollowRedirect(info, newLocationUrl);
            }
        };
    }

    private static <T> Future<HttpResponse<T>> addResponseFutureListener(
            InMemoryTransformCallback<T> callback) {
        CompletableFuture<HttpResponse<T>> completableFuture = new CompletableFuture<>();
        callback.addCompletionListener(new RequestCompletionListener<T>() {
            @Override
            public void onFailed(@Nullable UrlResponseInfo info, HttpException exception) {
                completableFuture.completeExceptionally(exception);
            }

            @Override
            public void onCanceled(@Nullable UrlResponseInfo info) {
                completableFuture.completeExceptionally(
                        new HttpException("The request was canceled!", null) {});
            }

            @Override
            public void onSucceeded(UrlResponseInfo info, T body) {
                completableFuture.complete(new HttpResponse<>(info, body));
            }
        });
        return completableFuture;
    }

    /**
     * A named pair-like structure encapsulating callbacks and associated response futures.
     *
     * <p>The request should be used to pass to {@code HttpEngine#newUrlRequestBuilder}, the future
     * will contain the response to the request.
     *
     * @param <CallbackT> the subtype of the callback
     * @param <ResponseBodyT> The type of the deserialized response body
     */
    public static class CallbackAndResponseFuturePair<
            ResponseBodyT, CallbackT extends InMemoryTransformCallback<ResponseBodyT>> {
        private final Future<HttpResponse<ResponseBodyT>> mFuture;
        private final CallbackT mCallback;

        CallbackAndResponseFuturePair(
                Future<HttpResponse<ResponseBodyT>> future, CallbackT callback) {
            this.mFuture = future;
            this.mCallback = callback;
        }

        public Future<HttpResponse<ResponseBodyT>> getFuture() {
            return mFuture;
        }

        public CallbackT getCallback() {
            return mCallback;
        }
    }

    private UrlRequestCallbacks() {}
}
