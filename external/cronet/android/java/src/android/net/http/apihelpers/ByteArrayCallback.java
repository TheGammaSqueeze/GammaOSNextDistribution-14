// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import android.net.http.UrlResponseInfo;

/**
 * A specialization of {@link InMemoryTransformCallback} which returns the body bytes
 * verbatim without any interpretation.
 */
public abstract class ByteArrayCallback extends InMemoryTransformCallback<byte[]> {
    @Override // Override to return the subtype
    public ByteArrayCallback addCompletionListener(
            RequestCompletionListener<? super byte[]> listener) {
        super.addCompletionListener(listener);
        return this;
    }

    @Override
    protected final byte[] transformBodyBytes(UrlResponseInfo info, byte[] bodyBytes) {
        return bodyBytes;
    }
}
