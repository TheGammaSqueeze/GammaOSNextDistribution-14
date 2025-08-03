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

#include "icing/index/property-existence-indexing-handler.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
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
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/index.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::IsTrue;
using ::testing::Test;

static constexpr std::string_view kTreeType = "TreeNode";
static constexpr std::string_view kPropertyName = "name";
static constexpr std::string_view kPropertyValue = "value";
static constexpr std::string_view kPropertySubtrees = "subtrees";

static constexpr std::string_view kValueType = "Value";
static constexpr std::string_view kPropertyBody = "body";
static constexpr std::string_view kPropertyTimestamp = "timestamp";
static constexpr std::string_view kPropertyScore = "score";

class PropertyExistenceIndexingHandlerTest : public Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    base_dir_ = GetTestTempDir() + "/icing_test";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    index_dir_ = base_dir_ + "/index";
    schema_store_dir_ = base_dir_ + "/schema_store";
    document_store_dir_ = base_dir_ + "/document_store";

    language_segmenter_factory::SegmenterOptions segmenter_options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        lang_segmenter_,
        language_segmenter_factory::Create(std::move(segmenter_options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        normalizer_,
        normalizer_factory::Create(
            /*max_term_byte_size=*/std::numeric_limits<int32_t>::max()));

    ASSERT_THAT(
        filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str()),
        IsTrue());
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kTreeType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyName)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kPropertyValue)
                            .SetDataTypeDocument(
                                kValueType, /*index_nested_properties=*/true)
                            .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kPropertySubtrees)
                            .SetDataTypeDocument(
                                kTreeType, /*index_nested_properties=*/false)
                            .SetCardinality(CARDINALITY_REPEATED)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kValueType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyBody)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyTimestamp)
                                     .SetDataType(TYPE_INT64)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyScore)
                                     .SetDataType(TYPE_DOUBLE)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/true));

    ASSERT_TRUE(
        filesystem_.CreateDirectoryRecursively(document_store_dir_.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult doc_store_create_result,
        DocumentStore::Create(&filesystem_, document_store_dir_, &fake_clock_,
                              schema_store_.get(),
                              /*force_recovery_and_revalidate_documents=*/false,
                              /*namespace_id_fingerprint=*/false,
                              /*pre_mapping_fbv=*/false,
                              /*use_persistent_hash_map=*/false,
                              PortableFileBackedProtoLog<
                                  DocumentWrapper>::kDeflateCompressionLevel,
                              /*initialize_stats=*/nullptr));
    document_store_ = std::move(doc_store_create_result.document_store);
  }

  void TearDown() override {
    document_store_.reset();
    schema_store_.reset();
    normalizer_.reset();
    lang_segmenter_.reset();

    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  FakeClock fake_clock_;
  std::string base_dir_;
  std::string index_dir_;
  std::string schema_store_dir_;
  std::string document_store_dir_;

  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
};

libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
QueryExistence(Index* index, std::string_view property_path) {
  return index->GetIterator(
      absl_ports::StrCat(kPropertyExistenceTokenPrefix, property_path),
      /*term_start_index=*/0,
      /*unnormalized_term_length=*/0, kSectionIdMaskAll,
      TermMatchType::EXACT_ONLY,
      /*need_hit_term_frequency=*/false);
}

std::vector<DocHitInfo> GetHits(std::unique_ptr<DocHitInfoIterator> iterator) {
  std::vector<DocHitInfo> infos;
  while (iterator->Advance().ok()) {
    infos.push_back(iterator->doc_hit_info());
  }
  return infos;
}

TEST_F(PropertyExistenceIndexingHandlerTest, HandlePropertyExistence) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  // Create a document with every property.
  DocumentProto document0 =
      DocumentBuilder()
          .SetKey("icing", "uri0")
          .SetSchema(std::string(kValueType))
          .AddStringProperty(std::string(kPropertyBody), "foo")
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .AddDoubleProperty(std::string(kPropertyScore), 456.789)
          .Build();
  // Create a document with missing body.
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("icing", "uri1")
          .SetSchema(std::string(kValueType))
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .AddDoubleProperty(std::string(kPropertyScore), 456.789)
          .Build();
  // Create a document with missing timestamp.
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(std::string(kValueType))
          .AddStringProperty(std::string(kPropertyBody), "foo")
          .AddDoubleProperty(std::string(kPropertyScore), 456.789)
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document0,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document0)));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document1,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document2,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id0,
      document_store_->Put(tokenized_document0.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(tokenized_document1.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(tokenized_document2.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PropertyExistenceIndexingHandler> handler,
      PropertyExistenceIndexingHandler::Create(&fake_clock_, index.get()));

  // Handle all docs
  EXPECT_THAT(handler->Handle(tokenized_document0, document_id0,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document1, document_id1,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document2, document_id2,
                              /*put_document_stats=*/nullptr),
              IsOk());

  // Get all documents that have "body".
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<DocHitInfoIterator> itr,
                             QueryExistence(index.get(), kPropertyBody));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id2, std::vector<SectionId>{0}),
                  EqualsDocHitInfo(document_id0, std::vector<SectionId>{0})));

  // Get all documents that have "timestamp".
  ICING_ASSERT_OK_AND_ASSIGN(itr,
                             QueryExistence(index.get(), kPropertyTimestamp));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id1, std::vector<SectionId>{0}),
                  EqualsDocHitInfo(document_id0, std::vector<SectionId>{0})));

  // Get all documents that have "score".
  ICING_ASSERT_OK_AND_ASSIGN(itr, QueryExistence(index.get(), kPropertyScore));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id2, std::vector<SectionId>{0}),
                  EqualsDocHitInfo(document_id1, std::vector<SectionId>{0}),
                  EqualsDocHitInfo(document_id0, std::vector<SectionId>{0})));
}

