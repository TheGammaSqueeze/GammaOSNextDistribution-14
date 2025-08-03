/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "berberis/base/exec_region_anonymous.h"

#include <sys/mman.h>

#include "berberis/base/mmap.h"
#include "fd.h"

namespace berberis {

ExecRegion ExecRegionAnonymousFactory::Create(size_t size) {
  size = AlignUpPageSize(size);

  auto fd = CreateMemfdOrDie("exec");
  FtruncateOrDie(fd, static_cast<off64_t>(size));

  ExecRegion result{
      static_cast<uint8_t*>(MmapImplOrDie(
          {.size = size, .prot = PROT_READ | PROT_EXEC, .flags = MAP_SHARED, .fd = fd})),
      static_cast<uint8_t*>(MmapImplOrDie(
          {.size = size, .prot = PROT_READ | PROT_WRITE, .flags = MAP_SHARED, .fd = fd})),
      size};

  CloseUnsafe(fd);
  return result;
}

}  // namespace berberis
