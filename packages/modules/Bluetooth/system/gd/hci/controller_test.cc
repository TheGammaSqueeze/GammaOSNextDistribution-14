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

#include "hci/controller.h"

#include <android_bluetooth_flags.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <chrono>
#include <future>
#include <memory>
#include <sstream>

#include "common/bind.h"
#include "common/init_flags.h"
#include "hci/address.h"
#include "hci/hci_layer_fake.h"
#include "module_dumper.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

using namespace bluetooth;
using namespace std::chrono_literals;

using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

namespace bluetooth {
namespace hci {

namespace {

constexpr uint16_t kHandle1 = 0x123;
constexpr uint16_t kCredits1 = 0x78;
constexpr uint16_t kHandle2 = 0x456;
constexpr uint16_t kCredits2 = 0x9a;
constexpr uint64_t kRandomNumber = 0x123456789abcdef0;
/*sbc_supported= 1, aac_supported= 1, aptx_supported= 0, aptx_hd_supported= 0, ldac_supported= 1 */
constexpr uint32_t kDynamicAudioBufferSupport = 0x13;
uint16_t feature_spec_version = 55;
constexpr char title[] = "hci_controller_test";

}  // namespace

namespace {

class HciLayerFakeForController : public HciLayerFake {
 public:
  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> command,
      common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) override {
    GetHandler()->Post(common::BindOnce(
        &HciLayerFakeForController::HandleCommand,
        common::Unretained(this),
        std::move(command),
        std::move(on_complete)));
  }

  void EnqueueCommand(
      std::unique_ptr<CommandBuilder> /* command */,
      common::ContextualOnceCallback<void(CommandStatusView)> /* on_status */) override {
    FAIL() << "Controller properties should not generate Command Status";
  }

