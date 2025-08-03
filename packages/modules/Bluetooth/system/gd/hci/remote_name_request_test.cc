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

#include "hci/remote_name_request.h"

#include <android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <chrono>
#include <future>
#include <tuple>
#include <utility>

#include "hci/address.h"
#include "hci/hci_layer_fake.h"
#include "os/thread.h"

namespace bluetooth {
namespace hci {
namespace {

using ::testing::Eq;

const auto address1 = Address::FromString("A1:A2:A3:A4:A5:A6").value();
const auto address2 = Address::FromString("B1:B2:B3:B4:B5:B6").value();
const auto address3 = Address::FromString("C1:C2:C3:C4:C5:C6").value();

const auto remote_name1 = std::array<uint8_t, 248>{1, 2, 3};

const auto timeout = std::chrono::milliseconds(100);

MATCHER(IsSet, "Future is not set") {
  if (arg.wait_for(timeout) != std::future_status::ready) {
    return false;
  }
  const_cast<std::future<void>&>(arg).get();
  return true;
}

MATCHER_P(IsSetWithValue, matcher, "Future is not set with value") {
  if (arg.wait_for(timeout) != std::future_status::ready) {
    return false;
  }
  EXPECT_THAT(const_cast<std::decay_t<decltype(arg)>&>(arg).get(), matcher);
  return true;
}

class RemoteNameRequestModuleTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_hci_layer_ = new HciLayerFake;
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);

    fake_registry_.Start<RemoteNameRequestModule>(&thread_);
    ASSERT_TRUE(fake_registry_.IsStarted<RemoteNameRequestModule>());

    client_handler_ = fake_registry_.GetTestModuleHandler(&RemoteNameRequestModule::Factory);
    ASSERT_NE(client_handler_, nullptr);

    remote_name_request_module_ = static_cast<RemoteNameRequestModule*>(
        fake_registry_.GetModuleUnderTest(&RemoteNameRequestModule::Factory));

    ::testing::FLAGS_gtest_death_test_style = "threadsafe";
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&RemoteNameRequestModule::Factory, timeout);
    fake_registry_.StopAll();
  }

  template <typename... T>
  common::ContextualOnceCallback<void(T...)> impossibleCallback() {
    return client_handler_->BindOnce([](T... /* args */) { ADD_FAILURE(); });
  }

  template <typename... T>
  common::ContextualOnceCallback<void(T...)> emptyCallback() {
    return client_handler_->BindOnce([](T... /* args */) {});
  }

  template <typename... T>
  common::ContextualOnceCallback<void(T...)> promiseCallback(std::promise<void> promise) {
    return client_handler_->BindOnce(
        [](std::promise<void> promise, T... /* args */) { promise.set_value(); },
        std::move(promise));
  }

  template <typename... T>
  common::ContextualOnceCallback<void(T...)> capturingPromiseCallback(
      std::promise<std::tuple<T...>> promise) {
    return client_handler_->BindOnce(
        [](std::promise<std::tuple<T...>> promise, T... args) {
          promise.set_value(std::make_tuple(args...));
        },
        std::move(promise));
  }

  template <typename T>
  common::ContextualOnceCallback<void(T)> capturingPromiseCallback(std::promise<T> promise) {
    return client_handler_->BindOnce(
        [](std::promise<T> promise, T arg) { promise.set_value(arg); }, std::move(promise));
  }

  TestModuleRegistry fake_registry_;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  HciLayerFake* test_hci_layer_ = nullptr;
  RemoteNameRequestModule* remote_name_request_module_ = nullptr;
  os::Handler* client_handler_ = nullptr;
};

TEST_F(RemoteNameRequestModuleTest, CorrectCommandSent) {
  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      impossibleCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());

  // verify that the correct HCI command was sent
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address1);
  EXPECT_EQ(rnr_command.GetPageScanRepetitionMode(), PageScanRepetitionMode::R0);
  EXPECT_EQ(rnr_command.GetClockOffset(), 3);
  EXPECT_EQ(rnr_command.GetClockOffsetValid(), ClockOffsetValid::INVALID);
}

