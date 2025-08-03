/*
 * Copyright 2022 The Android Open Source Project
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

#include <future>
#include <map>
#include <vector>

#include "common/bind.h"
#include "hci/address.h"
#include "hci/hci_layer.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace hci {

packet::PacketView<packet::kLittleEndian> GetPacketView(
    std::unique_ptr<packet::BasePacketBuilder> packet);

std::unique_ptr<BasePacketBuilder> NextPayload(uint16_t handle);

class HciLayerFake : public HciLayer {
 public:
  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> command,
      common::ContextualOnceCallback<void(CommandStatusView)> on_status) override;

  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> command,
      common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) override;

  CommandView GetCommand();

  CommandView GetCommand(OpCode op_code);

  void AssertNoQueuedCommand();

  void RegisterEventHandler(EventCode event_code, common::ContextualCallback<void(EventView)> event_handler) override;

  void UnregisterEventHandler(EventCode event_code) override;

  void RegisterLeEventHandler(
      SubeventCode subevent_code, common::ContextualCallback<void(LeMetaEventView)> event_handler) override;

  void UnregisterLeEventHandler(SubeventCode subevent_code) override;

  void IncomingEvent(std::unique_ptr<EventBuilder> event_builder);

  void IncomingLeMetaEvent(std::unique_ptr<LeMetaEventBuilder> event_builder);

  void CommandCompleteCallback(EventView event);

  void CommandStatusCallback(EventView event);

  void IncomingAclData(uint16_t handle);

  void IncomingAclData(uint16_t handle, std::unique_ptr<AclBuilder> acl_builder);

  void AssertNoOutgoingAclData();

  packet::PacketView<packet::kLittleEndian> OutgoingAclData();

  common::BidiQueueEnd<AclBuilder, AclView>* GetAclQueueEnd() override;

  void Disconnect(uint16_t handle, ErrorCode reason) override;

 protected:
  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  void Stop() override;

 private:
  void InitEmptyCommand();
  void do_disconnect(uint16_t handle, ErrorCode reason);

  // Handler-only state. Mutexes are not needed when accessing these fields.
  std::list<common::ContextualOnceCallback<void(CommandCompleteView)>> command_complete_callbacks;
  std::list<common::ContextualOnceCallback<void(CommandStatusView)>> command_status_callbacks;
  std::map<EventCode, common::ContextualCallback<void(EventView)>> registered_events_;
  std::map<SubeventCode, common::ContextualCallback<void(LeMetaEventView)>> registered_le_events_;

  // thread-safe
  common::BidiQueue<AclView, AclBuilder> acl_queue_{3 /* TODO: Set queue depth */};

  // Most operations must acquire this mutex before manipulating shared state. The ONLY exception
  // is blocking on a promise, IF your thread is the only one mutating it. Note that SETTING a
  // promise REQUIRES a lock, since another thread may replace the promise while you are doing so.
  mutable std::mutex mutex_{};

  // Shared state between the test and stack threads
  std::queue<std::unique_ptr<CommandBuilder>> command_queue_;

  // We start with Consumed=Set, Command=Unset.
  // When a command is enqueued, we set Command=set
  // When a command is popped, we block until Command=Set, then (if the queue is now empty) we
  // reset Command=Unset and set Consumed=Set. This way we emulate a blocking queue.
  std::promise<void> command_promise_{};  // Set when at least one command is in the queue
  std::future<void> command_future_ =
      command_promise_.get_future();  // GetCommand() blocks until this is fulfilled

  CommandView empty_command_view_ = CommandView::Create(
      PacketView<packet::kLittleEndian>(std::make_shared<std::vector<uint8_t>>()));
};

}  // namespace hci
}  // namespace bluetooth
