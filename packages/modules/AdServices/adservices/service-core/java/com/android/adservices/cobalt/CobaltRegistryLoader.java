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

package com.android.adservices.cobalt;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.AssetManager;

import com.android.cobalt.domain.Project;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.CobaltRegistry;
import com.google.common.io.ByteStreams;

import java.io.InputStream;

/** Loads the Cobalt registry from a APK asset. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class CobaltRegistryLoader {
    private static final String REGISTRY_ASSET_FILE = "cobalt/cobalt_registry.binarypb";

    /**
     * Get the Cobalt registry from the APK asset directory.
     *
     * @return the CobaltRegistry
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public static Project getRegistry(@NonNull Context context)
            throws CobaltInitializationException {
        if (!CobaltRegistryValidated.IS_REGISTRY_VALIDATED) {
            throw new AssertionError(
                    "Cobalt registry was not validated at build time, something is very wrong");
        }

        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open(REGISTRY_ASSET_FILE)) {
            CobaltRegistry registry =
                    CobaltRegistry.parseFrom(ByteStreams.toByteArray(inputStream));
            return Project.create(registry);
        } catch (Exception e) {
            throw new CobaltInitializationException("Exception while reading registry", e);
        }
    }
}
