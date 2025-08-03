// Copyright (C) 2022 Google LLC
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

#include "icing/query/advanced_query_parser/query-visitor.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/index/hit/hit.h"
#include "icing/index/index.h"
#include "icing/index/iterator/doc-hit-info-iterator-filter.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/dummy-numeric-index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/property-existence-indexing-handler.h"
#include "icing/jni/jni-cache.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/portable/platform.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/query/advanced_query_parser/lexer.h"
#include "icing/query/advanced_query_parser/parser.h"
#include "icing/query/query-features.h"
#include "icing/query/query-results.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "icing/util/status-macros.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;
using ::testing::UnorderedElementsAre;

constexpr DocumentId kDocumentId0 = 0;
constexpr DocumentId kDocumentId1 = 1;
constexpr DocumentId kDocumentId2 = 2;

constexpr SectionId kSectionId0 = 0;
constexpr SectionId kSectionId1 = 1;
constexpr SectionId kSectionId2 = 2;

template <typename T, typename U>
std::vector<T> ExtractKeys(const std::unordered_map<T, U>& map) {
  std::vector<T> keys;
  keys.reserve(map.size());
  for (const auto& [key, value] : map) {
    keys.push_back(key);
  }
  return keys;
}

enum class QueryType {
  kPlain,
  kSearch,
};

class QueryVisitorTest : public ::testing::TestWithParam<QueryType> {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";
    index_dir_ = test_dir_ + "/index";
    numeric_index_dir_ = test_dir_ + "/numeric_index";
    store_dir_ = test_dir_ + "/store";
    schema_store_dir_ = test_dir_ + "/schema_store";
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(index_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(store_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str());

    jni_cache_ = GetTestJniCache();

    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      // If we've specified using the reverse-JNI method for segmentation (i.e.
      // not ICU), then we won't have the ICU data file included to set up.
      // Technically, we could choose to use reverse-JNI for segmentation AND
      // include an ICU data file, but that seems unlikely and our current BUILD
      // setup doesn't do this.
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &clock_));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(
            &filesystem_, store_dir_, &clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(create_result.document_store);

    Index::Options options(index_dir_.c_str(),
                           /*index_merge_size=*/1024 * 1024,
                           /*lite_index_sort_at_indexing=*/true,
                           /*lite_index_sort_size=*/1024 * 8);
    ICING_ASSERT_OK_AND_ASSIGN(
        index_, Index::Create(options, &filesystem_, &icing_filesystem_));

    ICING_ASSERT_OK_AND_ASSIGN(
        numeric_index_,
        DummyNumericIndex<int64_t>::Create(filesystem_, numeric_index_dir_));

    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/1000));

    language_segmenter_factory::SegmenterOptions segmenter_options(
        ULOC_US, jni_cache_.get());
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(segmenter_options));

    ICING_ASSERT_OK_AND_ASSIGN(tokenizer_,
                               tokenizer_factory::CreateIndexingTokenizer(
                                   StringIndexingConfig::TokenizerType::PLAIN,
                                   language_segmenter_.get()));
  }

  libtextclassifier3::StatusOr<std::unique_ptr<Node>> ParseQueryHelper(
      std::string_view query) {
    Lexer lexer(query, Lexer::Language::QUERY);
    ICING_ASSIGN_OR_RETURN(std::vector<Lexer::LexerToken> lexer_tokens,
                           lexer.ExtractTokens());
    Parser parser = Parser::Create(std::move(lexer_tokens));
    return parser.ConsumeQuery();
  }

  std::string EscapeString(std::string_view str) {
    std::string result;
    result.reserve(str.size());
    for (char c : str) {
      if (c == '\\' || c == '"') {
        result.push_back('\\');
      }
      result.push_back(c);
    }
    return result;
  }

  std::string CreateQuery(std::string query,
                          std::string property_restrict = "") {
    switch (GetParam()) {
      case QueryType::kPlain:
        if (property_restrict.empty()) {
          // CreateQuery("foo bar") returns `foo bar`
          return query;
        }
        // CreateQuery("foo", "subject") returns `subject:foo`
        return absl_ports::StrCat(property_restrict, ":", query);
      case QueryType::kSearch:
        query = EscapeString(query);
        property_restrict = EscapeString(property_restrict);
        if (property_restrict.empty()) {
          // CreateQuery("foo bar") returns `search("foo bar")`
          return absl_ports::StrCat("search(\"", query, "\")");
        }
        // CreateQuery("foo", "subject") returns
        // `search("foo bar", createList("subject"))`
        return absl_ports::StrCat("search(\"", query, "\", createList(\"",
                                  property_restrict, "\"))");
    }
  }

  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::string test_dir_;
  std::string index_dir_;
  std::string numeric_index_dir_;
  std::string schema_store_dir_;
  std::string store_dir_;
  Clock clock_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  std::unique_ptr<Index> index_;
  std::unique_ptr<DummyNumericIndex<int64_t>> numeric_index_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<Tokenizer> tokenizer_;
  std::unique_ptr<const JniCache> jni_cache_;
};

TEST_P(QueryVisitorTest, SimpleLessThan) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price < 2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and "2" isn't a "term" - its a numeric
  // value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, SimpleLessThanEq) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price <= 1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and "1" isn't a "term" - its a numeric
  // value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, SimpleEqual) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price == 2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and "2" isn't a "term" - its a numeric
  // value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, SimpleGreaterThanEq) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price >= 1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and "1" isn't a "term" - its a numeric
  // value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId1));
}

TEST_P(QueryVisitorTest, SimpleGreaterThan) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price > 1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and "1" isn't a "term" - its a numeric
  // value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, IntMinLessThanEqual) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values INT_MIN,
  // INT_MAX and INT_MIN + 1 respectively.
  int64_t int_min = std::numeric_limits<int64_t>::min();
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(int_min));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(std::numeric_limits<int64_t>::max()));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(int_min + 1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price <= " + std::to_string(int_min));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and int_min isn't a "term" - its a
  // numeric value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));
}

