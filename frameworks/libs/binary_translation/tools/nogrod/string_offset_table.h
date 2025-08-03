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

#ifndef NOGROD_STRING_OFFSET_TABLE_H_
#define NOGROD_STRING_OFFSET_TABLE_H_

#include <cstdint>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "dwarf_constants.h"

#include "buffer.h"

namespace nogrod {

using berberis::bit_cast;

// The class provides assess to .debug_str_offsets section of the elf-file
class StringOffsetTable {
 public:
  StringOffsetTable() : format_{DwarfFormat::k32Bit} {}
  explicit StringOffsetTable(Buffer<uint8_t> buffer)
      : buffer_{std::move(buffer)}, format_{DetectDwarfFormat(buffer_.data(), buffer_.size())} {}

  StringOffsetTable(const StringOffsetTable&) = delete;
  StringOffsetTable& operator=(const StringOffsetTable&) = delete;

  StringOffsetTable(StringOffsetTable&&) = default;
  StringOffsetTable& operator=(StringOffsetTable&&) = default;

  // According to DWARF5 spec (7.26) DW_AT_str_offsets_base attribute
  // points to the first entry following the header which is 8 for 32bit
  // format and 16 for 64bit. We do not enforce it here, since this might
  // not always be the case. But we do check that the base offset is greater
  // than or equals to header size.
  [[nodiscard]] uint64_t GetStringOffset(size_t offsets_base, size_t index) const {
    constexpr const size_t k64BitHeaderSize = 16;
    constexpr const size_t k32BitHeaderSize = 8;

    switch (format_) {
      case DwarfFormat::k64Bit:
        CHECK_GE(offsets_base, k64BitHeaderSize);
        return GetOffsetAt<uint64_t>(offsets_base, index);
      case DwarfFormat::k32Bit:
        CHECK_GE(offsets_base, k32BitHeaderSize);
        return GetOffsetAt<uint32_t>(offsets_base, index);
    }
    UNREACHABLE();
  }

 private:
  static DwarfFormat DetectDwarfFormat(const uint8_t* table, size_t size) {
    CHECK_GE(size, sizeof(uint32_t));
    uint32_t size32 = *bit_cast<const uint32_t*>(table);
    if (size32 == uint32_t{0xFFFF'FFFFu}) {
      return DwarfFormat::k64Bit;
    } else {
      return DwarfFormat::k32Bit;
    }
  }

  template <typename T>
  [[nodiscard]] T GetOffsetAt(size_t offsets_base, size_t index) const {
    CHECK_EQ(offsets_base % sizeof(T), 0);
    uint64_t offset = offsets_base + index * sizeof(T);
    CHECK_LE(offset + sizeof(T), buffer_.size());
    return *bit_cast<const T*>(buffer_.data() + offset);
  }

  Buffer<uint8_t> buffer_;
  DwarfFormat format_;
};

}  // namespace nogrod
#endif  // NOGROD_STRING_OFFSET_TABLE_H_
