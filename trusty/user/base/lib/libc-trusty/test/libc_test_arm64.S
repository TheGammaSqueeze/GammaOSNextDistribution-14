/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <lk/asm.h>

.section .text

/**
 * void** guard_region_ptr(void) - return pointer to scs guard region
 */
FUNCTION(guard_region_ptr)
    mov x0, x18
    ret

SECTION_GNU_NOTE_PROPERTY_AARCH64_FEATURES(GNU_NOTE_FEATURE_AARCH64_BTI)

