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
package com.android.timezone.location.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An interface for objects that could contain user or other sensitive information. Implementers
 * must ensure that {@link #toString()} can be used for the default / redacted / log-safe form, and
 * {@link #toPiiString()} is provided for the form that can contains sensitive information.
 *
 * <p>The purpose of this interface is to ensure Java's default {@link #toString()} behavior is safe
 * to use by default in logs, exception messages, etc. See {@link PiiLoggables} for support methods.
 */
public interface PiiLoggable {

    /** Used for logs, should be implemented to be log-safe, not containing PII. */
    @Override
    @NonNull String toString();

    /** Like {@link #toString()} but can include PII for debugging. */
    @NonNull String toPiiString();

    /**
     * Returns a string representation of {@code value}. Operates like {@link
     * String#valueOf(Object)}} but calls {@link PiiLoggable#toPiiString()} instead of
     * {@link Object#toString()}.
     */
    static String toPiiString(@Nullable PiiLoggable value) {
        return value == null ? PiiLoggables.NULL_STRING : value.toPiiString();
    }
}
