/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.net.Uri;

import java.util.Collection;

/**
 * Validations for the Measurement PPAPI module.
 */
public final class Validation {
    /**
     * @throws IllegalArgumentException if one of the parameters is null.
     */
    public static void validateNonNull(Object... objects) throws IllegalArgumentException {
        for (Object o : objects) {
            if (o == null) {
                throw new IllegalArgumentException("Received null values");
            }
        }
    }

    /**
     * @throws IllegalArgumentException if one of the parameters is an empty collection.
     */
    public static void validateNotEmpty(Collection... collections) throws IllegalArgumentException {
        for (Collection c : collections) {
            if (c.isEmpty()) {
                throw new IllegalArgumentException("Received an empty collection");
            }
        }
    }

    /**
     * @throws IllegalArgumentException if one of the Uri parameters is null or has no scheme.
     */
    public static void validateUri(Uri... uris) throws IllegalArgumentException {
        for (Uri uri : uris) {
            if (uri == null || uri.getScheme() == null) {
                throw new IllegalArgumentException("Uri with no scheme is not valid");
            }
        }
    }
}
