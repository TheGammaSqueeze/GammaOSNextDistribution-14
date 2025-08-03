/******************************************************************************
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
 ******************************************************************************/

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <memory>

#include "common/testing/wired_pair_of_bidi_queues.h"
#include "hci/le_security_interface.h"
#include "hci/octets.h"
#include "packet/raw_builder.h"
#include "security/pairing_handler_le.h"
#include "security/test/mocks.h"

using namespace std::chrono_literals;
using testing::_;
using testing::Invoke;
using testing::InvokeWithoutArgs;
using testing::Matcher;
using testing::SaveArg;

using bluetooth::hci::Address;
using bluetooth::hci::AddressType;
using bluetooth::hci::CommandCompleteView;
using bluetooth::hci::CommandStatusView;
using bluetooth::hci::EncryptionChangeBuilder;
using bluetooth::hci::EncryptionEnabled;
using bluetooth::hci::ErrorCode;
using bluetooth::hci::EventBuilder;
using bluetooth::hci::EventView;
using bluetooth::hci::LeSecurityCommandBuilder;

// run:
// out/host/linux-x86/nativetest/bluetooth_test_gd/bluetooth_test_gd --gtest_filter=Pairing*
// adb shell /data/nativetest/bluetooth_test_gd/bluetooth_test_gd  --gtest_filter=PairingHandlerPairTest.*
// --gtest_repeat=10 --gtest_shuffle

namespace bluetooth {
namespace security {
CommandView CommandBuilderToView(std::unique_ptr<BasePacketBuilder> builder) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter it(*packet_bytes);
  builder->Serialize(it);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto temp_cmd_view = CommandView::Create(packet_bytes_view);
  return CommandView::Create(temp_cmd_view);
}

EventView EventBuilderToView(std::unique_ptr<EventBuilder> builder) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter it(*packet_bytes);
  builder->Serialize(it);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto temp_evt_view = EventView::Create(packet_bytes_view);
  return EventView::Create(temp_evt_view);
}
}  // namespace security
}  // namespace bluetooth

namespace {

constexpr uint16_t CONN_HANDLE_CENTRAL = 0x31, CONN_HANDLE_PERIPHERAL = 0x32;
std::unique_ptr<bluetooth::security::PairingHandlerLe> pairing_handler_a, pairing_handler_b;

}  // namespace

namespace bluetooth {
namespace security {

namespace {
Address ADDRESS_CENTRAL{{0x26, 0x64, 0x76, 0x86, 0xab, 0xba}};
AddressType ADDRESS_TYPE_CENTRAL = AddressType::RANDOM_DEVICE_ADDRESS;
Address IDENTITY_ADDRESS_CENTRAL{{0x12, 0x34, 0x56, 0x78, 0x90, 0xaa}};
AddressType IDENTITY_ADDRESS_TYPE_CENTRAL = AddressType::PUBLIC_DEVICE_ADDRESS;
hci::Octet16 IRK_CENTRAL = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

Address ADDRESS_PERIPHERAL{{0x33, 0x58, 0x24, 0x76, 0x11, 0x89}};
AddressType ADDRESS_TYPE_PERIPHERAL = AddressType::RANDOM_DEVICE_ADDRESS;
Address IDENTITY_ADDRESS_PERIPHERAL{{0x21, 0x43, 0x65, 0x87, 0x09, 0x44}};
AddressType IDENTITY_ADDRESS_TYPE_PERIPHERAL = AddressType::PUBLIC_DEVICE_ADDRESS;
hci::Octet16 IRK_PERIPHERAL = {
    0x0f, 0x0e, 0x0d, 0x0c, 0x0b, 0x0a, 0x09, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01};

std::optional<PairingResultOrFailure> pairing_result_central;
std::optional<PairingResultOrFailure> pairing_result_peripheral;

void OnPairingFinishedCentral(PairingResultOrFailure r) {
  pairing_result_central = r;
  if (std::holds_alternative<PairingResult>(r)) {
    LOG_INFO(
        "pairing finished successfully with %s",
        ADDRESS_TO_LOGGABLE_CSTR(std::get<PairingResult>(r).connection_address));
  } else {
    LOG_INFO("pairing with ... failed: %s", std::get<PairingFailure>(r).message.c_str());
  }
}

void OnPairingFinishedPeripheral(PairingResultOrFailure r) {
  pairing_result_peripheral = r;
  if (std::holds_alternative<PairingResult>(r)) {
    LOG_INFO(
        "pairing finished successfully with %s",
        ADDRESS_TO_LOGGABLE_CSTR(std::get<PairingResult>(r).connection_address));
  } else {
    LOG_INFO("pairing with ... failed: %s", std::get<PairingFailure>(r).message.c_str());
  }
}

};  // namespace

// We obtain this mutex when we start initializing the handlers, and relese it when both handlers are initialized
std::mutex handlers_initialization_guard;

class PairingHandlerPairTest : public testing::Test {
  void dequeue_callback_central() {
    auto packet_bytes_view = l2cap_->GetQueueAUpEnd()->TryDequeue();
    if (!packet_bytes_view) LOG_ERROR("Received dequeue, but no data ready...");

    auto temp_cmd_view = CommandView::Create(*packet_bytes_view);
    if (!first_command_sent) {
      first_command = std::make_unique<CommandView>(CommandView::Create(temp_cmd_view));
      first_command_sent = true;
      return;
    }

    if (!pairing_handler_a) LOG_ALWAYS_FATAL("Peripheral handler not initlized yet!");

    pairing_handler_a->OnCommandView(CommandView::Create(temp_cmd_view));
  }

