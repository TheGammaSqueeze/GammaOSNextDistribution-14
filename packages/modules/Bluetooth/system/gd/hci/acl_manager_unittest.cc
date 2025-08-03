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

#include "hci/acl_manager.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>
#include <deque>
#include <future>
#include <list>
#include <map>

#include "common/bind.h"
#include "common/init_flags.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_layer_fake.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

using namespace std::chrono_literals;

namespace bluetooth {
namespace hci {
namespace acl_manager {
namespace {

using common::BidiQueue;
using common::BidiQueueEnd;
using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

namespace {
constexpr char kLocalRandomAddressString[] = "D0:05:04:03:02:01";
constexpr char kRemotePublicDeviceStringA[] = "11:A2:A3:A4:A5:A6";
constexpr char kRemotePublicDeviceStringB[] = "11:B2:B3:B4:B5:B6";
constexpr uint16_t kHciHandleA = 123;
constexpr uint16_t kHciHandleB = 456;

constexpr auto kMinimumRotationTime = std::chrono::milliseconds(7 * 60 * 1000);
constexpr auto kMaximumRotationTime = std::chrono::milliseconds(15 * 60 * 1000);

const AddressWithType empty_address_with_type = hci::AddressWithType();

struct {
  Address address;
  ClassOfDevice class_of_device;
  const uint16_t handle;
} remote_device[2] = {
    {.address = {}, .class_of_device = {}, .handle = kHciHandleA},
    {.address = {}, .class_of_device = {}, .handle = kHciHandleB},
};
}  // namespace

std::unique_ptr<BasePacketBuilder> NextPayload(uint16_t handle) {
  static uint32_t packet_number = 1;
  auto payload = std::make_unique<RawBuilder>();
  payload->AddOctets2(6);  // L2CAP PDU size
  payload->AddOctets2(2);  // L2CAP CID
  payload->AddOctets2(handle);
  payload->AddOctets4(packet_number++);
  return std::move(payload);
}

class TestController : public Controller {
 public:
  uint16_t GetAclPacketLength() const override {
    return acl_buffer_length_;
  }

  uint16_t GetNumAclPacketBuffers() const override {
    return total_acl_buffers_;
  }

  bool IsSupported(bluetooth::hci::OpCode /* op_code */) const override {
    return false;
  }

  LeBufferSize GetLeBufferSize() const override {
    LeBufferSize le_buffer_size;
    le_buffer_size.total_num_le_packets_ = 2;
    le_buffer_size.le_data_packet_length_ = 32;
    return le_buffer_size;
  }

 protected:
  void Start() override {}
  void Stop() override {}
  void ListDependencies(ModuleList* /* list */) const {}

 private:
  uint16_t acl_buffer_length_ = 1024;
  uint16_t total_acl_buffers_ = 2;
  common::ContextualCallback<void(uint16_t /* handle */, uint16_t /* packets */)> acl_cb_;
};

class MockConnectionCallback : public ConnectionCallbacks {
 public:
  void OnConnectSuccess(std::unique_ptr<ClassicAclConnection> connection) override {
    // Convert to std::shared_ptr during push_back()
    connections_.push_back(std::move(connection));
    if (is_promise_set_) {
      is_promise_set_ = false;
      connection_promise_.set_value(connections_.back());
    }
  }
  MOCK_METHOD(void, OnConnectRequest, (Address, ClassOfDevice), (override));
  MOCK_METHOD(void, OnConnectFail, (Address, ErrorCode reason, bool locally_initiated), (override));

  size_t NumberOfConnections() const {
    return connections_.size();
  }

 private:
  friend class AclManagerWithCallbacksTest;
  friend class AclManagerNoCallbacksTest;

  std::deque<std::shared_ptr<ClassicAclConnection>> connections_;
  std::promise<std::shared_ptr<ClassicAclConnection>> connection_promise_;
  bool is_promise_set_{false};
};

class MockLeConnectionCallbacks : public LeConnectionCallbacks {
 public:
  void OnLeConnectSuccess(
      AddressWithType /* address_with_type */,
      std::unique_ptr<LeAclConnection> connection) override {
    le_connections_.push_back(std::move(connection));
    if (le_connection_promise_ != nullptr) {
      std::promise<void>* prom = le_connection_promise_.release();
      prom->set_value();
      delete prom;
    }
  }
  MOCK_METHOD(void, OnLeConnectFail, (AddressWithType, ErrorCode reason), (override));

