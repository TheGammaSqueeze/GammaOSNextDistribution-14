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

package com.android.server.art;

/**
 * The result of {@code IArtd.copyAndRewriteProfileResult}.
 *
 * @hide
 */
parcelable CopyAndRewriteProfileResult {
    /** The status code. */
    Status status;
    /** The error message, if `status` is `BAD_PROFILE`. */
    @utf8InCpp String errorMsg;

    @Backing(type="int")
    enum Status {
        /** The operation succeeded. */
        SUCCESS = 0,
        /** The input does not exist or is empty. This is not considered as an error. */
        NO_PROFILE = 1,
        /** The input is a bad profile. */
        BAD_PROFILE = 2,
    }
}
