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

package com.android.ondevicepersonalization.services.data;

import android.annotation.NonNull;
import android.content.ComponentName;

import java.util.Objects;

/** Database utilities */
public class DbUtils {
    /** Returns the table name for a service */
    public static String getTableName(
            @NonNull String prefix, @NonNull ComponentName owner, @NonNull String certDigest) {
        String ownerStr = owner.getPackageName() + "__" + owner.getShortClassName();
        ownerStr = ownerStr.replace(".", "_");
        return Objects.requireNonNull(prefix) + "_" + ownerStr + "_"
                + Objects.requireNonNull(certDigest);
    }

    /** Maps a service to a unique string for database tables and filenames */
    public static String toTableValue(@NonNull ComponentName owner) {
        return owner.flattenToString();
    }

    /** Parses the string representation of a service. */
    public static ComponentName fromTableValue(@NonNull String tableValue) {
        return ComponentName.unflattenFromString(Objects.requireNonNull(tableValue));
    }

    private DbUtils() {}
}
