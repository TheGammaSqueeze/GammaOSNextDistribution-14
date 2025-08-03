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

#include "hci/acl_manager/classic_acl_connection.h"

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

#include "hci/acl_connection_interface.h"
#include "hci/acl_manager/connection_management_callbacks.h"
#include "hci/address.h"
#include "hci/hci_packets.h"
#include "os/handler.h"
#include "os/log.h"
#include "os/thread.h"

using namespace bluetooth;
using namespace std::chrono_literals;

namespace {
constexpr char kAddress[] = "00:11:22:33:44:55";
constexpr uint16_t kConnectionHandle = 123;
constexpr size_t kQueueSize = 10;

std::vector<hci::DisconnectReason> disconnect_reason_vector = {
    hci::DisconnectReason::AUTHENTICATION_FAILURE,
    hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION,
    hci::DisconnectReason::REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES,
    hci::DisconnectReason::REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF,
    hci::DisconnectReason::UNSUPPORTED_REMOTE_FEATURE,
    hci::DisconnectReason::PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED,
    hci::DisconnectReason::UNACCEPTABLE_CONNECTION_PARAMETERS,
};

std::vector<hci::ErrorCode> error_code_vector = {
    hci::ErrorCode::SUCCESS,
    hci::ErrorCode::UNKNOWN_HCI_COMMAND,
    hci::ErrorCode::UNKNOWN_CONNECTION,
    hci::ErrorCode::HARDWARE_FAILURE,
    hci::ErrorCode::PAGE_TIMEOUT,
    hci::ErrorCode::AUTHENTICATION_FAILURE,
    hci::ErrorCode::PIN_OR_KEY_MISSING,
    hci::ErrorCode::MEMORY_CAPACITY_EXCEEDED,
    hci::ErrorCode::CONNECTION_TIMEOUT,
    hci::ErrorCode::CONNECTION_LIMIT_EXCEEDED,
    hci::ErrorCode::SYNCHRONOUS_CONNECTION_LIMIT_EXCEEDED,
    hci::ErrorCode::CONNECTION_ALREADY_EXISTS,
    hci::ErrorCode::COMMAND_DISALLOWED,
    hci::ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES,
    hci::ErrorCode::CONNECTION_REJECTED_SECURITY_REASONS,
    hci::ErrorCode::CONNECTION_REJECTED_UNACCEPTABLE_BD_ADDR,
    hci::ErrorCode::CONNECTION_ACCEPT_TIMEOUT,
    hci::ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE,
    hci::ErrorCode::INVALID_HCI_COMMAND_PARAMETERS,
    hci::ErrorCode::REMOTE_USER_TERMINATED_CONNECTION,
    hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES,
    hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF,
    hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST,
    hci::ErrorCode::REPEATED_ATTEMPTS,
    hci::ErrorCode::PAIRING_NOT_ALLOWED,
    hci::ErrorCode::UNKNOWN_LMP_PDU,
    hci::ErrorCode::UNSUPPORTED_REMOTE_OR_LMP_FEATURE,
    hci::ErrorCode::SCO_OFFSET_REJECTED,
    hci::ErrorCode::SCO_INTERVAL_REJECTED,
    hci::ErrorCode::SCO_AIR_MODE_REJECTED,
    hci::ErrorCode::INVALID_LMP_OR_LL_PARAMETERS,
    hci::ErrorCode::UNSPECIFIED_ERROR,
    hci::ErrorCode::UNSUPPORTED_LMP_OR_LL_PARAMETER,
    hci::ErrorCode::ROLE_CHANGE_NOT_ALLOWED,
    hci::ErrorCode::TRANSACTION_RESPONSE_TIMEOUT,
    hci::ErrorCode::LINK_LAYER_COLLISION,
    hci::ErrorCode::ENCRYPTION_MODE_NOT_ACCEPTABLE,
    hci::ErrorCode::ROLE_SWITCH_FAILED,
    hci::ErrorCode::CONTROLLER_BUSY,
    hci::ErrorCode::ADVERTISING_TIMEOUT,
    hci::ErrorCode::CONNECTION_FAILED_ESTABLISHMENT,
    hci::ErrorCode::LIMIT_REACHED,
    hci::ErrorCode::STATUS_UNKNOWN,
};

// Generic template for all commands
template <typename T, typename U>
T CreateCommand(U /* u */) {
  T command;
  return command;
}

template <>
hci::DisconnectView CreateCommand(std::shared_ptr<std::vector<uint8_t>> bytes) {
  return hci::DisconnectView::Create(
      hci::AclCommandView::Create(hci::CommandView::Create(hci::PacketView<hci::kLittleEndian>(bytes))));
}

}  // namespace

