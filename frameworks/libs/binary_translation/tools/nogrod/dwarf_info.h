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

#ifndef NOGROD_DWARF_INFO_
#define NOGROD_DWARF_INFO_

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "buffer.h"
#include "dwarf_abbrev.h"
#include "string_offset_table.h"
#include "string_table.h"

#include "berberis/base/macros.h"

namespace nogrod {

class DwarfDie final {
 public:
  DwarfDie(const DwarfCompilationUnitHeader* cu,
           const DwarfDie* parent,
           uint64_t offset,
           uint16_t tag);

  DwarfDie(const DwarfDie&) = delete;
  const DwarfDie& operator=(const DwarfDie&) = delete;

  void AddAttribute(DwarfAttribute* attribute);
  void AddChild(const DwarfDie* child);

  [[nodiscard]] uint16_t tag() const { return tag_; }
  [[nodiscard]] uint64_t offset() const { return offset_; }

  [[nodiscard]] const DwarfCompilationUnitHeader* compilation_unit_header() const {
    return compilation_unit_header_;
  }

  [[nodiscard]] const DwarfDie* parent() const { return parent_; }
  [[nodiscard]] const std::vector<const DwarfDie*>& children() const { return children_; }

  [[nodiscard]] std::optional<std::string> GetStringAttribute(uint16_t attr_name) const;
  [[nodiscard]] std::optional<uint64_t> GetUint64Attribute(uint16_t attr_name) const;

  [[nodiscard]] uint64_t GetUint64AttributeOr(uint16_t attr_name, uint64_t default_value) const {
    return GetUint64Attribute(attr_name).value_or(default_value);
  }

  [[nodiscard]] bool GetBoolAttributeOr(uint16_t attr_name, bool default_value) const;

  void ResolveAttributes(DwarfContext* context);

 private:
  const DwarfCompilationUnitHeader* compilation_unit_header_;
  const DwarfDie* parent_;
  uint64_t offset_;
  uint16_t tag_;
  std::vector<std::unique_ptr<DwarfAttribute>> attributes_;
  std::vector<const DwarfDie*> children_;
};

class DwarfCompilationUnit {
 public:
  DwarfCompilationUnit(uint64_t unit_offset,
                       uint64_t unit_length,
                       uint16_t version,
                       uint64_t abbrev_offset,
                       uint8_t address_size,
                       bool is_dwarf64);

  DwarfCompilationUnit(const DwarfCompilationUnit&) = delete;
  DwarfCompilationUnit& operator=(const DwarfCompilationUnit&) = delete;
  DwarfCompilationUnit(DwarfCompilationUnit&&) = default;
  DwarfCompilationUnit& operator=(DwarfCompilationUnit&&) = default;

  void SetDie(const DwarfDie* die);

  [[nodiscard]] const DwarfDie* GetDie() const { return cu_die_; }

  [[nodiscard]] const DwarfCompilationUnitHeader& header() const { return header_; }

 private:
  DwarfCompilationUnitHeader header_;
  const DwarfDie* cu_die_;
};

class DwarfInfo {
 public:
  DwarfInfo(Buffer<uint8_t> abbrev_buf_,
            Buffer<uint8_t> info_buf_,
            StringTable string_table,
            std::optional<StringOffsetTable> string_offset_table);

  DwarfInfo(const DwarfInfo&) = delete;
  const DwarfInfo& operator=(const DwarfInfo&) = delete;

  bool Parse(std::string* error_msg);

  [[nodiscard]] std::vector<const DwarfDie*> FindDiesByName(const std::string& name) const;
  [[nodiscard]] const DwarfDie* GetDieByOffset(uint64_t offset) const;

 private:
  Buffer<uint8_t> abbrev_buf_;
  Buffer<uint8_t> info_buf_;
  StringTable string_table_;
  std::optional<StringOffsetTable> string_offset_table_;

  std::vector<std::unique_ptr<DwarfCompilationUnit>> compilation_units_;
  std::unordered_map<uint64_t, std::unique_ptr<DwarfDie>> die_offset_map_;
};

}  // namespace nogrod
#endif  // NOGROD_DWARF_INFO_
