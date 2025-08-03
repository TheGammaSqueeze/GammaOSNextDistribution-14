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

package com.android.adservices.service.measurement.util;

import android.annotation.NonNull;
import android.net.Uri;

/**
 * Class to extract URI.
 */
public class BaseUriExtractor {
    /**
     * Returns the base URI of the given URI. For example: "https://www.example.com/abc" ->
     * "https://www.example.com" "android-app://com.example.sample" ->
     * "android-app://com.example.sample" "https://www.example.com:8080/abc" ->
     * "https://www.example.com:8080"
     */
    @NonNull
    public static Uri getBaseUri(Uri uri) {
        if (!uri.isHierarchical() || !uri.isAbsolute()) {
            throw new IllegalArgumentException(
                    String.format("URI should be hierarchical and absolute. Input: %s", uri));
        }
        return new Uri.Builder()
                .scheme(uri.getScheme())
                .encodedAuthority(uri.getEncodedAuthority())
                .build();
    }
    private BaseUriExtractor() {
    }
}
