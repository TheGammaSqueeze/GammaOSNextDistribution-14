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

#include "icing/result/snippet-retriever.h"

#include <cstdint>
#include <limits>
#include <memory>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/mock-filesystem.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-terms.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section-manager.h"
#include "icing/store/document-id.h"
#include "icing/store/key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/map/map-normalizer.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/snippet-helpers.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::SizeIs;

// TODO (b/246964044): remove ifdef guard when url-tokenizer is ready for export
// to Android. Also move it to schema-builder.h
#ifdef ENABLE_URL_TOKENIZER
constexpr StringIndexingConfig::TokenizerType::Code TOKENIZER_URL =
    StringIndexingConfig::TokenizerType::URL;
#endif  // ENABLE_URL_TOKENIZER

std::vector<std::string_view> GetPropertyPaths(const SnippetProto& snippet) {
  std::vector<std::string_view> paths;
  for (const SnippetProto::EntryProto& entry : snippet.entries()) {
    paths.push_back(entry.property_name());
  }
  return paths;
}

class SnippetRetrieverTest : public testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());

    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    jni_cache_ = GetTestJniCache();
    language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                         jni_cache_.get());
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(std::move(options)));

    // Setup the schema
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("email")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("subject")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("body")
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/10000));
    ICING_ASSERT_OK_AND_ASSIGN(
        snippet_retriever_,
        SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                                 normalizer_.get()));

    // Set limits to max - effectively no limit. Enable matching and request a
    // window of 64 bytes.
    snippet_spec_.set_num_to_snippet(std::numeric_limits<int32_t>::max());
    snippet_spec_.set_num_matches_per_property(
        std::numeric_limits<int32_t>::max());
    snippet_spec_.set_max_window_utf32_length(64);
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  Filesystem filesystem_;
  FakeClock fake_clock_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<SnippetRetriever> snippet_retriever_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<const JniCache> jni_cache_;
  ResultSpecProto::SnippetSpecProto snippet_spec_;
  std::string test_dir_;
};

