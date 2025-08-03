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
#include <memory>

#include "tensorflow/lite/kernels/register.h"

#include "tensorflow_lite_support/custom_ops/kernel/unsorted_segment.h"

namespace tflite {
namespace task {
// Create a custom op resolver to provide the unsorted_segment_prod op
// required by the bert_nl_classifier and rb_model for BertNLClassifier.
std::unique_ptr<tflite::OpResolver> CreateOpResolver() {  // NOLINT
  std::unique_ptr<tflite::ops::builtin::BuiltinOpResolver> resolver(
    new tflite::ops::builtin::BuiltinOpResolver);
  // "UnsortedSegmentProd" is the name used by unsorted_segment_prod op when
  // when converting SavedModel to tflite using the size optimization approach.
  resolver->AddCustom("UnsortedSegmentProd",
                      tflite::ops::custom::Register_UNSORTED_SEGMENT_PROD());
  // "FlexUnsortedSegmentProd" is the name used by unsorted_segment_prod op when
  // when converting SavedModel to tflite using the the other approaches.
  resolver->AddCustom("FlexUnsortedSegmentProd",
                      tflite::ops::custom::Register_UNSORTED_SEGMENT_PROD());
  return std::unique_ptr<tflite::OpResolver>(std::move(resolver));
}

}  // namespace task
}  // namespace tflite