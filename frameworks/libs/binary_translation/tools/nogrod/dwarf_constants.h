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

#ifndef NOGROD_DWARF_CONSTANTS_
#define NOGROD_DWARF_CONSTANTS_

#include <cstdint>

enum class DwarfFormat { k32Bit, k64Bit };

// Unit header unit type encodings
constexpr uint8_t DW_UT_compile = 0x01;
constexpr uint8_t DW_UT_type = 0x02;
constexpr uint8_t DW_UT_partial = 0x03;

constexpr uint8_t DW_CHILDREN_no = 0x0;
constexpr uint8_t DW_CHILDREN_yes = 0x1;

// Base type encoding values
constexpr uint8_t DW_ATE_address = 0x01;
constexpr uint8_t DW_ATE_boolean = 0x02;
constexpr uint8_t DW_ATE_complex_float = 0x03;
constexpr uint8_t DW_ATE_float = 0x04;
constexpr uint8_t DW_ATE_signed = 0x05;
constexpr uint8_t DW_ATE_signed_char = 0x06;
constexpr uint8_t DW_ATE_unsigned = 0x07;
constexpr uint8_t DW_ATE_unsigned_char = 0x08;
constexpr uint8_t DW_ATE_imaginary_float = 0x09;
constexpr uint8_t DW_ATE_packed_decimal = 0x0a;
constexpr uint8_t DW_ATE_numeric_string = 0x0b;
constexpr uint8_t DW_ATE_edited = 0x0c;
constexpr uint8_t DW_ATE_signed_fixed = 0x0d;
constexpr uint8_t DW_ATE_unsigned_fixed = 0x0e;
constexpr uint8_t DW_ATE_decimal_float = 0x0f;
constexpr uint8_t DW_ATE_UTF = 0x10;
constexpr uint8_t DW_ATE_UCS = 0x11;
constexpr uint8_t DW_ATE_ASCII = 0x12;

// Tag encodings
constexpr uint16_t DW_TAG_array_type = 0x01;
constexpr uint16_t DW_TAG_class_type = 0x02;
constexpr uint16_t DW_TAG_entry_point = 0x03;
constexpr uint16_t DW_TAG_enumeration_type = 0x04;
constexpr uint16_t DW_TAG_formal_parameter = 0x05;

constexpr uint16_t DW_TAG_imported_declaration = 0x08;

constexpr uint16_t DW_TAG_label = 0x0a;
constexpr uint16_t DW_TAG_lexical_block = 0x0b;

constexpr uint16_t DW_TAG_member = 0x0d;

constexpr uint16_t DW_TAG_pointer_type = 0x0f;
constexpr uint16_t DW_TAG_reference_type = 0x10;
constexpr uint16_t DW_TAG_compile_unit = 0x11;
constexpr uint16_t DW_TAG_string_type = 0x12;
constexpr uint16_t DW_TAG_structure_type = 0x13;

