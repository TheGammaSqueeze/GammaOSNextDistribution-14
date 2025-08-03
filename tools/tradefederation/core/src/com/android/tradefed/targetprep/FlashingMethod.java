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

package com.android.tradefed.targetprep;

/**
 * An enum to describe the method used to flash device under test
 */
public enum FlashingMethod {
    /**
     * The fallback for not explicitly tracked flashing method
     */
    UNKNOWN,

    /**
     * The fallback for fastboot flashing but no further categorization
     */
    FASTBOOT_UNCATEGORIZED,

    /**
     * The device was flashed via a `fastboot update` command with a device image zip
     */
    FASTBOOT_UPDATE,

    /**
     * The device was flashed via a `fastboot flashall` command on a directory of partition images
     * which is mounted from a device image zip file via fuse-zip
     */
    FASTBOOT_FLASH_ALL_FUSE_ZIP,

    /** The device was flashed via flashstation using the cl_flashstation script */
    FLASHSTATION
}