  void HandleCommand(
      std::unique_ptr<CommandBuilder> command_builder,
      common::ContextualOnceCallback<void(CommandCompleteView)> on_complete) {
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    BitInserter i(*bytes);
    bytes->reserve((command_builder)->size());
    command_builder->Serialize(i);
    auto packet_view = packet::PacketView<packet::kLittleEndian>(bytes);
    CommandView command = CommandView::Create(packet_view);
    ASSERT_TRUE(command.IsValid());

    uint8_t num_packets = 1;
    std::unique_ptr<packet::BasePacketBuilder> event_builder;
    switch (command.GetOpCode()) {
      case (OpCode::READ_LOCAL_NAME): {
        std::array<uint8_t, 248> local_name = {'D', 'U', 'T', '\0'};
        event_builder = ReadLocalNameCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, local_name);
      } break;
      case (OpCode::READ_LOCAL_VERSION_INFORMATION): {
        LocalVersionInformation local_version_information;
        local_version_information.hci_version_ = HciVersion::V_5_0;
        local_version_information.hci_revision_ = 0x1234;
        local_version_information.lmp_version_ = LmpVersion::V_4_2;
        local_version_information.manufacturer_name_ = 0xBAD;
        local_version_information.lmp_subversion_ = 0x5678;
        event_builder = ReadLocalVersionInformationCompleteBuilder::Create(
            num_packets, ErrorCode::SUCCESS, local_version_information);
      } break;
      case (OpCode::READ_LOCAL_SUPPORTED_COMMANDS): {
        std::array<uint8_t, 64> supported_commands;
        for (int i = 0; i < 37; i++) {
          supported_commands[i] = 0xff;
        }
        for (int i = 37; i < 64; i++) {
          supported_commands[i] = 0x00;
        }
        event_builder =
            ReadLocalSupportedCommandsCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, supported_commands);
      } break;
      case (OpCode::READ_LOCAL_SUPPORTED_CODECS_V1): {
        std::vector<uint8_t> supported_codecs{0, 1, 2, 3, 4, 5, 6};
        std::vector<uint32_t> supported_vendor_codecs;
        event_builder = ReadLocalSupportedCodecsV1CompleteBuilder::Create(
            num_packets, ErrorCode::SUCCESS, supported_codecs, supported_vendor_codecs);
      } break;
      case (OpCode::READ_LOCAL_EXTENDED_FEATURES): {
        ReadLocalExtendedFeaturesView read_command = ReadLocalExtendedFeaturesView::Create(command);
        ASSERT_TRUE(read_command.IsValid());
        uint8_t page_bumber = read_command.GetPageNumber();
        uint64_t lmp_features = 0x012345678abcdef;
        lmp_features += page_bumber;
        event_builder = ReadLocalExtendedFeaturesCompleteBuilder::Create(
            num_packets, ErrorCode::SUCCESS, page_bumber, 0x02, lmp_features);
      } break;
      case (OpCode::READ_BUFFER_SIZE): {
        event_builder = ReadBufferSizeCompleteBuilder::Create(
            num_packets,
            ErrorCode::SUCCESS,
            acl_data_packet_length,
            synchronous_data_packet_length,
            total_num_acl_data_packets,
            total_num_synchronous_data_packets);
      } break;
      case (OpCode::READ_BD_ADDR): {
        event_builder = ReadBdAddrCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, Address::kAny);
      } break;
      case (OpCode::LE_READ_BUFFER_SIZE_V1): {
        LeBufferSize le_buffer_size;
        le_buffer_size.le_data_packet_length_ = 0x16;
        le_buffer_size.total_num_le_packets_ = 0x08;
        event_builder = LeReadBufferSizeV1CompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, le_buffer_size);
      } break;
      case (OpCode::LE_READ_LOCAL_SUPPORTED_FEATURES): {
        event_builder =
            LeReadLocalSupportedFeaturesCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, 0x001f123456789abc);
      } break;
      case (OpCode::LE_READ_SUPPORTED_STATES): {
        event_builder =
            LeReadSupportedStatesCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, 0x001f123456789abe);
      } break;
      case (OpCode::LE_READ_MAXIMUM_DATA_LENGTH): {
        LeMaximumDataLength le_maximum_data_length;
        le_maximum_data_length.supported_max_tx_octets_ = 0x12;
        le_maximum_data_length.supported_max_tx_time_ = 0x34;
        le_maximum_data_length.supported_max_rx_octets_ = 0x56;
        le_maximum_data_length.supported_max_rx_time_ = 0x78;
        event_builder =
            LeReadMaximumDataLengthCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, le_maximum_data_length);
      } break;
      case (OpCode::LE_READ_MAXIMUM_ADVERTISING_DATA_LENGTH): {
        event_builder =
            LeReadMaximumAdvertisingDataLengthCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, 0x0672);
      } break;
      case (OpCode::LE_READ_NUMBER_OF_SUPPORTED_ADVERTISING_SETS): {
        event_builder =
            LeReadNumberOfSupportedAdvertisingSetsCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, 0xF0);
      } break;
      case (OpCode::LE_GET_VENDOR_CAPABILITIES):
        if (vendor_capabilities_ == nullptr) {
          BaseVendorCapabilities base_vendor_capabilities;
          base_vendor_capabilities.max_advt_instances_ = 0x10;
          base_vendor_capabilities.offloaded_resolution_of_private_address_ = 0x01;
          base_vendor_capabilities.total_scan_results_storage_ = 0x2800;
          base_vendor_capabilities.max_irk_list_sz_ = 0x20;
          base_vendor_capabilities.filtering_support_ = 0x01;
          base_vendor_capabilities.max_filter_ = 0x10;
          base_vendor_capabilities.activity_energy_info_support_ = 0x01;

          auto payload = std::make_unique<RawBuilder>();
          if (feature_spec_version > 55) {
            std::vector<uint8_t> payload_bytes = {
                0x20, 0x00, 0x01, 0x00, 0x00, 0x1f, 0x00, 0x00, 0x00, 0x00};
            payload->AddOctets2(feature_spec_version);
            payload->AddOctets(payload_bytes);
          }
          event_builder = LeGetVendorCapabilitiesCompleteBuilder::Create(
              num_packets, ErrorCode::SUCCESS, base_vendor_capabilities, std::move(payload));
        } else {
          event_builder = std::move(vendor_capabilities_);
          vendor_capabilities_.reset();
        }
        break;
      case (OpCode::DYNAMIC_AUDIO_BUFFER): {
        auto dab_command =
            DynamicAudioBufferView::CreateOptional(VendorCommandView::Create(command));
        if (dab_command->GetDabCommand() == DabCommand::GET_AUDIO_BUFFER_TIME_CAPABILITY) {
          std::array<DynamicAudioBufferCodecCapability, 32> capabilities{};
          capabilities[0] =
              DynamicAudioBufferCodecCapability(0x123, 0x103, 0x1234);  // sbc_capabilities
          capabilities[1] =
              DynamicAudioBufferCodecCapability(0x223, 0x123, 0x2340);  // aac_capabilities
          capabilities[4] =
              DynamicAudioBufferCodecCapability(0x323, 0x223, 0x3456);  // ldac_capabilities
          event_builder = DabGetAudioBufferTimeCapabilityCompleteBuilder::Create(
              1, ErrorCode::SUCCESS, kDynamicAudioBufferSupport, capabilities);
        } else {
          auto set_command = DabSetAudioBufferTimeView::CreateOptional(*dab_command);
          dynamic_audio_buffer_time = set_command->GetBufferTimeMs();
          event_builder = DabSetAudioBufferTimeCompleteBuilder::Create(
              1, ErrorCode::SUCCESS, dynamic_audio_buffer_time);
        }
      } break;
      case (OpCode::SET_EVENT_MASK): {
        auto view = SetEventMaskView::Create(command);
        ASSERT_TRUE(view.IsValid());
        event_mask = view.GetEventMask();
        event_builder = SetEventMaskCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS);
      } break;
      case (OpCode::LE_SET_EVENT_MASK): {
        auto view = LeSetEventMaskView::Create(command);
        ASSERT_TRUE(view.IsValid());
        le_event_mask = view.GetLeEventMask();
        event_builder = LeSetEventMaskCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS);
      } break;

      case (OpCode::LE_RAND): {
        auto view = LeRandView::Create(LeSecurityCommandView::Create(command));
        ASSERT_TRUE(view.IsValid());
        event_builder = LeRandCompleteBuilder::Create(num_packets, ErrorCode::SUCCESS, kRandomNumber);
      } break;

      // Let the test check and handle these commands.
      case (OpCode::RESET):
      case (OpCode::SET_EVENT_FILTER):
      case (OpCode::HOST_BUFFER_SIZE):
        HciLayerFake::EnqueueCommand(std::move(command_builder), std::move(on_complete));
        return;

      default:
        LOG_INFO("Dropping unhandled packet (%s)", OpCodeText(command.GetOpCode()).c_str());
        return;
    }
    auto packet = GetPacketView(std::move(event_builder));
    EventView event = EventView::Create(packet);
    ASSERT_TRUE(event.IsValid());
    CommandCompleteView command_complete = CommandCompleteView::Create(event);
    ASSERT_TRUE(command_complete.IsValid());
    on_complete.Invoke(std::move(command_complete));
  }

  void IncomingCredit() {
    std::vector<CompletedPackets> completed_packets;
    CompletedPackets cp;
    cp.host_num_of_completed_packets_ = kCredits1;
    cp.connection_handle_ = kHandle1;
    completed_packets.push_back(cp);
    cp.host_num_of_completed_packets_ = kCredits2;
    cp.connection_handle_ = kHandle2;
    completed_packets.push_back(cp);
    IncomingEvent(NumberOfCompletedPacketsBuilder::Create(completed_packets));
  }

  std::unique_ptr<EventBuilder> vendor_capabilities_ = nullptr;
  constexpr static uint16_t acl_data_packet_length = 1024;
  constexpr static uint8_t synchronous_data_packet_length = 60;
  constexpr static uint16_t total_num_acl_data_packets = 10;
  constexpr static uint16_t total_num_synchronous_data_packets = 12;
  uint64_t event_mask = 0;
  uint64_t le_event_mask = 0;
  uint16_t dynamic_audio_buffer_time = 0;
};

class ControllerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    feature_spec_version = feature_spec_version_;
    bluetooth::common::InitFlags::SetAllForTesting();
    test_hci_layer_ = new HciLayerFakeForController;
    test_hci_layer_->vendor_capabilities_ = std::move(vendor_capabilities_);
    vendor_capabilities_.reset();
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);
    client_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    fake_registry_.Start<Controller>(&thread_);
    controller_ = static_cast<Controller*>(fake_registry_.GetModuleUnderTest(&Controller::Factory));
  }

  void TearDown() override {
    fake_registry_.StopAll();
  }

  TestModuleRegistry fake_registry_;
  HciLayerFakeForController* test_hci_layer_ = nullptr;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  Controller* controller_ = nullptr;
  os::Handler* client_handler_ = nullptr;
  uint16_t feature_spec_version_ = 98;
  std::unique_ptr<EventBuilder> vendor_capabilities_ = nullptr;
};
}  // namespace

class Controller055Test : public ControllerTest {
 protected:
  void SetUp() override {
    feature_spec_version_ = 55;
    ControllerTest::SetUp();
  }
};

class Controller095Test : public ControllerTest {
 protected:
  void SetUp() override {
    feature_spec_version_ = 95;
    ControllerTest::SetUp();
  }
};

class Controller096Test : public ControllerTest {
 protected:
  void SetUp() override {
    feature_spec_version_ = 96;
    ControllerTest::SetUp();
  }
};

