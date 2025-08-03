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

#include "icing/index/main/main-index.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/filesystem.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/index/main/doc-hit-info-iterator-term-main.h"
#include "icing/index/main/main-index-merger.h"
#include "icing/index/term-id-codec.h"
#include "icing/index/term-property-id.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mock-filesystem.h"
#include "icing/schema/section.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::SizeIs;

std::vector<DocHitInfo> GetHits(std::unique_ptr<DocHitInfoIterator> iterator) {
  std::vector<DocHitInfo> infos;
  while (iterator->Advance().ok()) {
    infos.push_back(iterator->doc_hit_info());
  }
  return infos;
}

std::vector<DocHitInfo> GetExactHits(
    MainIndex* main_index, int term_start_index, int unnormalized_term_length,
    const std::string& term, SectionIdMask section_mask = kSectionIdMaskAll) {
  auto iterator = std::make_unique<DocHitInfoIteratorTermMainExact>(
      main_index, term, term_start_index, unnormalized_term_length,
      section_mask, /*need_hit_term_frequency=*/true);
  return GetHits(std::move(iterator));
}

std::vector<DocHitInfo> GetPrefixHits(
    MainIndex* main_index, int term_start_index, int unnormalized_term_length,
    const std::string& term, SectionIdMask section_mask = kSectionIdMaskAll) {
  auto iterator = std::make_unique<DocHitInfoIteratorTermMainPrefix>(
      main_index, term, term_start_index, unnormalized_term_length,
      section_mask, /*need_hit_term_frequency=*/true);
  return GetHits(std::move(iterator));
}

libtextclassifier3::Status Merge(const LiteIndex& lite_index,
                                 const TermIdCodec& term_id_codec,
                                 MainIndex* main_index) {
  ICING_ASSIGN_OR_RETURN(MainIndex::LexiconMergeOutputs outputs,
                         main_index->MergeLexicon(lite_index.lexicon()));
  ICING_ASSIGN_OR_RETURN(std::vector<TermIdHitPair> term_id_hit_pairs,
                         MainIndexMerger::TranslateAndExpandLiteHits(
                             lite_index, term_id_codec, outputs));
  return main_index->AddHits(term_id_codec, std::move(outputs.backfill_map),
                             std::move(term_id_hit_pairs),
                             lite_index.last_added_document_id());
}

class MainIndexTest : public testing::Test {
 protected:
  void SetUp() override {
    index_dir_ = GetTestTempDir() + "/test_dir";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(index_dir_.c_str()));

    std::string lite_index_file_name = index_dir_ + "/test_file.lite-idx.index";
    LiteIndex::Options options(lite_index_file_name,
                               /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                               /*hit_buffer_sort_at_indexing=*/true,
                               /*hit_buffer_sort_threshold_bytes=*/1024 * 8);
    ICING_ASSERT_OK_AND_ASSIGN(lite_index_,
                               LiteIndex::Create(options, &icing_filesystem_));

    ICING_ASSERT_OK_AND_ASSIGN(
        term_id_codec_,
        TermIdCodec::Create(
            IcingDynamicTrie::max_value_index(IcingDynamicTrie::Options()),
            IcingDynamicTrie::max_value_index(options.lexicon_options)));
  }

  void TearDown() override {
    term_id_codec_.reset();
    lite_index_.reset();
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(index_dir_.c_str()));
  }

  std::string index_dir_;
  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::unique_ptr<LiteIndex> lite_index_;
  std::unique_ptr<TermIdCodec> term_id_codec_;
};

constexpr NamespaceId kNamespace0 = 0;

