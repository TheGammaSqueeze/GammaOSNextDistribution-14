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

#include "hci/hci_layer.h"

#include <gtest/gtest.h>

#include <chrono>
#include <future>

#include "common/bind.h"
#include "common/init_flags.h"
#include "hal/hci_hal_fake.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "hci/controller.h"
#include "module.h"
#include "os/fake_timer/fake_timerfd.h"
#include "os/handler.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

using namespace std::chrono_literals;

namespace {
constexpr char kOurAclEventHandlerWasInvoked[] = "Our ACL event handler was invoked.";
constexpr char kOurCommandCompleteHandlerWasInvoked[] = "Our command complete handler was invoked.";
constexpr char kOurCommandStatusHandlerWasInvoked[] = "Our command status handler was invoked.";
constexpr char kOurDisconnectHandlerWasInvoked[] = "Our disconnect handler was invoked.";
constexpr char kOurEventHandlerWasInvoked[] = "Our event handler was invoked.";
constexpr char kOurLeAclEventHandlerWasInvoked[] = "Our LE ACL event handler was invoked.";
constexpr char kOurLeAdvertisementEventHandlerWasInvoked[] = "Our LE advertisement event handler was invoked.";
constexpr char kOurLeDisconnectHandlerWasInvoked[] = "Our LE disconnect handler was invoked.";
constexpr char kOurLeEventHandlerWasInvoked[] = "Our LE event handler was invoked.";
constexpr char kOurLeIsoEventHandlerWasInvoked[] = "Our LE ISO event handler was invoked.";
constexpr char kOurLeReadRemoteVersionHandlerWasInvoked[] = "Our Read Remote Version complete handler was invoked.";
constexpr char kOurLeScanningEventHandlerWasInvoked[] = "Our LE scanning event handler was invoked.";
constexpr char kOurReadRemoteVersionHandlerWasInvoked[] = "Our Read Remote Version complete handler was invoked.";
constexpr char kOurLeSecurityEventHandlerWasInvoked[] = "Our LE security event handler was invoked.";
constexpr char kOurSecurityEventHandlerWasInvoked[] = "Our security event handler was invoked.";
}  // namespace

namespace bluetooth {
namespace hci {

using common::BidiQueue;
using common::BidiQueueEnd;
using common::InitFlags;
using os::fake_timer::fake_timerfd_advance;
using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

std::vector<uint8_t> GetPacketBytes(std::unique_ptr<packet::BasePacketBuilder> packet) {
  std::vector<uint8_t> bytes;
  BitInserter i(bytes);
  bytes.reserve(packet->size());
  packet->Serialize(i);
  return bytes;
}

class HciLayerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    hal_ = new hal::TestHciHal();
    fake_registry_.InjectTestModule(&hal::HciHal::Factory, hal_);
    fake_registry_.Start<HciLayer>(&fake_registry_.GetTestThread());
    hci_ = static_cast<HciLayer*>(fake_registry_.GetModuleUnderTest(&HciLayer::Factory));
    hci_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_TRUE(fake_registry_.IsStarted<HciLayer>());
    ::testing::FLAGS_gtest_death_test_style = "threadsafe";
    InitFlags::SetAllForTesting();
    sync_handler();
  }

  void TearDown() override {
    fake_registry_.SynchronizeModuleHandler(&HciLayer::Factory, std::chrono::milliseconds(20));
    fake_registry_.StopAll();
  }

  void FakeTimerAdvance(uint64_t ms) {
    hci_handler_->Post(common::BindOnce(fake_timerfd_advance, ms));
  }

  void FailIfResetNotSent() {
    hci_handler_->BindOnceOn(this, &HciLayerTest::fail_if_reset_not_sent).Invoke();
    sync_handler();
  }

  void fail_if_reset_not_sent() {
    auto sent_command = hal_->GetSentCommand();
    ASSERT_TRUE(sent_command.has_value());
    auto reset_view = ResetView::Create(CommandView::Create(*sent_command));
    ASSERT_TRUE(reset_view.IsValid());
  }

  void sync_handler() {
    ASSERT(fake_registry_.GetTestThread().GetReactor()->WaitForIdle(2s));
  }

  hal::TestHciHal* hal_ = nullptr;
  HciLayer* hci_ = nullptr;
  os::Handler* hci_handler_ = nullptr;
  TestModuleRegistry fake_registry_;
};

