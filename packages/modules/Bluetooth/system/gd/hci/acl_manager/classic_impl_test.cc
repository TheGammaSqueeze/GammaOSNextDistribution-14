/*
 * Copyright 2023 The Android Open Source Project
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

#include "hci/acl_manager/classic_impl.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>

#include "common/bidi_queue.h"
#include "common/testing/log_capture.h"
#include "hci/acl_manager/acl_scheduler.h"
#include "hci/acl_manager/connection_callbacks_mock.h"
#include "hci/acl_manager/connection_management_callbacks_mock.h"
#include "hci/address.h"
#include "hci/controller_mock.h"
#include "hci/hci_layer_fake.h"
#include "hci/hci_packets.h"
#include "os/handler.h"
#include "packet/bit_inserter.h"
#include "packet/raw_builder.h"

using namespace bluetooth;
using namespace std::chrono_literals;

using ::bluetooth::common::BidiQueue;
using ::bluetooth::common::Callback;
using ::bluetooth::os::Handler;
using ::bluetooth::os::Thread;
using ::bluetooth::packet::BitInserter;
using ::bluetooth::packet::RawBuilder;
using ::bluetooth::testing::LogCapture;

using ::testing::_;
using ::testing::DoAll;
using ::testing::Eq;
using ::testing::Field;
using ::testing::Mock;
using ::testing::MockFunction;
using ::testing::SaveArg;
using ::testing::VariantWith;
using ::testing::WithArg;

namespace {
constexpr bool kCrashOnUnknownHandle = true;
constexpr char kFixedAddress[] = "c0:aa:bb:cc:dd:ee";
const bluetooth::hci::Address kRemoteAddress =
    bluetooth::hci::Address({0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
[[maybe_unused]] constexpr uint16_t kHciHandle = 123;
template <typename B>
std::shared_ptr<std::vector<uint8_t>> Serialize(std::unique_ptr<B> build) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter bi(*bytes);
  build->Serialize(bi);
  return bytes;
}

template <typename T>
T CreateCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(hci::CommandView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

template <typename T>
T CreateAclCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateCommandView<hci::AclCommandView>(bytes));
}

template <typename T>
T CreateConnectionManagementCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateAclCommandView<hci::ConnectionManagementCommandView>(bytes));
}

template <typename T>
T CreateSecurityCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateCommandView<hci::SecurityCommandView>(bytes));
}

template <typename T>
T CreateEventView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

[[maybe_unused]] hci::CommandCompleteView ReturnCommandComplete(
    hci::OpCode op_code, hci::ErrorCode error_code) {
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(error_code)};
  auto builder = hci::CommandCompleteBuilder::Create(
      uint8_t{1}, op_code, std::make_unique<RawBuilder>(success_vector));
  auto bytes = Serialize<hci::CommandCompleteBuilder>(std::move(builder));
  return hci::CommandCompleteView::Create(
      hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

[[maybe_unused]] hci::CommandStatusView ReturnCommandStatus(
    hci::OpCode op_code, hci::ErrorCode error_code) {
  std::vector<uint8_t> success_vector{static_cast<uint8_t>(error_code)};
  auto builder = hci::CommandStatusBuilder::Create(
      hci::ErrorCode::SUCCESS, uint8_t{1}, op_code, std::make_unique<RawBuilder>(success_vector));
  auto bytes = Serialize<hci::CommandStatusBuilder>(std::move(builder));
  return hci::CommandStatusView::Create(
      hci::EventView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

bool handle_outgoing_connection_ = false;
bool handle_incoming_connection_ = false;

}  // namespace

namespace bluetooth {
namespace hci {
namespace acl_manager {

class MockAclScheduler : public AclScheduler {
 public:
  virtual void ReportAclConnectionCompletion(
      Address /* address */,
      common::ContextualOnceCallback<void()> handle_outgoing_connection,
      common::ContextualOnceCallback<void()> handle_incoming_connection,
      common::ContextualOnceCallback<void(std::string)> handle_unknown_connection) override {
    if (handle_outgoing_connection_) {
      handle_outgoing_connection.InvokeIfNotEmpty();
      return;
    }

    if (handle_incoming_connection_) {
      handle_incoming_connection.InvokeIfNotEmpty();
    } else {
      handle_unknown_connection.InvokeIfNotEmpty("set_of_incoming_connecting_addresses()");
    }
  }
};