constexpr uint16_t DW_TAG_subroutine_type = 0x15;
constexpr uint16_t DW_TAG_typedef = 0x16;
constexpr uint16_t DW_TAG_union_type = 0x17;
constexpr uint16_t DW_TAG_unspecified_parameters = 0x18;
constexpr uint16_t DW_TAG_variant = 0x19;
constexpr uint16_t DW_TAG_common_block = 0x1a;
constexpr uint16_t DW_TAG_common_inclusion = 0x1b;
constexpr uint16_t DW_TAG_inheritance = 0x1c;
constexpr uint16_t DW_TAG_inlined_subroutine = 0x1d;
constexpr uint16_t DW_TAG_module = 0x1e;
constexpr uint16_t DW_TAG_ptr_to_member_type = 0x1f;
constexpr uint16_t DW_TAG_set_type = 0x20;
constexpr uint16_t DW_TAG_subrange_type = 0x21;
constexpr uint16_t DW_TAG_with_stmt = 0x22;
constexpr uint16_t DW_TAG_access_declaration = 0x23;
constexpr uint16_t DW_TAG_base_type = 0x24;
constexpr uint16_t DW_TAG_catch_block = 0x25;
constexpr uint16_t DW_TAG_const_type = 0x26;
constexpr uint16_t DW_TAG_constant = 0x27;
constexpr uint16_t DW_TAG_enumerator = 0x28;
constexpr uint16_t DW_TAG_file_type = 0x29;
constexpr uint16_t DW_TAG_friend = 0x2a;
constexpr uint16_t DW_TAG_namelist = 0x2b;
constexpr uint16_t DW_TAG_namelist_item = 0x2c;
constexpr uint16_t DW_TAG_packed_type = 0x2d;
constexpr uint16_t DW_TAG_subprogram = 0x2e;
constexpr uint16_t DW_TAG_template_type_parameter = 0x2f;
constexpr uint16_t DW_TAG_template_value_parameter = 0x30;
constexpr uint16_t DW_TAG_thrown_type = 0x31;
constexpr uint16_t DW_TAG_try_block = 0x32;
constexpr uint16_t DW_TAG_variant_part = 0x33;
constexpr uint16_t DW_TAG_variable = 0x34;
constexpr uint16_t DW_TAG_volatile_type = 0x35;
constexpr uint16_t DW_TAG_dwarf_procedure = 0x36;
constexpr uint16_t DW_TAG_restrict_type = 0x37;
constexpr uint16_t DW_TAG_interface_type = 0x38;
constexpr uint16_t DW_TAG_namespace = 0x39;
constexpr uint16_t DW_TAG_imported_module = 0x3a;
constexpr uint16_t DW_TAG_unspecified_type = 0x3b;
constexpr uint16_t DW_TAG_partial_unit = 0x3c;
constexpr uint16_t DW_TAG_imported_unit = 0x3d;
constexpr uint16_t DW_TAG_condition = 0x3f;
constexpr uint16_t DW_TAG_shared_type = 0x40;
constexpr uint16_t DW_TAG_type_unit = 0x41;
constexpr uint16_t DW_TAG_rvalue_reference_type = 0x42;
constexpr uint16_t DW_TAG_template_alias = 0x43;
// New in Dwarf5
constexpr uint16_t DW_TAG_coarray_type = 0x44;
constexpr uint16_t DW_TAG_generic_subrange = 0x45;
constexpr uint16_t DW_TAG_dynamic_type = 0x46;
constexpr uint16_t DW_TAG_atomic_type = 0x47;
constexpr uint16_t DW_TAG_call_site = 0x48;
constexpr uint16_t DW_TAG_call_site_parameter = 0x49;
constexpr uint16_t DW_TAG_skeleton_unit = 0x4a;
constexpr uint16_t DW_TAG_immutable_type = 0x4b;

// GNU extension tags
constexpr uint16_t DW_TAG_GNU_template_template_param = 0x4106;
constexpr uint16_t DW_TAG_GNU_template_parameter_pack = 0x4107;
constexpr uint16_t DW_TAG_GNU_formal_parameter_pack = 0x4108;
constexpr uint16_t DW_TAG_GNU_call_site = 0x4109;
constexpr uint16_t DW_TAG_GNU_call_site_parameter = 0x410a;

constexpr uint16_t DW_TAG_MAX_VALUE = DW_TAG_GNU_call_site_parameter;

// Attribute form encodings
constexpr uint16_t DW_FORM_addr = 0x01;
constexpr uint16_t DW_FORM_block2 = 0x03;
constexpr uint16_t DW_FORM_block4 = 0x04;
constexpr uint16_t DW_FORM_data2 = 0x05;
constexpr uint16_t DW_FORM_data4 = 0x06;
constexpr uint16_t DW_FORM_data8 = 0x07;
constexpr uint16_t DW_FORM_string = 0x08;
constexpr uint16_t DW_FORM_block = 0x09;
constexpr uint16_t DW_FORM_block1 = 0x0a;
constexpr uint16_t DW_FORM_data1 = 0x0b;
constexpr uint16_t DW_FORM_flag = 0x0c;
constexpr uint16_t DW_FORM_sdata = 0x0d;
constexpr uint16_t DW_FORM_strp = 0x0e;
constexpr uint16_t DW_FORM_udata = 0x0f;
constexpr uint16_t DW_FORM_ref_addr = 0x10;
constexpr uint16_t DW_FORM_ref1 = 0x11;
constexpr uint16_t DW_FORM_ref2 = 0x12;
constexpr uint16_t DW_FORM_ref4 = 0x13;
constexpr uint16_t DW_FORM_ref8 = 0x14;
constexpr uint16_t DW_FORM_ref_udata = 0x15;
constexpr uint16_t DW_FORM_indirect = 0x16;
constexpr uint16_t DW_FORM_sec_offset = 0x17;
constexpr uint16_t DW_FORM_exprloc = 0x18;
constexpr uint16_t DW_FORM_flag_present = 0x19;
constexpr uint16_t DW_FORM_strx = 0x1a;
constexpr uint16_t DW_FORM_addrx = 0x1b;
constexpr uint16_t DW_FORM_ref_sup4 = 0x1c;
constexpr uint16_t DW_FORM_strp_sup = 0x1d;
constexpr uint16_t DW_FORM_data16 = 0x1e;
constexpr uint16_t DW_FORM_line_strp = 0x1f;
constexpr uint16_t DW_FORM_ref_sig8 = 0x20;
constexpr uint16_t DW_FORM_implicit_const = 0x21;
constexpr uint16_t DW_FORM_loclistx = 0x22;
constexpr uint16_t DW_FORM_rnglistx = 0x23;
constexpr uint16_t DW_FORM_ref_sup8 = 0x24;
constexpr uint16_t DW_FORM_strx1 = 0x25;
constexpr uint16_t DW_FORM_strx2 = 0x26;
constexpr uint16_t DW_FORM_strx3 = 0x27;
constexpr uint16_t DW_FORM_strx4 = 0x28;
constexpr uint16_t DW_FORM_addrx1 = 0x29;
constexpr uint16_t DW_FORM_addrx2 = 0x2a;
constexpr uint16_t DW_FORM_addrx3 = 0x2b;
constexpr uint16_t DW_FORM_addrx4 = 0x2c;