class HciLayerDeathTest : public HciLayerTest {};

TEST_F(HciLayerTest, setup_teardown) {}

TEST_F(HciLayerTest, reset_command_sent_on_start) {
  FailIfResetNotSent();
}

TEST_F(HciLayerTest, controller_debug_info_requested_on_hci_timeout) {
  FailIfResetNotSent();
  FakeTimerAdvance(HciLayer::kHciTimeoutMs.count());

  sync_handler();

  auto sent_command = hal_->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto debug_info_view = ControllerDebugInfoView::Create(VendorCommandView::Create(*sent_command));
  ASSERT_TRUE(debug_info_view.IsValid());
}

TEST_F(HciLayerDeathTest, abort_after_hci_restart_timeout) {
  FailIfResetNotSent();
  FakeTimerAdvance(HciLayer::kHciTimeoutMs.count());

  auto sent_command = hal_->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto debug_info_view = ControllerDebugInfoView::Create(VendorCommandView::Create(*sent_command));
  ASSERT_TRUE(debug_info_view.IsValid());

  ASSERT_DEATH(
      {
        sync_handler();
        FakeTimerAdvance(HciLayer::kHciTimeoutRestartMs.count());
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, discard_event_after_hci_timeout) {
  FailIfResetNotSent();
  FakeTimerAdvance(HciLayer::kHciTimeoutMs.count());

  auto sent_command = hal_->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto debug_info_view = ControllerDebugInfoView::Create(VendorCommandView::Create(*sent_command));
  ASSERT_TRUE(debug_info_view.IsValid());

  // This event should be discarded, not cause an abort.
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  sync_handler();

  ASSERT_DEATH(
      {
        FakeTimerAdvance(HciLayer::kHciTimeoutRestartMs.count());
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, abort_on_root_inflammation_event) {
  FailIfResetNotSent();

  ASSERT_DEATH(
      {
        sync_handler();
        hal_->InjectEvent(BqrRootInflammationEventBuilder::Create(
            0x01, 0x01, std::make_unique<packet::RawBuilder>()));
        FakeTimerAdvance(HciLayer::kHciTimeoutRestartMs.count());
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, abort_on_hardware_error) {
  FailIfResetNotSent();

  ASSERT_DEATH(
      {
        sync_handler();
        hal_->InjectEvent(HardwareErrorBuilder::Create(0xbb));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerTest, successful_reset) {
  FailIfResetNotSent();
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  sync_handler();
}

TEST_F(HciLayerDeathTest, abort_if_reset_complete_returns_error) {
  FailIfResetNotSent();
  ASSERT_DEATH(
      {
        hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::HARDWARE_FAILURE));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerTest, event_handler_is_invoked) {
  FailIfResetNotSent();
  hci_->RegisterEventHandler(
      EventCode::COMMAND_COMPLETE, hci_handler_->Bind([](EventView /* view */) {
        LOG_DEBUG("%s", kOurEventHandlerWasInvoked);
      }));
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
}

TEST_F(HciLayerTest, le_event_handler_is_invoked) {
  FailIfResetNotSent();
  hci_->RegisterLeEventHandler(
      SubeventCode::ENHANCED_CONNECTION_COMPLETE,
      hci_handler_->Bind(
          [](LeMetaEventView /* view */) { LOG_DEBUG("%s", kOurLeEventHandlerWasInvoked); }));
  hci::Address remote_address;
  Address::FromString("D0:05:04:03:02:01", remote_address);
  hal_->InjectEvent(LeEnhancedConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS,
      0x0041,
      Role::PERIPHERAL,
      AddressType::PUBLIC_DEVICE_ADDRESS,
      remote_address,
      Address::kEmpty,
      Address::kEmpty,
      0x0024,
      0x0000,
      0x0011,
      ClockAccuracy::PPM_30));
}

TEST_F(HciLayerDeathTest, abort_on_second_register_event_handler) {
  FailIfResetNotSent();
  ASSERT_DEATH(
      {
        hci_->RegisterEventHandler(
            EventCode::SIMPLE_PAIRING_COMPLETE, hci_handler_->Bind([](EventView /* view */) {}));
        hci_->RegisterEventHandler(
            EventCode::SIMPLE_PAIRING_COMPLETE, hci_handler_->Bind([](EventView /* view */) {}));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, abort_on_second_register_le_event_handler) {
  ASSERT_DEATH(
      {
        FailIfResetNotSent();
        hci_->RegisterLeEventHandler(
            SubeventCode::ENHANCED_CONNECTION_COMPLETE,
            hci_handler_->Bind([](LeMetaEventView /* view */) {}));
        hci_->RegisterLeEventHandler(
            SubeventCode::ENHANCED_CONNECTION_COMPLETE,
            hci_handler_->Bind([](LeMetaEventView /* view */) {}));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerTest, our_acl_event_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetAclConnectionInterface(
      hci_handler_->Bind(
          [](EventView /* view */) { LOG_DEBUG("%s", kOurAclEventHandlerWasInvoked); }),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {}),
      hci_handler_->Bind([](Address /* bd_addr */, ClassOfDevice /* cod */) {}),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {}));
  hal_->InjectEvent(ReadClockOffsetCompleteBuilder::Create(ErrorCode::SUCCESS, 0x0001, 0x0123));
}

TEST_F(HciLayerTest, our_disconnect_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetAclConnectionInterface(
      hci_handler_->Bind([](EventView /* view */) {}),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {
        LOG_DEBUG("%s", kOurDisconnectHandlerWasInvoked);
      }),
      hci_handler_->Bind([](Address /* bd_addr */, ClassOfDevice /* cod */) {}),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {}));
  hal_->InjectEvent(DisconnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS, 0x0001, ErrorCode::REMOTE_USER_TERMINATED_CONNECTION));
}

TEST_F(HciLayerTest, our_read_remote_version_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetAclConnectionInterface(
      hci_handler_->Bind([](EventView /* view */) {}),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {}),
      hci_handler_->Bind([](Address /* bd_addr */, ClassOfDevice /* cod */) {}),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {
        LOG_DEBUG("%s", kOurReadRemoteVersionHandlerWasInvoked);
      }));
  hal_->InjectEvent(ReadRemoteVersionInformationCompleteBuilder::Create(
      ErrorCode::SUCCESS, 0x0001, 0x0b, 0x000f, 0x0000));
}

TEST_F(HciLayerTest, our_le_acl_event_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeAclConnectionInterface(
      hci_handler_->Bind(
          [](LeMetaEventView /* view */) { LOG_DEBUG("%s", kOurLeAclEventHandlerWasInvoked); }),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {}),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {}));
  hal_->InjectEvent(LeDataLengthChangeBuilder::Create(0x0001, 0x001B, 0x0148, 0x001B, 0x0148));
}

TEST_F(HciLayerTest, our_le_disconnect_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeAclConnectionInterface(
      hci_handler_->Bind([](LeMetaEventView /* view */) {}),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {
        LOG_DEBUG("%s", kOurLeDisconnectHandlerWasInvoked);
      }),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {}));
  hal_->InjectEvent(DisconnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS, 0x0001, ErrorCode::REMOTE_USER_TERMINATED_CONNECTION));
}

