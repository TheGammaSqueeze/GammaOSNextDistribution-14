/* Copyright 2020 The TensorFlow Authors. All Rights Reserved.

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

#include "tensorflow_lite_support/cc/task/text/nlclassifier/bert_nl_classifier.h"

#include <fcntl.h>

#include "android-base/file.h"
#include "tensorflow_lite_support/cc/port/gmock.h"
#include "tensorflow_lite_support/cc/port/gtest.h"
#include "tensorflow_lite_support/cc/task/core/task_utils.h"

namespace tflite {
namespace task {
namespace text {
namespace nlclassifier {

namespace {

using ::android::base::GetExecutableDirectory;
using ::testing::HasSubstr;
using ::tflite::support::kTfLiteSupportPayload;
using ::tflite::support::StatusOr;
using ::tflite::support::TfLiteSupportStatus;
using ::tflite::task::core::Category;
using ::tflite::task::core::LoadBinaryContent;

constexpr char kTestModelPath[] =
    "/tensorflow_lite_support/cc/test/testdata/task/text/"
    "test_model_nl_classifier_bert.tflite";

constexpr char kInvalidModelPath[] = "i/do/not/exist.tflite";

constexpr int kMaxSeqLen = 128;

TEST(BertNLClassifierTest, TestNLClassifierCreationFilePath) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromFile(test_model_path);
  EXPECT_TRUE(classifier.ok());
}

TEST(BertNLClassifierTest, TestNLClassifierCreationBinary) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  std::string model_buffer = LoadBinaryContent(test_model_path.c_str());
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromBuffer(model_buffer.data(), model_buffer.size());
  EXPECT_TRUE(classifier.ok());
}

TEST(BertNLClassifierTest, TestNLClassifierCreationFailure) {
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromFile(kInvalidModelPath);

  EXPECT_EQ(classifier.status().code(), absl::StatusCode::kNotFound);
  EXPECT_THAT(classifier.status().message(),
              HasSubstr("Unable to open file at i/do/not/exist.tflite"));
  EXPECT_THAT(classifier.status().GetPayload(kTfLiteSupportPayload),
              testing::Optional(absl::Cord(
                  absl::StrCat(TfLiteSupportStatus::kFileNotFoundError))));
}

Category* GetCategoryWithClassName(const std::string& class_name,
                                   std::vector<Category>& categories) {
  for (Category& category : categories) {
    if (category.class_name == class_name) {
      return &category;
    }
  }
  return nullptr;
}

void verify_classifier(std::unique_ptr<BertNLClassifier> classifier,
                       bool verify_positive) {
  if (verify_positive) {
    tflite::support::StatusOr<std::vector<core::Category>> results =
        classifier->ClassifyText("unflinchingly bleak and desperate");

    EXPECT_TRUE(results.ok());
    EXPECT_GT(GetCategoryWithClassName("negative", results.value())->score,
              GetCategoryWithClassName("positive", results.value())->score);
  } else {
    tflite::support::StatusOr<std::vector<core::Category>> results =
        classifier->ClassifyText("it's a charming and often affecting journey");

    EXPECT_TRUE(results.ok());
    EXPECT_GT(GetCategoryWithClassName("positive", results.value())->score,
              GetCategoryWithClassName("negative", results.value())->score);
  }
}

TEST(BertNLClassifierTest, TestNLClassifier_ClassifyNegative) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  std::string model_buffer = LoadBinaryContent(test_model_path.c_str());
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromBuffer(model_buffer.data(), model_buffer.size());
  EXPECT_TRUE(classifier.ok());

  verify_classifier(std::move(*classifier), false);
}

TEST(BertNLClassifierTest, TestNLClassifier_ClassifyPositive) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  std::string model_buffer = LoadBinaryContent(test_model_path.c_str());
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromBuffer(model_buffer.data(), model_buffer.size());
  EXPECT_TRUE(classifier.ok());

  verify_classifier(std::move(*classifier), true);
}

TEST(BertNLClassifierTest, TestNLClassifierFd_ClassifyPositive) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromFd(open(test_model_path.c_str(), O_RDONLY));
  EXPECT_TRUE(classifier.ok());

  verify_classifier(std::move(*classifier), false);
}

TEST(BertNLClassifierTest, TestNLClassifierFd_ClassifyNegative) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromFd(open(test_model_path.c_str(), O_RDONLY));
  EXPECT_TRUE(classifier.ok());

  verify_classifier(std::move(*classifier), true);
}

// BertNLClassifier limits the input sequence to kMaxSeqLen, test when input is
// longer than this the classifier still works correctly.
TEST(BertNLClassifierTest, TestNLClassifier_ClassifyLongPositive_notOOB) {
  std::string test_model_path = absl::StrCat(GetExecutableDirectory(), kTestModelPath);
  std::string model_buffer = LoadBinaryContent(test_model_path.c_str());
  std::stringstream ss_for_positive_review;
  ss_for_positive_review
      << "it's a charming and often affecting journey and this is a long";
  for (int i = 0; i < kMaxSeqLen; ++i) {
    ss_for_positive_review << " long";
  }
  ss_for_positive_review << " movie review";
  StatusOr<std::unique_ptr<BertNLClassifier>> classifier =
      BertNLClassifier::CreateFromBuffer(model_buffer.data(), model_buffer.size());
  EXPECT_TRUE(classifier.ok());

  tflite::support::StatusOr<std::vector<core::Category>> results =
      classifier.value()->ClassifyText(ss_for_positive_review.str());

  EXPECT_TRUE(results.ok());
  EXPECT_GT(GetCategoryWithClassName("positive", results.value())->score,
            GetCategoryWithClassName("negative", results.value())->score);
}

}  // namespace

}  // namespace nlclassifier
}  // namespace text
}  // namespace task
}  // namespace tflite
