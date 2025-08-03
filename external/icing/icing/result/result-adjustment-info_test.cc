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

#include "icing/result/result-adjustment-info.h"

#include <string>
#include <unordered_set>
#include <vector>

#include "gtest/gtest.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/result/projection-tree.h"
#include "icing/result/snippet-context.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::AnyOf;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::Pair;
using ::testing::UnorderedElementsAre;

class ResultAdjustmentInfoTest : public testing::Test {
 protected:
  ResultAdjustmentInfoTest() : test_dir_(GetTestTempDir() + "/icing") {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
  }

  void SetUp() override {
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType("Email"))
            .AddType(SchemaTypeConfigBuilder().SetType("Phone"))
            .Build();
    ASSERT_THAT(schema_store_->SetSchema(
                    schema, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOk());
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  const Filesystem filesystem_;
  const std::string test_dir_;
  std::unique_ptr<SchemaStore> schema_store_;
  FakeClock fake_clock_;
};

SearchSpecProto CreateSearchSpec(TermMatchType::Code match_type) {
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(match_type);
  return search_spec;
}

ScoringSpecProto CreateScoringSpec(bool is_descending_order) {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_order_by(is_descending_order ? ScoringSpecProto::Order::DESC
                                                : ScoringSpecProto::Order::ASC);
  return scoring_spec;
}

ResultSpecProto CreateResultSpec(
    int num_per_page, ResultSpecProto::ResultGroupingType result_group_type) {
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(result_group_type);
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

TEST_F(ResultAdjustmentInfoTest,
       ShouldConstructSnippetContextAccordingToSpecs) {
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(5);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(5);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(5);

  SectionRestrictQueryTermsMap query_terms_map;
  query_terms_map.emplace("term1", std::unordered_set<std::string>());

  ResultAdjustmentInfo result_adjustment_info(
      CreateSearchSpec(TermMatchType::EXACT_ONLY),
      CreateScoringSpec(/*is_descending_order=*/true), result_spec,
      schema_store_.get(), query_terms_map);
  const SnippetContext snippet_context = result_adjustment_info.snippet_context;

  // Snippet context should be derived from the specs above.
  EXPECT_TRUE(
      result_adjustment_info.snippet_context.query_terms.find("term1") !=
      result_adjustment_info.snippet_context.query_terms.end());
  EXPECT_THAT(result_adjustment_info.snippet_context.snippet_spec,
              EqualsProto(result_spec.snippet_spec()));
  EXPECT_THAT(result_adjustment_info.snippet_context.match_type,
              Eq(TermMatchType::EXACT_ONLY));
  EXPECT_THAT(result_adjustment_info.remaining_num_to_snippet, Eq(5));
}

TEST_F(ResultAdjustmentInfoTest, NoSnippetingShouldReturnNull) {
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE);
  // Setting num_to_snippet to 0 so that snippeting info won't be
  // stored.
  result_spec.mutable_snippet_spec()->set_num_to_snippet(0);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(5);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(5);

  SectionRestrictQueryTermsMap query_terms_map;
  query_terms_map.emplace("term1", std::unordered_set<std::string>());

  ResultAdjustmentInfo result_adjustment_info(
      CreateSearchSpec(TermMatchType::EXACT_ONLY),
      CreateScoringSpec(/*is_descending_order=*/true), result_spec,
      schema_store_.get(), query_terms_map);

  EXPECT_THAT(result_adjustment_info.snippet_context.query_terms, IsEmpty());
  EXPECT_THAT(
      result_adjustment_info.snippet_context.snippet_spec,
      EqualsProto(ResultSpecProto::SnippetSpecProto::default_instance()));
  EXPECT_THAT(result_adjustment_info.snippet_context.match_type,
              TermMatchType::UNKNOWN);
  EXPECT_THAT(result_adjustment_info.remaining_num_to_snippet, Eq(0));
}

TEST_F(ResultAdjustmentInfoTest,
       ShouldConstructProjectionTreeMapAccordingToSpecs) {
  // Create a ResultSpec with type property mask.
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE);
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Email");
  email_type_property_mask->add_paths("sender.name");
  email_type_property_mask->add_paths("sender.emailAddress");
  TypePropertyMask* phone_type_property_mask =
      result_spec.add_type_property_masks();
  phone_type_property_mask->set_schema_type("Phone");
  phone_type_property_mask->add_paths("caller");
  TypePropertyMask* wildcard_type_property_mask =
      result_spec.add_type_property_masks();
  wildcard_type_property_mask->set_schema_type(
      std::string(SchemaStore::kSchemaTypeWildcard));
  wildcard_type_property_mask->add_paths("wild.card");

  ResultAdjustmentInfo result_adjustment_info(
      CreateSearchSpec(TermMatchType::EXACT_ONLY),
      CreateScoringSpec(/*is_descending_order=*/true), result_spec,
      schema_store_.get(),
      /*query_terms=*/{});

  ProjectionTree email_projection_tree =
      ProjectionTree({"Email", {"sender.name", "sender.emailAddress"}});
  ProjectionTree alternative_email_projection_tree =
      ProjectionTree({"Email", {"sender.emailAddress", "sender.name"}});
  ProjectionTree phone_projection_tree = ProjectionTree({"Phone", {"caller"}});
  ProjectionTree wildcard_projection_tree = ProjectionTree(
      {std::string(SchemaStore::kSchemaTypeWildcard), {"wild.card"}});

  EXPECT_THAT(result_adjustment_info.projection_tree_map,
              UnorderedElementsAre(
                  Pair("Email", AnyOf(email_projection_tree,
                                      alternative_email_projection_tree)),
                  Pair("Phone", phone_projection_tree),
                  Pair(std::string(SchemaStore::kSchemaTypeWildcard),
                       wildcard_projection_tree)));
}

}  // namespace

}  // namespace lib
}  // namespace icing
