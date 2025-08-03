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

package com.android.adservices.service.common;

import android.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

/** Topics part of the app manifest config (<ad-services-config>). */
public final class AppManifestIncludesSdkLibraryConfig {
    private final boolean mContainsByDefault;

    private final @Nullable List<String> mIncludesSdkLibraries;

    /**
     * Constructor.
     *
     * @param containsByDefault whether {@link #contains(String)} should return {@code true} by
     *     default (i.e, when {@link #mIncludesSdkLibraries} is {@code null})
     * @param includesSdkLibraries corresponds to the list in the config.
     */
    public AppManifestIncludesSdkLibraryConfig(
            boolean containsByDefault, @Nullable List<String> includesSdkLibraries) {
        mContainsByDefault = containsByDefault;
        mIncludesSdkLibraries = includesSdkLibraries;
    }

    /**
     * Checks if the given SDK is included.
     *
     * <p>It returns {@code true} if either the {@code id} is explicitly (i.e., when the app config
     * XML has a {@code <includes-sdk-library>} entry with such id) or implicitly (i.e., when the
     * app config XML doesn't have any {@code <includes-sdk-library>} AND this object was
     * constructed with {@code containsByDefault=true}) included.
     */
    public boolean contains(String id) {
        return mIncludesSdkLibraries == null
                ? mContainsByDefault
                : mIncludesSdkLibraries.contains(id);
    }

    @VisibleForTesting
    boolean isEmpty() {
        return mIncludesSdkLibraries == null || mIncludesSdkLibraries.isEmpty();
    }
}
