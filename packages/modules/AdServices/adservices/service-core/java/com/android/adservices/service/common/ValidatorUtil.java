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

package com.android.adservices.service.common;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Constants and utility method for data validation. */
public class ValidatorUtil {
    public static final String AD_TECH_ROLE_BUYER = "buyer";
    public static final String AD_TECH_ROLE_SELLER = "seller";

    public static final String HTTPS_SCHEME = "https";

    /** Returns true if the given string is null or empty or vise versa. */
    public static boolean isStringNullOrEmpty(@Nullable String str) {
        return Objects.isNull(str) || str.isEmpty();
    }

    private ValidatorUtil() {}
}
