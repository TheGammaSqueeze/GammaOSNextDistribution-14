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

#include "icing/legacy/index/icing-dynamic-trie.h"

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/hash/farmhash.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/random-string.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace {

using testing::ContainerEq;
using testing::ElementsAre;
using testing::StrEq;

constexpr std::string_view kKeys[] = {
    "", "ab", "ac", "abd", "bac", "bb", "bacd", "abbb", "abcdefg",
};
constexpr uint32_t kNumKeys = ABSL_ARRAYSIZE(kKeys);

class IcingDynamicTrieTest : public ::testing::Test {
 protected:
  // Output trie stats to stderr.
  static void StatsDump(const IcingDynamicTrie& trie) {
    IcingDynamicTrie::Stats stats;
    trie.CollectStats(&stats);
    DLOG(INFO) << "Stats:\n" << stats.DumpStats(true);
  }

  static void AddToTrie(IcingDynamicTrie* trie, uint32_t num_keys) {
    std::string key;
    for (uint32_t i = 0; i < kNumKeys; i++) {
      key.clear();
      IcingStringUtil::SStringAppendF(&key, 0, "%u+%010u", i % 2, i);
      ASSERT_THAT(trie->Insert(key.c_str(), &i), IsOk());
    }
  }

  static void CheckTrie(const IcingDynamicTrie& trie, uint32_t num_keys) {
    std::string key;
    for (uint32_t i = 0; i < kNumKeys; i++) {
      key.clear();
      IcingStringUtil::SStringAppendF(&key, 0, "%u+%010u", i % 2, i);
      uint32_t val;
      bool found = trie.Find(key.c_str(), &val);
      EXPECT_TRUE(found);
      EXPECT_EQ(i, val);
    }
  }

  static void PrintTrie(const IcingDynamicTrie& trie) {
    std::vector<std::string> keys;
    std::ostringstream os;
    DLOG(INFO) << "Trie:\n";
    trie.DumpTrie(&os, &keys);
    DLOG(INFO) << os.str();
  }

  void SetUp() override {
    trie_files_dir_ = GetTestTempDir() + "/trie_files";
    trie_files_prefix_ = trie_files_dir_ + "/test_file_";
  }

  void TearDown() override {
    IcingFilesystem filesystem;
    filesystem.DeleteDirectoryRecursively(trie_files_dir_.c_str());
  }

  std::string trie_files_dir_;
  std::string trie_files_prefix_;
};

std::vector<std::pair<std::string, int>> RetrieveKeyValuePairs(
    IcingDynamicTrie::Iterator& term_iter) {
  std::vector<std::pair<std::string, int>> key_value;
  for (; term_iter.IsValid(); term_iter.Advance()) {
    uint32_t val;
    memcpy(&val, term_iter.GetValue(), sizeof(val));
    key_value.push_back(std::make_pair(term_iter.GetKey(), val));
  }
  return key_value;
}

constexpr std::string_view kCommonEnglishWords[] = {
    "that", "was",  "for",  "on",   "are",  "with",  "they", "be",    "at",
    "one",  "have", "this", "from", "word", "but",   "what", "some",  "you",
    "had",  "the",  "and",  "can",  "out",  "other", "were", "which", "their",
    "time", "will", "how",  "said", "each", "tell",  "may",  "three"};
constexpr uint32_t kCommonEnglishWordArrayLen =
    sizeof(kCommonEnglishWords) / sizeof(std::string_view);

TEST_F(IcingDynamicTrieTest, Simple) {
  // Test simple key insertions.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  for (uint32_t i = 0; i < kNumKeys; i++) {
    ASSERT_THAT(trie.Insert(kKeys[i].data(), &i), IsOk());

    uint32_t val;
    bool found = trie.Find(kKeys[i].data(), &val);
    EXPECT_TRUE(found) << kKeys[i];
    if (found) EXPECT_EQ(i, val) << kKeys[i] << " " << val;
  }

  EXPECT_EQ(trie.size(), kNumKeys);

  StatsDump(trie);
  std::vector<std::string> keys;
  std::ostringstream os;
  DLOG(INFO) << "Trie:\n";
  trie.DumpTrie(&os, &keys);
  DLOG(INFO) << os.str();
  EXPECT_EQ(keys.size(), kNumKeys);
}

TEST_F(IcingDynamicTrieTest, Init) {
  // Test create/init behavior.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  EXPECT_FALSE(trie.is_initialized());
  EXPECT_FALSE(trie.Init());

  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  EXPECT_TRUE(trie.Init());
  EXPECT_TRUE(trie.is_initialized());
}

