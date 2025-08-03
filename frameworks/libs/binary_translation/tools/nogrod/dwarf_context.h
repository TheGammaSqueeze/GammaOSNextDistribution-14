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

#ifndef NOGROD_DWARF_CONTEXT_
#define NOGROD_DWARF_CONTEXT_

#include <cstdint>
#include <optional>
#include <string>

#include <berberis/base/macros.h>

#include "byte_input_stream.h"
#include "string_offset_table.h"
#include "string_table.h"

namespace nogrod {

class DwarfContext {
 public:
  DwarfContext(ByteInputStream* dwarf_info_stream,
               const StringTable* debug_str_table,
               const std::optional<StringOffsetTable>& string_offset_table)
      : dwarf_info_stream_{dwarf_info_stream},
        debug_str_table_{debug_str_table},
        string_offset_table_{string_offset_table} {}

  DwarfContext(const DwarfContext&) = delete;
  const DwarfContext& operator=(const DwarfContext&) = delete;
  DwarfContext(DwarfContext&&) = delete;
  DwarfContext& operator=(DwarfContext&&) = delete;

  [[nodiscard]] const StringTable* debug_str_table() const { return debug_str_table_; }
  [[nodiscard]] const std::optional<StringOffsetTable>& string_offset_table() const {
    return string_offset_table_;
  }

  [[nodiscard]] ByteInputStream* info_stream() { return dwarf_info_stream_; }

  [[nodiscard]] std::optional<uint64_t> str_offsets_base() const { return str_offsets_base_; }

  // DW_AT_str_offsets_base may be defined in Compilation Unit, in which case we set it here.
  void SetStrOffsetsBase(uint64_t str_offsets_base) { str_offsets_base_ = str_offsets_base; }
  // DW_AT_str_offsets_base is reset once new compilation unit starts
  void ResetStrOffsetsBase() { str_offsets_base_.reset(); }

 private:
  ByteInputStream* dwarf_info_stream_;
  const StringTable* debug_str_table_;
  const std::optional<StringOffsetTable>& string_offset_table_;
  std::optional<uint64_t> str_offsets_base_{};
};

}  // namespace nogrod
#endif  // NOGROD_DWARF_CONTEXT_
