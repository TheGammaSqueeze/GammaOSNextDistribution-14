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

#ifndef LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_MUTABLE_H_
#define LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_MUTABLE_H_

#include <memory>
#include <string>
#include <unordered_map>

#include "annotator/model_generated.h"
#include "utils/base/logging.h"
#include "utils/flatbuffers/flatbuffers_generated.h"
#include "utils/flatbuffers/reflection.h"
#include "utils/strings/stringpiece.h"
#include "utils/variant.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/reflection.h"
#include "flatbuffers/reflection_generated.h"

namespace libtextclassifier3 {

class MutableFlatbuffer;
class RepeatedField;

template <typename T>
constexpr bool IsStringType() {
  return std::is_same<T, std::string>::value ||
         std::is_same<T, StringPiece>::value ||
         std::is_same<T, const char*>::value;
}

// Checks whether a variant value type agrees with a field type.
template <typename T>
bool IsMatchingType(const reflection::BaseType type) {
  switch (type) {
    case reflection::String:
      return IsStringType<T>();
    case reflection::Obj:
      return std::is_same<T, MutableFlatbuffer>::value;
    default:
      return type == flatbuffers_base_type<T>::value;
  }
}

// A mutable flatbuffer that can be built using flatbuffer reflection data of
// the schema. Normally, field information is hard-coded in code generated from
// a flatbuffer schema. Here we lookup the necessary information for building a
// flatbuffer from the provided reflection meta data. When serializing a
// flatbuffer, the library requires that the sub messages are already
// serialized, therefore we explicitly keep the field values and serialize the
// message in (reverse) topological dependency order.
class MutableFlatbuffer {
 public:
  MutableFlatbuffer(const reflection::Schema* schema,
                    const reflection::Object* type)
      : schema_(schema), type_(type) {}

  // Gets the field information for a field name, returns nullptr if the
  // field was not defined.
  const reflection::Field* GetFieldOrNull(const StringPiece field_name) const;
  const reflection::Field* GetFieldOrNull(const FlatbufferField* field) const;
  const reflection::Field* GetFieldOrNull(const int field_offset) const;

  // Gets a nested field and the message it is defined on.
  bool GetFieldWithParent(const FlatbufferFieldPath* field_path,
                          MutableFlatbuffer** parent,
                          reflection::Field const** field);

  // Sets a field to a specific value.
  // Returns true if successful, and false if the field was not found or the
  // expected type doesn't match.
  template <typename T>
  bool Set(StringPiece field_name, T value);

  // Sets a field to a specific value.
  // Returns true if successful, and false if the expected type doesn't match.
  // Expects `field` to be non-null.
  template <typename T>
  bool Set(const reflection::Field* field, T value);

  // Sets a field to a specific value. Field is specified by path.
  template <typename T>
  bool Set(const FlatbufferFieldPath* path, T value);

  // Sets an enum field from an enum value name.
  // Returns true if the value could be successfully parsed.
  bool SetFromEnumValueName(StringPiece field_name, StringPiece value_name);

  // Sets an enum field from an enum value name.
  // Returns true if the value could be successfully parsed.
  bool SetFromEnumValueName(const reflection::Field* field,
                            StringPiece value_name);

  // Sets an enum field from an enum value name. Field is specified by path.
  // Returns true if the value could be successfully parsed.
  bool SetFromEnumValueName(const FlatbufferFieldPath* path,
                            StringPiece value_name);

  // Sets sub-message field (if not set yet), and returns a pointer to it.
  // Returns nullptr if the field was not found, or the field type was not a
  // table.
  MutableFlatbuffer* Mutable(StringPiece field_name);
  MutableFlatbuffer* Mutable(const reflection::Field* field);

  // Sets a sub-message field (if not set yet) specified by path, and returns a
  // pointer to it. Returns nullptr if the field was not found, or the field
  // type was not a table.
  MutableFlatbuffer* Mutable(const FlatbufferFieldPath* path);

  // Parses the value (according to the type) and sets a primitive field to the
  // parsed value.
  bool ParseAndSet(const reflection::Field* field, const std::string& value);
  bool ParseAndSet(const FlatbufferFieldPath* path, const std::string& value);

  // Adds a primitive value to the repeated field.
  template <typename T>
  bool Add(StringPiece field_name, T value);

  // Add a sub-message to the repeated field.
  MutableFlatbuffer* Add(StringPiece field_name);

  template <typename T>
  bool Add(const reflection::Field* field, T value);

  MutableFlatbuffer* Add(const reflection::Field* field);