class Controller103Test : public ControllerTest {
 protected:
  void SetUp() override {
    feature_spec_version_ = 0x100 + 0x03;
    BaseVendorCapabilities base_vendor_capabilities;
    base_vendor_capabilities.max_advt_instances_ = 0x10;
    base_vendor_capabilities.offloaded_resolution_of_private_address_ = 0x01;
    base_vendor_capabilities.total_scan_results_storage_ = 0x2800;
    base_vendor_capabilities.max_irk_list_sz_ = 0x20;
    base_vendor_capabilities.filtering_support_ = 0x01;
    base_vendor_capabilities.max_filter_ = 0x10;
    base_vendor_capabilities.activity_energy_info_support_ = 0x01;
    vendor_capabilities_ = LeGetVendorCapabilitiesComplete103Builder::Create(
        1,
        ErrorCode::SUCCESS,
        base_vendor_capabilities,
        feature_spec_version_,
        0x102,
        /*extended_scan_support=*/1,
        /*debug_logging_supported=*/1,
        /*le_address_generation_offloading_support=*/0,
        /*a2dp_source_offload_capability_mask=*/0x4,
        /*bluetooth_quality_report_support=*/1,
        kDynamicAudioBufferSupport,
        std::make_unique<RawBuilder>());
    ControllerTest::SetUp();
  }
};

class Controller104Test : public ControllerTest {
 protected:
  void SetUp() override {
    feature_spec_version_ = 0x100 + 0x04;
    BaseVendorCapabilities base_vendor_capabilities;
    base_vendor_capabilities.max_advt_instances_ = 0x10;
    base_vendor_capabilities.offloaded_resolution_of_private_address_ = 0x01;
    base_vendor_capabilities.total_scan_results_storage_ = 0x2800;
    base_vendor_capabilities.max_irk_list_sz_ = 0x20;
    base_vendor_capabilities.filtering_support_ = 0x01;
    base_vendor_capabilities.max_filter_ = 0x10;
    base_vendor_capabilities.activity_energy_info_support_ = 0x01;
    vendor_capabilities_ = LeGetVendorCapabilitiesComplete104Builder::Create(
        1,
        ErrorCode::SUCCESS,
        base_vendor_capabilities,
        feature_spec_version_,
        0x102,
        /*extended_scan_support=*/1,
        /*debug_logging_supported=*/1,
        /*le_address_generation_offloading_support=*/0,
        /*a2dp_source_offload_capability_mask=*/0x4,
        /*bluetooth_quality_report_support=*/1,
        kDynamicAudioBufferSupport,
        /*a2dp_offload_v2_support=*/1,
        std::make_unique<RawBuilder>());
    ControllerTest::SetUp();
  }
};

TEST_F(ControllerTest, startup_teardown) {}

