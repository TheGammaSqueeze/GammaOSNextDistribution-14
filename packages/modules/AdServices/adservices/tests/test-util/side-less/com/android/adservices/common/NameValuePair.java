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
package com.android.adservices.common;

import java.util.Objects;

/** Simple name-value pair, like a flag or system property. */
final class NameValuePair {

    public final String name;
    public final String value;
    public final @Nullable String separator;

    NameValuePair(String name, String value, @Nullable String separator) {
        this.name = name;
        this.value = value;
        this.separator = separator;
    }

    NameValuePair(String name, String value) {
        this(name, value, /* separator= */ null);
    }

    // TODO(b/294423183): need to add unit test for equals() / hashcode() as they don't use
    // separator

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        NameValuePair other = (NameValuePair) obj;
        return Objects.equals(name, other.name) && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(name).append('=').append(value);
        if (separator != null) {
            string.append(" (separator=").append(separator).append(')');
        }
        return string.toString();
    }

    interface Matcher {
        boolean matches(NameValuePair pair);
    }
}
