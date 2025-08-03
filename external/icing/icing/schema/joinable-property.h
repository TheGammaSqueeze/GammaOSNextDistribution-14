// Copyright (C) 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_SCHEMA_JOINABLE_PROPERTY_H_
#define ICING_SCHEMA_JOINABLE_PROPERTY_H_

#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/proto/schema.pb.h"

namespace icing {
namespace lib {

using JoinablePropertyId = int8_t;

// 6 bits for 64 values.
inline constexpr int kJoinablePropertyIdBits = 6;
inline constexpr JoinablePropertyId kTotalNumJoinableProperties =
    (INT8_C(1) << kJoinablePropertyIdBits);
inline constexpr JoinablePropertyId kInvalidJoinablePropertyId =
    kTotalNumJoinableProperties;
inline constexpr JoinablePropertyId kMaxJoinablePropertyId =
    kTotalNumJoinableProperties - 1;
inline constexpr JoinablePropertyId kMinJoinablePropertyId = 0;

constexpr bool IsJoinablePropertyIdValid(
    JoinablePropertyId joinable_property_id) {
  return joinable_property_id >= kMinJoinablePropertyId &&
         joinable_property_id <= kMaxJoinablePropertyId;
}

static_assert(
    kJoinablePropertyIdBits < 8 * sizeof(JoinablePropertyId),
    "Cannot exhaust all bits of JoinablePropertyId since it is a signed "
    "integer and the most significant bit should be preserved.");

struct JoinablePropertyMetadata {
  // Dot-joined property names, representing the location of joinable property
  // inside an document. E.g. "property1.property2".
  std::string path;

  // A unique id of joinable property.
  JoinablePropertyId id;

  // Data type of this joinable property values. Currently we only support
  // STRING.
  PropertyConfigProto::DataType::Code data_type;

  // How values will be used as a joining matcher.
  //
  // JoinableConfig::ValueType::QUALIFIED_ID:
  //   Value in this property is a joinable (string) qualified id. Qualified id
  //   is composed of namespace and uri, and it will be used as the identifier
  //   of the parent document. Note: it is invalid to use this value type with
  //   non-string DataType.
  JoinableConfig::ValueType::Code value_type;

  explicit JoinablePropertyMetadata(
      JoinablePropertyId id_in,
      PropertyConfigProto::DataType::Code data_type_in,
      JoinableConfig::ValueType::Code value_type_in, std::string&& path_in)
      : path(std::move(path_in)),
        id(id_in),
        data_type(data_type_in),
        value_type(value_type_in) {}

  JoinablePropertyMetadata(const JoinablePropertyMetadata& other) = default;
  JoinablePropertyMetadata& operator=(const JoinablePropertyMetadata& other) =
      default;

  JoinablePropertyMetadata(JoinablePropertyMetadata&& other) = default;
  JoinablePropertyMetadata& operator=(JoinablePropertyMetadata&& other) =
      default;

  bool operator==(const JoinablePropertyMetadata& rhs) const {
    return path == rhs.path && id == rhs.id && data_type == rhs.data_type &&
           value_type == rhs.value_type;
  }
};

// JoinableProperty is an icing internal concept similar to document property
// values (contents), but with extra metadata. the data type of value is
// specified by template.
//
// Current supported data types:
// - std::string_view (PropertyConfigProto::DataType::STRING)
template <typename T>
struct JoinableProperty {
  JoinablePropertyMetadata metadata;
  std::vector<T> values;

  explicit JoinableProperty(JoinablePropertyMetadata&& metadata_in,
                            std::vector<T>&& values_in)
      : metadata(std::move(metadata_in)), values(std::move(values_in)) {}

  PropertyConfigProto::DataType::Code data_type() const {
    return metadata.data_type;
  }

  JoinableConfig::ValueType::Code value_type() const {
    return metadata.value_type;
  }
};

// Groups of different type joinable properties. Callers can access joinable
// properties with types they want and avoid going through non-desired ones.
//
// REQUIRES: lifecycle of the property must be longer than this object, since we
//   use std::string_view for extracting its string_values.
struct JoinablePropertyGroup {
  std::vector<JoinableProperty<std::string_view>> qualified_id_properties;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_JOINABLE_PROPERTY_H_