TEST_F(ControllerTest, read_controller_info) {
  ASSERT_EQ(controller_->GetAclPacketLength(), test_hci_layer_->acl_data_packet_length);
  ASSERT_EQ(controller_->GetNumAclPacketBuffers(), test_hci_layer_->total_num_acl_data_packets);
  ASSERT_EQ(controller_->GetScoPacketLength(), test_hci_layer_->synchronous_data_packet_length);
  ASSERT_EQ(controller_->GetNumScoPacketBuffers(), test_hci_layer_->total_num_synchronous_data_packets);
  ASSERT_EQ(controller_->GetMacAddress(), Address::kAny);
  LocalVersionInformation local_version_information = controller_->GetLocalVersionInformation();
  ASSERT_EQ(local_version_information.hci_version_, HciVersion::V_5_0);
  ASSERT_EQ(local_version_information.hci_revision_, 0x1234);
  ASSERT_EQ(local_version_information.lmp_version_, LmpVersion::V_4_2);
  ASSERT_EQ(local_version_information.manufacturer_name_, 0xBAD);
  ASSERT_EQ(local_version_information.lmp_subversion_, 0x5678);
  ASSERT_EQ(controller_->GetLeBufferSize().le_data_packet_length_, 0x16);
  ASSERT_EQ(controller_->GetLeBufferSize().total_num_le_packets_, 0x08);
  ASSERT_EQ(controller_->GetLeSupportedStates(), 0x001f123456789abeUL);
  ASSERT_EQ(controller_->GetLeMaximumDataLength().supported_max_tx_octets_, 0x12);
  ASSERT_EQ(controller_->GetLeMaximumDataLength().supported_max_tx_time_, 0x34);
  ASSERT_EQ(controller_->GetLeMaximumDataLength().supported_max_rx_octets_, 0x56);
  ASSERT_EQ(controller_->GetLeMaximumDataLength().supported_max_rx_time_, 0x78);
  ASSERT_EQ(controller_->GetLeMaximumAdvertisingDataLength(), 0x0672);
  ASSERT_EQ(controller_->GetLeNumberOfSupportedAdverisingSets(), 0xF0);
  ASSERT_TRUE(controller_->GetLocalSupportedBrEdrCodecIds().size() > 0);
}

TEST_F(ControllerTest, read_write_local_name) {
  ASSERT_EQ(controller_->GetLocalName(), "DUT");
  controller_->WriteLocalName("New name");
  ASSERT_EQ(controller_->GetLocalName(), "New name");
}

TEST_F(ControllerTest, send_set_event_mask_command) {
  uint64_t new_event_mask = test_hci_layer_->event_mask - 1;
  controller_->SetEventMask(new_event_mask);
  // Send another command to make sure it was applied
  controller_->Reset();
  auto packet = test_hci_layer_->GetCommand(OpCode::RESET);
  ASSERT_EQ(new_event_mask, test_hci_layer_->event_mask);
}

TEST_F(ControllerTest, send_reset_command) {
  controller_->Reset();
  auto packet = test_hci_layer_->GetCommand(OpCode::RESET);
  auto command = ResetView::Create(packet);
  ASSERT_TRUE(command.IsValid());
}

TEST_F(ControllerTest, send_set_event_filter_command) {
  controller_->SetEventFilterInquiryResultAllDevices();
  auto packet = test_hci_layer_->GetCommand(OpCode::SET_EVENT_FILTER);
  auto set_event_filter_view1 = SetEventFilterView::Create(packet);
  auto set_event_filter_inquiry_result_view1 = SetEventFilterInquiryResultView::Create(set_event_filter_view1);
  auto command1 = SetEventFilterInquiryResultAllDevicesView::Create(set_event_filter_inquiry_result_view1);
  ASSERT_TRUE(command1.IsValid());

  ClassOfDevice class_of_device({0xab, 0xcd, 0xef});
  ClassOfDevice class_of_device_mask({0x12, 0x34, 0x56});
  controller_->SetEventFilterInquiryResultClassOfDevice(class_of_device, class_of_device_mask);
  packet = test_hci_layer_->GetCommand(OpCode::SET_EVENT_FILTER);
  auto set_event_filter_view2 = SetEventFilterView::Create(packet);
  auto set_event_filter_inquiry_result_view2 = SetEventFilterInquiryResultView::Create(set_event_filter_view2);
  auto command2 = SetEventFilterInquiryResultClassOfDeviceView::Create(set_event_filter_inquiry_result_view2);
  ASSERT_TRUE(command2.IsValid());
  ASSERT_EQ(command2.GetClassOfDevice(), class_of_device);

  Address bdaddr({0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc});
  controller_->SetEventFilterConnectionSetupAddress(bdaddr, AutoAcceptFlag::AUTO_ACCEPT_ON_ROLE_SWITCH_ENABLED);
  packet = test_hci_layer_->GetCommand(OpCode::SET_EVENT_FILTER);
  auto set_event_filter_view3 = SetEventFilterView::Create(packet);
  auto set_event_filter_connection_setup_view = SetEventFilterConnectionSetupView::Create(set_event_filter_view3);
  auto command3 = SetEventFilterConnectionSetupAddressView::Create(set_event_filter_connection_setup_view);
  ASSERT_TRUE(command3.IsValid());
  ASSERT_EQ(command3.GetAddress(), bdaddr);
}

