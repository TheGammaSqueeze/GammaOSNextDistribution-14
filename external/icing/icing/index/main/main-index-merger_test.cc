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
#include "icing/index/main/main-index-merger.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/filesystem.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/main/doc-hit-info-iterator-term-main.h"
#include "icing/index/main/main-index-merger.h"
#include "icing/index/main/main-index.h"
#include "icing/index/term-id-codec.h"
#include "icing/index/term-property-id.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/schema/section.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::UnorderedElementsAre;

class MainIndexMergerTest : public testing::Test {
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
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(index_dir_.c_str()));
  }

  std::string index_dir_;
  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  std::unique_ptr<LiteIndex> lite_index_;
  std::unique_ptr<TermIdCodec> term_id_codec_;
};

constexpr NamespaceId kNamespace0 = 0;

TEST_F(MainIndexMergerTest, TranslateTermNotAdded) {
  // 1. Index two docs in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=FALSE}
  // - Doc1 {"fool", is_in_prefix_section=FALSE}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_term_id,
      term_id_codec_->EncodeTvi(foot_tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_tvi,
      lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_term_id,
      term_id_codec_->EncodeTvi(fool_tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/57,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));
  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc1_hit));

  // 2. Build up a fake LexiconMergeOutputs
  // This is some made up number that doesn't matter for this test.
  uint32_t foot_main_tvi = 5;

  // Only create a mapping for 'foot'. Leave out the mapping for 'fool'
  MainIndex::LexiconMergeOutputs lexicon_outputs;
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foot_tvi, foot_main_tvi);

  // 3. TranslateAndExpand should fail because 'fool' doesn't have a main tvi
  // mapping.
  ASSERT_THAT(MainIndexMerger::TranslateAndExpandLiteHits(
                  *lite_index_, *term_id_codec_, lexicon_outputs),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_F(MainIndexMergerTest, PrefixExpansion) {
  // 1. Index two docs in the Lite Index:
  // - Doc0 {"foot" is_in_prefix_section=FALSE}
  // - Doc1 {"fool", is_in_prefix_section=TRUE}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_term_id,
      term_id_codec_->EncodeTvi(foot_tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_tvi,
      lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_term_id,
      term_id_codec_->EncodeTvi(fool_tvi, TviType::LITE));

  Hit doc0_hit(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/57,
               /*is_in_prefix_section=*/false);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, doc0_hit));
  Hit doc1_hit(/*section_id=*/0, /*document_id=*/1, Hit::kDefaultTermFrequency,
               /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, doc1_hit));

  // 2. Build up a fake LexiconMergeOutputs
  // This is some made up number that doesn't matter for this test.
  uint32_t foo_main_tvi = 12;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_term_id,
      term_id_codec_->EncodeTvi(foo_main_tvi, TviType::MAIN));
  Hit doc1_prefix_hit(/*section_id=*/0, /*document_id=*/1,
                      Hit::kDefaultTermFrequency,
                      /*is_in_prefix_section=*/true, /*is_prefix_hit=*/true);

  uint32_t foot_main_tvi = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_main_term_id,
      term_id_codec_->EncodeTvi(foot_main_tvi, TviType::MAIN));
  uint32_t fool_main_tvi = 10;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_main_term_id,
      term_id_codec_->EncodeTvi(fool_main_tvi, TviType::MAIN));

  MainIndex::LexiconMergeOutputs lexicon_outputs;
  // Map "fool" to it's prefix hit for "foo".
  lexicon_outputs.other_tvi_to_prefix_main_tvis.emplace(fool_tvi,
                                                        std::make_pair(0, 1));
  lexicon_outputs.prefix_tvis_buf.push_back(foo_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foot_tvi, foot_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(fool_tvi, fool_main_tvi);

  // 3. TranslateAndExpand should;
  //   a. Translate lite term ids to main term ids based on the map
  //   b. Expand 'fool' to have a hit for 'foo'
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermIdHitPair> expanded_term_id_hit_pairs,
      MainIndexMerger::TranslateAndExpandLiteHits(*lite_index_, *term_id_codec_,
                                                  lexicon_outputs));
  EXPECT_THAT(
      expanded_term_id_hit_pairs,
      UnorderedElementsAre(TermIdHitPair(foot_main_term_id, doc0_hit),
                           TermIdHitPair(fool_main_term_id, doc1_hit),
                           TermIdHitPair(foo_term_id, doc1_prefix_hit)));
}

