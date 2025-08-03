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
 * Represents the conditions where dexopt should be performed.
 * See `OatFileAssistant::DexOptTrigger`.
 *
 * This is actually used as a bit field, but is declared as an enum because AIDL doesn't support bit
 * fields.
 *
 * @hide
 */
@Backing(type="int")
enum DexoptTrigger {
    COMPILER_FILTER_IS_BETTER = 1 << 0,
    COMPILER_FILTER_IS_SAME = 1 << 1,
    COMPILER_FILTER_IS_WORSE = 1 << 2,
    PRIMARY_BOOT_IMAGE_BECOMES_USABLE = 1 << 3,
    NEED_EXTRACTION = 1 << 4,
}