TEST_F(HciLayerTest, our_le_read_remote_version_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeAclConnectionInterface(
      hci_handler_->Bind([](LeMetaEventView /* view */) {}),
      hci_handler_->Bind([](uint16_t /* handle */, ErrorCode /* reason */) {}),
      hci_handler_->Bind([](hci::ErrorCode /* hci_status */,
                            uint16_t /* handle */,
                            uint8_t /* version */,
                            uint16_t /* manufacturer_name */,
                            uint16_t /* sub_version */) {
        LOG_DEBUG("%s", kOurLeReadRemoteVersionHandlerWasInvoked);
      }));
  hal_->InjectEvent(ReadRemoteVersionInformationCompleteBuilder::Create(
      ErrorCode::SUCCESS, 0x0001, 0x0b, 0x000f, 0x0000));
}

TEST_F(HciLayerTest, our_security_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetSecurityInterface(hci_handler_->Bind(
      [](EventView /* view */) { LOG_DEBUG("%s", kOurSecurityEventHandlerWasInvoked); }));
  hal_->InjectEvent(EncryptionChangeBuilder::Create(
      ErrorCode::SUCCESS, 0x0001, bluetooth::hci::EncryptionEnabled::ON));
}

TEST_F(HciLayerTest, our_le_security_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeSecurityInterface(hci_handler_->Bind(
      [](LeMetaEventView /* view */) { LOG_DEBUG("%s", kOurLeSecurityEventHandlerWasInvoked); }));
  hal_->InjectEvent(LeLongTermKeyRequestBuilder::Create(0x0001, {0, 0, 0, 0, 0, 0, 0, 0}, 0));
}

