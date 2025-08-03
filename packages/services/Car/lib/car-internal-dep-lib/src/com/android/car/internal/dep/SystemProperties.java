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

package com.android.car.internal.dep;

import android.annotation.NonNull;

/**
 * A wrapper for {@link android.os.SystemProperties}.
 *
 * @hide
 */
public final class SystemProperties {

    private SystemProperties() {
        throw new UnsupportedOperationException("SystemProperties must be used statically");
    }

    /**
     * Get the value for the given {@code key}, and return as an integer.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as an integer, or def if the key isn't found or
     *         cannot be parsed
     */
    public static int getInt(@NonNull String key, int def) {
        return android.os.SystemProperties.getInt(key, def);
    }
}
