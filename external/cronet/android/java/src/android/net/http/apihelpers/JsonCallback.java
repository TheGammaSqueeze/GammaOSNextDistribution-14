// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import org.json.JSONException;
import org.json.JSONObject;

import android.net.http.UrlResponseInfo;

/**
 * A specialization of {@link InMemoryTransformCallback} that interprets the response body as
 * JSON.
 */
public abstract class JsonCallback extends InMemoryTransformCallback<JSONObject> {
    private static final StringCallback STRING_CALLBACK = new StringCallback() {
        @Override
        protected boolean shouldFollowRedirect(UrlResponseInfo info, String newLocationUrl) {
            throw new UnsupportedOperationException();
        }
    };

    @Override // Override to return the subtype
    public JsonCallback addCompletionListener(
            RequestCompletionListener<? super JSONObject> listener) {
        super.addCompletionListener(listener);
        return this;
    }

    @Override
    protected JSONObject transformBodyBytes(UrlResponseInfo info, byte[] bodyBytes) {
        String bodyString = STRING_CALLBACK.transformBodyBytes(info, bodyBytes);
        try {
            return new JSONObject(bodyString);
        } catch (JSONException e) {
            // As suggested by JSONException javadoc
            throw new IllegalArgumentException("Cannot parse the string as JSON!", e);
        }
    }
}
