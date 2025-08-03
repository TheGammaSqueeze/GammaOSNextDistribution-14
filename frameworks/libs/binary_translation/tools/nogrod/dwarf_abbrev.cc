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

#include "dwarf_abbrev.h"

#include <map>
#include <memory>
#include <vector>

#include "berberis/base/stringprintf.h"

#include "dwarf_constants.h"

namespace nogrod {

namespace {

using berberis::StringPrintf;

class DwarfClasses {
 public:
  DwarfClasses() { classes_[0] = {}; }

  DwarfClasses(std::initializer_list<const DwarfClass*> classes) {
    classes_[0] = std::vector(classes);
  }

  DwarfClasses(
      std::initializer_list<std::map<uint16_t, std::vector<const DwarfClass*>>::value_type> classes)
      : classes_(classes) {}

  [[nodiscard]] const std::vector<const DwarfClass*>* get(uint16_t version) const {
    auto candidate = classes_.find(version);
    if (candidate != classes_.end()) {
      return &candidate->second;
    }

    for (auto it = classes_.begin(), end = classes_.end(); it != end; ++it) {
      if (it->first <= version) {
        candidate = it;
      } else {
        break;
      }
    }

    return candidate != classes_.end() ? &candidate->second : nullptr;
  }

 private:
  // classes for every version
  std::map<uint16_t, std::vector<const DwarfClass*>> classes_;
};

struct AbbrevDescriptor {
  uint32_t code;
  DwarfClasses classes;
  const char* name;
};

// clang-format makes this part unreadable so we disable it.
// clang-format off
const AbbrevDescriptor kFormDescriptors[] = {
  { 0x00, { }, "null"},
  { 0x01, { DwarfClass::kAddress }, "DW_FORM_addr" },
  { 0x02, { }, "Reserved 0x02" },
  { 0x03, { DwarfClass::kBlock }, "DW_FORM_block2" },
  { 0x04, { DwarfClass::kBlock }, "DW_FORM_block4" },
  { 0x05, { DwarfClass::kConstant }, "DW_FORM_data2" },
  { 0x06, { DwarfClass::kConstant }, "DW_FORM_data4" },
  { 0x07, { DwarfClass::kConstant }, "DW_FORM_data8" },
  { 0x08, { DwarfClass::kString }, "DW_FORM_string" },
  { 0x09, { DwarfClass::kBlock }, "DW_FORM_block" },
  { 0x0a, { DwarfClass::kBlock }, "DW_FORM_block4" },
  { 0x0b, { DwarfClass::kConstant }, "DW_FORM_data1" },
  { 0x0c, { DwarfClass::kFlag }, "DW_FORM_flag" },
  { 0x0d, { DwarfClass::kConstant }, "DW_FORM_sdata" },
  { 0x0e, { DwarfClass::kString }, "DW_FORM_strp" },
  { 0x0f, { DwarfClass::kConstant }, "DW_FORM_udata" },
  { 0x10, { DwarfClass::kReference }, "DW_FORM_ref_addr" },
  { 0x11, { DwarfClass::kReference }, "DW_FORM_ref1" },
  { 0x12, { DwarfClass::kReference }, "DW_FORM_ref2" },
  { 0x13, { DwarfClass::kReference }, "DW_FORM_ref4" },
  { 0x14, { DwarfClass::kReference }, "DW_FORM_ref8" },
  { 0x15, { DwarfClass::kReference }, "DW_FORM_ref_udata" },
  { 0x16, {}, "DW_FORM_indirect" }, // TODO(dimitry): DwarfClass::kIndirect?
  { 0x17, { DwarfClass::kAddrptr,
            DwarfClass::kLineptr,
            //DwarfClass::kLoclist,
            DwarfClass::kLoclistsptr,
            DwarfClass::kMacptr,
            //DwarfClass::kRnglist,
            DwarfClass::kRnglistsptr,
            DwarfClass::kStroffsetsptr,
          }, "DW_FORM_sec_offset"},
  { 0x18, { DwarfClass::kExprloc }, "DW_FORM_exprloc" },
  { 0x19, { DwarfClass::kFlag }, "DW_FORM_flag_present" },
  { 0x1a, { DwarfClass::kString }, "DW_FORM_strx" },
  { 0x1b, { DwarfClass::kAddress }, "DW_FORM_addrx" },
  { 0x1c, { DwarfClass::kReference }, "DW_FORM_ref_sup4" },
  { 0x1d, { DwarfClass::kString }, "DW_FORM_strp_sup" },
  { 0x1e, { DwarfClass::kConstant }, "DW_FORM_data16" },
  { 0x1f, { DwarfClass::kString }, "DW_FORM_line_strp" },
  { 0x20, { DwarfClass::kReference }, "DW_FORM_ref_sig8" },
  { 0x21, { DwarfClass::kConstant }, "DW_FORM_implicit_const" },
  { 0x22, { DwarfClass::kLoclist }, "DW_FORM_loclistx" },
  { 0x23, { DwarfClass::kRnglist }, "DW_FORM_rnglistx" },
  { 0x24, { DwarfClass::kReference }, "DW_FORM_ref_sup8" },
  { 0x25, { DwarfClass::kString }, "DW_FORM_strx1" },
  { 0x26, { DwarfClass::kString }, "DW_FORM_strx2" },
  { 0x27, { DwarfClass::kString }, "DW_FORM_strx3" },
  { 0x28, { DwarfClass::kString }, "DW_FORM_strx4" },
  { 0x29, { DwarfClass::kAddress }, "DW_FORM_addrx1" },
  { 0x2a, { DwarfClass::kAddress }, "DW_FORM_addrx2" },
  { 0x2b, { DwarfClass::kAddress }, "DW_FORM_addrx3" },
  { 0x2c, { DwarfClass::kAddress }, "DW_FORM_addrx4" },
};

const AbbrevDescriptor kNameDescriptors[] = {
  { 0x00, { }, "null" },
  { 0x01, { { 2, { DwarfClass::kReference } } }, "DW_AT_sibling" },
  { 0x02, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, DwarfClass::kLoclist } },
          }, "DW_AT_location" },
  { 0x03, { { 2, { DwarfClass::kString } } }, "DW_AT_name" },
  { 0x04, { }, "Reserved 0x04" },
  { 0x05, { }, "Reserved 0x05" },
  { 0x06, { }, "Reserved 0x06" },
  { 0x07, { }, "Reserved 0x07" },
  { 0x08, { }, "Reserved 0x08" },
  { 0x09, { { 2, { DwarfClass::kConstant } } }, "DW_AT_ordering" },
  { 0x0a, { }, "Reserved 0x0a" },
  { 0x0b, {
            { 2, { DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock,
                   DwarfClass::kConstant,
                   DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant,
                   DwarfClass::kExprloc,
                   DwarfClass::kReference } },
          }, "DW_AT_byte_size" },
  { 0x0c, {
            { 2, { DwarfClass::kConstant } },
            { 3, { DwarfClass::kConstant,
                   DwarfClass::kBlock,
                   DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant,
                   DwarfClass::kExprloc,
                   DwarfClass::kReference } },
          }, "DW_AT_bit_offset" }, // Removed in dwarf5??
  { 0x0d, {
            { 2, { DwarfClass::kConstant } },
            { 3, { DwarfClass::kConstant,
                   DwarfClass::kBlock,
                   DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant,
                   DwarfClass::kExprloc,
                   DwarfClass::kReference } },
          }, "DW_AT_bit_size" },
  { 0x0e, { }, "Reserved 0x0e" },
  { 0x0f, { }, "Reserved 0x0f" },
  { 0x10, {
            { 2, { DwarfClass::kConstant } },
            { 3, { DwarfClass::kLineptr } },
          }, "DW_AT_stmt_list" },
  { 0x11, { { 2, { DwarfClass::kAddress } } }, "DW_AT_low_pc" },
  { 0x12, {
            { 2, { DwarfClass::kAddress } },
            { 4, { DwarfClass::kAddress, DwarfClass::kConstant } },
          }, "DW_AT_high_pc" },
  { 0x13, { { 2, { DwarfClass::kConstant } } }, "DW_AT_language" },
  { 0x14, { }, "Reserved 0x14" },
  { 0x15, { { 2, { DwarfClass::kReference } } }, "DW_AT_discr" },
  { 0x16, { { 2, { DwarfClass::kConstant } } }, "DW_AT_discr_value" },
  { 0x17, { { 2, { DwarfClass::kConstant } } }, "DW_AT_visibility" },
  { 0x18, { { 2, { DwarfClass::kReference } } }, "DW_AT_import" },
  { 0x19, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc/*, DwarfClass::kLoclist */, DwarfClass::kReference } },
          }, "DW_AT_string_length" },
  { 0x1a, { { 2, { DwarfClass::kReference } } }, "DW_AT_common_reference" },
  { 0x1b, { { 2, { DwarfClass::kString } } }, "DW_AT_comp_dir" },
  { 0x1c, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kString } }
          }, "DW_AT_const_value" },
  { 0x1d, { { 2, { DwarfClass::kReference } } }, "DW_AT_containing_type" },
  { 0x1e, {
            { 2, { DwarfClass::kReference } },
            { 5, { DwarfClass::kConstant,
                   DwarfClass::kReference,
                   DwarfClass::kFlag } }
          }, "DW_AT_default_value" },
  { 0x1f, { }, "Reserved 0x1f" },
  { 0x20, { { 2, { DwarfClass::kConstant } } }, "DW_AT_inline" },
  { 0x21, { { 2, { DwarfClass::kFlag } } }, "DW_AT_is_optional" },
  { 0x22, {
            { 2, { DwarfClass::kConstant, DwarfClass::kReference } },
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_lower_bound" },
  { 0x23, { }, "Reserved 0x23" },
  { 0x24, { }, "Reserved 0x24" },
  { 0x25, { { 2, { DwarfClass::kString } } }, "DW_AT_producer" },
  { 0x26, { }, "Reserved 0x26" },
  { 0x27, { { 2, { DwarfClass::kFlag } } }, "DW_AT_prototyped" },
  { 0x28, { }, "Reserved 0x28" },
  { 0x29, { }, "Reserved 0x29" },
  { 0x2a, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } }
          }, "DW_AT_return_addr" },
  { 0x2b, { }, "Reserved 0x2b" },
  { 0x2c, {
            { 2, { DwarfClass::kConstant } },
            { 4, { DwarfClass::kConstant, DwarfClass::kRnglistsptr } },
            { 5, { DwarfClass::kConstant, /* DwarfClass::kRnglist */ } }
          }, "DW_AT_start_scope" },
  { 0x2d, { }, "Reserved 0x2d" },
  { 0x2e, {
            { 2, { DwarfClass::kConstant } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_bit_stride" },  // called "DW_AT_stride_size" in dwarf2
  { 0x2f, {
            { 2, { DwarfClass::kConstant, DwarfClass::kReference } },
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_upper_bound" },
  { 0x30, { }, "Reserved 0x30" },
  { 0x31, { { 2, { DwarfClass::kReference } } }, "DW_AT_abstract_origin" },
  { 0x32, { { 2, { DwarfClass::kConstant } } }, "DW_AT_accessibility" },
  { 0x33, { { 2, { DwarfClass::kConstant } } }, "DW_AT_address_class" },
  { 0x34, { { 2, { DwarfClass::kFlag } } }, "DW_AT_artificial" },
  { 0x35, { { 2, { DwarfClass::kReference } } }, "DW_AT_base_types" },
  { 0x36, { { 2, { DwarfClass::kConstant } } }, "DW_AT_calling_convention" },
  { 0x37, {
            { 2, { DwarfClass::kConstant, DwarfClass::kReference } },
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_count" },
  { 0x38, {
            { 2, { DwarfClass::kBlock, DwarfClass::kReference } },
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kConstant, DwarfClass::kExprloc /*, DwarfClass::kLoclist */ } },
          }, "DW_AT_data_member_location" },
  { 0x39, { { 2, { DwarfClass::kConstant } } }, "DW_AT_decl_column" },
  { 0x3a, { { 2, { DwarfClass::kConstant } } }, "DW_AT_decl_file" },
  { 0x3b, { { 2, { DwarfClass::kConstant } } }, "DW_AT_decl_line" },
  { 0x3c, { { 2, { DwarfClass::kFlag } } }, "DW_AT_declaration" },
  { 0x3d, { { 2, { DwarfClass::kBlock } } }, "DW_AT_discr_list" },
  { 0x3e, { { 2, { DwarfClass::kConstant } } }, "DW_AT_encoding" },
  { 0x3f, { { 2, { DwarfClass::kFlag } } }, "DW_AT_external" },
  { 0x40, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } },
          }, "DW_AT_frame_base" },
  { 0x41, { { 2, { DwarfClass::kReference } } }, "DW_AT_friend" },
  { 0x42, { { 2, { DwarfClass::kConstant } } }, "DW_AT_identifier_case" },
  { 0x43, {
            { 2, { DwarfClass::kConstant } },
            { 3, { DwarfClass::kMacptr } },
          }, "DW_AT_macro_info" }, // Removed in dwarf5??
  { 0x44, {
            { 2, { DwarfClass::kBlock } },
            { 4, { DwarfClass::kReference } },
          }, "DW_AT_namelist_item" },
  { 0x45, { { 2, { DwarfClass::kReference } } }, "DW_AT_priority" },
  { 0x46, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } },
          }, "DW_AT_segment" },
  { 0x47, { { 2, { DwarfClass::kReference } } }, "DW_AT_specification" },
  { 0x48, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } },
          }, "DW_AT_static_link" },
  { 0x49, { { 2, { DwarfClass::kReference } } }, "DW_AT_type" },
  { 0x4a, {
            { 2, { DwarfClass::kBlock, DwarfClass::kConstant } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } },
          }, "DW_AT_use_location" },
  { 0x4b, { { 2, { DwarfClass::kFlag } } }, "DW_AT_variable_parameter" },
  { 0x4c, { { 2, { DwarfClass::kConstant } } }, "DW_AT_virtuality" },
  { 0x4d, {
            { 2, { DwarfClass::kBlock, DwarfClass::kReference } },
            { 3, { DwarfClass::kBlock, DwarfClass::kLoclistsptr } },
            { 4, { DwarfClass::kExprloc, DwarfClass::kLoclistsptr } },
            { 5, { DwarfClass::kExprloc, /* DwarfClass::kLoclist */ } },
          }, "DW_AT_vtable_elem_location" },
  // Dwarf 3
  { 0x4e, {
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_allocated" },
  { 0x4f, {
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_associated" },
  { 0x50, {
            { 3, { DwarfClass::kBlock } },
            { 4, { DwarfClass::kExprloc } },
          }, "DW_AT_data_location" },
  { 0x51, {
            { 3, { DwarfClass::kBlock, DwarfClass::kConstant, DwarfClass::kReference } },
            { 4, { DwarfClass::kConstant, DwarfClass::kExprloc, DwarfClass::kReference } },
          }, "DW_AT_byte_stride" },
  { 0x52, {
            { 3, { DwarfClass::kAddress } },
            { 5, { DwarfClass::kAddress, DwarfClass::kConstant } },
          }, "DW_AT_entry_pc" },
  { 0x53, { { 3, { DwarfClass::kFlag } } }, "DW_AT_use_UTF8" },
  { 0x54, { { 3, { DwarfClass::kReference } } }, "DW_AT_extension" },
  { 0x55, {
            { 2, { DwarfClass::kConstant } },  // not in spec, but clang uses this in dwarf2??
            { 3, { DwarfClass::kRnglistsptr } },
            { 5, { DwarfClass::kRnglist } },
          }, "DW_AT_ranges" },
  { 0x56, {
            { 3, { DwarfClass::kAddress,
                   DwarfClass::kFlag,
                   DwarfClass::kReference,
                   DwarfClass::kString } },
          }, "DW_AT_trampoline" },
  { 0x57, { { 3, { DwarfClass::kConstant } } }, "DW_AT_call_column" },
  { 0x58, { { 3, { DwarfClass::kConstant } } }, "DW_AT_call_file" },
  { 0x59, { { 3, { DwarfClass::kConstant } } }, "DW_AT_call_line" },
  { 0x5a, { { 3, { DwarfClass::kString } } }, "DW_AT_description" },
  { 0x5b, { { 3, { DwarfClass::kConstant } } }, "DW_AT_binary_scale" },
  { 0x5c, { { 3, { DwarfClass::kConstant } } }, "DW_AT_decimal_scale" },
  { 0x5d, { { 3, { DwarfClass::kReference } } }, "DW_AT_small" },
  { 0x5e, { { 3, { DwarfClass::kConstant } } }, "DW_AT_decimal_sign" },
  { 0x5f, { { 3, { DwarfClass::kConstant } } }, "DW_AT_digit_count" },
  { 0x60, { { 3, { DwarfClass::kString } } }, "DW_AT_picture_string" },
  { 0x61, { { 3, { DwarfClass::kFlag } } }, "DW_AT_mutable" },
  { 0x62, { { 3, { DwarfClass::kFlag } } }, "DW_AT_thread_scaled" },
  { 0x63, { { 3, { DwarfClass::kFlag } } }, "DW_AT_explicit" },
  { 0x64, { { 3, { DwarfClass::kReference } } }, "DW_AT_object_pointer" },
  { 0x65, { { 3, { DwarfClass::kConstant } } }, "DW_AT_endianity" },
  { 0x66, { { 3, { DwarfClass::kFlag } } }, "DW_AT_elemental" },
  { 0x67, { { 3, { DwarfClass::kFlag } } }, "DW_AT_pure" },
  { 0x68, { { 3, { DwarfClass::kFlag } } }, "DW_AT_recursive" },
  // Dwarf 4
  { 0x69, { { 4, { DwarfClass::kReference } } }, "DW_AT_signature" },
  { 0x6a, { { 4, { DwarfClass::kFlag } } }, "DW_AT_main_subprogram" },
  { 0x6b, { { 4, { DwarfClass::kConstant } } }, "DW_AT_data_bit_offset" },
  { 0x6c, { { 4, { DwarfClass::kFlag } } }, "DW_AT_const_expr" },
  { 0x6d, { { 4, { DwarfClass::kFlag } } }, "DW_AT_enum_class" },
  { 0x6e, { { 4, { DwarfClass::kString } } }, "DW_AT_linkage_name" },
  // Dwarf 5
  { 0x6f, { { 5, { DwarfClass::kConstant } } }, "DW_AT_string_length_bit_size" },
  { 0x70, { { 5, { DwarfClass::kConstant } } }, "DW_AT_string_length_byte_size" },
  { 0x71, { { 5, { DwarfClass::kConstant, DwarfClass::kExprloc } } }, "DW_AT_rank" },
  { 0x72, { { 5, { DwarfClass::kStroffsetsptr } } }, "DW_AT_str_offset_base" },
  { 0x73, { { 5, { DwarfClass::kAddrptr } } }, "DW_AT_addr_base" },
  { 0x74, { { 5, { DwarfClass::kRnglistsptr } } }, "DW_AT_rnglists_base" },
  { 0x75, { }, "Unused 0x75" },
  { 0x76, { { 5, { DwarfClass::kString } } }, "DW_AT_dwo_name" },
  // The following are dwarf 5 by spec but clang still injects it to dwarf4
  { 0x77, { { 4, { DwarfClass::kFlag } } }, "DW_AT_reference" },
  { 0x78, { { 4, { DwarfClass::kFlag } } }, "DW_AT_rvalue_reference" },
  { 0x79, { { 5, { DwarfClass::kMacptr } } }, "DW_AT_macros" },
  { 0x7a, { { 5, { DwarfClass::kFlag } } }, "DW_AT_call_all_calls" },
  { 0x7b, { { 5, { DwarfClass::kFlag } } }, "DW_AT_call_all_source_calls" },
  { 0x7c, { { 5, { DwarfClass::kFlag } } }, "DW_AT_call_all_tail_calls" },
  { 0x7d, { { 5, { DwarfClass::kAddress } } }, "DW_AT_call_return_pc" },
  { 0x7e, { { 5, { DwarfClass::kExprloc } } }, "DW_AT_call_value" },
  // kReference is not allowed for DW_AT_call_origin by DWARF5 standard, but it is used by clang
  { 0x7f, { { 5, { DwarfClass::kExprloc, DwarfClass::kReference } } }, "DW_AT_call_origin" },
  { 0x80, { { 5, { DwarfClass::kReference } } }, "DW_AT_call_parameter" },
  { 0x81, { { 5, { DwarfClass::kAddress } } }, "DW_AT_call_pc" },
  { 0x82, { { 5, { DwarfClass::kFlag } } }, "DW_AT_call_tail_call" },
  { 0x83, { { 5, { DwarfClass::kExprloc } } }, "DW_AT_call_target" },
  { 0x84, { { 5, { DwarfClass::kExprloc } } }, "DW_AT_call_target_clobbered" },
  { 0x85, { { 5, { DwarfClass::kExprloc } } }, "DW_AT_call_data_location" },
  { 0x86, { { 5, { DwarfClass::kExprloc } } }, "DW_AT_call_data_value" },
  // Apparently clang uses these in dwarf4 CUs
  { 0x87, { { 4, { DwarfClass::kFlag } } }, "DW_AT_noreturn" },
  { 0x88, { { 4, { DwarfClass::kConstant } } }, "DW_AT_alignment" },
  { 0x89, { { 4, { DwarfClass::kFlag } } }, "DW_AT_export_symbols" },
  { 0x8a, { { 5, { DwarfClass::kFlag } } }, "DW_AT_deleted" },
  { 0x8b, { { 5, { DwarfClass::kConstant } } }, "DW_AT_defaulted" },
  { 0x8c, { { 5, { DwarfClass::kLoclistsptr } } }, "DW_AT_loclists_base" },
};
// clang-format on

static_assert(sizeof(kFormDescriptors) / sizeof(AbbrevDescriptor) == (DW_FORM_MAX_VALUE + 1), "");
static_assert(sizeof(kNameDescriptors) / sizeof(AbbrevDescriptor) == (DW_AT_MAX_VALUE + 1), "");

const AbbrevDescriptor kAtGnuVector = {0x2107, {DwarfClass::kFlag}, "DW_AT_GNU_vector"};

const AbbrevDescriptor kAtGnuTemplateName = {0x2110,
                                             {DwarfClass::kString},
                                             "DW_AT_GNU_template_name"};

const AbbrevDescriptor kAtGnuCallSiteValue = {0x2111,
                                              {DwarfClass::kExprloc},
                                              "DW_AT_GNU_call_site_value"};

const AbbrevDescriptor kAtGnuCallSiteTarget = {0x2113,
                                               {DwarfClass::kExprloc},
                                               "DW_AT_GNU_call_site_target"};

const AbbrevDescriptor kAtGnuTailCall = {0x2115, {DwarfClass::kFlag}, "DW_AT_GNU_tail_call"};

const AbbrevDescriptor kAtGnuAllTailCallSites = {0x2116,
                                                 {DwarfClass::kFlag},
                                                 "DW_AT_GNU_all_tail_call_sites"};

const AbbrevDescriptor kAtGnuAllCallSites = {0x2117,
                                             {DwarfClass::kFlag},
                                             "DW_AT_GNU_all_call_sites"};

const AbbrevDescriptor kAtGnuPubnamesDescriptor = {0x2134,
                                                   {DwarfClass::kFlag},
                                                   "DW_AT_GNU_pubnames"};

const AbbrevDescriptor kAtGnuDiscriminator = {0x2136,
                                              {DwarfClass::kConstant},
                                              "DW_AT_GNU_discriminator"};

const AbbrevDescriptor kAtGnuLocviews = {0x2137, {DwarfClass::kLoclistsptr}, "DW_AT_GNU_locviews"};

const AbbrevDescriptor kAtGnuEntryView = {0x2138, {DwarfClass::kConstant}, "DW_AT_GNU_entry_view"};

const AbbrevDescriptor* GetNameDescriptor(uint32_t name) {
  switch (name) {
    case DW_AT_GNU_vector:
      return &kAtGnuVector;
    case DW_AT_GNU_template_name:
      return &kAtGnuTemplateName;
    case DW_AT_GNU_call_site_value:
      return &kAtGnuCallSiteValue;
    case DW_AT_GNU_call_site_target:
      return &kAtGnuCallSiteTarget;
    case DW_AT_GNU_tail_call:
      return &kAtGnuTailCall;
    case DW_AT_GNU_all_tail_call_sites:
      return &kAtGnuAllTailCallSites;
    case DW_AT_GNU_all_call_sites:
      return &kAtGnuAllCallSites;
    case DW_AT_GNU_pubnames:
      return &kAtGnuPubnamesDescriptor;
    case DW_AT_GNU_discriminator:
      return &kAtGnuDiscriminator;
    case DW_AT_GNU_locviews:
      return &kAtGnuLocviews;
    case DW_AT_GNU_entry_view:
      return &kAtGnuEntryView;
  }

  if (name > DW_AT_MAX_VALUE) {
    return nullptr;
  }

  return kNameDescriptors + name;
}

std::string NameToString(uint32_t name) {
  auto descriptor = GetNameDescriptor(name);
  if (descriptor == nullptr) {
    return StringPrintf("unknown-0x%x", name);
  }

  return descriptor->name;
}

std::string FormToString(uint32_t form) {
  if (form >= DW_FORM_MAX_VALUE) {
    return StringPrintf("unknown-0x%x", form);
  }

  return kFormDescriptors[form].name;
}

class DwarfClassAddress : public DwarfClass {
 public:
  DwarfClassAddress() : DwarfClass("address") {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader* cu,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint64_t address;
    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();
    if (form == DW_FORM_addr) {
      uint8_t address_size = cu->address_size();
      if (address_size != 4 && address_size != 8) {
        *error_msg = StringPrintf("Invalid address size %d (expected 4 or 8)", address_size);
        return nullptr;
      }
      address = address_size == 4 ? bs->ReadUint32() : bs->ReadUint64();
    } else if (form == DW_FORM_addrx || form == DW_FORM_addrx1 || form == DW_FORM_addrx2 ||
               form == DW_FORM_addrx3 || form == DW_FORM_addrx4) {
      address = bs->ReadLeb128();
    } else {
      *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                __FILE__,
                                __LINE__,
                                __FUNCTION__,
                                FormToString(form).c_str(),
                                NameToString(name).c_str());
      return nullptr;
    }

    return std::unique_ptr<DwarfAttribute>(new DwarfAttributeValue<uint64_t>(name, address));
  }
};

class DwarfClassBlock : public DwarfClass {
 public:
  DwarfClassBlock() : DwarfClass("block") {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader*,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint64_t size = 0;
    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    switch (form) {
      case DW_FORM_block1:
        size = bs->ReadUint8();
        break;
      case DW_FORM_block2:
        size = bs->ReadUint16();
        break;
      case DW_FORM_block4:
        size = bs->ReadUint32();
        break;
      case DW_FORM_block:
        size = bs->ReadLeb128();
        break;
      default:
        *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                  __FILE__,
                                  __LINE__,
                                  __FUNCTION__,
                                  FormToString(form).c_str(),
                                  NameToString(name).c_str());
        return nullptr;
    }

    std::vector<uint8_t> data = bs->ReadBytes(size);

    return std::unique_ptr<DwarfAttribute>(
        new DwarfAttributeValue<std::vector<uint8_t>>(name, std::move(data)));
  }
};

class DwarfClassConstant : public DwarfClass {
 public:
  DwarfClassConstant() : DwarfClass("constant") {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader*,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint64_t size = 0;
    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    if (form == DW_FORM_implicit_const) {
      return std::unique_ptr<DwarfAttribute>(
          new DwarfAttributeValue<int64_t>(name, abbrev_attr->value()));
    }

    if (form == DW_FORM_sdata) {
      return std::unique_ptr<DwarfAttribute>(
          new DwarfAttributeValue<int64_t>(name, bs->ReadSleb128()));
    }

    if (form == DW_FORM_udata) {
      return std::unique_ptr<DwarfAttribute>(
          new DwarfAttributeValue<uint64_t>(name, bs->ReadLeb128()));
    }

    switch (form) {
      case DW_FORM_data1:
        size = 1;
        break;
      case DW_FORM_data2:
        size = 2;
        break;
      case DW_FORM_data4:
        size = 4;
        break;
      case DW_FORM_data8:
        size = 8;
        break;
      case DW_FORM_data16:
        size = 16;
        break;
      default:
        *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                  __FILE__,
                                  __LINE__,
                                  __FUNCTION__,
                                  FormToString(form).c_str(),
                                  NameToString(name).c_str());
        return nullptr;
    }

    std::vector<uint8_t> data = bs->ReadBytes(size);

    return std::unique_ptr<DwarfAttribute>(
        new DwarfAttributeValue<std::vector<uint8_t>>(name, std::move(data)));
  }
};

