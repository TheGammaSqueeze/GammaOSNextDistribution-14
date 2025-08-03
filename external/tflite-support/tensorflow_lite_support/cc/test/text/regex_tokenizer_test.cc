/* Copyright 2022 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#include "tensorflow_lite_support/cc/text/tokenizers/regex_tokenizer.h"

#include "android-base/file.h"
#include "tensorflow_lite_support/cc/port/gmock.h"
#include "tensorflow_lite_support/cc/port/gtest.h"
#include "tensorflow_lite_support/cc/task/core/task_utils.h"

namespace tflite {
namespace support {
namespace text {
namespace tokenizer {

using ::android::base::GetExecutableDirectory;
using ::testing::ElementsAre;
using ::tflite::task::core::LoadBinaryContent;

namespace {
constexpr char kTestRegexVocabSubPath[] =
    "/tensorflow_lite_support/cc/test/testdata/task/text/"
    "vocab_for_regex_tokenizer.txt";

constexpr char kTestRegexEmptyVocabSubPath[] =
    "/tensorflow_lite_support/cc/test/testdata/task/text/"
    "empty_vocab_for_regex_tokenizer.txt";

constexpr char kRegex[] = "[^\\w\\']+";

TEST(RegexTokenizerTest, TestTokenize) {
  std::string test_regex_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexVocabSubPath);
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, test_regex_vocab_path);
  auto results = tokenizer->Tokenize("good    morning, i'm your teacher.\n");
  EXPECT_THAT(results.subwords,
              ElementsAre("good", "morning", "i'm", "your", "teacher"));
}

TEST(RegexTokenizerTest, TestTokenizeFromFileBuffer) {
  std::string test_regex_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexVocabSubPath);
  std::string buffer = LoadBinaryContent(test_regex_vocab_path.c_str());
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, buffer.data(), buffer.size());
  auto results = tokenizer->Tokenize("good    morning, i'm your teacher.\n");
  EXPECT_THAT(results.subwords,
              ElementsAre("good", "morning", "i'm", "your", "teacher"));
}

TEST(RegexTokenizerTest, TestLookupId) {
  std::string test_regex_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexVocabSubPath);
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, test_regex_vocab_path);
  std::vector<std::string> subwords = {"good", "morning", "i'm", "your",
                                       "teacher"};
  std::vector<int> true_ids = {52, 1972, 146, 129, 1750};
  int id;
  for (int i = 0; i < subwords.size(); i++) {
    ASSERT_TRUE(tokenizer->LookupId(subwords[i], &id));
    ASSERT_EQ(id, true_ids[i]);
  }
}

TEST(RegexTokenizerTest, TestLookupWord) {
  std::string test_regex_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexVocabSubPath);
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, test_regex_vocab_path);
  std::vector<int> ids = {52, 1972, 146, 129, 1750};
  std::vector<std::string> subwords = {"good", "morning", "i'm", "your",
                                       "teacher"};
  absl::string_view result;
  for (int i = 0; i < ids.size(); i++) {
    ASSERT_TRUE(tokenizer->LookupWord(ids[i], &result));
    ASSERT_EQ(result, subwords[i]);
  }
}

TEST(RegexTokenizerTest, TestGetSpecialTokens) {
  // The vocab the following tokens:
  // <PAD> 0
  // <START> 1
  // <UNKNOWN> 2
  std::string test_regex_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexVocabSubPath);
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, test_regex_vocab_path);

  int start_token;
  ASSERT_TRUE(tokenizer->GetStartToken(&start_token));
  ASSERT_EQ(start_token, 1);

  int pad_token;
  ASSERT_TRUE(tokenizer->GetPadToken(&pad_token));
  ASSERT_EQ(pad_token, 0);

  int unknown_token;
  ASSERT_TRUE(tokenizer->GetUnknownToken(&unknown_token));
  ASSERT_EQ(unknown_token, 2);
}

TEST(RegexTokenizerTest, TestGetSpecialTokensFailure) {
  std::string test_regex_empty_vocab_path =
      absl::StrCat(GetExecutableDirectory(), kTestRegexEmptyVocabSubPath);
  auto tokenizer =
      absl::make_unique<RegexTokenizer>(kRegex, test_regex_empty_vocab_path);

  int start_token;
  ASSERT_FALSE(tokenizer->GetStartToken(&start_token));

  int pad_token;
  ASSERT_FALSE(tokenizer->GetPadToken(&pad_token));

  int unknown_token;
  ASSERT_FALSE(tokenizer->GetUnknownToken(&unknown_token));
}

}  // namespace

}  // namespace tokenizer
}  // namespace text
}  // namespace support
}  // namespace tflite
