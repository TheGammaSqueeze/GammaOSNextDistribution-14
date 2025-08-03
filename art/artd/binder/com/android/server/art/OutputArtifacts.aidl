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

/**
 * Represents output dexopt artifacts of a dex file (i.e., ART, OAT, and VDEX files).
 *
 * @hide
 */
parcelable OutputArtifacts {
    /** The path to the output. */
    com.android.server.art.ArtifactsPath artifactsPath;

    parcelable PermissionSettings {
        /**
         * The permission of the directories that contain the artifacts. Has no effect if
         * `artifactsPath.isInDalvikCache` is true.
         */
        com.android.server.art.FsPermission dirFsPermission;

        /** The permission of the files. */
        com.android.server.art.FsPermission fileFsPermission;

        /** The tuple used for looking up for the SELinux context. */
        parcelable SeContext {
            /** The seinfo tag in SELinux policy. */
            @utf8InCpp String seInfo;

            /** The uid that represents the combination of the user id and the app id. */
            int uid;
        }

        /**
         * Determines the SELinux context of the directories and the files. If empty, the default
         * context based on the file path will be used. Has no effect if
         * `artifactsPath.isInDalvikCache` is true.
         */
        @nullable SeContext seContext;
    }

    PermissionSettings permissionSettings;
}
