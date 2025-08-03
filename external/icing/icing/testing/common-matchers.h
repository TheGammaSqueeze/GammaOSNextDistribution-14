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

#ifndef ICING_TESTING_COMMON_MATCHERS_H_
#define ICING_TESTING_COMMON_MATCHERS_H_

#include <algorithm>
#include <cinttypes>
#include <cmath>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/status_macros.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_join.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/portable/equals-proto.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

// Used to match Token(Token::Type type, std::string_view text)
MATCHER_P2(EqualsToken, type, text, "") {
  std::string arg_string(arg.text.data(), arg.text.length());
  if (arg.type != type || arg.text != text) {
    *result_listener << IcingStringUtil::StringPrintf(
        "(Expected: type=%d, text=\"%s\". Actual: type=%d, text=\"%s\")", type,
        text, arg.type, arg_string.c_str());
    return false;
  }
  return true;
}

// Used to match a DocHitInfo
MATCHER_P2(EqualsDocHitInfo, document_id, section_ids, "") {
  const DocHitInfo& actual = arg;
  SectionIdMask section_mask = kSectionIdMaskNone;
  for (SectionId section_id : section_ids) {
    section_mask |= UINT64_C(1) << section_id;
  }
  *result_listener << IcingStringUtil::StringPrintf(
      "(actual is {document_id=%d, section_mask=%" PRIu64
      "}, but expected was "
      "{document_id=%d, section_mask=%" PRIu64 "}.)",
      actual.document_id(), actual.hit_section_ids_mask(), document_id,
      section_mask);
  return actual.document_id() == document_id &&
         actual.hit_section_ids_mask() == section_mask;
}

// Used to match a DocHitInfoIterator::CallStats
MATCHER_P5(EqualsDocHitInfoIteratorCallStats, num_leaf_advance_calls_lite_index,
           num_leaf_advance_calls_main_index,
           num_leaf_advance_calls_integer_index,
           num_leaf_advance_calls_no_index, num_blocks_inspected, "") {
  const DocHitInfoIterator::CallStats& actual = arg;
  *result_listener << IcingStringUtil::StringPrintf(
      "(actual is {num_leaf_advance_calls_lite_index=%d, "
      "num_leaf_advance_calls_main_index=%d, "
      "num_leaf_advance_calls_integer_index=%d, "
      "num_leaf_advance_calls_no_index=%d, num_blocks_inspected=%d}, but "
      "expected was {num_leaf_advance_calls_lite_index=%d, "
      "num_leaf_advance_calls_main_index=%d, "
      "num_leaf_advance_calls_integer_index=%d, "
      "num_leaf_advance_calls_no_index=%d, num_blocks_inspected=%d}.)",
      actual.num_leaf_advance_calls_lite_index,
      actual.num_leaf_advance_calls_main_index,
      actual.num_leaf_advance_calls_integer_index,
      actual.num_leaf_advance_calls_no_index, actual.num_blocks_inspected,
      num_leaf_advance_calls_lite_index, num_leaf_advance_calls_main_index,
      num_leaf_advance_calls_integer_index, num_leaf_advance_calls_no_index,
      num_blocks_inspected);
  return actual.num_leaf_advance_calls_lite_index ==
             num_leaf_advance_calls_lite_index &&
         actual.num_leaf_advance_calls_main_index ==
             num_leaf_advance_calls_main_index &&
         actual.num_leaf_advance_calls_integer_index ==
             num_leaf_advance_calls_integer_index &&
         actual.num_leaf_advance_calls_no_index ==
             num_leaf_advance_calls_no_index &&
         actual.num_blocks_inspected == num_blocks_inspected;
}

struct ExtractTermFrequenciesResult {
  std::array<Hit::TermFrequency, kTotalNumSections> term_frequencies = {0};
  SectionIdMask section_mask = kSectionIdMaskNone;
};
// Extracts the term frequencies represented by the section_ids_tf_map.
// Returns:
//   - a SectionIdMask representing all sections that appears as entries in the
//     map, even if they have an entry with term_frequency==0
//   - an array representing the term frequencies for each section. Sections not
//     present in section_ids_tf_map have a term frequency of 0.
ExtractTermFrequenciesResult ExtractTermFrequencies(
    const std::unordered_map<SectionId, Hit::TermFrequency>&
        section_ids_tf_map);