  // Gets the reflective flatbuffer for a repeated field.
  // Returns nullptr if the field was not found, or the field type was not a
  // vector.
  RepeatedField* Repeated(StringPiece field_name);
  RepeatedField* Repeated(const reflection::Field* field);

  // Gets a repeated field specified by path.
  // Returns nullptr if the field was not found, or the field
  // type was not a repeated field.
  RepeatedField* Repeated(const FlatbufferFieldPath* path);

  // Serializes the flatbuffer.
  flatbuffers::uoffset_t Serialize(
      flatbuffers::FlatBufferBuilder* builder) const;
  std::string Serialize() const;

  // Merges the fields from the given flatbuffer table into this flatbuffer.
  // Scalar fields will be overwritten, if present in `from`.
  // Embedded messages will be merged.
  bool MergeFrom(const flatbuffers::Table* from);
  bool MergeFromSerializedFlatbuffer(StringPiece from);

  // Flattens the flatbuffer as a flat map.
  // (Nested) fields names are joined by `key_separator`.
  std::map<std::string, Variant> AsFlatMap(
      const std::string& key_separator = ".") const {
    std::map<std::string, Variant> result;
    AsFlatMap(key_separator, /*key_prefix=*/"", &result);
    return result;
  }

  // Converts the flatbuffer's content to a human-readable textproto
  // representation.
  std::string ToTextProto() const;

  bool HasExplicitlySetFields() const {
    return !fields_.empty() || !children_.empty() || !repeated_fields_.empty();
  }

  const reflection::Object* type() const { return type_; }

 private:
  // Helper function for merging given repeated field from given flatbuffer
  // table. Appends the elements.
  template <typename T>
  bool AppendFromVector(const flatbuffers::Table* from,
                        const reflection::Field* field);

  // Flattens the flatbuffer as a flat map.
  // (Nested) fields names are joined by `key_separator` and prefixed by
  // `key_prefix`.
  void AsFlatMap(const std::string& key_separator,
                 const std::string& key_prefix,
                 std::map<std::string, Variant>* result) const;

  const reflection::Schema* const schema_;
  const reflection::Object* const type_;

  // Cached primitive fields (scalars and strings).
  std::unordered_map<const reflection::Field*, Variant> fields_;

  // Cached sub-messages.
  std::unordered_map<const reflection::Field*,
                     std::unique_ptr<MutableFlatbuffer>>
      children_;

  // Cached repeated fields.
  std::unordered_map<const reflection::Field*, std::unique_ptr<RepeatedField>>
      repeated_fields_;
};

// A helper class to build flatbuffers based on schema reflection data.
// Can be used to a `MutableFlatbuffer` for the root message of the
// schema, or any defined table via name.
class MutableFlatbufferBuilder {
 public:
  explicit MutableFlatbufferBuilder(const reflection::Schema* schema)
      : schema_(schema), root_type_(schema->root_table()) {}
  explicit MutableFlatbufferBuilder(const reflection::Schema* schema,
                                    StringPiece root_type);

  // Starts a new root table message.
  std::unique_ptr<MutableFlatbuffer> NewRoot() const;

  // Creates a new table message. Returns nullptr if no table with given name is
  // found in the schema.
  std::unique_ptr<MutableFlatbuffer> NewTable(
      const StringPiece table_name) const;

  // Creates a new message for the given type id. Returns nullptr if the type is
  // invalid.
  std::unique_ptr<MutableFlatbuffer> NewTable(int type_id) const;

  // Creates a new message for the given type.
  std::unique_ptr<MutableFlatbuffer> NewTable(
      const reflection::Object* type) const;

 private:
  const reflection::Schema* const schema_;
  const reflection::Object* const root_type_;
};

// Encapsulates a repeated field.
// Serves as a common base class for repeated fields.
class RepeatedField {
 public:
  RepeatedField(const reflection::Schema* const schema,
                const reflection::Field* field)
      : schema_(schema),
        field_(field),
        is_primitive_(field->type()->element() != reflection::BaseType::Obj) {}

  template <typename T>
  bool Add(const T value);

  MutableFlatbuffer* Add();

  template <typename T>
  T Get(int index) const {
    return items_.at(index).Value<T>();
  }

  template <>
  MutableFlatbuffer* Get(int index) const {
    if (is_primitive_) {
      TC3_LOG(ERROR) << "Trying to get primitive value out of non-primitive "
                        "repeated field.";
      return nullptr;
    }
    return object_items_.at(index).get();
  }