TEST_F(SnippetRetrieverTest, CreationWithNullPointerShouldFail) {
  EXPECT_THAT(
      SnippetRetriever::Create(/*schema_store=*/nullptr,
                               language_segmenter_.get(), normalizer_.get()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(SnippetRetriever::Create(schema_store_.get(),
                                       /*language_segmenter=*/nullptr,
                                       normalizer_.get()),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               /*normalizer=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowSizeSmallerThanMatch) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Window starts at the beginning of "three" and ends in the middle of
  // "three". len=4, orig_window= "thre"
  snippet_spec_.set_max_window_utf32_length(4);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre(""));
}

TEST_F(SnippetRetrieverTest,
       SnippetingWindowMaxWindowSizeEqualToMatch_OddLengthMatch) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Window starts at the beginning of "three" and at the exact end of
  // "three". len=5, orig_window= "three"
  snippet_spec_.set_max_window_utf32_length(5);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("three"));
}

TEST_F(SnippetRetrieverTest,
       SnippetingWindowMaxWindowSizeEqualToMatch_EvenLengthMatch) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"four"}}};

  // Window starts at the beginning of "four" and at the exact end of
  // "four". len=4, orig_window= "four"
  snippet_spec_.set_max_window_utf32_length(4);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("four"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowStartsInWhitespace) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // String:      "one two three four.... five"
  //               ^   ^   ^     ^        ^   ^
  // UTF-8 idx:    0   4   8     14       23  27
  // UTF-32 idx:   0   4   8     14       23  27
  //
  // The window will be:
  //   1. untrimmed, no-shifting window will be (2,17).
  //   2. trimmed, no-shifting window [4,13) "two three"
  //   3. trimmed, shifted window [4,18) "two three four"
  snippet_spec_.set_max_window_utf32_length(14);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("two three four"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowStartsMidToken) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // String:      "one two three four.... five"
  //               ^   ^   ^     ^        ^   ^
  // UTF-8 idx:    0   4   8     14       23  27
  // UTF-32 idx:   0   4   8     14       23  27
  //
  // The window will be:
  //   1. untrimmed, no-shifting window will be (1,18).
  //   2. trimmed, no-shifting window [4,18) "two three four"
  //   3. trimmed, shifted window [4,20) "two three four.."
  snippet_spec_.set_max_window_utf32_length(16);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("two three four.."));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowEndsInPunctuation) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Window ends in the middle of all the punctuation and window starts at 0.
  // len=20, orig_window="one two three four.."
  snippet_spec_.set_max_window_utf32_length(20);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four.."));
}

TEST_F(SnippetRetrieverTest,
       SnippetingWindowMaxWindowEndsMultiBytePunctuation) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body",
                             "Is everything upside down in Australia¿ Crikey!")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"in"}}};

  // Window ends in the middle of all the punctuation and window starts at 0.
  // len=26, orig_window="pside down in Australia¿"
  snippet_spec_.set_max_window_utf32_length(24);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("down in Australia¿"));
}

TEST_F(SnippetRetrieverTest,
       SnippetingWindowMaxWindowBeyondMultiBytePunctuation) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body",
                             "Is everything upside down in Australia¿ Crikey!")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"in"}}};

  // Window ends in the middle of all the punctuation and window starts at 0.
  // len=26, orig_window="upside down in Australia¿ "
  snippet_spec_.set_max_window_utf32_length(26);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("upside down in Australia¿"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowStartsBeforeValueStart) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // String:      "one two three four.... five"
  //               ^   ^   ^     ^        ^   ^
  // UTF-8 idx:    0   4   8     14       23  27
  // UTF-32 idx:   0   4   8     14       23  27
  //
  // The window will be:
  //   1. untrimmed, no-shifting window will be (-2,21).
  //   2. trimmed, no-shifting window [0,21) "one two three four..."
  //   3. trimmed, shifted window [0,22) "one two three four...."
  snippet_spec_.set_max_window_utf32_length(22);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four...."));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowEndsInWhitespace) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Window ends before "five" but after all the punctuation
  // len=26, orig_window="one two three four.... "
  snippet_spec_.set_max_window_utf32_length(26);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four...."));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowEndsMidToken) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // String:      "one two three four.... five"
  //               ^   ^   ^     ^        ^   ^
  // UTF-8 idx:    0   4   8     14       23  27
  // UTF-32 idx:   0   4   8     14       23  27
  //
  // The window will be:
  //   1. untrimmed, no-shifting window will be ((-7,26).
  //   2. trimmed, no-shifting window [0,26) "one two three four...."
  //   3. trimmed, shifted window [0,27) "one two three four.... five"
  snippet_spec_.set_max_window_utf32_length(32);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four.... five"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowSizeEqualToValueSize) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Max window size equals the size of the value.
  // len=34, orig_window="one two three four.... five"
  snippet_spec_.set_max_window_utf32_length(34);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four.... five"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMaxWindowSizeLargerThanValueSize) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"three"}}};

  // Max window size exceeds the size of the value.
  // len=36, orig_window="one two three four.... five"
  snippet_spec_.set_max_window_utf32_length(36);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four.... five"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMatchAtTextStart) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five six")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"two"}}};

  // String:      "one two three four.... five six"
  //               ^   ^   ^     ^        ^    ^  ^
  // UTF-8 idx:    0   4   8     14       23  28  31
  // UTF-32 idx:   0   4   8     14       23  28  31
  //
  // Window size will go past the start of the window.
  // The window will be:
  //   1. untrimmed, no-shifting window will be (-10,19).
  //   2. trimmed, no-shifting window [0,19) "one two three four."
  //   3. trimmed, shifted window [0,27) "one two three four.... five"
  snippet_spec_.set_max_window_utf32_length(28);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four.... five"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMatchAtTextEnd) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four.... five six")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"five"}}};

  // String:      "one two three four.... five six"
  //               ^   ^   ^     ^        ^    ^  ^
  // UTF-8 idx:    0   4   8     14       23  28  31
  // UTF-32 idx:   0   4   8     14       23  28  31
  //
  // Window size will go past the end of the window.
  // The window will be:
  //   1. untrimmed, no-shifting window will be (10,39).
  //   2. trimmed, no-shifting window [14,31) "four.... five six"
  //   3. trimmed, shifted window [4,31) "two three four.... five six"
  snippet_spec_.set_max_window_utf32_length(28);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("two three four.... five six"));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMatchAtTextStartShortText) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four....")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"two"}}};

  // String:      "one two three four...."
  //               ^   ^   ^     ^       ^
  // UTF-8 idx:    0   4   8     14      22
  // UTF-32 idx:   0   4   8     14      22
  //
  // Window size will go past the start of the window.
  // The window will be:
  //   1. untrimmed, no-shifting window will be (-10,19).
  //   2. trimmed, no-shifting window [0, 19) "one two three four."
  //   3. trimmed, shifted window [0, 22) "one two three four...."
  snippet_spec_.set_max_window_utf32_length(28);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four...."));
}

TEST_F(SnippetRetrieverTest, SnippetingWindowMatchAtTextEndShortText) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "counting")
          .AddStringProperty("body", "one two three four....")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"four"}}};

  // String:      "one two three four...."
  //               ^   ^   ^     ^       ^
  // UTF-8 idx:    0   4   8     14      22
  // UTF-32 idx:   0   4   8     14      22
  //
  // Window size will go past the start of the window.
  // The window will be:
  //   1. untrimmed, no-shifting window will be (1,30).
  //   2. trimmed, no-shifting window [4, 22) "two three four...."
  //   3. trimmed, shifted window [0, 22) "one two three four...."
  snippet_spec_.set_max_window_utf32_length(28);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("one two three four...."));
}

TEST_F(SnippetRetrieverTest, PrefixSnippeting) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body", "Only a fool would match this content.")
          .Build();
  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"f"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Check the snippets. 'f' should match prefix-enabled property 'subject', but
  // not exact-only property 'body'
  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("subject foo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("f"));
}

TEST_F(SnippetRetrieverTest, ExactSnippeting) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body", "Only a fool would match this content.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"f"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), IsEmpty());
}

TEST_F(SnippetRetrieverTest, SimpleSnippetingNoWindowing) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body", "Only a fool would match this content.")
          .Build();

  snippet_spec_.set_max_window_utf32_length(0);

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"foo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre(""));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("foo"));
}

TEST_F(SnippetRetrieverTest, SnippetingMultipleMatches) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();
  // String:      "Concerning the subject of foo, we need to begin considering "
  //               ^          ^   ^       ^  ^    ^  ^    ^  ^     ^
  // UTF-8 idx:    0          11  15     23  26  31  34  39  42    48
  // UTF-32 idx:   0          11  15     23  26  31  34  39  42    48
  //
  // String ctd:  "our options regarding body bar."
  //               ^   ^       ^         ^    ^   ^
  // UTF-8 idx:    60  64      72        82   87  91
  // UTF-32 idx:   60  64      72        82   87  91
  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"foo", "bar"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), SizeIs(2));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  // The first window will be:
  //   1. untrimmed, no-shifting window will be (-6,59).
  //   2. trimmed, no-shifting window [0, 59) "Concerning... considering".
  //   3. trimmed, shifted window [0, 63) "Concerning... our"
  // The second window will be:
  //   1. untrimmed, no-shifting window will be (54,91).
  //   2. trimmed, no-shifting window [60, 91) "our... bar.".
  //   3. trimmed, shifted window [31, 91) "we... bar."
  EXPECT_THAT(
      GetWindows(content, snippet.entries(0)),
      ElementsAre(
          "Concerning the subject of foo, we need to begin considering our",
          "we need to begin considering our options regarding body bar."));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("foo", "bar"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("foo", "bar"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("subject"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)),
              ElementsAre("subject foo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("foo"));
}

TEST_F(SnippetRetrieverTest, SnippetingMultipleMatchesSectionRestrict) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();
  // String:      "Concerning the subject of foo, we need to begin considering "
  //               ^          ^   ^       ^  ^    ^  ^    ^  ^     ^
  // UTF-8 idx:    0          11  15     23  26  31  34  39  42    48
  // UTF-32 idx:   0          11  15     23  26  31  34  39  42    48
  //
  // String ctd:  "our options regarding body bar."
  //               ^   ^       ^         ^    ^   ^
  // UTF-8 idx:    60  64      72        82   87  91
  // UTF-32 idx:   60  64      72        82   87  91
  //
  // Section 1 "subject" is not in the section_mask, so no snippet information
  // from that section should be returned by the SnippetRetriever.
  SectionIdMask section_mask = 0b00000001;
  SectionRestrictQueryTermsMap query_terms{{"", {"foo", "bar"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  // The first window will be:
  //   1. untrimmed, no-shifting window will be (-6,59).
  //   2. trimmed, no-shifting window [0, 59) "Concerning... considering".
  //   3. trimmed, shifted window [0, 63) "Concerning... our"
  // The second window will be:
  //   1. untrimmed, no-shifting window will be (54,91).
  //   2. trimmed, no-shifting window [60, 91) "our... bar.".
  //   3. trimmed, shifted window [31, 91) "we... bar."
  EXPECT_THAT(
      GetWindows(content, snippet.entries(0)),
      ElementsAre(
          "Concerning the subject of foo, we need to begin considering our",
          "we need to begin considering our options regarding body bar."));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("foo", "bar"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("foo", "bar"));
}

TEST_F(SnippetRetrieverTest, SnippetingMultipleMatchesSectionRestrictedTerm) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();
  // String:      "Concerning the subject of foo, we need to begin considering "
  //               ^          ^   ^       ^  ^    ^  ^    ^  ^     ^
  // UTF-8 idx:    0          11  15     23  26  31  34  39  42    48
  // UTF-32 idx:   0          11  15     23  26  31  34  39  42    48
  //
  // String ctd:  "our options regarding body bar."
  //               ^   ^       ^         ^    ^   ^
  // UTF-8 idx:    60  64      72        82   87  91
  // UTF-32 idx:   60  64      72        82   87  91
  SectionIdMask section_mask = 0b00000011;
  // "subject" should match in both sections, but "foo" is restricted to "body"
  // so it should only match in the 'body' section and not the 'subject'
  // section.
  SectionRestrictQueryTermsMap query_terms{{"", {"subject"}},
                                           {"body", {"foo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), SizeIs(2));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  // The first window will be:
  //   1. untrimmed, no-shifting window will be (-15,50).
  //   2. trimmed, no-shifting window [0, 47) "Concerning... begin".
  //   3. trimmed, shifted window [0, 63) "Concerning... our"
  // The second window will be:
  //   1. untrimmed, no-shifting window will be (-6,59).
  //   2. trimmed, no-shifting window [0, 59) "Concerning... considering".
  //   3. trimmed, shifted window [0, 63) "Concerning... our"
  EXPECT_THAT(
      GetWindows(content, snippet.entries(0)),
      ElementsAre(
          "Concerning the subject of foo, we need to begin considering our",
          "Concerning the subject of foo, we need to begin considering our"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("subject", "foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("subject", "foo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("subject"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)),
              ElementsAre("subject foo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("subject"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)),
              ElementsAre("subject"));
}

TEST_F(SnippetRetrieverTest, SnippetingMultipleMatchesOneMatchPerProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "subject foo")
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();

  // String:      "Concerning the subject of foo, we need to begin considering "
  //               ^          ^   ^       ^  ^    ^  ^    ^  ^     ^
  // UTF-8 idx:    0          11  15     23  26  31  34  39  42    48
  // UTF-32 idx:   0          11  15     23  26  31  34  39  42    48
  //
  // String ctd:  "our options regarding body bar."
  //               ^   ^       ^         ^    ^   ^
  // UTF-8 idx:    60  64      72        82   87  91
  // UTF-32 idx:   60  64      72        82   87  91
  snippet_spec_.set_num_matches_per_property(1);

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"foo", "bar"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Check the snippets
  EXPECT_THAT(snippet.entries(), SizeIs(2));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  // The window will be:
  //   1. untrimmed, no-shifting window will be (-6,59).
  //   2. trimmed, no-shifting window [0, 59) "Concerning... considering".
  //   3. trimmed, shifted window [0, 63) "Concerning... our"
  EXPECT_THAT(
      GetWindows(content, snippet.entries(0)),
      ElementsAre(
          "Concerning the subject of foo, we need to begin considering our"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("foo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("subject"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)),
              ElementsAre("subject foo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("foo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("foo"));
}

TEST_F(SnippetRetrieverTest, PrefixSnippetingNormalization) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "MDI team")
          .AddStringProperty("body", "Some members are in Zürich.")
          .Build();
  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"md"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("MDI team"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("MDI"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("MD"));
}

TEST_F(SnippetRetrieverTest, ExactSnippetingNormalization) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", "MDI team")
          .AddStringProperty("body", "Some members are in Zürich.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"zurich"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("body"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("Some members are in Zürich."));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("Zürich"));

  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("Zürich"));
}

TEST_F(SnippetRetrieverTest, SnippetingTestOneLevel) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("SingleLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("X")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Y")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Z")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  std::vector<std::string> string_values = {"marco", "polo", "marco", "polo"};
  DocumentProto document;
  document.set_schema("SingleLevelType");
  PropertyProto* prop = document.add_properties();
  prop->set_name("X");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = document.add_properties();
  prop->set_name("Y");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = document.add_properties();
  prop->set_name("Z");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }

  SectionIdMask section_mask = 0b00000111;
  SectionRestrictQueryTermsMap query_terms{{"", {"polo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(6));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("X[1]"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("polo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("X[3]"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("polo"));

  EXPECT_THAT(GetPropertyPaths(snippet),
              ElementsAre("X[1]", "X[3]", "Y[1]", "Y[3]", "Z[1]", "Z[3]"));
}

TEST_F(SnippetRetrieverTest, SnippetingTestMultiLevel) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("SingleLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("X")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Y")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Z")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("MultiLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("A")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("B")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("C")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  std::vector<std::string> string_values = {"marco", "polo", "marco", "polo"};
  DocumentProto subdocument;
  PropertyProto* prop = subdocument.add_properties();
  prop->set_name("X");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = subdocument.add_properties();
  prop->set_name("Y");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = subdocument.add_properties();
  prop->set_name("Z");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }

  DocumentProto document;
  document.set_schema("MultiLevelType");
  prop = document.add_properties();
  prop->set_name("A");
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("B");
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("C");
  *prop->add_document_values() = subdocument;

  SectionIdMask section_mask = 0b111111111;
  SectionRestrictQueryTermsMap query_terms{{"", {"polo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(18));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("A.X[1]"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("polo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("A.X[3]"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("polo"));

  EXPECT_THAT(
      GetPropertyPaths(snippet),
      ElementsAre("A.X[1]", "A.X[3]", "A.Y[1]", "A.Y[3]", "A.Z[1]", "A.Z[3]",
                  "B.X[1]", "B.X[3]", "B.Y[1]", "B.Y[3]", "B.Z[1]", "B.Z[3]",
                  "C.X[1]", "C.X[3]", "C.Y[1]", "C.Y[3]", "C.Z[1]", "C.Z[3]"));
}

TEST_F(SnippetRetrieverTest, SnippetingTestMultiLevelRepeated) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("SingleLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("X")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Y")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Z")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("MultiLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("A")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("B")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("C")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  std::vector<std::string> string_values = {"marco", "polo", "marco", "polo"};
  DocumentProto subdocument;
  PropertyProto* prop = subdocument.add_properties();
  prop->set_name("X");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = subdocument.add_properties();
  prop->set_name("Y");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }
  prop = subdocument.add_properties();
  prop->set_name("Z");
  for (const std::string& s : string_values) {
    prop->add_string_values(s);
  }

  DocumentProto document;
  document.set_schema("MultiLevelType");
  prop = document.add_properties();
  prop->set_name("A");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("B");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("C");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  SectionIdMask section_mask = 0b111111111;
  SectionRestrictQueryTermsMap query_terms{{"", {"polo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(36));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("A[0].X[1]"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("polo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("A[0].X[3]"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("polo"));

  EXPECT_THAT(GetPropertyPaths(snippet),
              ElementsAre("A[0].X[1]", "A[0].X[3]", "A[1].X[1]", "A[1].X[3]",
                          "A[0].Y[1]", "A[0].Y[3]", "A[1].Y[1]", "A[1].Y[3]",
                          "A[0].Z[1]", "A[0].Z[3]", "A[1].Z[1]", "A[1].Z[3]",
                          "B[0].X[1]", "B[0].X[3]", "B[1].X[1]", "B[1].X[3]",
                          "B[0].Y[1]", "B[0].Y[3]", "B[1].Y[1]", "B[1].Y[3]",
                          "B[0].Z[1]", "B[0].Z[3]", "B[1].Z[1]", "B[1].Z[3]",
                          "C[0].X[1]", "C[0].X[3]", "C[1].X[1]", "C[1].X[3]",
                          "C[0].Y[1]", "C[0].Y[3]", "C[1].Y[1]", "C[1].Y[3]",
                          "C[0].Z[1]", "C[0].Z[3]", "C[1].Z[1]", "C[1].Z[3]"));
}

TEST_F(SnippetRetrieverTest, SnippetingTestMultiLevelSingleValue) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("SingleLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("X")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Y")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("Z")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("MultiLevelType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("A")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("B")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("C")
                                        .SetDataTypeDocument(
                                            "SingleLevelType",
                                            /*index_nested_properties=*/true)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  DocumentProto subdocument;
  PropertyProto* prop = subdocument.add_properties();
  prop->set_name("X");
  prop->add_string_values("polo");
  prop = subdocument.add_properties();
  prop->set_name("Y");
  prop->add_string_values("marco");
  prop = subdocument.add_properties();
  prop->set_name("Z");
  prop->add_string_values("polo");

  DocumentProto document;
  document.set_schema("MultiLevelType");
  prop = document.add_properties();
  prop->set_name("A");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("B");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  prop = document.add_properties();
  prop->set_name("C");
  *prop->add_document_values() = subdocument;
  *prop->add_document_values() = subdocument;

  SectionIdMask section_mask = 0b111111111;
  SectionRestrictQueryTermsMap query_terms{{"", {"polo"}}};
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  EXPECT_THAT(snippet.entries(), SizeIs(12));
  EXPECT_THAT(snippet.entries(0).property_name(), Eq("A[0].X"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("polo"));

  EXPECT_THAT(snippet.entries(1).property_name(), Eq("A[1].X"));
  content = GetString(&document, snippet.entries(1).property_name());
  EXPECT_THAT(GetWindows(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetMatches(content, snippet.entries(1)), ElementsAre("polo"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(1)), ElementsAre("polo"));

  EXPECT_THAT(
      GetPropertyPaths(snippet),
      ElementsAre("A[0].X", "A[1].X", "A[0].Z", "A[1].Z", "B[0].X", "B[1].X",
                  "B[0].Z", "B[1].Z", "C[0].X", "C[1].X", "C[0].Z", "C[1].Z"));
}

TEST_F(SnippetRetrieverTest, CJKSnippetMatchTest) {
  // String:     "我每天走路去上班。"
  //              ^ ^  ^   ^^
  // UTF8 idx:    0 3  9  15 18
  // UTF16 idx:   0 1  3   5 6
  // Breaks into segments: "我", "每天", "走路", "去", "上班"
  constexpr std::string_view kChinese = "我每天走路去上班。";
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", kChinese)
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"走"}}};

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(snippet.entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  EXPECT_THAT(entry->property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  // Ensure that the match is correct.
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("走路"));
  EXPECT_THAT(GetSubMatches(content, *entry), ElementsAre("走"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(3));
  EXPECT_THAT(match_proto.exact_match_utf16_length(), Eq(2));
  EXPECT_THAT(match_proto.submatch_utf16_length(), Eq(1));
}

TEST_F(SnippetRetrieverTest, CJKSnippetWindowTest) {
  language_segmenter_factory::SegmenterOptions options(ULOC_SIMPLIFIED_CHINESE,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      language_segmenter_,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  // String:     "我每天走路去上班。"
  //              ^ ^  ^   ^^
  // UTF8 idx:    0 3  9  15 18
  // UTF16 idx:   0 1  3   5 6
  // UTF32 idx:   0 1  3   5 6
  // Breaks into segments: "我", "每天", "走路", "去", "上班"
  constexpr std::string_view kChinese = "我每天走路去上班。";
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", kChinese)
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"走"}}};

  // The window will be:
  //   1. untrimmed, no-shifting window will be (0,7).
  //   2. trimmed, no-shifting window [1, 6) "每天走路去".
  //   3. trimmed, shifted window [0, 6) "我每天走路去"
  snippet_spec_.set_max_window_utf32_length(6);

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(snippet.entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  EXPECT_THAT(entry->property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  // Ensure that the match is correct.
  EXPECT_THAT(GetWindows(content, *entry), ElementsAre("我每天走路去"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.window_utf16_position(), Eq(0));
  EXPECT_THAT(match_proto.window_utf16_length(), Eq(6));
}

TEST_F(SnippetRetrieverTest, Utf16MultiCodeUnitSnippetMatchTest) {
  // The following string has four-byte UTF-8 characters. Most importantly, it
  // is also two code units in UTF-16.
  // String:     "𐀀𐀁 𐀂𐀃 𐀄"
  //              ^  ^  ^
  // UTF8 idx:    0  9  18
  // UTF16 idx:   0  5  10
  // Breaks into segments: "𐀀𐀁", "𐀂𐀃", "𐀄"
  constexpr std::string_view kText = "𐀀𐀁 𐀂𐀃 𐀄";
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", kText)
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"𐀂"}}};

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(snippet.entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  EXPECT_THAT(entry->property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  // Ensure that the match is correct.
  EXPECT_THAT(GetMatches(content, *entry), ElementsAre("𐀂𐀃"));
  EXPECT_THAT(GetSubMatches(content, *entry), ElementsAre("𐀂"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(5));
  EXPECT_THAT(match_proto.exact_match_utf16_length(), Eq(4));
  EXPECT_THAT(match_proto.submatch_utf16_length(), Eq(2));
}

TEST_F(SnippetRetrieverTest, Utf16MultiCodeUnitWindowTest) {
  // The following string has four-byte UTF-8 characters. Most importantly, it
  // is also two code units in UTF-16.
  // String:     "𐀀𐀁 𐀂𐀃 𐀄"
  //              ^  ^  ^
  // UTF8 idx:    0  9  18
  // UTF16 idx:   0  5  10
  // UTF32 idx:   0  3  6
  // Breaks into segments: "𐀀𐀁", "𐀂𐀃", "𐀄"
  constexpr std::string_view kText = "𐀀𐀁 𐀂𐀃 𐀄";
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "email/1")
          .SetSchema("email")
          .AddStringProperty("subject", kText)
          .AddStringProperty("body",
                             "Concerning the subject of foo, we need to begin "
                             "considering our options regarding body bar.")
          .Build();

  SectionIdMask section_mask = 0b00000011;
  SectionRestrictQueryTermsMap query_terms{{"", {"𐀂"}}};

  // Set a six character window. This will produce a window like this:
  // String:     "𐀀𐀁 𐀂𐀃 𐀄"
  //                 ^   ^
  // UTF8 idx:       9   22
  // UTF16 idx:      5   12
  // UTF32 idx:      3   7
  snippet_spec_.set_max_window_utf32_length(6);

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // Ensure that one and only one property was matched and it was "body"
  ASSERT_THAT(snippet.entries(), SizeIs(1));
  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  EXPECT_THAT(entry->property_name(), Eq("subject"));
  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  // Ensure that there is one and only one match within "subject"
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  const SnippetMatchProto& match_proto = entry->snippet_matches(0);

  // Ensure that the match is correct.
  EXPECT_THAT(GetWindows(content, *entry), ElementsAre("𐀂𐀃 𐀄"));

  // Ensure that the utf-16 values are also as expected
  EXPECT_THAT(match_proto.window_utf16_position(), Eq(5));
  EXPECT_THAT(match_proto.window_utf16_length(), Eq(7));
}

TEST_F(SnippetRetrieverTest, SnippettingVerbatimAscii) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("verbatimType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("verbatim")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_VERBATIM)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "verbatim/1")
                               .SetSchema("verbatimType")
                               .AddStringProperty("verbatim", "Hello, world!")
                               .Build();

  SectionIdMask section_mask = 0b00000001;
  SectionRestrictQueryTermsMap query_terms{{"", {"Hello, world!"}}};

  snippet_spec_.set_max_window_utf32_length(13);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_EXACT, snippet_spec_, document, section_mask);

  // There should only be one snippet entry and match, the verbatim token in its
  // entirety.
  ASSERT_THAT(snippet.entries(), SizeIs(1));

  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  ASSERT_THAT(entry->property_name(), "verbatim");

  const SnippetMatchProto& match_proto = entry->snippet_matches(0);
  // We expect the match to begin at position 0, and to span the entire token
  // which contains 13 characters.
  EXPECT_THAT(match_proto.window_byte_position(), Eq(0));
  EXPECT_THAT(match_proto.window_utf16_length(), Eq(13));

  // We expect the submatch to begin at position 0 of the verbatim token and
  // span the length of our query term "Hello, world!", which has utf-16 length
  // of 13. The submatch length is equal to the window length as the query the
  // snippet is retrieved with an exact term match.
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(0));
  EXPECT_THAT(match_proto.submatch_utf16_length(), Eq(13));
}

TEST_F(SnippetRetrieverTest, SnippettingVerbatimCJK) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("verbatimType")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("verbatim")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_VERBATIM)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  // String:     "我每天走路去上班。"
  //              ^ ^  ^   ^^
  // UTF8 idx:    0 3  9  15 18
  // UTF16 idx:   0 1  3   5 6
  // UTF32 idx:   0 1  3   5 6
  // Breaks into segments: "我", "每天", "走路", "去", "上班"
  std::string chinese_string = "我每天走路去上班。";
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "verbatim/1")
                               .SetSchema("verbatimType")
                               .AddStringProperty("verbatim", chinese_string)
                               .Build();

  SectionIdMask section_mask = 0b00000001;
  SectionRestrictQueryTermsMap query_terms{{"", {"我每"}}};

  snippet_spec_.set_max_window_utf32_length(9);
  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // There should only be one snippet entry and match, the verbatim token in its
  // entirety.
  ASSERT_THAT(snippet.entries(), SizeIs(1));

  const SnippetProto::EntryProto* entry = &snippet.entries(0);
  ASSERT_THAT(entry->snippet_matches(), SizeIs(1));
  ASSERT_THAT(entry->property_name(), "verbatim");

  const SnippetMatchProto& match_proto = entry->snippet_matches(0);
  // We expect the match to begin at position 0, and to span the entire token
  // which has utf-16 length of 9.
  EXPECT_THAT(match_proto.window_byte_position(), Eq(0));
  EXPECT_THAT(match_proto.window_utf16_length(), Eq(9));

  // We expect the submatch to begin at position 0 of the verbatim token and
  // span the length of our query term "我每", which has utf-16 length of 2.
  EXPECT_THAT(match_proto.exact_match_utf16_position(), Eq(0));
  EXPECT_THAT(match_proto.submatch_utf16_length(), Eq(2));
}

