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

#ifndef NOGROD_DWARF_ABBREV_
#define NOGROD_DWARF_ABBREV_

#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include <berberis/base/macros.h>

#include "dwarf_context.h"

namespace nogrod {

class DwarfAttribute {
 public:
  explicit DwarfAttribute(uint32_t name);
  virtual ~DwarfAttribute() = default;

  DwarfAttribute(const DwarfAttribute&) = delete;
  const DwarfAttribute& operator=(const DwarfAttribute&) = delete;

  [[nodiscard]] uint32_t name() const { return name_; }
  [[nodiscard]] virtual std::optional<std::string> StringValue() const = 0;
  [[nodiscard]] virtual std::optional<uint64_t> Uint64Value() const = 0;
  [[nodiscard]] virtual std::optional<bool> BoolValue() const = 0;

  // Some forms of attributes need to be resolved after reading entire abbrev
  // The example are string attributes of form DW_FORM_strx? in compilation unit
  // may come before DW_AT_str_offset_base and have undefined base offset at
  // the time of reading. These need to be resolved after whole compilation unit
  // is read.
  virtual void Resolve(DwarfContext* context) = 0;

 private:
  uint32_t name_;
};

template <typename T>
class DwarfAttributeValue : public DwarfAttribute {
 public:
  DwarfAttributeValue(uint32_t name, T value) : DwarfAttribute{name}, value_{std::move(value)} {}

  DwarfAttributeValue(const DwarfAttributeValue&) = delete;
  const DwarfAttributeValue& operator=(const DwarfAttributeValue&) = delete;

  [[nodiscard]] const T& value() const { return value_; }
  [[nodiscard]] std::optional<std::string> StringValue() const override;
  [[nodiscard]] std::optional<uint64_t> Uint64Value() const override;
  [[nodiscard]] std::optional<bool> BoolValue() const override;

  void Resolve(DwarfContext* context) override;

 private:
  T value_;
};

class DwarfStrXAttribute : public DwarfAttribute {
 public:
  DwarfStrXAttribute(uint32_t name, uint64_t index) : DwarfAttribute{name}, index_{index} {}

  [[nodiscard]] std::optional<std::string> StringValue() const override;

  [[nodiscard]] std::optional<uint64_t> Uint64Value() const override { return std::nullopt; }

  [[nodiscard]] std::optional<bool> BoolValue() const override { return std::nullopt; }

  void Resolve(DwarfContext* context) override;

 private:
  std::optional<std::string> string_;
  uint64_t index_;
};

class DwarfCompilationUnitHeader {
 public:
  DwarfCompilationUnitHeader(uint64_t unit_offset,
                             uint64_t unit_length,
                             uint16_t version,
                             uint64_t abbrev_offset,
                             uint8_t address_size,
                             bool is_dwarf64);

  DwarfCompilationUnitHeader(const DwarfCompilationUnitHeader&) = delete;
  const DwarfCompilationUnitHeader& operator=(const DwarfCompilationUnitHeader&) = delete;

  DwarfCompilationUnitHeader(DwarfCompilationUnitHeader&&) = default;
  DwarfCompilationUnitHeader& operator=(DwarfCompilationUnitHeader&&) = default;

  [[nodiscard]] uint64_t unit_offset() const { return unit_offset_; }
  [[nodiscard]] uint64_t unit_length() const { return unit_length_; }
  [[nodiscard]] uint16_t version() const { return version_; }
  [[nodiscard]] uint64_t abbrev_offset() const { return abbrev_offset_; }
  [[nodiscard]] uint8_t address_size() const { return address_size_; }
  [[nodiscard]] bool is_dwarf64() const { return is_dwarf64_; }

 private:
  uint64_t unit_offset_;
  uint64_t unit_length_;
  uint16_t version_;
  uint64_t abbrev_offset_;
  uint8_t address_size_;
  bool is_dwarf64_;
};

class DwarfAbbrevAttribute;

class DwarfClass {
 public:
  static const DwarfClass* kAddress;
  static const DwarfClass* kAddrptr;
  static const DwarfClass* kBlock;
  static const DwarfClass* kConstant;
  static const DwarfClass* kExprloc;
  static const DwarfClass* kFlag;
  static const DwarfClass* kLineptr;
  static const DwarfClass* kLoclist;
  static const DwarfClass* kLoclistsptr;
  static const DwarfClass* kMacptr;
  static const DwarfClass* kRnglist;
  static const DwarfClass* kRnglistsptr;
  static const DwarfClass* kReference;
  static const DwarfClass* kString;
  static const DwarfClass* kStroffsetsptr;

  DwarfClass() = delete;
  DwarfClass(const DwarfClass&) = delete;
  DwarfClass& operator=(const DwarfClass&) = delete;
  DwarfClass(DwarfClass&&) = delete;
  DwarfClass& operator=(DwarfClass&&) = delete;

  virtual ~DwarfClass() = default;
  [[nodiscard]] const char* name() const;
  [[nodiscard]] virtual std::unique_ptr<DwarfAttribute> ReadAttribute(
      const DwarfCompilationUnitHeader* cu,
      const DwarfAbbrevAttribute* abbrev_attr,
      DwarfContext* context,
      std::string* error_msg) const = 0;

 protected:
  explicit DwarfClass(const char* name);

 private:
  const char* name_;
};

class DwarfAbbrevAttribute {
 public:
  static std::unique_ptr<const DwarfAbbrevAttribute> CreateAbbrevAttribute(uint16_t version,
                                                                           uint32_t name,
                                                                           uint32_t form,
                                                                           int64_t value,
                                                                           std::string* error_msg);

  DwarfAbbrevAttribute();
  DwarfAbbrevAttribute(uint32_t name, uint32_t form, int64_t value, const DwarfClass* dwarf_class);

  [[nodiscard]] uint32_t name() const { return name_; }
  [[nodiscard]] uint32_t form() const { return form_; }
  [[nodiscard]] int64_t value() const { return value_; }
  [[nodiscard]] const DwarfClass* dwarf_class() const { return dwarf_class_; }

 private:
  uint32_t name_;
  uint32_t form_;
  int64_t value_;
  const DwarfClass* dwarf_class_;
};

class DwarfAbbrev {
 public:
  DwarfAbbrev();
  DwarfAbbrev(uint64_t code, uint64_t tag, bool has_children);

  DwarfAbbrev(const DwarfAbbrev&) = delete;
  const DwarfAbbrev& operator=(const DwarfAbbrev&) = delete;

  DwarfAbbrev(DwarfAbbrev&&) = default;
  DwarfAbbrev& operator=(DwarfAbbrev&&) = default;

  void AddAttribute(std::unique_ptr<const DwarfAbbrevAttribute>&& abbrev_attribute);

  [[nodiscard]] uint64_t tag() const { return tag_; }
  [[nodiscard]] uint64_t code() const { return code_; }
  [[nodiscard]] bool has_children() const { return has_children_; }
  [[nodiscard]] const std::vector<std::unique_ptr<const DwarfAbbrevAttribute>>& attributes() const {
    return attributes_;
  }

 private:
  uint64_t code_;
  uint64_t tag_;
  bool has_children_;
  std::vector<std::unique_ptr<const DwarfAbbrevAttribute>> attributes_;
};

}  // namespace nogrod
#endif  // NOGROD_DWARF_ABBREV_
