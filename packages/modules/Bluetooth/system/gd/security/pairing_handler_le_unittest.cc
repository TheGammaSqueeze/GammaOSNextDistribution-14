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

#include "security/pairing_handler_le.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <memory>

#include "os/rand.h"
#include "security/test/mocks.h"

using ::testing::_;
using ::testing::Eq;
using ::testing::Field;
using ::testing::VariantWith;

using bluetooth::os::GenerateRandom;
using bluetooth::security::CommandView;

namespace bluetooth {
namespace security {

namespace {

CommandView BuilderToView(std::unique_ptr<BasePacketBuilder> builder) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter it(*packet_bytes);
  builder->Serialize(it);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto temp_cmd_view = CommandView::Create(packet_bytes_view);
  return CommandView::Create(temp_cmd_view);
}

class PairingResultHandlerMock {
 public:
  MOCK_CONST_METHOD1(OnPairingFinished, void(PairingResultOrFailure));
};

std::unique_ptr<PairingResultHandlerMock> pairingResult;
LeSecurityInterfaceMock leSecurityMock;
UIMock uiMock;

void OnPairingFinished(PairingResultOrFailure r) {
  if (std::holds_alternative<PairingResult>(r)) {
    LOG(INFO) << "pairing with " << std::get<PairingResult>(r).connection_address << " finished successfully!";
  } else {
    LOG(INFO) << "pairing with ... failed!";
  }
  pairingResult->OnPairingFinished(r);
}
}  // namespace

class PairingHandlerUnitTest : public testing::Test {
 protected:
  void SetUp() {
    thread_ = new os::Thread("test_thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);

    bidi_queue_ =
        std::make_unique<common::BidiQueue<packet::PacketView<packet::kLittleEndian>, packet::BasePacketBuilder>>(10);
    up_buffer_ = std::make_unique<os::EnqueueBuffer<packet::BasePacketBuilder>>(bidi_queue_->GetUpEnd());

    bidi_queue_->GetDownEnd()->RegisterDequeue(
        handler_, common::Bind(&PairingHandlerUnitTest::L2CAP_SendSmp, common::Unretained(this)));

    pairingResult.reset(new PairingResultHandlerMock);
  }
  void TearDown() {
    pairingResult.reset();
    bidi_queue_->GetDownEnd()->UnregisterDequeue();
    handler_->Clear();
    delete handler_;
    delete thread_;

    ::testing::Mock::VerifyAndClearExpectations(&leSecurityMock);
    ::testing::Mock::VerifyAndClearExpectations(&uiMock);
  }

  void L2CAP_SendSmp() {
    std::unique_ptr<packet::BasePacketBuilder> builder = bidi_queue_->GetDownEnd()->TryDequeue();

    outgoing_l2cap_packet_ = BuilderToView(std::move(builder));
    outgoing_l2cap_packet_->IsValid();

    outgoing_l2cap_blocker_.notify_one();
  }

  std::optional<bluetooth::security::CommandView> WaitForOutgoingL2capPacket() {
    std::mutex mutex;
    std::unique_lock<std::mutex> lock(mutex);

    // It is possible that we lost wakeup from condition_variable, check if data is already waiting to be processed
    if (outgoing_l2cap_packet_ != std::nullopt) {
      std::optional<bluetooth::security::CommandView> tmp = std::nullopt;
      outgoing_l2cap_packet_.swap(tmp);
      return tmp;
    }

    // Data not ready yet, wait for it.
    if (outgoing_l2cap_blocker_.wait_for(lock, std::chrono::seconds(5)) == std::cv_status::timeout) {
      return std::nullopt;
    }

    std::optional<bluetooth::security::CommandView> tmp = std::nullopt;
    outgoing_l2cap_packet_.swap(tmp);
    return tmp;
  }

 public:
  os::Thread* thread_;
  os::Handler* handler_;
  std::unique_ptr<common::BidiQueue<packet::PacketView<packet::kLittleEndian>, packet::BasePacketBuilder>> bidi_queue_;
  std::unique_ptr<os::EnqueueBuffer<packet::BasePacketBuilder>> up_buffer_;
  std::condition_variable outgoing_l2cap_blocker_;
  std::optional<bluetooth::security::CommandView> outgoing_l2cap_packet_ = std::nullopt;
};

InitialInformations initial_informations{
    .my_role = hci::Role::CENTRAL,
    .my_connection_address = {{}, hci::AddressType::PUBLIC_DEVICE_ADDRESS},
    .my_identity_address = {{}, hci::AddressType::PUBLIC_DEVICE_ADDRESS},
    .my_identity_resolving_key =
        {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f},

    .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                              .oob_data_flag = OobDataFlag::NOT_PRESENT,
                              .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                              .maximum_encryption_key_size = 16,
                              .initiator_key_distribution = 0x03,
                              .responder_key_distribution = 0x03},