PacketView<kLittleEndian> GetPacketView(std::unique_ptr<packet::BasePacketBuilder> packet) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter i(*bytes);
  bytes->reserve(packet->size());
  packet->Serialize(i);
  return packet::PacketView<packet::kLittleEndian>(bytes);
}

class ClassicImplTest : public ::testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::SetAllForTesting();
    thread_ = new Thread("thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    hci_layer_ = new HciLayerFake();
    controller_ = new testing::MockController();

    EXPECT_CALL(*controller_, GetNumAclPacketBuffers);
    EXPECT_CALL(*controller_, GetAclPacketLength);
    EXPECT_CALL(*controller_, GetLeBufferSize);
    EXPECT_CALL(*controller_, RegisterCompletedAclPacketsCallback);
    EXPECT_CALL(*controller_, UnregisterCompletedAclPacketsCallback);

    round_robin_scheduler_ =
        new acl_manager::RoundRobinScheduler(handler_, controller_, hci_queue_.GetUpEnd());
    hci_queue_.GetDownEnd()->RegisterDequeue(
        handler_, common::Bind(&ClassicImplTest::HciDownEndDequeue, common::Unretained(this)));
    acl_scheduler_ = new MockAclScheduler();
    rnr_ = new RemoteNameRequestModule();
    classic_impl_ = new acl_manager::classic_impl(
        hci_layer_,
        controller_,
        handler_,
        round_robin_scheduler_,
        kCrashOnUnknownHandle,
        acl_scheduler_,
        rnr_);
    classic_impl_->handle_register_callbacks(&mock_connection_callback_, handler_);

    Address address;
    Address::FromString(kFixedAddress, address);
  }

  void TearDown() override {
    sync_handler();
    delete classic_impl_;

    hci_queue_.GetDownEnd()->UnregisterDequeue();

    delete rnr_;
    delete acl_scheduler_;
    delete round_robin_scheduler_;
    delete controller_;
    delete hci_layer_;

    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  MockAclScheduler* acl_scheduler_;
  RemoteNameRequestModule* rnr_;

  void sync_handler() {
    thread_->GetReactor()->WaitForIdle(2s);
  }

  void HciDownEndDequeue() {
    auto packet = hci_queue_.GetDownEnd()->TryDequeue();
    // Convert from a Builder to a View
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    bluetooth::packet::BitInserter i(*bytes);
    bytes->reserve(packet->size());
    packet->Serialize(i);
    auto packet_view = bluetooth::packet::PacketView<bluetooth::packet::kLittleEndian>(bytes);
    AclView acl_packet_view = AclView::Create(packet_view);
    ASSERT_TRUE(acl_packet_view.IsValid());
    PacketView<true> count_view = acl_packet_view.GetPayload();
    sent_acl_packets_.push(acl_packet_view);

    packet_count_--;
    if (packet_count_ == 0) {
      packet_promise_->set_value();
      packet_promise_ = nullptr;
    }
  }

 protected:
  Address remote_address_;

  uint16_t packet_count_;
  std::unique_ptr<std::promise<void>> packet_promise_;
  std::unique_ptr<std::future<void>> packet_future_;
  std::queue<AclView> sent_acl_packets_;

  BidiQueue<AclView, AclBuilder> hci_queue_{3};

  Thread* thread_;
  Handler* handler_;
  HciLayerFake* hci_layer_{nullptr};
  testing::MockController* controller_;
  acl_manager::RoundRobinScheduler* round_robin_scheduler_{nullptr};

  acl_manager::MockConnectionCallback mock_connection_callback_;
  acl_manager::MockConnectionManagementCallbacks connection_management_callbacks_;

  struct acl_manager::classic_impl* classic_impl_;
};

TEST_F(ClassicImplTest, nop) {}

TEST_F(ClassicImplTest, on_classic_event_CONNECTION_COMPLETE__SUCCESS) {
  // Expecting valid response
  EXPECT_CALL(mock_connection_callback_, OnConnectSuccess);
  handle_outgoing_connection_ = true;

  auto command = ConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      kHciHandle,
      kRemoteAddress,
      LinkType::ACL,
      bluetooth::hci::Enable::ENABLED);

  auto bytes = Serialize<ConnectionCompleteBuilder>(std::move(command));
  auto view = CreateEventView<hci::ConnectionCompleteView>(bytes);
  ASSERT_TRUE(view.IsValid());
  classic_impl_->on_classic_event(view);
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
