/*
 * Copyright 2020 The Android Open Source Project
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

#include <algorithm>
#include <string>

#include "dumpsys/internal/filter_internal.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/idl.h"
#include "os/log.h"

#define DBG 0

using namespace bluetooth;
using namespace dumpsys;

constexpr flatbuffers::voffset_t kErasedFromTable = 0;
constexpr bool kFieldIsNotPopulated = true;
constexpr bool kFieldHasBeenFiltered = true;
constexpr bool kFieldContinueFiltering = false;

void internal::ScrubFromTable(flatbuffers::Table* table, flatbuffers::voffset_t field_offset) {
  ASSERT(table != nullptr);
  uint8_t* vtable = const_cast<uint8_t*>(table->GetVTable());
  vtable[field_offset] = kErasedFromTable;
}

void internal::ReplaceInString(flatbuffers::String* string, int c) {
  uint8_t* p = const_cast<uint8_t*>(string->Data());
  memset(p, c, string->size());
}

void internal::RandomizeInString(flatbuffers::String* string) {
  std::size_t hash = std::hash<std::string>{}(string->str());
  std::string hashed_string = std::to_string(hash);
  ReplaceInString(string, ' ');
  size_t len = std::min(static_cast<size_t>(string->size()), hashed_string.size());
  uint8_t* p = const_cast<uint8_t*>(string->Data());
  memcpy(p, hashed_string.c_str(), len);
}

const char* internal::PrivacyLevelName(PrivacyLevel privacy_level) {
  switch (privacy_level) {
    case kPrivate:
      return "Private";
      break;
    case kOpaque:
      return "Opaque";
      break;
    case kAnonymized:
      return "Anonymized";
      break;
    case kAny:
      return "Any";
      break;
  }
};
internal::PrivacyLevel internal::GetPrivacyLevelAttribute(const std::string& string) {
  if (string == "Any") {
    return kAny;
  } else if (string == "Anonymized") {
    return kAnonymized;
  } else if (string == "Opaque") {
    return kOpaque;
  } else if (string == "Private") {
    return kPrivate;
  }
  return kDefaultPrivacyLevel;
}

internal::PrivacyLevel internal::FindFieldPrivacyLevel(const reflection::Field& field) {
  PrivacyLevel privacy_level = kDefaultPrivacyLevel;

  if (field.attributes() != nullptr) {
    auto key = field.attributes()->LookupByKey(kPrivacyAttributeKeyword);
    if (key != nullptr) {
      privacy_level = internal::GetPrivacyLevelAttribute(key->value()->str());
    }
  }
  return privacy_level;
}

const reflection::Object* internal::FindReflectionObject(
    const flatbuffers::Vector<flatbuffers::Offset<reflection::Object>>* objects, const flatbuffers::String* name) {
  ASSERT(objects != nullptr);
  ASSERT(name != nullptr);
  for (auto it = objects->cbegin(); it != objects->cend(); ++it) {
    if (it->name()->str() == name->str()) {
      return *it;
    }
  }
  return nullptr;
}

bool internal::FilterTypeBool(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);

  const bool default_val = flatbuffers::GetFieldDefaultI<int8_t>(field);
  flatbuffers::voffset_t field_offset = field.offset();

  // boolean privacy levels are simpler.
  switch (privacy_level) {
    case kPrivate:
    case kOpaque:
    case kAnonymized:
      flatbuffers::SetField<int8_t>(table, field, default_val);
      internal::ScrubFromTable(table, field_offset);
      break;
    default:
    case kAny:
      break;
  }
  return kFieldHasBeenFiltered;
}

bool internal::FilterTypeInteger(
    const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);
  ASSERT(flatbuffers::IsInteger(field.type()->base_type()));

  int32_t default_val = flatbuffers::GetFieldDefaultI<int32_t>(field);
  flatbuffers::voffset_t field_offset = field.offset();
  [[maybe_unused]] int32_t val = table->GetField<int32_t>(field_offset, default_val);

  switch (privacy_level) {
    case kPrivate:
      flatbuffers::SetField<int32_t>(table, field, default_val);
      internal::ScrubFromTable(table, field_offset);
      break;
    case kOpaque:
      flatbuffers::SetField<int32_t>(table, field, default_val);
      break;
    case kAnonymized: {
      auto target_field = flatbuffers::GetFieldI<int32_t>(*table, field);
      int32_t new_val = static_cast<int32_t>(std::hash<std::string>{}(std::to_string(target_field)));
      flatbuffers::SetField<int32_t>(table, field, new_val);
    } break;
    default:
    case kAny:
      break;
  }

  if (DBG) {
    LOG_INFO(
        "Integer Field_name:%s privacy_level:%s old_value:%d / 0x%x ==> new_value:%d\n",
        field.name()->c_str(),
        PrivacyLevelName(privacy_level),
        val,
        val,
        table->GetField<int32_t>(field_offset, default_val));
  }
  return kFieldHasBeenFiltered;
}

bool internal::FilterTypeFloat(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);
  ASSERT(flatbuffers::IsFloat(field.type()->base_type()));

  float default_val = flatbuffers::GetFieldDefaultI<float>(field);
  flatbuffers::voffset_t field_offset = field.offset();
  [[maybe_unused]] float val = table->GetField<float>(field_offset, default_val);
  switch (privacy_level) {
    case kPrivate:
      flatbuffers::SetField<float>(table, field, default_val);
      internal::ScrubFromTable(table, field_offset);
      break;
    case kOpaque:
      flatbuffers::SetField<float>(table, field, default_val);
      break;
    case kAnonymized: {
      auto target_field = flatbuffers::GetFieldF<float>(*table, field);
      int32_t new_val = static_cast<float>(std::hash<std::string>{}(std::to_string(target_field)));
      flatbuffers::SetField<float>(table, field, new_val);
    } break;
    default:
    case kAny:
      break;
  }
  if (DBG) {
    LOG_INFO(
        "Float Field_name:%s privacy_level:%s old_value:%f ==> new_value:%f",
        field.name()->c_str(),
        PrivacyLevelName(privacy_level),
        val,
        table->GetField<float>(field_offset, default_val));
  }
  return kFieldHasBeenFiltered;
}

bool internal::FilterTypeLong(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);

  const int64_t default_val = flatbuffers::GetFieldDefaultI<int64_t>(field);
  flatbuffers::voffset_t field_offset = field.offset();

  switch (privacy_level) {
    case kPrivate:
      flatbuffers::SetField<int64_t>(table, field, default_val);
      internal::ScrubFromTable(table, field_offset);
      break;
    case kOpaque:
      flatbuffers::SetField<int64_t>(table, field, default_val);
      break;
    case kAnonymized: {
      auto target_field = flatbuffers::GetFieldI<int64_t>(*table, field);
      int64_t new_val = static_cast<int64_t>(std::hash<std::string>{}(std::to_string(target_field)));
      flatbuffers::SetField<int64_t>(table, field, new_val);
    } break;
    default:
    case kAny:
      break;
  }
  return kFieldHasBeenFiltered;
}

bool internal::FilterTypeString(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);
  ASSERT(field.type()->base_type() == reflection::BaseType::String);

  flatbuffers::voffset_t field_offset = field.offset();

  const flatbuffers::String* string = flatbuffers::GetFieldS(*table, field);
  if (string == nullptr) {
    return kFieldIsNotPopulated;
    // Field is not populated
  }
  ASSERT(string != nullptr);
  flatbuffers::String* mutable_string = const_cast<flatbuffers::String*>(string);

  [[maybe_unused]] std::string old_string(string->str());
  switch (privacy_level) {
    case kPrivate:
      internal::ReplaceInString(mutable_string, '*');
      internal::ScrubFromTable(table, field_offset);
      break;
    case kOpaque:
      internal::ReplaceInString(mutable_string, '*');
      break;
    case kAnonymized:
      internal::RandomizeInString(mutable_string);
      break;
    default:
    case kAny:
      break;
  }
  if (DBG) {
    LOG_INFO(
        "%s Field_name:%s size:%u privacy_level:%s old_string:%s ==> new_string:%s",
        __func__,
        field.name()->c_str(),
        string->size(),
        PrivacyLevelName(privacy_level),
        old_string.c_str(),
        string->c_str());
  }
  return kFieldHasBeenFiltered;
}

bool internal::FilterTypeStruct(const reflection::Field& field, flatbuffers::Table* table, PrivacyLevel privacy_level) {
  ASSERT(table != nullptr);
  ASSERT(!flatbuffers::IsScalar(field.type()->base_type()));

  flatbuffers::voffset_t field_offset = field.offset();

  if (privacy_level != kAny) {
    flatbuffers::SetFieldT(table, field, nullptr);
    internal::ScrubFromTable(table, field_offset);
    if (DBG) {
      LOG_INFO(
          " Table Removing field name:%s privacy_level:%s", field.name()->c_str(), PrivacyLevelName(privacy_level));
    }
  }
  return kFieldContinueFiltering;
}