struct CheckTermFrequencyResult {
  std::string expected_term_frequencies_str;
  std::string actual_term_frequencies_str;
  bool term_frequencies_match = true;
};
// Checks that the term frequencies in actual_term_frequencies match those
// specified in expected_section_ids_tf_map. If there is no entry in
// expected_section_ids_tf_map, then it is assumed that the term frequency for
// that section is 0.
// Returns:
//   - a bool indicating if the term frequencies match
//   - debug strings representing the contents of the actual and expected term
//     term frequency arrays.
CheckTermFrequencyResult CheckTermFrequency(
    const std::array<Hit::TermFrequency, kTotalNumSections>&
        expected_term_frequencies,
    const std::array<Hit::TermFrequency, kTotalNumSections>&
        actual_term_frequencies);

// Used to match a DocHitInfo
MATCHER_P2(EqualsDocHitInfoWithTermFrequency, document_id,
           section_ids_to_term_frequencies_map, "") {
  const DocHitInfoTermFrequencyPair& actual = arg;
  std::array<Hit::TermFrequency, kTotalNumSections> actual_tf_array;
  for (SectionId section_id = 0; section_id < kTotalNumSections; ++section_id) {
    actual_tf_array[section_id] = actual.hit_term_frequency(section_id);
  }
  ExtractTermFrequenciesResult expected =
      ExtractTermFrequencies(section_ids_to_term_frequencies_map);
  CheckTermFrequencyResult check_tf_result =
      CheckTermFrequency(expected.term_frequencies, actual_tf_array);

  *result_listener << IcingStringUtil::StringPrintf(
      "(actual is {document_id=%d, section_mask=%" PRIu64
      ", term_frequencies=%s}, but expected was "
      "{document_id=%d, section_mask=%" PRIu64 ", term_frequencies=%s}.)",
      actual.doc_hit_info().document_id(),
      actual.doc_hit_info().hit_section_ids_mask(),
      check_tf_result.actual_term_frequencies_str.c_str(), document_id,
      expected.section_mask,
      check_tf_result.expected_term_frequencies_str.c_str());
  return actual.doc_hit_info().document_id() == document_id &&
         actual.doc_hit_info().hit_section_ids_mask() ==
             expected.section_mask &&
         check_tf_result.term_frequencies_match;
}

MATCHER_P2(EqualsTermMatchInfo, term, section_ids_to_term_frequencies_map, "") {
  const TermMatchInfo& actual = arg;
  std::string term_str(term);
  ExtractTermFrequenciesResult expected =
      ExtractTermFrequencies(section_ids_to_term_frequencies_map);
  CheckTermFrequencyResult check_tf_result =
      CheckTermFrequency(expected.term_frequencies, actual.term_frequencies);
  *result_listener << IcingStringUtil::StringPrintf(
      "(actual is {term=%s, section_mask=%" PRIu64
      ", term_frequencies=%s}, but expected was "
      "{term=%s, section_mask=%" PRIu64 ", term_frequencies=%s}.)",
      actual.term.data(), actual.section_ids_mask,
      check_tf_result.actual_term_frequencies_str.c_str(), term_str.data(),
      expected.section_mask,
      check_tf_result.expected_term_frequencies_str.c_str());
  return actual.term == term &&
         actual.section_ids_mask == expected.section_mask &&
         check_tf_result.term_frequencies_match;
}

class ScoredDocumentHitFormatter {
 public:
  std::string operator()(const ScoredDocumentHit& scored_document_hit) {
    return IcingStringUtil::StringPrintf(
        "(document_id=%d, hit_section_id_mask=%" PRId64 ", score=%.2f)",
        scored_document_hit.document_id(),
        scored_document_hit.hit_section_id_mask(), scored_document_hit.score());
  }
};