TEST_F(IcingDynamicTrieTest, Iterator) {
  // Test iterator.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  for (uint32_t i = 0; i < kNumKeys; i++) {
    ASSERT_THAT(trie.Insert(kKeys[i].data(), &i), IsOk());
  }

  // Should get the entire trie.
  std::vector<std::pair<std::string, int>> exp_key_values = {
      {"", 0},   {"ab", 1},  {"abbb", 7}, {"abcdefg", 8}, {"abd", 3},
      {"ac", 2}, {"bac", 4}, {"bacd", 6}, {"bb", 5}};
  IcingDynamicTrie::Iterator it_all(trie, "");
  std::vector<std::pair<std::string, int>> key_values =
      RetrieveKeyValuePairs(it_all);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it_all.Reset();
  key_values = RetrieveKeyValuePairs(it_all);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Get everything under "a".
  exp_key_values = {
      {"ab", 1}, {"abbb", 7}, {"abcdefg", 8}, {"abd", 3}, {"ac", 2}};
  IcingDynamicTrie::Iterator it1(trie, "a");
  key_values = RetrieveKeyValuePairs(it1);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it1.Reset();
  key_values = RetrieveKeyValuePairs(it1);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Now "b".
  exp_key_values = {{"bac", 4}, {"bacd", 6}, {"bb", 5}};
  IcingDynamicTrie::Iterator it2(trie, "b");
  key_values = RetrieveKeyValuePairs(it2);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it2.Reset();
  key_values = RetrieveKeyValuePairs(it2);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Get everything under "ab".
  exp_key_values = {{"ab", 1}, {"abbb", 7}, {"abcdefg", 8}, {"abd", 3}};
  IcingDynamicTrie::Iterator it3(trie, "ab");
  key_values = RetrieveKeyValuePairs(it3);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it3.Reset();
  key_values = RetrieveKeyValuePairs(it3);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should match only one key exactly.
  constexpr std::string_view kOneMatch[] = {
      "abd",
      "abcd",
      "abcdef",
      "abcdefg",
  };
  // With the following match:
  constexpr std::string_view kOneMatchMatched[] = {
      "abd",
      "abcdefg",
      "abcdefg",
      "abcdefg",
  };

  for (size_t k = 0; k < ABSL_ARRAYSIZE(kOneMatch); k++) {
    IcingDynamicTrie::Iterator it_single(trie, kOneMatch[k].data());
    ASSERT_TRUE(it_single.IsValid()) << kOneMatch[k];
    EXPECT_THAT(it_single.GetKey(), StrEq(kOneMatchMatched[k].data()));
    EXPECT_FALSE(it_single.Advance()) << kOneMatch[k];
    EXPECT_FALSE(it_single.IsValid()) << kOneMatch[k];

    // Should get same results after calling Reset
    it_single.Reset();
    ASSERT_TRUE(it_single.IsValid()) << kOneMatch[k];
    EXPECT_THAT(it_single.GetKey(), StrEq(kOneMatchMatched[k].data()));
    EXPECT_FALSE(it_single.Advance()) << kOneMatch[k];
    EXPECT_FALSE(it_single.IsValid()) << kOneMatch[k];
  }

  // Matches nothing.
  constexpr std::string_view kNoMatch[] = {
      "abbd",
      "abcdeg",
      "abcdefh",
  };
  for (size_t k = 0; k < ABSL_ARRAYSIZE(kNoMatch); k++) {
    IcingDynamicTrie::Iterator it_empty(trie, kNoMatch[k].data());
    EXPECT_FALSE(it_empty.IsValid());
    it_empty.Reset();
    EXPECT_FALSE(it_empty.IsValid());
  }

  // Clear.
  trie.Clear();
  EXPECT_FALSE(IcingDynamicTrie::Iterator(trie, "").IsValid());
  EXPECT_EQ(0u, trie.size());
  EXPECT_EQ(1.0, trie.min_free_fraction());
}

TEST_F(IcingDynamicTrieTest, IteratorReverse) {
  // Test iterator.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  for (uint32_t i = 0; i < kNumKeys; i++) {
    ASSERT_THAT(trie.Insert(kKeys[i].data(), &i), IsOk());
  }

  // Should get the entire trie.
  std::vector<std::pair<std::string, int>> exp_key_values = {
      {"bb", 5},      {"bacd", 6}, {"bac", 4}, {"ac", 2}, {"abd", 3},
      {"abcdefg", 8}, {"abbb", 7}, {"ab", 1},  {"", 0}};
  IcingDynamicTrie::Iterator it_all(trie, "", /*reverse=*/true);
  std::vector<std::pair<std::string, int>> key_values =
      RetrieveKeyValuePairs(it_all);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));
  it_all.Reset();
  key_values = RetrieveKeyValuePairs(it_all);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Get everything under "a".
  exp_key_values = {
      {"ac", 2}, {"abd", 3}, {"abcdefg", 8}, {"abbb", 7}, {"ab", 1}};
  IcingDynamicTrie::Iterator it1(trie, "a", /*reverse=*/true);
  key_values = RetrieveKeyValuePairs(it1);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it1.Reset();
  key_values = RetrieveKeyValuePairs(it1);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Now "b".
  exp_key_values = {{"bb", 5}, {"bacd", 6}, {"bac", 4}};
  IcingDynamicTrie::Iterator it2(trie, "b", /*reverse=*/true);
  key_values = RetrieveKeyValuePairs(it2);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it2.Reset();
  key_values = RetrieveKeyValuePairs(it2);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Get everything under "ab".
  exp_key_values = {{"abd", 3}, {"abcdefg", 8}, {"abbb", 7}, {"ab", 1}};
  IcingDynamicTrie::Iterator it3(trie, "ab", /*reverse=*/true);
  key_values = RetrieveKeyValuePairs(it3);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should get same results after calling Reset
  it3.Reset();
  key_values = RetrieveKeyValuePairs(it3);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Should match only one key exactly.
  constexpr std::string_view kOneMatch[] = {
      "abd",
      "abcd",
      "abcdef",
      "abcdefg",
  };
  // With the following match:
  constexpr std::string_view kOneMatchMatched[] = {
      "abd",
      "abcdefg",
      "abcdefg",
      "abcdefg",
  };

  for (size_t k = 0; k < ABSL_ARRAYSIZE(kOneMatch); k++) {
    IcingDynamicTrie::Iterator it_single(trie, kOneMatch[k].data(),
                                         /*reverse=*/true);
    ASSERT_TRUE(it_single.IsValid()) << kOneMatch[k];
    EXPECT_THAT(it_single.GetKey(), StrEq(kOneMatchMatched[k].data()));
    EXPECT_FALSE(it_single.Advance()) << kOneMatch[k];
    EXPECT_FALSE(it_single.IsValid()) << kOneMatch[k];

    // Should get same results after calling Reset
    it_single.Reset();
    ASSERT_TRUE(it_single.IsValid()) << kOneMatch[k];
    EXPECT_THAT(it_single.GetKey(), StrEq(kOneMatchMatched[k].data()));
    EXPECT_FALSE(it_single.Advance()) << kOneMatch[k];
    EXPECT_FALSE(it_single.IsValid()) << kOneMatch[k];
  }

  // Matches nothing.
  constexpr std::string_view kNoMatch[] = {
      "abbd",
      "abcdeg",
      "abcdefh",
  };
  for (size_t k = 0; k < ABSL_ARRAYSIZE(kNoMatch); k++) {
    IcingDynamicTrie::Iterator it_empty(trie, kNoMatch[k].data(),
                                        /*reverse=*/true);
    EXPECT_FALSE(it_empty.IsValid());
    it_empty.Reset();
    EXPECT_FALSE(it_empty.IsValid());
  }

  // Clear.
  trie.Clear();
  EXPECT_FALSE(
      IcingDynamicTrie::Iterator(trie, "", /*reverse=*/true).IsValid());
  EXPECT_EQ(0u, trie.size());
  EXPECT_EQ(1.0, trie.min_free_fraction());
}

