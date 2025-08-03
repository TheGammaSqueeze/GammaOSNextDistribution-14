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

package com.android.adservices.service.common;

import android.os.Binder;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility class for reading {@link android.provider.DeviceConfig} flags from the Binder thread.
 *
 * <p>The caller in the {@link Binder} thread does not have the {@link
 * android.Manifest.permission#READ_DEVICE_CONFIG} permission, so the calling identity needs to be
 * temporarily cleared in order to read with the local process's permissions.
 */
public class BinderFlagReader {
    /**
     * Reads and returns the given flag from the {@link Binder} thread.
     *
     * <p>The {@link Binder} thread does not have the {@link
     * android.Manifest.permission#READ_DEVICE_CONFIG} permission, so the calling identity needs to
     * be temporarily cleared in order to read with the local process's permissions.
     *
     * @param <T> type returned by the {@code flagSupplier}
     */
    public static <T> T readFlag(@NonNull Supplier<T> flagSupplier) {
        Objects.requireNonNull(flagSupplier);
        final long token = Binder.clearCallingIdentity();
        try {
            return flagSupplier.get();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