class DwarfClassExprloc : public DwarfClass {
 public:
  DwarfClassExprloc() : DwarfClass("exprloc") {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader*,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    if (form != DW_FORM_exprloc) {
      *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                __FILE__,
                                __LINE__,
                                __FUNCTION__,
                                FormToString(form).c_str(),
                                NameToString(name).c_str());
      return nullptr;
    }

    uint64_t length = bs->ReadLeb128();

    return std::unique_ptr<DwarfAttribute>(
        new DwarfAttributeValue<std::vector<uint8_t>>(name, bs->ReadBytes(length)));
  }
};

class DwarfClassFlag : public DwarfClass {
 public:
  DwarfClassFlag() : DwarfClass("flag") {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader*,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    bool value;

    if (form == DW_FORM_flag_present) {
      value = true;
    } else if (form == DW_FORM_flag) {
      value = bs->ReadUint8();
    } else {
      *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                __FILE__,
                                __LINE__,
                                __FUNCTION__,
                                FormToString(form).c_str(),
                                NameToString(name).c_str());
      return nullptr;
    }

    return std::unique_ptr<DwarfAttribute>(new DwarfAttributeValue<bool>(name, value));
  }
};

// Use this implementation for classes where we are not interested in the value
// This one reads offset and puts it into attribute list. It does not read the
// actual value from the corresponding target segment.
class DwarfClassBaseptr : public DwarfClass {
 public:
  explicit DwarfClassBaseptr(const char* name) : DwarfClass(name) {}
  virtual std::unique_ptr<DwarfAttribute> ReadAttribute(const DwarfCompilationUnitHeader* cu,
                                                        const DwarfAbbrevAttribute* abbrev_attr,
                                                        DwarfContext* context,
                                                        std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    if (form == DW_FORM_sec_offset) {
      uint64_t offset = cu->is_dwarf64() ? bs->ReadUint64() : bs->ReadUint32();
      return std::make_unique<DwarfAttributeValue<uint64_t>>(name, offset);
    }

    if (form == DW_FORM_rnglistx || form == DW_FORM_loclistx) {
      return std::make_unique<DwarfAttributeValue<uint64_t>>(name, bs->ReadLeb128());
    }

    *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                              __FILE__,
                              __LINE__,
                              __FUNCTION__,
                              FormToString(form).c_str(),
                              NameToString(name).c_str());
    return nullptr;
  }
};