TEST_F(IcingDynamicTrieTest, IteratorLoadTest) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  std::default_random_engine random;
  ICING_LOG(ERROR) << "Seed: " << std::default_random_engine::default_seed;

  std::vector<std::pair<std::string, int>> exp_key_values;
  // Randomly generate 1024 terms.
  for (int i = 0; i < 1024; ++i) {
    std::string term = RandomString("abcdefg", 5, &random) + std::to_string(i);
    ASSERT_THAT(trie.Insert(term.c_str(), &i), IsOk());
    exp_key_values.push_back(std::make_pair(term, i));
  }
  // Lexicographically sort the expected keys.
  std::sort(exp_key_values.begin(), exp_key_values.end());

  // Check that the iterator works.
  IcingDynamicTrie::Iterator term_iter(trie, /*prefix=*/"");
  std::vector<std::pair<std::string, int>> key_values =
      RetrieveKeyValuePairs(term_iter);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Check that Reset works.
  term_iter.Reset();
  key_values = RetrieveKeyValuePairs(term_iter);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  std::reverse(exp_key_values.begin(), exp_key_values.end());
  // Check that the reverse iterator works.
  IcingDynamicTrie::Iterator term_iter_reverse(trie, /*prefix=*/"",
                                               /*reverse=*/true);
  key_values = RetrieveKeyValuePairs(term_iter_reverse);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));

  // Check that Reset works.
  term_iter_reverse.Reset();
  key_values = RetrieveKeyValuePairs(term_iter_reverse);
  EXPECT_THAT(key_values, ContainerEq(exp_key_values));
}

TEST_F(IcingDynamicTrieTest, Persistence) {
  // Test persistence on the English dictionary.
  IcingFilesystem filesystem;
  {
    // Test with a trie including strings in words. Test will fail if
    // words are not unique.
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    EXPECT_FALSE(trie.Init());
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
    ASSERT_TRUE(trie.Init());

    for (uint32_t i = 0; i < kCommonEnglishWordArrayLen; i++) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &i), IsOk());
    }
    // Explicitly omit sync.

    StatsDump(trie);
  }

  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Init());
    EXPECT_EQ(0U, trie.size());

    for (uint32_t i = 0; i < kCommonEnglishWordArrayLen; i++) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &i), IsOk());
    }
    trie.Sync();

    StatsDump(trie);
  }

  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Init());

    // Make sure we can find everything with the right value.
    uint32_t found_count = 0;
    uint32_t matched_count = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen; i++) {
      uint32_t val;
      bool found = trie.Find(kCommonEnglishWords[i].data(), &val);
      if (found) {
        found_count++;
        if (i == val) {
          matched_count++;
        }
      }
    }
    EXPECT_EQ(found_count, kCommonEnglishWordArrayLen);
    EXPECT_EQ(matched_count, kCommonEnglishWordArrayLen);

    StatsDump(trie);
  }
}

TEST_F(IcingDynamicTrieTest, PersistenceShared) {
  // Test persistence on the English dictionary.
  IcingFilesystem filesystem;
  IcingDynamicTrie::RuntimeOptions ropt;

  {
    // Test with a trie including strings in words. Test will fail if
    // words are not unique.
    ropt.storage_policy = IcingDynamicTrie::RuntimeOptions::kMapSharedWithCrc;
    IcingDynamicTrie trie(trie_files_prefix_, ropt, &filesystem);
    EXPECT_FALSE(trie.Init());
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
    ASSERT_TRUE(trie.Init());

    uint32_t next_reopen = kCommonEnglishWordArrayLen / 16;
    for (uint32_t i = 0; i < kCommonEnglishWordArrayLen; i++) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &i), IsOk());

      if (i == next_reopen) {
        ASSERT_NE(0u, trie.UpdateCrc());
        trie.Close();
        ASSERT_TRUE(trie.Init());

        next_reopen += next_reopen / 2;
      }
    }
    // Explicitly omit sync. Shared should automatically persist.

    StatsDump(trie);
  }

  // Go back and forth between the two policies.
  for (int i = 0; i < 5; i++) {
    if (i % 2 == 0) {
      DLOG(INFO) << "Opening with map shared";
      ropt.storage_policy = IcingDynamicTrie::RuntimeOptions::kMapSharedWithCrc;
    } else {
      DLOG(INFO) << "Opening with explicit flush";
      ropt.storage_policy = IcingDynamicTrie::RuntimeOptions::kExplicitFlush;
    }
    IcingDynamicTrie trie(trie_files_prefix_, ropt, &filesystem);
    ASSERT_TRUE(trie.Init());

    // Make sure we can find everything with the right value.
    uint32_t found_count = 0;
    uint32_t matched_count = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen; i++) {
      uint32_t val;
      bool found = trie.Find(kCommonEnglishWords[i].data(), &val);
      if (found) {
        found_count++;
        if (i == val) {
          matched_count++;
        }
      }
    }
    EXPECT_EQ(found_count, kCommonEnglishWordArrayLen);
    EXPECT_EQ(matched_count, kCommonEnglishWordArrayLen);

    StatsDump(trie);
  }

  // Clear and re-open.
  ropt.storage_policy = IcingDynamicTrie::RuntimeOptions::kMapSharedWithCrc;
  IcingDynamicTrie trie(trie_files_prefix_, ropt, &filesystem);
  ASSERT_TRUE(trie.Init());
  trie.Clear();
  trie.Close();
  ASSERT_TRUE(trie.Init());
}

