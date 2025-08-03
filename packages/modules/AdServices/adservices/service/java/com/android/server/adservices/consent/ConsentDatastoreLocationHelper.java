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

package com.android.server.adservices.consent;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

class ConsentDatastoreLocationHelper {
    private static final String CONSENT_DIR = "consent";

    /** Get the consent data store location, and initialize the path if not exist. */
    @VisibleForTesting
    @NonNull
    static String getConsentDataStoreDir(@NonNull String baseDir, int userIdentifier) {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");
        return baseDir + "/" + userIdentifier + "/" + CONSENT_DIR;
    }

    static String getConsentDataStoreDirAndCreateDir(@NonNull String baseDir, int userIdentifier)
            throws IOException {
        String consentDataStoreDir = getConsentDataStoreDir(baseDir, userIdentifier);
        final Path packageDir = Paths.get(consentDataStoreDir);
        if (!Files.exists(packageDir)) {
            Files.createDirectories(packageDir);
        }
        return consentDataStoreDir;
    }
}
