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

#include "art_dex_file_loader.h"

#include <sys/stat.h>

#include <memory>

#include "android-base/stringprintf.h"
#include "base/file_magic.h"
#include "base/file_utils.h"
#include "base/logging.h"
#include "base/mem_map.h"
#include "base/mman.h"  // For the PROT_* and MAP_* constants.
#include "base/stl_util.h"
#include "base/systrace.h"
#include "base/unix_file/fd_file.h"
#include "base/zip_archive.h"
#include "dex/compact_dex_file.h"
#include "dex/dex_file.h"
#include "dex/dex_file_verifier.h"
#include "dex/standard_dex_file.h"

namespace art {

std::unique_ptr<const DexFile> ArtDexFileLoader::Open(
    const uint8_t* base,
    size_t size,
    const std::string& location,
    uint32_t location_checksum,
    const OatDexFile* oat_dex_file,
    bool verify,
    bool verify_checksum,
    std::string* error_msg,
    std::unique_ptr<DexFileContainer> container) const {
  return OpenCommon(base,
                    size,
                    /*data_base=*/nullptr,
                    /*data_size=*/0,
                    location,
                    location_checksum,
                    oat_dex_file,
                    verify,
                    verify_checksum,
                    error_msg,
                    std::move(container),
                    /*verify_result=*/nullptr);
}

std::unique_ptr<const DexFile> ArtDexFileLoader::Open(const std::string& location,
                                                      uint32_t location_checksum,
                                                      MemMap&& mem_map,
                                                      bool verify,
                                                      bool verify_checksum,
                                                      std::string* error_msg) const {
  ArtDexFileLoader loader(std::move(mem_map), location);
  return loader.Open(location_checksum, verify, verify_checksum, error_msg);
}

bool ArtDexFileLoader::Open(const char* filename,
                            const std::string& location,
                            bool verify,
                            bool verify_checksum,
                            std::string* error_msg,
                            std::vector<std::unique_ptr<const DexFile>>* dex_files) const {
  ArtDexFileLoader loader(filename, location);
  return loader.Open(verify, verify_checksum, error_msg, dex_files);
}

}  // namespace art
