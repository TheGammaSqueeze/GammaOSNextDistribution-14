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

package com.android.server.adservices.rollback;

import android.annotation.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

class RollbackHandlingDatastoreLocationHelper {
    private static final String ROLLBACK_HANDLING_DIR = "rollback";

    /**
     * Get the rollback handling data store location, and initialize the path if it does not exist.
     */
    static String getRollbackHandlingDataStoreDirAndCreateDir(
            @NonNull String baseDir, int userIdentifier) throws IOException {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");
        String rollbackHandlingDataStoreDir =
                baseDir + "/" + userIdentifier + "/" + ROLLBACK_HANDLING_DIR;
        final Path packageDir = Paths.get(rollbackHandlingDataStoreDir);
        if (!Files.exists(packageDir)) {
            Files.createDirectories(packageDir);
        }
        return rollbackHandlingDataStoreDir;
    }
}
