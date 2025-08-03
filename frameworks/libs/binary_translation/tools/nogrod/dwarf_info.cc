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

#include "dwarf_info.h"

#include <cinttypes>
#include <queue>

#include "berberis/base/stringprintf.h"

#include "dwarf_constants.h"

namespace nogrod {

namespace {

using berberis::StringPrintf;

class DwarfParser {
 public:
  DwarfParser(const Buffer<uint8_t>* abbrev_buf,
              const Buffer<uint8_t>* info_buf,
              const StringTable* debug_str_table,
              const std::optional<StringOffsetTable>& string_offset_table)
      : abbrev_buf_{abbrev_buf},
        info_buf_{info_buf},
        debug_str_table_{debug_str_table},
        string_offset_table_{string_offset_table} {}

  [[nodiscard]] bool ReadDwarfInfo(
      std::vector<std::unique_ptr<DwarfCompilationUnit>>* compilation_units,
      std::unordered_map<uint64_t, std::unique_ptr<DwarfDie>>* die_map,
      std::string* error_msg) {
    ByteInputStream bs(info_buf_->data(), info_buf_->size());
    DwarfContext context(&bs, debug_str_table_, string_offset_table_);

    while (bs.available()) {
      std::unique_ptr<DwarfCompilationUnit> cu = ReadCompilationUnit(&context, die_map, error_msg);
      if (!cu) {
        return false;
      }
      compilation_units->push_back(std::move(cu));
    }

    return true;
  }

 private:
  [[nodiscard]] static std::unique_ptr<DwarfAttribute> ReadAttribute(
      const DwarfCompilationUnitHeader* cu,
      const DwarfAbbrevAttribute* abbrev_attr,
      DwarfContext* context,
      std::string* error_msg) {
    const DwarfClass* attribute_class = abbrev_attr->dwarf_class();
    return attribute_class->ReadAttribute(cu, abbrev_attr, context, error_msg);
  }

  [[nodiscard]] static const DwarfDie* ReadOneDie(
      DwarfContext* context,
      const DwarfDie* parent_die,
      const DwarfCompilationUnitHeader* cu,
      const std::unordered_map<uint64_t, DwarfAbbrev>* abbrev_map,
      std::unordered_map<uint64_t, std::unique_ptr<DwarfDie>>* die_map,
      std::string* error_msg) {
    ByteInputStream* bs = context->info_stream();

    uint64_t offset = bs->offset();
    uint64_t abbrev_code = bs->ReadLeb128();

    if (abbrev_code == 0) {
      // null-die
      std::unique_ptr<DwarfDie> null_die(new DwarfDie(cu, parent_die, offset, 0));
      const DwarfDie* result = null_die.get();
      (*die_map)[offset] = std::move(null_die);
      return result;
    }

    auto it = abbrev_map->find(abbrev_code);
    if (it == abbrev_map->end()) {
      *error_msg = StringPrintf("<%" PRIx64 "> Abbrev code %" PRId64
                                " was not found in .debug_abbrev "
                                "with offset %" PRIx64,
                                bs->offset(),
                                abbrev_code,
                                cu->abbrev_offset());
      return nullptr;
    }

    auto& abbrev = it->second;

    std::unique_ptr<DwarfDie> die(new DwarfDie(cu, parent_die, offset, abbrev.tag()));

    for (auto& abbrev_attr : abbrev.attributes()) {
      std::unique_ptr<DwarfAttribute> attribute =
          ReadAttribute(cu, abbrev_attr.get(), context, error_msg);
      if (!attribute) {
        return nullptr;
      }

      if (attribute->name() == DW_AT_str_offsets_base) {
        if (abbrev.tag() != DW_TAG_compile_unit) {
          *error_msg = StringPrintf(
              "<%" PRIx64
              "> DW_AT_str_offsets_base is only supported for DW_TAG_compile_unit abbrev.",
              bs->offset());
          return nullptr;
        }

        context->SetStrOffsetsBase(attribute->Uint64Value().value());
      }

      die->AddAttribute(attribute.release());
    }

    die->ResolveAttributes(context);

    if (abbrev.has_children()) {
      while (true) {
        const DwarfDie* child_die =
            ReadOneDie(context, die.get(), cu, abbrev_map, die_map, error_msg);
        if (!child_die) {
          return nullptr;
        }

        if (child_die->tag() == 0) {
          break;
        }

        die->AddChild(child_die);
      }
    }

    const DwarfDie* result = die.get();

    (*die_map)[offset] = std::move(die);

    return result;
  }

