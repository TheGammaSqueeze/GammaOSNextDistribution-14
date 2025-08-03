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
package com.android.adservices.shared.testing.common;

import android.app.UiAutomation;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.function.Supplier;

/** Provides helpers for Shell-related operations. */
public final class ShellHelper {

    // TODO(b/306522832): add unit tests
    // NOTE: copied from ShellIdentityUtils to avoid importing compatibility-device-util-axt
    /**
     * Run an arbitrary piece of code while holding shell permissions.
     *
     * @param supplier an expression that performs the desired operation with shell permissions
     * @param <T> the return type of the expression
     * @return the return value of the expression
     */
    public static <T> T invokeWithShellPermissions(Supplier<T> supplier) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            return supplier.get();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private ShellHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
