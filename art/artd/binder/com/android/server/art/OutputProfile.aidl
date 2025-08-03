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
 * Represents output profile file.
 *
 * @hide
 */
parcelable OutputProfile {
    /**
     * The path to the output.
     *
     * Only outputing to a temporary file is supported to avoid race condition.
     */
    com.android.server.art.ProfilePath.TmpProfilePath profilePath;

    /** The permission of the file. */
    com.android.server.art.FsPermission fsPermission;
}