constexpr uint16_t DW_FORM_MAX_VALUE = DW_FORM_addrx4;

// Attribute name encodings
constexpr uint16_t DW_AT_sibling = 0x01;
constexpr uint16_t DW_AT_location = 0x02;
constexpr uint16_t DW_AT_name = 0x03;
constexpr uint16_t DW_AT_ordering = 0x09;
constexpr uint16_t DW_AT_byte_size = 0x0b;
constexpr uint16_t DW_AT_bit_offset = 0x0c;  // deprecated in DWARF5?
constexpr uint16_t DW_AT_bit_size = 0x0d;
constexpr uint16_t DW_AT_stmt_list = 0x10;
constexpr uint16_t DW_AT_low_pc = 0x11;
constexpr uint16_t DW_AT_high_pc = 0x12;
constexpr uint16_t DW_AT_language = 0x13;
constexpr uint16_t DW_AT_discr = 0x15;
constexpr uint16_t DW_AT_discr_value = 0x16;
constexpr uint16_t DW_AT_visibility = 0x17;
constexpr uint16_t DW_AT_import = 0x18;
constexpr uint16_t DW_AT_string_length = 0x19;
constexpr uint16_t DW_AT_common_reference = 0x1a;
constexpr uint16_t DW_AT_comp_dir = 0x1b;
constexpr uint16_t DW_AT_const_value = 0x1c;
constexpr uint16_t DW_AT_containing_type = 0x1d;
constexpr uint16_t DW_AT_default_value = 0x1e;
constexpr uint16_t DW_AT_inline = 0x20;
constexpr uint16_t DW_AT_is_optional = 0x21;
constexpr uint16_t DW_AT_lower_bound = 0x22;
constexpr uint16_t DW_AT_producer = 0x25;
constexpr uint16_t DW_AT_prototyped = 0x27;
constexpr uint16_t DW_AT_return_addr = 0x2a;
constexpr uint16_t DW_AT_start_scope = 0x2c;
constexpr uint16_t DW_AT_bit_stride = 0x2e;
constexpr uint16_t DW_AT_upper_bound = 0x2f;
constexpr uint16_t DW_AT_abstract_origin = 0x31;
constexpr uint16_t DW_AT_accessibility = 0x32;
constexpr uint16_t DW_AT_address_class = 0x33;
constexpr uint16_t DW_AT_artificial = 0x34;
constexpr uint16_t DW_AT_base_types = 0x35;
constexpr uint16_t DW_AT_calling_convention = 0x36;
constexpr uint16_t DW_AT_count = 0x37;
constexpr uint16_t DW_AT_data_member_location = 0x38;
constexpr uint16_t DW_AT_decl_column = 0x39;
constexpr uint16_t DW_AT_decl_file = 0x3a;
constexpr uint16_t DW_AT_decl_line = 0x3b;
constexpr uint16_t DW_AT_declaration = 0x3c;
constexpr uint16_t DW_AT_discr_list = 0x3d;
constexpr uint16_t DW_AT_encoding = 0x3e;
constexpr uint16_t DW_AT_external = 0x3f;
//...
constexpr uint16_t DW_AT_specification = 0x47;
constexpr uint16_t DW_AT_static_link = 0x48;
constexpr uint16_t DW_AT_type = 0x49;
constexpr uint16_t DW_AT_use_location = 0x4a;
constexpr uint16_t DW_AT_variable_parameter = 0x4b;
constexpr uint16_t DW_AT_virtuality = 0x4c;
constexpr uint16_t DW_AT_vtable_elem_location = 0x4d;
constexpr uint16_t DW_AT_allocated = 0x4e;
constexpr uint16_t DW_AT_associated = 0x4f;
constexpr uint16_t DW_AT_data_location = 0x50;
constexpr uint16_t DW_AT_byte_stride = 0x51;
constexpr uint16_t DW_AT_entry_pc = 0x52;
constexpr uint16_t DW_AT_use_UTF8 = 0x53;
constexpr uint16_t DW_AT_extension = 0x54;
constexpr uint16_t DW_AT_ranges = 0x55;
constexpr uint16_t DW_AT_trampoline = 0x56;
constexpr uint16_t DW_AT_call_column = 0x57;
constexpr uint16_t DW_AT_call_file = 0x58;
constexpr uint16_t DW_AT_call_line = 0x59;
constexpr uint16_t DW_AT_description = 0x5a;
constexpr uint16_t DW_AT_binary_scale = 0x5b;
constexpr uint16_t DW_AT_decimal_scale = 0x5c;
constexpr uint16_t DW_AT_small = 0x5d;
constexpr uint16_t DW_AT_decimal_sign = 0x5e;
constexpr uint16_t DW_AT_digit_count = 0x5f;
constexpr uint16_t DW_AT_picture_string = 0x60;
constexpr uint16_t DW_AT_mutable = 0x61;
constexpr uint16_t DW_AT_threads_scaled = 0x62;
constexpr uint16_t DW_AT_explicit = 0x63;
constexpr uint16_t DW_AT_object_pointer = 0x64;
constexpr uint16_t DW_AT_endianity = 0x65;
constexpr uint16_t DW_AT_elemental = 0x66;
constexpr uint16_t DW_AT_pure = 0x67;
constexpr uint16_t DW_AT_recursive = 0x68;
constexpr uint16_t DW_AT_signature = 0x69;
constexpr uint16_t DW_AT_main_subprogram = 0x6a;
constexpr uint16_t DW_AT_data_bit_offset = 0x6b;
constexpr uint16_t DW_AT_const_expr = 0x6c;
constexpr uint16_t DW_AT_enum_class = 0x6d;
constexpr uint16_t DW_AT_linkage_name = 0x6e;
constexpr uint16_t DW_AT_string_length_bit_size = 0x6f;
constexpr uint16_t DW_AT_string_length_byte_size = 0x70;
constexpr uint16_t DW_AT_rank = 0x71;
constexpr uint16_t DW_AT_str_offsets_base = 0x72;
//...
constexpr uint16_t DW_AT_loclists_base = 0x8c;