TEST_F(PropertyExistenceIndexingHandlerTest, HandleNestedPropertyExistence) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  // Create a complex nested root_document with the following property paths.
  // - name
  // - subtrees
  // - subtrees.name
  // - subtrees.value
  // - subtrees.value.timestamp
  // - subtrees.subtrees
  // - subtrees.subtrees.name
  // - subtrees.subtrees.value
  // - subtrees.subtrees.value.body
  // - subtrees.subtrees.value.score
  DocumentProto leaf_document =
      DocumentBuilder()
          .SetKey("icing", "uri")
          .SetSchema(std::string(kTreeType))
          .AddStringProperty(std::string(kPropertyName), "leaf")
          .AddDocumentProperty(
              std::string(kPropertyValue),
              DocumentBuilder()
                  .SetKey("icing", "uri")
                  .SetSchema(std::string(kValueType))
                  .AddStringProperty(std::string(kPropertyBody), "foo")
                  .AddDoubleProperty(std::string(kPropertyScore), 456.789)
                  .Build())
          .Build();
  DocumentProto intermediate_document1 =
      DocumentBuilder()
          .SetKey("icing", "uri")
          .SetSchema(std::string(kTreeType))
          .AddStringProperty(std::string(kPropertyName), "intermediate1")
          .AddDocumentProperty(
              std::string(kPropertyValue),
              DocumentBuilder()
                  .SetKey("icing", "uri")
                  .SetSchema(std::string(kValueType))
                  .AddInt64Property(std::string(kPropertyTimestamp), 123)
                  .Build())
          .AddDocumentProperty(std::string(kPropertySubtrees), leaf_document)
          .Build();
  DocumentProto intermediate_document2 =
      DocumentBuilder()
          .SetKey("icing", "uri")
          .SetSchema(std::string(kTreeType))
          .AddStringProperty(std::string(kPropertyName), "intermediate2")
          .Build();
  DocumentProto root_document =
      DocumentBuilder()
          .SetKey("icing", "uri")
          .SetSchema(std::string(kTreeType))
          .AddStringProperty(std::string(kPropertyName), "root")
          .AddDocumentProperty(std::string(kPropertySubtrees),
                               intermediate_document1, intermediate_document2)
          .Build();

  // Handle root_document
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_root_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(root_document)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_root_document.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PropertyExistenceIndexingHandler> handler,
      PropertyExistenceIndexingHandler::Create(&fake_clock_, index.get()));
  EXPECT_THAT(handler->Handle(tokenized_root_document, document_id,
                              /*put_document_stats=*/nullptr),
              IsOk());

  // Check that the above property paths can be found by query.
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<DocHitInfoIterator> itr,
                             QueryExistence(index.get(), "name"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(itr, QueryExistence(index.get(), "subtrees"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(itr, QueryExistence(index.get(), "subtrees.name"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(itr,
                             QueryExistence(index.get(), "subtrees.value"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, QueryExistence(index.get(), "subtrees.value.timestamp"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(itr,
                             QueryExistence(index.get(), "subtrees.subtrees"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, QueryExistence(index.get(), "subtrees.subtrees.name"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, QueryExistence(index.get(), "subtrees.subtrees.value"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, QueryExistence(index.get(), "subtrees.subtrees.value.body"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, QueryExistence(index.get(), "subtrees.subtrees.value.score"));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));
}

TEST_F(PropertyExistenceIndexingHandlerTest, SingleEmptyStringIsNonExisting) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  // Create a document with one empty body.
  DocumentProto document0 =
      DocumentBuilder()
          .SetKey("icing", "uri0")
          .SetSchema(std::string(kValueType))
          .AddStringProperty(std::string(kPropertyBody), "")
          .Build();
  // Create a document with two empty body.
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("icing", "uri1")
          .SetSchema(std::string(kValueType))
          .AddStringProperty(std::string(kPropertyBody), "", "")
          .Build();
  // Create a document with one non-empty body.
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("icing", "uri2")
          .SetSchema(std::string(kValueType))
          .AddStringProperty(std::string(kPropertyBody), "foo")
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document0,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document0)));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document1,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document2,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id0,
      document_store_->Put(tokenized_document0.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(tokenized_document1.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(tokenized_document2.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PropertyExistenceIndexingHandler> handler,
      PropertyExistenceIndexingHandler::Create(&fake_clock_, index.get()));

  // Handle all docs
  EXPECT_THAT(handler->Handle(tokenized_document0, document_id0,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document1, document_id1,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document2, document_id2,
                              /*put_document_stats=*/nullptr),
              IsOk());

  // Check that the documents that have one or two empty bodies will not be
  // considered as having a body property.
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<DocHitInfoIterator> itr,
                             QueryExistence(index.get(), kPropertyBody));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id2, std::vector<SectionId>{0})));
}

}  // namespace

}  // namespace lib
}  // namespace icing