    .remotely_initiated = false,
    .remote_connection_address = {{}, hci::AddressType::RANDOM_DEVICE_ADDRESS},
    .user_interface = &uiMock,
    .le_security_interface = &leSecurityMock,
    .OnPairingFinished = OnPairingFinished,
};

TEST_F(PairingHandlerUnitTest, test_phase_1_failure) {
  initial_informations.proper_l2cap_interface = up_buffer_.get();
  initial_informations.l2cap_handler = handler_;
  initial_informations.user_interface_handler = handler_;

  std::unique_ptr<PairingHandlerLe> pairing_handler =
      std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, initial_informations);

  std::optional<bluetooth::security::CommandView> pairing_request = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(pairing_request.has_value());
  EXPECT_EQ(pairing_request->GetCode(), Code::PAIRING_REQUEST);

  EXPECT_CALL(*pairingResult, OnPairingFinished(VariantWith<PairingFailure>(_))).Times(1);

  // SMP will waith for Pairing Response, once bad packet is received, it should stop the Pairing
  CommandView bad_pairing_response = BuilderToView(PairingRandomBuilder::Create({}));
  bad_pairing_response.IsValid();
  pairing_handler->OnCommandView(bad_pairing_response);

  std::optional<bluetooth::security::CommandView> pairing_failure = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(pairing_failure.has_value());
  EXPECT_EQ(pairing_failure->GetCode(), Code::PAIRING_FAILED);
}