TEST_P(QueryVisitorTest, IntMaxGreaterThanEqual) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values INT_MIN,
  // INT_MAX and INT_MAX - 1 respectively.
  int64_t int_max = std::numeric_limits<int64_t>::max();
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(std::numeric_limits<int64_t>::min()));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(int_max));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(int_max - 1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price >= " + std::to_string(int_max));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "price" is a property restrict here and int_max isn't a "term" - its a
  // numeric value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, NestedPropertyLessThan) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("subscription.price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("subscription.price < 2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  // "subscription.price" is a property restrict here and int_max isn't a "term"
  // - its a numeric value. So QueryTermIterators should be empty.
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, IntParsingError) {
  std::string query = CreateQuery("subscription.price < fruit");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, NotEqualsUnsupported) {
  std::string query = CreateQuery("subscription.price != 3");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::UNIMPLEMENTED));
}

TEST_P(QueryVisitorTest, LessThanTooManyOperandsInvalid) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("subscription.price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  // Create an invalid AST for the query '3 < subscription.price 25' where '<'
  // has three operands
  std::string_view query = "3 < subscription.price 25";
  auto property_node =
      std::make_unique<TextNode>("subscription", query.substr(4, 12));
  auto subproperty_node =
      std::make_unique<TextNode>("price", query.substr(17, 5));
  std::vector<std::unique_ptr<TextNode>> member_args;
  member_args.push_back(std::move(property_node));
  member_args.push_back(std::move(subproperty_node));
  auto member_node = std::make_unique<MemberNode>(std::move(member_args),
                                                  /*function=*/nullptr);

  auto value_node = std::make_unique<TextNode>("3", query.substr(0, 1));
  auto extra_value_node = std::make_unique<TextNode>("25", query.substr(23, 2));
  std::vector<std::unique_ptr<Node>> args;
  args.push_back(std::move(value_node));
  args.push_back(std::move(member_node));
  args.push_back(std::move(extra_value_node));
  auto root_node = std::make_unique<NaryOperatorNode>("<", std::move(args));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, LessThanTooFewOperandsInvalid) {
  // Create an invalid AST for the query 'subscription.price <' where '<'
  // has a single operand
  std::string_view query = "subscription.price <";
  auto property_node =
      std::make_unique<TextNode>("subscription", query.substr(0, 12));
  auto subproperty_node =
      std::make_unique<TextNode>("price", query.substr(13, 5));
  std::vector<std::unique_ptr<TextNode>> member_args;
  member_args.push_back(std::move(property_node));
  member_args.push_back(std::move(subproperty_node));
  auto member_node = std::make_unique<MemberNode>(std::move(member_args),
                                                  /*function=*/nullptr);

  std::vector<std::unique_ptr<Node>> args;
  args.push_back(std::move(member_node));
  auto root_node = std::make_unique<NaryOperatorNode>("<", std::move(args));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, LessThanNonExistentPropertyNotFound) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values 0, 1 and 2
  // respectively.
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("subscription.price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(0));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor =
      numeric_index_->Edit("subscription.price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("time < 25");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());
}

TEST_P(QueryVisitorTest, NeverVisitedReturnsInvalid) {
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), "",
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, IntMinLessThanInvalid) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values INT_MIN,
  // INT_MAX and INT_MIN + 1 respectively.
  int64_t int_min = std::numeric_limits<int64_t>::min();
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(int_min));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(std::numeric_limits<int64_t>::max()));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(int_min + 1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price <" + std::to_string(int_min));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, IntMaxGreaterThanInvalid) {
  // Setup the numeric index with docs 0, 1 and 2 holding the values INT_MIN,
  // INT_MAX and INT_MAX - 1 respectively.
  int64_t int_max = std::numeric_limits<int64_t>::max();
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(std::numeric_limits<int64_t>::min()));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId1);
  ICING_ASSERT_OK(editor->BufferKey(int_max));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId2);
  ICING_ASSERT_OK(editor->BufferKey(int_max - 1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  std::string query = CreateQuery("price >" + std::to_string(int_max));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, NumericComparisonPropertyStringIsInvalid) {
  // "price" is a STRING token, which cannot be a property name.
  std::string query = CreateQuery(R"("price" > 7)");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, NumericComparatorDoesntAffectLaterTerms) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Index three documents:
  // - Doc0: ["-2", "-1", "1", "2"] and [-2, -1, 1, 2]
  // - Doc1: [-1]
  // - Doc2: ["2"] and [-1]
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      numeric_index_->Edit("price", kDocumentId0, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(-2));
  ICING_ASSERT_OK(editor->BufferKey(-1));
  ICING_ASSERT_OK(editor->BufferKey(1));
  ICING_ASSERT_OK(editor->BufferKey(2));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());
  Index::Editor term_editor = index_->Edit(
      kDocumentId0, kSectionId1, TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(term_editor.BufferTerm("-2"));
  ICING_ASSERT_OK(term_editor.BufferTerm("-1"));
  ICING_ASSERT_OK(term_editor.BufferTerm("1"));
  ICING_ASSERT_OK(term_editor.BufferTerm("2"));
  ICING_ASSERT_OK(term_editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = numeric_index_->Edit("price", kDocumentId1, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(-1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = numeric_index_->Edit("price", kDocumentId2, kSectionId0);
  ICING_ASSERT_OK(editor->BufferKey(-1));
  ICING_ASSERT_OK(std::move(*editor).IndexAllBufferedKeys());
  term_editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                             /*namespace_id=*/0);
  ICING_ASSERT_OK(term_editor.BufferTerm("2"));
  ICING_ASSERT_OK(term_editor.IndexAllBufferedTerms());

  // Translating MINUS chars that are interpreted as NOTs, this query would be
  // `price == -1 AND NOT 2`
  // All documents should match `price == -1`
  // Both docs 0 and 2 should be excluded because of the `NOT 2` clause
  // doc0 has both a text and number entry for `-2`, neither of which should
  // match.
  std::string query = CreateQuery("price == -1 -2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kNumericSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, SingleTermTermFrequencyEnabled) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo", "foo" and
  // "bar" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));

  ASSERT_THAT(query_results.root_iterator->Advance(), IsOk());
  std::vector<TermMatchInfo> match_infos;
  query_results.root_iterator->PopulateMatchedTermsStats(&match_infos);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map = {{kSectionId1, 1}};
  EXPECT_THAT(match_infos, ElementsAre(EqualsTermMatchInfo(
                               "foo", expected_section_ids_tf_map)));

  ASSERT_THAT(query_results.root_iterator->Advance(), IsOk());
  match_infos.clear();
  query_results.root_iterator->PopulateMatchedTermsStats(&match_infos);
  EXPECT_THAT(match_infos, ElementsAre(EqualsTermMatchInfo(
                               "foo", expected_section_ids_tf_map)));

  EXPECT_THAT(query_results.root_iterator->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_P(QueryVisitorTest, SingleTermTermFrequencyDisabled) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo", "foo" and
  // "bar" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/false, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());

  ASSERT_THAT(query_results.root_iterator->Advance(), IsOk());
  std::vector<TermMatchInfo> match_infos;
  query_results.root_iterator->PopulateMatchedTermsStats(&match_infos);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map = {{kSectionId1, 0}};
  EXPECT_THAT(match_infos, ElementsAre(EqualsTermMatchInfo(
                               "foo", expected_section_ids_tf_map)));

  ASSERT_THAT(query_results.root_iterator->Advance(), IsOk());
  match_infos.clear();
  query_results.root_iterator->PopulateMatchedTermsStats(&match_infos);
  EXPECT_THAT(match_infos, ElementsAre(EqualsTermMatchInfo(
                               "foo", expected_section_ids_tf_map)));

  EXPECT_THAT(query_results.root_iterator->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_P(QueryVisitorTest, SingleTermPrefix) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo", "foo" and
  // "bar" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // An EXACT query for 'fo' won't match anything.
  std::string query = CreateQuery("fo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_EXACT,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("fo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("fo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());

  query = CreateQuery("fo*");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_EXACT,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("fo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("fo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, PrefixOperatorAfterPropertyReturnsInvalid) {
  std::string query = "price* < 2";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, PrefixOperatorAfterNumericValueReturnsInvalid) {
  std::string query = "price < 2*";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, PrefixOperatorAfterPropertyRestrictReturnsInvalid) {
  std::string query = "subject*:foo";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, SegmentationWithPrefix) {
  // Setup the index with docs 0, 1 and 2 holding the values ["foo", "ba"],
  // ["foo", "ba"] and ["bar", "fo"] respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("ba"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("ba"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm("fo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // An EXACT query for `ba?fo` will be lexed into a single TEXT token.
  // The visitor will tokenize it into `ba` and `fo` (`?` is dropped because it
  // is punctuation). Each document will match one and only one of these exact
  // tokens. Therefore, nothing will match this query.
  std::string query = CreateQuery("ba?fo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_EXACT,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("ba", "fo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("ba", "fo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());

  // An EXACT query for `ba?fo*` will be lexed into a TEXT token and a TIMES
  // token.
  // The visitor will tokenize the TEXT into `ba` and `fo` (`?` is dropped
  // because it is punctuation). The prefix operator should only apply to the
  // final token `fo`. This will cause matches with docs 0 and 1 which contain
  // "ba" and "foo". doc2 will not match because "ba" does not exactly match
  // either "bar" or "fo".
  query = CreateQuery("ba?fo*");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_EXACT,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("ba", "fo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("ba", "fo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, SingleVerbatimTerm) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo:bar(baz)",
  // "foo:bar(baz)" and "bar:baz(foo)" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo:bar(baz)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo:bar(baz)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar:baz(foo)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("\"foo:bar(baz)\"");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo:bar(baz)"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo:bar(baz)"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, SingleVerbatimTermPrefix) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo:bar(baz)",
  // "foo:bar(abc)" and "bar:baz(foo)" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo:bar(baz)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo:bar(abc)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar:baz(foo)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Query for `"foo:bar("*`. This should match docs 0 and 1.
  std::string query = CreateQuery("\"foo:bar(\"*");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_EXACT,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kVerbatimSearchFeature,
                                   kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("foo:bar("));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo:bar("));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

// There are three primary cases to worry about for escaping:
//
// NOTE: The following comments use ` chars to denote the beginning and end of
// the verbatim term rather than " chars to avoid confusion. Additionally, the
// raw chars themselves are shown. So `foobar\\` in actual c++ would be written
// as std::string verbatim_term = "foobar\\\\";
//
// 1. How does a user represent a quote char (") without terminating the
//    verbatim term?
//    Example: verbatim_term = `foobar"`
//    Answer: quote char must be escaped. verbatim_query = `foobar\"`
TEST_P(QueryVisitorTest, VerbatimTermEscapingQuote) {
  // Setup the index with docs 0, 1 and 2 holding the values "foobary",
  // "foobar\" and "foobar"" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_EXACT, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobary)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar")"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // From the comment above, verbatim_term = `foobar"` and verbatim_query =
  // `foobar\"`
  std::string query = CreateQuery(R"(("foobar\""))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foobar")"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foobar")"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

// 2. How does a user represent a escape char (\) that immediately precedes the
//    end of the verbatim term
//    Example: verbatim_term = `foobar\`
//    Answer: escape chars can be escaped. verbatim_query = `foobar\\`
TEST_P(QueryVisitorTest, VerbatimTermEscapingEscape) {
  // Setup the index with docs 0, 1 and 2 holding the values "foobary",
  // "foobar\" and "foobar"" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_EXACT, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobary)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  // From the comment above, verbatim_term = `foobar\`.
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar")"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Issue a query for the verbatim token `foobar\`.
  std::string query = CreateQuery(R"(("foobar\\"))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foobar\)"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foobar\)"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

// 3. How do we handle other escaped chars?
//    Example: verbatim_query = `foobar\y`.
//    Answer: all chars preceded by an escape character are blindly escaped (as
//            in, consume the escape char and add the char like we do for the
//            quote char). So the above query would match the verbatim_term
//            `foobary`.
TEST_P(QueryVisitorTest, VerbatimTermEscapingNonSpecialChar) {
  // Setup the index with docs 0, 1 and 2 holding the values "foobary",
  // "foobar\" and "foobar"" respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_EXACT, /*namespace_id=*/0);
  // From the comment above, verbatim_term = `foobary`.
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobary)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\y)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Issue a query for the verbatim token `foobary`.
  std::string query = CreateQuery(R"(("foobar\y"))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foobary)"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foobary)"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));

  // Issue a query for the verbatim token `foobar\y`.
  query = CreateQuery(R"(("foobar\\y"))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foobar\y)"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foobar\y)"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

// This isn't a special case, but is fairly useful for demonstrating. There are
// a number of escaped sequences in c++, including the new line character '\n'.
// It is worth emphasizing that the new line character, like the others in c++,
// is its own separate ascii value. For a query `foobar\n`, the parser will see
// the character sequence [`f`, `o`, `o`, `b`, `a`, `r`, `\n`] - it *won't* ever
// see `\` and `n`.
TEST_P(QueryVisitorTest, VerbatimTermNewLine) {
  // Setup the index with docs 0, 1 and 2 holding the values "foobar\n",
  // `foobar\` and `foobar\n` respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_EXACT, /*namespace_id=*/0);
  // From the comment above, verbatim_term = `foobar` + '\n'.
  ICING_ASSERT_OK(editor.BufferTerm("foobar\n"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  // verbatim_term = `foobar\n`. This is distinct from the term added above.
  ICING_ASSERT_OK(editor.BufferTerm(R"(foobar\n)"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Issue a query for the verbatim token `foobar` + '\n'.
  std::string query = CreateQuery("\"foobar\n\"");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("foobar\n"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foobar\n"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));

  // Now, issue a query for the verbatim token `foobar\n`.
  query = CreateQuery(R"(("foobar\\n"))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foobar\n)"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foobar\n)"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, VerbatimTermEscapingComplex) {
  // Setup the index with docs 0, 1 and 2 holding the values `foo\"bar\nbaz"`,
  // `foo\\\"bar\\nbaz\"` and `foo\\"bar\\nbaz"` respectively.
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_EXACT, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(R"(foo\"bar\nbaz")"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  // Add the verbatim_term from doc 0 but with all of the escapes left in
  ICING_ASSERT_OK(editor.BufferTerm(R"(foo\\\"bar\\nbaz\")"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_EXACT,
                        /*namespace_id=*/0);
  // Add the verbatim_term from doc 0 but with the escapes for '\' chars left in
  ICING_ASSERT_OK(editor.BufferTerm(R"(foo\\"bar\\nbaz")"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Issue a query for the verbatim token `foo\"bar\nbaz"`.
  std::string query = CreateQuery(R"(("foo\\\"bar\\nbaz\""))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature,
                                     kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kVerbatimSearchFeature));
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre(R"(foo\"bar\nbaz")"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre(R"(foo\"bar\nbaz")"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));
}

TEST_P(QueryVisitorTest, SingleMinusTerm) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo", "foo" and
  // "bar" respectively.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("-foo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms), IsEmpty());
  EXPECT_THAT(query_results.query_term_iterators, IsEmpty());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, SingleNotTerm) {
  // Setup the index with docs 0, 1 and 2 holding the values "foo", "foo" and
  // "bar" respectively.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("NOT foo");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.query_terms, IsEmpty());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(query_results.query_term_iterators, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, NestedNotTerms) {
  // Setup the index with docs 0, 1 and 2 holding the values
  // ["foo", "bar", "baz"], ["foo", "baz"] and ["bar", "baz"] respectively.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Double negative could be rewritten as `(foo AND NOT bar) baz`
  std::string query = CreateQuery("NOT (-foo OR bar) baz");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "baz"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "baz"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, DeeplyNestedNotTerms) {
  // Setup the index with docs 0, 1 and 2 holding the values
  // ["foo", "bar", "baz"], ["foo", "baz"] and ["bar", "baz"] respectively.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Simplifying:
  //   NOT (-(NOT (foo -bar) baz) -bat) NOT bass
  //   NOT (-((-foo OR bar) baz) -bat) NOT bass
  //   NOT (((foo -bar) OR -baz) -bat) NOT bass
  //   (((-foo OR bar) baz) OR bat) NOT bass
  //
  // Doc 0 : (((-TRUE OR TRUE) TRUE) OR FALSE) NOT FALSE ->
  //         ((FALSE OR TRUE) TRUE) TRUE -> ((TRUE) TRUE) TRUE -> TRUE
  // Doc 1 : (((-TRUE OR FALSE) TRUE) OR FALSE) NOT FALSE
  //         ((FALSE OR FALSE) TRUE) TRUE -> ((FALSE) TRUE) TRUE -> FALSE
  // Doc 2 : (((-FALSE OR TRUE) TRUE) OR FALSE) NOT FALSE
  //         ((TRUE OR TRUE) TRUE) TRUE -> ((TRUE) TRUE) TRUE -> TRUE
  std::string query = CreateQuery("NOT (-(NOT (foo -bar) baz) -bat) NOT bass");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("bar", "baz", "bat"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("bar", "baz", "bat"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId0));
}

TEST_P(QueryVisitorTest, ImplicitAndTerms) {
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo bar");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, ExplicitAndTerms) {
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo AND bar");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, OrTerms) {
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("fo"));
  ICING_ASSERT_OK(editor.BufferTerm("ba"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo OR bar");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId0));
}

TEST_P(QueryVisitorTest, AndOrTermPrecedence) {
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Should be interpreted like `foo (bar OR baz)`
  std::string query = CreateQuery("foo bar OR baz");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId1));

  // Should be interpreted like `(bar OR baz) foo`
  query = CreateQuery("bar OR baz foo");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId1));

  query = CreateQuery("(bar OR baz) foo");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_three(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_three);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_three).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar", "baz"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId1));
}

TEST_P(QueryVisitorTest, AndOrNotPrecedence) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("baz"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Should be interpreted like `foo ((NOT bar) OR baz)`
  std::string query = CreateQuery("foo NOT bar OR baz");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("foo", "baz"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "baz"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId0));

  query = CreateQuery("foo NOT (bar OR baz)");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  EXPECT_THAT(query_results.query_terms[""], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));
}

TEST_P(QueryVisitorTest, PropertyFilter) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_F(QueryVisitorTest, MultiPropertyFilter) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop3")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;
  SectionId prop3_section_id = 2;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop3_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = R"(search("foo", createList("prop1", "prop2")))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1", "prop2"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop2"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, PropertyFilterStringIsInvalid) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // "prop1" is a STRING token, which cannot be a property name.
  std::string query = CreateQuery(R"(("prop1":foo))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, PropertyFilterNonNormalized) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("PROP1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("PROP2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));
  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo", /*property_restrict=*/"PROP1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("PROP1"));
  EXPECT_THAT(query_results.query_terms["PROP1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, PropertyFilterWithGrouping) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query =
      CreateQuery("(foo OR bar)", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1, kDocumentId0));
}

TEST_P(QueryVisitorTest, ValidNestedPropertyFilter) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("(prop1:foo)", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));

  query = CreateQuery("(prop1:(prop1:(prop1:(prop1:foo))))",
                      /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId1));
}

TEST_P(QueryVisitorTest, InvalidNestedPropertyFilter) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("(prop2:foo)", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), IsEmpty());
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());

  // Resulting queries:
  // - kPlain: `prop1:(prop2:(prop1:(prop2:(prop1:foo))))`
  // - kSearch: `-search("(prop2:(prop1:(prop2:(prop1:foo))))",
  //                             createList("prop1"))`
  query = CreateQuery("(prop2:(prop1:(prop2:(prop1:foo))))",
                      /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), IsEmpty());
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators), IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());
}