class DwarfClassReference : public DwarfClass {
 public:
  DwarfClassReference() : DwarfClass("reference") {}
  [[nodiscard]] std::unique_ptr<DwarfAttribute> ReadAttribute(
      const DwarfCompilationUnitHeader* cu,
      const DwarfAbbrevAttribute* abbrev_attr,
      DwarfContext* context,
      std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    uint64_t offset = 0;
    switch (form) {
      case DW_FORM_ref1:
        offset = cu->unit_offset() + bs->ReadUint8();
        break;
      case DW_FORM_ref2:
        offset = cu->unit_offset() + bs->ReadUint16();
        break;
      case DW_FORM_ref4:
        offset = cu->unit_offset() + bs->ReadUint32();
        break;
      case DW_FORM_ref8:
        offset = cu->unit_offset() + bs->ReadUint64();
        break;
      case DW_FORM_ref_udata:
        offset = cu->unit_offset() + bs->ReadLeb128();
        break;
      case DW_FORM_ref_addr:
        offset = cu->is_dwarf64() ? bs->ReadUint64() : bs->ReadUint32();
        break;
      // TODO(dimitry): DW_FORM_ref_sig8?
      default:
        *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                  __FILE__,
                                  __LINE__,
                                  __FUNCTION__,
                                  FormToString(form).c_str(),
                                  NameToString(name).c_str());
        return nullptr;
    }

