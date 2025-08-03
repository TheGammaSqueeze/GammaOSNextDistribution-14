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
 * The result of {@code IArtd.dexopt}.
 *
 * @hide
 */
parcelable ArtdDexoptResult {
    /** True if the operation is cancelled. */
    boolean cancelled;
    /**
     * The wall time of the dex2oat invocation, in milliseconds, or 0 if dex2oat is not run or if
     * failed to get the value.
     */
    long wallTimeMs;
    /**
     * The CPU time of the dex2oat invocation, in milliseconds, or 0 if dex2oat is not run or if
     * failed to get the value.
     */
    long cpuTimeMs;
    /**
     * The total size, in bytes, of the dexopt artifacts, or 0 if dex2oat fails, is cancelled, or
     * is not run.
     */
    long sizeBytes;
    /**
     * The total size, in bytes, of the previous dexopt artifacts that have been replaced, or
     * 0 if there were no previous dexopt artifacts or dex2oat fails, is cancelled, or is not
     * run.
     */
    long sizeBeforeBytes;
}
