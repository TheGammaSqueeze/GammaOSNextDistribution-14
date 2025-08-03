/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/bert_tokenizer.h"

#include <memory>

#include "utils/test-data-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {

using ::testing::ElementsAre;

namespace {
constexpr char kTestVocabPath[] = "annotator/pod_ner/test_data/vocab.txt";

void AssertTokenizerResults(std::unique_ptr<BertTokenizer> tokenizer) {
  auto results = tokenizer->Tokenize("i'm question");

  EXPECT_THAT(results.subwords, ElementsAre("i", "'", "m", "question"));
}

TEST(BertTokenizerTest, TestTokenizerCreationFromBuffer) {
  std::string buffer = GetTestFileContent(kTestVocabPath);

  auto tokenizer =
      std::make_unique<BertTokenizer>(buffer.data(), buffer.size());

  AssertTokenizerResults(std::move(tokenizer));
}

TEST(BertTokenizerTest, TestTokenizerCreationFromFile) {
  auto tokenizer =
      std::make_unique<BertTokenizer>(GetTestDataPath(kTestVocabPath));

  AssertTokenizerResults(std::move(tokenizer));
}

TEST(BertTokenizerTest, TestTokenizerCreationFromVector) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  AssertTokenizerResults(std::move(tokenizer));
}

TEST(BertTokenizerTest, TestTokenizerMultipleRows) {
  auto tokenizer =
      std::make_unique<BertTokenizer>(GetTestDataPath(kTestVocabPath));

  auto results = tokenizer->Tokenize("i'm questionansweraskask");

  EXPECT_THAT(results.subwords, ElementsAre("i", "'", "m", "question", "##ans",
                                            "##wer", "##ask", "##ask"));
}

TEST(BertTokenizerTest, TestTokenizeIntoWordpieces) {
  auto tokenizer =
      std::make_unique<BertTokenizer>(GetTestDataPath(kTestVocabPath));

  auto results = tokenizer->TokenizeIntoWordpieces("i'm questionansweraskask");

  EXPECT_THAT(results.subwords, ElementsAre("i", "'", "m", "question", "##ans",
                                            "##wer", "##ask", "##ask"));
  EXPECT_THAT(results.wp_begin_offset, ElementsAre(0, 1, 2, 4, 12, 15, 18, 21));
  EXPECT_THAT(results.wp_end_offset, ElementsAre(1, 2, 3, 12, 15, 18, 21, 24));
  EXPECT_THAT(results.row_lengths, ElementsAre(1, 1, 1, 5));
}

TEST(BertTokenizerTest, TestTokenizeIntoWordpiecesLongNonAscii) {
  auto tokenizer =
      std::make_unique<BertTokenizer>(GetTestDataPath(kTestVocabPath));

  std::string token;
  for (int i = 0; i < 100; ++i) {
    token += "ń";
  }
  auto results = tokenizer->TokenizeIntoWordpieces(token);

  EXPECT_THAT(results.subwords, ElementsAre("[UNK]"));
  EXPECT_THAT(results.wp_begin_offset, ElementsAre(0));
  EXPECT_THAT(results.wp_end_offset, ElementsAre(100));
  EXPECT_THAT(results.row_lengths, ElementsAre(1));
}

TEST(BertTokenizerTest, TestTokenizerUnknownTokens) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  auto results = tokenizer->Tokenize("i'm questionansweraskask");

  EXPECT_THAT(results.subwords,
              ElementsAre("i", "'", "m", kDefaultUnknownToken));
}

TEST(BertTokenizerTest, TestLookupId) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  int i;
  ASSERT_FALSE(tokenizer->LookupId("iDontExist", &i));

  ASSERT_TRUE(tokenizer->LookupId("i", &i));
  ASSERT_EQ(i, 0);
  ASSERT_TRUE(tokenizer->LookupId("'", &i));
  ASSERT_EQ(i, 1);
  ASSERT_TRUE(tokenizer->LookupId("m", &i));
  ASSERT_EQ(i, 2);
  ASSERT_TRUE(tokenizer->LookupId("question", &i));
  ASSERT_EQ(i, 3);
}

TEST(BertTokenizerTest, TestLookupWord) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  absl::string_view result;
  ASSERT_FALSE(tokenizer->LookupWord(6, &result));

  ASSERT_TRUE(tokenizer->LookupWord(0, &result));
  ASSERT_EQ(result, "i");
  ASSERT_TRUE(tokenizer->LookupWord(1, &result));
  ASSERT_EQ(result, "'");
  ASSERT_TRUE(tokenizer->LookupWord(2, &result));
  ASSERT_EQ(result, "m");
  ASSERT_TRUE(tokenizer->LookupWord(3, &result));
  ASSERT_EQ(result, "question");
}

TEST(BertTokenizerTest, TestContains) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  bool result;
  tokenizer->Contains("iDontExist", &result);
  ASSERT_FALSE(result);

  tokenizer->Contains("i", &result);
  ASSERT_TRUE(result);
  tokenizer->Contains("'", &result);
  ASSERT_TRUE(result);
  tokenizer->Contains("m", &result);
  ASSERT_TRUE(result);
  tokenizer->Contains("question", &result);
  ASSERT_TRUE(result);
}

TEST(BertTokenizerTest, TestLVocabularySize) {
  std::vector<std::string> vocab;
  vocab.emplace_back("i");
  vocab.emplace_back("'");
  vocab.emplace_back("m");
  vocab.emplace_back("question");
  auto tokenizer = std::make_unique<BertTokenizer>(vocab);

  ASSERT_EQ(tokenizer->VocabularySize(), 4);
}

TEST(BertTokenizerTest, SimpleEnglishWithPunctuation) {
  absl::string_view input = "I am fine, thanks!";

  std::vector<std::string> tokens = BertTokenizer::PreTokenize(input);

  EXPECT_THAT(tokens, testing::ElementsAreArray(
                          {"I", "am", "fine", ",", "thanks", "!"}));
}
}  // namespace
}  // namespace libtextclassifier3