TEST_F(RemoteNameRequestModuleTest, FailToSendCommand) {
  auto promise = std::promise<ErrorCode>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      capturingPromiseCallback<ErrorCode>(std::move(promise)),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());
  // on the command, return a failure HCI status
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestStatusBuilder::Create(ErrorCode::STATUS_UNKNOWN, 1));

  // the completion callback should be immediately invoked with the failing status
  EXPECT_THAT(future, IsSetWithValue(Eq(ErrorCode::STATUS_UNKNOWN)));
}

TEST_F(RemoteNameRequestModuleTest, SendCommandSuccessfully) {
  auto promise = std::promise<ErrorCode>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      capturingPromiseCallback<ErrorCode>(std::move(promise)),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());
  // the command receives a successful reply, so it successfully starts
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // the completion callback should be invoked with the failing status
  EXPECT_THAT(future, IsSetWithValue(Eq(ErrorCode::SUCCESS)));
}

TEST_F(RemoteNameRequestModuleTest, SendCommandThenCancelIt) {
  auto promise = std::promise<ErrorCode>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());

  // we successfully start
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // but then the request is cancelled
  remote_name_request_module_->CancelRemoteNameRequest(address1);

  // get the cancel command and check it is correct
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto cancel_command =
      RemoteNameRequestCancelView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(cancel_command.IsValid());
  EXPECT_EQ(cancel_command.GetBdAddr(), address1);
}

TEST_F(RemoteNameRequestModuleTest, SendCommandThenCancelItCallback) {
  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));

  // we successfully start
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // but then the request is cancelled successfully (the status doesn't matter)
  remote_name_request_module_->CancelRemoteNameRequest(address1);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCancelCompleteBuilder::Create(1, ErrorCode::SUCCESS, address1));

  // verify that the completion has NOT yet been invoked (we need to wait for the RNR itself to
  // complete)
  EXPECT_THAT(future.wait_for(timeout), std::future_status::timeout);

  // let the RNR complete with a failure
  test_hci_layer_->IncomingEvent(RemoteNameRequestCompleteBuilder::Create(
      ErrorCode::UNKNOWN_CONNECTION, address1, remote_name1));

  // only now should the name callback be invoked
  EXPECT_THAT(
      future, IsSetWithValue(Eq(std::make_tuple(ErrorCode::UNKNOWN_CONNECTION, remote_name1))));
}

// TODO(aryarahul) - unify HciLayerFake so this test can be run
TEST_F(RemoteNameRequestModuleTest, DISABLED_SendCommandThenCancelItCallbackInteropWorkaround) {
  // Some controllers INCORRECTLY give us an ACL Connection Complete event, rather than a Remote
  // Name Request Complete event, if we issue a cancellation. We should nonetheless handle this
  // properly.

  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));

  // we successfully start
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // but then the request is cancelled successfully (the status doesn't matter)
  remote_name_request_module_->CancelRemoteNameRequest(address1);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCancelCompleteBuilder::Create(1, ErrorCode::SUCCESS, address1));

  // get the INCORRECT ACL connection complete event
  test_hci_layer_->IncomingEvent(ConnectionCompleteBuilder::Create(
      ErrorCode::UNKNOWN_CONNECTION, 0, address1, LinkType::ACL, Enable::DISABLED));

  // we expect the name callback to be invoked nonetheless
  EXPECT_THAT(
      future,
      IsSetWithValue(
          Eq(std::make_tuple(ErrorCode::UNKNOWN_CONNECTION, std::array<uint8_t, 248>{}))));
}

// This test should be replaced with the above one, so we test the integration of AclManager and
// RnrModule
TEST_F(RemoteNameRequestModuleTest, SendCommandThenCancelItCallbackInteropWorkaround) {
  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));

  // we successfully start
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // but then the request is cancelled successfully (the status doesn't matter)
  remote_name_request_module_->CancelRemoteNameRequest(address1);
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCancelCompleteBuilder::Create(1, ErrorCode::SUCCESS, address1));

  // the INCORRECT ACL connection complete event will, from ACLManager, trigger this event
  remote_name_request_module_->ReportRemoteNameRequestCancellation(address1);

  // we expect the name callback to be invoked nonetheless
  EXPECT_THAT(
      future,
      IsSetWithValue(
          Eq(std::make_tuple(ErrorCode::UNKNOWN_CONNECTION, std::array<uint8_t, 248>{}))));
}