    return std::unique_ptr<DwarfAttribute>(new DwarfAttributeValue<uint64_t>(name, offset));
  }
};

class DwarfClassString : public DwarfClass {
 public:
  DwarfClassString() : DwarfClass("string") {}

  [[nodiscard]] std::unique_ptr<DwarfAttribute> ReadAttribute(
      const DwarfCompilationUnitHeader* cu,
      const DwarfAbbrevAttribute* abbrev_attr,
      DwarfContext* context,
      std::string* error_msg) const override {
    ByteInputStream* bs = context->info_stream();

    uint32_t form = abbrev_attr->form();
    uint32_t name = abbrev_attr->name();

    switch (form) {
      case DW_FORM_string:
        // This form should be deprecated...
        return std::make_unique<DwarfAttributeValue<std::string>>(name, bs->ReadString());
      case DW_FORM_strp: {
        uint64_t offset = cu->is_dwarf64() ? bs->ReadUint64() : bs->ReadUint32();
        std::string value = context->debug_str_table()->GetString(offset);
        return std::make_unique<DwarfAttributeValue<std::string>>(name, value);
      }
      case DW_FORM_strx:
        return std::make_unique<DwarfStrXAttribute>(name, bs->ReadLeb128());
      case DW_FORM_strx1:
        return std::make_unique<DwarfStrXAttribute>(name, bs->ReadUint8());
      case DW_FORM_strx2:
        return std::make_unique<DwarfStrXAttribute>(name, bs->ReadUint16());
      case DW_FORM_strx3:
        return std::make_unique<DwarfStrXAttribute>(name, bs->ReadUint24());
      case DW_FORM_strx4:
        return std::make_unique<DwarfStrXAttribute>(name, bs->ReadUint32());
      default:
        // We do not support supplemental object files and debug_line_str (DW_FORM_line_strp) atm.
        *error_msg = StringPrintf("%s:%d:%s: Unsupported form %s for class: %s",
                                  __FILE__,
                                  __LINE__,
                                  __FUNCTION__,
                                  FormToString(form).c_str(),
                                  NameToString(name).c_str());
        return nullptr;
    }
  }
};

