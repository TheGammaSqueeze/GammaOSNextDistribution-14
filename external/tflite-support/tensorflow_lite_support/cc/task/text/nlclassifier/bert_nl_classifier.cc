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

#include <stddef.h>

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/status/status.h"
#include "absl/strings/ascii.h"
#include "absl/strings/str_format.h"
#include "tensorflow/lite/c/common.h"
#include "tensorflow/lite/core/api/op_resolver.h"
#include "tensorflow/lite/string_type.h"
#include "tensorflow_lite_support/cc/common.h"
#include "tensorflow_lite_support/cc/port/status_macros.h"
#include "tensorflow_lite_support/cc/task/core/category.h"
#include "tensorflow_lite_support/cc/task/core/task_api_factory.h"
#include "tensorflow_lite_support/cc/task/core/task_utils.h"
#include "tensorflow_lite_support/cc/task/text/nlclassifier/nl_classifier.h"
#include "tensorflow_lite_support/cc/text/tokenizers/tokenizer.h"
#include "tensorflow_lite_support/cc/text/tokenizers/tokenizer_utils.h"
#include "tensorflow_lite_support/metadata/cc/metadata_extractor.h"

namespace tflite {
namespace task {
namespace text {
namespace nlclassifier {

using ::tflite::support::CreateStatusWithPayload;
using ::tflite::support::StatusOr;
using ::tflite::support::TfLiteSupportStatus;
using ::tflite::support::text::tokenizer::CreateTokenizerFromProcessUnit;
using ::tflite::support::text::tokenizer::TokenizerResult;
using ::tflite::task::core::FindTensorByName;
using ::tflite::task::core::PopulateTensor;

namespace {
constexpr char kIdsTensorName[] = "ids";
constexpr char kMaskTensorName[] = "mask";
constexpr char kSegmentIdsTensorName[] = "segment_ids";
constexpr int kIdsTensorIndex = 0;
constexpr int kMaskTensorIndex = 1;
constexpr int kSegmentIdsTensorIndex = 2;
constexpr char kScoreTensorName[] = "probability";
constexpr char kClassificationToken[] = "[CLS]";
constexpr char kSeparator[] = "[SEP]";
constexpr int kTokenizerProcessUnitIndex = 0;
}  // namespace

absl::Status BertNLClassifier::Preprocess(
    const std::vector<TfLiteTensor*>& input_tensors, const std::string& input) {
  auto* input_tensor_metadatas =
      GetMetadataExtractor()->GetInputTensorMetadata();
  auto* ids_tensor =
      FindTensorByName(input_tensors, input_tensor_metadatas, kIdsTensorName);
  auto* mask_tensor =
      FindTensorByName(input_tensors, input_tensor_metadatas, kMaskTensorName);
  auto* segment_ids_tensor = FindTensorByName(
      input_tensors, input_tensor_metadatas, kSegmentIdsTensorName);

  std::string processed_input = input;
  absl::AsciiStrToLower(&processed_input);

  TokenizerResult input_tokenize_results;
  input_tokenize_results = tokenizer_->Tokenize(processed_input);

  // Offset by 2 to account for [CLS] and [SEP]
  int input_tokens_size =
      static_cast<int>(input_tokenize_results.subwords.size()) + 2;
  int input_tensor_length = input_tokens_size;
  if (!input_tensors_are_dynamic_) {
    input_tokens_size = std::min(kMaxSeqLen, input_tokens_size);
    input_tensor_length = kMaxSeqLen;
  } else {
    GetTfLiteEngine()->interpreter()->ResizeInputTensorStrict(kIdsTensorIndex,
                                                    {1, input_tensor_length});
    GetTfLiteEngine()->interpreter()->ResizeInputTensorStrict(kMaskTensorIndex,
                                                    {1, input_tensor_length});
    GetTfLiteEngine()->interpreter()->ResizeInputTensorStrict(kSegmentIdsTensorIndex,
                                                    {1, input_tensor_length});
    GetTfLiteEngine()->interpreter()->AllocateTensors();
  }

  std::vector<std::string> input_tokens;
  input_tokens.reserve(input_tokens_size);
  input_tokens.push_back(std::string(kClassificationToken));
  for (int i = 0; i < input_tokens_size - 2; ++i) {
    input_tokens.push_back(std::move(input_tokenize_results.subwords[i]));
  }
  input_tokens.push_back(std::string(kSeparator));

  std::vector<int> input_ids(input_tensor_length, 0);
  std::vector<int> input_mask(input_tensor_length, 0);
  // Convert tokens back into ids and set mask
  for (int i = 0; i < input_tokens.size(); ++i) {
    tokenizer_->LookupId(input_tokens[i], &input_ids[i]);
    input_mask[i] = 1;
  }
  //                           |<--------input_tensor_length------->|
  // input_ids                 [CLS] s1  s2...  sn [SEP]  0  0...  0
  // input_masks                 1    1   1...  1    1    0  0...  0
  // segment_ids                 0    0   0...  0    0    0  0...  0

  PopulateTensor(input_ids, ids_tensor);
  PopulateTensor(input_mask, mask_tensor);
  PopulateTensor(std::vector<int>(input_tensor_length, 0), segment_ids_tensor);

  return absl::OkStatus();
}

StatusOr<std::vector<core::Category>> BertNLClassifier::Postprocess(
    const std::vector<const TfLiteTensor*>& output_tensors,
    const std::string& /*input*/) {
  if (output_tensors.size() != 1) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInvalidArgument,
        absl::StrFormat("BertNLClassifier models are expected to have only 1 "
                        "output, found %d",
                        output_tensors.size()),
        TfLiteSupportStatus::kInvalidNumOutputTensorsError);
  }
  const TfLiteTensor* scores = FindTensorByName(
      output_tensors, GetMetadataExtractor()->GetOutputTensorMetadata(),
      kScoreTensorName);

