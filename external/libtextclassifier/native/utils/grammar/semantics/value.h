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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_VALUE_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_VALUE_H_

#include "utils/base/arena.h"
#include "utils/base/logging.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/flatbuffers/reflection.h"
#include "utils/strings/stringpiece.h"
#include "utils/utf8/unicodetext.h"
#include "flatbuffers/base.h"
#include "flatbuffers/reflection.h"

namespace libtextclassifier3::grammar {

// A semantic value as a typed, arena-allocated flatbuffer.
// This denotes the possible results of the evaluation of a semantic expression.
class SemanticValue {
 public:
  // Creates an arena allocated semantic value.
  template <typename T>
  static const SemanticValue* Create(const T value, UnsafeArena* arena) {
    static_assert(!std::is_pointer<T>() && std::is_scalar<T>());
    if (char* buffer = reinterpret_cast<char*>(
            arena->AllocAligned(sizeof(T), alignof(T)))) {
      flatbuffers::WriteScalar<T>(buffer, value);
      return arena->AllocAndInit<SemanticValue>(
          libtextclassifier3::flatbuffers_base_type<T>::value,
          StringPiece(buffer, sizeof(T)));
    }
    return nullptr;
  }

  template <>
  const SemanticValue* Create(const StringPiece value, UnsafeArena* arena) {
    return arena->AllocAndInit<SemanticValue>(reflection::BaseType::String,
                                              value);
  }

  template <>
  const SemanticValue* Create(const UnicodeText value, UnsafeArena* arena) {
    return arena->AllocAndInit<SemanticValue>(
        reflection::BaseType::String,
        StringPiece(value.data(), value.size_bytes()));
  }

  template <>
  const SemanticValue* Create(const MutableFlatbuffer* value,
                              UnsafeArena* arena) {
    const std::string buffer = value->Serialize();
    return Create(
        value->type(),
        StringPiece(arena->Memdup(buffer.data(), buffer.size()), buffer.size()),
        arena);
  }

  static const SemanticValue* Create(const reflection::Object* type,
                                     const StringPiece data,
                                     UnsafeArena* arena) {
    return arena->AllocAndInit<SemanticValue>(type, data);
  }

  static const SemanticValue* Create(const reflection::BaseType base_type,
                                     const StringPiece data,
                                     UnsafeArena* arena) {
    return arena->AllocAndInit<SemanticValue>(base_type, data);
  }

  template <typename T>
  static const SemanticValue* Create(const reflection::BaseType base_type,
                                     const T value, UnsafeArena* arena) {
    switch (base_type) {
      case reflection::BaseType::Bool:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Bool>::value>(value),
            arena);
      case reflection::BaseType::Byte:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Byte>::value>(value),
            arena);
      case reflection::BaseType::UByte:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::UByte>::value>(
                value),
            arena);
      case reflection::BaseType::Short:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Short>::value>(
                value),
            arena);
      case reflection::BaseType::UShort:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::UShort>::value>(
                value),
            arena);
      case reflection::BaseType::Int:
        return Create(
            static_cast<flatbuffers_cpp_type<reflection::BaseType::Int>::value>(
                value),
            arena);
      case reflection::BaseType::UInt:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::UInt>::value>(value),
            arena);
      case reflection::BaseType::Long:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Long>::value>(value),
            arena);
      case reflection::BaseType::ULong:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::ULong>::value>(
                value),
            arena);
      case reflection::BaseType::Float:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Float>::value>(
                value),
            arena);
      case reflection::BaseType::Double:
        return Create(
            static_cast<
                flatbuffers_cpp_type<reflection::BaseType::Double>::value>(
                value),
            arena);
      default: {
        TC3_LOG(ERROR) << "Unhandled type: " << base_type;
        return nullptr;
      }
    }
  }

  explicit SemanticValue(const reflection::BaseType base_type,
                         const StringPiece data)
      : base_type_(base_type), type_(nullptr), data_(data) {}
  explicit SemanticValue(const reflection::Object* type, const StringPiece data)
      : base_type_(reflection::BaseType::Obj), type_(type), data_(data) {}

  template <typename T>
  bool Has() const {
    return base_type_ == libtextclassifier3::flatbuffers_base_type<T>::value;
  }

  template <>
  bool Has<flatbuffers::Table>() const {
    return base_type_ == reflection::BaseType::Obj;
  }

  template <typename T = flatbuffers::Table>
  const T* Table() const {
    TC3_CHECK(Has<flatbuffers::Table>());
    return flatbuffers::GetRoot<T>(
        reinterpret_cast<const unsigned char*>(data_.data()));
  }

  template <typename T>
  const T Value() const {
    TC3_CHECK(Has<T>());
    return flatbuffers::ReadScalar<T>(data_.data());
  }

  template <>
  const StringPiece Value<StringPiece>() const {
    TC3_CHECK(Has<StringPiece>());
    return data_;
  }

  template <>
  const std::string Value<std::string>() const {
    TC3_CHECK(Has<StringPiece>());
    return data_.ToString();
  }

  template <>
  const UnicodeText Value<UnicodeText>() const {
    TC3_CHECK(Has<StringPiece>());
    return UTF8ToUnicodeText(data_, /*do_copy=*/false);
  }

  const reflection::BaseType base_type() const { return base_type_; }
  const reflection::Object* type() const { return type_; }

 private:
  // The base type.
  const reflection::BaseType base_type_;

  // The object type of the value.
  const reflection::Object* type_;

  StringPiece data_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_SEMANTICS_VALUE_H_