const DwarfClass* FindDwarfClass(uint16_t version,
                                 uint32_t name,
                                 uint32_t form,
                                 std::string* error_msg) {
  if (form > DW_FORM_MAX_VALUE) {
    *error_msg = StringPrintf("Invalid abbrev attribute form: 0x%x", form);
    return nullptr;
  }

  auto name_descriptor = GetNameDescriptor(name);
  if (name_descriptor == nullptr) {
    *error_msg = StringPrintf("Invalid abbrev attribute name: 0x%x", name);
    return nullptr;
  }

  auto name_classes = name_descriptor->classes.get(version);
  if (name_classes == nullptr) {
    *error_msg = StringPrintf(
        "failed to lookup classes for %s (0x%x) version=%d", name_descriptor->name, name, version);
    return nullptr;
  }

  auto& form_descriptor = kFormDescriptors[form];
  auto form_classes = form_descriptor.classes.get(version);

  if (form_classes == nullptr) {
    *error_msg = StringPrintf(
        "failed to lookup classes for %s (0x%x) version=%d", form_descriptor.name, form, version);
    return nullptr;
  }

  // Check if the class identified by form actually in the list of classes
  // supported by name
  const DwarfClass* result = nullptr;
  for (auto form_class : *form_classes) {
    for (auto name_class : *name_classes) {
      if (name_class == form_class) {  // found valid combination
        if (result != nullptr) {
          *error_msg = StringPrintf(
              "Incompatible combination of form %s(%x) and name %s(%x): "
              "Found more than one intersection of classes (%s and %s)",
              form_descriptor.name,
              form,
              name_descriptor->name,
              name,
              result->name(),
              name_class->name());
          return nullptr;
        }

        result = name_class;
      }
    }
  }

  if (result == nullptr) {
    *error_msg = StringPrintf("form %s (0x%x) is not applicable to the name %s (0x%x) version=%d.",
                              form_descriptor.name,
                              form,
                              name_descriptor->name,
                              name,
                              version);
  }

  return result;
}