TEST_F(PairingHandlerUnitTest, test_secure_connections_just_works) {
  initial_informations.proper_l2cap_interface = up_buffer_.get();
  initial_informations.l2cap_handler = handler_;
  initial_informations.user_interface_handler = handler_;

  // we keep the pairing_handler as unique_ptr to better mimick how it's used
  // in the real world
  std::unique_ptr<PairingHandlerLe> pairing_handler =
      std::make_unique<PairingHandlerLe>(PairingHandlerLe::PHASE1, initial_informations);

  std::optional<bluetooth::security::CommandView> pairing_request_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(pairing_request_pkt.has_value());
  EXPECT_EQ(pairing_request_pkt->GetCode(), Code::PAIRING_REQUEST);
  CommandView pairing_request = pairing_request_pkt.value();

  auto pairing_response = BuilderToView(
      PairingResponseBuilder::Create(IoCapability::KEYBOARD_DISPLAY, OobDataFlag::NOT_PRESENT,
                                     AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc, 16, 0x03, 0x03));
  pairing_handler->OnCommandView(pairing_response);
  // Phase 1 finished.

  // pairing public key
  std::optional<bluetooth::security::CommandView> public_key_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(public_key_pkt.has_value());
  EXPECT_EQ(Code::PAIRING_PUBLIC_KEY, public_key_pkt->GetCode());
  EcdhPublicKey my_public_key;
  auto ppkv = PairingPublicKeyView::Create(public_key_pkt.value());
  ppkv.IsValid();
  my_public_key.x = ppkv.GetPublicKeyX();
  my_public_key.y = ppkv.GetPublicKeyY();

  const auto [private_key, public_key] = GenerateECDHKeyPair();

  pairing_handler->OnCommandView(BuilderToView(PairingPublicKeyBuilder::Create(public_key.x, public_key.y)));
  // DHKey exchange finished
  std::array<uint8_t, 32> dhkey = ComputeDHKey(private_key, my_public_key);

  // Phasae 2 Stage 1 start
  Octet16 ra, rb;
  ra = rb = {0};

  Octet16 Nb = GenerateRandom<16>();

  // Compute confirm
  Octet16 Cb = crypto_toolbox::f4((uint8_t*)public_key.x.data(), (uint8_t*)my_public_key.x.data(), Nb, 0);

  pairing_handler->OnCommandView(BuilderToView(PairingConfirmBuilder::Create(Cb)));

  // random
  std::optional<bluetooth::security::CommandView> random_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(random_pkt.has_value());
  EXPECT_EQ(Code::PAIRING_RANDOM, random_pkt->GetCode());
  auto prv = PairingRandomView::Create(random_pkt.value());
  prv.IsValid();
  Octet16 Na = prv.GetRandomValue();

  pairing_handler->OnCommandView(BuilderToView(PairingRandomBuilder::Create(Nb)));

  // Start of authentication stage 2
  uint8_t a[7];
  uint8_t b[7];
  memcpy(b, initial_informations.remote_connection_address.GetAddress().data(), hci::Address::kLength);
  b[6] = (uint8_t)initial_informations.remote_connection_address.GetAddressType();
  memcpy(a, initial_informations.my_connection_address.GetAddress().data(), hci::Address::kLength);
  a[6] = (uint8_t)initial_informations.my_connection_address.GetAddressType();

  Octet16 ltk, mac_key;
  crypto_toolbox::f5(dhkey.data(), Na, Nb, a, b, &mac_key, &ltk);

  PairingRequestView preqv = PairingRequestView::Create(pairing_request);
  PairingResponseView prspv = PairingResponseView::Create(pairing_response);

  preqv.IsValid();
  prspv.IsValid();
  std::array<uint8_t, 3> iocapA{static_cast<uint8_t>(preqv.GetIoCapability()),
                                static_cast<uint8_t>(preqv.GetOobDataFlag()), preqv.GetAuthReq()};
  std::array<uint8_t, 3> iocapB{static_cast<uint8_t>(prspv.GetIoCapability()),
                                static_cast<uint8_t>(prspv.GetOobDataFlag()), prspv.GetAuthReq()};

  Octet16 Ea = crypto_toolbox::f6(mac_key, Na, Nb, rb, iocapA.data(), a, b);
  Octet16 Eb = crypto_toolbox::f6(mac_key, Nb, Na, ra, iocapB.data(), b, a);

  std::optional<bluetooth::security::CommandView> dh_key_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(dh_key_pkt.has_value());
  EXPECT_EQ(Code::PAIRING_DH_KEY_CHECK, dh_key_pkt->GetCode());
  auto pdhkcv = PairingDhKeyCheckView::Create(dh_key_pkt.value());
  pdhkcv.IsValid();
  EXPECT_EQ(pdhkcv.GetDhKeyCheck(), Ea);

  pairing_handler->OnCommandView(BuilderToView(PairingDhKeyCheckBuilder::Create(Eb)));

  // Phase 2 finished
  // We don't care for the rest of the flow, let it die.
}

InitialInformations initial_informations_trsi{
    .my_role = hci::Role::CENTRAL,
    .my_connection_address = hci::AddressWithType(),
    .my_identity_address = {{}, hci::AddressType::PUBLIC_DEVICE_ADDRESS},
    .my_identity_resolving_key =
        {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f},

    .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                              .oob_data_flag = OobDataFlag::NOT_PRESENT,
                              .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                              .maximum_encryption_key_size = 16,
                              .initiator_key_distribution = 0x03,
                              .responder_key_distribution = 0x03},

    .remotely_initiated = true,
    .remote_connection_address = hci::AddressWithType(),
    .user_interface = &uiMock,
    .le_security_interface = &leSecurityMock,
    .OnPairingFinished = OnPairingFinished,
};