  [[nodiscard]] std::unique_ptr<DwarfCompilationUnit> ReadCompilationUnit(
      DwarfContext* context,
      std::unordered_map<uint64_t, std::unique_ptr<DwarfDie>>* die_map,
      std::string* error_msg) {
    ByteInputStream* bs = context->info_stream();

    uint64_t offset = bs->offset();

    uint64_t unit_length = bs->ReadUint32();
    bool is_dwarf64 = false;
    if (unit_length == 0xFFFFFFFF) {
      unit_length = bs->ReadUint64();
      is_dwarf64 = true;
    }

    uint16_t version = bs->ReadUint16();
    uint64_t abbrev_offset;
    uint8_t address_size;

    if (version >= 2 && version <= 4) {
      abbrev_offset = is_dwarf64 ? bs->ReadUint64() : bs->ReadUint32();
      address_size = bs->ReadUint8();
    } else if (version == 5) {
      uint8_t unit_type = bs->ReadUint8();
      // TODO(dimitry): can a .so file have DW_UT_partial CUs?
      if (unit_type != DW_UT_compile) {
        *error_msg =
            StringPrintf("Unsupported DWARF5 compilation unit type encoding: %x", unit_type);
        return nullptr;
      }

      address_size = bs->ReadUint8();
      abbrev_offset = is_dwarf64 ? bs->ReadUint64() : bs->ReadUint32();
    } else {
      *error_msg =
          StringPrintf("Unsupported dwarf version: %d, CU offset: 0x%" PRIx64, version, offset);
      return nullptr;
    }

    std::unique_ptr<DwarfCompilationUnit> cu(new DwarfCompilationUnit(
        offset, unit_length, version, abbrev_offset, address_size, is_dwarf64));

    // Even though in .so files abbrev codes is a sequence [1..n]
    // the spec does not specify this as a requirement. Therefore
    // it is safer to use unordered_map.
    std::unordered_map<uint64_t, DwarfAbbrev>* abbrev_map =
        ReadAbbrev(version, abbrev_offset, error_msg);

    if (abbrev_map == nullptr) {
      *error_msg =
          StringPrintf("error reading abbrev for compilation unit at offset 0x%" PRIx64 ": %s",
                       offset,
                       error_msg->c_str());
      return nullptr;
    }

    // We expect this attribute to be set if needed in the DW_TAG_compile_unit die.
    context->ResetStrOffsetsBase();

    // CU consists of one DIE (DW_TAG_compile_unit) - read it
    const DwarfDie* cu_die =
        ReadOneDie(context, nullptr, &cu->header(), abbrev_map, die_map, error_msg);

    if (!cu_die) {
      return nullptr;
    }

    if (cu_die->tag() != DW_TAG_compile_unit) {
      *error_msg = StringPrintf(
          "Unexpected DIE tag for Compilation Unit: %d, expected DW_TAG_compile_unit(%d)",
          cu_die->tag(),
          DW_TAG_compile_unit);
      return nullptr;
    }

    cu->SetDie(cu_die);

    return cu;
  }

  std::unordered_map<uint64_t, DwarfAbbrev>* ReadAbbrev(uint16_t version,
                                                        uint64_t offset,
                                                        std::string* error_msg) {
    auto it = abbrevs_.find(offset);
    if (it != abbrevs_.end()) {
      return &it->second;
    }

    if (offset >= abbrev_buf_->size()) {
      *error_msg = StringPrintf(
          "abbrev offset (%" PRId64 ") is out of bounds: %" PRId64, offset, abbrev_buf_->size());
      return nullptr;
    }

    std::unordered_map<uint64_t, DwarfAbbrev> abbrev_map;
    ByteInputStream bs(abbrev_buf_->data() + offset, abbrev_buf_->size() - offset);
    while (true) {
      uint64_t code = bs.ReadLeb128();

      // The abbreviations for a given compilation unit end with an entry consisting of a 0 byte
      // for the abbreviation code.
      if (code == 0) {
        break;
      }

      uint64_t entry_tag = bs.ReadLeb128();
      uint8_t has_children = bs.ReadUint8();

      DwarfAbbrev abbrev(code, entry_tag, has_children == DW_CHILDREN_yes);

      while (true) {
        uint64_t attr_offset = offset + bs.offset();
        uint64_t attr_name = bs.ReadLeb128();
        uint64_t attr_form = bs.ReadLeb128();
        int64_t value = 0;
        // The series of attribute specifications ends with an entry containing 0 for the name
        // and 0 for the form.
        if (attr_name == 0 && attr_form == 0) {
          break;
        }

        // "The attribute form DW_FORM_implicit_const is another special case. For
        // attributes with this form, the attribute specification contains a third part, which is
        // a signed LEB128 number."

        if (attr_form == DW_FORM_implicit_const) {
          value = bs.ReadSleb128();
        }

        std::unique_ptr<const DwarfAbbrevAttribute> abbrev_attribute =
            DwarfAbbrevAttribute::CreateAbbrevAttribute(
                version, attr_name, attr_form, value, error_msg);

        if (!abbrev_attribute) {
          *error_msg =
              StringPrintf("error getting attribute at debug_abbrev offset 0x%" PRIx64 ": %s",
                           attr_offset,
                           error_msg->c_str());
          return nullptr;
        }
        abbrev.AddAttribute(std::move(abbrev_attribute));
      }

      abbrev_map[code] = std::move(abbrev);
    }

    abbrevs_[offset] = std::move(abbrev_map);
    return &abbrevs_[offset];
  }

 private:
  const Buffer<uint8_t>* abbrev_buf_;
  const Buffer<uint8_t>* info_buf_;
  const StringTable* debug_str_table_;
  const std::optional<StringOffsetTable>& string_offset_table_;