TEST_F(IcingDynamicTrieTest, Sync) {
  IcingFilesystem filesystem;
  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
    ASSERT_TRUE(trie.Init());

    for (uint32_t i = 0; i < kNumKeys; i++) {
      ASSERT_THAT(trie.Insert(kKeys[i].data(), &i), IsOk());

      uint32_t val;
      bool found = trie.Find(kKeys[i].data(), &val);
      EXPECT_TRUE(found) << kKeys[i];
      if (found) EXPECT_EQ(i, val) << kKeys[i] << " " << val;
    }

    StatsDump(trie);
    PrintTrie(trie);

    trie.Sync();

    for (uint32_t i = 0; i < kNumKeys; i++) {
      uint32_t val;
      bool found = trie.Find(kKeys[i].data(), &val);
      EXPECT_TRUE(found) << kKeys[i];
      if (found) EXPECT_EQ(i, val) << kKeys[i] << " " << val;
    }
  }

  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Init());

    for (uint32_t i = 0; i < kNumKeys; i++) {
      uint32_t val;
      bool found = trie.Find(kKeys[i].data(), &val);
      EXPECT_TRUE(found) << kKeys[i];
      if (found) EXPECT_EQ(i, val) << kKeys[i] << " " << val;
    }

    StatsDump(trie);
    PrintTrie(trie);
  }
}

TEST_F(IcingDynamicTrieTest, LimitsZero) {
  // Don't crash if we set limits to 0.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_FALSE(trie.CreateIfNotExist(IcingDynamicTrie::Options(0, 0, 0, 0)));
}

TEST_F(IcingDynamicTrieTest, LimitsSmall) {
  // Test limits with a few keys.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(
      IcingDynamicTrie::Options(10, 300, 30, sizeof(uint32_t))));
  ASSERT_TRUE(trie.Init());

  ASSERT_LT(3U, kNumKeys);

  for (uint32_t i = 0; i < 3; i++) {
    ASSERT_THAT(trie.Insert(kKeys[i].data(), &i), IsOk()) << i;

    uint32_t val;
    bool found = trie.Find(kKeys[i].data(), &val);
    EXPECT_TRUE(found) << kKeys[i];
    if (found) EXPECT_EQ(i, val) << kKeys[i] << " " << val;
  }

  uint32_t val = 3;
  EXPECT_THAT(trie.Insert(kKeys[3].data(), &val),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  StatsDump(trie);
  PrintTrie(trie);
}

TEST_F(IcingDynamicTrieTest, DISABLEDFingerprintedKeys) {
  IcingFilesystem filesystem;
  IcingDynamicTrie::Options options(4 << 20, 4 << 20, 20 << 20,
                                    sizeof(uint32_t));
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(options));
  ASSERT_TRUE(trie.Init());
  IcingDynamicTrie triefp(trie_files_prefix_ + ".fps",
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
  ASSERT_TRUE(triefp.CreateIfNotExist(options));
  ASSERT_TRUE(triefp.Init());

  static const uint32_t kNumKeys = 1000000;
  std::string key;
  for (uint32_t i = 0; i < kNumKeys; i++) {
    key.clear();
    IcingStringUtil::SStringAppendF(
        &key, 1000, "content://gmail-ls/account/conversation/%u/message/%u", i,
        10 * i);
    ASSERT_THAT(trie.Insert(key.c_str(), &i), IsOk());

    // Now compute a fingerprint.
    uint64_t fpkey = tc3farmhash::Fingerprint64(key);

    // Convert to base255 since keys in trie cannot contain 0.
    uint8_t fpkey_base255[9];
    for (int j = 0; j < 8; j++) {
      fpkey_base255[j] = (fpkey % 255) + 1;
      fpkey /= 255;
    }
    fpkey_base255[8] = '\0';
    ASSERT_THAT(triefp.Insert(reinterpret_cast<const char*>(fpkey_base255), &i),
                IsOk());

    // Sync periodically to gauge write locality.
    if ((i + 1) % (kNumKeys / 10) == 0) {
      DLOG(INFO) << "Trie sync";
      trie.Sync();
      DLOG(INFO) << "Trie fp sync";
      triefp.Sync();
    }
  }

  DLOG(INFO) << "Trie stats";
  StatsDump(trie);
  DLOG(INFO) << "Trie fp stats";
  StatsDump(triefp);
}

TEST_F(IcingDynamicTrieTest, AddDups) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  static const uint32_t kNumKeys = 5000;
  AddToTrie(&trie, kNumKeys);
  CheckTrie(trie, kNumKeys);

  DLOG(INFO) << "Trie stats";
  StatsDump(trie);

  AddToTrie(&trie, kNumKeys);
  CheckTrie(trie, kNumKeys);
  DLOG(INFO) << "Trie stats";
  StatsDump(trie);
}

