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

#include <lk/compiler.h>
#include <sys/types.h>
#include <uapi/mm.h>

__BEGIN_CDECLS

/**
 * memref_create - create a handle to a region of your address space
 * @addr:      Where to start the region. Must be page aligned.
 * @size:      How many bytes to capture. Must be a multiple of page size.
 * @mmap_prot: MMAP_FLAG_PROT_* attributes for the handle. Must be more
 *             restrictive than how the memory is mapped in your process.
 *
 * Creates a shareable, mappable handle to a region of your address space.
 * Currently, this will only work on regions in the data segment, the heap,
 * or which have been mapped in through mapping a memref.
 *
 * Return: Negative error code on error, otherwise the requested handle.
 */
int memref_create(void* addr, size_t size, uint32_t mmap_prot);

__END_CDECLS
