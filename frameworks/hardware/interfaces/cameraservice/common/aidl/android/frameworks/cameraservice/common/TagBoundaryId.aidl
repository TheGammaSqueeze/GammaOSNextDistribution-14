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

package android.frameworks.cameraservice.common;

/**
 * Boundaries for VendorTag tagIds.
 */
@VintfStability
@Backing(type="long")
enum TagBoundaryId {
    /**
     * First valid tag id for android-defined tags.
     */
    AOSP = 0x0,
    /**
     * First valid tag id for vendor extension tags.
     */
    VENDOR = 0x80000000L // 1 << 31
}
