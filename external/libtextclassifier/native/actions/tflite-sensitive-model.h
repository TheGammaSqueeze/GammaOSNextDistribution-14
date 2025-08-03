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

#ifndef LIBTEXTCLASSIFIER_ACTIONS_TFLITE_SENSITIVE_MODEL_H_
#define LIBTEXTCLASSIFIER_ACTIONS_TFLITE_SENSITIVE_MODEL_H_

#include <memory>

#include "actions/actions_model_generated.h"
#include "actions/sensitive-classifier-base.h"
#include "utils/tflite-model-executor.h"

namespace libtextclassifier3 {
class TFLiteSensitiveModel : public SensitiveTopicModelBase {
 public:
  // The object keeps but doesn't own model_config.
  static std::unique_ptr<TFLiteSensitiveModel> Create(
      const TFLiteSensitiveClassifierConfig* model_config);

  std::pair<bool, float> Eval(const UnicodeText& text) const override;
  std::pair<bool, float> EvalConversation(const Conversation& conversation,
                                          int num_messages) const override;

 private:
  explicit TFLiteSensitiveModel(
      const TFLiteSensitiveClassifierConfig* model_config);
  const TFLiteSensitiveClassifierConfig* model_config_ = nullptr;  // not owned.
  std::unique_ptr<const TfLiteModelExecutor> model_executor_;
};
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ACTIONS_TFLITE_SENSITIVE_MODEL_H_
