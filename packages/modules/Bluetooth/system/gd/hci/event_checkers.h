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

#include "hci/hci_packets.h"
#include "os/log.h"

namespace bluetooth {
namespace hci {

template <class T>
void check_complete(CommandCompleteView view) {
  ASSERT(view.IsValid());
  auto status_view = T::Create(view);
  if (!status_view.IsValid()) {
    LOG_ERROR("Invalid packet, opcode 0x%02hx", view.GetCommandOpCode());
    return;
  }
  ErrorCode status = status_view.GetStatus();
  OpCode op_code = status_view.GetCommandOpCode();
  if (status != ErrorCode::SUCCESS) {
    std::string error_code = ErrorCodeText(status);
    LOG_ERROR("Error code %s, opcode 0x%02hx", error_code.c_str(), op_code);
    return;
  }
}

template <class T>
void check_status(CommandStatusView view) {
  ASSERT(view.IsValid());
  auto status_view = T::Create(view);
  if (!status_view.IsValid()) {
    LOG_ERROR("Invalid packet, opcode 0x%02hx", view.GetCommandOpCode());
    return;
  }
  ErrorCode status = status_view.GetStatus();
  OpCode op_code = status_view.GetCommandOpCode();
  if (status != ErrorCode::SUCCESS) {
    std::string error_code = ErrorCodeText(status);
    LOG_ERROR("Error code %s, opcode 0x%02hx", error_code.c_str(), op_code);
    return;
  }
}

}  // namespace hci
}  // namespace bluetooth