  void dequeue_callback_peripheral() {
    auto packet_bytes_view = l2cap_->GetQueueBUpEnd()->TryDequeue();
    if (!packet_bytes_view) LOG_ERROR("Received dequeue, but no data ready...");

    auto temp_cmd_view = CommandView::Create(*packet_bytes_view);
    if (!first_command_sent) {
      first_command = std::make_unique<CommandView>(CommandView::Create(temp_cmd_view));
      first_command_sent = true;
      return;
    }

    if (!pairing_handler_b) LOG_ALWAYS_FATAL("Central handler not initlized yet!");

    pairing_handler_b->OnCommandView(CommandView::Create(temp_cmd_view));
  }

 protected:
  void SetUp() {
    thread_ = new os::Thread("test_thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);

    l2cap_ = new common::testing::WiredPairOfL2capQueues(handler_);
    // central sends it's packet into l2cap->down_buffer_b_
    // peripheral sends it's packet into l2cap->down_buffer_a_
    l2cap_->GetQueueAUpEnd()->RegisterDequeue(
        handler_, common::Bind(&PairingHandlerPairTest::dequeue_callback_central, common::Unretained(this)));
    l2cap_->GetQueueBUpEnd()->RegisterDequeue(
        handler_, common::Bind(&PairingHandlerPairTest::dequeue_callback_peripheral, common::Unretained(this)));

    up_buffer_a_ = std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(l2cap_->GetQueueAUpEnd());
    up_buffer_b_ = std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(l2cap_->GetQueueBUpEnd());

    central_setup = {
        .my_role = hci::Role::CENTRAL,
        .my_connection_address = {ADDRESS_CENTRAL, ADDRESS_TYPE_CENTRAL},
        .my_identity_address = {IDENTITY_ADDRESS_CENTRAL, IDENTITY_ADDRESS_TYPE_CENTRAL},
        .my_identity_resolving_key = IRK_CENTRAL,

        .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                                  .oob_data_flag = OobDataFlag::NOT_PRESENT,
                                  .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                                  .maximum_encryption_key_size = 16,
                                  .initiator_key_distribution = KeyMaskId | KeyMaskSign,
                                  .responder_key_distribution = KeyMaskId | KeyMaskSign},

        .remotely_initiated = false,
        .connection_handle = CONN_HANDLE_CENTRAL,
        .remote_connection_address = {ADDRESS_PERIPHERAL, ADDRESS_TYPE_PERIPHERAL},
        .user_interface = &central_user_interface,
        .user_interface_handler = handler_,
        .le_security_interface = &central_le_security_mock,
        .proper_l2cap_interface = up_buffer_a_.get(),
        .l2cap_handler = handler_,
        .OnPairingFinished = OnPairingFinishedCentral,
    };

    peripheral_setup = {
        .my_role = hci::Role::PERIPHERAL,

        .my_connection_address = {ADDRESS_PERIPHERAL, ADDRESS_TYPE_PERIPHERAL},
        .my_identity_address = {IDENTITY_ADDRESS_PERIPHERAL, IDENTITY_ADDRESS_TYPE_PERIPHERAL},
        .my_identity_resolving_key = IRK_PERIPHERAL,

        .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                                  .oob_data_flag = OobDataFlag::NOT_PRESENT,
                                  .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                                  .maximum_encryption_key_size = 16,
                                  .initiator_key_distribution = KeyMaskId | KeyMaskSign,
                                  .responder_key_distribution = KeyMaskId | KeyMaskSign},
        .remotely_initiated = true,
        .connection_handle = CONN_HANDLE_PERIPHERAL,
        .remote_connection_address = {ADDRESS_CENTRAL, ADDRESS_TYPE_CENTRAL},
        .user_interface = &peripheral_user_interface,
        .user_interface_handler = handler_,
        .le_security_interface = &peripheral_le_security_mock,
        .proper_l2cap_interface = up_buffer_b_.get(),
        .l2cap_handler = handler_,
        .OnPairingFinished = OnPairingFinishedPeripheral,
    };