TEST_F(ControllerTest, send_host_buffer_size_command) {
  controller_->HostBufferSize(0xFF00, 0xF1, 0xFF02, 0xFF03);
  auto packet = test_hci_layer_->GetCommand(OpCode::HOST_BUFFER_SIZE);
  auto command = HostBufferSizeView::Create(packet);
  ASSERT_TRUE(command.IsValid());
  ASSERT_EQ(command.GetHostAclDataPacketLength(), 0xFF00);
  ASSERT_EQ(command.GetHostSynchronousDataPacketLength(), 0xF1);
  ASSERT_EQ(command.GetHostTotalNumAclDataPackets(), 0xFF02);
  ASSERT_EQ(command.GetHostTotalNumSynchronousDataPackets(), 0xFF03);
}

TEST_F(ControllerTest, send_le_set_event_mask_command) {
  uint64_t new_le_event_mask = test_hci_layer_->event_mask - 1;
  controller_->LeSetEventMask(new_le_event_mask);
  // Send another command to make sure it was applied
  controller_->Reset();
  auto packet = test_hci_layer_->GetCommand(OpCode::RESET);
  ASSERT_EQ(new_le_event_mask, test_hci_layer_->le_event_mask);
}

TEST_F(ControllerTest, is_supported_test) {
  ASSERT_TRUE(controller_->IsSupported(OpCode::INQUIRY));
  ASSERT_TRUE(controller_->IsSupported(OpCode::REJECT_CONNECTION_REQUEST));
  ASSERT_TRUE(controller_->IsSupported(OpCode::ACCEPT_CONNECTION_REQUEST));
  ASSERT_FALSE(controller_->IsSupported(OpCode::LE_REMOVE_ADVERTISING_SET));
  ASSERT_FALSE(controller_->IsSupported(OpCode::LE_CLEAR_ADVERTISING_SETS));
  ASSERT_FALSE(controller_->IsSupported(OpCode::LE_SET_PERIODIC_ADVERTISING_PARAMETERS));
}

TEST_F(Controller055Test, feature_spec_version_055_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 55);
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
}

TEST_F(Controller095Test, feature_spec_version_095_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 95);
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
}

TEST_F(Controller096Test, feature_spec_version_096_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 96);
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
}

TEST_F(ControllerTest, feature_spec_version_098_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 98);
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_FALSE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_TRUE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
}

TEST_F(ControllerTest, feature_spec_version_098_no_dab_test) {
  ASSERT_FALSE(controller_->IsSupported(OpCode::DYNAMIC_AUDIO_BUFFER));
}

TEST_F(ControllerTest, set_dynamic_audio_buffer_time) {
  controller_->SetDabAudioBufferTime(123);
  thread_.GetReactor()->WaitForIdle(std::chrono::seconds(1));
  ASSERT_EQ(0, test_hci_layer_->dynamic_audio_buffer_time);
}

TEST_F(Controller103Test, feature_spec_version_103_dab_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 0x100 + 3);
  ASSERT_FALSE(controller_->GetVendorCapabilities().a2dp_offload_v2_support_);
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_TRUE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_TRUE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
  ASSERT_TRUE(controller_->IsSupported(OpCode::DYNAMIC_AUDIO_BUFFER));
  ASSERT_EQ(controller_->GetDabSupportedCodecs(), kDynamicAudioBufferSupport);
  for (size_t bit = 0; bit < 32; bit++) {
    if (kDynamicAudioBufferSupport & (1u << bit)) {
      ASSERT_GT(controller_->GetDabCodecCapabilities()[bit].maximum_time_ms_, 0) << " bit " << bit;
    } else {
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].maximum_time_ms_, 0);
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].minimum_time_ms_, 0);
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].default_time_ms_, 0);
    }
  }
}

TEST_F(Controller103Test, set_dynamic_audio_buffer_time) {
  controller_->SetDabAudioBufferTime(123);
  thread_.GetReactor()->WaitForIdle(std::chrono::seconds(1));
  ASSERT_EQ(123, test_hci_layer_->dynamic_audio_buffer_time);
}

