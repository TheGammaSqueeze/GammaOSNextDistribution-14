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

#include "hci/acl_manager/le_acl_connection.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>
#include <cstdint>
#include <future>
#include <list>
#include <memory>
#include <mutex>
#include <queue>
#include <vector>

#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "hci/address_with_type.h"
#include "hci/hci_layer_fake.h"
#include "hci/hci_packets.h"
#include "hci/le_acl_connection_interface.h"
#include "os/handler.h"
#include "os/log.h"
#include "os/thread.h"

using namespace bluetooth;
using namespace std::chrono_literals;

template <typename T>
T CreateCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(hci::CommandView::Create(hci::PacketView<hci::kLittleEndian>(bytes)));
}

template <typename T>
T CreateAclCommandView(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return T::Create(CreateCommandView<hci::AclCommandView>(bytes));
}

constexpr uint16_t kConnectionHandle = 123;
constexpr size_t kQueueSize = 10;

constexpr uint16_t kIntervalMin = 0x20;
constexpr uint16_t kIntervalMax = 0x40;
constexpr uint16_t kLatency = 0x60;
constexpr uint16_t kTimeout = 0x80;
constexpr uint16_t kContinuationNumber = 0x32;

namespace bluetooth::hci::acl_manager {

namespace {

class TestLeConnectionManagementCallbacks : public hci::acl_manager::LeConnectionManagementCallbacks {
  void OnConnectionUpdate(
      hci::ErrorCode /* hci_status */,
      uint16_t /* connection_interval */,
      uint16_t /* connection_latency */,
      uint16_t /* supervision_timeout */) override {}
  virtual void OnDataLengthChange(
      uint16_t /* tx_octets */,
      uint16_t /* tx_time */,
      uint16_t /* rx_octets */,
      uint16_t /* rx_time */) override {}
  virtual void OnDisconnection(hci::ErrorCode /* reason */) override {}
  virtual void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode /* hci_status */,
      uint8_t /* lmp_version */,
      uint16_t /* manufacturer_name */,
      uint16_t /* sub_version */) override {}
  virtual void OnLeReadRemoteFeaturesComplete(
      hci::ErrorCode /* hci_status */, uint64_t /* features */) override {}
  virtual void OnPhyUpdate(
      hci::ErrorCode /* hci_status */, uint8_t /* tx_phy */, uint8_t /* rx_phy */) override {}
  MOCK_METHOD(
      void,
      OnLeSubrateChange,
      (hci::ErrorCode hci_status,
       uint16_t subrate_factor,
       uint16_t peripheral_latency,
       uint16_t continuation_number,
       uint16_t supervision_timeout),
      (override));