    RecordSuccessfulEncryptionComplete();
  }

  void TearDown() {
    ::testing::Mock::VerifyAndClearExpectations(&peripheral_user_interface);
    ::testing::Mock::VerifyAndClearExpectations(&central_user_interface);
    ::testing::Mock::VerifyAndClearExpectations(&peripheral_le_security_mock);
    ::testing::Mock::VerifyAndClearExpectations(&central_le_security_mock);

    pairing_handler_a.reset();
    pairing_handler_b.reset();
    pairing_result_central.reset();
    pairing_result_peripheral.reset();

    first_command_sent = false;
    first_command.reset();

    l2cap_->GetQueueAUpEnd()->UnregisterDequeue();
    l2cap_->GetQueueBUpEnd()->UnregisterDequeue();

    delete l2cap_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void RecordPairingPromptHandling(UIMock& ui_mock, std::unique_ptr<PairingHandlerLe>* handler) {
    EXPECT_CALL(ui_mock, DisplayPairingPrompt(_, _)).Times(1).WillOnce(InvokeWithoutArgs([handler]() {
      LOG_INFO("UI mock received pairing prompt");

      {
        // By grabbing the lock, we ensure initialization of both pairing handlers is finished.
        std::lock_guard<std::mutex> lock(handlers_initialization_guard);
      }

      if (!(*handler)) LOG_ALWAYS_FATAL("handler not initalized yet!");
      // Simulate user accepting the pairing in UI
      (*handler)->OnUiAction(PairingEvent::PAIRING_ACCEPTED, 0x01 /* Non-zero value means success */);
    }));
  }

  void RecordSuccessfulEncryptionComplete() {
    // For now, all tests are succeeding to go through Encryption. Record that in the setup.
    //  Once we test failure cases, move this to each test
    EXPECT_CALL(
        central_le_security_mock,
        EnqueueCommand(_, Matcher<common::ContextualOnceCallback<void(CommandStatusView)>>(_)))
        .Times(1)
        .WillOnce([](std::unique_ptr<LeSecurityCommandBuilder> command,
                     common::ContextualOnceCallback<void(CommandStatusView)> on_status) {
          // TODO: on_status.Run();

          pairing_handler_a->OnHciEvent(EventBuilderToView(
              EncryptionChangeBuilder::Create(ErrorCode::SUCCESS, CONN_HANDLE_CENTRAL, EncryptionEnabled::ON)));

          pairing_handler_b->OnHciEvent(EventBuilderToView(
              hci::LeLongTermKeyRequestBuilder::Create(CONN_HANDLE_PERIPHERAL, {0, 0, 0, 0, 0, 0, 0, 0}, 0)));

          pairing_handler_b->OnHciEvent(EventBuilderToView(
              EncryptionChangeBuilder::Create(ErrorCode::SUCCESS, CONN_HANDLE_PERIPHERAL, EncryptionEnabled::ON)));
        });
  }

 public:
  std::unique_ptr<bluetooth::security::CommandView> WaitFirstL2capCommand() {
    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }

    return std::move(first_command);
  }