TEST_F(MainIndexTest, MainIndexCreateIOFailure) {
  // Create the index with mock filesystem. By default, Mock will return false,
  // so the first attempted file operation will fail.
  NiceMock<IcingMockFilesystem> mock_icing_filesystem;
  ON_CALL(mock_icing_filesystem, CreateDirectoryRecursively)
      .WillByDefault(Return(false));
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  EXPECT_THAT(MainIndex::Create(main_index_file_name, &filesystem_,
                                &mock_icing_filesystem),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_F(MainIndexTest, MainIndexGetAccessorForPrefixTermNotFound) {
  // Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));
  EXPECT_THAT(main_index->GetAccessorForPrefixTerm("foo"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(MainIndexTest, MainIndexGetAccessorForPrefixReturnsValidAccessor) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=true}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the index. The main index should contain "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // GetAccessorForPrefixTerm should return a valid accessor for "foo".
  EXPECT_THAT(main_index->GetAccessorForPrefixTerm("foo"), IsOk());
}

TEST_F(MainIndexTest, MainIndexGetAccessorForPrefixReturnsNotFound) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=false}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the index. The main index should return not found when we search
  // prefix contain "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // GetAccessorForPrefixTerm should return a valid accessor for "foo".
  EXPECT_THAT(main_index->GetAccessorForPrefixTerm("foo"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(MainIndexTest, MainIndexGetAccessorForExactTermNotFound) {
  // Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));
  EXPECT_THAT(main_index->GetAccessorForExactTerm("foo"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(MainIndexTest, MainIndexGetAccessorForExactReturnsValidAccessor) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foo" is_in_prefix_section=false}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foo", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the index. The main index should contain "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // GetAccessorForPrefixTerm should return a valid accessor for "foo".
  EXPECT_THAT(main_index->GetAccessorForExactTerm("foo"), IsOk());
}

TEST_F(MainIndexTest, MergeIndexToEmpty) {
  // 1. Index three docs in the Lite Index:
  // - Doc0 {"foot", "fool", "far" is_in_prefix_section=false}
  // - Doc1 {"foot", "fool" is_in_prefix_section=true}
  // - Doc2 {"fool", "far" is_in_prefix_section=false}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi, lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t fool_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("far", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t far_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc0_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(far_term_id, doc0_hit));

  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc1_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc1_hit));

  Hit doc2_hit(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc2_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(far_term_id, doc2_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  std::vector<DocHitInfo> hits =
      GetExactHits(main_index.get(), /*term_start_index=*/0,
                   /*unnormalized_term_length=*/0, "foot");
  EXPECT_THAT(hits, IsEmpty());
  hits = GetPrefixHits(main_index.get(), /*term_start_index=*/0,
                       /*unnormalized_term_length=*/0, "fo");
  EXPECT_THAT(hits, IsEmpty());

  // 3. Merge the index. The main index should contain "fool", "foot"
  // and "far" as well as a branch points for "foo" and "f". "fa" and "fo"
  // should not be present because it is not a branch point.
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // Get hits from an exact posting list.
  hits = GetExactHits(main_index.get(), /*term_start_index=*/0,
                      /*unnormalized_term_length=*/0, "foot");
  // We should get hits for "foot" in doc1 and doc0
  EXPECT_THAT(
      hits,
      ElementsAre(
          EqualsDocHitInfo(doc1_hit.document_id(),
                           std::vector<SectionId>{doc1_hit.section_id()}),
          EqualsDocHitInfo(doc0_hit.document_id(),
                           std::vector<SectionId>{doc0_hit.section_id()})));

  // Get hits from a branching point posting list. "fo" should redirect to "foo"
  hits = GetPrefixHits(main_index.get(), /*term_start_index=*/0,
                       /*unnormalized_term_length=*/0, "fo");
  // We should get hits for "foot" in doc1 and "fool" in doc1. We shouldn't get
  // the hits for "foot" in doc0 and "fool" in doc0 and doc2 because they
  // weren't hits in prefix sections.
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                        doc1_hit.document_id(),
                        std::vector<SectionId>{doc1_hit.section_id()})));
}

TEST_F(MainIndexTest, MergeIndexToPreexisting) {
  // 1. Index three docs in the Lite Index:
  // - Doc0 {"foot", "fool", "far" is_in_prefix_section=false}
  // - Doc1 {"foot", "fool" is_in_prefix_section=true}
  // - Doc2 {"fool", "far" is_in_prefix_section=false}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi, lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t fool_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("far", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t far_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc0_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(far_term_id, doc0_hit));

  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc1_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc1_hit));

  Hit doc2_hit(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc2_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(far_term_id, doc2_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the index. The main index should contain "fool", "foot"
  // and "far" as well as a branch points for "foo" and "f". "fa" and "fo"
  // should not be present because it is not a branch point.
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));

  // 4. Index two docs in a new Lite Index:
  // - Doc3 {"foot", "four", "foul", "fall" is_in_prefix_section=false}
  // - Doc4 {"four", "foul" is_in_prefix_section=true}
  std::string lite_index_file_name2 = index_dir_ + "/test_file.lite-idx.index2";
  LiteIndex::Options options(lite_index_file_name2,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(lite_index_,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("foot", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi, lite_index_->InsertTerm("four", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t four_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi, lite_index_->InsertTerm("foul", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foul_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("fall", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t fall_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc3_hit(/*section_id=*/0, /*document_id=*/3, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc3_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(four_term_id, doc3_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(foul_term_id, doc3_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(fall_term_id, doc3_hit));

  Hit doc4_hit(/*section_id=*/0, /*document_id=*/4, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(four_term_id, doc4_hit));
  ICING_ASSERT_OK(lite_index_->AddHit(foul_term_id, doc4_hit));

  // 3. Merge the index. The main index should now contain "foul", "four"
  // and "fall", a branch points for "fou" and backfill points for "fo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // Get hits from an exact posting list the existed before the merge.
  std::vector<DocHitInfo> hits =
      GetExactHits(main_index.get(), /*term_start_index=*/0,
                   /*unnormalized_term_length=*/0, "foot");

  // We should get hits for "foot" in doc3, doc1 and doc0
  EXPECT_THAT(
      hits,
      ElementsAre(
          EqualsDocHitInfo(doc3_hit.document_id(),
                           std::vector<SectionId>{doc3_hit.section_id()}),
          EqualsDocHitInfo(doc1_hit.document_id(),
                           std::vector<SectionId>{doc1_hit.section_id()}),
          EqualsDocHitInfo(doc0_hit.document_id(),
                           std::vector<SectionId>{doc0_hit.section_id()})));
  // Get hits from backfill posting list.
  hits = GetPrefixHits(main_index.get(), /*term_start_index=*/0,
                       /*unnormalized_term_length=*/0, "fo");
  // We should get hits for "four" and "foul" in doc4 and hits for "foot" and
  // "fool" in doc1. We shouldn't get the hits for "foot" in doc0 and doc3,
  // "fool" in doc0 and doc2 or the hits for "four" and "foul" in doc4 because
  // they weren't hits in prefix sections.
  EXPECT_THAT(
      hits,
      ElementsAre(
          EqualsDocHitInfo(doc4_hit.document_id(),
                           std::vector<SectionId>{doc4_hit.section_id()}),
          EqualsDocHitInfo(doc1_hit.document_id(),
                           std::vector<SectionId>{doc1_hit.section_id()})));
}

TEST_F(MainIndexTest, ExactRetrievedInPrefixSearch) {
  // 1. Index two docs in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=true}
  // - Doc1 {"foo" is_in_prefix_section=false}
  // - Doc2 {"foot" is_in_prefix_section=false}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("foo", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));

  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc1_hit));

  Hit doc2_hit(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc2_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the lite lexicon. The main lexicon should contain "foot" and
  // "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  std::vector<DocHitInfo> hits =
      GetPrefixHits(main_index.get(), /*term_start_index=*/0,
                    /*unnormalized_term_length=*/0, "foo");
  // We should get hits for "foo" in doc1 and doc0, but not in doc2 because it
  // is not a prefix hit.
  EXPECT_THAT(
      hits,
      ElementsAre(
          EqualsDocHitInfo(doc1_hit.document_id(),
                           std::vector<SectionId>{doc1_hit.section_id()}),
          EqualsDocHitInfo(doc0_hit.document_id(),
                           std::vector<SectionId>{doc0_hit.section_id()})));
}

TEST_F(MainIndexTest, PrefixNotRetrievedInExactSearch) {
  // 1. Index two docs in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=true}
  // - Doc1 {"foo" is_in_prefix_section=false}
  // - Doc1 {"foo" is_in_prefix_section=true}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi, lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));

  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc1_hit));

  Hit doc2_hit(/*section_id=*/0, /*document_id=*/2, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc2_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the lite lexicon. The main lexicon should contain "foot" and
  // "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  std::vector<DocHitInfo> hits =
      GetExactHits(main_index.get(), /*term_start_index=*/0,
                   /*unnormalized_term_length=*/0, "foo");

  // We should get hits for "foo" in doc2 and doc1, but not in doc0 because it
  // is not an exact hit.
  EXPECT_THAT(
      hits,
      ElementsAre(
          EqualsDocHitInfo(doc2_hit.document_id(),
                           std::vector<SectionId>{doc2_hit.section_id()}),
          EqualsDocHitInfo(doc1_hit.document_id(),
                           std::vector<SectionId>{doc1_hit.section_id()})));
}

TEST_F(MainIndexTest,
       SearchChainedPostingListsShouldMergeSectionsAndTermFrequency) {
  // Index 2048 document with 3 hits in each document. When merged into the main
  // index, this will 1) lead to a chained posting list and 2) split at least
  // one document's hits across multiple posting lists.
  const std::string term = "foot";

  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm(term, TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  for (DocumentId document_id = 0; document_id < 2048; ++document_id) {
    Hit::TermFrequency term_frequency = static_cast<Hit::TermFrequency>(
        document_id % Hit::kMaxTermFrequency + 1);
    Hit doc_hit0(
        /*section_id=*/0, /*document_id=*/document_id, term_frequency,
        /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc_hit0));

    Hit doc_hit1(
        /*section_id=*/1, /*document_id=*/document_id, term_frequency,
        /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc_hit1));

    Hit doc_hit2(
        /*section_id=*/2, /*document_id=*/document_id, term_frequency,
        /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc_hit2));
  }

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the lite index.
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // Get hits for all documents containing "foot" - which should be all of them.

  auto iterator = std::make_unique<DocHitInfoIteratorTermMainExact>(
      main_index.get(), term, /*term_start_index=*/0,
      /*unnormalized_term_length=*/0, kSectionIdMaskAll,
      /*need_hit_term_frequency=*/true);

  DocumentId expected_document_id = 2047;
  while (iterator->Advance().ok()) {
    EXPECT_THAT(iterator->doc_hit_info(),
                EqualsDocHitInfo(expected_document_id,
                                 std::vector<SectionId>{0, 1, 2}));

    std::vector<TermMatchInfo> matched_terms_stats;
    iterator->PopulateMatchedTermsStats(&matched_terms_stats);

    Hit::TermFrequency expected_term_frequency =
        static_cast<Hit::TermFrequency>(
            expected_document_id % Hit::kMaxTermFrequency + 1);
    ASSERT_THAT(matched_terms_stats, SizeIs(1));
    EXPECT_THAT(matched_terms_stats[0].term, Eq(term));
    EXPECT_THAT(matched_terms_stats[0].term_frequencies[0],
                Eq(expected_term_frequency));
    EXPECT_THAT(matched_terms_stats[0].term_frequencies[1],
                Eq(expected_term_frequency));
    EXPECT_THAT(matched_terms_stats[0].term_frequencies[2],
                Eq(expected_term_frequency));
    --expected_document_id;
  }
  EXPECT_THAT(expected_document_id, Eq(-1));
}

TEST_F(MainIndexTest, MergeIndexBackfilling) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"fool" is_in_prefix_section=true}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t fool_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc0_hit));

  // 2. Create the main index. It should have no entries in its lexicon.
  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  // 3. Merge the index. The main index should contain "fool".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));

  // 4. Index two docs in a new Lite Index:
  // - Doc1 {"foot" is_in_prefix_section=false}
  std::string lite_index_file_name2 = index_dir_ + "/test_file.lite-idx.index2";
  LiteIndex::Options options(lite_index_file_name2,
                             /*hit_buffer_want_merge_bytes=*/1024 * 1024,
                             /*hit_buffer_sort_at_indexing=*/true,
                             /*hit_buffer_sort_threshold_bytes=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(lite_index_,
                             LiteIndex::Create(options, &icing_filesystem_));
  ICING_ASSERT_OK_AND_ASSIGN(
      tvi,
      lite_index_->InsertTerm("foot", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foot_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));

  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc1_hit));

  // 5. Merge the index. The main index should now contain "fool", "foot"
  // and a backfill point for "foo".
  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  // Get hits from an exact posting list the existed before the merge.
  std::vector<DocHitInfo> hits =
      GetExactHits(main_index.get(), /*term_start_index=*/0,
                   /*unnormalized_term_length=*/0, "foo");
  EXPECT_THAT(hits, IsEmpty());

  // Get hits from backfill posting list.
  hits = GetPrefixHits(main_index.get(), /*term_start_index=*/0,
                       /*unnormalized_term_length=*/0, "foo");
  // We should get a hit for "fool" in doc0.
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfo(
                        doc0_hit.document_id(),
                        std::vector<SectionId>{doc0_hit.section_id()})));
}