TEST_F(SnippetRetrieverTest, SnippettingRfc822Ascii) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("rfc822Type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("rfc822")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_RFC822)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "rfc822/1")
          .SetSchema("rfc822Type")
          .AddStringProperty("rfc822",
                             "Alexander Sav <tom.bar@google.com>, Very Long "
                             "Name Example <tjbarron@google.com>")
          .Build();

  SectionIdMask section_mask = 0b00000001;

  // This should match both the first name token as well as the entire RFC822.
  SectionRestrictQueryTermsMap query_terms{{"", {"alexand"}}};

  snippet_spec_.set_max_window_utf32_length(35);

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "rfc822");

  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("Alexander Sav <tom.bar@google.com>,",
                          "Alexander Sav <tom.bar@google.com>,"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("Alexander Sav <tom.bar@google.com>", "Alexander"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("Alexand", "Alexand"));

  // "tom" should match the local component, local address, and address tokens.
  query_terms = SectionRestrictQueryTermsMap{{"", {"tom"}}};
  snippet_spec_.set_max_window_utf32_length(36);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "rfc822");

  content = GetString(&document, snippet.entries(0).property_name());

  // TODO(b/248362902) Stop returning duplicate matches.
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("Alexander Sav <tom.bar@google.com>,",
                          "Alexander Sav <tom.bar@google.com>,",
                          "Alexander Sav <tom.bar@google.com>,"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("tom.bar", "tom.bar@google.com", "tom"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("tom", "tom", "tom"));
}

