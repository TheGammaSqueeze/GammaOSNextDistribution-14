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

package com.android.server.art;

import android.annotation.NonNull;
import android.annotation.Nullable;

import dalvik.system.VMRuntime;

/**
 * JNI methods for ART Service, with wrappers added for testability because Mockito cannot mock JNI
 * methods.
 *
 * @hide
 */
public class ArtJni {
    static {
        if (VMRuntime.getRuntime().vmLibrary().equals("libartd.so")) {
            System.loadLibrary("artserviced");
        } else {
            System.loadLibrary("artservice");
        }
    }

    private ArtJni() {}

    /**
     * Returns an error message if the given dex path is invalid, or null if the validation passes.
     */
    @Nullable
    public static String validateDexPath(@NonNull String dexPath) {
        return validateDexPathNative(dexPath);
    }

    /**
     * Returns an error message if the given class loader context is invalid, or null if the
     * validation passes.
     */
    @Nullable
    public static String validateClassLoaderContext(
            @NonNull String dexPath, @NonNull String classLoaderContext) {
        return validateClassLoaderContextNative(dexPath, classLoaderContext);
    }

    /**
     * Returns the name of the Garbage Collector currently in use in the Android Runtime.
     */
    @NonNull
    public static String getGarbageCollector() {
        return getGarbageCollectorNative();
    }

    @Nullable private static native String validateDexPathNative(@NonNull String dexPath);
    @Nullable
    private static native String validateClassLoaderContextNative(
            @NonNull String dexPath, @NonNull String classLoaderContext);
    @NonNull private static native String getGarbageCollectorNative();
}