class ScoredDocumentHitEqualComparator {
 public:
  bool operator()(const ScoredDocumentHit& lhs,
                  const ScoredDocumentHit& rhs) const {
    return lhs.document_id() == rhs.document_id() &&
           lhs.hit_section_id_mask() == rhs.hit_section_id_mask() &&
           std::fabs(lhs.score() - rhs.score()) < 1e-6;
  }
};

// Used to match a ScoredDocumentHit
MATCHER_P(EqualsScoredDocumentHit, expected_scored_document_hit, "") {
  ScoredDocumentHitEqualComparator equal_comparator;
  if (!equal_comparator(arg, expected_scored_document_hit)) {
    ScoredDocumentHitFormatter formatter;
    *result_listener << "Expected: " << formatter(expected_scored_document_hit)
                     << ". Actual: " << formatter(arg);
    return false;
  }
  return true;
}

// Used to match a JoinedScoredDocumentHit
MATCHER_P(EqualsJoinedScoredDocumentHit, expected_joined_scored_document_hit,
          "") {
  ScoredDocumentHitEqualComparator equal_comparator;
  if (std::fabs(arg.final_score() -
                expected_joined_scored_document_hit.final_score()) > 1e-6 ||
      !equal_comparator(
          arg.parent_scored_document_hit(),
          expected_joined_scored_document_hit.parent_scored_document_hit()) ||
      arg.child_scored_document_hits().size() !=
          expected_joined_scored_document_hit.child_scored_document_hits()
              .size() ||
      !std::equal(
          arg.child_scored_document_hits().cbegin(),
          arg.child_scored_document_hits().cend(),
          expected_joined_scored_document_hit.child_scored_document_hits()
              .cbegin(),
          equal_comparator)) {
    ScoredDocumentHitFormatter formatter;

    *result_listener << IcingStringUtil::StringPrintf(
        "Expected: final_score=%.2f, parent_scored_document_hit=%s, "
        "child_scored_document_hits=[%s]. Actual: final_score=%.2f, "
        "parent_scored_document_hit=%s, child_scored_document_hits=[%s]",
        expected_joined_scored_document_hit.final_score(),
        formatter(
            expected_joined_scored_document_hit.parent_scored_document_hit())
            .c_str(),
        absl_ports::StrJoin(
            expected_joined_scored_document_hit.child_scored_document_hits(),
            ",", formatter)
            .c_str(),
        arg.final_score(), formatter(arg.parent_scored_document_hit()).c_str(),
        absl_ports::StrJoin(arg.child_scored_document_hits(), ",", formatter)
            .c_str());
    return false;
  }
  return true;
}