TEST_F(RemoteNameRequestModuleTest, HostSupportedEvents) {
  auto promise = std::promise<uint64_t>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      capturingPromiseCallback<uint64_t>(std::move(promise)),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // verify that the completion has NOT yet been invoked (we need to wait for the RNR itself to
  // complete)
  EXPECT_THAT(future.wait_for(timeout), std::future_status::timeout);

  // report host supported events
  test_hci_layer_->IncomingEvent(
      RemoteHostSupportedFeaturesNotificationBuilder::Create(address1, 1234));

  // verify that we got the features
  EXPECT_THAT(future, IsSetWithValue(Eq((uint64_t)1234)));
}

TEST_F(RemoteNameRequestModuleTest, CompletedRemoteNameRequest) {
  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // verify that the completion has NOT yet been invoked (we need to wait for the RNR itself to
  // complete)
  EXPECT_THAT(future.wait_for(timeout), std::future_status::timeout);

  // report remote name (with some random status that should be passed through)
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCompleteBuilder::Create(ErrorCode::STATUS_UNKNOWN, address1, remote_name1));

  // verify that the callback was invoked with the same status
  EXPECT_THAT(future, IsSetWithValue(Eq(std::make_tuple(ErrorCode::STATUS_UNKNOWN, remote_name1))));
}

TEST_F(RemoteNameRequestModuleTest, QueuingRemoteNameRequestsSecondOneStarts) {
  auto promise1 = std::promise<void>{};
  auto future1 = promise1.get_future();
  auto promise2 = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future2 = promise2.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      promiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise1)));

  // enqueue a second one
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise2)));

  // acknowledge that the first one has started
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // report remote name for the first one
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCompleteBuilder::Create(ErrorCode::STATUS_UNKNOWN, address1, remote_name1));

  // verify that the first callback was invoked
  EXPECT_THAT(future1, IsSet());

  // verify that the second request has now started (so we are participating in the ACL scheduling
  // process)
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address2);
  EXPECT_EQ(rnr_command.GetPageScanRepetitionMode(), PageScanRepetitionMode::R1);
  EXPECT_EQ(rnr_command.GetClockOffset(), 4);
  EXPECT_EQ(rnr_command.GetClockOffsetValid(), ClockOffsetValid::VALID);
}

TEST_F(RemoteNameRequestModuleTest, QueuingRemoteNameRequestsSecondOneCancelledWhileQueued) {
  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      emptyCallback<ErrorCode, std::array<uint8_t, 248>>());

  // enqueue a second one
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));

  // acknowledge that the first one has started
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // cancel the second one
  remote_name_request_module_->CancelRemoteNameRequest(address2);

  // verify that the cancellation callback was properly invoked immediately
  EXPECT_THAT(
      future,
      IsSetWithValue(Eq(std::make_tuple(ErrorCode::PAGE_TIMEOUT, std::array<uint8_t, 248>{}))));
}

TEST_F(RemoteNameRequestModuleTest, QueuingRemoteNameRequestsCancelFirst) {
  auto promise1 = std::promise<void>{};
  auto future1 = promise1.get_future();
  auto promise2 = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future2 = promise2.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      promiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise1)));

  // enqueue a second one
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise2)));

  // acknowledge that the first one has started
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // cancel the first one
  remote_name_request_module_->CancelRemoteNameRequest(address1);

  // let the cancel complete
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCancelCompleteBuilder::Create(1, ErrorCode::SUCCESS, address1));
  test_hci_layer_->IncomingEvent(RemoteNameRequestCompleteBuilder::Create(
      ErrorCode::UNKNOWN_CONNECTION, address1, remote_name1));

  // verify that the second request has now started
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address2);
}