DwarfClassAddress g_class_address;
DwarfClassBaseptr g_class_addrptr("addrptr");
DwarfClassBlock g_class_block;
DwarfClassConstant g_class_constant;
DwarfClassExprloc g_class_exprloc;
DwarfClassFlag g_class_flag;
DwarfClassBaseptr g_class_lineptr("lineptr");
DwarfClassBaseptr g_class_loclist("loclist");
DwarfClassBaseptr g_class_loclistptr("loclistptr");
DwarfClassBaseptr g_class_macptr("macptr");
DwarfClassReference g_class_reference;
DwarfClassBaseptr g_class_rnglist("rnglist");
DwarfClassBaseptr g_class_rnglistptr("rnglistptr");
DwarfClassString g_class_string;
DwarfClassBaseptr g_class_stroffsetsptr("rnglistptr");

}  // namespace

const DwarfClass* DwarfClass::kAddress = &g_class_address;
const DwarfClass* DwarfClass::kAddrptr = &g_class_addrptr;
const DwarfClass* DwarfClass::kBlock = &g_class_block;
const DwarfClass* DwarfClass::kConstant = &g_class_constant;
const DwarfClass* DwarfClass::kFlag = &g_class_flag;
const DwarfClass* DwarfClass::kExprloc = &g_class_exprloc;
const DwarfClass* DwarfClass::kLineptr = &g_class_lineptr;
const DwarfClass* DwarfClass::kLoclist = &g_class_loclist;
const DwarfClass* DwarfClass::kLoclistsptr = &g_class_loclistptr;
const DwarfClass* DwarfClass::kMacptr = &g_class_macptr;
const DwarfClass* DwarfClass::kReference = &g_class_reference;
const DwarfClass* DwarfClass::kRnglist = &g_class_rnglist;
const DwarfClass* DwarfClass::kRnglistsptr = &g_class_rnglistptr;
const DwarfClass* DwarfClass::kString = &g_class_string;
const DwarfClass* DwarfClass::kStroffsetsptr = &g_class_stroffsetsptr;

