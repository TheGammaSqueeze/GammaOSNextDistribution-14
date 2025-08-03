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

package com.android.bedstead.nene.utils;

/**
 * Test utilities related to assertions
 */
public final class Assert {

    private Assert() {

    }

    /**
     * Assert that a particular exception type is thrown.
     */
    public static <E extends Throwable> E assertThrows(Class<E> e, Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected to throw " + e + " but nothing thrown");
        } catch (Throwable expected) {
            if (e.isInstance(expected)) {
                return (E) expected;
            }
            throw expected;
        }
    }

}
