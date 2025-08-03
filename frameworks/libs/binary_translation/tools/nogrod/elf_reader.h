/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef __NOGROD_ELF_READER_
#define __NOGROD_ELF_READER_

#include <cstdint>
#include <string>
#include <vector>

#include "dwarf_info.h"

namespace nogrod {

class ElfFile {
 public:
  ElfFile() = default;
  virtual ~ElfFile() = default;

  [[nodiscard]] static std::unique_ptr<ElfFile> Load(const char* path, std::string* error_msg);

  [[nodiscard]] virtual bool ReadExportedSymbols(std::vector<std::string>* symbols,
                                                 std::string* error_msg) = 0;
  [[nodiscard]] virtual std::unique_ptr<DwarfInfo> ReadDwarfInfo(std::string* error_msg) = 0;
};

}  // namespace nogrod
#endif  // __NOGROD_ELF_READER_