  std::deque<std::shared_ptr<LeAclConnection>> le_connections_;
  std::unique_ptr<std::promise<void>> le_connection_promise_;
};

class AclManagerBaseTest : public ::testing::Test {
 protected:
  void SetUp() override {
    common::InitFlags::SetAllForTesting();
    test_hci_layer_ = new HciLayerFake;  // Ownership is transferred to registry
    test_controller_ = new TestController;
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);
    fake_registry_.InjectTestModule(&Controller::Factory, test_controller_);
    client_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_NE(client_handler_, nullptr);
    fake_registry_.Start<AclManager>(&thread_);
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&AclManager::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  void sync_client_handler() {
    std::promise<void> promise;
    auto future = promise.get_future();
    client_handler_->Post(common::BindOnce(&std::promise<void>::set_value, common::Unretained(&promise)));
    auto future_status = future.wait_for(std::chrono::seconds(1));
    ASSERT_EQ(future_status, std::future_status::ready);
  }

  HciLayerFake* test_hci_layer_ = nullptr;
  TestController* test_controller_ = nullptr;

  TestModuleRegistry fake_registry_;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  AclManager* acl_manager_ = nullptr;
  os::Handler* client_handler_ = nullptr;
};

class AclManagerNoCallbacksTest : public AclManagerBaseTest {
 protected:
  void SetUp() override {
    AclManagerBaseTest::SetUp();

    acl_manager_ = static_cast<AclManager*>(fake_registry_.GetModuleUnderTest(&AclManager::Factory));

    local_address_with_type_ = AddressWithType(
        Address::FromString(kLocalRandomAddressString).value(), hci::AddressType::RANDOM_DEVICE_ADDRESS);

    acl_manager_->SetPrivacyPolicyForInitiatorAddress(
        LeAddressManager::AddressPolicy::USE_STATIC_ADDRESS,
        local_address_with_type_,
        kMinimumRotationTime,
        kMaximumRotationTime);

    auto command = test_hci_layer_->GetCommand();
    ASSERT_TRUE(command.IsValid());
    ASSERT_EQ(OpCode::LE_SET_RANDOM_ADDRESS, command.GetOpCode());
  }

  void TearDown() override {
    AclManagerBaseTest::TearDown();
  }

  AddressWithType local_address_with_type_;
  const bool use_accept_list_ = true;  // gd currently only supports connect list

  void SendAclData(uint16_t handle, AclConnection::QueueUpEnd* queue_end) {
    std::promise<void> promise;
    auto future = promise.get_future();
    queue_end->RegisterEnqueue(
        client_handler_,
        common::Bind(
            [](decltype(queue_end) queue_end, uint16_t handle, std::promise<void> promise) {
              queue_end->UnregisterEnqueue();
              promise.set_value();
              return NextPayload(handle);
            },
            queue_end,
            handle,
            common::Passed(std::move(promise))));
    auto status = future.wait_for(2s);
    ASSERT_EQ(status, std::future_status::ready);
  }
};

class AclManagerWithCallbacksTest : public AclManagerNoCallbacksTest {
 protected:
  void SetUp() override {
    AclManagerNoCallbacksTest::SetUp();
    acl_manager_->RegisterCallbacks(&mock_connection_callbacks_, client_handler_);
    acl_manager_->RegisterLeCallbacks(&mock_le_connection_callbacks_, client_handler_);
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    fake_registry_.SynchronizeModuleHandler(&AclManager::Factory, std::chrono::milliseconds(20));
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    {
      std::promise<void> promise;
      auto future = promise.get_future();
      acl_manager_->UnregisterLeCallbacks(&mock_le_connection_callbacks_, std::move(promise));
      future.wait_for(2s);
    }
    {
      std::promise<void> promise;
      auto future = promise.get_future();
      acl_manager_->UnregisterCallbacks(&mock_connection_callbacks_, std::move(promise));
      future.wait_for(2s);
    }

    mock_connection_callbacks_.connections_.clear();
    mock_le_connection_callbacks_.le_connections_.clear();

    AclManagerNoCallbacksTest::TearDown();
  }

