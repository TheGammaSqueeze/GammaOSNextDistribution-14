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

#include "berberis/base/exec_region_elf_backed.h"

#include <android/dlext.h>
#include <dlfcn.h>
#include <sys/mman.h>

#include "berberis/base/bit_util.h"
#include "berberis/base/mmap.h"

#include "fd.h"

// Note that we have to use absolute path for ANDROID_DLEXT_FORCE_LOAD to work correctly
// otherwise searching by soname will trigger and the flag will have no effect.
#if defined(__LP64__)
const constexpr char* kExecRegionLibraryPath = "/system/lib64/libberberis_exec_region.so";
#else
const constexpr char* kExecRegionLibraryPath = "/system/lib/libberberis_exec_region.so";
#endif

const constexpr char* kRegionStartSymbolName = "exec_region_start";
const constexpr char* kRegionEndSymbolName = "exec_region_end";

namespace berberis {

ExecRegion ExecRegionElfBackedFactory::Create(size_t size) {
  size = AlignUpPageSize(size);

  android_dlextinfo dlextinfo{.flags = ANDROID_DLEXT_FORCE_LOAD};
  void* handle = android_dlopen_ext(kExecRegionLibraryPath, RTLD_NOW, &dlextinfo);
  if (handle == nullptr) {
    FATAL("Couldn't load \"%s\": %s", kExecRegionLibraryPath, dlerror());
  }
  void* region_start = dlsym(handle, kRegionStartSymbolName);
  CHECK(region_start != nullptr);
  auto region_start_addr = bit_cast<uintptr_t>(region_start);
  CHECK(region_start_addr % kPageSize == 0);

  void* region_end = dlsym(handle, kRegionEndSymbolName);
  CHECK(region_end != nullptr);
  auto region_end_addr = bit_cast<uintptr_t>(region_end);
  CHECK(region_end_addr % kPageSize == 0);
  size_t region_size = region_end_addr - region_start_addr;
  CHECK_GE(region_size, size);

  auto fd = CreateMemfdOrDie("exec");
  FtruncateOrDie(fd, static_cast<off64_t>(region_size));

  ExecRegion result{
      static_cast<uint8_t*>(MmapImplOrDie({.addr = region_start,
                                           .size = region_size,
                                           .prot = PROT_READ | PROT_EXEC,
                                           .flags = MAP_FIXED | MAP_SHARED,
                                           .fd = fd})),
      static_cast<uint8_t*>(MmapImplOrDie(
          {.size = region_size, .prot = PROT_READ | PROT_WRITE, .flags = MAP_SHARED, .fd = fd})),
      region_size};

  CloseUnsafe(fd);
  return result;
}

}  // namespace berberis
