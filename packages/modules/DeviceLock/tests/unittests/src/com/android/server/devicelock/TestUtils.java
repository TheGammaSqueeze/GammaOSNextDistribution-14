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

package com.android.server.devicelock;

import org.junit.function.ThrowingRunnable;
import org.junit.runners.model.TestTimedOutException;

/**
 * Utils useful for testing
 */
final class TestUtils {
    private TestUtils() {}

    /**
     * Make sure that a runnable eventually finishes without throwing an exception.
     */
    static void eventually(ThrowingRunnable r, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                return;
            } catch (TestTimedOutException e) {
                throw new RuntimeException(e);
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < timeoutMillis) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