/* This test verifies that when remote peripheral device sends security request , and user
 * does accept the prompt, we do send pairing request */
TEST_F(PairingHandlerUnitTest, test_remote_peripheral_initiating) {
  initial_informations_trsi.proper_l2cap_interface = up_buffer_.get();
  initial_informations_trsi.l2cap_handler = handler_;
  initial_informations_trsi.user_interface_handler = handler_;

  std::unique_ptr<PairingHandlerLe> pairing_handler =
      std::make_unique<PairingHandlerLe>(PairingHandlerLe::ACCEPT_PROMPT, initial_informations_trsi);

  // Simulate user accepting the pairing in UI
  pairing_handler->OnUiAction(PairingEvent::PAIRING_ACCEPTED, 0x01 /* Non-zero value means success */);

  std::optional<bluetooth::security::CommandView> pairing_request_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(pairing_request_pkt.has_value());
  EXPECT_EQ(Code::PAIRING_REQUEST, pairing_request_pkt->GetCode());

  // We don't care for the rest of the flow, let it die.
  pairing_handler.reset();
}

InitialInformations initial_informations_trmi{
    .my_role = hci::Role::PERIPHERAL,
    .my_connection_address = hci::AddressWithType(),
    .my_identity_address = {{}, hci::AddressType::PUBLIC_DEVICE_ADDRESS},
    .my_identity_resolving_key =
        {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f},

    .myPairingCapabilities = {.io_capability = IoCapability::NO_INPUT_NO_OUTPUT,
                              .oob_data_flag = OobDataFlag::NOT_PRESENT,
                              .auth_req = AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
                              .maximum_encryption_key_size = 16,
                              .initiator_key_distribution = 0x03,
                              .responder_key_distribution = 0x03},

    .remotely_initiated = true,
    .remote_connection_address = hci::AddressWithType(),
    .pairing_request = PairingRequestView::Create(BuilderToView(PairingRequestBuilder::Create(
        IoCapability::NO_INPUT_NO_OUTPUT,
        OobDataFlag::NOT_PRESENT,
        AuthReqMaskBondingFlag | AuthReqMaskMitm | AuthReqMaskSc,
        16,
        0x03,
        0x03))),
    .user_interface = &uiMock,
    .le_security_interface = &leSecurityMock,

    .OnPairingFinished = OnPairingFinished,
};

/* This test verifies that when remote device sends pairing request, and user does accept the prompt, we do send proper
 * reply back */
TEST_F(PairingHandlerUnitTest, test_remote_central_initiating) {
  initial_informations_trmi.proper_l2cap_interface = up_buffer_.get();
  initial_informations_trmi.l2cap_handler = handler_;
  initial_informations_trmi.user_interface_handler = handler_;

  std::unique_ptr<PairingHandlerLe> pairing_handler =
      std::make_unique<PairingHandlerLe>(PairingHandlerLe::ACCEPT_PROMPT, initial_informations_trmi);

  // Simulate user accepting the pairing in UI
  pairing_handler->OnUiAction(PairingEvent::PAIRING_ACCEPTED, 0x01 /* Non-zero value means success */);

  std::optional<bluetooth::security::CommandView> pairing_response_pkt = WaitForOutgoingL2capPacket();
  EXPECT_TRUE(pairing_response_pkt.has_value());
  EXPECT_EQ(Code::PAIRING_RESPONSE, pairing_response_pkt->GetCode());
  // Phase 1 finished.

  // We don't care for the rest of the flow, it's handled in in other tests. let it die.
  pairing_handler.reset();
}

}  // namespace security
}  // namespace bluetooth
