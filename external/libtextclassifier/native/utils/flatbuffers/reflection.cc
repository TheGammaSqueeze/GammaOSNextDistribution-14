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

#include "utils/flatbuffers/reflection.h"

namespace libtextclassifier3 {

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const StringPiece field_name) {
  TC3_CHECK(type != nullptr && type->fields() != nullptr);
  return type->fields()->LookupByKey(field_name.data());
}

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const int field_offset) {
  if (type->fields() == nullptr) {
    return nullptr;
  }
  for (const reflection::Field* field : *type->fields()) {
    if (field->offset() == field_offset) {
      return field;
    }
  }
  return nullptr;
}

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const StringPiece field_name,
                                        const int field_offset) {
  // Lookup by name might be faster as the fields are sorted by name in the
  // schema data, so try that first.
  if (!field_name.empty()) {
    return GetFieldOrNull(type, field_name.data());
  }
  return GetFieldOrNull(type, field_offset);
}

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const FlatbufferField* field) {
  TC3_CHECK(type != nullptr && field != nullptr);
  if (field->field_name() == nullptr) {
    return GetFieldOrNull(type, field->field_offset());
  }
  return GetFieldOrNull(
      type,
      StringPiece(field->field_name()->data(), field->field_name()->size()),
      field->field_offset());
}

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const FlatbufferFieldT* field) {
  TC3_CHECK(type != nullptr && field != nullptr);
  return GetFieldOrNull(type, field->field_name, field->field_offset);
}

const reflection::Object* TypeForName(const reflection::Schema* schema,
                                      const StringPiece type_name) {
  for (const reflection::Object* object : *schema->objects()) {
    if (type_name.Equals(object->name()->str())) {
      return object;
    }
  }
  return nullptr;
}

Optional<int> TypeIdForObject(const reflection::Schema* schema,
                              const reflection::Object* type) {
  for (int i = 0; i < schema->objects()->size(); i++) {
    if (schema->objects()->Get(i) == type) {
      return Optional<int>(i);
    }
  }
  return Optional<int>();
}

Optional<int> TypeIdForName(const reflection::Schema* schema,
                            const StringPiece type_name) {
  for (int i = 0; i < schema->objects()->size(); i++) {
    if (type_name.Equals(schema->objects()->Get(i)->name()->str())) {
      return Optional<int>(i);
    }
  }
  return Optional<int>();
}

bool SwapFieldNamesForOffsetsInPath(const reflection::Schema* schema,
                                    FlatbufferFieldPathT* path) {
  if (schema == nullptr || !schema->root_table()) {
    TC3_LOG(ERROR) << "Empty schema provided.";
    return false;
  }

  reflection::Object const* type = schema->root_table();
  for (int i = 0; i < path->field.size(); i++) {
    const reflection::Field* field = GetFieldOrNull(type, path->field[i].get());
    if (field == nullptr) {
      TC3_LOG(ERROR) << "Could not find field: " << path->field[i]->field_name;
      return false;
    }
    path->field[i]->field_name.clear();
    path->field[i]->field_offset = field->offset();

    // Descend.
    if (i < path->field.size() - 1) {
      if (field->type()->base_type() != reflection::Obj) {
        TC3_LOG(ERROR) << "Field: " << field->name()->str()
                       << " is not of type `Object`.";
        return false;
      }
      type = schema->objects()->Get(field->type()->index());
    }
  }
  return true;
}

Variant ParseEnumValue(const reflection::Schema* schema,
                       const reflection::Type* type, StringPiece value) {
  TC3_DCHECK(IsEnum(type));
  TC3_CHECK_NE(schema->enums(), nullptr);
  const auto* enum_values = schema->enums()->Get(type->index())->values();
  if (enum_values == nullptr) {
    TC3_LOG(ERROR) << "Enum has no specified values.";
    return Variant();
  }
  for (const reflection::EnumVal* enum_value : *enum_values) {
    if (value.Equals(StringPiece(enum_value->name()->c_str(),
                                 enum_value->name()->size()))) {
      const int64 value = enum_value->value();
      switch (type->base_type()) {
        case reflection::BaseType::Byte:
          return Variant(static_cast<int8>(value));
        case reflection::BaseType::UByte:
          return Variant(static_cast<uint8>(value));
        case reflection::BaseType::Short:
          return Variant(static_cast<int16>(value));
        case reflection::BaseType::UShort:
          return Variant(static_cast<uint16>(value));
        case reflection::BaseType::Int:
          return Variant(static_cast<int32>(value));
        case reflection::BaseType::UInt:
          return Variant(static_cast<uint32>(value));
        case reflection::BaseType::Long:
          return Variant(value);
        case reflection::BaseType::ULong:
          return Variant(static_cast<uint64>(value));
        default:
          break;
      }
    }
  }
  return Variant();
}

}  // namespace libtextclassifier3