TEST_P(QueryVisitorTest, NotWithPropertyFilter) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Resulting queries:
  // - kPlain: `-prop1:(foo OR bar)`
  // - kSearch: `-search("foo OR bar", createList("prop1"))`
  std::string query = absl_ports::StrCat(
      "-", CreateQuery("(foo OR bar)", /*property_restrict=*/"prop1"));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), IsEmpty());
  EXPECT_THAT(query_results.query_term_iterators, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));

  // Resulting queries:
  // - kPlain: `NOT prop1:(foo OR bar)`
  // - kSearch: `NOT search("foo OR bar", createList("prop1"))`
  query = absl_ports::StrCat(
      "NOT ", CreateQuery("(foo OR bar)", /*property_restrict=*/"prop1"));
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms), IsEmpty());
  EXPECT_THAT(query_results.query_term_iterators, IsEmpty());
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

TEST_P(QueryVisitorTest, PropertyFilterWithNot) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  // Create documents as follows:
  //   Doc0:
  //     prop1: "bar"
  //     prop2: ""
  //   Doc1:
  //     prop1: "foo"
  //     prop2: ""
  //   Doc2:
  //     prop1: ""
  //     prop2: "foo"
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Resulting queries:
  // - kPlain: `prop1:(-foo OR bar)`
  // - kSearch: `search("-foo OR bar", createList("prop1"))`
  //
  // The query is equivalent to `-prop1:foo OR prop1:bar`, thus doc0 and doc2
  // will be matched.
  std::string query =
      CreateQuery("(-foo OR bar)", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId0));

  // Resulting queries:
  // - kPlain: `prop1:(-foo OR bar)`
  // - kSearch: `search("-foo OR bar", createList("prop1"))`
  //
  // The query is equivalent to `-prop1:foo OR prop1:bar`, thus doc0 and doc2
  // will be matched.
  query = CreateQuery("(NOT foo OR bar)", /*property_restrict=*/"prop1");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2, kDocumentId0));
}