  std::unordered_map<uint64_t, std::unordered_map<uint64_t, DwarfAbbrev>> abbrevs_;
};

}  // namespace

DwarfCompilationUnit::DwarfCompilationUnit(uint64_t unit_offset,
                                           uint64_t unit_length,
                                           uint16_t version,
                                           uint64_t abbrev_offset,
                                           uint8_t address_size,
                                           bool is_dwarf64)
    : header_(unit_offset, unit_length, version, abbrev_offset, address_size, is_dwarf64),
      cu_die_(nullptr) {}

void DwarfCompilationUnit::SetDie(const DwarfDie* die) {
  cu_die_ = die;
}

DwarfInfo::DwarfInfo(Buffer<uint8_t> abbrev_buf,
                     Buffer<uint8_t> info_buf,
                     StringTable string_table,
                     std::optional<StringOffsetTable> string_offset_table)
    : abbrev_buf_{std::move(abbrev_buf)},
      info_buf_{std::move(info_buf)},
      string_table_{std::move(string_table)},
      string_offset_table_{std::move(string_offset_table)} {}

bool DwarfInfo::Parse(std::string* error_msg) {
  DwarfParser parser(&abbrev_buf_, &info_buf_, &string_table_, string_offset_table_);
  if (!parser.ReadDwarfInfo(&compilation_units_, &die_offset_map_, error_msg)) {
    return false;
  }

  return true;
}

std::vector<const DwarfDie*> DwarfInfo::FindDiesByName(const std::string& name) const {
  std::vector<const DwarfDie*> result;

  for (auto& cu : compilation_units_) {
    const DwarfDie* cu_die = cu->GetDie();

    // DIE and name prefix
    std::queue<std::pair<const DwarfDie*, std::string>> visit_queue;
    visit_queue.push(make_pair(cu_die, std::string("")));
    while (!visit_queue.empty()) {
      auto current = visit_queue.front();
      visit_queue.pop();  // why doesn't pop() return the value on the front again?
      auto current_die = current.first;
      auto current_prefix = current.second;

      for (const DwarfDie* child : current_die->children()) {
        // TODO(random-googler): Can we rely on DW_AT_linkage_name being present for all members?
        // It looks like if member is not a function (DW_TAG_member) it lacks
        // DW_AT_linkage_name. There is non-zero chance that this is going to
        // need a C++ mangler in order to resolve all the names.
        if (child->tag() == DW_TAG_class_type || child->tag() == DW_TAG_structure_type ||
            child->tag() == DW_TAG_namespace) {
          auto die_name = child->GetStringAttribute(DW_AT_name);
          if (!die_name) {
            // do not search anonymous dies
            continue;
          }
          visit_queue.push(make_pair(child, current_prefix + die_name.value() + "::"));
        }

        auto die_name = child->GetStringAttribute(DW_AT_linkage_name);

        if (!die_name) {
          die_name = child->GetStringAttribute(DW_AT_name);
          if (die_name) {
            die_name = make_optional(current_prefix + die_name.value());
          }
        }

        if (die_name && die_name.value() == name) {
          result.push_back(child);
        }
      }
    }
  }

  return result;
}

const DwarfDie* DwarfInfo::GetDieByOffset(uint64_t offset) const {
  auto it = die_offset_map_.find(offset);
  if (it == die_offset_map_.end()) {
    return nullptr;
  }

  return it->second.get();
}

DwarfDie::DwarfDie(const DwarfCompilationUnitHeader* cu,
                   const DwarfDie* parent,
                   uint64_t offset,
                   uint16_t tag)
    : compilation_unit_header_(cu), parent_(parent), offset_(offset), tag_(tag) {}

void DwarfDie::AddAttribute(DwarfAttribute* attr) {
  attributes_.push_back(std::unique_ptr<DwarfAttribute>(attr));
}

void DwarfDie::AddChild(const DwarfDie* child) {
  children_.push_back(child);
}

std::optional<std::string> DwarfDie::GetStringAttribute(uint16_t attr_name) const {
  for (auto& attr : attributes_) {
    if (attr->name() == attr_name) {
      std::optional<std::string> result = attr->StringValue();
      CHECK(result.has_value());
      return result;
    }
  }
  return {};
}

std::optional<uint64_t> DwarfDie::GetUint64Attribute(uint16_t attr_name) const {
  for (auto& attr : attributes_) {
    if (attr->name() == attr_name) {
      std::optional<uint64_t> result = attr->Uint64Value();
      CHECK(result.has_value());
      return result;
    }
  }
  return {};
}

bool DwarfDie::GetBoolAttributeOr(uint16_t attr_name, bool default_value) const {
  for (auto& attr : attributes_) {
    if (attr->name() == attr_name) {
      std::optional<bool> result = attr->BoolValue();
      CHECK(result.has_value());
      return result.value();
    }
  }

  return default_value;
}

void DwarfDie::ResolveAttributes(DwarfContext* context) {
  for (auto& attr : attributes_) {
    attr->Resolve(context);
  }
}

}  // namespace nogrod
