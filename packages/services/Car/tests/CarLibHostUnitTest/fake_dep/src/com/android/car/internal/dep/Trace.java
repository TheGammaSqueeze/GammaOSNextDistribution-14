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
 * A fake implementation for {@link android.os.Trace}.
 */
public final class Trace {

    private Trace() {
        throw new UnsupportedOperationException("Trace must be used statically");
    }

    /**
     * Begins a trace section.
     *
     * Do nothing in the fake implementation.
     */
    public static void beginSection(@NonNull String sectionName) {
        // Do nothing.
    }

    /**
     * Ends a trace section.
     *
     * Do nothing in the fake implementation.
     */
    public static void endSection() {
        // Do nothing.
    }
}