TEST_P(QueryVisitorTest, SegmentationTest) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;
  SectionId prop2_section_id = 1;

  // ICU segmentation will break this into "每天" and "上班".
  // CFStringTokenizer (ios) will break this into "每", "天" and "上班"
  std::string query = CreateQuery("每天上班");
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("上班"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(kDocumentId0, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  if (IsCfStringTokenization()) {
    ICING_ASSERT_OK(editor.BufferTerm("每"));
    ICING_ASSERT_OK(editor.BufferTerm("天"));
  } else {
    ICING_ASSERT_OK(editor.BufferTerm("每天"));
  }
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("上班"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop2_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  if (IsCfStringTokenization()) {
    ICING_ASSERT_OK(editor.BufferTerm("每"));
    ICING_ASSERT_OK(editor.BufferTerm("天"));
  } else {
    ICING_ASSERT_OK(editor.BufferTerm("每天"));
  }
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms), UnorderedElementsAre(""));
  if (IsCfStringTokenization()) {
    EXPECT_THAT(query_results.query_terms[""],
                UnorderedElementsAre("每", "天", "上班"));
    EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
                UnorderedElementsAre("每", "天", "上班"));
  } else {
    EXPECT_THAT(query_results.query_terms[""],
                UnorderedElementsAre("每天", "上班"));
    EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
                UnorderedElementsAre("每天", "上班"));
  }
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId0));
}