  InitialInformations central_setup;
  InitialInformations peripheral_setup;
  UIMock central_user_interface;
  UIMock peripheral_user_interface;
  LeSecurityInterfaceMock central_le_security_mock;
  LeSecurityInterfaceMock peripheral_le_security_mock;

  uint16_t first_command_sent = false;
  std::unique_ptr<bluetooth::security::CommandView> first_command;

  os::Thread* thread_;
  os::Handler* handler_;
  common::testing::WiredPairOfL2capQueues* l2cap_;

  std::unique_ptr<os::EnqueueBuffer<packet::BasePacketBuilder>> up_buffer_a_;
  std::unique_ptr<os::EnqueueBuffer<packet::BasePacketBuilder>> up_buffer_b_;
};

/* This test verifies that Just Works pairing flow works.
 * Both simulated devices specify capabilities as NO_INPUT_NO_OUTPUT, and secure connecitons support */
TEST_F(PairingHandlerPairTest, test_secure_connections_just_works) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::NO_INPUT_NO_OUTPUT;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::NO_INPUT_NO_OUTPUT;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;

  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);

    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);

    auto first_pkt = WaitFirstL2capCommand();
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_pkt);

    EXPECT_CALL(peripheral_user_interface, DisplayPairingPrompt(_, _)).Times(1).WillOnce(InvokeWithoutArgs([] {
      LOG_INFO("UI mock received pairing prompt");

      {
        // By grabbing the lock, we ensure initialization of both pairing handlers is finished.
        std::lock_guard<std::mutex> lock(handlers_initialization_guard);
      }

      if (!pairing_handler_b) LOG_ALWAYS_FATAL("handler not initalized yet!");

      // Simulate user accepting the pairing in UI
      pairing_handler_b->OnUiAction(PairingEvent::PAIRING_ACCEPTED, 0x01 /* Non-zero value means success */);
    }));

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }

  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));

  auto central_result = std::get<PairingResult>(pairing_result_central.value());
  ASSERT_EQ(central_result.distributed_keys.remote_identity_address->GetAddress(), IDENTITY_ADDRESS_PERIPHERAL);
  ASSERT_EQ(
      central_result.distributed_keys.remote_identity_address->GetAddressType(), IDENTITY_ADDRESS_TYPE_PERIPHERAL);
  ASSERT_EQ(*central_result.distributed_keys.remote_irk, IRK_PERIPHERAL);

  auto peripheral_result = std::get<PairingResult>(pairing_result_peripheral.value());
  ASSERT_EQ(peripheral_result.distributed_keys.remote_identity_address->GetAddress(), IDENTITY_ADDRESS_CENTRAL);
  ASSERT_EQ(
      peripheral_result.distributed_keys.remote_identity_address->GetAddressType(), IDENTITY_ADDRESS_TYPE_CENTRAL);
  ASSERT_EQ(*peripheral_result.distributed_keys.remote_irk, IRK_CENTRAL);
}

