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

package com.android.adservices.service.common.bhttp;

import com.android.internal.util.Preconditions;

/** Utility methods for HTTP status code. */
public class HttpStatusCodeUtil {

    public static final int INFORMATIVE_MIN = 100;
    public static final int INFORMATIVE_MAX = 199;
    public static final int FINAL_MIN = 200;
    public static final int FINAL_MAX = 599;

    /** Precondition check for informative status code. */
    public static void checkIsInformativeStatusCode(final int statusCode) {
        Preconditions.checkArgument(
                isInformativeStatusCode(statusCode),
                "Status code %d is not an informative status code.",
                statusCode);
    }

    /** Precondition check for final status code. */
    public static void checkIsFinalStatusCode(final int statusCode) {
        Preconditions.checkArgument(
                isFinalStatusCode(statusCode),
                "Status code %d is not an final status code.",
                statusCode);
    }

    /** Returns whether the given status code is an informative status code. */
    public static boolean isInformativeStatusCode(final int statusCode) {
        return statusCode >= INFORMATIVE_MIN && statusCode <= INFORMATIVE_MAX;
    }

    /** Returns whether the given status code is a final status code. */
    public static boolean isFinalStatusCode(final int statusCode) {
        return statusCode >= FINAL_MIN && statusCode <= FINAL_MAX;
    }
}
