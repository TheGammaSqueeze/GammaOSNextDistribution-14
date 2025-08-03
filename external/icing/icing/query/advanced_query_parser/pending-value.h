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
#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_PENDING_VALUE_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_PENDING_VALUE_H_

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

enum class DataType {
  kNone,
  kLong,
  kText,
  kString,
  kStringList,
  kDocumentIterator,
};

struct QueryTerm {
  std::string term;
  std::string_view raw_term;
  bool is_prefix_val;
};

// A holder for intermediate results when processing child nodes.
struct PendingValue {
  static PendingValue CreateStringPendingValue(QueryTerm str) {
    return PendingValue(std::move(str), DataType::kString);
  }

  static PendingValue CreateTextPendingValue(QueryTerm text) {
    return PendingValue(std::move(text), DataType::kText);
  }

  PendingValue() : data_type_(DataType::kNone) {}

  explicit PendingValue(std::unique_ptr<DocHitInfoIterator> iterator)
      : iterator_(std::move(iterator)),
        data_type_(DataType::kDocumentIterator) {}

  explicit PendingValue(std::vector<std::string> string_lists)
      : string_vals_(std::move(string_lists)),
        data_type_(DataType::kStringList) {}

  PendingValue(const PendingValue&) = delete;
  PendingValue(PendingValue&&) = default;

  PendingValue& operator=(const PendingValue&) = delete;
  PendingValue& operator=(PendingValue&&) = default;

  // Placeholder is used to indicate where the children of a particular node
  // begin.
  bool is_placeholder() const { return data_type_ == DataType::kNone; }

  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
  iterator() && {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kDocumentIterator));
    return std::move(iterator_);
  }

  libtextclassifier3::StatusOr<const std::vector<std::string>*> string_vals()
      const& {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kStringList));
    return &string_vals_;
  }
  libtextclassifier3::StatusOr<std::vector<std::string>> string_vals() && {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kStringList));
    return std::move(string_vals_);
  }

  libtextclassifier3::StatusOr<const QueryTerm*> string_val() const& {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kString));
    return &query_term_;
  }
  libtextclassifier3::StatusOr<QueryTerm> string_val() && {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kString));
    return std::move(query_term_);
  }

  libtextclassifier3::StatusOr<const QueryTerm*> text_val() const& {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kText));
    return &query_term_;
  }
  libtextclassifier3::StatusOr<QueryTerm> text_val() && {
    ICING_RETURN_IF_ERROR(CheckDataType(DataType::kText));
    return std::move(query_term_);
  }

  libtextclassifier3::StatusOr<int64_t> long_val() {
    ICING_RETURN_IF_ERROR(ParseInt());
    return long_val_;
  }

  // Attempts to interpret the value as an int. A pending value can be parsed as
  // an int under two circumstances:
  //   1. It holds a kText value which can be parsed to an int
  //   2. It holds a kLong value
  // If #1 is true, then the parsed value will be stored in long_value and
  // data_type will be updated to kLong.
  // RETURNS:
  //   - OK, if able to successfully parse the value into a long
  //   - INVALID_ARGUMENT if the value could not be parsed as a long
  libtextclassifier3::Status ParseInt();

  DataType data_type() const { return data_type_; }

 private:
  explicit PendingValue(QueryTerm query_term, DataType data_type)
      : query_term_(std::move(query_term)), data_type_(data_type) {}

  libtextclassifier3::Status CheckDataType(DataType required_data_type) const {
    if (data_type_ == required_data_type) {
      return libtextclassifier3::Status::OK;
    }
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Unable to retrieve value of type '",
                           std::to_string(static_cast<int>(required_data_type)),
                           "' from pending value of type '",
                           std::to_string(static_cast<int>(data_type_)), "'"));
  }

  // iterator_ will be populated when data_type_ is kDocumentIterator.
  std::unique_ptr<DocHitInfoIterator> iterator_;

  // string_vals_ will be populated when data_type_ kStringList.
  std::vector<std::string> string_vals_;

  // query_term_ will be populated when data_type_ is kString or kText
  QueryTerm query_term_;

  // long_val_ will be populated when data_type_ is kLong - after a successful
  // call to ParseInt.
  int64_t long_val_;
  DataType data_type_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_PENDING_VALUE_H_