  std::future<std::shared_ptr<ClassicAclConnection>> GetConnectionFuture() {
    // Run on main thread
    mock_connection_callbacks_.connection_promise_ = std::promise<std::shared_ptr<ClassicAclConnection>>();
    mock_connection_callbacks_.is_promise_set_ = true;
    return mock_connection_callbacks_.connection_promise_.get_future();
  }

  std::future<void> GetLeConnectionFuture() {
    mock_le_connection_callbacks_.le_connection_promise_ = std::make_unique<std::promise<void>>();
    return mock_le_connection_callbacks_.le_connection_promise_->get_future();
  }

  std::shared_ptr<ClassicAclConnection> GetLastConnection() {
    return mock_connection_callbacks_.connections_.back();
  }

  size_t NumberOfConnections() {
    return mock_connection_callbacks_.connections_.size();
  }

  std::shared_ptr<LeAclConnection> GetLastLeConnection() {
    return mock_le_connection_callbacks_.le_connections_.back();
  }

  size_t NumberOfLeConnections() {
    return mock_le_connection_callbacks_.le_connections_.size();
  }

  MockConnectionCallback mock_connection_callbacks_;
  MockLeConnectionCallbacks mock_le_connection_callbacks_;
};

class AclManagerWithConnectionTest : public AclManagerWithCallbacksTest {
 protected:
  void SetUp() override {
    AclManagerWithCallbacksTest::SetUp();

    handle_ = 0x123;
    Address::FromString("A1:A2:A3:A4:A5:A6", remote);

    acl_manager_->CreateConnection(remote);

    // Wait for the connection request
    auto last_command = test_hci_layer_->GetCommand(OpCode::CREATE_CONNECTION);

    EXPECT_CALL(mock_connection_management_callbacks_, OnRoleChange(hci::ErrorCode::SUCCESS, Role::CENTRAL));

    auto first_connection = GetConnectionFuture();
    test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS, handle_, remote, LinkType::ACL, Enable::DISABLED));

    auto first_connection_status = first_connection.wait_for(2s);
    ASSERT_EQ(first_connection_status, std::future_status::ready);

