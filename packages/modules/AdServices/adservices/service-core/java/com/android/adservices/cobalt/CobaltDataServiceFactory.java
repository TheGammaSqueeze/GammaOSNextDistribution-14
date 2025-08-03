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

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.cobalt.data.CobaltDatabase;
import com.android.cobalt.data.DataService;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Static data and functions related to the Cobalt data service.
 *
 * <p>This may makes sense to have in the Cobalt library itself if manual migrations are needed, but
 * it will be owned by AdServices for now to make the database name and destructive migration
 * explicit.
 */
final class CobaltDataServiceFactory {
    private static final String DB_NAME = FileCompatUtils.getAdservicesFilename("cobalt_db");

    static DataService createDataService(
            @NonNull Context context, @NonNull ExecutorService executorService) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(executorService);

        CobaltDatabase cobaltDatabase =
                FileCompatUtils.roomDatabaseBuilderHelper(context, CobaltDatabase.class, DB_NAME)
                        .fallbackToDestructiveMigration()
                        .build();
        return new DataService(executorService, cobaltDatabase);
    }
}
