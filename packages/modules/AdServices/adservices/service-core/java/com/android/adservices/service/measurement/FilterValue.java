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

package com.android.adservices.service.measurement;

import com.google.auto.value.AutoOneOf;

import java.util.List;

/** POJO for filter value. */
@AutoOneOf(FilterValue.Kind.class)
public abstract class FilterValue {

    public enum Kind {
        STRING_LIST_VALUE,
        LONG_VALUE
    }

    /** Returns the type of the filter value. */
    public abstract Kind kind();

    /** Returns the string list value. */
    public abstract List<String> stringListValue();

    /** Returns the long value. */
    public abstract long longValue();

    /** Returns an instance for a string list value. */
    public static FilterValue ofStringList(List<String> stringList) {
        return AutoOneOf_FilterValue.stringListValue(stringList);
    }

    /** Returns an instance for a long value. */
    public static FilterValue ofLong(long longValue) {
        return AutoOneOf_FilterValue.longValue(longValue);
    }
}
