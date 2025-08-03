/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.inputmethod.cts.util;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A utility class to mix-in cleanup operations into any existing class by using
 * {@link AutoCloseable} protocol.
 *
 * @param <T> Class to be cleaned up.
 */
public final class AutoCloseableWrapper<T> implements AutoCloseable {

    /**
     * Instantiates {@link AutoCloseableWrapper} object.
     *
     * @param object Object that needs to be cleaned up.
     * @param closingFunction Operation to clean up the object.
     * @param <U> Type of the object to be cleaned up.
     * @return {@link AutoCloseableWrapper} that wraps {@code object}.
     */
    @AnyThread
    public static <U> AutoCloseableWrapper<U> create(@Nullable U object,
            @NonNull ClosingFunction<U> closingFunction) {
        Objects.requireNonNull(object, "object cannot be null");
        Objects.requireNonNull(closingFunction, "closingFunction cannot be null");
        return new AutoCloseableWrapper<>(object, closingFunction);
    }

    /**
     * An internal utility interface in case the closing operation throws {@link Exception}.
     *
     * @param <U> Class to be cleaned up.
     */
    @FunctionalInterface
    public interface ClosingFunction<U> {
        /**
         * Perform cleanup operation.
         *
         * @param object Object to be cleaned up.
         * @throws Exception For whatever reason this operations has failed.
         */
        void close(U object) throws Exception;
    }

    @NonNull
    private final T mObject;

    @NonNull
    private final ClosingFunction<T> mCloser;

    private AutoCloseableWrapper(@NonNull T object, @NonNull ClosingFunction<T> closer) {
        mObject = object;
        mCloser = closer;
    }

    /**
     * @return The wrapped object.
     */
    @AnyThread
    @NonNull
    public T get() {
        return mObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        mCloser.close(mObject);
    }
}
