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

#include "actions/tflite-sensitive-model.h"

#include <utility>

#include "actions/actions_model_generated.h"
#include "actions/types.h"

namespace libtextclassifier3 {
namespace {
const char kNotSensitive[] = "NOT_SENSITIVE";
}  // namespace

std::unique_ptr<TFLiteSensitiveModel> TFLiteSensitiveModel::Create(
    const TFLiteSensitiveClassifierConfig* model_config) {
  auto result_model = std::unique_ptr<TFLiteSensitiveModel>(
      new TFLiteSensitiveModel(model_config));
  if (result_model->model_executor_ == nullptr) {
    return nullptr;
  }
  return result_model;
}

std::pair<bool, float> TFLiteSensitiveModel::Eval(
    const UnicodeText& text) const {
  // Create a conversation with one message and classify it.
  Conversation conversation;
  conversation.messages.emplace_back();
  conversation.messages.front().text = text.ToUTF8String();

  return EvalConversation(conversation, 1);
}

std::pair<bool, float> TFLiteSensitiveModel::EvalConversation(
    const Conversation& conversation, int num_messages) const {
  if (model_executor_ == nullptr) {
    return std::make_pair(false, 0.0f);
  }
  const auto interpreter = model_executor_->CreateInterpreter();

  if (interpreter->AllocateTensors() != kTfLiteOk) {
    // TODO(mgubin):  report error that tensors can't be allocated.
    return std::make_pair(false, 0.0f);
  }
  // The sensitive model is actually an ordinary TFLite model with Lingua API,
  // prepare texts and user_ids similar way, it doesn't use timediffs.
  std::vector<std::string> context;
  std::vector<int> user_ids;
  context.reserve(num_messages);
  user_ids.reserve(num_messages);

  // Gather last `num_messages` messages from the conversation.
  for (int i = conversation.messages.size() - num_messages;
       i < conversation.messages.size(); i++) {
    const ConversationMessage& message = conversation.messages[i];
    context.push_back(message.text);
    user_ids.push_back(message.user_id);
  }

  // Allocate tensors.
  //

  if (model_config_->model_spec()->input_context() >= 0) {
    if (model_config_->model_spec()->input_length_to_pad() > 0) {
      context.resize(model_config_->model_spec()->input_length_to_pad());
    }
    model_executor_->SetInput<std::string>(
        model_config_->model_spec()->input_context(), context,
        interpreter.get());
  }
  if (model_config_->model_spec()->input_context_length() >= 0) {
    model_executor_->SetInput<int>(
        model_config_->model_spec()->input_context_length(), context.size(),
        interpreter.get());
  }

  // Num suggestions is always locked to 3.
  if (model_config_->model_spec()->input_num_suggestions() > 0) {
    model_executor_->SetInput<int>(
        model_config_->model_spec()->input_num_suggestions(), 3,
        interpreter.get());
  }

  if (interpreter->Invoke() != kTfLiteOk) {
    // TODO(mgubin): Report a error about invoke.
    return std::make_pair(false, 0.0f);
  }

  // Check that the prediction is not-sensitive.
  const std::vector<tflite::StringRef> replies =
      model_executor_->Output<tflite::StringRef>(
          model_config_->model_spec()->output_replies(), interpreter.get());
  const TensorView<float> scores = model_executor_->OutputView<float>(
      model_config_->model_spec()->output_replies_scores(), interpreter.get());
  for (int i = 0; i < replies.size(); ++i) {
    const auto reply = replies[i];
    if (reply.len != sizeof(kNotSensitive) - 1 &&
        0 != memcmp(reply.str, kNotSensitive, sizeof(kNotSensitive))) {
      const auto score = scores.data()[i];
      if (score >= model_config_->threshold()) {
        return std::make_pair(true, score);
      }
    }
  }
  return std::make_pair(false, 1.0);
}

TFLiteSensitiveModel::TFLiteSensitiveModel(
    const TFLiteSensitiveClassifierConfig* model_config)
    : model_config_(model_config),
      model_executor_(TfLiteModelExecutor::FromBuffer(
          model_config->model_spec()->tflite_model())) {}
}  // namespace libtextclassifier3
