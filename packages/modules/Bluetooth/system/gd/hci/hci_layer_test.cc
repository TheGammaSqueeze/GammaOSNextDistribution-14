/*
 * Copyright 2019 The Android Open Source Project
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

#include <list>
#include <memory>

#include "hal/hci_hal_fake.h"
#include "hci/hci_packets.h"
#include "module.h"
#include "os/log.h"
#include "os/thread.h"
#include "packet/bit_inserter.h"
#include "packet/raw_builder.h"

using bluetooth::os::Thread;
using bluetooth::packet::BitInserter;
using bluetooth::packet::RawBuilder;
using std::vector;

namespace {
vector<uint8_t> information_request = {
    0xfe,
    0x2e,
    0x0a,
    0x00,
    0x06,
    0x00,
    0x01,
    0x00,
    0x0a,
    0x02,
    0x02,
    0x00,
    0x02,
    0x00,
};
// 0x00, 0x01, 0x02, 0x03, ...
vector<uint8_t> counting_bytes;
// 0xFF, 0xFE, 0xFD, 0xFC, ...
vector<uint8_t> counting_down_bytes;
const size_t count_size = 0x8;

}  // namespace

namespace bluetooth {
namespace hci {
namespace {

constexpr std::chrono::milliseconds kTimeout = HciLayer::kHciTimeoutMs / 2;

class DependsOnHci : public Module {
 public:
  DependsOnHci() : Module() {}

  void SendHciCommandExpectingStatus(std::unique_ptr<CommandBuilder> command) {
    hci_->EnqueueCommand(
        std::move(command), GetHandler()->BindOnceOn(this, &DependsOnHci::handle_event<CommandStatusView>));
  }

  void SendHciCommandExpectingComplete(std::unique_ptr<CommandBuilder> command) {
    hci_->EnqueueCommand(
        std::move(command), GetHandler()->BindOnceOn(this, &DependsOnHci::handle_event<CommandCompleteView>));
  }

  void SendSecurityCommandExpectingComplete(std::unique_ptr<SecurityCommandBuilder> command) {
    if (security_interface_ == nullptr) {
      security_interface_ =
          hci_->GetSecurityInterface(GetHandler()->BindOn(this, &DependsOnHci::handle_event<EventView>));
    }
    hci_->EnqueueCommand(
        std::move(command), GetHandler()->BindOnceOn(this, &DependsOnHci::handle_event<CommandCompleteView>));
  }

  void SendLeSecurityCommandExpectingComplete(std::unique_ptr<LeSecurityCommandBuilder> command) {
    if (le_security_interface_ == nullptr) {
      le_security_interface_ =
          hci_->GetLeSecurityInterface(GetHandler()->BindOn(this, &DependsOnHci::handle_event<LeMetaEventView>));
    }
    hci_->EnqueueCommand(
        std::move(command), GetHandler()->BindOnceOn(this, &DependsOnHci::handle_event<CommandCompleteView>));
  }

  void SendAclData(std::unique_ptr<AclBuilder> acl) {
    outgoing_acl_.push(std::move(acl));
    auto queue_end = hci_->GetAclQueueEnd();
    queue_end->RegisterEnqueue(GetHandler(), common::Bind(&DependsOnHci::handle_enqueue, common::Unretained(this)));
  }

  void SendIsoData(std::unique_ptr<IsoBuilder> iso) {
    outgoing_iso_.push(std::move(iso));
    auto queue_end = hci_->GetIsoQueueEnd();
    queue_end->RegisterEnqueue(GetHandler(), common::Bind(&DependsOnHci::handle_enqueue_iso, common::Unretained(this)));
  }

  std::optional<EventView> GetReceivedEvent(std::chrono::milliseconds timeout = kTimeout) {
    if (!incoming_events_.wait_to_take(timeout)) {
      return {};
    }
    auto event = EventView::Create(incoming_events_.take());
    ASSERT(event.IsValid());
    return event;
  }

  std::optional<AclView> GetReceivedAcl(
      std::chrono::milliseconds timeout = std::chrono::seconds(1)) {
    if (!incoming_acl_.wait_to_take(timeout)) {
      return {};
    }
    auto acl = AclView::Create(incoming_acl_.take());
    ASSERT(acl.IsValid());
    return acl;
  }

  std::optional<IsoView> GetReceivedIso(
      std::chrono::milliseconds timeout = std::chrono::seconds(1)) {
    if (!incoming_iso_.wait_to_take(timeout)) {
      return {};
    }
    auto iso = IsoView::Create(incoming_iso_.take());
    ASSERT(iso.IsValid());
    return iso;
  }

  void Start() {
    hci_ = GetDependency<HciLayer>();
    hci_->RegisterEventHandler(
        EventCode::CONNECTION_COMPLETE, GetHandler()->BindOn(this, &DependsOnHci::handle_event<EventView>));
    hci_->RegisterLeEventHandler(
        SubeventCode::CONNECTION_COMPLETE, GetHandler()->BindOn(this, &DependsOnHci::handle_event<LeMetaEventView>));
    hci_->GetAclQueueEnd()->RegisterDequeue(
        GetHandler(), common::Bind(&DependsOnHci::handle_acl, common::Unretained(this)));
    hci_->GetIsoQueueEnd()->RegisterDequeue(
        GetHandler(), common::Bind(&DependsOnHci::handle_iso, common::Unretained(this)));
  }

  void Stop() {
    hci_->GetAclQueueEnd()->UnregisterDequeue();
    hci_->GetIsoQueueEnd()->UnregisterDequeue();
  }

  void ListDependencies(ModuleList* list) const {
    list->add<HciLayer>();
  }

  std::string ToString() const override {
    return std::string("DependsOnHci");
  }

  static const ModuleFactory Factory;

 private:
  HciLayer* hci_ = nullptr;
  const SecurityInterface* security_interface_;
  const LeSecurityInterface* le_security_interface_;
  common::BlockingQueue<EventView> incoming_events_;
  common::BlockingQueue<AclView> incoming_acl_;
  common::BlockingQueue<IsoView> incoming_iso_;

  void handle_acl() {
    auto acl_ptr = hci_->GetAclQueueEnd()->TryDequeue();
    incoming_acl_.push(*acl_ptr);
  }

  template <typename T>
  void handle_event(T event) {
    incoming_events_.push(event);
  }

  void handle_iso() {
    auto iso_ptr = hci_->GetIsoQueueEnd()->TryDequeue();
    incoming_iso_.push(*iso_ptr);
  }

  std::queue<std::unique_ptr<AclBuilder>> outgoing_acl_;

  std::unique_ptr<AclBuilder> handle_enqueue() {
    hci_->GetAclQueueEnd()->UnregisterEnqueue();
    auto acl = std::move(outgoing_acl_.front());
    outgoing_acl_.pop();
    return acl;
  }

  std::queue<std::unique_ptr<IsoBuilder>> outgoing_iso_;

  std::unique_ptr<IsoBuilder> handle_enqueue_iso() {
    hci_->GetIsoQueueEnd()->UnregisterEnqueue();
    auto iso = std::move(outgoing_iso_.front());
    outgoing_iso_.pop();
    return iso;
  }
};

const ModuleFactory DependsOnHci::Factory = ModuleFactory([]() { return new DependsOnHci(); });

class HciTest : public ::testing::Test {
 public:
  void SetUp() override {
    counting_bytes.reserve(count_size);
    counting_down_bytes.reserve(count_size);
    for (size_t i = 0; i < count_size; i++) {
      counting_bytes.push_back(i);
      counting_down_bytes.push_back(~i);
    }
    hal = new hal::TestHciHal();

    fake_registry_.InjectTestModule(&hal::HciHal::Factory, hal);
    fake_registry_.Start<DependsOnHci>(&fake_registry_.GetTestThread());
    hci = static_cast<HciLayer*>(fake_registry_.GetModuleUnderTest(&HciLayer::Factory));
    upper = static_cast<DependsOnHci*>(fake_registry_.GetModuleUnderTest(&DependsOnHci::Factory));
    ASSERT_TRUE(fake_registry_.IsStarted<HciLayer>());

    // Verify that reset was received
    auto sent_command = hal->GetSentCommand();
    ASSERT_TRUE(sent_command.has_value());
    auto reset_view = ResetView::Create(CommandView::Create(*sent_command));
    ASSERT_TRUE(reset_view.IsValid());

    // Send the response event
    uint8_t num_packets = 1;
    ErrorCode error_code = ErrorCode::SUCCESS;
    hal->callbacks->hciEventReceived(GetPacketBytes(ResetCompleteBuilder::Create(num_packets, error_code)));
  }

  void TearDown() override {
    fake_registry_.StopAll();
  }

  std::vector<uint8_t> GetPacketBytes(std::unique_ptr<packet::BasePacketBuilder> packet) {
    std::vector<uint8_t> bytes;
    BitInserter i(bytes);
    bytes.reserve(packet->size());
    packet->Serialize(i);
    return bytes;
  }

  DependsOnHci* upper = nullptr;
  hal::TestHciHal* hal = nullptr;
  HciLayer* hci = nullptr;
  TestModuleRegistry fake_registry_;
};

TEST_F(HciTest, initAndClose) {}

TEST_F(HciTest, leMetaEvent) {
  // Send an LE event
  ErrorCode status = ErrorCode::SUCCESS;
  uint16_t handle = 0x123;
  Role role = Role::CENTRAL;
  AddressType peer_address_type = AddressType::PUBLIC_DEVICE_ADDRESS;
  Address peer_address = Address::kAny;
  uint16_t conn_interval = 0x0ABC;
  uint16_t conn_latency = 0x0123;
  uint16_t supervision_timeout = 0x0B05;
  ClockAccuracy central_clock_accuracy = ClockAccuracy::PPM_50;
  hal->callbacks->hciEventReceived(GetPacketBytes(LeConnectionCompleteBuilder::Create(
      status,
      handle,
      role,
      peer_address_type,
      peer_address,
      conn_interval,
      conn_latency,
      supervision_timeout,
      central_clock_accuracy)));

  // Wait for the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(LeConnectionCompleteView::Create(LeMetaEventView::Create(EventView::Create(*event)))
                  .IsValid());
}

TEST_F(HciTest, postEventsOnceOnHciHandler) {
  // Send a CreateConnection command.
  Address addr;
  Address::FromString("01:02:03:04:05:06", addr);
  upper->SendHciCommandExpectingStatus(CreateConnectionBuilder::Create(
      addr,
      0,
      PageScanRepetitionMode::R0,
      0,
      ClockOffsetValid::INVALID,
      CreateConnectionRoleSwitch::ALLOW_ROLE_SWITCH));

  // Validate the received command.
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto command = CreateConnectionView::Create(
      ConnectionManagementCommandView::Create(AclCommandView::Create(*sent_command)));
  ASSERT_TRUE(command.IsValid());

  // Send a status and a connection complete at the same time.
  uint8_t num_packets = 1;
  hal->callbacks->hciEventReceived(
      GetPacketBytes(CreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, num_packets)));
  hal->callbacks->hciEventReceived(GetPacketBytes(ConnectionCompleteBuilder::Create(
      ErrorCode::SUCCESS, 0x123, addr, LinkType::ACL, Enable::DISABLED)));

  // Make sure the status comes first.
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(
      CreateConnectionStatusView::Create(CommandStatusView::Create(EventView::Create(*event)))
          .IsValid());
}

TEST_F(HciTest, DISABLED_hciTimeOut) {
  upper->SendHciCommandExpectingComplete(ResetBuilder::Create());
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto reset = ResetView::Create(*sent_command);
  ASSERT_TRUE(reset.IsValid());

  auto event = upper->GetReceivedEvent(HciLayer::kHciTimeoutMs);
  ASSERT_FALSE(event.has_value());

  sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto debug = ControllerDebugInfoView::Create(VendorCommandView::Create(*sent_command));
  ASSERT_TRUE(debug.IsValid());
}

TEST_F(HciTest, noOpCredits) {
  // Send 0 credits
  uint8_t num_packets = 0;
  hal->callbacks->hciEventReceived(GetPacketBytes(NoCommandCompleteBuilder::Create(num_packets)));

  upper->SendHciCommandExpectingComplete(ReadLocalVersionInformationBuilder::Create());

  // Verify that nothing was sent
  ASSERT_FALSE(hal->GetSentCommand(std::chrono::milliseconds(10)).has_value());

  num_packets = 1;
  hal->callbacks->hciEventReceived(GetPacketBytes(NoCommandCompleteBuilder::Create(num_packets)));

  // Verify that one was sent
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());

  // Send the response event
  ErrorCode error_code = ErrorCode::SUCCESS;
  LocalVersionInformation local_version_information;
  local_version_information.hci_version_ = HciVersion::V_5_0;
  local_version_information.hci_revision_ = 0x1234;
  local_version_information.lmp_version_ = LmpVersion::V_4_2;
  local_version_information.manufacturer_name_ = 0xBAD;
  local_version_information.lmp_subversion_ = 0x5678;
  hal->callbacks->hciEventReceived(GetPacketBytes(
      ReadLocalVersionInformationCompleteBuilder::Create(num_packets, error_code, local_version_information)));

  // Wait for the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(ReadLocalVersionInformationCompleteView::Create(
                  CommandCompleteView::Create(EventView::Create(*event)))
                  .IsValid());
}

TEST_F(HciTest, creditsTest) {
  auto sent_command = hal->GetSentCommand(std::chrono::milliseconds(10));
  ASSERT_FALSE(sent_command.has_value());

  // Send all three commands
  upper->SendHciCommandExpectingComplete(ReadLocalVersionInformationBuilder::Create());
  upper->SendHciCommandExpectingComplete(ReadLocalSupportedCommandsBuilder::Create());
  upper->SendHciCommandExpectingComplete(ReadLocalSupportedFeaturesBuilder::Create());

  // Verify that the first one is sent
  sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto version_view = ReadLocalVersionInformationView::Create(CommandView::Create(*sent_command));
  ASSERT_TRUE(version_view.IsValid());

  // Verify that only one was sent
  sent_command = hal->GetSentCommand(std::chrono::milliseconds(10));
  ASSERT_FALSE(sent_command.has_value());

  // Send the response event
  uint8_t num_packets = 1;
  ErrorCode error_code = ErrorCode::SUCCESS;
  LocalVersionInformation local_version_information;
  local_version_information.hci_version_ = HciVersion::V_5_0;
  local_version_information.hci_revision_ = 0x1234;
  local_version_information.lmp_version_ = LmpVersion::V_4_2;
  local_version_information.manufacturer_name_ = 0xBAD;
  local_version_information.lmp_subversion_ = 0x5678;
  hal->callbacks->hciEventReceived(GetPacketBytes(
      ReadLocalVersionInformationCompleteBuilder::Create(num_packets, error_code, local_version_information)));

  // Wait for the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(ReadLocalVersionInformationCompleteView::Create(
                  CommandCompleteView::Create(EventView::Create(*event)))
                  .IsValid());

  // Verify that the second one is sent
  sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto supported_commands_view =
      ReadLocalSupportedCommandsView::Create(CommandView::Create(*sent_command));
  ASSERT_TRUE(supported_commands_view.IsValid());

  // Verify that only one was sent
  sent_command = hal->GetSentCommand(std::chrono::milliseconds(10));
  ASSERT_FALSE(sent_command.has_value());

  // Send the response event
  std::array<uint8_t, 64> supported_commands;
  for (uint8_t i = 0; i < 64; i++) {
    supported_commands[i] = i;
  }
  hal->callbacks->hciEventReceived(GetPacketBytes(ReadLocalSupportedCommandsCompleteBuilder::Create(
      num_packets, error_code, supported_commands)));

  // Wait for the event
  event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(ReadLocalSupportedCommandsCompleteView::Create(CommandCompleteView::Create(*event))
                  .IsValid());
  // Verify that the third one is sent
  sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto supported_features_view =
      ReadLocalSupportedFeaturesView::Create(CommandView::Create(*sent_command));
  ASSERT_TRUE(supported_features_view.IsValid());

  // Verify that only one was sent
  sent_command = hal->GetSentCommand(std::chrono::milliseconds(10));
  ASSERT_FALSE(sent_command.has_value());

  // Send the response event
  uint64_t lmp_features = 0x012345678abcdef;
  hal->callbacks->hciEventReceived(
      GetPacketBytes(ReadLocalSupportedFeaturesCompleteBuilder::Create(num_packets, error_code, lmp_features)));

  // Wait for the event
  event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(ReadLocalSupportedFeaturesCompleteView::Create(CommandCompleteView::Create(*event))
                  .IsValid());
}

TEST_F(HciTest, leSecurityInterfaceTest) {
  // Send LeRand to the controller
  upper->SendLeSecurityCommandExpectingComplete(LeRandBuilder::Create());

  // Check the command
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  LeRandView view =
      LeRandView::Create(LeSecurityCommandView::Create(CommandView::Create(*sent_command)));
  ASSERT_TRUE(view.IsValid());

  // Send a Command Complete to the host
  uint8_t num_packets = 1;
  ErrorCode status = ErrorCode::SUCCESS;
  uint64_t rand = 0x0123456789abcdef;
  hal->callbacks->hciEventReceived(GetPacketBytes(LeRandCompleteBuilder::Create(num_packets, status, rand)));

  // Verify the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(LeRandCompleteView::Create(CommandCompleteView::Create(*event)).IsValid());
}

TEST_F(HciTest, securityInterfacesTest) {
  // Send WriteSimplePairingMode to the controller
  Enable enable = Enable::ENABLED;
  upper->SendSecurityCommandExpectingComplete(WriteSimplePairingModeBuilder::Create(enable));

  // Check the command
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  auto view = WriteSimplePairingModeView::Create(
      SecurityCommandView::Create(CommandView::Create(*sent_command)));
  ASSERT_TRUE(view.IsValid());

  // Send a Command Complete to the host
  uint8_t num_packets = 1;
  ErrorCode status = ErrorCode::SUCCESS;
  hal->callbacks->hciEventReceived(GetPacketBytes(WriteSimplePairingModeCompleteBuilder::Create(num_packets, status)));

  // Verify the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(
      WriteSimplePairingModeCompleteView::Create(CommandCompleteView::Create(*event)).IsValid());
}

TEST_F(HciTest, createConnectionTest) {
  // Send CreateConnection to the controller
  Address bd_addr;
  ASSERT_TRUE(Address::FromString("A1:A2:A3:A4:A5:A6", bd_addr));
  uint16_t packet_type = 0x1234;
  PageScanRepetitionMode page_scan_repetition_mode = PageScanRepetitionMode::R0;
  uint16_t clock_offset = 0x3456;
  ClockOffsetValid clock_offset_valid = ClockOffsetValid::VALID;
  CreateConnectionRoleSwitch allow_role_switch = CreateConnectionRoleSwitch::ALLOW_ROLE_SWITCH;
  upper->SendHciCommandExpectingStatus(CreateConnectionBuilder::Create(
      bd_addr, packet_type, page_scan_repetition_mode, clock_offset, clock_offset_valid, allow_role_switch));

  // Check the command
  auto sent_command = hal->GetSentCommand();
  ASSERT_TRUE(sent_command.has_value());
  CreateConnectionView view = CreateConnectionView::Create(ConnectionManagementCommandView::Create(
      AclCommandView::Create(CommandView::Create(*sent_command))));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(bd_addr, view.GetBdAddr());
  ASSERT_EQ(packet_type, view.GetPacketType());
  ASSERT_EQ(page_scan_repetition_mode, view.GetPageScanRepetitionMode());
  ASSERT_EQ(clock_offset, view.GetClockOffset());
  ASSERT_EQ(clock_offset_valid, view.GetClockOffsetValid());
  ASSERT_EQ(allow_role_switch, view.GetAllowRoleSwitch());

  // Send a Command Status to the host
  ErrorCode status = ErrorCode::SUCCESS;
  uint16_t handle = 0x123;
  LinkType link_type = LinkType::ACL;
  Enable encryption_enabled = Enable::DISABLED;
  hal->callbacks->hciEventReceived(GetPacketBytes(CreateConnectionStatusBuilder::Create(ErrorCode::SUCCESS, 1)));

  // Verify the event
  auto event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ASSERT_TRUE(CreateConnectionStatusView::Create(CommandStatusView::Create(*event)).IsValid());

  // Send a ConnectionComplete to the host
  hal->callbacks->hciEventReceived(
      GetPacketBytes(ConnectionCompleteBuilder::Create(status, handle, bd_addr, link_type, encryption_enabled)));

  // Verify the event
  event = upper->GetReceivedEvent();
  ASSERT_TRUE(event.has_value());
  ConnectionCompleteView connection_complete_view = ConnectionCompleteView::Create(*event);
  ASSERT_TRUE(connection_complete_view.IsValid());
  ASSERT_EQ(status, connection_complete_view.GetStatus());
  ASSERT_EQ(handle, connection_complete_view.GetConnectionHandle());
  ASSERT_EQ(link_type, connection_complete_view.GetLinkType());
  ASSERT_EQ(encryption_enabled, connection_complete_view.GetEncryptionEnabled());

  // Send an ACL packet from the remote
  PacketBoundaryFlag packet_boundary_flag = PacketBoundaryFlag::FIRST_AUTOMATICALLY_FLUSHABLE;
  BroadcastFlag broadcast_flag = BroadcastFlag::POINT_TO_POINT;
  auto acl_payload = std::make_unique<RawBuilder>();
  acl_payload->AddOctets(bd_addr.address);
  acl_payload->AddOctets2(handle);
  hal->callbacks->aclDataReceived(
      GetPacketBytes(AclBuilder::Create(handle, packet_boundary_flag, broadcast_flag, std::move(acl_payload))));

  // Verify the ACL packet
  auto acl_view_result = upper->GetReceivedAcl();
  ASSERT_TRUE(acl_view_result.has_value());
  auto acl_view = *acl_view_result;
  ASSERT_TRUE(acl_view.IsValid());
  ASSERT_EQ(bd_addr.length() + sizeof(handle), acl_view.GetPayload().size());
  auto itr = acl_view.GetPayload().begin();
  ASSERT_EQ(bd_addr, itr.extract<Address>());
  ASSERT_EQ(handle, itr.extract<uint16_t>());

  // Send an ACL packet from DependsOnHci
  PacketBoundaryFlag packet_boundary_flag2 = PacketBoundaryFlag::FIRST_AUTOMATICALLY_FLUSHABLE;
  BroadcastFlag broadcast_flag2 = BroadcastFlag::POINT_TO_POINT;
  auto acl_payload2 = std::make_unique<RawBuilder>();
  acl_payload2->AddOctets2(handle);
  acl_payload2->AddOctets(bd_addr.address);
  upper->SendAclData(AclBuilder::Create(handle, packet_boundary_flag2, broadcast_flag2, std::move(acl_payload2)));

  // Verify the ACL packet
  auto sent_acl = hal->GetSentAcl();
  ASSERT_TRUE(sent_acl.has_value());
  AclView sent_acl_view = AclView::Create(*sent_acl);
  ASSERT_TRUE(sent_acl_view.IsValid());
  ASSERT_EQ(bd_addr.length() + sizeof(handle), sent_acl_view.GetPayload().size());
  auto sent_itr = sent_acl_view.GetPayload().begin();
  ASSERT_EQ(handle, sent_itr.extract<uint16_t>());
  ASSERT_EQ(bd_addr, sent_itr.extract<Address>());
}

TEST_F(HciTest, receiveMultipleAclPackets) {
  Address bd_addr;
  ASSERT_TRUE(Address::FromString("A1:A2:A3:A4:A5:A6", bd_addr));
  uint16_t handle = 0x0001;
  const uint16_t num_packets = 100;
  PacketBoundaryFlag packet_boundary_flag = PacketBoundaryFlag::FIRST_AUTOMATICALLY_FLUSHABLE;
  BroadcastFlag broadcast_flag = BroadcastFlag::POINT_TO_POINT;
  for (uint16_t i = 0; i < num_packets; i++) {
    auto acl_payload = std::make_unique<RawBuilder>();
    acl_payload->AddOctets(bd_addr.address);
    acl_payload->AddOctets2(handle);
    acl_payload->AddOctets2(i);
    hal->callbacks->aclDataReceived(
        GetPacketBytes(AclBuilder::Create(handle, packet_boundary_flag, broadcast_flag, std::move(acl_payload))));
  }

  for (uint16_t i = 0; i < num_packets; i++) {
    auto acl_opt = upper->GetReceivedAcl();
    ASSERT_TRUE(acl_opt.has_value());
    auto acl_view = *acl_opt;
    ASSERT_TRUE(acl_view.IsValid());
    ASSERT_EQ(bd_addr.length() + sizeof(handle) + sizeof(i), acl_view.GetPayload().size());
    auto itr = acl_view.GetPayload().begin();
    ASSERT_EQ(bd_addr, itr.extract<Address>());
    ASSERT_EQ(handle, itr.extract<uint16_t>());
    ASSERT_EQ(i, itr.extract<uint16_t>());
  }
}

TEST_F(HciTest, receiveMultipleIsoPackets) {
  uint16_t handle = 0x0001;
  const uint16_t num_packets = 100;
  IsoPacketBoundaryFlag packet_boundary_flag = IsoPacketBoundaryFlag::COMPLETE_SDU;
  TimeStampFlag timestamp_flag = TimeStampFlag::NOT_PRESENT;
  for (uint16_t i = 0; i < num_packets; i++) {
    auto iso_payload = std::make_unique<RawBuilder>();
    iso_payload->AddOctets2(handle);
    iso_payload->AddOctets2(i);
    hal->callbacks->isoDataReceived(
        GetPacketBytes(IsoBuilder::Create(handle, packet_boundary_flag, timestamp_flag, std::move(iso_payload))));
  }
  for (uint16_t i = 0; i < num_packets; i++) {
    auto iso_opt = upper->GetReceivedIso();
    ASSERT_TRUE(iso_opt.has_value());
    auto iso_view = *iso_opt;
    ASSERT_TRUE(iso_view.IsValid());
    ASSERT_EQ(sizeof(handle) + sizeof(i), iso_view.GetPayload().size());
    auto itr = iso_view.GetPayload().begin();
    ASSERT_EQ(handle, itr.extract<uint16_t>());
    ASSERT_EQ(i, itr.extract<uint16_t>());
  }
}

}  // namespace
}  // namespace hci
}  // namespace bluetooth
