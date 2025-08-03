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

#include "utils/sentencepiece/normalizer.h"

#include <fstream>
#include <string>

#include "utils/container/double-array-trie.h"
#include "utils/sentencepiece/test_utils.h"
#include "utils/strings/stringpiece.h"
#include "utils/test-data-test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

std::string GetTestConfigPath() {
  return GetTestDataPath("utils/sentencepiece/test_data/nmt_nfkc_charsmap.bin");
}

TEST(NormalizerTest, NormalizesAsReferenceNormalizer) {
  std::ifstream test_config_stream(GetTestConfigPath());
  std::string config((std::istreambuf_iterator<char>(test_config_stream)),
                     (std::istreambuf_iterator<char>()));
  SentencePieceNormalizer normalizer =
      NormalizerFromSpec(config, /*add_dummy_prefix=*/true,
                         /*remove_extra_whitespaces=*/true,
                         /*escape_whitespaces=*/true);
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("hello there", &normalized));
    EXPECT_EQ(normalized, "▁hello▁there");
  }

  // Redundant whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("when is  the  world cup?", &normalized));
    EXPECT_EQ(normalized, "▁when▁is▁the▁world▁cup?");
  }

  // Different whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("general\tkenobi", &normalized));
    EXPECT_EQ(normalized, "▁general▁kenobi");
  }

  // NFKC char to multi-char normalization.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("㍿", &normalized));
    EXPECT_EQ(normalized, "▁株式会社");
  }

  // Half width katakana, character composition happens.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize(" ｸﾞｰｸﾞﾙ ", &normalized));
    EXPECT_EQ(normalized, "▁グーグル");
  }

  // NFKC char to char normalization.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("①②③", &normalized));
    EXPECT_EQ(normalized, "▁123");
  }
}

TEST(NormalizerTest, NoDummyPrefix) {
  std::ifstream test_config_stream(GetTestConfigPath());
  std::string config((std::istreambuf_iterator<char>(test_config_stream)),
                     (std::istreambuf_iterator<char>()));
  SentencePieceNormalizer normalizer =
      NormalizerFromSpec(config, /*add_dummy_prefix=*/false,
                         /*remove_extra_whitespaces=*/true,
                         /*escape_whitespaces=*/true);

  // NFKC char to char normalization.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("hello there", &normalized));
    EXPECT_EQ(normalized, "hello▁there");
  }

  // Redundant whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("when is  the  world cup?", &normalized));
    EXPECT_EQ(normalized, "when▁is▁the▁world▁cup?");
  }

  // Different whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("general\tkenobi", &normalized));
    EXPECT_EQ(normalized, "general▁kenobi");
  }

  // NFKC char to multi-char normalization.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("㍿", &normalized));
    EXPECT_EQ(normalized, "株式会社");
  }

  // Half width katakana, character composition happens.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize(" ｸﾞｰｸﾞﾙ ", &normalized));
    EXPECT_EQ(normalized, "グーグル");
  }

  // NFKC char to char normalization.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("①②③", &normalized));
    EXPECT_EQ(normalized, "123");
  }
}

TEST(NormalizerTest, NoRemoveExtraWhitespace) {
  std::ifstream test_config_stream(GetTestConfigPath());
  std::string config((std::istreambuf_iterator<char>(test_config_stream)),
                     (std::istreambuf_iterator<char>()));
  SentencePieceNormalizer normalizer =
      NormalizerFromSpec(config, /*add_dummy_prefix=*/false,
                         /*remove_extra_whitespaces=*/false,
                         /*escape_whitespaces=*/true);

  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("hello there", &normalized));
    EXPECT_EQ(normalized, "hello▁there");
  }

  // Redundant whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("when is  the  world cup?", &normalized));
    EXPECT_EQ(normalized, "when▁is▁▁the▁▁world▁cup?");
  }

  // Different whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("general\tkenobi", &normalized));
    EXPECT_EQ(normalized, "general▁kenobi");
  }
}

TEST(NormalizerTest, NoEscapeWhitespaces) {
  std::ifstream test_config_stream(GetTestConfigPath());
  std::string config((std::istreambuf_iterator<char>(test_config_stream)),
                     (std::istreambuf_iterator<char>()));
  SentencePieceNormalizer normalizer =
      NormalizerFromSpec(config, /*add_dummy_prefix=*/false,
                         /*remove_extra_whitespaces=*/false,
                         /*escape_whitespaces=*/false);

  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("hello there", &normalized));
    EXPECT_EQ(normalized, "hello there");
  }

  // Redundant whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("when is  the  world cup?", &normalized));
    EXPECT_EQ(normalized, "when is  the  world cup?");
  }

  // Different whitespace.
  {
    std::string normalized;
    EXPECT_TRUE(normalizer.Normalize("general\tkenobi", &normalized));
    EXPECT_EQ(normalized, "general kenobi");
  }
}

}  // namespace
}  // namespace libtextclassifier3
