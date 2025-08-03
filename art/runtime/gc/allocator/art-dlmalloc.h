/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_GC_ALLOCATOR_ART_DLMALLOC_H_
#define ART_RUNTIME_GC_ALLOCATOR_ART_DLMALLOC_H_

#include <cstdint>

// Configure dlmalloc for mspaces.
// Avoid a collision with one used in llvm.
#undef HAVE_MMAP
#define HAVE_MMAP 0
#define HAVE_MREMAP 0
#define HAVE_MORECORE 1
#define MSPACES 1
#define NO_MALLINFO 1
#define ONLY_MSPACES 1
#define MALLOC_INSPECT_ALL 1

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wredundant-decls"
#pragma GCC diagnostic ignored "-Wnull-pointer-arithmetic"
#include "dlmalloc.h"
#pragma GCC diagnostic pop

#endif  // ART_RUNTIME_GC_ALLOCATOR_ART_DLMALLOC_H_
