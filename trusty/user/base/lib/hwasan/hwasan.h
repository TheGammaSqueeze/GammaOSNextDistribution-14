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

#pragma once

#include "stddef.h"
#include "stdint.h"

extern const uint64_t hwasan_addr_tag_shift;
extern const uint64_t hwasan_addr_tag_mask;

extern uintptr_t __hwasan_shadow_memory_dynamic_address;

void __hwasan_init(void);

void* __hwasan_memset(uintptr_t ptr, int val, size_t size);
void* __hwasan_memcpy(uintptr_t dst, const uintptr_t src, size_t size);
void* __hwasan_memmove(uintptr_t dst, const uintptr_t src, size_t size);

void __hwasan_loadN(uintptr_t ptr, size_t size);
void __hwasan_storeN(uintptr_t ptr, size_t size);

void __hwasan_tag_mismatch(void);