TEST_F(Controller104Test, feature_spec_version_104_test) {
  ASSERT_EQ(controller_->GetVendorCapabilities().version_supported_, 0x100 + 4);
  if (IS_FLAG_ENABLED(a2dp_offload_codec_extensibility)) {
    ASSERT_TRUE(controller_->GetVendorCapabilities().a2dp_offload_v2_support_);
  } else {
    ASSERT_FALSE(controller_->GetVendorCapabilities().a2dp_offload_v2_support_);
  }
  ASSERT_TRUE(controller_->IsSupported(OpCode::LE_MULTI_ADVT));
  ASSERT_TRUE(controller_->IsSupported(OpCode::CONTROLLER_DEBUG_INFO));
  ASSERT_TRUE(controller_->IsSupported(OpCode::CONTROLLER_A2DP_OPCODE));
  ASSERT_TRUE(controller_->IsSupported(OpCode::DYNAMIC_AUDIO_BUFFER));
  ASSERT_EQ(controller_->GetDabSupportedCodecs(), kDynamicAudioBufferSupport);
  for (size_t bit = 0; bit < 32; bit++) {
    if (kDynamicAudioBufferSupport & (1u << bit)) {
      ASSERT_GT(controller_->GetDabCodecCapabilities()[bit].maximum_time_ms_, 0) << " bit " << bit;
    } else {
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].maximum_time_ms_, 0);
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].minimum_time_ms_, 0);
      ASSERT_EQ(controller_->GetDabCodecCapabilities()[bit].default_time_ms_, 0);
    }
  }
}

std::promise<void> credits1_set;
std::promise<void> credits2_set;

void CheckReceivedCredits(uint16_t handle, uint16_t credits) {
  switch (handle) {
    case (kHandle1):
      ASSERT_EQ(kCredits1, credits);
      credits1_set.set_value();
      break;
    case (kHandle2):
      ASSERT_EQ(kCredits2, credits);
      credits2_set.set_value();
      break;
    default:
      ASSERT_LOG(false, "Unknown handle 0x%0hx with 0x%0hx credits", handle, credits);
  }
}

TEST_F(ControllerTest, aclCreditCallbacksTest) {
  credits1_set = std::promise<void>();
  credits2_set = std::promise<void>();

  auto credits1_set_future = credits1_set.get_future();
  auto credits2_set_future = credits2_set.get_future();

  controller_->RegisterCompletedAclPacketsCallback(client_handler_->Bind(&CheckReceivedCredits));

  test_hci_layer_->IncomingCredit();

  ASSERT_EQ(std::future_status::ready, credits1_set_future.wait_for(2s));
  ASSERT_EQ(std::future_status::ready, credits2_set_future.wait_for(2s));
}

TEST_F(ControllerTest, aclCreditCallbackListenerUnregistered) {
  os::Thread thread("test_thread", os::Thread::Priority::NORMAL);
  os::Handler handler(&thread);
  controller_->RegisterCompletedAclPacketsCallback(handler.Bind(&CheckReceivedCredits));

  handler.Clear();
  handler.WaitUntilStopped(std::chrono::milliseconds(100));
  controller_->UnregisterCompletedAclPacketsCallback();

  test_hci_layer_->IncomingCredit();
}

std::promise<uint64_t> le_rand_set;

void le_rand_callback(uint64_t random) {
  le_rand_set.set_value(random);
}

TEST_F(ControllerTest, leRandTest) {
  le_rand_set = std::promise<uint64_t>();
  auto le_rand_set_future = le_rand_set.get_future();

  controller_->LeRand(common::Bind(le_rand_callback));

  ASSERT_EQ(std::future_status::ready, le_rand_set_future.wait_for(2s));
  ASSERT_EQ(kRandomNumber, le_rand_set_future.get());
}

TEST_F(ControllerTest, Dumpsys) {
  ModuleDumper dumper(STDOUT_FILENO, fake_registry_, title);

  std::string output;
  std::ostringstream oss;
  dumper.DumpState(&output, oss);

  ASSERT_TRUE(output.find("Hci Controller Dumpsys") != std::string::npos);
}

}  // namespace hci
}  // namespace bluetooth