  // optional labels extracted from metadata
  return BuildResults(scores, /*labels=*/nullptr);
}

StatusOr<std::unique_ptr<BertNLClassifier>>
BertNLClassifier::CreateFromFile(
    const std::string& path_to_model_with_metadata,
    std::unique_ptr<tflite::OpResolver> resolver) {
  std::unique_ptr<BertNLClassifier> bert_nl_classifier;
  ASSIGN_OR_RETURN(bert_nl_classifier,
                   core::TaskAPIFactory::CreateFromFile<BertNLClassifier>(
                       path_to_model_with_metadata, std::move(resolver)));
  RETURN_IF_ERROR(bert_nl_classifier->InitializeFromMetadata());
  return std::move(bert_nl_classifier);
}

StatusOr<std::unique_ptr<BertNLClassifier>>
BertNLClassifier::CreateFromBuffer(
    const char* model_with_metadata_buffer_data,
    size_t model_with_metadata_buffer_size,
    std::unique_ptr<tflite::OpResolver> resolver) {
  std::unique_ptr<BertNLClassifier> bert_nl_classifier;
  ASSIGN_OR_RETURN(bert_nl_classifier,
                   core::TaskAPIFactory::CreateFromBuffer<BertNLClassifier>(
                       model_with_metadata_buffer_data,
                       model_with_metadata_buffer_size, std::move(resolver)));
  RETURN_IF_ERROR(bert_nl_classifier->InitializeFromMetadata());
  return std::move(bert_nl_classifier);
}

StatusOr<std::unique_ptr<BertNLClassifier>> BertNLClassifier::CreateFromFd(
    int fd, std::unique_ptr<tflite::OpResolver> resolver) {
  std::unique_ptr<BertNLClassifier> bert_nl_classifier;
  ASSIGN_OR_RETURN(
      bert_nl_classifier,
      core::TaskAPIFactory::CreateFromFileDescriptor<BertNLClassifier>(
          fd, std::move(resolver)));
  RETURN_IF_ERROR(bert_nl_classifier->InitializeFromMetadata());
  return std::move(bert_nl_classifier);
}

absl::Status BertNLClassifier::InitializeFromMetadata() {
  // Set up mandatory tokenizer.
  const ProcessUnit* tokenizer_process_unit =
      GetMetadataExtractor()->GetInputProcessUnit(kTokenizerProcessUnitIndex);
  if (tokenizer_process_unit == nullptr) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInvalidArgument,
        "No input process unit found from metadata.",
        TfLiteSupportStatus::kMetadataInvalidTokenizerError);
  }
  ASSIGN_OR_RETURN(tokenizer_,
                   CreateTokenizerFromProcessUnit(tokenizer_process_unit,
                                                  GetMetadataExtractor()));