class TestAclConnectionInterface : public hci::AclConnectionInterface {
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
  virtual ~TestAclConnectionInterface() = default;

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

class TestConnectionManagementCallbacks : public hci::acl_manager::ConnectionManagementCallbacks {
 public:
  ~TestConnectionManagementCallbacks() = default;
  void OnConnectionPacketTypeChanged(uint16_t /* packet_type */) override {}
  void OnAuthenticationComplete(hci::ErrorCode /* hci_status */) override {}
  void OnEncryptionChange(hci::EncryptionEnabled /* enabled */) override {}
  void OnChangeConnectionLinkKeyComplete() override {}
  void OnReadClockOffsetComplete(uint16_t /* clock_offset */) override {}
  void OnModeChange(
      hci::ErrorCode /* status */, hci::Mode /* current_mode */, uint16_t /* interval */) override {
  }
  void OnSniffSubrating(
      hci::ErrorCode /* hci_status */,
      uint16_t /* maximum_transmit_latency */,
      uint16_t /* maximum_receive_latency */,
      uint16_t /* minimum_remote_timeout */,
      uint16_t /* minimum_local_timeout */) override {}
  void OnQosSetupComplete(
      hci::ServiceType /* service_type */,
      uint32_t /* token_rate */,
      uint32_t /* peak_bandwidth */,
      uint32_t /* latency */,
      uint32_t /* delay_variation */) override {}
  void OnFlowSpecificationComplete(
      hci::FlowDirection /* flow_direction */,
      hci::ServiceType /* service_type */,
      uint32_t /* token_rate */,
      uint32_t /* token_bucket_size */,
      uint32_t /* peak_bandwidth */,
      uint32_t /* access_latency */) override {}
  void OnFlushOccurred() override {}
  void OnRoleDiscoveryComplete(hci::Role /* current_role */) override {}
  void OnReadLinkPolicySettingsComplete(uint16_t /* link_policy_settings */) override {}
  void OnReadAutomaticFlushTimeoutComplete(uint16_t /* flush_timeout */) override {}
  void OnReadTransmitPowerLevelComplete(uint8_t /* transmit_power_level */) override {}
  void OnReadLinkSupervisionTimeoutComplete(uint16_t /* link_supervision_timeout */) override {}
  void OnReadFailedContactCounterComplete(uint16_t /* failed_contact_counter */) override {}
  void OnReadLinkQualityComplete(uint8_t /* link_quality */) override {}
  void OnReadAfhChannelMapComplete(
      hci::AfhMode /* afh_mode */, std::array<uint8_t, 10> /* afh_channel_map */) override {}
  void OnReadRssiComplete(uint8_t /* rssi */) override {}
  void OnReadClockComplete(uint32_t /* clock */, uint16_t /* accuracy */) override {}
  void OnCentralLinkKeyComplete(hci::KeyFlag /* key_flag */) override {}
  void OnRoleChange(hci::ErrorCode /* hci_status */, hci::Role /* new_role */) override {}
  void OnDisconnection(hci::ErrorCode reason) override {
    on_disconnection_error_code_queue_.push(reason);
  }
  void OnReadRemoteVersionInformationComplete(
      hci::ErrorCode /* hci_status */,
      uint8_t /* lmp_version */,
      uint16_t /* manufacturer_name */,
      uint16_t /* sub_version */) override {}
  void OnReadRemoteSupportedFeaturesComplete(uint64_t /* features */) override {}
  void OnReadRemoteExtendedFeaturesComplete(
      uint8_t /* page_number */, uint8_t /* max_page_number */, uint64_t /* features */) override {}