    connection_ = GetLastConnection();
    connection_->RegisterCallbacks(&mock_connection_management_callbacks_, client_handler_);
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    fake_registry_.SynchronizeModuleHandler(&AclManager::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  uint16_t handle_;
  Address remote;
  std::shared_ptr<ClassicAclConnection> connection_;

  class MockConnectionManagementCallbacks : public ConnectionManagementCallbacks {
   public:
    MOCK_METHOD1(OnConnectionPacketTypeChanged, void(uint16_t packet_type));
    MOCK_METHOD1(OnAuthenticationComplete, void(hci::ErrorCode hci_status));
    MOCK_METHOD1(OnEncryptionChange, void(EncryptionEnabled enabled));
    MOCK_METHOD0(OnChangeConnectionLinkKeyComplete, void());
    MOCK_METHOD1(OnReadClockOffsetComplete, void(uint16_t clock_offse));
    MOCK_METHOD3(OnModeChange, void(ErrorCode status, Mode current_mode, uint16_t interval));
    MOCK_METHOD5(
        OnSniffSubrating,
        void(
            ErrorCode status,
            uint16_t maximum_transmit_latency,
            uint16_t maximum_receive_latency,
            uint16_t minimum_remote_timeout,
            uint16_t minimum_local_timeout));
    MOCK_METHOD5(
        OnQosSetupComplete,
        void(
            ServiceType service_type,
            uint32_t token_rate,
            uint32_t peak_bandwidth,
            uint32_t latency,
            uint32_t delay_variation));
    MOCK_METHOD6(
        OnFlowSpecificationComplete,
        void(
            FlowDirection flow_direction,
            ServiceType service_type,
            uint32_t token_rate,
            uint32_t token_bucket_size,
            uint32_t peak_bandwidth,
            uint32_t access_latency));
    MOCK_METHOD0(OnFlushOccurred, void());
    MOCK_METHOD1(OnRoleDiscoveryComplete, void(Role current_role));
    MOCK_METHOD1(OnReadLinkPolicySettingsComplete, void(uint16_t link_policy_settings));
    MOCK_METHOD1(OnReadAutomaticFlushTimeoutComplete, void(uint16_t flush_timeout));
    MOCK_METHOD1(OnReadTransmitPowerLevelComplete, void(uint8_t transmit_power_level));
    MOCK_METHOD1(OnReadLinkSupervisionTimeoutComplete, void(uint16_t link_supervision_timeout));
    MOCK_METHOD1(OnReadFailedContactCounterComplete, void(uint16_t failed_contact_counter));
    MOCK_METHOD1(OnReadLinkQualityComplete, void(uint8_t link_quality));
    MOCK_METHOD2(OnReadAfhChannelMapComplete, void(AfhMode afh_mode, std::array<uint8_t, 10> afh_channel_map));
    MOCK_METHOD1(OnReadRssiComplete, void(uint8_t rssi));
    MOCK_METHOD2(OnReadClockComplete, void(uint32_t clock, uint16_t accuracy));
    MOCK_METHOD1(OnCentralLinkKeyComplete, void(KeyFlag flag));
    MOCK_METHOD2(OnRoleChange, void(ErrorCode hci_status, Role new_role));
    MOCK_METHOD1(OnDisconnection, void(ErrorCode reason));
    MOCK_METHOD4(
        OnReadRemoteVersionInformationComplete,
        void(hci::ErrorCode hci_status, uint8_t lmp_version, uint16_t manufacturer_name, uint16_t sub_version));
    MOCK_METHOD1(OnReadRemoteSupportedFeaturesComplete, void(uint64_t features));
    MOCK_METHOD3(
        OnReadRemoteExtendedFeaturesComplete, void(uint8_t page_number, uint8_t max_page_number, uint64_t features));
  } mock_connection_management_callbacks_;
};

TEST_F(AclManagerWithCallbacksTest, startup_teardown) {}

class AclManagerWithLeConnectionTest : public AclManagerWithCallbacksTest {
 protected:
  void SetUp() override {
    AclManagerWithCallbacksTest::SetUp();

    Address remote_public_address = Address::FromString(kRemotePublicDeviceStringA).value();
    remote_with_type_ = AddressWithType(remote_public_address, AddressType::PUBLIC_DEVICE_ADDRESS);
    acl_manager_->CreateLeConnection(remote_with_type_, true);
    test_hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
    test_hci_layer_->IncomingEvent(
        LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
    auto packet = test_hci_layer_->GetCommand(OpCode::LE_CREATE_CONNECTION);
    auto le_connection_management_command_view =
        LeConnectionManagementCommandView::Create(AclCommandView::Create(packet));
    auto command_view = LeCreateConnectionView::Create(le_connection_management_command_view);
    ASSERT_TRUE(command_view.IsValid());
    if (use_accept_list_) {
      ASSERT_EQ(command_view.GetPeerAddress(), empty_address_with_type.GetAddress());
      ASSERT_EQ(command_view.GetPeerAddressType(), empty_address_with_type.GetAddressType());
    } else {
      ASSERT_EQ(command_view.GetPeerAddress(), remote_public_address);
      ASSERT_EQ(command_view.GetPeerAddressType(), AddressType::PUBLIC_DEVICE_ADDRESS);
    }

    test_hci_layer_->IncomingEvent(
        LeCreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 0x01));

    auto first_connection = GetLeConnectionFuture();

    test_hci_layer_->IncomingLeMetaEvent(LeConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        handle_,
        Role::PERIPHERAL,
        AddressType::PUBLIC_DEVICE_ADDRESS,
        remote_public_address,
        0x0100,
        0x0010,
        0x0C80,
        ClockAccuracy::PPM_30));

