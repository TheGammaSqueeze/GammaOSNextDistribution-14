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

#include "utils/tflite/blacklist.h"

#include "utils/tflite/blacklist_base.h"
#include "utils/tflite/skipgram_finder.h"
#include "flatbuffers/flexbuffers.h"

namespace tflite {
namespace ops {
namespace custom {

namespace libtextclassifier3 {
namespace blacklist {

// Generates prediction vectors for input strings using a skipgram blacklist.
// This uses the framework in `blacklist_base.h`, with the implementation detail
// that the input is a string tensor of messages and the terms are skipgrams.
class BlacklistOp : public BlacklistOpBase {
 public:
  explicit BlacklistOp(const flexbuffers::Map& custom_options)
      : BlacklistOpBase(custom_options),
        skipgram_finder_(custom_options["max_skip_size"].AsInt32()),
        input_(nullptr) {
    auto blacklist = custom_options["blacklist"].AsTypedVector();
    auto blacklist_category =
        custom_options["blacklist_category"].AsTypedVector();
    for (int i = 0; i < blacklist.size(); i++) {
      int category = blacklist_category[i].AsInt32();
      flexbuffers::String s = blacklist[i].AsString();
      skipgram_finder_.AddSkipgram(std::string(s.c_str(), s.length()),
                                   category);
    }
  }

  TfLiteStatus InitializeInput(TfLiteContext* context,
                               TfLiteNode* node) override {
    input_ = &context->tensors[node->inputs->data[kInputMessage]];
    return kTfLiteOk;
  }

  absl::flat_hash_set<int> GetCategories(int i) const override {
    StringRef input = GetString(input_, i);
    return skipgram_finder_.FindSkipgrams(std::string(input.str, input.len));
  }

  void FinalizeInput() override { input_ = nullptr; }

  TfLiteIntArray* GetInputShape(TfLiteContext* context,
                                TfLiteNode* node) override {
    return context->tensors[node->inputs->data[kInputMessage]].dims;
  }

 private:
  ::libtextclassifier3::SkipgramFinder skipgram_finder_;
  TfLiteTensor* input_;

  static constexpr int kInputMessage = 0;
};

void* BlacklistOpInit(TfLiteContext* context, const char* buffer,
                      size_t length) {
  const uint8_t* buffer_t = reinterpret_cast<const uint8_t*>(buffer);
  return new BlacklistOp(flexbuffers::GetRoot(buffer_t, length).AsMap());
}

}  // namespace blacklist

TfLiteRegistration* Register_BLACKLIST() {
  static TfLiteRegistration r = {libtextclassifier3::blacklist::BlacklistOpInit,
                                 libtextclassifier3::blacklist::Free,
                                 libtextclassifier3::blacklist::Resize,
                                 libtextclassifier3::blacklist::Eval};
  return &r;
}

}  // namespace libtextclassifier3
}  // namespace custom
}  // namespace ops
}  // namespace tflite