TEST_F(HciLayerTest, our_le_advertising_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeAdvertisingInterface(hci_handler_->Bind([](LeMetaEventView /* view */) {
    LOG_DEBUG("%s", kOurLeAdvertisementEventHandlerWasInvoked);
  }));
  hal_->InjectEvent(
      LeAdvertisingSetTerminatedBuilder::Create(ErrorCode::SUCCESS, 0x01, 0x001, 0x01));
}

TEST_F(HciLayerTest, our_le_scanning_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeScanningInterface(hci_handler_->Bind(
      [](LeMetaEventView /* view */) { LOG_DEBUG("%s", kOurLeScanningEventHandlerWasInvoked); }));
  hal_->InjectEvent(LeScanTimeoutBuilder::Create());
}

TEST_F(HciLayerTest, our_le_iso_callback_is_invoked) {
  FailIfResetNotSent();
  hci_->GetLeIsoInterface(hci_handler_->Bind(
      [](LeMetaEventView /* view */) { LOG_DEBUG("%s", kOurLeIsoEventHandlerWasInvoked); }));
  hal_->InjectEvent(LeCisRequestBuilder::Create(0x0001, 0x0001, 0x01, 0x01));
}

TEST_F(HciLayerTest, our_command_complete_callback_is_invoked) {
  FailIfResetNotSent();
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  hci_->EnqueueCommand(
      ResetBuilder::Create(), hci_handler_->BindOnce([](CommandCompleteView /* view */) {
        LOG_DEBUG("%s", kOurCommandCompleteHandlerWasInvoked);
      }));
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
}

TEST_F(HciLayerTest, our_command_status_callback_is_invoked) {
  FailIfResetNotSent();
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  hci_->EnqueueCommand(
      ReadClockOffsetBuilder::Create(0x001),
      hci_handler_->BindOnce([](CommandStatusView /* view */) {
        LOG_DEBUG("%s", kOurCommandStatusHandlerWasInvoked);
      }));
  hal_->InjectEvent(ReadClockOffsetStatusBuilder::Create(ErrorCode::SUCCESS, 1));
}

TEST_F(
    HciLayerDeathTest,
    command_complete_callback_is_invoked_with_an_opcode_that_does_not_match_command_queue) {
  ASSERT_DEATH(
      {
        FailIfResetNotSent();
        hci_->EnqueueCommand(
            ReadClockOffsetBuilder::Create(0x001),
            hci_handler_->BindOnce([](CommandCompleteView /* view */) {}));
        hal_->InjectEvent(ReadClockOffsetStatusBuilder::Create(ErrorCode::SUCCESS, 1));
        sync_handler();
      },
      "");
}

TEST_F(
    HciLayerDeathTest,
    command_status_callback_is_invoked_with_an_opcode_that_does_not_match_command_queue) {
  ASSERT_DEATH(
      {
        FailIfResetNotSent();
        hci_->EnqueueCommand(
            ReadClockOffsetBuilder::Create(0x001),
            hci_handler_->BindOnce([](CommandStatusView /* view */) {}));
        hal_->InjectEvent(ReadClockOffsetStatusBuilder::Create(ErrorCode::SUCCESS, 1));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, command_complete_callback_is_invoked_but_command_queue_empty) {
  ASSERT_DEATH(
      {
        FailIfResetNotSent();
        hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
        hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerDeathTest, command_status_callback_is_invoked_but_command_queue_empty) {
  ASSERT_DEATH(
      {
        FailIfResetNotSent();
        hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
        hal_->InjectEvent(ReadClockOffsetStatusBuilder::Create(ErrorCode::SUCCESS, 1));
        sync_handler();
      },
      "");
}

TEST_F(HciLayerTest, command_status_callback_is_invoked_with_failure_status) {
  FailIfResetNotSent();
  hal_->InjectEvent(ResetCompleteBuilder::Create(1, ErrorCode::SUCCESS));
  hci_->EnqueueCommand(
      ReadClockOffsetBuilder::Create(0x001),
      hci_handler_->BindOnce([](CommandStatusView /* view */) {}));
  hal_->InjectEvent(ReadClockOffsetStatusBuilder::Create(ErrorCode::HARDWARE_FAILURE, 1));
  sync_handler();
}

}  // namespace hci
}  // namespace bluetooth