TEST_P(QueryVisitorTest, PropertyRestrictsPopCorrectly) {
  PropertyConfigProto prop =
      PropertyConfigBuilder()
          .SetName("prop0")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("type")
                  .AddProperty(prop)
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop1"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop2")))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  SectionId prop0_id = 0;
  SectionId prop1_id = 1;
  SectionId prop2_id = 2;
  NamespaceId ns_id = 0;

  // Create the following docs:
  // - Doc 0: Contains 'val0', 'val1', 'val2' in 'prop0'. Shouldn't match.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid0, document_store_->Put(doc));
  Index::Editor editor =
      index_->Edit(docid0, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 1: Contains 'val0', 'val1', 'val2' in 'prop1'. Should match.
  doc = DocumentBuilder(doc).SetUri("uri1").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid1, document_store_->Put(doc));
  editor = index_->Edit(docid1, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 2: Contains 'val0', 'val1', 'val2' in 'prop2'. Shouldn't match.
  doc = DocumentBuilder(doc).SetUri("uri2").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid2, document_store_->Put(doc));
  editor = index_->Edit(docid2, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 3: Contains 'val0' in 'prop0', 'val1' in 'prop1' etc. Should match.
  doc = DocumentBuilder(doc).SetUri("uri3").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid3, document_store_->Put(doc));
  editor = index_->Edit(docid3, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid3, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid3, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 4: Contains 'val1' in 'prop0', 'val2' in 'prop1', 'val0' in 'prop2'.
  //          Shouldn't match.
  doc = DocumentBuilder(doc).SetUri("uri4").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid4, document_store_->Put(doc));
  editor = index_->Edit(docid4, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid4, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid4, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Now issue a query with 'val1' restricted to 'prop1'. This should match only
  // docs 1 and 3.
  // Resulting queries:
  // - kPlain: `val0 prop1:val1 val2`
  // - kSearch: `val0 search("val1", createList("prop1")) val2`
  std::string query = absl_ports::StrCat(
      "val0 ", CreateQuery("val1", /*property_restrict=*/"prop1"), " val2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  if (GetParam() == QueryType::kSearch) {
    EXPECT_THAT(query_results.features_in_use,
                UnorderedElementsAre(kListFilterQueryLanguageFeature));
  } else {
    EXPECT_THAT(query_results.features_in_use, IsEmpty());
  }
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("", "prop1"));
  EXPECT_THAT(query_results.query_terms[""],
              UnorderedElementsAre("val0", "val2"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("val1"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("val0", "val1", "val2"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid3, docid1));
}

TEST_P(QueryVisitorTest, UnsatisfiablePropertyRestrictsPopCorrectly) {
  PropertyConfigProto prop =
      PropertyConfigBuilder()
          .SetName("prop0")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("type")
                  .AddProperty(prop)
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop1"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop2")))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  SectionId prop0_id = 0;
  SectionId prop1_id = 1;
  SectionId prop2_id = 2;
  NamespaceId ns_id = 0;

  // Create the following docs:
  // - Doc 0: Contains 'val0', 'val1', 'val2' in 'prop0'. Should match.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid0, document_store_->Put(doc));
  Index::Editor editor =
      index_->Edit(docid0, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 1: Contains 'val0', 'val1', 'val2' in 'prop1'. Shouldn't match.
  doc = DocumentBuilder(doc).SetUri("uri1").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid1, document_store_->Put(doc));
  editor = index_->Edit(docid1, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 2: Contains 'val0', 'val1', 'val2' in 'prop2'. Should match.
  doc = DocumentBuilder(doc).SetUri("uri2").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid2, document_store_->Put(doc));
  editor = index_->Edit(docid2, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 3: Contains 'val0' in 'prop0', 'val1' in 'prop1' etc. Should match.
  doc = DocumentBuilder(doc).SetUri("uri3").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid3, document_store_->Put(doc));
  editor = index_->Edit(docid3, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid3, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid3, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // - Doc 4: Contains 'val1' in 'prop0', 'val2' in 'prop1', 'val0' in 'prop2'.
  //          Shouldn't match.
  doc = DocumentBuilder(doc).SetUri("uri4").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid4, document_store_->Put(doc));
  editor = index_->Edit(docid4, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val1"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid4, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val2"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
  editor = index_->Edit(docid4, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("val0"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Now issue a query with 'val1' restricted to 'prop1'. This should match only
  // docs 1 and 3.
  // Resulting queries:
  // - kPlain: `val0 OR prop1:(prop2:val1) OR val2`
  // - kSearch: `prop0:val0 OR search("(prop2:val1)", createList("prop1")) OR
  // prop2:val2`
  std::string query = absl_ports::StrCat(
      "prop0:val0 OR prop1:(",
      CreateQuery("val1", /*property_restrict=*/"prop2"), ") OR prop2:val2");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop2"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("val0"));
  EXPECT_THAT(query_results.query_terms["prop2"], UnorderedElementsAre("val2"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("val0", "val2"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid3, docid2, docid0));
}

TEST_F(QueryVisitorTest, UnsupportedFunctionReturnsInvalidArgument) {
  std::string query = "unsupportedFunction()";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest, SearchFunctionTooFewArgumentsReturnsInvalidArgument) {
  std::string query = "search()";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest, SearchFunctionTooManyArgumentsReturnsInvalidArgument) {
  std::string query = R"(search("foo", createList("subject"), "bar"))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       SearchFunctionWrongFirstArgumentTypeReturnsInvalidArgument) {
  // First argument type=TEXT, expected STRING.
  std::string query = "search(7)";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // First argument type=string list, expected STRING.
  query = R"(search(createList("subject")))";
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  EXPECT_THAT(std::move(query_visitor_two).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       SearchFunctionWrongSecondArgumentTypeReturnsInvalidArgument) {
  // Second argument type=STRING, expected string list.
  std::string query = R"(search("foo", "bar"))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Second argument type=TEXT, expected string list.
  query = R"(search("foo", 7))";
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  EXPECT_THAT(std::move(query_visitor_two).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       SearchFunctionCreateListZeroPropertiesReturnsInvalidArgument) {
  std::string query = R"(search("foo", createList()))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest, SearchFunctionNestedFunctionCalls) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop1")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("prop2")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop1_section_id = 0;

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, prop1_section_id,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId1, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("type").Build()));
  editor = index_->Edit(kDocumentId2, prop1_section_id, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // *If* nested function calls were allowed, then this would simplify as:
  // `search("search(\"foo\") bar")` -> `search("foo bar")` -> `foo bar`
  // But nested function calls are disallowed. So this is rejected.
  std::string level_one_query = R"(search("foo", createList("prop1")) bar)";
  std::string level_two_query =
      absl_ports::StrCat(R"(search(")", EscapeString(level_one_query),
                         R"(", createList("prop1")))");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(level_two_query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), level_two_query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));

  std::string level_three_query =
      absl_ports::StrCat(R"(search(")", EscapeString(level_two_query),
                         R"(", createList("prop1")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_three_query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(),
      level_three_query, DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));

  std::string level_four_query =
      absl_ports::StrCat(R"(search(")", EscapeString(level_three_query),
                         R"(", createList("prop1")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_four_query));
  QueryVisitor query_visitor_three(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(),
      level_four_query, DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_three);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_three).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop1"));
  EXPECT_THAT(query_results.query_terms["prop1"],
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo", "bar"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(kDocumentId2));
}

