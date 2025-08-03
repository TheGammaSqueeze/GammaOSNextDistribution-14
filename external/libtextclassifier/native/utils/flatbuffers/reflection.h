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

// Utility functions for working with FlatBuffers.

#ifndef LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_REFLECTION_H_
#define LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_REFLECTION_H_

#include "utils/flatbuffers/flatbuffers_generated.h"
#include "utils/optional.h"
#include "utils/strings/stringpiece.h"
#include "utils/variant.h"
#include "flatbuffers/reflection.h"
#include "flatbuffers/reflection_generated.h"

namespace libtextclassifier3 {

template <typename T>
struct flatbuffers_base_type {
  static const reflection::BaseType value;
};

template <typename T>
inline const reflection::BaseType flatbuffers_base_type<T>::value =
    reflection::None;

template <>
inline const reflection::BaseType flatbuffers_base_type<bool>::value =
    reflection::Bool;

template <>
inline const reflection::BaseType flatbuffers_base_type<int8>::value =
    reflection::Byte;

template <>
inline const reflection::BaseType flatbuffers_base_type<uint8>::value =
    reflection::UByte;

template <>
inline const reflection::BaseType flatbuffers_base_type<int16>::value =
    reflection::Short;

template <>
inline const reflection::BaseType flatbuffers_base_type<uint16>::value =
    reflection::UShort;

template <>
inline const reflection::BaseType flatbuffers_base_type<int32>::value =
    reflection::Int;

template <>
inline const reflection::BaseType flatbuffers_base_type<uint32>::value =
    reflection::UInt;

template <>
inline const reflection::BaseType flatbuffers_base_type<int64>::value =
    reflection::Long;

template <>
inline const reflection::BaseType flatbuffers_base_type<uint64>::value =
    reflection::ULong;

template <>
inline const reflection::BaseType flatbuffers_base_type<float>::value =
    reflection::Float;

template <>
inline const reflection::BaseType flatbuffers_base_type<double>::value =
    reflection::Double;

template <>
inline const reflection::BaseType flatbuffers_base_type<StringPiece>::value =
    reflection::String;

template <reflection::BaseType>
struct flatbuffers_cpp_type;

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Bool> {
  using value = bool;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Byte> {
  using value = int8;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::UByte> {
  using value = uint8;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Short> {
  using value = int16;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::UShort> {
  using value = uint16;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Int> {
  using value = int32;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::UInt> {
  using value = uint32;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Long> {
  using value = int64;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::ULong> {
  using value = uint64;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Float> {
  using value = float;
};

template <>
struct flatbuffers_cpp_type<reflection::BaseType::Double> {
  using value = double;
};

// Gets the field information for a field name, returns nullptr if the
// field was not defined.
const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const StringPiece field_name);

// Gets the field information for a field offet, returns nullptr if no field was
// defined with the given offset.
const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const int field_offset);

// Gets a field by name or offset, returns nullptr if no field was found.
const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const StringPiece field_name,
                                        const int field_offset);

// Gets a field by a field spec, either by name or offset. Returns nullptr if no
// such field was found.
const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const FlatbufferField* field);

const reflection::Field* GetFieldOrNull(const reflection::Object* type,
                                        const FlatbufferFieldT* field);

// Gets the type information for the given type name or nullptr if not
// specified.
const reflection::Object* TypeForName(const reflection::Schema* schema,
                                      const StringPiece type_name);

// Gets the type id for a type name.
Optional<int> TypeIdForName(const reflection::Schema* schema,
                            const StringPiece type_name);

// Gets the type id for a type.
Optional<int> TypeIdForObject(const reflection::Schema* schema,
                              const reflection::Object* type);

// Resolves field lookups by name to the concrete field offsets.
bool SwapFieldNamesForOffsetsInPath(const reflection::Schema* schema,
                                    FlatbufferFieldPathT* path);

// Checks whether a type denotes an enum.
inline bool IsEnum(const reflection::Type* type) {
  return flatbuffers::IsInteger(type->base_type()) && type->index() >= 0;
}

// Parses an enum value.
Variant ParseEnumValue(const reflection::Schema* schema,
                       const reflection::Type* type, StringPiece value);

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_REFLECTION_H_
