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

#include "utils/tflite/string_projection_base.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "utils/hash/cityhash.h"
#include "utils/hash/farmhash.h"
#include "flatbuffers/flexbuffers.h"
#include "tensorflow/lite/context.h"
#include "tensorflow_models/seq_flow_lite/tflite_ops/quantization_util.h"

namespace tflite {
namespace ops {
namespace custom {
namespace libtextclassifier3 {
namespace string_projection {

namespace {
const int32_t kMaxInputChars = 300;

const int kOutputLabel = 0;
const char kFastHash[] = "[DEV] FastHash";
const char kAXB[] = "[DEV] AXB";

const int kSeedSize = sizeof(float);
const int kInputItemBytes = sizeof(int32_t);
const int kKeyBytes = sizeof(float) + sizeof(int32_t);

}  // namespace

StringProjectionOpBase::StringProjectionOpBase(
    const flexbuffers::Map& custom_options)
    : hash_function_(custom_options["hash_function"].AsTypedVector()),
      num_hash_(custom_options["num_hash"].AsInt32()),
      num_bits_(custom_options["num_bits"].AsInt32()),
      binary_projection_(custom_options["binary_projection"].AsBool()),
      hash_method_(custom_options["hash_method"].ToString()),
      axb_scale_(custom_options["axb_scale"].AsFloat()) {
  skip_gram_params_ = {
      .ngram_size = custom_options["ngram_size"].AsInt32(),
      .max_skip_size = custom_options["max_skip_size"].AsInt32(),
      .include_all_ngrams = custom_options["include_all_ngrams"].AsBool(),
      .preprocess = custom_options["preprocess"].AsBool(),
      .char_level = custom_options["char_level"].AsBool(),
      .remove_punctuation = custom_options["remove_punctuation"].AsBool(),
      .max_input_chars = kMaxInputChars,
  };
}

void StringProjectionOpBase::GetFeatureWeights(
    const std::unordered_map<std::string, int>& feature_counts,
    std::vector<std::vector<int64_t>>* batch_ids,
    std::vector<std::vector<float>>* batch_weights) {
  std::vector<int64_t> ids;
  std::vector<float> weights;
  for (const auto& iter : feature_counts) {
    if (hash_method_ == kFastHash || hash_method_ == kAXB) {
      int32_t feature_id =
          tc3farmhash::CityHash64(iter.first.c_str(), iter.first.size());
      ids.push_back(feature_id);
      weights.push_back(iter.second);
    } else {
      int64_t feature_id =
          tc3farmhash::Fingerprint64(iter.first.c_str(), iter.first.size());
      ids.push_back(feature_id);
      weights.push_back(iter.second);
    }
  }

  batch_ids->push_back(ids);
  batch_weights->push_back(weights);
}

void StringProjectionOpBase::DenseLshProjection(
    const int batch_size, const std::vector<std::vector<int64_t>>& batch_ids,
    const std::vector<std::vector<float>>& batch_weights,
    TfLiteTensor* output) {
  auto key = std::unique_ptr<char[]>(
      new char[kKeyBytes]);  // NOLINT: modernize-make-unique

  if (output->type == kTfLiteFloat32) {
    for (int batch = 0; batch < batch_size; ++batch) {
      const std::vector<int64_t>& input = batch_ids[batch];
      const std::vector<float>& weight = batch_weights[batch];

      for (int i = 0; i < num_hash_; i++) {
        for (int j = 0; j < num_bits_; j++) {
          int hash_bit = i * num_bits_ + j;
          float seed = hash_function_[hash_bit].AsFloat();
          float bit = running_sign_bit(input, weight, seed, key.get());
          output->data.f[batch * num_hash_ * num_bits_ + hash_bit] = bit;
        }
      }
    }
  } else if (output->type == kTfLiteUInt8) {
    const float inverse_scale = 1.0 / output->params.scale;
    for (int batch = 0; batch < batch_size; ++batch) {
      const std::vector<int64_t>& input = batch_ids[batch];
      const std::vector<float>& weight = batch_weights[batch];

      for (int i = 0; i < num_hash_; i++) {
        for (int j = 0; j < num_bits_; j++) {
          int hash_bit = i * num_bits_ + j;
          float seed = hash_function_[hash_bit].AsFloat();
          float bit = running_sign_bit(input, weight, seed, key.get());
          output->data.uint8[batch * num_hash_ * num_bits_ + hash_bit] =
              seq_flow_lite::PodQuantize(bit, output->params.zero_point,
                                         inverse_scale);
        }
      }
    }
  }
}

namespace {

int32_t hash32(int32_t value, uint32_t seed) {
  uint32_t hash = value;
  hash = (hash ^ 61) ^ (hash >> 16);
  hash = hash + (hash << 3);
  hash = hash ^ (hash >> 4);
  hash = hash * seed;
  hash = hash ^ (hash >> 15);
  return static_cast<int32_t>(hash);
}

double axb(int32_t value, float seed, float scale) {
  // Convert seed to a larger scale of range, multiplier is 1e5 to avoid
  // precision difference on different hardware.
  int64_t hash_signature =
      static_cast<int64_t>(scale) * static_cast<int64_t>(seed * 1e5) * value;
  hash_signature %= 0x100000000;
  hash_signature = fabs(hash_signature);
  if (hash_signature >= 0x80000000) {
    hash_signature -= 0x100000000;
  }
  return hash_signature;
}

}  // namespace

// Compute sign bit of dot product of hash(seed, input) and weight.
float StringProjectionOpBase::running_sign_bit(
    const std::vector<int64_t>& input, const std::vector<float>& weight,
    float seed, char* key) {
  double score = 0.0;
  memcpy(key, &seed, kSeedSize);
  int cnt = 0;
  for (int i = 0; i < input.size(); ++i) {
    if (weight[i] == 0.0) continue;
    cnt++;
    const int32_t curr_input = input[i];
    memcpy(key + kSeedSize, &curr_input, kInputItemBytes);

    // Create running hash id and value for current dimension.
    if (hash_method_ == kFastHash) {
      int32_t hash_signature =
          hash32(input[i], *reinterpret_cast<uint32_t*>(&seed));
      score += static_cast<double>(weight[i]) * hash_signature;
    } else if (hash_method_ == kAXB) {
      score += weight[i] * axb(input[i], seed, axb_scale_);
    } else {
      int64_t hash_signature = tc3farmhash::Fingerprint64(key, kKeyBytes);
      double running_value = static_cast<double>(hash_signature);
      score += weight[i] * running_value;
    }
  }

  const double inverse_normalizer = 0.00000000046566129;
  if (!binary_projection_) {
    if (hash_method_ == kAXB) {
      return tanh(score / cnt * inverse_normalizer);
    } else {
      return tanh(score * inverse_normalizer);
    }
  }

  return (score > 0) ? 1 : 0;
}

void Free(TfLiteContext* context, void* buffer) {
  delete reinterpret_cast<StringProjectionOpBase*>(buffer);
}

TfLiteStatus Resize(TfLiteContext* context, TfLiteNode* node) {
  auto* op = reinterpret_cast<StringProjectionOpBase*>(node->user_data);

  // The shape of the output should be the shape of the input + a new inner
  // dimension equal to the number of features.
  TfLiteIntArray* input_shape = op->GetInputShape(context, node);
  TfLiteIntArray* output_shape = TfLiteIntArrayCreate(input_shape->size + 1);
  for (int i = 0; i < input_shape->size; ++i) {
    output_shape->data[i] = input_shape->data[i];
  }
  output_shape->data[input_shape->size] = op->num_hash() * op->num_bits();
  context->ResizeTensor(context,
                        &context->tensors[node->outputs->data[kOutputLabel]],
                        output_shape);
  return kTfLiteOk;
}

TfLiteStatus Eval(TfLiteContext* context, TfLiteNode* node) {
  auto* op = reinterpret_cast<StringProjectionOpBase*>(node->user_data);

  TfLiteTensor* label = &context->tensors[node->outputs->data[kOutputLabel]];

  TfLiteIntArray* input_shape = op->GetInputShape(context, node);
  int input_size = 1;
  for (int i = 0; i < input_shape->size; ++i) {
    input_size *= input_shape->data[i];
  }

  TF_LITE_ENSURE_STATUS(op->InitializeInput(context, node));

  std::vector<std::vector<int64_t>> batch_ids;
  std::vector<std::vector<float>> batch_weights;
  for (int i = 0; i < input_size; ++i) {
    std::unordered_map<std::string, int> feature_counts =
        op->ExtractSkipGrams(i);
    op->GetFeatureWeights(feature_counts, &batch_ids, &batch_weights);
  }

  op->DenseLshProjection(input_size, batch_ids, batch_weights, label);

  op->FinalizeInput();

  return kTfLiteOk;
}

}  // namespace string_projection
}  // namespace libtextclassifier3
}  // namespace custom
}  // namespace ops
}  // namespace tflite