    test_hci_layer_->GetCommand(OpCode::LE_REMOVE_DEVICE_FROM_FILTER_ACCEPT_LIST);
    test_hci_layer_->IncomingEvent(
        LeRemoveDeviceFromFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

    auto first_connection_status = first_connection.wait_for(2s);
    ASSERT_EQ(first_connection_status, std::future_status::ready);

    connection_ = GetLastLeConnection();
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    fake_registry_.SynchronizeModuleHandler(&AclManager::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  void sync_client_handler() {
    std::promise<void> promise;
    auto future = promise.get_future();
    client_handler_->Post(common::BindOnce(&std::promise<void>::set_value, common::Unretained(&promise)));
    auto future_status = future.wait_for(std::chrono::seconds(1));
    ASSERT_EQ(future_status, std::future_status::ready);
  }

  uint16_t handle_ = 0x123;
  std::shared_ptr<LeAclConnection> connection_;
  AddressWithType remote_with_type_;

  class MockLeConnectionManagementCallbacks : public LeConnectionManagementCallbacks {
   public:
    MOCK_METHOD1(OnDisconnection, void(ErrorCode reason));
    MOCK_METHOD4(
        OnConnectionUpdate,
        void(
            hci::ErrorCode hci_status,
            uint16_t connection_interval,
            uint16_t connection_latency,
            uint16_t supervision_timeout));
    MOCK_METHOD4(OnDataLengthChange, void(uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time));
    MOCK_METHOD4(
        OnReadRemoteVersionInformationComplete,
        void(hci::ErrorCode hci_status, uint8_t version, uint16_t manufacturer_name, uint16_t sub_version));
    MOCK_METHOD2(OnLeReadRemoteFeaturesComplete, void(hci::ErrorCode hci_status, uint64_t features));
    MOCK_METHOD3(OnPhyUpdate, void(hci::ErrorCode hci_status, uint8_t tx_phy, uint8_t rx_phy));
    MOCK_METHOD5(
        OnLeSubrateChange,
        void(
            hci::ErrorCode hci_status,
            uint16_t subrate_factor,
            uint16_t peripheral_latency,
            uint16_t continuation_number,
            uint16_t supervision_timeout));
  } mock_le_connection_management_callbacks_;
};

class AclManagerWithResolvableAddressTest : public AclManagerWithCallbacksTest {
 protected:
  void SetUp() override {
    test_hci_layer_ = new HciLayerFake;  // Ownership is transferred to registry
    test_controller_ = new TestController;
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);
    fake_registry_.InjectTestModule(&Controller::Factory, test_controller_);
    client_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_NE(client_handler_, nullptr);
    fake_registry_.Start<AclManager>(&thread_);
    acl_manager_ = static_cast<AclManager*>(fake_registry_.GetModuleUnderTest(&AclManager::Factory));
    hci::Address address;
    Address::FromString("D0:05:04:03:02:01", address);
    hci::AddressWithType address_with_type(address, hci::AddressType::RANDOM_DEVICE_ADDRESS);
    acl_manager_->RegisterCallbacks(&mock_connection_callbacks_, client_handler_);
    acl_manager_->RegisterLeCallbacks(&mock_le_connection_callbacks_, client_handler_);
    auto minimum_rotation_time = std::chrono::milliseconds(7 * 60 * 1000);
    auto maximum_rotation_time = std::chrono::milliseconds(15 * 60 * 1000);
    acl_manager_->SetPrivacyPolicyForInitiatorAddress(
        LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS,
        address_with_type,
        minimum_rotation_time,
        maximum_rotation_time);

    test_hci_layer_->GetCommand(OpCode::LE_SET_RANDOM_ADDRESS);
    test_hci_layer_->IncomingEvent(
        LeSetRandomAddressCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
};

TEST_F(AclManagerNoCallbacksTest, unregister_classic_before_connection_request) {
  ClassOfDevice class_of_device;

  MockConnectionCallback mock_connection_callbacks_;

  acl_manager_->RegisterCallbacks(&mock_connection_callbacks_, client_handler_);

  // Unregister callbacks before receiving connection request
  auto promise = std::promise<void>();
  auto future = promise.get_future();
  acl_manager_->UnregisterCallbacks(&mock_connection_callbacks_, std::move(promise));
  future.get();

  // Inject peer sending connection request
  test_hci_layer_->IncomingEvent(ConnectionRequestBuilder::Create(
      local_address_with_type_.GetAddress(), class_of_device, ConnectionRequestLinkType::ACL));
  sync_client_handler();

  // There should be no connections
  ASSERT_EQ(0UL, mock_connection_callbacks_.NumberOfConnections());

  auto command = test_hci_layer_->GetCommand(OpCode::REJECT_CONNECTION_REQUEST);
}

TEST_F(AclManagerWithCallbacksTest, two_remote_connection_requests_ABAB) {
  Address::FromString(kRemotePublicDeviceStringA, remote_device[0].address);
  Address::FromString(kRemotePublicDeviceStringB, remote_device[1].address);

  {
    // Device A sends connection request
    test_hci_layer_->IncomingEvent(ConnectionRequestBuilder::Create(
        remote_device[0].address,
        remote_device[0].class_of_device,
        ConnectionRequestLinkType::ACL));
    sync_client_handler();
    // Verify we accept this connection
    auto command = test_hci_layer_->GetCommand(OpCode::ACCEPT_CONNECTION_REQUEST);
  }

  {
    // Device B sends connection request
    test_hci_layer_->IncomingEvent(ConnectionRequestBuilder::Create(
        remote_device[1].address,
        remote_device[1].class_of_device,
        ConnectionRequestLinkType::ACL));
    sync_client_handler();
    // Verify we accept this connection
    auto command = test_hci_layer_->GetCommand(OpCode::ACCEPT_CONNECTION_REQUEST);
  }

  ASSERT_EQ(0UL, NumberOfConnections());

  {
    // Device A completes first connection
    auto future = GetConnectionFuture();
    test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        remote_device[0].handle,
        remote_device[0].address,
        LinkType::ACL,
        Enable::DISABLED));
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s)) << "Timeout waiting for first connection complete";
    ASSERT_EQ(1UL, NumberOfConnections());
    auto connection = future.get();
    ASSERT_EQ(connection->GetAddress(), remote_device[0].address) << "First connection remote address mismatch";
  }

