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
 * Miscellaneous options for merging profiles. Every field corresponds to a profman command line
 * flag.
 *
 * DO NOT add fields for flags that artd can determine directly with trivial logic. That includes
 * static flags, and flags that only depend on system properties or other passed parameters.
 *
 * All fields are required.
 *
 * @hide
 */
parcelable MergeProfileOptions {
    /** --force-merge-and-analyze */
    boolean forceMerge;
    /** --boot-image-merge */
    boolean forBootImage;
    /** --dump-only */
    boolean dumpOnly;
    /** --dump-classes-and-methods */
    boolean dumpClassesAndMethods;
}
