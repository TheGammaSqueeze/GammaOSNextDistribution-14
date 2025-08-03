/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.common.httpclient;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class AdServicesHttpUtil {
    public static final String CONTENT_LENGTH_HDR = "Content-Length";
    public static final byte[] EMPTY_BODY = new byte[0];
    public static String CONTENT_TYPE_HDR = "Content-Type";
    public static String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";
    public static String OHTTP_CONTENT_TYPE = "message/ohttp-req";
    public static String OHTTP_KEYS_CONTENT_TYPE = "application/ohttp-keys";
    ;

    public enum HttpMethodType {
        GET,
        POST,
    }

    public static ImmutableMap<String, String> REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE =
            ImmutableMap.of(CONTENT_TYPE_HDR, PROTOBUF_CONTENT_TYPE);

    public static ImmutableMap<String, String> REQUEST_PROPERTIES_OHTTP_KEYS_TYPE =
            ImmutableMap.of(CONTENT_TYPE_HDR, OHTTP_KEYS_CONTENT_TYPE);

    public static ImmutableSet<String> RESPONSE_PROPERTIES_CONTENT_TYPE =
            ImmutableSet.of(CONTENT_TYPE_HDR);

    public static ImmutableMap<String, String> REQUEST_PROPERTIES_OHTTP_CONTENT_TYPE =
            ImmutableMap.of(CONTENT_TYPE_HDR, OHTTP_CONTENT_TYPE);
}
