/*
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#include <gtest/gtest.h>

#include "hci/hci_layer.h"

namespace bluetooth {
namespace security {

using common::ContextualOnceCallback;
using hci::BitInserter;
using hci::CommandCompleteView;
using hci::CommandStatusView;
using hci::EventBuilder;
using hci::EventCode;
using hci::EventView;
using hci::HciLayer;
using hci::kLittleEndian;
using hci::PacketView;

namespace {

PacketView<kLittleEndian> GetPacketView(std::unique_ptr<packet::BasePacketBuilder> packet) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter i(*bytes);
  bytes->reserve(packet->size());
  packet->Serialize(i);
  return packet::PacketView<packet::kLittleEndian>(bytes);
}

class CommandQueueEntry {
 public:
  CommandQueueEntry(
      std::unique_ptr<hci::CommandBuilder> command_packet,
      ContextualOnceCallback<void(CommandCompleteView)> on_complete_function)
      : command(std::move(command_packet)), waiting_for_status_(false), on_complete(std::move(on_complete_function)) {}

  CommandQueueEntry(
      std::unique_ptr<hci::CommandBuilder> command_packet,
      ContextualOnceCallback<void(CommandStatusView)> on_status_function)
      : command(std::move(command_packet)), waiting_for_status_(true), on_status(std::move(on_status_function)) {}

  std::unique_ptr<hci::CommandBuilder> command;
  bool waiting_for_status_;
  ContextualOnceCallback<void(CommandStatusView)> on_status;
  ContextualOnceCallback<void(CommandCompleteView)> on_complete;
};

}  // namespace

class FakeHciLayer : public HciLayer {
 public:
  void EnqueueCommand(
      std::unique_ptr<hci::CommandBuilder> command,
      ContextualOnceCallback<void(CommandStatusView)> on_status) override {
    auto command_queue_entry = std::make_unique<CommandQueueEntry>(std::move(command), std::move(on_status));
    command_queue_.push(std::move(command_queue_entry));
  }

  void EnqueueCommand(
      std::unique_ptr<hci::CommandBuilder> command,
      ContextualOnceCallback<void(CommandCompleteView)> on_complete) override {
    auto command_queue_entry = std::make_unique<CommandQueueEntry>(std::move(command), std::move(on_complete));
    command_queue_.push(std::move(command_queue_entry));
  }

  std::unique_ptr<CommandQueueEntry> GetLastCommand() {
    EXPECT_FALSE(command_queue_.empty());
    auto last = std::move(command_queue_.front());
    command_queue_.pop();
    return last;
  }

  void RegisterEventHandler(EventCode event_code, common::ContextualCallback<void(EventView)> event_handler) override {
    registered_events_[event_code] = event_handler;
  }

  void UnregisterEventHandler(EventCode event_code) override {
    registered_events_.erase(event_code);
  }

  void IncomingEvent(std::unique_ptr<EventBuilder> event_builder) {
    auto packet = GetPacketView(std::move(event_builder));
    EventView event = EventView::Create(packet);
    ASSERT_TRUE(event.IsValid());
    EventCode event_code = event.GetEventCode();
    ASSERT_TRUE(registered_events_.find(event_code) != registered_events_.end());
    registered_events_[event_code].Invoke(event);
  }

  void ListDependencies(ModuleList* /* list */) const override {}
  void Start() override {}
  void Stop() override {}

 private:
  std::map<EventCode, common::ContextualCallback<void(EventView)>> registered_events_;
  std::queue<std::unique_ptr<CommandQueueEntry>> command_queue_;
};

}  // namespace security
}  // namespace bluetooth