TEST_F(PairingHandlerPairTest, test_secure_connections_just_works_peripheral_initiated) {
  central_setup = {
      .my_role = hci::Role::CENTRAL,
      .my_connection_address = {ADDRESS_CENTRAL, ADDRESS_TYPE_CENTRAL},
      .my_identity_address = {IDENTITY_ADDRESS_CENTRAL, IDENTITY_ADDRESS_TYPE_CENTRAL},
      .my_identity_resolving_key = IRK_CENTRAL,
      .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                                .oob_data_flag = OobDataFlag::NOT_PRESENT,
                                .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                                .maximum_encryption_key_size = 16,
                                .initiator_key_distribution = KeyMaskId | KeyMaskSign,
                                .responder_key_distribution = KeyMaskId | KeyMaskSign},
      .remotely_initiated = true,
      .connection_handle = CONN_HANDLE_CENTRAL,
      .remote_connection_address = {ADDRESS_PERIPHERAL, ADDRESS_TYPE_PERIPHERAL},
      .user_interface = &central_user_interface,
      .user_interface_handler = handler_,
      .le_security_interface = &central_le_security_mock,
      .proper_l2cap_interface = up_buffer_a_.get(),
      .l2cap_handler = handler_,
      .OnPairingFinished = OnPairingFinishedCentral,
  };

  peripheral_setup = {
      .my_role = hci::Role::PERIPHERAL,
      .my_connection_address = {ADDRESS_PERIPHERAL, ADDRESS_TYPE_PERIPHERAL},
      .my_identity_address = {IDENTITY_ADDRESS_PERIPHERAL, IDENTITY_ADDRESS_TYPE_PERIPHERAL},
      .my_identity_resolving_key = IRK_PERIPHERAL,
      .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                                .oob_data_flag = OobDataFlag::NOT_PRESENT,
                                .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                                .maximum_encryption_key_size = 16,
                                .initiator_key_distribution = KeyMaskId | KeyMaskSign,
                                .responder_key_distribution = KeyMaskId | KeyMaskSign},
      .remotely_initiated = false,
      .connection_handle = CONN_HANDLE_PERIPHERAL,
      .remote_connection_address = {ADDRESS_CENTRAL, ADDRESS_TYPE_CENTRAL},
      .user_interface = &peripheral_user_interface,
      .user_interface_handler = handler_,
      .le_security_interface = &peripheral_le_security_mock,
      .proper_l2cap_interface = up_buffer_b_.get(),
      .l2cap_handler = handler_,
      .OnPairingFinished = OnPairingFinishedPeripheral,
  };

  std::unique_ptr<bluetooth::security::CommandView> first_pkt;
  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);

    first_pkt = WaitFirstL2capCommand();

    EXPECT_CALL(central_user_interface, DisplayPairingPrompt(_, _))
        .Times(1)
        .WillOnce(InvokeWithoutArgs([&first_pkt, this] {
          LOG_INFO("UI mock received pairing prompt");

          {
            // By grabbing the lock, we ensure initialization of both pairing handlers is finished.
            std::lock_guard<std::mutex> lock(handlers_initialization_guard);
          }
          if (!pairing_handler_a) LOG_ALWAYS_FATAL("handler not initalized yet!");
          // Simulate user accepting the pairing in UI
          pairing_handler_a->OnUiAction(PairingEvent::PAIRING_ACCEPTED, 0x01 /* Non-zero value means success */);

          // Send the first packet from the peripheral to central
          auto view_to_packet = std::make_unique<packet::RawBuilder>();
          view_to_packet->AddOctets(std::vector(first_pkt->begin(), first_pkt->end()));
          up_buffer_b_->Enqueue(std::move(view_to_packet), handler_);
        }));
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);
  }

  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_secure_connections_numeric_comparison) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::DISPLAY_YES_NO;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc;

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::DISPLAY_YES_NO;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc;

  ConfirmationData data_peripheral;
  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    // Initiator must be initialized after the responder.
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);

    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    EXPECT_CALL(peripheral_user_interface, DisplayConfirmValue(_)).WillOnce(SaveArg<0>(&data_peripheral));
    EXPECT_CALL(central_user_interface, DisplayConfirmValue(_)).WillOnce(Invoke([&](ConfirmationData data) {
      EXPECT_EQ(data_peripheral.GetNumericValue(), data.GetNumericValue());
      if (data_peripheral.GetNumericValue() == data.GetNumericValue()) {
        pairing_handler_a->OnUiAction(PairingEvent::CONFIRM_YESNO, 0x01);
        pairing_handler_b->OnUiAction(PairingEvent::CONFIRM_YESNO, 0x01);
      }
    }));

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_secure_connections_passkey_entry) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::KEYBOARD_ONLY;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc;

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::DISPLAY_ONLY;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc;

  // In this test either central or peripheral display the UI prompt first. This variable makes sure both prompts are
  // displayed before passkey is confirmed. Since both UI handlers are same thread, it's safe.
  int ui_prompts_count = 0;
  uint32_t passkey_ = std::numeric_limits<uint32_t>::max();
  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);

    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    EXPECT_CALL(peripheral_user_interface, DisplayPasskey(_)).WillOnce(Invoke([&](ConfirmationData data) {
      passkey_ = data.GetNumericValue();
      ui_prompts_count++;
      if (ui_prompts_count == 2) {
        pairing_handler_a->OnUiAction(PairingEvent::PASSKEY, passkey_);
      }
    }));

    EXPECT_CALL(central_user_interface, DisplayEnterPasskeyDialog(_)).WillOnce(Invoke([&](ConfirmationData data) {
      ui_prompts_count++;
      if (ui_prompts_count == 2) {
        pairing_handler_a->OnUiAction(PairingEvent::PASSKEY, passkey_);
      }
    }));

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  // Initiator must be initialized after the responder.
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_secure_connections_out_of_band) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::KEYBOARD_ONLY;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::DISPLAY_ONLY;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,

  central_setup.my_oob_data = std::make_optional<MyOobData>(PairingHandlerLe::GenerateOobData());
  peripheral_setup.remote_oob_data =
      std::make_optional<InitialInformations::out_of_band_data>(InitialInformations::out_of_band_data{
          .le_sc_c = central_setup.my_oob_data->c,
          .le_sc_r = central_setup.my_oob_data->r,
      });

  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);
    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_secure_connections_out_of_band_two_way) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::KEYBOARD_ONLY;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::DISPLAY_ONLY;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,

  central_setup.my_oob_data = std::make_optional<MyOobData>(PairingHandlerLe::GenerateOobData());
  peripheral_setup.remote_oob_data =
      std::make_optional<InitialInformations::out_of_band_data>(InitialInformations::out_of_band_data{
          .le_sc_c = central_setup.my_oob_data->c,
          .le_sc_r = central_setup.my_oob_data->r,
      });

  peripheral_setup.my_oob_data = std::make_optional<MyOobData>(PairingHandlerLe::GenerateOobData());
  central_setup.remote_oob_data =
      std::make_optional<InitialInformations::out_of_band_data>(InitialInformations::out_of_band_data{
          .le_sc_c = peripheral_setup.my_oob_data->c,
          .le_sc_r = peripheral_setup.my_oob_data->r,
      });

  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);
    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_legacy_just_works) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::NO_INPUT_NO_OUTPUT;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm,

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::NO_INPUT_NO_OUTPUT;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm;

  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);
    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

