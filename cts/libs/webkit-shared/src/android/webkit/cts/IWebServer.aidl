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

import android.webkit.cts.HttpRequest;
import android.webkit.cts.HttpHeader;

import java.util.List;

interface IWebServer {
    void start(int sslMode, in @nullable byte[] acceptedIssuerDer, int keyResId, int certResId);

    void shutdown();

    void resetRequestState();

    String setResponse(
        String path, String responseString, in List<HttpHeader> responseHeaders);

    String getAbsoluteUrl(String path);

    String getUserAgentUrl();

    String getDelayedAssetUrl(String path);

    String getRedirectingAssetUrl(String path);

    String getAssetUrl(String path);

    String getAuthAssetUrl(String path);

    String getBinaryUrl(String mimeType, int contentLength);

    String getAppCacheUrl();

    int getRequestCount();

    int getRequestCountWithPath(String path);

    boolean wasResourceRequested(String url);

    HttpRequest getLastRequest(String path);

    HttpRequest getLastAssetRequest(String url);

    String getCookieUrl(String path);

    String getSetCookieUrl(String path, String key, String value, String attributes);

    String getLinkedScriptUrl(String path, String url);
}