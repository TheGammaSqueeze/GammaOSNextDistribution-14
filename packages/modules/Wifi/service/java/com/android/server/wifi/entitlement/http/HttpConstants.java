/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi.entitlement.http;

import android.annotation.NonNull;

/**
 * HTTP constants using for the entitlement flow.
 */
public final class HttpConstants {
    private HttpConstants() {
    }

    /**
     * Possible request methods for Entitlement server response.
     */
    public static final class RequestMethod {
        private RequestMethod() {
        }

        public static final String GET = "GET";
        public static final String POST = "POST";
    }

    /**
     * Possible content type for Entitlement server response.
     */
    public static final class ContentType {
        private ContentType() {
        }

        public static final int UNKNOWN = -1;
        public static final int JSON = 0;
        public static final int XML = 1;

        public static final String NAME = "Content-Type";
    }

    static final int DEFAULT_TIMEOUT_IN_SEC = 30;

    /**
     * Returns an int defined in ContentType from the input string.
     */
    public static int getContentType(@NonNull String contentType) {
        if (contentType.contains("xml")) {
            return ContentType.XML;
        }
        if ("text/vnd.wap.connectivity".equals(contentType)) {
            // Workaround that a server vendor uses this type for XML
            return ContentType.XML;
        }
        if (contentType.contains("json")) {
            return ContentType.JSON;
        }
        return ContentType.UNKNOWN;
    }
}