  int Size() const {
    if (is_primitive_) {
      return items_.size();
    } else {
      return object_items_.size();
    }
  }

  bool Extend(const flatbuffers::Table* from);

  flatbuffers::uoffset_t Serialize(
      flatbuffers::FlatBufferBuilder* builder) const;

  std::string ToTextProto() const;

 private:
  template <typename T>
  bool AppendFromVector(const flatbuffers::Table* from);

  flatbuffers::uoffset_t SerializeString(
      flatbuffers::FlatBufferBuilder* builder) const;
  flatbuffers::uoffset_t SerializeObject(
      flatbuffers::FlatBufferBuilder* builder) const;

  const reflection::Schema* const schema_;
  const reflection::Field* field_;
  bool is_primitive_;

  std::vector<Variant> items_;
  std::vector<std::unique_ptr<MutableFlatbuffer>> object_items_;
};

template <typename T>
bool MutableFlatbuffer::Set(StringPiece field_name, T value) {
  if (const reflection::Field* field = GetFieldOrNull(field_name)) {
    if (field->type()->base_type() == reflection::BaseType::Vector ||
        field->type()->base_type() == reflection::BaseType::Obj) {
      TC3_LOG(ERROR)
          << "Trying to set a primitive value on a non-scalar field.";
      return false;
    }
    return Set<T>(field, value);
  }
  TC3_LOG(ERROR) << "Couldn't find a field: " << field_name;
  return false;
}

template <typename T>
bool MutableFlatbuffer::Set(const reflection::Field* field, T value) {
  if (field == nullptr) {
    TC3_LOG(ERROR) << "Expected non-null field.";
    return false;
  }
  Variant variant_value(value);
  if (!IsMatchingType<T>(field->type()->base_type())) {
    TC3_LOG(ERROR) << "Type mismatch for field `" << field->name()->str()
                   << "`, expected: "
                   << EnumNameBaseType(field->type()->base_type())
                   << ", got: " << variant_value.GetType();
    return false;
  }
  fields_[field] = variant_value;
  return true;
}

template <typename T>
bool MutableFlatbuffer::Set(const FlatbufferFieldPath* path, T value) {
  MutableFlatbuffer* parent;
  const reflection::Field* field;
  if (!GetFieldWithParent(path, &parent, &field)) {
    return false;
  }
  return parent->Set<T>(field, value);
}

template <typename T>
bool MutableFlatbuffer::Add(StringPiece field_name, T value) {
  const reflection::Field* field = GetFieldOrNull(field_name);
  if (field == nullptr) {
    return false;
  }

  if (field->type()->base_type() != reflection::BaseType::Vector) {
    return false;
  }

  return Add<T>(field, value);
}

template <typename T>
bool MutableFlatbuffer::Add(const reflection::Field* field, T value) {
  if (field == nullptr) {
    return false;
  }
  Repeated(field)->Add(value);
  return true;
}

template <typename T>
bool RepeatedField::Add(const T value) {
  if (!is_primitive_ || !IsMatchingType<T>(field_->type()->element())) {
    TC3_LOG(ERROR) << "Trying to add value of unmatching type.";
    return false;
  }
  items_.push_back(Variant{value});
  return true;
}

template <typename T>
bool RepeatedField::AppendFromVector(const flatbuffers::Table* from) {
  const flatbuffers::Vector<T>* values =
      from->GetPointer<const flatbuffers::Vector<T>*>(field_->offset());
  if (values == nullptr) {
    return false;
  }
  for (const T element : *values) {
    Add(element);
  }
  return true;
}

template <>
inline bool RepeatedField::AppendFromVector<std::string>(
    const flatbuffers::Table* from) {
  auto* values = from->GetPointer<
      const flatbuffers::Vector<flatbuffers::Offset<flatbuffers::String>>*>(
      field_->offset());
  if (values == nullptr) {
    return false;
  }
  for (const flatbuffers::String* element : *values) {
    Add(element->str());
  }
  return true;
}

template <>
inline bool RepeatedField::AppendFromVector<MutableFlatbuffer>(
    const flatbuffers::Table* from) {
  auto* values = from->GetPointer<const flatbuffers::Vector<
      flatbuffers::Offset<const flatbuffers::Table>>*>(field_->offset());
  if (values == nullptr) {
    return false;
  }
  for (const flatbuffers::Table* const from_element : *values) {
    MutableFlatbuffer* to_element = Add();
    if (to_element == nullptr) {
      return false;
    }
    to_element->MergeFrom(from_element);
  }
  return true;
}

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_MUTABLE_H_