TEST_F(MainIndexMergerTest, DedupePrefixAndExactWithDifferentTermFrequencies) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foot" "foo" is_in_prefix_section=TRUE}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_term_id,
      term_id_codec_->EncodeTvi(foot_tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));

  Hit foot_doc0_hit(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/57,
                    /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, foot_doc0_hit));
  Hit foo_doc0_hit(/*section_id=*/0, /*document_id=*/0,
                   Hit::kDefaultTermFrequency,
                   /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, foo_doc0_hit));

  // 2. Build up a fake LexiconMergeOutputs
  // This is some made up number that doesn't matter for this test.
  uint32_t foo_main_tvi = 12;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_main_term_id,
      term_id_codec_->EncodeTvi(foo_main_tvi, TviType::MAIN));
  // The prefix hit for 'foot' should have the same term frequency as the exact
  // hit for 'foot'. The final prefix hit has term frequency equal to 58.
  Hit doc0_prefix_hit(/*section_id=*/0, /*document_id=*/0,
                      /*term_frequency=*/58,
                      /*is_in_prefix_section=*/true, /*is_prefix_hit=*/true);

  uint32_t foot_main_tvi = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_main_term_id,
      term_id_codec_->EncodeTvi(foot_main_tvi, TviType::MAIN));

  MainIndex::LexiconMergeOutputs lexicon_outputs;
  // Map "foot" to it's prefix hit for "foo".
  lexicon_outputs.other_tvi_to_prefix_main_tvis.emplace(foot_tvi,
                                                        std::make_pair(0, 1));
  lexicon_outputs.prefix_tvis_buf.push_back(foo_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foot_tvi, foot_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foo_tvi, foo_main_tvi);

  // 3. TranslateAndExpand should;
  //   a. Translate lite term ids to main term ids based on the map
  //   b. Expand 'foot' to have a hit for 'foo'
  //   c. Keep both the exact hit for 'foo' and the prefix hit for 'foot', the
  //   latter with term frequency as the sum of the term frequencies.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermIdHitPair> expanded_term_id_hit_pairs,
      MainIndexMerger::TranslateAndExpandLiteHits(*lite_index_, *term_id_codec_,
                                                  lexicon_outputs));
  EXPECT_THAT(
      expanded_term_id_hit_pairs,
      UnorderedElementsAre(TermIdHitPair(foot_main_term_id, foot_doc0_hit),
                           TermIdHitPair(foo_main_term_id, foo_doc0_hit),
                           TermIdHitPair(foo_main_term_id, doc0_prefix_hit)));
}

TEST_F(MainIndexMergerTest, DedupeWithExactSameTermFrequencies) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foot" "foo" is_in_prefix_section=TRUE}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_term_id,
      term_id_codec_->EncodeTvi(foot_tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_tvi,
      lite_index_->InsertTerm("foo", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t foo_term_id,
                             term_id_codec_->EncodeTvi(foo_tvi, TviType::LITE));

  Hit foot_doc0_hit(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/57,
                    /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, foot_doc0_hit));
  Hit foo_doc0_hit(/*section_id=*/0, /*document_id=*/0, /*term_frequency=*/57,
                   /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foo_term_id, foo_doc0_hit));
  // The prefix hit should take the sum as term_frequency - 114.
  Hit prefix_foo_doc0_hit(/*section_id=*/0, /*document_id=*/0,
                          /*term_frequency=*/114,
                          /*is_in_prefix_section=*/true,
                          /*is_prefix_hit=*/true);

  // 2. Build up a fake LexiconMergeOutputs
  // This is some made up number that doesn't matter for this test.
  uint32_t foo_main_tvi = 12;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_main_term_id,
      term_id_codec_->EncodeTvi(foo_main_tvi, TviType::MAIN));

  uint32_t foot_main_tvi = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_main_term_id,
      term_id_codec_->EncodeTvi(foot_main_tvi, TviType::MAIN));

  MainIndex::LexiconMergeOutputs lexicon_outputs;
  // Map "foot" to it's prefix hit for "foo".
  lexicon_outputs.other_tvi_to_prefix_main_tvis.emplace(foot_tvi,
                                                        std::make_pair(0, 1));
  lexicon_outputs.prefix_tvis_buf.push_back(foo_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foot_tvi, foot_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foo_tvi, foo_main_tvi);

  // 3. TranslateAndExpand should;
  //   a. Translate lite term ids to main term ids based on the map
  //   b. Expand 'foot' to have a hit for 'foo'
  //   c. Keep both the exact hit for 'foo' and the prefix hit for 'foot', the
  //   latter with term frequency as the sum of the term frequencies.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermIdHitPair> expanded_term_id_hit_pairs,
      MainIndexMerger::TranslateAndExpandLiteHits(*lite_index_, *term_id_codec_,
                                                  lexicon_outputs));
  EXPECT_THAT(expanded_term_id_hit_pairs,
              UnorderedElementsAre(
                  TermIdHitPair(foot_main_term_id, foot_doc0_hit),
                  TermIdHitPair(foo_main_term_id, foo_doc0_hit),
                  TermIdHitPair(foo_main_term_id, prefix_foo_doc0_hit)));
}