// This test will nest `search` calls together with the set of restricts
// narrowing at each level so that the set of docs matching the query shrinks.
TEST_F(QueryVisitorTest, SearchFunctionNestedPropertyRestrictsNarrowing) {
  PropertyConfigProto prop =
      PropertyConfigBuilder()
          .SetName("prop0")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("type")
                  .AddProperty(prop)
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop1"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop2"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop3"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop4"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop5"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop6"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop7")))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop0_id = 0;
  SectionId prop1_id = 1;
  SectionId prop2_id = 2;
  SectionId prop3_id = 3;
  SectionId prop4_id = 4;
  SectionId prop5_id = 5;
  SectionId prop6_id = 6;
  SectionId prop7_id = 7;

  NamespaceId ns_id = 0;
  DocumentProto doc =
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid0, document_store_->Put(doc));
  Index::Editor editor =
      index_->Edit(kDocumentId0, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid1,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  editor = index_->Edit(docid1, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid2,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  editor = index_->Edit(docid2, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid3,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  editor = index_->Edit(docid3, prop3_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid4,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  editor = index_->Edit(docid4, prop4_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid5,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  editor = index_->Edit(docid5, prop5_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid6,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  editor = index_->Edit(docid6, prop6_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid7,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  editor = index_->Edit(docid7, prop7_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // *If* nested function calls were allowed, then this would simplify as:
  // `search("search(\"foo\") bar")` -> `search("foo bar")` -> `foo bar`
  // But nested function calls are disallowed. So this is rejected.
  std::string level_one_query =
      R"(search("foo", createList("prop2", "prop5", "prop1", "prop3", "prop0", "prop6", "prop4", "prop7")))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(level_one_query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), level_one_query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop1", "prop2", "prop3", "prop4",
                                   "prop5", "prop6", "prop7"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop1"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop2"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop3"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop4"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop5"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop7"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid7, docid6, docid5, docid4, docid3, docid2,
                          docid1, docid0));

  std::string level_two_query = absl_ports::StrCat(
      R"(search(")", EscapeString(level_one_query),
      R"(", createList("prop6", "prop0", "prop4", "prop2")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_two_query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), level_two_query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop2", "prop4", "prop6"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop2"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop4"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid6, docid4, docid2, docid0));

  std::string level_three_query =
      absl_ports::StrCat(R"(search(")", EscapeString(level_two_query),
                         R"(", createList("prop0", "prop6")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_three_query));
  QueryVisitor query_visitor_three(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(),
      level_three_query, DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_three);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_three).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop6"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid6, docid0));
}

// This test will nest `search` calls together with the set of restricts
// narrowing at each level so that the set of docs matching the query shrinks.
TEST_F(QueryVisitorTest, SearchFunctionNestedPropertyRestrictsExpanding) {
  PropertyConfigProto prop =
      PropertyConfigBuilder()
          .SetName("prop0")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("type")
                  .AddProperty(prop)
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop1"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop2"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop3"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop4"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop5"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop6"))
                  .AddProperty(PropertyConfigBuilder(prop).SetName("prop7")))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Section ids are assigned alphabetically.
  SectionId prop0_id = 0;
  SectionId prop1_id = 1;
  SectionId prop2_id = 2;
  SectionId prop3_id = 3;
  SectionId prop4_id = 4;
  SectionId prop5_id = 5;
  SectionId prop6_id = 6;
  SectionId prop7_id = 7;

  NamespaceId ns_id = 0;
  DocumentProto doc =
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("type").Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId docid0, document_store_->Put(doc));
  Index::Editor editor =
      index_->Edit(kDocumentId0, prop0_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid1,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  editor = index_->Edit(docid1, prop1_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid2,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  editor = index_->Edit(docid2, prop2_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid3,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  editor = index_->Edit(docid3, prop3_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid4,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  editor = index_->Edit(docid4, prop4_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid5,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  editor = index_->Edit(docid5, prop5_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid6,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  editor = index_->Edit(docid6, prop6_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId docid7,
      document_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  editor = index_->Edit(docid7, prop7_id, TERM_MATCH_PREFIX, ns_id);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // *If* nested function calls were allowed, then this would simplify as:
  // `search("search(\"foo\") bar")` -> `search("foo bar")` -> `foo bar`
  // But nested function calls are disallowed. So this is rejected.
  std::string level_one_query =
      R"(search("foo", createList("prop0", "prop6")))";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(level_one_query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), level_one_query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop6"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid6, docid0));

  std::string level_two_query = absl_ports::StrCat(
      R"(search(")", EscapeString(level_one_query),
      R"(", createList("prop6", "prop0", "prop4", "prop2")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_two_query));
  QueryVisitor query_visitor_two(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), level_two_query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_two);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_two).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop6"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid6, docid0));

  std::string level_three_query =
      absl_ports::StrCat(R"(search(")", EscapeString(level_two_query),
                         R"(", createList("prop2", "prop5", "prop1", "prop3",)",
                         R"( "prop0", "prop6", "prop4", "prop7")))");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(level_three_query));
  QueryVisitor query_visitor_three(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(),
      level_three_query, DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor_three);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor_three).ConsumeResults());

  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));
  EXPECT_THAT(ExtractKeys(query_results.query_terms),
              UnorderedElementsAre("prop0", "prop6"));
  EXPECT_THAT(query_results.query_terms["prop0"], UnorderedElementsAre("foo"));
  EXPECT_THAT(query_results.query_terms["prop6"], UnorderedElementsAre("foo"));
  EXPECT_THAT(ExtractKeys(query_results.query_term_iterators),
              UnorderedElementsAre("foo"));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              ElementsAre(docid6, docid0));
}

