/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.testapi;

import android.annotation.NonNull;
import android.car.util.concurrent.AsyncFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provides common helpers for generic car-related classes.
 */
public final class CarTestingHelper {

    private static final long ASYNC_TIMEOUT_MS = 500;

    /**
     * Gets the result of a future, or throw a {@link IllegalStateException} if it times out after
     * {@value #ASYNC_TIMEOUT_MS} ms.
     */
    @NonNull
    public static <T> T getResult(@NonNull AsyncFuture<T> future)
            throws InterruptedException, ExecutionException {
        return getResult(future, ASYNC_TIMEOUT_MS);
    }

    /**
     * Gets the result of a future, or throw a {@link IllegalStateException} if it times out.
     */
    @NonNull
    public static <T> T getResult(@NonNull AsyncFuture<T> future, long timeoutMs) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("future interrupted", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("future not called in " + ASYNC_TIMEOUT_MS + "ms", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("failed to get future", e);
        }
    }

    private CarTestingHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }

}