MATCHER_P(EqualsSetSchemaResult, expected, "") {
  const SchemaStore::SetSchemaResult& actual = arg;

  if (actual.success == expected.success &&
      actual.old_schema_type_ids_changed ==
          expected.old_schema_type_ids_changed &&
      actual.schema_types_deleted_by_name ==
          expected.schema_types_deleted_by_name &&
      actual.schema_types_deleted_by_id ==
          expected.schema_types_deleted_by_id &&
      actual.schema_types_incompatible_by_name ==
          expected.schema_types_incompatible_by_name &&
      actual.schema_types_incompatible_by_id ==
          expected.schema_types_incompatible_by_id &&
      actual.schema_types_new_by_name == expected.schema_types_new_by_name &&
      actual.schema_types_changed_fully_compatible_by_name ==
          expected.schema_types_changed_fully_compatible_by_name &&
      actual.schema_types_index_incompatible_by_name ==
          expected.schema_types_index_incompatible_by_name &&
      actual.schema_types_join_incompatible_by_name ==
          expected.schema_types_join_incompatible_by_name) {
    return true;
  }

  // Format schema_type_ids_changed
  std::string actual_old_schema_type_ids_changed = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(actual.old_schema_type_ids_changed, ",",
                          absl_ports::NumberFormatter()),
      "]");

  std::string expected_old_schema_type_ids_changed = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(expected.old_schema_type_ids_changed, ",",
                          absl_ports::NumberFormatter()),
      "]");

  // Format schema_types_deleted_by_name
  std::string actual_schema_types_deleted_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(actual.schema_types_deleted_by_name, ","), "]");

  std::string expected_schema_types_deleted_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(expected.schema_types_deleted_by_name, ","),
      "]");

  // Format schema_types_deleted_by_id
  std::string actual_schema_types_deleted_by_id = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(actual.schema_types_deleted_by_id, ",",
                          absl_ports::NumberFormatter()),
      "]");

  std::string expected_schema_types_deleted_by_id = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(expected.schema_types_deleted_by_id, ",",
                          absl_ports::NumberFormatter()),
      "]");

  // Format schema_types_incompatible_by_name
  std::string actual_schema_types_incompatible_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(actual.schema_types_incompatible_by_name, ","),
      "]");

  std::string expected_schema_types_incompatible_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(expected.schema_types_incompatible_by_name, ","),
      "]");

  // Format schema_types_incompatible_by_id
  std::string actual_schema_types_incompatible_by_id = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(actual.schema_types_incompatible_by_id, ",",
                          absl_ports::NumberFormatter()),
      "]");

  std::string expected_schema_types_incompatible_by_id = absl_ports::StrCat(
      "[",
      absl_ports::StrJoin(expected.schema_types_incompatible_by_id, ",",
                          absl_ports::NumberFormatter()),
      "]");

  // Format schema_types_new_by_name
  std::string actual_schema_types_new_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(actual.schema_types_new_by_name, ","), "]");

  std::string expected_schema_types_new_by_name = absl_ports::StrCat(
      "[", absl_ports::StrJoin(expected.schema_types_new_by_name, ","), "]");

  // Format schema_types_changed_fully_compatible_by_name
  std::string actual_schema_types_changed_fully_compatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(
              actual.schema_types_changed_fully_compatible_by_name, ","),
          "]");

  std::string expected_schema_types_changed_fully_compatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(
              expected.schema_types_changed_fully_compatible_by_name, ","),
          "]");

  // Format schema_types_deleted_by_id
  std::string actual_schema_types_index_incompatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(actual.schema_types_index_incompatible_by_name,
                              ","),
          "]");

  std::string expected_schema_types_index_incompatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(expected.schema_types_index_incompatible_by_name,
                              ","),
          "]");

  // Format schema_types_join_incompatible_by_name
  std::string actual_schema_types_join_incompatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(actual.schema_types_join_incompatible_by_name,
                              ","),
          "]");

  std::string expected_schema_types_join_incompatible_by_name =
      absl_ports::StrCat(
          "[",
          absl_ports::StrJoin(expected.schema_types_join_incompatible_by_name,
                              ","),
          "]");

  *result_listener << IcingStringUtil::StringPrintf(
      "\nExpected {\n"
      "\tsuccess=%d,\n"
      "\told_schema_type_ids_changed=%s,\n"
      "\tschema_types_deleted_by_name=%s,\n"
      "\tschema_types_deleted_by_id=%s,\n"
      "\tschema_types_incompatible_by_name=%s,\n"
      "\tschema_types_incompatible_by_id=%s\n"
      "\tschema_types_new_by_name=%s,\n"
      "\tschema_types_changed_fully_compatible_by_name=%s\n"
      "\tschema_types_index_incompatible_by_name=%s,\n"
      "\tschema_types_join_incompatible_by_name=%s\n"
      "}\n"
      "Actual {\n"
      "\tsuccess=%d,\n"
      "\told_schema_type_ids_changed=%s,\n"
      "\tschema_types_deleted_by_name=%s,\n"
      "\tschema_types_deleted_by_id=%s,\n"
      "\tschema_types_incompatible_by_name=%s,\n"
      "\tschema_types_incompatible_by_id=%s\n"
      "\tschema_types_new_by_name=%s,\n"
      "\tschema_types_changed_fully_compatible_by_name=%s\n"
      "\tschema_types_index_incompatible_by_name=%s,\n"
      "\tschema_types_join_incompatible_by_name=%s\n"
      "}\n",
      expected.success, expected_old_schema_type_ids_changed.c_str(),
      expected_schema_types_deleted_by_name.c_str(),
      expected_schema_types_deleted_by_id.c_str(),
      expected_schema_types_incompatible_by_name.c_str(),
      expected_schema_types_incompatible_by_id.c_str(),
      expected_schema_types_new_by_name.c_str(),
      expected_schema_types_changed_fully_compatible_by_name.c_str(),
      expected_schema_types_index_incompatible_by_name.c_str(),
      expected_schema_types_join_incompatible_by_name.c_str(), actual.success,
      actual_old_schema_type_ids_changed.c_str(),
      actual_schema_types_deleted_by_name.c_str(),
      actual_schema_types_deleted_by_id.c_str(),
      actual_schema_types_incompatible_by_name.c_str(),
      actual_schema_types_incompatible_by_id.c_str(),
      actual_schema_types_new_by_name.c_str(),
      actual_schema_types_changed_fully_compatible_by_name.c_str(),
      actual_schema_types_index_incompatible_by_name.c_str(),
      actual_schema_types_join_incompatible_by_name.c_str());
  return false;
}

