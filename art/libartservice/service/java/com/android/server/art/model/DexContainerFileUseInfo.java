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

package com.android.server.art.model;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.UserHandle;

import com.android.internal.annotations.Immutable;

import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Set;

/**
 * The information about the use of a dex container file.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
@AutoValue
public abstract class DexContainerFileUseInfo {
    /** @hide */
    protected DexContainerFileUseInfo() {}

    /** @hide */
    public static @NonNull DexContainerFileUseInfo create(@NonNull String dexContainerFile,
            @NonNull UserHandle userHandle, @NonNull Set<String> loadingPackages) {
        return new AutoValue_DexContainerFileUseInfo(dexContainerFile, userHandle, loadingPackages);
    }

    /** The absolute path to the dex container file. */
    public abstract @NonNull String getDexContainerFile();

    /** The {@link UserHandle} that represents the human user who loads the dex file. */
    public abstract @NonNull UserHandle getUserHandle();

    /** The names of packages that load the dex file. Guaranteed to be non-empty. */
    public abstract @NonNull Set<String> getLoadingPackages();
}