TEST_F(SnippetRetrieverTest, SnippettingRfc822CJK) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("rfc822Type")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("rfc822")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_RFC822)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  std::string chinese_string = "我, 每天@走路, 去@上班";
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "rfc822/1")
                               .SetSchema("rfc822Type")
                               .AddStringProperty("rfc822", chinese_string)
                               .Build();

  SectionIdMask section_mask = 0b00000001;

  SectionRestrictQueryTermsMap query_terms{{"", {"走"}}};

  snippet_spec_.set_max_window_utf32_length(8);

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, TERM_MATCH_PREFIX, snippet_spec_, document, section_mask);

  // There should only be one snippet entry and match, the local component token
  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "rfc822");

  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  // The local component, address, local address, and token will all match. The
  // windows for address and token are "" as the snippet window is too small.
  // TODO(b/248362902) Stop returning duplicate matches.
  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("每天@走路,", "每天@走路,"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("走路", "走路"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("走", "走"));
}

#ifdef ENABLE_URL_TOKENIZER
TEST_F(SnippetRetrieverTest, SnippettingUrlAscii) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("urlType").AddProperty(
              PropertyConfigBuilder()
                  .SetName("url")
                  .SetDataTypeString(MATCH_PREFIX, TOKENIZER_URL)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();
  ICING_ASSERT_OK(schema_store_->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/true));

  ICING_ASSERT_OK_AND_ASSIGN(
      snippet_retriever_,
      SnippetRetriever::Create(schema_store_.get(), language_segmenter_.get(),
                               normalizer_.get()));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "url/1")
          .SetSchema("urlType")
          .AddStringProperty("url", "https://mail.google.com/calendar/google/")
          .Build();

  SectionIdMask section_mask = 0b00000001;

  // Query with single url split-token match
  SectionRestrictQueryTermsMap query_terms{{"", {"com"}}};
  // 40 is the length of the url.
  // Window that is the size of the url should return entire url.
  snippet_spec_.set_max_window_utf32_length(40);

  SnippetProto snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  std::string_view content =
      GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)), ElementsAre("com"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)), ElementsAre("com"));

  // Query with single url suffix-token match
  query_terms = SectionRestrictQueryTermsMap{{"", {"mail.goo"}}};
  snippet_spec_.set_max_window_utf32_length(40);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  content = GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("mail.google.com/calendar/google/"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("mail.goo"));

  // Query with multiple url split-token matches
  query_terms = SectionRestrictQueryTermsMap{{"", {"goog"}}};
  snippet_spec_.set_max_window_utf32_length(40);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  content = GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://mail.google.com/calendar/google/",
                          "https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("google", "google"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("goog", "goog"));

  // Query with both url split-token and suffix-token matches
  query_terms = SectionRestrictQueryTermsMap{{"", {"mail"}}};
  snippet_spec_.set_max_window_utf32_length(40);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  content = GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://mail.google.com/calendar/google/",
                          "https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("mail", "mail.google.com/calendar/google/"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("mail", "mail"));

  // Prefix query with both url split-token and suffix-token matches
  query_terms = SectionRestrictQueryTermsMap{{"", {"http"}}};
  snippet_spec_.set_max_window_utf32_length(40);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  content = GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://mail.google.com/calendar/google/",
                          "https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("https", "https://mail.google.com/calendar/google/"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("http", "http"));

  // Window that's smaller than the input size should not return any matches.
  query_terms = SectionRestrictQueryTermsMap{{"", {"google"}}};
  snippet_spec_.set_max_window_utf32_length(10);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(0));

  // Test case with more than two matches
  document =
      DocumentBuilder()
          .SetKey("icing", "url/1")
          .SetSchema("urlType")
          .AddStringProperty("url", "https://www.google.com/calendar/google/")
          .Build();

  // Prefix query with both url split-token and suffix-token matches
  query_terms = SectionRestrictQueryTermsMap{{"", {"google"}}};
  snippet_spec_.set_max_window_utf32_length(39);

  snippet = snippet_retriever_->RetrieveSnippet(
      query_terms, MATCH_PREFIX, snippet_spec_, document, section_mask);

  ASSERT_THAT(snippet.entries(), SizeIs(1));
  EXPECT_THAT(snippet.entries(0).property_name(), "url");

  content = GetString(&document, snippet.entries(0).property_name());

  EXPECT_THAT(GetWindows(content, snippet.entries(0)),
              ElementsAre("https://www.google.com/calendar/google/",
                          "https://www.google.com/calendar/google/",
                          "https://www.google.com/calendar/google/"));
  EXPECT_THAT(GetMatches(content, snippet.entries(0)),
              ElementsAre("google", "google", "google.com/calendar/google/"));
  EXPECT_THAT(GetSubMatches(content, snippet.entries(0)),
              ElementsAre("google", "google", "google"));
}
#endif  // ENABLE_URL_TOKENIZER

}  // namespace

}  // namespace lib
}  // namespace icing