TEST_F(MainIndexTest, OneHitInTheFirstPageForTwoPagesMainIndex) {
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t tvi,
      lite_index_->InsertTerm("foo", TermMatchType::EXACT_ONLY, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  SectionId section_id = 0;
  // Based on debugging logs, 2038 documents in the following setting will
  // result in two pages in the posting list chain, and the first page only
  // contains one hit.
  uint32_t num_docs = 2038;
  for (DocumentId document_id = 0; document_id < num_docs; ++document_id) {
    Hit doc_hit(section_id, document_id, Hit::kDefaultTermFrequency,
                /*is_in_prefix_section=*/false);
    ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, doc_hit));
  }

  std::string main_index_file_name = index_dir_ + "/test_file.idx.index";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(main_index_file_name, &filesystem_,
                        &icing_filesystem_));

  ICING_ASSERT_OK(Merge(*lite_index_, *term_id_codec_, main_index.get()));
  std::vector<DocHitInfo> hits =
      GetExactHits(main_index.get(), /*term_start_index=*/0,
                   /*unnormalized_term_length=*/0, "foo");
  ASSERT_THAT(hits, SizeIs(num_docs));
  for (DocumentId document_id = num_docs - 1; document_id >= 0; --document_id) {
    ASSERT_THAT(
        hits[num_docs - 1 - document_id],
        EqualsDocHitInfo(document_id, std::vector<SectionId>{section_id}));
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
