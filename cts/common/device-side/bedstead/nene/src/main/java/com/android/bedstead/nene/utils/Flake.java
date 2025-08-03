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

package com.android.bedstead.nene.utils;

/**
 * Utilities for dealing with flakes.
 *
 * <p>Calls to this code should be removed and the core cause of the flake fixed. Using this class
 * is not a permanent solution to any flakes.
 */
public final class Flake {
    private Flake() {

    }

    /**
     * Repeat {@code r} some number of times.
     */
    public static void repeat(Runnable r, int times) {
        for (int i = 0; i < times; i++) {
            r.run();
        }
    }

    /**
     * Repeat {@code r} 10 times.
     */
    public static void repeat(Runnable r) {
        repeat(r, 10);
    }
}