TEST_F(PairingHandlerPairTest, test_legacy_passkey_entry) {
  central_setup.myPairingCapabilities.io_capability = IoCapability::KEYBOARD_DISPLAY;
  central_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  central_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm,

  peripheral_setup.myPairingCapabilities.io_capability = IoCapability::KEYBOARD_ONLY;
  peripheral_setup.myPairingCapabilities.oob_data_flag = OobDataFlag::NOT_PRESENT;
  peripheral_setup.myPairingCapabilities.auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm;

  {
    std::unique_lock<std::mutex> lock(handlers_initialization_guard);
    pairing_handler_a = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, central_setup);
    while (!first_command_sent) {
      std::this_thread::sleep_for(1ms);
      LOG_INFO("waiting for first command...");
    }
    peripheral_setup.pairing_request = PairingRequestView::Create(*first_command);

    RecordPairingPromptHandling(peripheral_user_interface, &pairing_handler_b);

    EXPECT_CALL(peripheral_user_interface, DisplayEnterPasskeyDialog(_));
    EXPECT_CALL(central_user_interface, DisplayConfirmValue(_)).WillOnce(Invoke([&](ConfirmationData data) {
      LOG_INFO("Passkey prompt displayed entering passkey: %08x", data.GetNumericValue());
      std::this_thread::sleep_for(1ms);

      // TODO: handle case where prompts are displayed in different order in the test!
      pairing_handler_b->OnUiAction(PairingEvent::PASSKEY, data.GetNumericValue());
    }));

    pairing_handler_b = std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, peripheral_setup);
  }
  pairing_handler_a->WaitUntilPairingFinished();
  pairing_handler_b->WaitUntilPairingFinished();

  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_central.value()));
  EXPECT_TRUE(std::holds_alternative<PairingResult>(pairing_result_peripheral.value()));
}

}  // namespace security
}  // namespace bluetooth
