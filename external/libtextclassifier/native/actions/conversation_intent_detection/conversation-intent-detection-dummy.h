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

#ifndef LIBTEXTCLASSIFIER_ACTIONS_CONVERSATION_INTENT_DETECTION_CONVERSATION_INTENT_DETECTION_DUMMY_H_
#define LIBTEXTCLASSIFIER_ACTIONS_CONVERSATION_INTENT_DETECTION_CONVERSATION_INTENT_DETECTION_DUMMY_H_

#include <string>
#include <vector>

#include "actions/types.h"
#include "utils/base/status.h"
#include "utils/base/statusor.h"

namespace libtextclassifier3 {

// A dummy implementation of conversation intent detection.
class ConversationIntentDetection {
 public:
  ConversationIntentDetection() {}

  Status Initialize(const std::string& serialized_config) { return Status::OK; }

  StatusOr<std::vector<ActionSuggestion>> SuggestActions(
      const Conversation& conversation, const ActionSuggestionOptions& options =
                                            ActionSuggestionOptions()) const {
    return Status::OK;
  }
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ACTIONS_CONVERSATION_INTENT_DETECTION_CONVERSATION_INTENT_DETECTION_DUMMY_H_