  std::queue<hci::ErrorCode> on_disconnection_error_code_queue_;
};

namespace bluetooth {
namespace hci {
namespace acl_manager {

class ClassicAclConnectionTest : public ::testing::Test {
 protected:
  void SetUp() override {
    ASSERT_TRUE(hci::Address::FromString(kAddress, address_));
    thread_ = new os::Thread("thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);
    queue_ = std::make_shared<hci::acl_manager::AclConnection::Queue>(kQueueSize);
    sync_handler();
  }

  void TearDown() override {
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void sync_handler() {
    ASSERT(thread_ != nullptr);
    ASSERT(thread_->GetReactor()->WaitForIdle(2s));
  }

  Address address_;
  os::Handler* handler_{nullptr};
  os::Thread* thread_{nullptr};
  std::shared_ptr<hci::acl_manager::AclConnection::Queue> queue_;

  TestAclConnectionInterface acl_connection_interface_;
  TestConnectionManagementCallbacks callbacks_;
};

TEST_F(ClassicAclConnectionTest, simple) {
  AclConnectionInterface* acl_connection_interface = nullptr;
  ClassicAclConnection* connection =
      new ClassicAclConnection(queue_, acl_connection_interface, kConnectionHandle, address_);
  connection->RegisterCallbacks(&callbacks_, handler_);

  delete connection;
}

class ClassicAclConnectionWithCallbacksTest : public ClassicAclConnectionTest {
 protected:
  void SetUp() override {
    ClassicAclConnectionTest::SetUp();
    connection_ =
        std::make_unique<ClassicAclConnection>(queue_, &acl_connection_interface_, kConnectionHandle, address_);
    connection_->RegisterCallbacks(&callbacks_, handler_);
    is_callbacks_registered_ = true;
    connection_management_callbacks_ = connection_->GetEventCallbacks(
        [this](uint16_t /* hci_handle */) { is_callbacks_invalidated_ = true; });
    is_callbacks_invalidated_ = false;
  }

  void TearDown() override {
    connection_.reset();
    ASSERT_TRUE(is_callbacks_invalidated_);
    ClassicAclConnectionTest::TearDown();
  }

 protected:
  std::unique_ptr<ClassicAclConnection> connection_;
  ConnectionManagementCallbacks* connection_management_callbacks_;
  bool is_callbacks_registered_{false};
  bool is_callbacks_invalidated_{false};
};

TEST_F(ClassicAclConnectionWithCallbacksTest, Disconnect) {
  for (const auto& reason : disconnect_reason_vector) {
    ASSERT_TRUE(connection_->Disconnect(reason));
  }

  for (const auto& reason : disconnect_reason_vector) {
    ASSERT_FALSE(acl_connection_interface_.IsPacketQueueEmpty());
    auto command = CreateCommand<DisconnectView>(acl_connection_interface_.DequeueCommandBytes());
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(reason, command.GetReason());
    ASSERT_EQ(kConnectionHandle, command.GetConnectionHandle());
  }
  ASSERT_TRUE(acl_connection_interface_.IsPacketQueueEmpty());
}

TEST_F(ClassicAclConnectionWithCallbacksTest, OnDisconnection) {
  for (const auto& error_code : error_code_vector) {
    connection_management_callbacks_->OnDisconnection(error_code);
  }

  sync_handler();
  ASSERT_TRUE(!callbacks_.on_disconnection_error_code_queue_.empty());

  for (const auto& error_code : error_code_vector) {
    ASSERT_EQ(error_code, callbacks_.on_disconnection_error_code_queue_.front());
    callbacks_.on_disconnection_error_code_queue_.pop();
  }
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