TEST_F(IcingDynamicTrieTest, Properties) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  static const uint32_t kOne = 1;
  uint32_t val_idx;
  ICING_ASSERT_OK(trie.Insert("abcd", &kOne, &val_idx, false));
  trie.SetProperty(val_idx, 0);
  trie.SetProperty(val_idx, 3);

  {
    IcingDynamicTrie::PropertyReader reader(trie, 3);
    ASSERT_TRUE(reader.Exists());
    EXPECT_TRUE(reader.HasProperty(val_idx));
    EXPECT_FALSE(reader.HasProperty(1000));
  }

  // Disappear after close.
  trie.Close();
  ASSERT_TRUE(trie.Init());
  {
    IcingDynamicTrie::PropertyReader reader(trie, 3);
    EXPECT_FALSE(reader.HasProperty(val_idx));
  }

  // Persist after sync.
  ICING_ASSERT_OK(trie.Insert("abcd", &kOne, &val_idx, false));
  trie.SetProperty(val_idx, 1);
  ASSERT_TRUE(trie.Sync());
  trie.Close();
  ASSERT_TRUE(trie.Init());

  uint32_t val;
  ASSERT_TRUE(trie.Find("abcd", &val, &val_idx));
  EXPECT_EQ(1u, val);
  {
    IcingDynamicTrie::PropertyReader reader(trie, 1);
    EXPECT_TRUE(reader.HasProperty(val_idx));
  }

  // Get all.
  {
    IcingDynamicTrie::PropertyReadersAll readers(trie);
    ASSERT_EQ(4u, readers.size());
    EXPECT_TRUE(readers.Exists(0));
    EXPECT_TRUE(readers.Exists(1));
    EXPECT_FALSE(readers.Exists(2));
    EXPECT_TRUE(readers.Exists(3));
  }
}

TEST_F(IcingDynamicTrieTest, ClearSingleProperty) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  static const uint32_t kOne = 1;
  uint32_t val_idx[3];
  ICING_ASSERT_OK(trie.Insert("abcd", &kOne, &val_idx[0], false));
  trie.SetProperty(val_idx[0], 0);
  trie.SetProperty(val_idx[0], 3);

  ICING_ASSERT_OK(trie.Insert("efgh", &kOne, &val_idx[1], false));
  trie.SetProperty(val_idx[1], 0);
  trie.SetProperty(val_idx[1], 3);

  ICING_ASSERT_OK(trie.Insert("ijkl", &kOne, &val_idx[2], false));
  trie.SetProperty(val_idx[2], 0);
  trie.SetProperty(val_idx[2], 3);

  {
    IcingDynamicTrie::PropertyReadersAll readers(trie);
    ASSERT_EQ(4u, readers.size());
    EXPECT_TRUE(readers.Exists(0));
    EXPECT_FALSE(readers.Exists(1));
    EXPECT_FALSE(readers.Exists(2));
    EXPECT_TRUE(readers.Exists(3));
    for (size_t i = 0; i < readers.size(); i++) {
      if (readers.Exists(i)) {
        for (size_t j = 0; j < sizeof(val_idx) / sizeof(uint32_t); ++j) {
          EXPECT_TRUE(readers.HasProperty(i, val_idx[j]));
        }
      }
    }
  }

  EXPECT_TRUE(trie.ClearPropertyForAllValues(3));

  {
    IcingDynamicTrie::PropertyReadersAll readers(trie);
    ASSERT_EQ(4u, readers.size());
    EXPECT_TRUE(readers.Exists(0));
    EXPECT_FALSE(readers.Exists(1));
    EXPECT_FALSE(readers.Exists(2));
    // Clearing the property causes all values to be deleted.
    EXPECT_FALSE(readers.Exists(3));
    for (size_t i = 0; i < readers.size(); i++) {
      for (size_t j = 0; j < sizeof(val_idx) / sizeof(uint32_t); ++j) {
        if (i == 0) {
          EXPECT_TRUE(readers.HasProperty(i, val_idx[j]));
        } else {
          EXPECT_FALSE(readers.HasProperty(i, val_idx[j]));
        }
      }
    }
  }
}

TEST_F(IcingDynamicTrieTest, DeletionShouldWorkWhenRootIsLeaf) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts a key, the root is a leaf.
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("foo", &value), IsOk());
  ASSERT_TRUE(trie.Find("foo", &value));

  // Deletes the key.
  EXPECT_TRUE(trie.Delete("foo"));
  EXPECT_FALSE(trie.Find("foo", &value));
}

TEST_F(IcingDynamicTrieTest, DeletionShouldWorkWhenLastCharIsLeaf) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts "bar" and "ba", the trie structure looks like:
  //       root
  //         |
  //         b
  //         |
  //         a
  //        / \
  //     null  r
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_THAT(trie.Insert("ba", &value), IsOk());
  ASSERT_TRUE(trie.Find("bar", &value));
  ASSERT_TRUE(trie.Find("ba", &value));

  // Deletes "bar". "r" is a leaf node in the trie.
  EXPECT_TRUE(trie.Delete("bar"));
  EXPECT_FALSE(trie.Find("bar", &value));
  EXPECT_TRUE(trie.Find("ba", &value));
}

TEST_F(IcingDynamicTrieTest, DeletionShouldWorkWithTerminationNode) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts "bar" and "ba", the trie structure looks like:
  //       root
  //         |
  //         b
  //         |
  //         a
  //        / \
  //     null  r
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_THAT(trie.Insert("ba", &value), IsOk());
  ASSERT_TRUE(trie.Find("bar", &value));
  ASSERT_TRUE(trie.Find("ba", &value));

  // Deletes "ba" which is a key with termination node in the trie.
  EXPECT_TRUE(trie.Delete("ba"));
  EXPECT_FALSE(trie.Find("ba", &value));
  EXPECT_TRUE(trie.Find("bar", &value));
}

