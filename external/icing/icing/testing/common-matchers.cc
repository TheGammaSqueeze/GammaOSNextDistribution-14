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
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

ExtractTermFrequenciesResult ExtractTermFrequencies(
    const std::unordered_map<SectionId, Hit::TermFrequency>&
        section_ids_tf_map) {
  ExtractTermFrequenciesResult result;
  for (const auto& [section_id, tf] : section_ids_tf_map) {
    result.term_frequencies[section_id] = tf;
    result.section_mask |= UINT64_C(1) << section_id;
  }
  return result;
}

CheckTermFrequencyResult CheckTermFrequency(
    const std::array<Hit::TermFrequency, kTotalNumSections>&
        expected_term_frequencies,
    const std::array<Hit::TermFrequency, kTotalNumSections>&
        actual_term_frequencies) {
  CheckTermFrequencyResult result;
  for (SectionId section_id = 0; section_id < kTotalNumSections; ++section_id) {
    if (expected_term_frequencies.at(section_id) !=
        actual_term_frequencies.at(section_id)) {
      result.term_frequencies_match = false;
    }
  }
  result.actual_term_frequencies_str =
      absl_ports::StrCat("[",
                         absl_ports::StrJoin(actual_term_frequencies, ",",
                                             absl_ports::NumberFormatter()),
                         "]");
  result.expected_term_frequencies_str =
      absl_ports::StrCat("[",
                         absl_ports::StrJoin(expected_term_frequencies, ",",
                                             absl_ports::NumberFormatter()),
                         "]");
  return result;
}

std::string StatusCodeToString(libtextclassifier3::StatusCode code) {
  switch (code) {
    case libtextclassifier3::StatusCode::OK:
      return "OK";
    case libtextclassifier3::StatusCode::CANCELLED:
      return "CANCELLED";
    case libtextclassifier3::StatusCode::UNKNOWN:
      return "UNKNOWN";
    case libtextclassifier3::StatusCode::INVALID_ARGUMENT:
      return "INVALID_ARGUMENT";
    case libtextclassifier3::StatusCode::DEADLINE_EXCEEDED:
      return "DEADLINE_EXCEEDED";
    case libtextclassifier3::StatusCode::NOT_FOUND:
      return "NOT_FOUND";
    case libtextclassifier3::StatusCode::ALREADY_EXISTS:
      return "ALREADY_EXISTS";
    case libtextclassifier3::StatusCode::PERMISSION_DENIED:
      return "PERMISSION_DENIED";
    case libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED:
      return "RESOURCE_EXHAUSTED";
    case libtextclassifier3::StatusCode::FAILED_PRECONDITION:
      return "FAILED_PRECONDITION";
    case libtextclassifier3::StatusCode::ABORTED:
      return "ABORTED";
    case libtextclassifier3::StatusCode::OUT_OF_RANGE:
      return "OUT_OF_RANGE";
    case libtextclassifier3::StatusCode::UNIMPLEMENTED:
      return "UNIMPLEMENTED";
    case libtextclassifier3::StatusCode::INTERNAL:
      return "INTERNAL";
    case libtextclassifier3::StatusCode::UNAVAILABLE:
      return "UNAVAILABLE";
    case libtextclassifier3::StatusCode::DATA_LOSS:
      return "DATA_LOSS";
    case libtextclassifier3::StatusCode::UNAUTHENTICATED:
      return "UNAUTHENTICATED";
    default:
      return "";
  }
}

std::string ProtoStatusCodeToString(StatusProto::Code code) {
  switch (code) {
    case StatusProto::OK:
      return "OK";
    case StatusProto::UNKNOWN:
      return "UNKNOWN";
    case StatusProto::INVALID_ARGUMENT:
      return "INVALID_ARGUMENT";
    case StatusProto::NOT_FOUND:
      return "NOT_FOUND";
    case StatusProto::ALREADY_EXISTS:
      return "ALREADY_EXISTS";
    case StatusProto::OUT_OF_SPACE:
      return "OUT_OF_SPACE";
    case StatusProto::FAILED_PRECONDITION:
      return "FAILED_PRECONDITION";
    case StatusProto::ABORTED:
      return "ABORTED";
    case StatusProto::INTERNAL:
      return "INTERNAL";
    case StatusProto::WARNING_DATA_LOSS:
      return "WARNING_DATA_LOSS";
    default:
      return "";
  }
}

}  // namespace lib
}  // namespace icing
