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

package com.android.adservices.shared.util;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state.
 *
 * <p>Note: This class contains method(s) added after S+ in modules-utils-preconditions library. Use
 * this class only when accessing Preconditions methods not available in R. For example, {@link
 * #checkState(boolean, String, Object...)}.
 */
public final class Preconditions {

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param messageTemplate a printf-style message template to use if the check fails; will be
     *     converted to a string using {@link String#format(String, Object...)}
     * @param messageArgs arguments for {@code messageTemplate}
     * @throws IllegalStateException if {@code expression} is false
     */
    @FormatMethod
    public static void checkState(
            boolean expression, @FormatString String messageTemplate, Object... messageArgs) {
        if (!expression) {
            throw new IllegalStateException(String.format(messageTemplate, messageArgs));
        }
    }
}
