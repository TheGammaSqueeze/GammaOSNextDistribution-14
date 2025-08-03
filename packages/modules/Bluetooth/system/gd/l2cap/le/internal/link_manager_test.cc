/*
 * Copyright 2018 The Android Open Source Project
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

#include "l2cap/le/internal/link_manager.h"

#include <future>
#include <thread>

#include "common/bind.h"
#include "common/testing/bind_test_util.h"
#include "hci/acl_manager_mock.h"
#include "hci/address.h"
#include "l2cap/cid.h"
#include "l2cap/internal/parameter_provider_mock.h"
#include "l2cap/le/fixed_channel_manager.h"
#include "l2cap/le/internal/fixed_channel_service_impl_mock.h"
#include "l2cap/le/internal/fixed_channel_service_manager_impl_mock.h"
#include "os/handler.h"
#include "os/thread.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace bluetooth {
namespace l2cap {
namespace le {
namespace internal {
namespace {

using hci::testing::MockAclManager;
using hci::testing::MockLeAclConnection;
using l2cap::internal::testing::MockParameterProvider;
using ::testing::_;  // Matcher to any value
using ::testing::ByMove;
using ::testing::DoAll;
using testing::MockFixedChannelServiceImpl;
using testing::MockFixedChannelServiceManagerImpl;
using ::testing::Return;
using ::testing::SaveArg;

constexpr static auto kTestIdleDisconnectTimeoutLong = std::chrono::milliseconds(1000);
constexpr static auto kTestIdleDisconnectTimeoutShort = std::chrono::milliseconds(1000);

class L2capLeLinkManagerTest : public ::testing::Test {
 public:
  static void SyncHandler(os::Handler* handler) {
    std::promise<void> promise;
    auto future = promise.get_future();
    handler->Post(common::BindOnce(&std::promise<void>::set_value, common::Unretained(&promise)));
    future.wait_for(std::chrono::milliseconds(3));
  }

 protected:
  void SetUp() override {
    thread_ = new os::Thread("test_thread", os::Thread::Priority::NORMAL);
    l2cap_handler_ = new os::Handler(thread_);
    user_handler_ = new os::Handler(thread_);
    mock_parameter_provider_ = new MockParameterProvider;
    EXPECT_CALL(*mock_parameter_provider_, GetLeLinkIdleDisconnectTimeout)
        .WillRepeatedly(Return(kTestIdleDisconnectTimeoutLong));
  }

  void TearDown() override {
    delete mock_parameter_provider_;
    l2cap_handler_->Clear();
    delete l2cap_handler_;
    user_handler_->Clear();
    delete user_handler_;
    delete thread_;
  }

  os::Thread* thread_ = nullptr;
  os::Handler* l2cap_handler_ = nullptr;
  os::Handler* user_handler_ = nullptr;
  MockParameterProvider* mock_parameter_provider_ = nullptr;
};

TEST_F(L2capLeLinkManagerTest, connect_fixed_channel_service_without_acl) {
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::RANDOM_DEVICE_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Register fake services
  MockFixedChannelServiceImpl mock_service_1, mock_service_2;
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  results.emplace_back(kSmpBrCid, &mock_service_1);
  results.emplace_back(kConnectionlessCid, &mock_service_2);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without ACL connection should trigger ACL connection process
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(1);
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::BindOnce([](FixedChannelManager::ConnectionResult result) { FAIL(); })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));

  // Step 3: ACL connection success event should trigger channel creation for all registered services
  std::unique_ptr<MockLeAclConnection> acl_connection = std::make_unique<MockLeAclConnection>();
  EXPECT_CALL(*acl_connection, GetRemoteAddress()).WillRepeatedly(Return(address_with_type));
  hci::acl_manager::LeConnectionManagementCallbacks* connection_management_callbacks = nullptr;
  os::Handler* connection_management_handler = nullptr;
  EXPECT_CALL(*acl_connection, RegisterCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&connection_management_callbacks), SaveArg<1>(&connection_management_handler)));
  std::unique_ptr<FixedChannel> channel_1, channel_2;
  std::promise<void> promise_1, promise_2;
  auto future_1 = promise_1.get_future();
  auto future_2 = promise_2.get_future();
  EXPECT_CALL(mock_service_1, NotifyChannelCreation(_))
      .WillOnce([&channel_1, &promise_1](std::unique_ptr<FixedChannel> channel) {
        channel_1 = std::move(channel);
        promise_1.set_value();
      });
  EXPECT_CALL(mock_service_2, NotifyChannelCreation(_))
      .WillOnce([&channel_2, &promise_2](std::unique_ptr<FixedChannel> channel) {
        channel_2 = std::move(channel);
        promise_2.set_value();
      });
  hci_callback_handler->Post(common::BindOnce(&hci::acl_manager::LeConnectionCallbacks::OnLeConnectSuccess,
                                              common::Unretained(hci_le_connection_callbacks), address_with_type,
                                              std::move(acl_connection)));
  SyncHandler(hci_callback_handler);
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnReadRemoteVersionInformationComplete,
      common::Unretained(connection_management_callbacks),
      hci::ErrorCode::SUCCESS,
      0,
      0,
      0));
  auto future_1_status = future_1.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_1_status, std::future_status::ready);
  auto future_2_status = future_2.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_2_status, std::future_status::ready);
  ASSERT_NE(channel_1, nullptr);
  ASSERT_NE(channel_2, nullptr);

  // Step 4: Calling ConnectServices() to the same device will not trigger another connection attempt
  FixedChannelManager::ConnectionResult my_result;
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection_2{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::testing::BindLambdaForTesting(
          [&my_result](FixedChannelManager::ConnectionResult result) { my_result = result; })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection_2));
  SyncHandler(user_handler_);
  EXPECT_EQ(my_result.connection_result_code,
            FixedChannelManager::ConnectionResultCode::FAIL_ALL_SERVICES_HAVE_CHANNEL);

  // Step 5: Register new service will cause new channels to be created during ConnectServices()
  MockFixedChannelServiceImpl mock_service_3;
  results.emplace_back(kSmpBrCid + 1, &mock_service_3);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection_3{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::BindOnce([](FixedChannelManager::ConnectionResult result) { FAIL(); })};
  std::unique_ptr<FixedChannel> channel_3;
  std::promise<void> promise_3;
  auto future_3 = promise_3.get_future();
  EXPECT_CALL(mock_service_3, NotifyChannelCreation(_))
      .WillOnce([&channel_3, &promise_3](std::unique_ptr<FixedChannel> channel) {
        channel_3 = std::move(channel);
        promise_3.set_value();
      });
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection_3));
  auto future_3_status = future_3.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_3_status, std::future_status::ready);
  EXPECT_NE(channel_3, nullptr);

  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnDisconnection,
      common::Unretained(connection_management_callbacks), hci::ErrorCode::REMOTE_USER_TERMINATED_CONNECTION));
  SyncHandler(connection_management_handler);
}

TEST_F(L2capLeLinkManagerTest, connect_fixed_channel_service_without_acl_with_no_service) {
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::PUBLIC_DEVICE_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Make sure no service is registered
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without any service registered will result in failure
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(0);
  FixedChannelManager::ConnectionResult my_result;
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::testing::BindLambdaForTesting(
          [&my_result](FixedChannelManager::ConnectionResult result) { my_result = result; })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));
  SyncHandler(user_handler_);
  EXPECT_EQ(my_result.connection_result_code, FixedChannelManager::ConnectionResultCode::FAIL_NO_SERVICE_REGISTERED);

}

TEST_F(L2capLeLinkManagerTest, connect_fixed_channel_service_without_acl_with_hci_failure) {
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::RANDOM_DEVICE_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Register fake services
  MockFixedChannelServiceImpl mock_service_1;
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  results.emplace_back(kSmpBrCid, &mock_service_1);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without ACL connection should trigger ACL connection process
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(1);
  FixedChannelManager::ConnectionResult my_result;
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::testing::BindLambdaForTesting(
          [&my_result](FixedChannelManager::ConnectionResult result) { my_result = result; })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));

  // Step 3: ACL connection failure event should trigger connection failure callback
  EXPECT_CALL(mock_service_1, NotifyChannelCreation(_)).Times(0);
  hci_callback_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionCallbacks::OnLeConnectFail,
      common::Unretained(hci_le_connection_callbacks),
      address_with_type,
      hci::ErrorCode::PAGE_TIMEOUT));
  SyncHandler(hci_callback_handler);
  SyncHandler(user_handler_);
  EXPECT_EQ(my_result.connection_result_code, FixedChannelManager::ConnectionResultCode::FAIL_HCI_ERROR);
  EXPECT_EQ(my_result.hci_error, hci::ErrorCode::PAGE_TIMEOUT);
}

TEST_F(L2capLeLinkManagerTest, not_acquiring_channels_should_disconnect_acl_after_timeout) {
  EXPECT_CALL(*mock_parameter_provider_, GetLeLinkIdleDisconnectTimeout)
      .WillRepeatedly(Return(kTestIdleDisconnectTimeoutShort));
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::RANDOM_DEVICE_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Register fake services
  MockFixedChannelServiceImpl mock_service_1, mock_service_2;
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  results.emplace_back(kSmpBrCid, &mock_service_1);
  results.emplace_back(kConnectionlessCid, &mock_service_2);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without ACL connection should trigger ACL connection process
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(1);
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::BindOnce([](FixedChannelManager::ConnectionResult result) { FAIL(); })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));

  // Step 3: ACL connection success event should trigger channel creation for all registered services
  auto* raw_acl_connection = new MockLeAclConnection();
  std::unique_ptr<MockLeAclConnection> acl_connection(raw_acl_connection);
  EXPECT_CALL(*acl_connection, GetRemoteAddress()).WillRepeatedly(Return(address_with_type));
  hci::acl_manager::LeConnectionManagementCallbacks* connection_management_callbacks = nullptr;
  os::Handler* connection_management_handler = nullptr;
  EXPECT_CALL(*acl_connection, RegisterCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&connection_management_callbacks), SaveArg<1>(&connection_management_handler)));
  std::unique_ptr<FixedChannel> channel_1, channel_2;
  std::promise<void> promise_1, promise_2;
  auto future_1 = promise_1.get_future();
  auto future_2 = promise_2.get_future();
  EXPECT_CALL(mock_service_1, NotifyChannelCreation(_))
      .WillOnce([&channel_1, &promise_1](std::unique_ptr<FixedChannel> channel) {
        channel_1 = std::move(channel);
        promise_1.set_value();
      });
  EXPECT_CALL(mock_service_2, NotifyChannelCreation(_))
      .WillOnce([&channel_2, &promise_2](std::unique_ptr<FixedChannel> channel) {
        channel_2 = std::move(channel);
        promise_2.set_value();
      });
  hci_callback_handler->Post(common::BindOnce(&hci::acl_manager::LeConnectionCallbacks::OnLeConnectSuccess,
                                              common::Unretained(hci_le_connection_callbacks), address_with_type,
                                              std::move(acl_connection)));
  SyncHandler(hci_callback_handler);
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnReadRemoteVersionInformationComplete,
      common::Unretained(connection_management_callbacks),
      hci::ErrorCode::SUCCESS,
      0,
      0,
      0));
  auto future_1_status = future_1.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_1_status, std::future_status::ready);
  EXPECT_NE(channel_1, nullptr);
  auto future_2_status = future_2.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_2_status, std::future_status::ready);
  EXPECT_NE(channel_2, nullptr);
  hci::ErrorCode status_1 = hci::ErrorCode::SUCCESS;
  channel_1->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_1 = status; }));
  hci::ErrorCode status_2 = hci::ErrorCode::SUCCESS;
  channel_2->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_2 = status; }));

  // Step 4: Leave channel IDLE long enough, they will disconnect
  EXPECT_CALL(*raw_acl_connection, Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION)).Times(1);
  std::this_thread::sleep_for(kTestIdleDisconnectTimeoutShort * 1.2);
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnDisconnection,
      common::Unretained(connection_management_callbacks), hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST));
  SyncHandler(connection_management_handler);

  // Step 5: Link disconnect will trigger all callbacks
  SyncHandler(user_handler_);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_1);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_2);
}

TEST_F(L2capLeLinkManagerTest, acquiring_channels_should_not_disconnect_acl_after_timeout) {
  EXPECT_CALL(*mock_parameter_provider_, GetLeLinkIdleDisconnectTimeout)
      .WillRepeatedly(Return(kTestIdleDisconnectTimeoutShort));
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::RANDOM_DEVICE_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Register fake services
  MockFixedChannelServiceImpl mock_service_1, mock_service_2;
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  results.emplace_back(kSmpBrCid, &mock_service_1);
  results.emplace_back(kConnectionlessCid, &mock_service_2);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without ACL connection should trigger ACL connection process
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(1);
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::BindOnce([](FixedChannelManager::ConnectionResult result) { FAIL(); })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));

  // Step 3: ACL connection success event should trigger channel creation for all registered services
  auto* raw_acl_connection = new MockLeAclConnection();
  std::unique_ptr<MockLeAclConnection> acl_connection(raw_acl_connection);
  EXPECT_CALL(*acl_connection, GetRemoteAddress()).WillRepeatedly(Return(address_with_type));
  hci::acl_manager::LeConnectionManagementCallbacks* connection_management_callbacks = nullptr;
  os::Handler* connection_management_handler = nullptr;
  EXPECT_CALL(*acl_connection, RegisterCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&connection_management_callbacks), SaveArg<1>(&connection_management_handler)));
  std::unique_ptr<FixedChannel> channel_1, channel_2;
  std::promise<void> promise_1, promise_2;
  auto future_1 = promise_1.get_future();
  auto future_2 = promise_2.get_future();
  EXPECT_CALL(mock_service_1, NotifyChannelCreation(_))
      .WillOnce([&channel_1, &promise_1](std::unique_ptr<FixedChannel> channel) {
        channel_1 = std::move(channel);
        promise_1.set_value();
      });
  EXPECT_CALL(mock_service_2, NotifyChannelCreation(_))
      .WillOnce([&channel_2, &promise_2](std::unique_ptr<FixedChannel> channel) {
        channel_2 = std::move(channel);
        promise_2.set_value();
      });
  hci_callback_handler->Post(common::BindOnce(&hci::acl_manager::LeConnectionCallbacks::OnLeConnectSuccess,
                                              common::Unretained(hci_le_connection_callbacks), address_with_type,
                                              std::move(acl_connection)));
  SyncHandler(hci_callback_handler);
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnReadRemoteVersionInformationComplete,
      common::Unretained(connection_management_callbacks),
      hci::ErrorCode::SUCCESS,
      0,
      0,
      0));
  auto future_1_status = future_1.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_1_status, std::future_status::ready);
  EXPECT_NE(channel_1, nullptr);
  auto future_2_status = future_2.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_2_status, std::future_status::ready);
  EXPECT_NE(channel_2, nullptr);
  hci::ErrorCode status_1 = hci::ErrorCode::SUCCESS;
  channel_1->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_1 = status; }));
  hci::ErrorCode status_2 = hci::ErrorCode::SUCCESS;
  channel_2->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_2 = status; }));

  channel_1->Acquire();

  // Step 4: ave channel IDLE, it won't disconnect to due acquired channel 1
  EXPECT_CALL(*raw_acl_connection, Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION)).Times(0);
  std::this_thread::sleep_for(kTestIdleDisconnectTimeoutShort * 2);

  // Step 5: Link disconnect will trigger all callbacks
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnDisconnection,
      common::Unretained(connection_management_callbacks), hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST));
  SyncHandler(connection_management_handler);
  SyncHandler(user_handler_);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_1);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_2);
}

TEST_F(L2capLeLinkManagerTest, acquiring_and_releasing_channels_should_eventually_disconnect_acl) {
  EXPECT_CALL(*mock_parameter_provider_, GetLeLinkIdleDisconnectTimeout)
      .WillRepeatedly(Return(kTestIdleDisconnectTimeoutShort));
  MockFixedChannelServiceManagerImpl mock_le_fixed_channel_service_manager;
  MockAclManager mock_acl_manager;
  hci::AddressWithType address_with_type({{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}},
                                         hci::AddressType::PUBLIC_IDENTITY_ADDRESS);

  // Step 1: Verify callback registration with HCI
  hci::acl_manager::LeConnectionCallbacks* hci_le_connection_callbacks = nullptr;
  os::Handler* hci_callback_handler = nullptr;
  EXPECT_CALL(mock_acl_manager, RegisterLeCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&hci_le_connection_callbacks), SaveArg<1>(&hci_callback_handler)));
  LinkManager le_link_manager(
      l2cap_handler_,
      &mock_acl_manager,
      &mock_le_fixed_channel_service_manager,
      nullptr,
      mock_parameter_provider_);
  EXPECT_EQ(hci_le_connection_callbacks, &le_link_manager);
  EXPECT_EQ(hci_callback_handler, l2cap_handler_);

  // Register fake services
  MockFixedChannelServiceImpl mock_service_1, mock_service_2;
  std::vector<std::pair<Cid, FixedChannelServiceImpl*>> results;
  results.emplace_back(kSmpBrCid, &mock_service_1);
  results.emplace_back(kConnectionlessCid, &mock_service_2);
  EXPECT_CALL(mock_le_fixed_channel_service_manager, GetRegisteredServices()).WillRepeatedly(Return(results));

  // Step 2: Connect to fixed channel without ACL connection should trigger ACL connection process
  EXPECT_CALL(mock_acl_manager, CreateLeConnection(address_with_type, true)).Times(1);
  LinkManager::PendingFixedChannelConnection pending_fixed_channel_connection{
      .handler_ = user_handler_,
      .on_fail_callback_ = common::BindOnce([](FixedChannelManager::ConnectionResult result) { FAIL(); })};
  le_link_manager.ConnectFixedChannelServices(address_with_type, std::move(pending_fixed_channel_connection));

  // Step 3: ACL connection success event should trigger channel creation for all registered services
  auto* raw_acl_connection = new MockLeAclConnection();
  std::unique_ptr<MockLeAclConnection> acl_connection(raw_acl_connection);
  EXPECT_CALL(*acl_connection, GetRemoteAddress()).WillRepeatedly(Return(address_with_type));
  hci::acl_manager::LeConnectionManagementCallbacks* connection_management_callbacks = nullptr;
  os::Handler* connection_management_handler = nullptr;
  EXPECT_CALL(*acl_connection, RegisterCallbacks(_, _))
      .WillOnce(DoAll(SaveArg<0>(&connection_management_callbacks), SaveArg<1>(&connection_management_handler)));
  std::unique_ptr<FixedChannel> channel_1, channel_2;
  std::promise<void> promise_1, promise_2;
  auto future_1 = promise_1.get_future();
  auto future_2 = promise_2.get_future();
  EXPECT_CALL(mock_service_1, NotifyChannelCreation(_))
      .WillOnce([&channel_1, &promise_1](std::unique_ptr<FixedChannel> channel) {
        channel_1 = std::move(channel);
        promise_1.set_value();
      });
  EXPECT_CALL(mock_service_2, NotifyChannelCreation(_))
      .WillOnce([&channel_2, &promise_2](std::unique_ptr<FixedChannel> channel) {
        channel_2 = std::move(channel);
        promise_2.set_value();
      });
  hci_callback_handler->Post(common::BindOnce(&hci::acl_manager::LeConnectionCallbacks::OnLeConnectSuccess,
                                              common::Unretained(hci_le_connection_callbacks), address_with_type,
                                              std::move(acl_connection)));
  SyncHandler(hci_callback_handler);
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnReadRemoteVersionInformationComplete,
      common::Unretained(connection_management_callbacks),
      hci::ErrorCode::SUCCESS,
      0,
      0,
      0));
  auto future_1_status = future_1.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_1_status, std::future_status::ready);
  EXPECT_NE(channel_1, nullptr);
  auto future_2_status = future_2.wait_for(kTestIdleDisconnectTimeoutShort);
  EXPECT_EQ(future_2_status, std::future_status::ready);
  EXPECT_NE(channel_2, nullptr);
  hci::ErrorCode status_1 = hci::ErrorCode::SUCCESS;
  channel_1->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_1 = status; }));
  hci::ErrorCode status_2 = hci::ErrorCode::SUCCESS;
  channel_2->RegisterOnCloseCallback(
      user_handler_, common::testing::BindLambdaForTesting([&](hci::ErrorCode status) { status_2 = status; }));

  channel_1->Acquire();

  // Step 4: ave channel IDLE, it won't disconnect to due acquired channel 1
  EXPECT_CALL(*raw_acl_connection, Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION)).Times(0);
  std::this_thread::sleep_for(kTestIdleDisconnectTimeoutShort * 2);

  // Step 5: ave channel IDLE long enough, they will disconnect
  channel_1->Release();
  EXPECT_CALL(*raw_acl_connection, Disconnect(hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION)).Times(1);
  std::this_thread::sleep_for(kTestIdleDisconnectTimeoutShort * 1.2);

  // Step 6: Link disconnect will trigger all callbacks
  connection_management_handler->Post(common::BindOnce(
      &hci::acl_manager::LeConnectionManagementCallbacks::OnDisconnection,
      common::Unretained(connection_management_callbacks), hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST));
  SyncHandler(connection_management_handler);
  SyncHandler(user_handler_);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_1);
  EXPECT_EQ(hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST, status_2);
}

}  // namespace
}  // namespace internal
}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
