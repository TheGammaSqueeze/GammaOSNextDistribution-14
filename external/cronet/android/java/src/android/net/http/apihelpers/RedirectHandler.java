// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package android.net.http.apihelpers;

import android.net.http.UrlResponseInfo;

/**
 * An interface for classes specifying how the HTTP stack should behave on redirects.
 */
public interface RedirectHandler {
    /**
     * Returns whether the redirect should be followed.
     *
     * @param info the response info of the redirect response
     * @param newLocationUrl the redirect location
     * @return whether the redirect should be followed
     */
    boolean shouldFollowRedirect(UrlResponseInfo info, String newLocationUrl) throws Exception;
}
