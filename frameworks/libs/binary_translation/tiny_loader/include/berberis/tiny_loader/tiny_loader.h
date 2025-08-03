/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef BERBERIS_TINY_LOADER_TINY_LOADER_H_
#define BERBERIS_TINY_LOADER_TINY_LOADER_H_

#include "berberis/base/macros.h"

#include <sys/mman.h>

#include <string>

#include "berberis/tiny_loader/loaded_elf_file.h"

class TinyLoader {
 public:
  using mmap64_fn_t =
      void* (*)(void* addr, size_t length, int prot, int flags, int fd, off64_t offset);
  using munmap_fn_t = int (*)(void* addr, size_t length);

  static bool LoadFromFile(const char* path, size_t align, mmap64_fn_t mmap64_fn,
                           munmap_fn_t munmap_fn, LoadedElfFile* loaded_elf_file,
                           std::string* error_msg);

  static bool LoadFromFile(const char* path, LoadedElfFile* loaded_elf_file,
                           std::string* error_msg) {
    return LoadFromFile(path, 0, mmap64, munmap, loaded_elf_file, error_msg);
  }

  static bool LoadFromMemory(const char* path, void* address, size_t size,
                             LoadedElfFile* loaded_elf_file, std::string* error_msg);

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(TinyLoader);
};

#endif  // BERBERIS_TINY_LOADER_TINY_LOADER_H_