TEST_F(IcingDynamicTrieTest, DeletionShouldWorkWithMultipleNexts) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts "ba", "bb", "bc", and "bd", the trie structure looks like:
  //       root
  //         |
  //         b
  //      / | | \
  //     a  b c  d
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("ba", &value), IsOk());
  ASSERT_THAT(trie.Insert("bb", &value), IsOk());
  ASSERT_THAT(trie.Insert("bc", &value), IsOk());
  ASSERT_THAT(trie.Insert("bd", &value), IsOk());
  ASSERT_TRUE(trie.Find("ba", &value));
  ASSERT_TRUE(trie.Find("bb", &value));
  ASSERT_TRUE(trie.Find("bc", &value));
  ASSERT_TRUE(trie.Find("bd", &value));

  // Deletes "bc".
  EXPECT_TRUE(trie.Delete("bc"));
  EXPECT_FALSE(trie.Find("bc", &value));
  EXPECT_TRUE(trie.Find("ba", &value));
  EXPECT_TRUE(trie.Find("bb", &value));
  EXPECT_TRUE(trie.Find("bd", &value));
}

TEST_F(IcingDynamicTrieTest, DeletionShouldWorkWithMultipleTrieBranches) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts "batter", "battle", and "bar", the trie structure looks like:
  //       root
  //         |
  //         b
  //         |
  //         a
  //        / \
  //       t   r
  //       |
  //       t
  //      / \
  //     e   l
  //     |   |
  //     r   e
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("batter", &value), IsOk());
  ASSERT_THAT(trie.Insert("battle", &value), IsOk());
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_TRUE(trie.Find("batter", &value));
  ASSERT_TRUE(trie.Find("battle", &value));
  ASSERT_TRUE(trie.Find("bar", &value));

  // Deletes "batter".
  EXPECT_TRUE(trie.Delete("batter"));
  EXPECT_FALSE(trie.Find("batter", &value));
  EXPECT_TRUE(trie.Find("battle", &value));
  EXPECT_TRUE(trie.Find("bar", &value));
}

TEST_F(IcingDynamicTrieTest, InsertionShouldWorkAfterDeletion) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts some keys.
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_THAT(trie.Insert("bed", &value), IsOk());
  ASSERT_THAT(trie.Insert("foo", &value), IsOk());

  // Deletes a key
  ASSERT_TRUE(trie.Delete("bed"));
  ASSERT_FALSE(trie.Find("bed", &value));

  // Inserts after deletion
  ASSERT_THAT(trie.Insert("bed", &value), IsOk());
  ASSERT_THAT(trie.Insert("bedroom", &value), IsOk());
  EXPECT_TRUE(trie.Find("bed", &value));
  EXPECT_TRUE(trie.Find("bedroom", &value));
}

TEST_F(IcingDynamicTrieTest, IteratorShouldWorkAfterDeletion) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts some keys.
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_THAT(trie.Insert("bed", &value), IsOk());
  ASSERT_THAT(trie.Insert("foo", &value), IsOk());

  // Deletes a key
  ASSERT_TRUE(trie.Delete("bed"));

  // Iterates through all keys
  IcingDynamicTrie::Iterator iterator_all(trie, "");
  std::vector<std::string> results;
  for (; iterator_all.IsValid(); iterator_all.Advance()) {
    results.emplace_back(iterator_all.GetKey());
  }
  EXPECT_THAT(results, ElementsAre("bar", "foo"));

  // Iterates through keys that start with "b"
  IcingDynamicTrie::Iterator iterator_b(trie, "b");
  results.clear();
  for (; iterator_b.IsValid(); iterator_b.Advance()) {
    results.emplace_back(iterator_b.GetKey());
  }
  EXPECT_THAT(results, ElementsAre("bar"));
}

TEST_F(IcingDynamicTrieTest, DeletingNonExistingKeyShouldReturnTrue) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  // Inserts some keys.
  uint32_t value = 1;
  ASSERT_THAT(trie.Insert("bar", &value), IsOk());
  ASSERT_THAT(trie.Insert("bed", &value), IsOk());

  // "ba" and bedroom are not keys in the trie.
  EXPECT_TRUE(trie.Delete("ba"));
  EXPECT_TRUE(trie.Delete("bedroom"));

  // The original keys are not affected.
  EXPECT_TRUE(trie.Find("bar", &value));
  EXPECT_TRUE(trie.Find("bed", &value));
}

TEST_F(IcingDynamicTrieTest, DeletionResortsFullNextArray) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  uint32_t value = 1;
  // 'f' -> [ 'a', 'j', 'o', 'u' ]
  ASSERT_THAT(trie.Insert("foul", &value), IsOk());
  ASSERT_THAT(trie.Insert("far", &value), IsOk());
  ASSERT_THAT(trie.Insert("fudge", &value), IsOk());
  ASSERT_THAT(trie.Insert("fjord", &value), IsOk());

  // Delete the third child
  EXPECT_TRUE(trie.Delete("foul"));

  std::vector<std::string> remaining;
  for (IcingDynamicTrie::Iterator term_iter(trie, /*prefix=*/"");
       term_iter.IsValid(); term_iter.Advance()) {
    remaining.push_back(term_iter.GetKey());
  }
  EXPECT_THAT(remaining, ElementsAre("far", "fjord", "fudge"));
}

TEST_F(IcingDynamicTrieTest, DeletionResortsPartiallyFilledNextArray) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  uint32_t value = 1;
  // 'f' -> [ 'a', 'o', 'u', 0xFF ]
  ASSERT_THAT(trie.Insert("foul", &value), IsOk());
  ASSERT_THAT(trie.Insert("far", &value), IsOk());
  ASSERT_THAT(trie.Insert("fudge", &value), IsOk());

  // Delete the second child
  EXPECT_TRUE(trie.Delete("foul"));

  std::vector<std::string> remaining;
  for (IcingDynamicTrie::Iterator term_iter(trie, /*prefix=*/"");
       term_iter.IsValid(); term_iter.Advance()) {
    remaining.push_back(term_iter.GetKey());
  }
  EXPECT_THAT(remaining, ElementsAre("far", "fudge"));
}

