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

#ifndef LIBTEXTCLASSIFIER_ACTIONS_SENSITIVE_CLASSIFIER_BASE_H_
#define LIBTEXTCLASSIFIER_ACTIONS_SENSITIVE_CLASSIFIER_BASE_H_

#include <memory>
#include <utility>

#include "actions/types.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {
class SensitiveTopicModelBase {
 public:
  // Returns a pair: a boolean, which is  true if the topic is sensitive, and a
  // score.
  virtual std::pair<bool, float> Eval(const UnicodeText& text) const = 0;
  virtual std::pair<bool, float> EvalConversation(
      const Conversation& conversation, int num_messages) const = 0;

  virtual ~SensitiveTopicModelBase() {}
};
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ACTIONS_SENSITIVE_CLASSIFIER_BASE_H_
