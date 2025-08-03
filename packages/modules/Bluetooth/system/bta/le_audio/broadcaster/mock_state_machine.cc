/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "mock_state_machine.h"

using namespace le_audio::broadcaster;

IBroadcastStateMachineCallbacks* callbacks;
AdvertisingCallbacks* adv_callbacks;
void BroadcastStateMachine::Initialize(IBroadcastStateMachineCallbacks* cb,
                                       AdvertisingCallbacks* adv_cb) {
  callbacks = cb;
  adv_callbacks = adv_cb;
}

std::unique_ptr<BroadcastStateMachine> BroadcastStateMachine::CreateInstance(
    BroadcastStateMachineConfig msg) {
  auto instance = std::make_unique<MockBroadcastStateMachine>(
      std::move(msg), callbacks, adv_callbacks);
  MockBroadcastStateMachine::last_instance_ = instance.get();
  return std::move(instance);
}

namespace le_audio {
namespace broadcaster {

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachine::Message& state) {
  static const char* char_value_[BroadcastStateMachine::MESSAGE_COUNT] = {
      "START", "SUSPEND", "STOP"};
  os << char_value_[static_cast<uint8_t>(state)];
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachine::State& state) {
  static const char* char_value_[BroadcastStateMachine::STATE_COUNT] = {
      "STOPPED", "CONFIGURING", "CONFIGURED", "STOPPING", "STREAMING"};
  os << char_value_[static_cast<uint8_t>(state)];
  return os;
}

std::ostream& operator<<(std::ostream& os, const BigConfig& config) {
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachineConfig& config) {
  return os;
}

std::ostream& operator<<(std::ostream& os,
                         const BroadcastStateMachine& machine) {
  return os;
}

}  // namespace broadcaster
}  // namespace le_audio

uint8_t MockBroadcastStateMachine::instance_counter_ = 0;
MockBroadcastStateMachine* MockBroadcastStateMachine::last_instance_ = nullptr;