TEST_F(IcingDynamicTrieTest, DeletionLoadTest) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  std::default_random_engine random;
  ICING_LOG(ERROR) << "Seed: " << std::default_random_engine::default_seed;
  std::vector<std::string> terms;
  uint32_t value;
  // Randomly generate 2048 terms.
  for (int i = 0; i < 2048; ++i) {
    terms.push_back(RandomString("abcdefg", 5, &random));
    ASSERT_THAT(trie.Insert(terms.back().c_str(), &value), IsOk());
  }

  // Randomly delete 1024 terms.
  std::unordered_set<std::string> exp_remaining(terms.begin(), terms.end());
  std::shuffle(terms.begin(), terms.end(), random);
  for (int i = 0; i < 1024; ++i) {
    exp_remaining.erase(terms[i]);
    ASSERT_TRUE(trie.Delete(terms[i].c_str()));
  }

  // Check that the iterator still works, and the remaining terms are correct.
  std::unordered_set<std::string> remaining;
  for (IcingDynamicTrie::Iterator term_iter(trie, /*prefix=*/"");
       term_iter.IsValid(); term_iter.Advance()) {
    remaining.insert(term_iter.GetKey());
  }
  EXPECT_THAT(remaining, ContainerEq(exp_remaining));

  // Check that we can still insert terms after delete.
  for (int i = 0; i < 2048; ++i) {
    std::string term = RandomString("abcdefg", 5, &random);
    ASSERT_THAT(trie.Insert(term.c_str(), &value), IsOk());
    exp_remaining.insert(term);
  }
  remaining.clear();
  for (IcingDynamicTrie::Iterator term_iter(trie, /*prefix=*/"");
       term_iter.IsValid(); term_iter.Advance()) {
    remaining.insert(term_iter.GetKey());
  }
  EXPECT_THAT(remaining, ContainerEq(exp_remaining));
}

}  // namespace

// The tests below are accessing private methods and fields of IcingDynamicTrie
// so can't be in the anonymous namespace.

TEST_F(IcingDynamicTrieTest, TrieShouldRespectLimits) {
  // Test limits on numbers of nodes, nexts, and suffixes size.
  IcingFilesystem filesystem;

  // These 3 numbers are the entities we need in order to insert all the test
  // words before the last one.
  uint32_t num_nodes_enough;
  uint32_t num_nexts_enough;
  uint32_t suffixes_size_enough;

  // First, try to fill the 3 numbers above.
  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Remove());
    // Creates a trie with enough numbers of nodes, nexts, and suffix file size.
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options(
        /*max_nodes_in=*/1000, /*max_nexts_in=*/1000,
        /*max_suffixes_size_in=*/1000, sizeof(uint32_t))));
    ASSERT_TRUE(trie.Init());

    // Inserts all the test words before the last one.
    uint32_t value = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen - 1; ++i) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &value), IsOk());
    }

    IcingDynamicTrieHeader header;
    trie.GetHeader(&header);

    // Before each insertion, it requires that there're (2 + 1 + key_length)
    // nodes left, so we need 8 nodes to insert the last word. +7 here will make
    // it just enough to insert the word before the last one.
    num_nodes_enough = header.num_nodes() + 7;

    // Before each insertion, it requires that there're (2 + 1 + key_length +
    // kMaxNextArraySize) nexts left, so we need (8 + kMaxNextArraySize) nexts
    // to insert the last word. (7 + kMaxNextArraySize) here will make it just
    // enough to insert the word before the last one.
    num_nexts_enough =
        header.num_nexts() + 7 + IcingDynamicTrie::kMaxNextArraySize;

    // Before each insertion, it requires that there're (1 + key_length +
    // value_size) bytes left for suffixes, so we need (6 + sizeof(uint32_t))
    // bytes to insert the last word. (5 + sizeof(uint32_t)) here will make it
    // just enough to insert the word before the last one.
    suffixes_size_enough = header.suffixes_size() + 5 + sizeof(uint32_t);
  }

  // Test a trie with just enough number of nodes.
  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Remove());
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options(
        num_nodes_enough, /*max_nexts_in=*/1000,
        /*max_suffixes_size_in=*/1000, sizeof(uint32_t))));
    ASSERT_TRUE(trie.Init());

    // Inserts all the test words before the last one.
    uint32_t value = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen - 1; ++i) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &value), IsOk());
    }

    // Fails to insert the last word because no enough nodes left.
    EXPECT_THAT(
        trie.Insert(kCommonEnglishWords[kCommonEnglishWordArrayLen - 1].data(),
                    &value),
        StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  }

  // Test a trie with just enough number of nexts.
  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Remove());
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options(
        /*max_nodes_in=*/1000, num_nexts_enough,
        /*max_suffixes_size_in=*/1000, sizeof(uint32_t))));
    ASSERT_TRUE(trie.Init());

    // Inserts all the test words before the last one.
    uint32_t value = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen - 1; ++i) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &value), IsOk());
    }

    // Fails to insert the last word because no enough nexts left.
    EXPECT_THAT(
        trie.Insert(kCommonEnglishWords[kCommonEnglishWordArrayLen - 1].data(),
                    &value),
        StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  }

  // Test a trie with just enough suffixes size.
  {
    IcingDynamicTrie trie(trie_files_prefix_,
                          IcingDynamicTrie::RuntimeOptions(), &filesystem);
    ASSERT_TRUE(trie.Remove());
    ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options(
        /*max_nodes_in=*/1000, /*max_nexts_in=*/1000, suffixes_size_enough,
        sizeof(uint32_t))));
    ASSERT_TRUE(trie.Init());

    // Inserts all the test words before the last one.
    uint32_t value = 0;
    for (size_t i = 0; i < kCommonEnglishWordArrayLen - 1; ++i) {
      ASSERT_THAT(trie.Insert(kCommonEnglishWords[i].data(), &value), IsOk());
    }

    // Fails to insert the last word because no enough space for more suffixes.
    EXPECT_THAT(
        trie.Insert(kCommonEnglishWords[kCommonEnglishWordArrayLen - 1].data(),
                    &value),
        StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  }
}

