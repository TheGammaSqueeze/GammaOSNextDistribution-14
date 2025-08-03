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
package com.android.cts.verifier;

import android.content.ComponentName;

/**
 * Provides common helper methods.
 */
public final class Utils {

    /**
     * Helper to get {@link #flattenToShortString()} in a {@link ComponentName} reference that can
     * be {@code null}.
     *
     * @hide
     */
    public static String flattenToShortString(ComponentName componentName) {
        return componentName == null ? null : componentName.flattenToShortString();
    }

    private Utils() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
