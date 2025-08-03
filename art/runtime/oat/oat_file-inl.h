/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_OAT_OAT_FILE_INL_H_
#define ART_RUNTIME_OAT_OAT_FILE_INL_H_

#include "oat_file.h"

#include "base/utils.h"
#include "oat_quick_method_header.h"
#include "runtime-inl.h"

namespace art HIDDEN {

inline const OatQuickMethodHeader* OatFile::OatMethod::GetOatQuickMethodHeader() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return nullptr;
  }
  // Return a pointer to the packed struct before the code.
  return reinterpret_cast<const OatQuickMethodHeader*>(code) - 1;
}

inline uint32_t OatFile::OatMethod::GetOatQuickMethodHeaderOffset() const {
  const OatQuickMethodHeader* method_header = GetOatQuickMethodHeader();
  if (method_header == nullptr) {
    return 0u;
  }
  return reinterpret_cast<const uint8_t*>(method_header) - begin_;
}

inline size_t OatFile::OatMethod::GetFrameSizeInBytes() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return 0u;
  }
  return reinterpret_cast<const OatQuickMethodHeader*>(code)[-1].GetFrameInfo().FrameSizeInBytes();
}

inline uint32_t OatFile::OatMethod::GetCoreSpillMask() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return 0u;
  }
  return reinterpret_cast<const OatQuickMethodHeader*>(code)[-1].GetFrameInfo().CoreSpillMask();
}

inline uint32_t OatFile::OatMethod::GetFpSpillMask() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return 0u;
  }
  return reinterpret_cast<const OatQuickMethodHeader*>(code)[-1].GetFrameInfo().FpSpillMask();
}

inline uint32_t OatFile::OatMethod::GetVmapTableOffset() const {
  const uint8_t* vmap_table = GetVmapTable();
  return static_cast<uint32_t>(vmap_table != nullptr ? vmap_table - begin_ : 0u);
}

inline const uint8_t* OatFile::OatMethod::GetVmapTable() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return nullptr;
  }
  uint32_t offset = reinterpret_cast<const OatQuickMethodHeader*>(code)[-1].GetCodeInfoOffset();
  if (UNLIKELY(offset == 0u)) {
    return nullptr;
  }
  return reinterpret_cast<const uint8_t*>(code) - offset;
}

inline uint32_t OatFile::OatMethod::GetQuickCodeSize() const {
  const void* code = EntryPointToCodePointer(GetQuickCode());
  if (code == nullptr) {
    return 0u;
  }
  return reinterpret_cast<const OatQuickMethodHeader*>(code)[-1].GetCodeSize();
}

inline const void* OatFile::OatMethod::GetQuickCode() const {
  if (code_offset_ == 0) {
    return nullptr;
  }
  return reinterpret_cast<const void *>(begin_ + code_offset_);
}

inline const OatFile::BssMappingInfo* OatFile::FindBcpMappingInfo(const DexFile* dex_file) const {
  ArrayRef<const OatFile::BssMappingInfo> mapping_info_vector(GetBcpBssInfo());
  ArrayRef<const DexFile* const> bcp_dexfiles(
      Runtime::Current()->GetClassLinker()->GetBootClassPath());
  // Create a sub array to limit search range.
  bcp_dexfiles = bcp_dexfiles.SubArray(/*pos=*/ 0u, mapping_info_vector.size());
  auto it = std::find(bcp_dexfiles.begin(), bcp_dexfiles.end(), dex_file);
  if (it != bcp_dexfiles.end()) {
    return &mapping_info_vector[std::distance(bcp_dexfiles.begin(), it)];
  } else {
    return nullptr;
  }
}

}  // namespace art

#endif  // ART_RUNTIME_OAT_OAT_FILE_INL_H_
