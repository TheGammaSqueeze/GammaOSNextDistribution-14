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

package com.android.permission.safetylabel;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.StringDef;

import java.lang.annotation.Retention;

/**
 * Constants and util methods for determining valid {@link String} data types for usage within
 * {@link SafetyLabel} and {@link DataLabel}
 */
public class DataLabelConstants {

    /** List of valid Safety Label data usages. Shared vs Collected */
    @Retention(SOURCE)
    @StringDef(
            prefix = "DATA_USAGE_",
            value = {
                    DATA_USAGE_COLLECTED,
                    DATA_USAGE_SHARED,
            })
    public @interface DataUsage {}
    public static final String DATA_USAGE_COLLECTED = "data_collected";
    public static final String DATA_USAGE_SHARED = "data_shared";
}