  // Set up optional label vector.
  TrySetLabelFromMetadata(
      GetMetadataExtractor()->GetOutputTensorMetadata(kOutputTensorIndex))
      .IgnoreError();

  auto* input_tensor_metadatas =
      GetMetadataExtractor()->GetInputTensorMetadata();
  const auto& input_tensors = GetInputTensors();
  const auto& ids_tensor = *FindTensorByName(input_tensors, input_tensor_metadatas,
                                             kIdsTensorName);
  const auto& mask_tensor = *FindTensorByName(input_tensors, input_tensor_metadatas,
                                              kMaskTensorName);
  const auto& segment_ids_tensor = *FindTensorByName(input_tensors, input_tensor_metadatas,
                                                     kSegmentIdsTensorName);
  if (ids_tensor.dims->size != 2 || mask_tensor.dims->size != 2 ||
      segment_ids_tensor.dims->size != 2) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInternal,
        absl::StrFormat(
            "The three input tensors in Bert models are expected to have dim "
            "2, but got ids_tensor (%d), mask_tensor (%d), segment_ids_tensor "
            "(%d).",
            ids_tensor.dims->size, mask_tensor.dims->size,
            segment_ids_tensor.dims->size),
        TfLiteSupportStatus::kInvalidInputTensorDimensionsError);
  }
  if (ids_tensor.dims->data[0] != 1 || mask_tensor.dims->data[0] != 1 ||
      segment_ids_tensor.dims->data[0] != 1) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInternal,
        absl::StrFormat(
            "The three input tensors in Bert models are expected to have same "
            "batch size 1, but got ids_tensor (%d), mask_tensor (%d), "
            "segment_ids_tensor (%d).",
            ids_tensor.dims->data[0], mask_tensor.dims->data[0],
            segment_ids_tensor.dims->data[0]),
        TfLiteSupportStatus::kInvalidInputTensorSizeError);
  }
  if (ids_tensor.dims->data[1] != mask_tensor.dims->data[1] ||
      ids_tensor.dims->data[1] != segment_ids_tensor.dims->data[1]) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInternal,
        absl::StrFormat("The three input tensors in Bert models are "
                        "expected to have same length, but got ids_tensor "
                        "(%d), mask_tensor (%d), segment_ids_tensor (%d).",
                        ids_tensor.dims->data[1], mask_tensor.dims->data[1],
                        segment_ids_tensor.dims->data[1]),
        TfLiteSupportStatus::kInvalidInputTensorSizeError);
  }

  // If some tensor does not have a size 2 dims_signature, then we
  // assume the input is not dynamic.
  if (ids_tensor.dims_signature->size != 2 ||
      mask_tensor.dims_signature->size != 2 ||
      segment_ids_tensor.dims_signature->size != 2) {
    return absl::OkStatus();
  }

  if (ids_tensor.dims_signature->data[1] == -1 &&
      mask_tensor.dims_signature->data[1] == -1 &&
      segment_ids_tensor.dims_signature->data[1] == -1) {
    input_tensors_are_dynamic_ = true;
  } else if (ids_tensor.dims_signature->data[1] == -1 ||
             mask_tensor.dims_signature->data[1] == -1 ||
             segment_ids_tensor.dims_signature->data[1] == -1) {
    return CreateStatusWithPayload(
        absl::StatusCode::kInternal,
        "Input tensors contain a mix of static and dynamic tensors",
        TfLiteSupportStatus::kInvalidInputTensorSizeError);
  }

  return absl::OkStatus();
}

}  // namespace nlclassifier
}  // namespace text
}  // namespace task
}  // namespace tflite