MATCHER_P3(EqualsSectionMetadata, expected_id, expected_property_path,
           expected_property_config_proto, "") {
  const SectionMetadata& actual = arg;
  return actual.id == expected_id && actual.path == expected_property_path &&
         actual.data_type == expected_property_config_proto.data_type() &&
         actual.tokenizer ==
             expected_property_config_proto.string_indexing_config()
                 .tokenizer_type() &&
         actual.term_match_type ==
             expected_property_config_proto.string_indexing_config()
                 .term_match_type() &&
         actual.numeric_match_type ==
             expected_property_config_proto.integer_indexing_config()
                 .numeric_match_type();
}

MATCHER_P3(EqualsJoinablePropertyMetadata, expected_id, expected_property_path,
           expected_property_config_proto, "") {
  const JoinablePropertyMetadata& actual = arg;
  return actual.id == expected_id && actual.path == expected_property_path &&
         actual.data_type == expected_property_config_proto.data_type() &&
         actual.value_type ==
             expected_property_config_proto.joinable_config().value_type();
}

std::string StatusCodeToString(libtextclassifier3::StatusCode code);

std::string ProtoStatusCodeToString(StatusProto::Code code);

MATCHER(IsOk, "") {
  libtextclassifier3::StatusAdapter adapter(arg);
  if (adapter.status().ok()) {
    return true;
  }
  *result_listener << IcingStringUtil::StringPrintf(
      "Expected OK, actual was (%s:%s)",
      StatusCodeToString(adapter.status().CanonicalCode()).c_str(),
      adapter.status().error_message().c_str());
  return false;
}

MATCHER_P(IsOkAndHolds, matcher, "") {
  if (!arg.ok()) {
    *result_listener << IcingStringUtil::StringPrintf(
        "Expected OK, actual was (%s:%s)",
        StatusCodeToString(arg.status().CanonicalCode()).c_str(),
        arg.status().error_message().c_str());
    return false;
  }
  return ExplainMatchResult(matcher, arg.ValueOrDie(), result_listener);
}

MATCHER_P(StatusIs, status_code, "") {
  libtextclassifier3::StatusAdapter adapter(arg);
  if (adapter.status().CanonicalCode() == status_code) {
    return true;
  }
  *result_listener << IcingStringUtil::StringPrintf(
      "Expected (%s:), actual was (%s:%s)",
      StatusCodeToString(status_code).c_str(),
      StatusCodeToString(adapter.status().CanonicalCode()).c_str(),
      adapter.status().error_message().c_str());
  return false;
}

