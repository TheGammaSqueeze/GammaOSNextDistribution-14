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

package com.android.adservices.service.adselection;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Provides JS version related payload helper methods. */
public class JsVersionHelper {
    private static final LoggerFactory.Logger LOGGER = LoggerFactory.getFledgeLogger();
    public static final Long DEFAULT_JS_VERSION_IF_ABSENT = 0L;

    @VisibleForTesting
    public static final String VERSION_HEADER_NAME_FORMAT = "X_FLEDGE_%s_VERSION";

    /** JS payload name. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "JS_PAYLOAD_TYPE_",
            value = {
                JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
            })
    public @interface JsPayloadType {}

    public static final int JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS = 1;

    @VisibleForTesting
    static final String JS_PAYLOAD_NAME_BUYER_BIDDING_LOGIC_JS = "BUYER_BIDDING_LOGIC";

    @VisibleForTesting
    static final ImmutableBiMap<Integer, String> JS_PAYLOAD_TYPE_HEADER_NAME_MAP =
            ImmutableBiMap.<Integer, String>builder()
                    .put(
                            JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                            String.format(
                                    VERSION_HEADER_NAME_FORMAT,
                                    JS_PAYLOAD_NAME_BUYER_BIDDING_LOGIC_JS))
                    .build();

    /** Returns the URI with appended version query parameter. */
    public static AdServicesHttpClientRequest getRequestWithVersionHeader(
            @NonNull Uri uri,
            @NonNull Map<Integer, Long> jsVersionMap,
            boolean useCache,
            @NonNull DevContext devContext) {
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.copyOf(
                        jsVersionMap.entrySet().stream()
                                .collect(
                                        Collectors.toMap(
                                                e -> getVersionHeaderName(e.getKey()),
                                                e -> Long.toString(e.getValue()))));
        return AdServicesHttpClientRequest.builder()
                .setUri(uri)
                .setRequestProperties(requestProperties)
                .setUseCache(useCache)
                .setResponseHeaderKeys(requestProperties.keySet())
                .setDevContext(devContext)
                .build();
    }

    /** Returns a payload header contains the js version attribute. */
    public static ImmutableMap<String, List<String>> constructVersionHeader(
            @JsPayloadType int jsPayloadType, Long version) {
        final long nonNullVersion = Optional.ofNullable(version).orElse(0L);
        return ImmutableMap.of(
                getVersionHeaderName(jsPayloadType),
                ImmutableList.of(Long.toString(nonNullVersion)));
    }

    @VisibleForTesting
    public static String getVersionHeaderName(@JsPayloadType int jsPayloadType) {
        return JS_PAYLOAD_TYPE_HEADER_NAME_MAP.get(jsPayloadType);
    }

    @Nullable
    static Integer getJsPayloadType(@NonNull String versionHeaderName) {
        return JS_PAYLOAD_TYPE_HEADER_NAME_MAP.inverse().get(versionHeaderName);
    }
}
