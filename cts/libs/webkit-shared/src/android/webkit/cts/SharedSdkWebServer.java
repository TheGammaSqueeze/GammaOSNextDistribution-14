/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit.cts;


import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * This class serves as the public fronting API for tests to interact with the CtsTestServer.
 *
 * <p>This is a light wrapper around the Binder Proxy so that we can wrap all the RemoteExceptions
 * in a Runtime exception so that the WebServer methods can be used the same as they were used
 * previously.
 */
public final class SharedSdkWebServer {
    private final IWebServer mWebServer;

    public SharedSdkWebServer(IWebServer webServer) {
        mWebServer = webServer;
    }

    /** Starts the web server using the provided parameters}. */
    public void start(
            @SslMode int sslMode, @Nullable byte[] acceptedIssuerDer, int keyResId, int certResId) {
        ExceptionWrapper.unwrap(() -> {
            mWebServer.start(sslMode, acceptedIssuerDer, keyResId, certResId);
        });
    }

    /** Shuts down the web server if it was started. */
    public void shutdown() {
        ExceptionWrapper.unwrap(() -> {
            mWebServer.shutdown();
        });
    }

    /** Resets all request state stored. */
    public void resetRequestState() {
        ExceptionWrapper.unwrap(() -> {
            mWebServer.resetRequestState();
        });
    }

    /**
     * Sets a response to be returned when a particular request path is passed in (with the option
     * to specify additional headers).
     */
    public String setResponse(
            String path, String responseString, List<HttpHeader> responseHeaders) {
        return ExceptionWrapper.unwrap(() -> {
            // We can't send a null value as a list
            // so default the responseHeaders to an empty list if null was provided.
            return mWebServer.setResponse(
                    path,
                    responseString,
                    responseHeaders == null ? Collections.emptyList() : responseHeaders);
        });
    }

    /** Return the absolute URL that refers to a path. */
    public String getAbsoluteUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getAbsoluteUrl(path);
        });
    }

    /** Returns a url that will contain the user agent in the header and in the body. */
    public String getUserAgentUrl() {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getUserAgentUrl();
        });
    }

    /** Get a delayed assert url for an asset path. */
    public String getDelayedAssetUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getDelayedAssetUrl(path);
        });
    }

    /** Get a url that will redirect for a path. */
    public String getRedirectingAssetUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getRedirectingAssetUrl(path);
        });
    }

    /** Get the full url for an asset. */
    public String getAssetUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getAssetUrl(path);
        });
    }

    /** Get the full auth url for an asset. */
    public String getAuthAssetUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getAuthAssetUrl(path);
        });
    }

    /** Get a binary url. */
    public String getBinaryUrl(String mimeType, int contentLength) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getBinaryUrl(mimeType, contentLength);
        });
    }

    /** Returns the url to the app cache. */
    public String getAppCacheUrl() {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getAppCacheUrl();
        });
    }

    /** Returns how many requests have been made. */
    public int getRequestCount() {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getRequestCount();
        });
    }

    /** Returns the request count for a particular path */
    public int getRequestCount(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getRequestCountWithPath(path);
        });
    }

    /** Verify if a resource was requested. */
    public boolean wasResourceRequested(String url) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.wasResourceRequested(url);
        });
    }

    /** Retrieve the last request to be made on a url. */
    public HttpRequest getLastRequest(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getLastRequest(path);
        });
    }

    /** Retrieve the last request for an asset path to be made on a url. */
    public HttpRequest getLastAssetRequest(String url) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getLastAssetRequest(url);
        });
    }

    /** Returns a url that will contain the path as a cookie. */
    public String getCookieUrl(String path) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getCookieUrl(path);
        });
    }

    /**
     * Returns a URL that attempts to set the cookie "key=value" with the given list of attributes
     * when fetched.
     */
    public String getSetCookieUrl(String path, String key, String value, String attributes) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getSetCookieUrl(path, key, value, attributes);
        });
    }

    /** Returns a URL for a page with a script tag where src equals the URL passed in. */
    public String getLinkedScriptUrl(String path, String url) {
        return ExceptionWrapper.unwrap(() -> {
            return mWebServer.getLinkedScriptUrl(path, url);
        });
    }
}