MATCHER_P2(StatusIs, status_code, error_matcher, "") {
  libtextclassifier3::StatusAdapter adapter(arg);
  if (adapter.status().CanonicalCode() != status_code) {
    *result_listener << IcingStringUtil::StringPrintf(
        "Expected (%s:), actual was (%s:%s)",
        StatusCodeToString(status_code).c_str(),
        StatusCodeToString(adapter.status().CanonicalCode()).c_str(),
        adapter.status().error_message().c_str());
    return false;
  }
  return ExplainMatchResult(error_matcher, adapter.status().error_message(),
                            result_listener);
}

MATCHER(ProtoIsOk, "") {
  if (arg.code() == StatusProto::OK) {
    return true;
  }
  *result_listener << IcingStringUtil::StringPrintf(
      "Expected OK, actual was (%s:%s)",
      ProtoStatusCodeToString(arg.code()).c_str(), arg.message().c_str());
  return false;
}

MATCHER_P(ProtoStatusIs, status_code, "") {
  if (arg.code() == status_code) {
    return true;
  }
  *result_listener << IcingStringUtil::StringPrintf(
      "Expected (%s:), actual was (%s:%s)",
      ProtoStatusCodeToString(status_code).c_str(),
      ProtoStatusCodeToString(arg.code()).c_str(), arg.message().c_str());
  return false;
}

MATCHER_P2(ProtoStatusIs, status_code, error_matcher, "") {
  if (arg.code() != status_code) {
    *result_listener << IcingStringUtil::StringPrintf(
        "Expected (%s:), actual was (%s:%s)",
        ProtoStatusCodeToString(status_code).c_str(),
        ProtoStatusCodeToString(arg.code()).c_str(), arg.message().c_str());
    return false;
  }
  return ExplainMatchResult(error_matcher, arg.message(), result_listener);
}

MATCHER_P(EqualsSearchResultIgnoreStatsAndScores, expected, "") {
  SearchResultProto actual_copy = arg;
  actual_copy.clear_query_stats();
  actual_copy.clear_debug_info();
  for (SearchResultProto::ResultProto& result :
       *actual_copy.mutable_results()) {
    // Joined results
    for (SearchResultProto::ResultProto& joined_result :
         *result.mutable_joined_results()) {
      joined_result.clear_score();
    }
    result.clear_score();
  }

  SearchResultProto expected_copy = expected;
  expected_copy.clear_query_stats();
  expected_copy.clear_debug_info();
  for (SearchResultProto::ResultProto& result :
       *expected_copy.mutable_results()) {
    // Joined results
    for (SearchResultProto::ResultProto& joined_result :
         *result.mutable_joined_results()) {
      joined_result.clear_score();
    }
    result.clear_score();
  }
  return ExplainMatchResult(portable_equals_proto::EqualsProto(expected_copy),
                            actual_copy, result_listener);
}

// TODO(tjbarron) Remove this once icing has switched to depend on TC3 Status
#define ICING_STATUS_MACROS_CONCAT_NAME(x, y) \
  ICING_STATUS_MACROS_CONCAT_IMPL(x, y)
#define ICING_STATUS_MACROS_CONCAT_IMPL(x, y) x##y

#define ICING_EXPECT_OK(func) EXPECT_THAT(func, IsOk())
#define ICING_ASSERT_OK(func) ASSERT_THAT(func, IsOk())
#define ICING_ASSERT_OK_AND_ASSIGN(lhs, rexpr)                             \
  ICING_ASSERT_OK_AND_ASSIGN_IMPL(                                         \
      ICING_STATUS_MACROS_CONCAT_NAME(_status_or_value, __COUNTER__), lhs, \
      rexpr)
#define ICING_ASSERT_OK_AND_ASSIGN_IMPL(statusor, lhs, rexpr) \
  auto statusor = (rexpr);                                    \
  ICING_ASSERT_OK(statusor.status());                         \
  lhs = std::move(statusor).ValueOrDie()

#define ICING_ASSERT_HAS_VALUE_AND_ASSIGN(lhs, rexpr) \
  ASSERT_TRUE(rexpr);                                 \
  lhs = rexpr.value()

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_COMMON_MATCHERS_H_