TEST_F(MainIndexMergerTest, DedupePrefixExpansion) {
  // 1. Index one doc in the Lite Index:
  // - Doc0 {"foot" "fool" is_in_prefix_section=TRUE}
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_tvi,
      lite_index_->InsertTerm("foot", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_term_id,
      term_id_codec_->EncodeTvi(foot_tvi, TviType::LITE));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_tvi,
      lite_index_->InsertTerm("fool", TermMatchType::PREFIX, kNamespace0));
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_term_id,
      term_id_codec_->EncodeTvi(fool_tvi, TviType::LITE));

  Hit foot_doc0_hit(/*section_id=*/0, /*document_id=*/0,
                    /*term_frequency=*/Hit::kMaxTermFrequency,
                    /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(foot_term_id, foot_doc0_hit));
  Hit fool_doc0_hit(/*section_id=*/0, /*document_id=*/0,
                    Hit::kDefaultTermFrequency,
                    /*is_in_prefix_section=*/true);
  ICING_ASSERT_OK(lite_index_->AddHit(fool_term_id, fool_doc0_hit));

  // 2. Build up a fake LexiconMergeOutputs
  // This is some made up number that doesn't matter for this test.
  uint32_t foo_main_tvi = 12;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foo_term_id,
      term_id_codec_->EncodeTvi(foo_main_tvi, TviType::MAIN));
  // The prefix hit should take the sum as term frequency - 256, capped at
  // kMaxTermFrequency.
  Hit doc0_prefix_hit(/*section_id=*/0, /*document_id=*/0,
                      /*term_frequency=*/Hit::kMaxTermFrequency,
                      /*is_in_prefix_section=*/true, /*is_prefix_hit=*/true);

  uint32_t foot_main_tvi = 5;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t foot_main_term_id,
      term_id_codec_->EncodeTvi(foot_main_tvi, TviType::MAIN));
  uint32_t fool_main_tvi = 10;
  ICING_ASSERT_OK_AND_ASSIGN(
      uint32_t fool_main_term_id,
      term_id_codec_->EncodeTvi(fool_main_tvi, TviType::MAIN));

  MainIndex::LexiconMergeOutputs lexicon_outputs;
  // Map "fool" to it's prefix hit for "foo" and "foot" to it's prefix hit for
  // "foo".
  lexicon_outputs.other_tvi_to_prefix_main_tvis.emplace(fool_tvi,
                                                        std::make_pair(0, 1));
  lexicon_outputs.prefix_tvis_buf.push_back(foo_main_tvi);
  lexicon_outputs.other_tvi_to_prefix_main_tvis.emplace(foot_tvi,
                                                        std::make_pair(1, 1));
  lexicon_outputs.prefix_tvis_buf.push_back(foo_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(foot_tvi, foot_main_tvi);
  lexicon_outputs.other_tvi_to_main_tvi.emplace(fool_tvi, fool_main_tvi);

  // 3. TranslateAndExpand should;
  //   a. Translate lite term ids to main term ids based on the map
  //   b. Expand 'foot' and 'fool' to have hits for 'foo'
  //   c. Merge the prefix hits from 'foot' and 'fool', taking the sum as
  //      term frequency.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<TermIdHitPair> expanded_term_id_hit_pairs,
      MainIndexMerger::TranslateAndExpandLiteHits(*lite_index_, *term_id_codec_,
                                                  lexicon_outputs));
  EXPECT_THAT(
      expanded_term_id_hit_pairs,
      UnorderedElementsAre(TermIdHitPair(foot_main_term_id, foot_doc0_hit),
                           TermIdHitPair(fool_main_term_id, fool_doc0_hit),
                           TermIdHitPair(foo_term_id, doc0_prefix_hit)));
}

}  // namespace

}  // namespace lib
}  // namespace icing
