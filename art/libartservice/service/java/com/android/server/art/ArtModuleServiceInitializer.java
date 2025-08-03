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

package com.android.server.art;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.ArtModuleServiceManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;

/**
 * Class for performing registration for the ART mainline module.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ArtModuleServiceInitializer {
    private ArtModuleServiceInitializer() {}

    @NonNull private static Object sLock = new Object();
    @GuardedBy("sLock") @Nullable private static ArtModuleServiceManager sArtModuleServiceManager;

    /**
     * Sets an instance of {@link ArtModuleServiceManager} that allows the ART mainline module to
     * obtain ART binder services. This is called by the platform during the system server
     * initialization.
     */
    public static void setArtModuleServiceManager(
            @NonNull ArtModuleServiceManager artModuleServiceManager) {
        synchronized (sLock) {
            if (sArtModuleServiceManager != null) {
                throw new IllegalStateException("ArtModuleServiceManager is already set");
            }
            sArtModuleServiceManager = artModuleServiceManager;
        }
    }

    /** @hide */
    @NonNull
    public static ArtModuleServiceManager getArtModuleServiceManager() {
        synchronized (sLock) {
            return Objects.requireNonNull(sArtModuleServiceManager);
        }
    }
}
