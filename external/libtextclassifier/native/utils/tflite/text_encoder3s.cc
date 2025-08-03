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

#include "utils/tflite/text_encoder3s.h"

#include <memory>
#include <vector>

#include "utils/base/logging.h"
#include "utils/strings/stringpiece.h"
#include "utils/tflite/encoder_common.h"
#include "utils/tflite/text_encoder_config_generated.h"
#include "utils/tokenfree/byte_encoder.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/flexbuffers.h"
#include "tensorflow/lite/kernels/kernel_util.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/string_util.h"

namespace libtextclassifier3 {
namespace {

// Input parameters for the op.
constexpr int kInputTextInd = 0;

constexpr int kTextLengthInd = 1;
constexpr int kMaxLengthInd = 2;
constexpr int kInputAttrInd = 3;

// Output parameters for the op.
constexpr int kOutputEncodedInd = 0;
constexpr int kOutputPositionInd = 1;
constexpr int kOutputLengthsInd = 2;
constexpr int kOutputAttrInd = 3;

// Initializes text encoder object from serialized parameters.
void* Initialize(TfLiteContext* context, const char* buffer, size_t length) {
  std::unique_ptr<ByteEncoder> encoder(new ByteEncoder());
  return encoder.release();
}

void Free(TfLiteContext* context, void* buffer) {
  delete reinterpret_cast<ByteEncoder*>(buffer);
}

namespace {
TfLiteStatus ResizeOutputTensors(TfLiteContext* context, TfLiteNode* node,
                                 int max_output_length) {
  TfLiteTensor& output_encoded =
      context->tensors[node->outputs->data[kOutputEncodedInd]];

  TF_LITE_ENSURE_OK(
      context, context->ResizeTensor(
                   context, &output_encoded,
                   CreateIntArray({kEncoderBatchSize, max_output_length})));
  TfLiteTensor& output_positions =
      context->tensors[node->outputs->data[kOutputPositionInd]];

  TF_LITE_ENSURE_OK(
      context, context->ResizeTensor(
                   context, &output_positions,
                   CreateIntArray({kEncoderBatchSize, max_output_length})));

  const int num_output_attrs = node->outputs->size - kOutputAttrInd;
  for (int i = 0; i < num_output_attrs; ++i) {
    TfLiteTensor& output =
        context->tensors[node->outputs->data[kOutputAttrInd + i]];
    TF_LITE_ENSURE_OK(
        context, context->ResizeTensor(
                     context, &output,
                     CreateIntArray({kEncoderBatchSize, max_output_length})));
  }
  return kTfLiteOk;
}
}  // namespace

TfLiteStatus Prepare(TfLiteContext* context, TfLiteNode* node) {
  // Check that the batch dimension is kEncoderBatchSize.
  const TfLiteTensor& input_text =
      context->tensors[node->inputs->data[kInputTextInd]];
  TF_LITE_ENSURE_EQ(context, input_text.dims->size, kEncoderInputRank);
  TF_LITE_ENSURE_EQ(context, input_text.dims->data[0], kEncoderBatchSize);

  TfLiteTensor& output_lengths =
      context->tensors[node->outputs->data[kOutputLengthsInd]];

  TfLiteTensor& output_encoded =
      context->tensors[node->outputs->data[kOutputEncodedInd]];
  TfLiteTensor& output_positions =
      context->tensors[node->outputs->data[kOutputPositionInd]];
  output_encoded.type = kTfLiteInt32;
  output_positions.type = kTfLiteInt32;
  output_lengths.type = kTfLiteInt32;

  TF_LITE_ENSURE_OK(context,
                    context->ResizeTensor(context, &output_lengths,
                                          CreateIntArray({kEncoderBatchSize})));

  // Check that there are enough outputs for attributes.
  const int num_output_attrs = node->outputs->size - kOutputAttrInd;
  TF_LITE_ENSURE_EQ(context, node->inputs->size - kInputAttrInd,
                    num_output_attrs);

  // Copy attribute types from input to output tensors.
  for (int i = 0; i < num_output_attrs; ++i) {
    TfLiteTensor& input =
        context->tensors[node->inputs->data[kInputAttrInd + i]];
    TfLiteTensor& output =
        context->tensors[node->outputs->data[kOutputAttrInd + i]];
    output.type = input.type;
  }

  const TfLiteTensor& output_length =
      context->tensors[node->inputs->data[kMaxLengthInd]];

  if (tflite::IsConstantTensor(&output_length)) {
    return ResizeOutputTensors(context, node, output_length.data.i64[0]);
  } else {
    tflite::SetTensorToDynamic(&output_encoded);
    tflite::SetTensorToDynamic(&output_positions);
    for (int i = 0; i < num_output_attrs; ++i) {
      TfLiteTensor& output_attr =
          context->tensors[node->outputs->data[kOutputAttrInd + i]];
      tflite::SetTensorToDynamic(&output_attr);
    }
  }

  return kTfLiteOk;
}

TfLiteStatus Eval(TfLiteContext* context, TfLiteNode* node) {
  if (node->user_data == nullptr) {
    return kTfLiteError;
  }
  auto text_encoder = reinterpret_cast<ByteEncoder*>(node->user_data);
  const TfLiteTensor& input_text =
      context->tensors[node->inputs->data[kInputTextInd]];
  const int num_strings_in_tensor = tflite::GetStringCount(&input_text);
  const int num_strings =
      context->tensors[node->inputs->data[kTextLengthInd]].data.i32[0];

  // Check that the number of strings is not bigger than the input tensor size.
  TF_LITE_ENSURE(context, num_strings_in_tensor >= num_strings);

  TfLiteTensor& output_encoded =
      context->tensors[node->outputs->data[kOutputEncodedInd]];
  if (tflite::IsDynamicTensor(&output_encoded)) {
    const TfLiteTensor& output_length =
        context->tensors[node->inputs->data[kMaxLengthInd]];
    TF_LITE_ENSURE_OK(
        context, ResizeOutputTensors(context, node, output_length.data.i64[0]));
  }
  TfLiteTensor& output_positions =
      context->tensors[node->outputs->data[kOutputPositionInd]];

  std::vector<int> encoded_total;
  std::vector<int> encoded_positions;
  std::vector<int> encoded_offsets;
  encoded_offsets.reserve(num_strings);
  const int max_output_length = output_encoded.dims->data[1];
  const int max_encoded_position = max_output_length;

  for (int i = 0; i < num_strings; ++i) {
    const auto& strref = tflite::GetString(&input_text, i);
    std::vector<int64_t> encoded;
    text_encoder->Encode(
        libtextclassifier3::StringPiece(strref.str, strref.len), &encoded);
    encoded_total.insert(encoded_total.end(), encoded.begin(), encoded.end());
    encoded_offsets.push_back(encoded_total.size());
    for (int i = 0; i < encoded.size(); ++i) {
      encoded_positions.push_back(std::min(i, max_encoded_position - 1));
    }
  }

  // Copy encoding to output tensor.
  const int start_offset =
      std::max(0, static_cast<int>(encoded_total.size()) - max_output_length);
  int output_offset = 0;
  int32_t* output_buffer = output_encoded.data.i32;
  int32_t* output_positions_buffer = output_positions.data.i32;
  for (int i = start_offset; i < encoded_total.size(); ++i, ++output_offset) {
    output_buffer[output_offset] = encoded_total[i];
    output_positions_buffer[output_offset] = encoded_positions[i];
  }

  // Save output encoded length.
  TfLiteTensor& output_lengths =
      context->tensors[node->outputs->data[kOutputLengthsInd]];
  output_lengths.data.i32[0] = output_offset;

  // Do padding.
  for (; output_offset < max_output_length; ++output_offset) {
    output_buffer[output_offset] = 0;
    output_positions_buffer[output_offset] = 0;
  }

  // Process attributes, all checks of sizes and types are done in Prepare.
  const int num_output_attrs = node->outputs->size - kOutputAttrInd;
  TF_LITE_ENSURE_EQ(context, node->inputs->size - kInputAttrInd,
                    num_output_attrs);
  for (int i = 0; i < num_output_attrs; ++i) {
    TfLiteStatus attr_status = CopyValuesToTensorAndPadOrTruncate(
        context->tensors[node->inputs->data[kInputAttrInd + i]],
        encoded_offsets, start_offset, context,
        &context->tensors[node->outputs->data[kOutputAttrInd + i]]);
    if (attr_status != kTfLiteOk) {
      return attr_status;
    }
  }

  return kTfLiteOk;
}

}  // namespace
}  // namespace libtextclassifier3

namespace tflite {
namespace ops {
namespace custom {

TfLiteRegistration* Register_TEXT_ENCODER3S() {
  static TfLiteRegistration registration = {
      libtextclassifier3::Initialize, libtextclassifier3::Free,
      libtextclassifier3::Prepare, libtextclassifier3::Eval};
  return &registration;
}

}  // namespace custom
}  // namespace ops
}  // namespace tflite