TEST_F(RemoteNameRequestModuleTest, QueuingRemoteNameRequestsCancelFirstWithBuggyController) {
  auto promise1 = std::promise<void>{};
  auto future1 = promise1.get_future();
  auto promise2 = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future2 = promise2.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      promiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise1)));

  // enqueue a second one
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise2)));

  // acknowledge that the first one has started
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(RemoteNameRequestStatusBuilder::Create(ErrorCode::SUCCESS, 1));

  // cancel the first one
  remote_name_request_module_->CancelRemoteNameRequest(address1);

  // let the cancel complete
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestCancelCompleteBuilder::Create(1, ErrorCode::SUCCESS, address1));
  // send the INCORRECT response that we tolerate for interop reasons
  remote_name_request_module_->ReportRemoteNameRequestCancellation(address1);

  // verify that the second request has now started
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address2);
}

TEST_F(RemoteNameRequestModuleTest, FailToSendCommandThenSendNext) {
  auto promise = std::promise<ErrorCode>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      capturingPromiseCallback<ErrorCode>(std::move(promise)),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());
  // on the command, return a failure HCI status
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestStatusBuilder::Create(ErrorCode::STATUS_UNKNOWN, 1));

  // start a second request
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());

  // verify that it started
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address2);
}

TEST_F(RemoteNameRequestModuleTest, FailToSendCommandThenDequeueNext) {
  auto promise = std::promise<ErrorCode>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());

  // enqueue a second one
  remote_name_request_module_->StartRemoteNameRequest(
      address2,
      RemoteNameRequestBuilder::Create(
          address2, PageScanRepetitionMode::R1, 4, ClockOffsetValid::VALID),
      impossibleCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      impossibleCallback<ErrorCode, std::array<uint8_t, 248>>());

  // for the first, return a failure HCI status
  test_hci_layer_->GetCommand();
  test_hci_layer_->IncomingEvent(
      RemoteNameRequestStatusBuilder::Create(ErrorCode::STATUS_UNKNOWN, 1));

  // verify that the second one started
  auto command = test_hci_layer_->GetCommand();
  auto discovery_command = DiscoveryCommandView::Create(command);
  ASSERT_TRUE(discovery_command.IsValid());
  auto rnr_command = RemoteNameRequestView::Create(DiscoveryCommandView::Create(discovery_command));
  ASSERT_TRUE(rnr_command.IsValid());
  EXPECT_EQ(rnr_command.GetBdAddr(), address2);
}

#define MY_PACKAGE com::android::bluetooth::flags

TEST_F_WITH_FLAGS(
    RemoteNameRequestModuleTest,
    CancelJustWhenRNREventReturns,
    REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(MY_PACKAGE, rnr_cancel_before_event_race))) {
  auto promise = std::promise<std::tuple<ErrorCode, std::array<uint8_t, 248>>>{};
  auto future = promise.get_future();

  // start a remote name request
  remote_name_request_module_->StartRemoteNameRequest(
      address1,
      RemoteNameRequestBuilder::Create(
          address1, PageScanRepetitionMode::R0, 3, ClockOffsetValid::INVALID),
      emptyCallback<ErrorCode>(),
      impossibleCallback<uint64_t>(),
      capturingPromiseCallback<ErrorCode, std::array<uint8_t, 248>>(std::move(promise)));

  // we successfully start
  test_hci_layer_->GetCommand();

  auto promise2 = std::promise<void>();
  auto future2 = promise2.get_future();
  client_handler_->Post(base::BindOnce(
      [](RemoteNameRequestModule* remote_name_request_module,
         HciLayerFake* test_hci_layer,
         std::promise<void> promise2) {
        // but then the request is cancelled successfully (the status doesn't matter)
        remote_name_request_module->CancelRemoteNameRequest(address1);

        // Send an rnr event completed with page timeout status
        test_hci_layer->IncomingEvent(
            RemoteNameRequestStatusBuilder::Create(ErrorCode::PAGE_TIMEOUT, 1));

        promise2.set_value();
      },
      remote_name_request_module_,
      test_hci_layer_,
      std::move(promise2)));

  future2.wait();
}

}  // namespace
}  // namespace hci
}  // namespace bluetooth
