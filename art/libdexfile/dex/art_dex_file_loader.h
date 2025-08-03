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

#ifndef ART_LIBDEXFILE_DEX_ART_DEX_FILE_LOADER_H_
#define ART_LIBDEXFILE_DEX_ART_DEX_FILE_LOADER_H_

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "base/macros.h"
#include "dex/dex_file_loader.h"

namespace art {

class DexFile;
class DexFileContainer;
class MemMap;
class OatDexFile;
class ZipArchive;

// Class that is used to open dex files and deal with corresponding multidex and location logic.
class ArtDexFileLoader : public DexFileLoader {
 public:
  using DexFileLoader::DexFileLoader;  // Use constructors from base class.
  using DexFileLoader::Open;           // Don't shadow overloads from base class.

  // Old signature preserved for app-compat.
  std::unique_ptr<const DexFile> Open(const uint8_t* base,
                                      size_t size,
                                      const std::string& location,
                                      uint32_t location_checksum,
                                      const OatDexFile* oat_dex_file,
                                      bool verify,
                                      bool verify_checksum,
                                      std::string* error_msg,
                                      std::unique_ptr<DexFileContainer> container) const;

  // Old signature preserved for app-compat.
  std::unique_ptr<const DexFile> Open(const std::string& location,
                                      uint32_t location_checksum,
                                      MemMap&& mem_map,
                                      bool verify,
                                      bool verify_checksum,
                                      std::string* error_msg) const;

  // Old signature preserved for app-compat.
  bool Open(const char* filename,
            const std::string& location,
            bool verify,
            bool verify_checksum,
            std::string* error_msg,
            std::vector<std::unique_ptr<const DexFile>>* dex_files) const;
};

}  // namespace art

#endif  // ART_LIBDEXFILE_DEX_ART_DEX_FILE_LOADER_H_
