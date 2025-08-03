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

#ifndef LIBTEXTCLASSIFIER_UTILS_TFLITE_STRING_PROJECTION_BASE_H_
#define LIBTEXTCLASSIFIER_UTILS_TFLITE_STRING_PROJECTION_BASE_H_

/**
 * String projection op used in Self-Governing Neural Network (SGNN)
 * and other ProjectionNet models for text prediction.
 * The code is copied/adapted from
 * learning/expander/pod/deep_pod/tflite_handlers/
 */

#include <string>
#include <unordered_map>
#include <vector>

#include "flatbuffers/flexbuffers.h"
#include "tensorflow/lite/context.h"

namespace tflite {
namespace ops {
namespace custom {
namespace libtextclassifier3 {
namespace string_projection {

struct SkipGramParams {
  // Num of tokens in ngram.
  int ngram_size;

  // Max num of tokens to skip in skip gram.
  int max_skip_size;

  // True when include all k-grams where k <= ngram_size.
  bool include_all_ngrams;

  // True when include preprocess.
  bool preprocess;

  // True when tokens are chars, false when tokens are whitespace separated.
  bool char_level;

  // True when punctuations are removed.
  bool remove_punctuation;

  // Max num of chars to process in input.
  int max_input_chars;
};

/**
 * A framework for writing TFLite ops that convert strings to integers via LSH
 * projections.  Input is defined by the specific implementation.
 * NOTE: Only supports dense projection.
 *
 * Attributes:
 *   num_hash:           int[]
 *     number of hash functions
 *   num_bits:           int[]
 *     number of bits in each hash function
 *   hash_function:      float[num_hash * num_bits]
 *     hash_functions used to generate projections
 *   ngram_size:         int[]
 *     maximum number of tokens in skipgrams
 *   max_skip_size:      int[]
 *     maximum number of tokens to skip between tokens in skipgrams.
 *   include_all_ngrams: bool[]
 *     if false, only use skipgrams with ngram_size tokens
 *   preprocess:         bool[]
 *     if true, normalize input strings (lower case, remove punctuation)
 *   hash_method:        string[]
 *     hashing function to use
 *   char_level:         bool[]
 *     if true, treat each character as a token
 *   binary_projection:  bool[]
 *     if true, output features are 0 or 1
 *   remove_punctuation: bool[]
 *     if true, remove punctuation during normalization/preprocessing
 *
 * Output:
 *   tensor[0]: computed projections. float32[..., num_func * num_bits]
 */

class StringProjectionOpBase {
 public:
  explicit StringProjectionOpBase(const flexbuffers::Map& custom_options);

  virtual ~StringProjectionOpBase() {}

  void GetFeatureWeights(
      const std::unordered_map<std::string, int>& feature_counts,
      std::vector<std::vector<int64_t>>* batch_ids,
      std::vector<std::vector<float>>* batch_weights);

  void DenseLshProjection(const int batch_size,
                          const std::vector<std::vector<int64_t>>& batch_ids,
                          const std::vector<std::vector<float>>& batch_weights,
                          TfLiteTensor* output);

  inline int num_hash() { return num_hash_; }
  inline int num_bits() { return num_bits_; }
  virtual TfLiteStatus InitializeInput(TfLiteContext* context,
                                       TfLiteNode* node) = 0;
  virtual std::unordered_map<std::string, int> ExtractSkipGrams(int i) = 0;
  virtual void FinalizeInput() = 0;

  // Returns the input shape.  TfLiteIntArray is owned by the object.
  virtual TfLiteIntArray* GetInputShape(TfLiteContext* context,
                                        TfLiteNode* node) = 0;

 protected:
  SkipGramParams& skip_gram_params() { return skip_gram_params_; }

 private:
  ::flexbuffers::TypedVector hash_function_;
  int num_hash_;
  int num_bits_;
  bool binary_projection_;
  std::string hash_method_;
  float axb_scale_;
  SkipGramParams skip_gram_params_;

  // Compute sign bit of dot product of hash(seed, input) and weight.
  float running_sign_bit(const std::vector<int64_t>& input,
                         const std::vector<float>& weight, float seed,
                         char* key);
};

// Individual ops should define an Init() function that returns a
// BlacklistOpBase.

void Free(TfLiteContext* context, void* buffer);

TfLiteStatus Resize(TfLiteContext* context, TfLiteNode* node);

TfLiteStatus Eval(TfLiteContext* context, TfLiteNode* node);

}  // namespace string_projection
}  // namespace libtextclassifier3
}  // namespace custom
}  // namespace ops
}  // namespace tflite

#endif  // LIBTEXTCLASSIFIER_UTILS_TFLITE_STRING_PROJECTION_BASE_H_