TEST_F(QueryVisitorTest,
       PropertyDefinedFunctionWithNoArgumentReturnsInvalidArgument) {
  std::string query = "propertyDefined()";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(
    QueryVisitorTest,
    PropertyDefinedFunctionWithMoreThanOneTextArgumentReturnsInvalidArgument) {
  std::string query = "propertyDefined(\"foo\", \"bar\")";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       PropertyDefinedFunctionWithTextArgumentReturnsInvalidArgument) {
  // The argument type is TEXT, not STRING here.
  std::string query = "propertyDefined(foo)";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       PropertyDefinedFunctionWithNonTextArgumentReturnsInvalidArgument) {
  std::string query = "propertyDefined(1 < 2)";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, PropertyDefinedFunctionReturnsMatchingDocuments) {
  // Set up two schemas, one with a "url" field and one without.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("typeWithUrl")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("url")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("typeWithoutUrl"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Document 0 has the term "foo" and its schema has the url property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("typeWithUrl").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 1 has the term "foo" and its schema DOESN'T have the url property.
  ICING_ASSERT_OK(document_store_->Put(DocumentBuilder()
                                           .SetKey("ns", "uri1")
                                           .SetSchema("typeWithoutUrl")
                                           .Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 2 has the term "bar" and its schema has the url property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("typeWithUrl").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo propertyDefined(\"url\")");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));

  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              UnorderedElementsAre(kDocumentId0));
}