TEST_F(IcingDynamicTrieTest, SyncErrorRecovery) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  static const uint32_t kNumKeys = 5000;
  AddToTrie(&trie, kNumKeys);
  CheckTrie(trie, kNumKeys);

  trie.Sync();
  trie.Close();

  // Reach into the file and set the value_size.
  ASSERT_TRUE(trie.Init());
  IcingDynamicTrieHeader hdr;
  trie.GetHeader(&hdr);
  hdr.set_value_size(hdr.value_size() + 123);
  trie.SetHeader(hdr);
  trie.Close();

  ASSERT_FALSE(trie.Init());
}

TEST_F(IcingDynamicTrieTest, BitmapsClosedWhenInitFails) {
  // Create trie with one property.
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(
      trie_files_prefix_,
      IcingDynamicTrie::RuntimeOptions().set_storage_policy(
          IcingDynamicTrie::RuntimeOptions::kMapSharedWithCrc),
      &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());
  ASSERT_TRUE(trie.deleted_bitmap_);
  trie.SetProperty(0, 0);
  ASSERT_EQ(1, trie.property_bitmaps_.size());
  ASSERT_TRUE(trie.property_bitmaps_[0]);
  trie.Close();

  // Intentionally corrupt deleted_bitmap file to make Init() fail.
  FILE* fp = fopen(trie.deleted_bitmap_filename_.c_str(), "r+");
  ASSERT_TRUE(fp);
  ASSERT_EQ(16, fwrite("################", 1, 16, fp));
  fclose(fp);
  ASSERT_FALSE(trie.Init());

  // Check that both the bitmap and the property files have been closed.
  ASSERT_FALSE(trie.deleted_bitmap_);
  ASSERT_EQ(0, trie.property_bitmaps_.size());
}

TEST_F(IcingDynamicTrieTest, IsBranchingTermShouldWorkForExistingTerms) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  uint32_t value = 1;

  ASSERT_THAT(trie.Insert("", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));

  ASSERT_THAT(trie.Insert("ab", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));

  ASSERT_THAT(trie.Insert("ac", &value), IsOk());
  // "" is a prefix of "ab" and "ac", but it is not a branching term.
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));

  ASSERT_THAT(trie.Insert("ba", &value), IsOk());
  // "" now branches to "ba"
  EXPECT_TRUE(trie.IsBranchingTerm(""));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));
  EXPECT_FALSE(trie.IsBranchingTerm("ba"));

  ASSERT_THAT(trie.Insert("a", &value), IsOk());
  EXPECT_TRUE(trie.IsBranchingTerm(""));
  // "a" branches to "ab" and "ac"
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));
  EXPECT_FALSE(trie.IsBranchingTerm("ba"));

  ASSERT_THAT(trie.Insert("abc", &value), IsOk());
  ASSERT_THAT(trie.Insert("acd", &value), IsOk());
  EXPECT_TRUE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  // "ab" is a prefix of "abc", but it is not a branching term.
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  // "ac" is a prefix of "acd", but it is not a branching term.
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));
  EXPECT_FALSE(trie.IsBranchingTerm("ba"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));
  EXPECT_FALSE(trie.IsBranchingTerm("acd"));

  ASSERT_THAT(trie.Insert("abcd", &value), IsOk());
  EXPECT_TRUE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  // "ab" is a prefix of "abc" and "abcd", but it is not a branching term.
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));
  EXPECT_FALSE(trie.IsBranchingTerm("ba"));
  // "abc" is a prefix of "abcd", but it is not a branching term.
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));
  EXPECT_FALSE(trie.IsBranchingTerm("acd"));
  EXPECT_FALSE(trie.IsBranchingTerm("abcd"));

  ASSERT_THAT(trie.Insert("abd", &value), IsOk());
  EXPECT_TRUE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  // "ab" branches to "abc" and "abd"
  EXPECT_TRUE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("ac"));
  EXPECT_FALSE(trie.IsBranchingTerm("ba"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));
  EXPECT_FALSE(trie.IsBranchingTerm("acd"));
  EXPECT_FALSE(trie.IsBranchingTerm("abcd"));
  EXPECT_FALSE(trie.IsBranchingTerm("abd"));
}

TEST_F(IcingDynamicTrieTest, IsBranchingTermShouldWorkForNonExistingTerms) {
  IcingFilesystem filesystem;
  IcingDynamicTrie trie(trie_files_prefix_, IcingDynamicTrie::RuntimeOptions(),
                        &filesystem);
  ASSERT_TRUE(trie.CreateIfNotExist(IcingDynamicTrie::Options()));
  ASSERT_TRUE(trie.Init());

  uint32_t value = 1;

  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_FALSE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("aa", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_FALSE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("ac", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  // "a" does not exist in the trie, but now it branches to "aa" and "ac".
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("ad", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("abcd", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_FALSE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("abd", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  // "ab" does not exist in the trie, but now it branches to "abcd" and "abd".
  EXPECT_TRUE(trie.IsBranchingTerm("ab"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("abce", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_TRUE(trie.IsBranchingTerm("ab"));
  // "abc" does not exist in the trie, but now it branches to "abcd" and "abce".
  EXPECT_TRUE(trie.IsBranchingTerm("abc"));

  ASSERT_THAT(trie.Insert("abc_suffix", &value), IsOk());
  EXPECT_FALSE(trie.IsBranchingTerm(""));
  EXPECT_TRUE(trie.IsBranchingTerm("a"));
  EXPECT_TRUE(trie.IsBranchingTerm("ab"));
  EXPECT_TRUE(trie.IsBranchingTerm("abc"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc_s"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc_su"));
  EXPECT_FALSE(trie.IsBranchingTerm("abc_suffi"));
}

}  // namespace lib
}  // namespace icing
