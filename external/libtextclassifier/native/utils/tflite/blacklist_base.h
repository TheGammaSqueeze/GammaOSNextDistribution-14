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

#ifndef LIBTEXTCLASSIFIER_UTILS_TFLITE_BLACKLIST_BASE_H_
#define LIBTEXTCLASSIFIER_UTILS_TFLITE_BLACKLIST_BASE_H_

#include "absl/container/flat_hash_set.h"
#include "flatbuffers/flexbuffers.h"
#include "tensorflow/lite/context.h"

namespace tflite {
namespace ops {
namespace custom {
namespace libtextclassifier3 {
namespace blacklist {

/*
 * A framework for writing ops that generates prediction vectors using a
 * blacklist.
 *
 * Input is defined by the specific implementation.
 *
 * Attributes:
 *   blacklist:           string[n]
 *     Terms in the blacklist.
 *   blacklist_category:  int[n]
 *     Category for each term in the blacklist.  Each category must be in
 *     [0, categories).
 *   categories:          int[]
 *     Total number of categories.
 *   negative_categories: int[]
 *     Total number of negative categories.
 *
 * Output:
 *   tensor[0]: Category indicators for each message, float[..., categories]
 *
 */

class BlacklistOpBase {
 public:
  explicit BlacklistOpBase(const flexbuffers::Map& custom_options)
      : categories_(custom_options["categories"].AsInt32()),
        negative_categories_(custom_options["negative_categories"].AsInt32()) {}

  virtual ~BlacklistOpBase() {}

  int categories() const { return categories_; }
  int negative_categories() const { return negative_categories_; }

  virtual TfLiteStatus InitializeInput(TfLiteContext* context,
                                       TfLiteNode* node) = 0;
  virtual absl::flat_hash_set<int> GetCategories(int i) const = 0;
  virtual void FinalizeInput() = 0;

  // Returns the input shape.  TfLiteIntArray is owned by the object.
  virtual TfLiteIntArray* GetInputShape(TfLiteContext* context,
                                        TfLiteNode* node) = 0;

 private:
  int categories_;
  int negative_categories_;
};

// Individual ops should define an Init() function that returns a
// BlacklistOpBase.

void Free(TfLiteContext* context, void* buffer);

TfLiteStatus Resize(TfLiteContext* context, TfLiteNode* node);

TfLiteStatus Eval(TfLiteContext* context, TfLiteNode* node);
}  // namespace blacklist
}  // namespace libtextclassifier3
}  // namespace custom
}  // namespace ops
}  // namespace tflite

#endif  // LIBTEXTCLASSIFIER_UTILS_TFLITE_BLACKLIST_BASE_H_
