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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/** Utility methods to support {@link PiiLoggable}. */
public final class PiiLoggables {

    static final String NULL_STRING = "null";
    static final String REDACTED_STRING = "<redacted>";

    private PiiLoggables() {}

    /**
     * Returns a null-safe function that calls {@link PiiLoggable#toPiiString(PiiLoggable)} when the
     * function's argument is not {@code null}.
     */
    @NonNull
    public static <V extends PiiLoggable> Function<V, String> toPiiStringFunction() {
        return x -> PiiLoggable.toPiiString(x);
    }

    /**
     * An adapter / wrapper for objects that output PII as part of their normal {@link #toString()}.
     * The referenced value can be {@code null}.
     *
     * <p>The implementation contract:
     * <ul>
     * <li>{@link #toPiiString()} must print {@code "null"} if the value is null, and may delegate
     * to the value's {@link #toString()}.</li>
     * <li>{@link #toString()} must print {@code "null"} if the value is null, or a PII safe string,
     * e.g. "&lt;redacted&gt;", or just non-PII information.
     * <li>Implementations may implement {@link #equals(Object)} and {@link #hashCode()}.</li>
     * </ul>
     *
     * <p>See {@link #fromPiiValue(Object)} for a method that returns objects that implement a
     * minimal contract.
     *
     * @param <V> the type of the value held
     */
    public interface PiiLoggableValue<V> extends PiiLoggable {
        /** Returns the held value. */
        @Nullable V get();
    }

    /**
     * Wraps a nullable reference in a {@link PiiLoggableValue}. {@link Object#toString()}
     * returns the string {@code "<redacted>"}. {@link Object#equals(Object)} and {@link
     * Object#hashCode()} delegate to {@code value} when not {@code null}.
     */
    @NonNull
    public static <V> PiiLoggableValue<V> fromPiiValue(@Nullable V value) {
        class PiiLoggableValueImpl<V> implements PiiLoggableValue<V> {

            private final V mValue;

            PiiLoggableValueImpl(V value) {
                mValue = value;
            }

            @Override
            public V get() {
                return mValue;
            }

            @Override
            public String toPiiString() {
                return String.valueOf(mValue);
            }

            @Override
            public String toString() {
                return mValue == null ? NULL_STRING : REDACTED_STRING;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                PiiLoggableValueImpl<?> that = (PiiLoggableValueImpl<?>) o;
                return Objects.equals(mValue, that.mValue);
            }

            @Override
            public int hashCode() {
                return Objects.hash(mValue);
            }
        }
        return new PiiLoggableValueImpl<V>(value);
    }

    /**
     * A convenience method for creating a {@link PiiLoggable} from a {@link String} that doesn't
     * contain PII. Both {@link PiiLoggable#toString()} and {@link PiiLoggable#toPiiString()} will
     * return {@code "null"} if the string is {@code null}, or the result of calling {@link
     * Object#toString()} if the string is not {@code null}.
     */
    @NonNull
    public static PiiLoggable fromString(@Nullable String s) {
        class PiiLoggableString implements PiiLoggable {

            private final String mString;

            PiiLoggableString(String string) {
                mString = string;
            }

            @Override
            public String toPiiString() {
                return toString();
            }

            @Override
            public String toString() {
                return String.valueOf(mString);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                PiiLoggableString that = (PiiLoggableString) o;
                return Objects.equals(mString, that.mString);
            }

            @Override
            public int hashCode() {
                return Objects.hash(mString);
            }
        }
        return new PiiLoggableString(s);
    }

    /**
     * Creates a {@link PiiLoggable} that generates strings using the supplied template. The
     * resulting object uses {@link String#format(String, Object...)} for {@link
     * PiiLoggable#toString()} and {@link PiiLoggables#formatPiiString(String, PiiLoggable...)} for
     * {@link PiiLoggable#toPiiString()}.
     */
    @NonNull
    public static PiiLoggable fromTemplate(@NonNull String template, PiiLoggable... piiLoggables) {
        class TemplatedPiiLoggable implements PiiLoggable {
            @NonNull private final String mTemplate;
            @NonNull private final PiiLoggable[] mLoggables;

            TemplatedPiiLoggable(String template, PiiLoggable... loggables) {
                mTemplate = Objects.requireNonNull(template);
                mLoggables = Objects.requireNonNull(loggables);
            }

            @Override
            public String toString() {
                return String.format(mTemplate, (Object[]) mLoggables);
            }

            @Override
            public String toPiiString() {
                return PiiLoggables.formatPiiString(mTemplate, mLoggables);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                TemplatedPiiLoggable that = (TemplatedPiiLoggable) o;
                return mTemplate.equals(that.mTemplate)
                        && Arrays.equals(mLoggables, that.mLoggables);
            }

            @Override
            public int hashCode() {
                int result = Objects.hash(mTemplate);
                result = 31 * result + Arrays.hashCode(mLoggables);
                return result;
            }
        }

        return new TemplatedPiiLoggable(template, piiLoggables);
    }

    /**
     * Formats a templated string. This method operates like {@link
     * String#format(String, Object...)} except {@code %s} will be replaced with the result of
     * {@link PiiLoggable#toPiiString(PiiLoggable)} instead of {@link String#valueOf(Object)}.
     */
    @NonNull
    public static String formatPiiString(@NonNull String format, PiiLoggable... piiLoggables) {
        String[] strings = new String[piiLoggables.length];
        for (int i = 0; i < strings.length; i++) {
            PiiLoggable piiLoggable = piiLoggables[i];
            strings[i] = PiiLoggable.toPiiString(piiLoggable);
        }
        return String.format(format, (Object[]) strings);
    }
}