  {
    // Device B completes second connection
    auto future = GetConnectionFuture();
    test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        remote_device[1].handle,
        remote_device[1].address,
        LinkType::ACL,
        Enable::DISABLED));
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s)) << "Timeout waiting for second connection complete";
    ASSERT_EQ(2UL, NumberOfConnections());
    auto connection = future.get();
    ASSERT_EQ(connection->GetAddress(), remote_device[1].address) << "Second connection remote address mismatch";
  }
}

TEST_F(AclManagerWithCallbacksTest, two_remote_connection_requests_ABBA) {
  Address::FromString(kRemotePublicDeviceStringA, remote_device[0].address);
  Address::FromString(kRemotePublicDeviceStringB, remote_device[1].address);

  {
    // Device A sends connection request
    test_hci_layer_->IncomingEvent(ConnectionRequestBuilder::Create(
        remote_device[0].address,
        remote_device[0].class_of_device,
        ConnectionRequestLinkType::ACL));
    sync_client_handler();
    // Verify we accept this connection
    auto command = test_hci_layer_->GetCommand(OpCode::ACCEPT_CONNECTION_REQUEST);
  }

  {
    // Device B sends connection request
    test_hci_layer_->IncomingEvent(ConnectionRequestBuilder::Create(
        remote_device[1].address,
        remote_device[1].class_of_device,
        ConnectionRequestLinkType::ACL));
    sync_client_handler();
    // Verify we accept this connection
    auto command = test_hci_layer_->GetCommand(OpCode::ACCEPT_CONNECTION_REQUEST);
  }

  ASSERT_EQ(0UL, NumberOfConnections());

  {
    // Device B completes first connection
    auto future = GetConnectionFuture();
    test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        remote_device[1].handle,
        remote_device[1].address,
        LinkType::ACL,
        Enable::DISABLED));
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s)) << "Timeout waiting for first connection complete";
    ASSERT_EQ(1UL, NumberOfConnections());
    auto connection = future.get();
    ASSERT_EQ(connection->GetAddress(), remote_device[1].address) << "First connection remote address mismatch";
  }

  {
    // Device A completes second connection
    auto future = GetConnectionFuture();
    test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
        ErrorCode::SUCCESS,
        remote_device[0].handle,
        remote_device[0].address,
        LinkType::ACL,
        Enable::DISABLED));
    ASSERT_EQ(std::future_status::ready, future.wait_for(2s)) << "Timeout waiting for second connection complete";
    ASSERT_EQ(2UL, NumberOfConnections());
    auto connection = future.get();
    ASSERT_EQ(connection->GetAddress(), remote_device[0].address) << "Second connection remote address mismatch";
  }
}

}  // namespace
}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
