// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import androidx.annotation.Nullable;

import android.net.http.UrlResponseInfo;

import java.util.Objects;

/**
 * A helper object encompassing the headers, body and metadata of a response to HTTP URL requests.
 *
 * @param <T> the response body type
 */
public class HttpResponse<T> {
    /** The headers and other metadata of the response. */
    private final UrlResponseInfo mUrlResponseInfo;
    /**
     * The full body of the response, after performing a user-defined deserialization.
     */
    private final @Nullable T mResponseBody;

    HttpResponse(UrlResponseInfo urlResponseInfo, @Nullable T responseBody) {
        this.mUrlResponseInfo = urlResponseInfo;
        this.mResponseBody = responseBody;
    }

    /**
     * Returns the headers and other metadata of the response.
     */
    public UrlResponseInfo getUrlResponseInfo() {
        return mUrlResponseInfo;
    }

    /**
     * Returns the full body of the response, after performing a user-defined deserialization.
     */
    public @Nullable T getResponseBody() {
        return mResponseBody;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HttpResponse)) return false;
        HttpResponse<?> that = (HttpResponse<?>) o;
        return Objects.equals(mUrlResponseInfo, that.mUrlResponseInfo)
                && Objects.equals(mResponseBody, that.mResponseBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUrlResponseInfo, mResponseBody);
    }
}