DwarfClass::DwarfClass(const char* name) : name_{name} {}

const char* DwarfClass::name() const {
  return name_;
}

DwarfAttribute::DwarfAttribute(uint32_t name) : name_(name) {}

template <typename T>
std::optional<std::string> DwarfAttributeValue<T>::StringValue() const {
  return {};
}

template <typename T>
std::optional<uint64_t> DwarfAttributeValue<T>::Uint64Value() const {
  return {};
}

template <typename T>
std::optional<bool> DwarfAttributeValue<T>::BoolValue() const {
  return {};
}
template <typename T>
void DwarfAttributeValue<T>::Resolve(DwarfContext* /*context*/) {}

template <>
std::optional<std::string> DwarfAttributeValue<std::string>::StringValue() const {
  return value_;
}

template <>
std::optional<uint64_t> DwarfAttributeValue<uint64_t>::Uint64Value() const {
  return value_;
}

template <>
std::optional<uint64_t> DwarfAttributeValue<std::vector<uint8_t>>::Uint64Value() const {
  CHECK(value_.size() <= sizeof(uint64_t));
  uint64_t result = 0;
  memcpy(&result, value_.data(), value_.size());
  return result;
}

template <>
std::optional<bool> DwarfAttributeValue<bool>::BoolValue() const {
  return value_;
}

