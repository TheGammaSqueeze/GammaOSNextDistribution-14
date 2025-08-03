/*
 * Copyright 2019 The Android Open Source Project
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

#pragma once

#include <cstdint>
#include <map>
#include <set>
#include <variant>
#include <vector>

#include "enum_def.h"
#include "field_list.h"
#include "fields/all_fields.h"
#include "fields/packet_field.h"
#include "parse_location.h"
#include "type_def.h"

class ParentDef : public TypeDef {
 public:
  ParentDef(std::string name, FieldList fields);
  ParentDef(std::string name, FieldList fields, ParentDef* parent);

  void AddParentConstraint(std::string field_name, std::variant<int64_t, std::string> value);

  void AddTestCase(std::string packet_bytes);

  // Assign all size fields to their corresponding variable length fields.
  // Will crash if
  //  - there aren't any fields that don't match up to a field.
  //  - the size field points to a fixed size field.
  //  - if the size field comes after the variable length field.
  void AssignSizeFields();

  void SetEndianness(bool is_little_endian);

  // Get the size. You scan specify without_payload to exclude payload and body fields as children override them.
  Size GetSize(bool without_payload = false) const;

  // Get the offset until the field is reached, if there is no field
  // returns an empty Size. from_end requests the offset to the field
  // starting from the end() iterator. If there is a field with an unknown
  // size along the traversal, then an empty size is returned.
  Size GetOffsetForField(std::string field_name, bool from_end = false) const;

  FieldList GetParamList() const;

  void GenMembers(std::ostream& s) const;

  void GenSize(std::ostream& s) const;

  void GenSerialize(std::ostream& s) const;

  void GenInstanceOf(std::ostream& s) const;

  const ParentDef* GetRootDef() const;

  bool HasAncestorNamed(std::string name) const;

  std::map<std::string, std::variant<int64_t, std::string>> GetAllConstraints() const;

  std::vector<const ParentDef*> GetAncestors() const;

  std::string FindConstraintField() const;

  std::map<const ParentDef*, const std::variant<int64_t, std::string>>
      FindDescendantsWithConstraint(std::string constraint_name) const;
  std::vector<const ParentDef*> FindPathToDescendant(std::string descendant) const;

  FieldList fields_;

  ParentDef* parent_{nullptr};

  ParentDef* complement_{nullptr};

  std::vector<ParentDef*> children_;

  std::set<std::string> test_cases_;
  std::map<std::string, std::variant<int64_t, std::string>> parent_constraints_;
  bool is_little_endian_;

  bool HasChildEnums() const;

  void GenSizeRetVal(std::ostream& s) const;
};
