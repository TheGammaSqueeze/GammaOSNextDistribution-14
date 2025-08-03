// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import androidx.annotation.Nullable;

import android.net.http.HttpException;
import android.net.http.UrlResponseInfo;

/**
 * A completion listener for accepting the results of a the HTTP stack request asynchronously.
 *
 * <p>To attach to an HTTP request use {@link InMemoryTransformCallback} and call {@link
 * InMemoryTransformCallback#addCompletionListener}.
 *
 * @param <T> the response body type
 */
public interface RequestCompletionListener<T> {
    /**
     * Invoked if request failed for any reason after starting the request. Once invoked, no other
     * methods will be invoked on this object. {@code exception} provides information about the
     * failure.
     *
     * @param info Response information. May be {@code null} if no response was received.
     * @param exception detailed information about the error that occurred.
     */
    void onFailed(@Nullable UrlResponseInfo info, HttpException exception);

    /**
     * Invoked if request was canceled via {@code UrlRequest#cancel}. Once invoked, no other methods
     * will be invoked on this object.
     *
     * @param info Response information. May be {@code null} if no response was received.
     */
    void onCanceled(@Nullable UrlResponseInfo info);

    /**
     * Invoked when request is completed successfully. Once invoked, no other methods will be
     * invoked on this object.
     *
     * @param info Response information.
     * @param body The response body transformed to the desired type.
     */
    void onSucceeded(UrlResponseInfo info, T body);
}
