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
package com.android.adservices.shared.testing.common;

import java.util.Locale;
import java.util.Objects;

public final class GenericHelper {

    private static final String UNIQUE_ID_TEMPLATE = "%s[%d]";

    /** Gets a user-friendly, unique id for a given object. */
    public static String getUniqueId(Object object) {
        Objects.requireNonNull(object, "object cannot be null");

        return object instanceof Identifiable
                ? ((Identifiable) object).getId()
                : String.format(
                        Locale.ENGLISH,
                        UNIQUE_ID_TEMPLATE,
                        object.getClass().getSimpleName(),
                        System.identityHashCode(object));
    }

    private GenericHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