constexpr uint16_t DW_AT_MAX_VALUE = DW_AT_loclists_base;

// GNU extension attributes
constexpr uint16_t DW_AT_GNU_vector = 0x2107;
constexpr uint16_t DW_AT_GNU_template_name = 0x2110;

// GNU extension attributes, http://www.dwarfstd.org/ShowIssue.php?issue=100909.2
constexpr uint16_t DW_AT_GNU_call_site_value = 0x2111;
constexpr uint16_t DW_AT_GNU_call_site_target = 0x2113;
constexpr uint16_t DW_AT_GNU_tail_call = 0x2115;
constexpr uint16_t DW_AT_GNU_all_tail_call_sites = 0x2116;
constexpr uint16_t DW_AT_GNU_all_call_sites = 0x2117;

// GNU extension attributes, see http://gcc.gnu.org/wiki/DebugFission
constexpr uint16_t DW_AT_GNU_pubnames = 0x2134;

// GNU extension attributes, see http://gcc.gnu.org/wiki/Discriminator
constexpr uint16_t DW_AT_GNU_discriminator = 0x2136;
constexpr uint16_t DW_AT_GNU_locviews = 0x2137;
constexpr uint16_t DW_AT_GNU_entry_view = 0x2138;

// Dwarf calling convention constants
constexpr uint64_t DW_CC_LLVM_AAPCS_VFP = 0xc4;

#endif  // NOGROD_DWARF_CONSTANTS_
