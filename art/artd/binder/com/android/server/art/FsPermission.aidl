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
 * Represents the Linux filesystem permission of a file or a directory.
 *
 * If both `uid` and `gid` are negative, no `chown` will be performed.
 *
 * If none of the booleans are set, the default permission bits are `rw-r-----` for a file, and
 * `rwxr-x---` for a directory.
 *
 * @hide
 */
parcelable FsPermission {
    int uid;
    int gid;
    /**
     * Whether the file/directory should have the "read" bit for "others" (S_IROTH).
     */
    boolean isOtherReadable;
    /**
     * Whether the file/directory should have the "execute" bit for "others" (S_IXOTH).
     */
    boolean isOtherExecutable;
}