  // give access to private method for test:
  friend class LeAclConnectionTest;
  FRIEND_TEST(LeAclConnectionTest, LeSubrateRequest_success);
  FRIEND_TEST(LeAclConnectionTest, LeSubrateRequest_error);
};

class TestLeAclConnectionInterface : public hci::LeAclConnectionInterface {
 private:
  void EnqueueCommand(
      std::unique_ptr<hci::AclCommandBuilder> command,
      common::ContextualOnceCallback<void(hci::CommandStatusView)> on_status) override {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    command_queue_.push(std::move(command));
    command_status_callbacks.push_back(std::move(on_status));
    if (command_promise_ != nullptr) {
      std::promise<void>* prom = command_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

  void EnqueueCommand(
      std::unique_ptr<hci::AclCommandBuilder> command,
      common::ContextualOnceCallback<void(hci::CommandCompleteView)> on_complete) override {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    command_queue_.push(std::move(command));
    command_complete_callbacks.push_back(std::move(on_complete));
    if (command_promise_ != nullptr) {
      std::promise<void>* prom = command_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

 public:
  virtual ~TestLeAclConnectionInterface() = default;

  std::unique_ptr<hci::CommandBuilder> DequeueCommand() {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    auto packet = std::move(command_queue_.front());
    command_queue_.pop();
    return std::move(packet);
  }

  std::shared_ptr<std::vector<uint8_t>> DequeueCommandBytes() {
    auto command = DequeueCommand();
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    packet::BitInserter bi(*bytes);
    command->Serialize(bi);
    return bytes;
  }

  common::ContextualOnceCallback<void(hci::CommandStatusView)> DequeueStatusCallback() {
    auto on_status = std::move(command_status_callbacks.front());
    command_status_callbacks.pop_front();
    return std::move(on_status);
  }

  bool IsPacketQueueEmpty() const {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    return command_queue_.empty();
  }

  size_t NumberOfQueuedCommands() const {
    const std::lock_guard<std::mutex> lock(command_queue_mutex_);
    return command_queue_.size();
  }

 private:
  std::list<common::ContextualOnceCallback<void(hci::CommandCompleteView)>> command_complete_callbacks;
  std::list<common::ContextualOnceCallback<void(hci::CommandStatusView)>> command_status_callbacks;
  std::queue<std::unique_ptr<hci::CommandBuilder>> command_queue_;
  mutable std::mutex command_queue_mutex_;
  std::unique_ptr<std::promise<void>> command_promise_;
  std::unique_ptr<std::future<void>> command_future_;
};

class LeAclConnectionTest : public ::testing::Test {
 protected:
  void SetUp() override {
    thread_ = new os::Thread("thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);
    queue_ = std::make_shared<LeAclConnection::Queue>(kQueueSize);
    sync_handler();
    connection_ = new LeAclConnection(
        queue_,
        &le_acl_connection_interface_,
        kConnectionHandle,
        DataAsCentral{address_1},
        address_2);
    connection_->RegisterCallbacks(&callbacks_, handler_);
  }

  void TearDown() override {
    handler_->Clear();
    delete connection_;
    delete handler_;
    delete thread_;
  }

  void sync_handler() {
    ASSERT(thread_ != nullptr);
    ASSERT(thread_->GetReactor()->WaitForIdle(2s));
  }

  AddressWithType address_1 =
      AddressWithType(Address{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}}, AddressType::RANDOM_DEVICE_ADDRESS);
  AddressWithType address_2 =
      AddressWithType(Address{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}}, AddressType::PUBLIC_DEVICE_ADDRESS);
  os::Handler* handler_{nullptr};
  os::Thread* thread_{nullptr};
  std::shared_ptr<LeAclConnection::Queue> queue_;

  TestLeAclConnectionInterface le_acl_connection_interface_;
  TestLeConnectionManagementCallbacks callbacks_;
  LeAclConnection* connection_;
};

TEST_F(LeAclConnectionTest, simple) {
  // empty
}

TEST_F(LeAclConnectionTest, LeSubrateRequest_success) {
  connection_->LeSubrateRequest(kIntervalMin, kIntervalMax, kLatency, kContinuationNumber, kTimeout);

  auto command = CreateAclCommandView<LeSubrateRequestView>(le_acl_connection_interface_.DequeueCommandBytes());
  ASSERT_TRUE(command.IsValid());
  ASSERT_EQ(kIntervalMin, command.GetSubrateMin());
  ASSERT_EQ(kIntervalMax, command.GetSubrateMax());
  ASSERT_EQ(kLatency, command.GetMaxLatency());
  ASSERT_EQ(kContinuationNumber, command.GetContinuationNumber());
  ASSERT_EQ(kTimeout, command.GetSupervisionTimeout());

  EXPECT_CALL(callbacks_, OnLeSubrateChange).Times(0);

  auto status_builder = LeSubrateRequestStatusBuilder::Create(ErrorCode::SUCCESS, 0x01);
  hci::EventView event = hci::EventView::Create(GetPacketView(std::move(status_builder)));
  hci::CommandStatusView command_status = hci::CommandStatusView::Create(event);
  auto on_status = le_acl_connection_interface_.DequeueStatusCallback();
  on_status.Invoke(command_status);
  sync_handler();
}

TEST_F(LeAclConnectionTest, LeSubrateRequest_error) {
  EXPECT_CALL(callbacks_, OnLeSubrateChange(ErrorCode::UNKNOWN_HCI_COMMAND, 0, 0, 0, 0));

  connection_->LeSubrateRequest(kIntervalMin, kIntervalMax, kLatency, kContinuationNumber, kTimeout);

  auto command = CreateAclCommandView<LeSubrateRequestView>(le_acl_connection_interface_.DequeueCommandBytes());
  ASSERT_TRUE(command.IsValid());
  ASSERT_EQ(kIntervalMin, command.GetSubrateMin());
  ASSERT_EQ(kIntervalMax, command.GetSubrateMax());
  ASSERT_EQ(kLatency, command.GetMaxLatency());
  ASSERT_EQ(kContinuationNumber, command.GetContinuationNumber());
  ASSERT_EQ(kTimeout, command.GetSupervisionTimeout());

  auto status_builder = LeSubrateRequestStatusBuilder::Create(ErrorCode::UNKNOWN_HCI_COMMAND, 0x01);
  hci::EventView event = hci::EventView::Create(GetPacketView(std::move(status_builder)));
  hci::CommandStatusView command_status = hci::CommandStatusView::Create(event);
  auto on_status = le_acl_connection_interface_.DequeueStatusCallback();
  on_status.Invoke(std::move(command_status));
  sync_handler();
}

}  // namespace
}  // namespace bluetooth::hci::acl_manager
