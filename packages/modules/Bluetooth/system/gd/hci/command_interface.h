/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include "common/contextual_callback.h"
#include "hci/hci_packets.h"
#include "os/handler.h"
#include "os/utils.h"

namespace bluetooth {
namespace hci {

template <typename T>
class CommandInterface {
 public:
  CommandInterface() = default;
  CommandInterface(const CommandInterface&) = delete;
  CommandInterface& operator=(const CommandInterface&) = delete;

  virtual ~CommandInterface() = default;

  virtual void EnqueueCommand(std::unique_ptr<T> command,
                              common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) = 0;

  virtual void EnqueueCommand(std::unique_ptr<T> command,
                              common::ContextualOnceCallback<void(CommandStatusView)> on_status) = 0;
};
}  // namespace hci
}  // namespace bluetooth