TEST_P(QueryVisitorTest,
       PropertyDefinedFunctionReturnsNothingIfNoMatchingProperties) {
  // Set up two schemas, one with a "url" field and one without.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("typeWithUrl")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("url")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("typeWithoutUrl"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Document 0 has the term "foo" and its schema has the url property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("typeWithUrl").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 1 has the term "foo" and its schema DOESN'T have the url property.
  ICING_ASSERT_OK(document_store_->Put(DocumentBuilder()
                                           .SetKey("ns", "uri1")
                                           .SetSchema("typeWithoutUrl")
                                           .Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Attempt to query a non-existent property.
  std::string query = CreateQuery("propertyDefined(\"nonexistentproperty\")");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));

  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());
}

TEST_P(QueryVisitorTest,
       PropertyDefinedFunctionWithNegationMatchesDocsWithNoSuchProperty) {
  // Set up two schemas, one with a "url" field and one without.
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("typeWithUrl")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("url")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder().SetType("typeWithoutUrl"))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Document 0 has the term "foo" and its schema has the url property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("typeWithUrl").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId1,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 1 has the term "foo" and its schema DOESN'T have the url property.
  ICING_ASSERT_OK(document_store_->Put(DocumentBuilder()
                                           .SetKey("ns", "uri1")
                                           .SetSchema("typeWithoutUrl")
                                           .Build()));
  editor = index_->Edit(kDocumentId1, kSectionId1, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  std::string query = CreateQuery("foo AND NOT propertyDefined(\"url\")");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info_=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kListFilterQueryLanguageFeature));

  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              UnorderedElementsAre(kDocumentId1));
}

TEST_F(QueryVisitorTest,
       HasPropertyFunctionWithNoArgumentReturnsInvalidArgument) {
  std::string query = "hasProperty()";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       HasPropertyFunctionWithMoreThanOneStringArgumentReturnsInvalidArgument) {
  std::string query = "hasProperty(\"foo\", \"bar\")";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       HasPropertyFunctionWithTextArgumentReturnsInvalidArgument) {
  // The argument type is TEXT, not STRING here.
  std::string query = "hasProperty(foo)";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(QueryVisitorTest,
       HasPropertyFunctionWithNonStringArgumentReturnsInvalidArgument) {
  std::string query = "hasProperty(1 < 2)";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  EXPECT_THAT(std::move(query_visitor).ConsumeResults(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(QueryVisitorTest, HasPropertyFunctionReturnsMatchingDocuments) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Simple")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("price")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Document 0 has the term "foo" and has the "price" property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("Simple").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId0,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm(
      absl_ports::StrCat(kPropertyExistenceTokenPrefix, "price").c_str()));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 1 has the term "foo" and doesn't have the "price" property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("Simple").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId0, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 2 has the term "bar" and has the "price" property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri2").SetSchema("Simple").Build()));
  editor = index_->Edit(kDocumentId2, kSectionId0, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("bar"));
  ICING_ASSERT_OK(editor.BufferTerm(
      absl_ports::StrCat(kPropertyExistenceTokenPrefix, "price").c_str()));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Test that `foo hasProperty("price")` matches document 0 only.
  std::string query = CreateQuery("foo hasProperty(\"price\")");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor1(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor1);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor1).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kHasPropertyFunctionFeature,
                                   kListFilterQueryLanguageFeature));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              UnorderedElementsAre(kDocumentId0));

  // Test that `bar OR NOT hasProperty("price")` matches document 1 and
  // document 2.
  query = CreateQuery("bar OR NOT hasProperty(\"price\")");
  ICING_ASSERT_OK_AND_ASSIGN(root_node, ParseQueryHelper(query));
  QueryVisitor query_visitor2(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor2);
  ICING_ASSERT_OK_AND_ASSIGN(query_results,
                             std::move(query_visitor2).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kHasPropertyFunctionFeature,
                                   kListFilterQueryLanguageFeature));
  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()),
              UnorderedElementsAre(kDocumentId1, kDocumentId2));
}

TEST_P(QueryVisitorTest,
       HasPropertyFunctionReturnsNothingIfNoMatchingProperties) {
  ICING_ASSERT_OK(schema_store_->SetSchema(
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Simple")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("name")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("price")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build(),
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Document 0 has the term "foo" and has the "price" property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri0").SetSchema("Simple").Build()));
  Index::Editor editor = index_->Edit(kDocumentId0, kSectionId0,
                                      TERM_MATCH_PREFIX, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.BufferTerm(
      absl_ports::StrCat(kPropertyExistenceTokenPrefix, "price").c_str()));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Document 1 has the term "foo" and doesn't have the "price" property.
  ICING_ASSERT_OK(document_store_->Put(
      DocumentBuilder().SetKey("ns", "uri1").SetSchema("Simple").Build()));
  editor = index_->Edit(kDocumentId1, kSectionId0, TERM_MATCH_PREFIX,
                        /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm("foo"));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());

  // Attempt to query a non-existent property.
  std::string query = CreateQuery("hasProperty(\"nonexistentproperty\")");
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> root_node,
                             ParseQueryHelper(query));
  QueryVisitor query_visitor(
      index_.get(), numeric_index_.get(), document_store_.get(),
      schema_store_.get(), normalizer_.get(), tokenizer_.get(), query,
      DocHitInfoIteratorFilter::Options(), TERM_MATCH_PREFIX,
      /*needs_term_frequency_info=*/true, clock_.GetSystemTimeMilliseconds());
  root_node->Accept(&query_visitor);
  ICING_ASSERT_OK_AND_ASSIGN(QueryResults query_results,
                             std::move(query_visitor).ConsumeResults());
  EXPECT_THAT(query_results.features_in_use,
              UnorderedElementsAre(kHasPropertyFunctionFeature,
                                   kListFilterQueryLanguageFeature));

  EXPECT_THAT(GetDocumentIds(query_results.root_iterator.get()), IsEmpty());
}

INSTANTIATE_TEST_SUITE_P(QueryVisitorTest, QueryVisitorTest,
                         testing::Values(QueryType::kPlain,
                                         QueryType::kSearch));

}  // namespace

}  // namespace lib
}  // namespace icing
