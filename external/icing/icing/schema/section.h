// Copyright (C) 2019 Google LLC
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

#ifndef ICING_SCHEMA_SECTION_H_
#define ICING_SCHEMA_SECTION_H_

#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"

namespace icing {
namespace lib {

using SectionId = int8_t;
// 6 bits for 64 values.
inline constexpr int kSectionIdBits = 6;
inline constexpr SectionId kTotalNumSections = (1 << kSectionIdBits);
inline constexpr SectionId kInvalidSectionId = kTotalNumSections;
inline constexpr SectionId kMaxSectionId = kTotalNumSections - 1;
// Prior versions of Icing only supported 16 indexed properties.
inline constexpr SectionId kOldTotalNumSections = 16;
inline constexpr SectionId kMinSectionId = 0;
constexpr bool IsSectionIdValid(SectionId section_id) {
  return section_id >= kMinSectionId && section_id <= kMaxSectionId;
}

using SectionIdMask = int64_t;
inline constexpr SectionIdMask kSectionIdMaskAll = ~SectionIdMask{0};
inline constexpr SectionIdMask kSectionIdMaskNone = SectionIdMask{0};

static_assert(kSectionIdBits < 8 * sizeof(SectionId),
              "Cannot exhaust all bits of SectionId since it is a signed "
              "integer and the most significant bit should be preserved.");

static_assert(
    kMaxSectionId < 8 * sizeof(SectionIdMask),
    "SectionIdMask is not large enough to represent all section values!");

struct SectionMetadata {
  // Dot-joined property names, representing the location of section inside an
  // document. E.g. "property1.property2"
  std::string path;

  // A unique id of property within a type config
  SectionId id;

  // Indexable data type of this section. E.g. STRING, INT64.
  PropertyConfigProto::DataType::Code data_type;

  // How strings should be tokenized. It is invalid for a string section
  // (data_type == 'STRING') to have tokenizer == 'NONE'.
  StringIndexingConfig::TokenizerType::Code tokenizer;

  // How tokens in a string section should be matched.
  //
  // TermMatchType::UNKNOWN:
  //   Terms will not match anything
  //
  // TermMatchType::PREFIX:
  //   Terms will be stored as a prefix match, "fool" matches "foo" and "fool"
  //
  // TermMatchType::EXACT_ONLY:
  //   Terms will be only stored as an exact match, "fool" only matches "fool"
  TermMatchType::Code term_match_type = TermMatchType::UNKNOWN;

  // How tokens in a numeric section should be matched.
  //
  // NumericMatchType::UNKNOWN:
  //   Contents will not match anything. It is invalid for a numeric section
  //   (data_type == 'INT64') to have numeric_match_type == 'UNKNOWN'.
  //
  // NumericMatchType::RANGE:
  //   Contents will be matched by a range query.
  IntegerIndexingConfig::NumericMatchType::Code numeric_match_type;

  explicit SectionMetadata(
      SectionId id_in, PropertyConfigProto::DataType::Code data_type_in,
      StringIndexingConfig::TokenizerType::Code tokenizer,
      TermMatchType::Code term_match_type_in,
      IntegerIndexingConfig::NumericMatchType::Code numeric_match_type_in,
      std::string&& path_in)
      : path(std::move(path_in)),
        id(id_in),
        data_type(data_type_in),
        tokenizer(tokenizer),
        term_match_type(term_match_type_in),
        numeric_match_type(numeric_match_type_in) {}

  SectionMetadata(const SectionMetadata& other) = default;
  SectionMetadata& operator=(const SectionMetadata& other) = default;

  SectionMetadata(SectionMetadata&& other) = default;
  SectionMetadata& operator=(SectionMetadata&& other) = default;

  bool operator==(const SectionMetadata& rhs) const {
    return path == rhs.path && id == rhs.id && data_type == rhs.data_type &&
           tokenizer == rhs.tokenizer &&
           term_match_type == rhs.term_match_type &&
           numeric_match_type == rhs.numeric_match_type;
  }
};

// Section is an icing internal concept similar to document property but with
// extra metadata. The content can be a value or the combination of repeated
// values of a property, and the type of content is specified by template.
//
// Current supported types:
// - std::string_view (PropertyConfigProto::DataType::STRING)
// - int64_t (PropertyConfigProto::DataType::INT64)
template <typename T>
struct Section {
  SectionMetadata metadata;
  std::vector<T> content;

  explicit Section(SectionMetadata&& metadata_in, std::vector<T>&& content_in)
      : metadata(std::move(metadata_in)), content(std::move(content_in)) {}

  PropertyConfigProto::DataType::Code data_type() const {
    return metadata.data_type;
  }
};

// Groups of different type sections. Callers can access sections with types
// they want and avoid going through non-desired ones.
//
// REQUIRES: lifecycle of the property must be longer than this object, since we
//   use std::string_view for extracting its string_values.
struct SectionGroup {
  std::vector<Section<std::string_view>> string_sections;
  std::vector<Section<int64_t>> integer_sections;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_SECTION_H_
