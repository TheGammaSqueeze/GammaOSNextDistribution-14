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

#include "fields/body_field.h"

const std::string BodyField::kFieldType = "BodyField";

BodyField::BodyField(ParseLocation loc) : PacketField("body", loc) {}

void BodyField::SetSizeField(const SizeField* size_field) {
  if (size_field_ != nullptr) {
    ERROR(this, size_field_, size_field) << "The size field for the body has already been assigned.";
  }
  size_field_ = size_field;
}

const std::string& BodyField::GetFieldType() const {
  return BodyField::kFieldType;
}

Size BodyField::GetSize() const {
  if (size_field_ == nullptr) {
    return Size(0);
  }
  std::string dynamic_size = "(" + size_field_->GetName() + " * 8)";
  return dynamic_size;
}

std::string BodyField::GetDataType() const {
  ERROR(this) << "No need to know the type of a body field.";
  return "BodyType";
}

void BodyField::GenExtractor(std::ostream&, int, bool) const {}

std::string BodyField::GetGetterFunctionName() const {
  return "";
}

void BodyField::GenGetter(std::ostream&, Size, Size) const {}

std::string BodyField::GetBuilderParameterType() const {
  return "";
}

bool BodyField::HasParameterValidator() const {
  return false;
}

void BodyField::GenParameterValidator(std::ostream&) const {
  // There is no validation needed for a payload
}

void BodyField::GenInserter(std::ostream&) const {
  // Do nothing
}

void BodyField::GenValidator(std::ostream&) const {
  // Do nothing
}

void BodyField::GenStringRepresentation(std::ostream& s, std::string accessor) const {
  s << "\"BODY REPRESENTATION_UNIMPLEMENTED " << accessor << " \"";
}