std::optional<std::string> DwarfStrXAttribute::StringValue() const {
  // At this point we expect the string to exist.
  CHECK(string_.has_value());
  return string_.value();
}

void DwarfStrXAttribute::Resolve(DwarfContext* context) {
  CHECK(context->str_offsets_base().has_value());
  CHECK(context->string_offset_table().has_value());
  uint64_t string_offset =
      context->string_offset_table()->GetStringOffset(context->str_offsets_base().value(), index_);
  string_.emplace(context->debug_str_table()->GetString(string_offset));
}

std::unique_ptr<const DwarfAbbrevAttribute> DwarfAbbrevAttribute::CreateAbbrevAttribute(
    uint16_t version,
    uint32_t name,
    uint32_t form,
    int64_t value,
    std::string* error_msg) {
  // TODO(dimitry): support DW_FORM_indirect, might require some refactoring of DwarfClass
  if (form == DW_FORM_indirect) {
    *error_msg = "DW_FORM_indirect is not yet supported.";
    return nullptr;
  }

  const DwarfClass* dwarf_class = FindDwarfClass(version, name, form, error_msg);

  if (dwarf_class == nullptr) {
    return nullptr;
  }

  return std::make_unique<DwarfAbbrevAttribute>(name, form, value, dwarf_class);
}

DwarfCompilationUnitHeader::DwarfCompilationUnitHeader(uint64_t unit_offset,
                                                       uint64_t unit_length,
                                                       uint16_t version,
                                                       uint64_t abbrev_offset,
                                                       uint8_t address_size,
                                                       bool is_dwarf64)
    : unit_offset_(unit_offset),
      unit_length_(unit_length),
      version_(version),
      abbrev_offset_(abbrev_offset),
      address_size_(address_size),
      is_dwarf64_(is_dwarf64) {}

DwarfAbbrev::DwarfAbbrev() : code_(0), tag_(0), has_children_(false) {}

DwarfAbbrev::DwarfAbbrev(uint64_t code, uint64_t tag, bool has_children)
    : code_(code), tag_(tag), has_children_(has_children) {}

void DwarfAbbrev::AddAttribute(std::unique_ptr<const DwarfAbbrevAttribute>&& abbrev_attribute) {
  attributes_.push_back(std::move(abbrev_attribute));
}

DwarfAbbrevAttribute::DwarfAbbrevAttribute()
    : name_{0}, form_{0}, value_{0}, dwarf_class_{nullptr} {}

DwarfAbbrevAttribute::DwarfAbbrevAttribute(uint32_t name,
                                           uint32_t form,
                                           int64_t value,
                                           const DwarfClass* dwarf_class)
    : name_(name), form_(form), value_(value), dwarf_class_(dwarf_class) {}

}  // namespace nogrod
