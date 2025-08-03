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

#include "hci/le_address_manager.h"

#include <gtest/gtest.h>

#include "common/init_flags.h"
#include "hci/hci_layer.h"
#include "hci/hci_layer_fake.h"
#include "hci/octets.h"
#include "os/log.h"
#include "packet/raw_builder.h"

using ::bluetooth::hci::Octet16;
using ::bluetooth::os::Handler;
using ::bluetooth::os::Thread;

namespace bluetooth {
namespace hci {
namespace {

using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

class RotatorClient : public LeAddressManagerCallback {
 public:
  RotatorClient(LeAddressManager* le_address_manager, size_t id) : le_address_manager_(le_address_manager), id_(id){};

  void OnPause() {
    paused = true;
    le_address_manager_->AckPause(this);
  }

  void OnResume() {
    paused = false;
    le_address_manager_->AckResume(this);
    if (resume_promise_ != nullptr) {
      std::promise<void>* prom = resume_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

  void WaitForResume() {
    if (paused) {
      resume_promise_ = std::make_unique<std::promise<void>>();
      auto resume_future = resume_promise_->get_future();
      auto result = resume_future.wait_for(std::chrono::milliseconds(1000));
      EXPECT_NE(std::future_status::timeout, result);
    }
  }

  bool paused{false};
  LeAddressManager* le_address_manager_;
  size_t id_;
  std::unique_ptr<std::promise<void>> resume_promise_;
};

class LeAddressManagerTest : public ::testing::Test {
 public:
  void SetUp() override {
    thread_ = new Thread("thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    hci_layer_ = new HciLayerFake();
    Address address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
    le_address_manager_ = new LeAddressManager(
        common::Bind(&LeAddressManagerTest::enqueue_command, common::Unretained(this)), handler_, address, 0x3F, 0x3F);
    AllocateClients(1);
  }

  void sync_handler(os::Handler* /* handler */) {
    std::promise<void> promise;
    auto future = promise.get_future();
    handler_->Post(common::BindOnce(&std::promise<void>::set_value, common::Unretained(&promise)));
    auto future_status = future.wait_for(std::chrono::seconds(1));
    EXPECT_EQ(future_status, std::future_status::ready);
  }

  void TearDown() override {
    sync_handler(handler_);
    delete le_address_manager_;
    delete hci_layer_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void AllocateClients(size_t num_clients) {
    size_t first_id = clients.size();
    for (size_t i = 0; i < num_clients; i++) {
      clients.emplace_back(std::make_unique<RotatorClient>(le_address_manager_, first_id + i));
    }
  }

  void enqueue_command(std::unique_ptr<CommandBuilder> command_packet) {
    hci_layer_->EnqueueCommand(
        std::move(command_packet),
        handler_->BindOnce(
            &LeAddressManager::OnCommandComplete, common::Unretained(le_address_manager_)));
  }

  Thread* thread_;
  Handler* handler_;
  HciLayerFake* hci_layer_ = nullptr;
  LeAddressManager* le_address_manager_;
  std::vector<std::unique_ptr<RotatorClient>> clients;
};

TEST_F(LeAddressManagerTest, startup_teardown) {}

TEST_F(LeAddressManagerTest, register_unregister_callback) {
  le_address_manager_->Register(clients[0].get());
  sync_handler(handler_);
  le_address_manager_->Unregister(clients[0].get());
  sync_handler(handler_);
}

TEST_F(LeAddressManagerTest, rotator_address_for_single_client) {
  Octet16 irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
  auto minimum_rotation_time = std::chrono::milliseconds(1000);
  auto maximum_rotation_time = std::chrono::milliseconds(3000);
  AddressWithType remote_address(Address::kEmpty, AddressType::RANDOM_DEVICE_ADDRESS);
  le_address_manager_->SetPrivacyPolicyForInitiatorAddress(
      LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS,
      remote_address,
      irk,
      false,
      minimum_rotation_time,
      maximum_rotation_time);

  le_address_manager_->Register(clients[0].get());
  sync_handler(handler_);
  hci_layer_->GetCommand(OpCode::LE_SET_RANDOM_ADDRESS);
  hci_layer_->IncomingEvent(LeSetRandomAddressCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  clients[0].get()->WaitForResume();
  le_address_manager_->Unregister(clients[0].get());
  sync_handler(handler_);
}

TEST_F(LeAddressManagerTest, rotator_non_resolvable_address_for_single_client) {
  Octet16 irk = {};
  auto minimum_rotation_time = std::chrono::milliseconds(1000);
  auto maximum_rotation_time = std::chrono::milliseconds(3000);
  AddressWithType remote_address(Address::kEmpty, AddressType::RANDOM_DEVICE_ADDRESS);
  le_address_manager_->SetPrivacyPolicyForInitiatorAddress(
      LeAddressManager::AddressPolicy::USE_NON_RESOLVABLE_ADDRESS,
      remote_address,
      irk,
      false,
      minimum_rotation_time,
      maximum_rotation_time);

  le_address_manager_->Register(clients[0].get());
  sync_handler(handler_);
  hci_layer_->GetCommand(OpCode::LE_SET_RANDOM_ADDRESS);
  hci_layer_->IncomingEvent(LeSetRandomAddressCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  clients[0].get()->WaitForResume();
  le_address_manager_->Unregister(clients[0].get());
  sync_handler(handler_);
}

// TODO handle the case "register during rotate_random_address" and enable this
TEST_F(LeAddressManagerTest, DISABLED_rotator_address_for_multiple_clients) {
  AllocateClients(2);
  Octet16 irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
  auto minimum_rotation_time = std::chrono::milliseconds(1000);
  auto maximum_rotation_time = std::chrono::milliseconds(3000);
  AddressWithType remote_address(Address::kEmpty, AddressType::RANDOM_DEVICE_ADDRESS);
  le_address_manager_->SetPrivacyPolicyForInitiatorAddress(
      LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS,
      remote_address,
      irk,
      false,
      minimum_rotation_time,
      maximum_rotation_time);
  le_address_manager_->Register(clients[0].get());
  le_address_manager_->Register(clients[1].get());
  le_address_manager_->Register(clients[2].get());
  sync_handler(handler_);

  le_address_manager_->Unregister(clients[0].get());
  le_address_manager_->Unregister(clients[1].get());
  le_address_manager_->Unregister(clients[2].get());
  sync_handler(handler_);
}

class LeAddressManagerWithSingleClientTest : public LeAddressManagerTest {
 public:
  void SetUp() override {
    bluetooth::common::InitFlags::SetAllForTesting();
    thread_ = new Thread("thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    hci_layer_ = new HciLayerFake();
    Address address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
    le_address_manager_ = new LeAddressManager(
        common::Bind(&LeAddressManagerWithSingleClientTest::enqueue_command, common::Unretained(this)),
        handler_,
        address,
        0x3F,
        0x3F);
    AllocateClients(1);

    Octet16 irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
    auto minimum_rotation_time = std::chrono::milliseconds(1000);
    auto maximum_rotation_time = std::chrono::milliseconds(3000);
    AddressWithType remote_address(Address::kEmpty, AddressType::RANDOM_DEVICE_ADDRESS);
    le_address_manager_->SetPrivacyPolicyForInitiatorAddress(
        LeAddressManager::AddressPolicy::USE_RESOLVABLE_ADDRESS,
        remote_address,
        irk,
        false,
        minimum_rotation_time,
        maximum_rotation_time);

    le_address_manager_->Register(clients[0].get());
    sync_handler(handler_);
    hci_layer_->GetCommand(OpCode::LE_SET_RANDOM_ADDRESS);
    hci_layer_->IncomingEvent(LeSetRandomAddressCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }

  void enqueue_command(std::unique_ptr<CommandBuilder> command_packet) {
    hci_layer_->EnqueueCommand(
        std::move(command_packet),
        handler_->BindOnce(
            &LeAddressManager::OnCommandComplete, common::Unretained(le_address_manager_)));
  }

  void TearDown() override {
    le_address_manager_->Unregister(clients[0].get());
    sync_handler(handler_);
    delete le_address_manager_;
    delete hci_layer_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }
};

TEST_F(LeAddressManagerWithSingleClientTest, add_device_to_accept_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  le_address_manager_->AddDeviceToFilterAcceptList(FilterAcceptListAddressType::RANDOM, address);
  auto packet = hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  auto packet_view = LeAddDeviceToFilterAcceptListView::Create(
      LeConnectionManagementCommandView::Create(AclCommandView::Create(packet)));
  ASSERT_TRUE(packet_view.IsValid());
  ASSERT_EQ(FilterAcceptListAddressType::RANDOM, packet_view.GetAddressType());
  ASSERT_EQ(address, packet_view.GetAddress());

  hci_layer_->IncomingEvent(
      LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  clients[0].get()->WaitForResume();
}

TEST_F(LeAddressManagerWithSingleClientTest, remove_device_from_accept_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  le_address_manager_->AddDeviceToFilterAcceptList(FilterAcceptListAddressType::RANDOM, address);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  hci_layer_->IncomingEvent(
      LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

  le_address_manager_->RemoveDeviceFromFilterAcceptList(FilterAcceptListAddressType::RANDOM, address);
  auto packet = hci_layer_->GetCommand(OpCode::LE_REMOVE_DEVICE_FROM_FILTER_ACCEPT_LIST);
  auto packet_view = LeRemoveDeviceFromFilterAcceptListView::Create(
      LeConnectionManagementCommandView::Create(AclCommandView::Create(packet)));
  ASSERT_TRUE(packet_view.IsValid());
  ASSERT_EQ(FilterAcceptListAddressType::RANDOM, packet_view.GetAddressType());
  ASSERT_EQ(address, packet_view.GetAddress());
  hci_layer_->IncomingEvent(
      LeRemoveDeviceFromFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  clients[0].get()->WaitForResume();
}

TEST_F(LeAddressManagerWithSingleClientTest, clear_filter_accept_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  le_address_manager_->AddDeviceToFilterAcceptList(FilterAcceptListAddressType::RANDOM, address);
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  hci_layer_->IncomingEvent(
      LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

  le_address_manager_->ClearFilterAcceptList();
  hci_layer_->GetCommand(OpCode::LE_CLEAR_FILTER_ACCEPT_LIST);
  hci_layer_->IncomingEvent(
      LeClearFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  clients[0].get()->WaitForResume();
}

// b/260916288
TEST_F(LeAddressManagerWithSingleClientTest, DISABLED_add_device_to_resolving_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  Octet16 peer_irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
  Octet16 local_irk = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10};

  le_address_manager_->AddDeviceToResolvingList(
      PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, address, peer_irk, local_irk);
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::DISABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_RESOLVING_LIST);
    auto packet_view = LeAddDeviceToResolvingListView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, packet_view.GetPeerIdentityAddressType());
    ASSERT_EQ(address, packet_view.GetPeerIdentityAddress());
    ASSERT_EQ(peer_irk, packet_view.GetPeerIrk());
    ASSERT_EQ(local_irk, packet_view.GetLocalIrk());
    hci_layer_->IncomingEvent(
        LeAddDeviceToResolvingListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::ENABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  clients[0].get()->WaitForResume();
}

// b/260916288
TEST_F(LeAddressManagerWithSingleClientTest, DISABLED_remove_device_from_resolving_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  Octet16 peer_irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
  Octet16 local_irk = {
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
      0x06,
      0x07,
      0x08,
      0x09,
      0x0a,
      0x0b,
      0x0c,
      0x0d,
      0x0e,
      0x0f,
      0x10};
  le_address_manager_->AddDeviceToResolvingList(
      PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, address, peer_irk, local_irk);
  hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
  hci_layer_->IncomingEvent(
      LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_RESOLVING_LIST);
  hci_layer_->IncomingEvent(
      LeAddDeviceToResolvingListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
  hci_layer_->IncomingEvent(
      LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

  le_address_manager_->RemoveDeviceFromResolvingList(PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, address);
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::DISABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_REMOVE_DEVICE_FROM_RESOLVING_LIST);
    auto packet_view = LeRemoveDeviceFromResolvingListView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, packet_view.GetPeerIdentityAddressType());
    ASSERT_EQ(address, packet_view.GetPeerIdentityAddress());
    hci_layer_->IncomingEvent(
        LeRemoveDeviceFromResolvingListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::ENABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  clients[0].get()->WaitForResume();
}

// b/260916288
TEST_F(LeAddressManagerWithSingleClientTest, DISABLED_clear_resolving_list) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  Octet16 peer_irk = {0xec, 0x02, 0x34, 0xa3, 0x57, 0xc8, 0xad, 0x05, 0x34, 0x10, 0x10, 0xa6, 0x0a, 0x39, 0x7d, 0x9b};
  Octet16 local_irk = {
      0x01,
      0x02,
      0x03,
      0x04,
      0x05,
      0x06,
      0x07,
      0x08,
      0x09,
      0x0a,
      0x0b,
      0x0c,
      0x0d,
      0x0e,
      0x0f,
      0x10};
  le_address_manager_->AddDeviceToResolvingList(
      PeerAddressType::RANDOM_DEVICE_OR_IDENTITY_ADDRESS, address, peer_irk, local_irk);
  hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
  hci_layer_->IncomingEvent(
      LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_RESOLVING_LIST);
  hci_layer_->IncomingEvent(
      LeAddDeviceToResolvingListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
  hci_layer_->IncomingEvent(
      LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

  le_address_manager_->ClearResolvingList();
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::DISABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_CLEAR_RESOLVING_LIST);
    auto packet_view = LeClearResolvingListView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    hci_layer_->IncomingEvent(
        LeClearResolvingListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }
  {
    auto packet = hci_layer_->GetCommand(OpCode::LE_SET_ADDRESS_RESOLUTION_ENABLE);
    auto packet_view = LeSetAddressResolutionEnableView::Create(LeSecurityCommandView::Create(packet));
    ASSERT_TRUE(packet_view.IsValid());
    ASSERT_EQ(Enable::ENABLED, packet_view.GetAddressResolutionEnable());
    hci_layer_->IncomingEvent(
        LeSetAddressResolutionEnableCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));
  }

  clients[0].get()->WaitForResume();
}

TEST_F(LeAddressManagerWithSingleClientTest, register_during_command_complete) {
  Address address;
  Address::FromString("01:02:03:04:05:06", address);
  le_address_manager_->AddDeviceToFilterAcceptList(FilterAcceptListAddressType::RANDOM, address);
  auto packet = hci_layer_->GetCommand(OpCode::LE_ADD_DEVICE_TO_FILTER_ACCEPT_LIST);
  auto packet_view = LeAddDeviceToFilterAcceptListView::Create(
      LeConnectionManagementCommandView::Create(AclCommandView::Create(packet)));
  ASSERT_TRUE(packet_view.IsValid());
  ASSERT_EQ(FilterAcceptListAddressType::RANDOM, packet_view.GetAddressType());
  ASSERT_EQ(address, packet_view.GetAddress());
  hci_layer_->IncomingEvent(
      LeAddDeviceToFilterAcceptListCompleteBuilder::Create(0x01, ErrorCode::SUCCESS));

  AllocateClients(1);
  le_address_manager_->Register(clients[1].get());
  clients[0].get()->WaitForResume();
  clients[1].get()->WaitForResume();
}

}  // namespace
}  // namespace hci
}  // namespace bluetooth
