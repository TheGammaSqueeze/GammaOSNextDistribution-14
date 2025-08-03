/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appsearch;

import android.annotation.NonNull;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityChecker;
import com.android.server.appsearch.stats.PlatformLogger;
import com.android.server.appsearch.visibilitystore.VisibilityCheckerImpl;

import java.util.concurrent.Executor;

/** This is a factory class for implementations needed based on environment for service code. */
public final class AppSearchComponentFactory {
    private static volatile FrameworkAppSearchConfig mConfigInstance;

    public static FrameworkAppSearchConfig getConfigInstance(@NonNull Executor executor) {
        FrameworkAppSearchConfig localRef = mConfigInstance;
        if (localRef == null) {
            synchronized (AppSearchComponentFactory.class) {
                localRef = mConfigInstance;
                if (localRef == null) {
                    mConfigInstance = localRef = FrameworkAppSearchConfigImpl
                            .getInstance(executor);
                }
            }
        }
        return localRef;
    }

    @VisibleForTesting
    static void setConfigInstanceForTest(
            @NonNull FrameworkAppSearchConfig appSearchConfig) {
        synchronized (AppSearchComponentFactory.class) {
            mConfigInstance = appSearchConfig;
        }
    }

    public static InternalAppSearchLogger createLoggerInstance(
            @NonNull Context context, @NonNull FrameworkAppSearchConfig config) {
        return new PlatformLogger(context, config);
    }

    public static VisibilityChecker createVisibilityCheckerInstance(@NonNull Context context) {
        return new VisibilityCheckerImpl(context);
    }

    private AppSearchComponentFactory() {
    }
}
