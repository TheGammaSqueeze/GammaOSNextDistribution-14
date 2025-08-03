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

#include "hci/le_scanning_manager.h"

#include <android_bluetooth_flags.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <algorithm>
#include <chrono>
#include <future>
#include <list>
#include <map>
#include <memory>
#include <mutex>
#include <queue>
#include <vector>

#include "common/bind.h"
#include "hci/acl_manager.h"
#include "hci/address.h"
#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_layer_fake.h"
#include "hci/uuid.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

using ::testing::_;
using ::testing::Eq;

using namespace bluetooth;
using namespace std::chrono_literals;

using packet::kLittleEndian;
using packet::PacketView;
using packet::RawBuilder;

namespace {

// Event type fields.
// TODO(b/315496838): Use a common enum for event type bits.
static constexpr uint16_t kConnectable = 0x1;
static constexpr uint16_t kScannable = 0x2;
static constexpr uint16_t kScanResponse = 0x8;
static constexpr uint16_t kLegacy = 0x10;

hci::AdvertisingPacketContentFilterCommand make_filter(const hci::ApcfFilterType& filter_type) {
  hci::AdvertisingPacketContentFilterCommand filter{};
  filter.filter_type = filter_type;

  switch (filter_type) {
    case hci::ApcfFilterType::AD_TYPE:
    case hci::ApcfFilterType::SERVICE_DATA:
      filter.ad_type = 0x09;
      filter.data = {0x12, 0x34, 0x56, 0x78};
      filter.data_mask = {0xff, 0xff, 0xff, 0xff};
      break;
    case hci::ApcfFilterType::BROADCASTER_ADDRESS:
      filter.address = hci::Address::kEmpty;
      filter.application_address_type = hci::ApcfApplicationAddressType::RANDOM;
      break;
    case hci::ApcfFilterType::SERVICE_UUID:
      filter.uuid = hci::Uuid::From32Bit(0x12345678);
      filter.uuid_mask = hci::Uuid::From32Bit(0xffffffff);
      break;
    case hci::ApcfFilterType::LOCAL_NAME:
      filter.name = {0x01, 0x02, 0x03};
      break;
    case hci::ApcfFilterType::MANUFACTURER_DATA:
      filter.company = 0x12;
      filter.company_mask = 0xff;
      filter.data = {0x12, 0x34, 0x56, 0x78};
      filter.data_mask = {0xff, 0xff, 0xff, 0xff};
      break;
    case hci::ApcfFilterType::TRANSPORT_DISCOVERY_DATA:
      filter.org_id = 0x02;
      filter.tds_flags = 0x01;
      filter.tds_flags_mask = 0xFF;
      filter.meta_data_type = hci::ApcfMetaDataType::WIFI_NAN_HASH;
      filter.meta_data = {0x4B, 0x14, 0x96, 0x96, 0x96, 0x5E, 0xA6, 0x33};
      break;
    default:
      break;
  }
  return filter;
}

hci::LeAdvertisingResponse make_advertising_report() {
  hci::LeAdvertisingResponse report{};
  report.event_type_ = hci::AdvertisingEventType::ADV_DIRECT_IND;
  report.address_type_ = hci::AddressType::PUBLIC_DEVICE_ADDRESS;
  hci::Address::FromString("12:34:56:78:9a:bc", report.address_);
  std::vector<hci::LengthAndData> adv_data{};
  hci::LengthAndData data_item{};
  data_item.data_.push_back(static_cast<uint8_t>(hci::GapDataType::FLAGS));
  data_item.data_.push_back(0x34);
  adv_data.push_back(data_item);
  data_item.data_.push_back(static_cast<uint8_t>(hci::GapDataType::COMPLETE_LOCAL_NAME));
  for (auto octet : {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'}) {
    data_item.data_.push_back(octet);
  }
  adv_data.push_back(data_item);
  report.advertising_data_ = adv_data;
  return report;
}

}  // namespace

namespace bluetooth {
namespace hci {
namespace {

class TestController : public Controller {
 public:
  bool IsSupported(OpCode op_code) const override {
    return supported_opcodes_.count(op_code) == 1;
  }

  void AddSupported(OpCode op_code) {
    supported_opcodes_.insert(op_code);
  }

  bool SupportsBleExtendedAdvertising() const override {
    return support_ble_extended_advertising_;
  }

  void SetBleExtendedAdvertisingSupport(bool support) {
    support_ble_extended_advertising_ = support;
  }

  bool SupportsBlePeriodicAdvertisingSyncTransferSender() const override {
    return support_ble_periodic_advertising_sync_transfer_;
  }

  void SetBlePeriodicAdvertisingSyncTransferSenderSupport(bool support) {
    support_ble_periodic_advertising_sync_transfer_ = support;
  }

 protected:
  void Start() override {}
  void Stop() override {}
  void ListDependencies(ModuleList* /* list */) const {}

 private:
  std::set<OpCode> supported_opcodes_{};
  bool support_ble_extended_advertising_ = false;
  bool support_ble_periodic_advertising_sync_transfer_ = false;
};

class TestLeAddressManager : public LeAddressManager {
 public:
  TestLeAddressManager(
      common::Callback<void(std::unique_ptr<CommandBuilder>)> enqueue_command,
      os::Handler* handler,
      Address public_address,
      uint8_t accept_list_size,
      uint8_t resolving_list_size)
      : LeAddressManager(enqueue_command, handler, public_address, accept_list_size, resolving_list_size) {}

  AddressPolicy Register(LeAddressManagerCallback* callback) override {
    client_ = callback;
    test_client_state_ = RESUMED;
    return AddressPolicy::USE_STATIC_ADDRESS;
  }

  void Unregister(LeAddressManagerCallback* /* callback */) override {
    if (!ignore_unregister_for_testing) {
      client_ = nullptr;
    }
    test_client_state_ = UNREGISTERED;
  }

  void AckPause(LeAddressManagerCallback* /* callback */) override {
    test_client_state_ = PAUSED;
  }

  void AckResume(LeAddressManagerCallback* /* callback */) override {
    test_client_state_ = RESUMED;
  }

  LeAddressManagerCallback* client_;
  bool ignore_unregister_for_testing = false;
  enum TestClientState {
    UNREGISTERED,
    PAUSED,
    RESUMED,
  };
  TestClientState test_client_state_ = UNREGISTERED;
};

class TestAclManager : public AclManager {
 public:
  LeAddressManager* GetLeAddressManager() override {
    return test_le_address_manager_;
  }

 protected:
  void Start() override {
    thread_ = new os::Thread("thread", os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);
    Address address({0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
    test_le_address_manager_ = new TestLeAddressManager(
        common::Bind(&TestAclManager::enqueue_command, common::Unretained(this)), handler_, address, 0x3F, 0x3F);
  }

  void Stop() override {
    delete test_le_address_manager_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void ListDependencies(ModuleList* /* list */) const {}

  void SetRandomAddress(Address /* address */) {}

  void enqueue_command(std::unique_ptr<CommandBuilder> /* command_packet */){};

 private:
  os::Thread* thread_;
  os::Handler* handler_;
  TestLeAddressManager* test_le_address_manager_;
};

class MockCallbacks : public bluetooth::hci::ScanningCallback {
 public:
  MOCK_METHOD(
      void,
      OnScannerRegistered,
      (const bluetooth::hci::Uuid app_uuid, ScannerId scanner_id, ScanningStatus status),
      (override));
  MOCK_METHOD(void, OnSetScannerParameterComplete, (ScannerId scanner_id, ScanningStatus status), (override));
  MOCK_METHOD(
      void,
      OnScanResult,
      (uint16_t event_type,
       uint8_t address_type,
       Address address,
       uint8_t primary_phy,
       uint8_t secondary_phy,
       uint8_t advertising_sid,
       int8_t tx_power,
       int8_t rssi,
       uint16_t periodic_advertising_interval,
       std::vector<uint8_t> advertising_data),
      (override));
  MOCK_METHOD(
      void,
      OnTrackAdvFoundLost,
      (bluetooth::hci::AdvertisingFilterOnFoundOnLostInfo on_found_on_lost_info),
      (override));
  MOCK_METHOD(
      void,
      OnBatchScanReports,
      (int client_if, int status, int report_format, int num_records, std::vector<uint8_t> data),
      (override));
  MOCK_METHOD(void, OnBatchScanThresholdCrossed, (int client_if), (override));
  MOCK_METHOD(void, OnTimeout, (), (override));
  MOCK_METHOD(void, OnFilterEnable, (Enable enable, uint8_t status), (override));
  MOCK_METHOD(void, OnFilterParamSetup, (uint8_t available_spaces, ApcfAction action, uint8_t status), (override));
  MOCK_METHOD(
      void,
      OnFilterConfigCallback,
      (ApcfFilterType filter_type, uint8_t available_spaces, ApcfAction action, uint8_t status),
      (override));
  MOCK_METHOD(void, OnPeriodicSyncStarted, (int, uint8_t, uint16_t, uint8_t, AddressWithType, uint8_t, uint16_t));
  MOCK_METHOD(void, OnPeriodicSyncReport, (uint16_t, int8_t, int8_t, uint8_t, std::vector<uint8_t>));
  MOCK_METHOD(void, OnPeriodicSyncLost, (uint16_t));
  MOCK_METHOD(void, OnPeriodicSyncTransferred, (int, uint8_t, Address));
  MOCK_METHOD(void, OnBigInfoReport, (uint16_t, bool));
} mock_callbacks_;

class LeScanningManagerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_hci_layer_ = new HciLayerFake;  // Ownership is transferred to registry
    test_controller_ = new TestController;
    test_acl_manager_ = new TestAclManager;
    fake_registry_.InjectTestModule(&HciLayer::Factory, test_hci_layer_);
    fake_registry_.InjectTestModule(&Controller::Factory, test_controller_);
    fake_registry_.InjectTestModule(&AclManager::Factory, test_acl_manager_);
    client_handler_ = fake_registry_.GetTestModuleHandler(&HciLayer::Factory);
    ASSERT_TRUE(client_handler_ != nullptr);
  }

  void TearDown() override {
    sync_client_handler();
    if (fake_registry_.IsStarted<LeScanningManager>()) {
      fake_registry_.SynchronizeModuleHandler(&LeScanningManager::Factory, std::chrono::milliseconds(20));
    }
    fake_registry_.StopAll();
  }

  void start_le_scanning_manager() {
    fake_registry_.Start<LeScanningManager>(&thread_);
    le_scanning_manager =
        static_cast<LeScanningManager*>(fake_registry_.GetModuleUnderTest(&LeScanningManager::Factory));
    le_scanning_manager->RegisterScanningCallback(&mock_callbacks_);
    sync_client_handler();
  }

  void sync_client_handler() {
    ASSERT(thread_.GetReactor()->WaitForIdle(std::chrono::seconds(2)));
  }

  TestModuleRegistry fake_registry_;
  HciLayerFake* test_hci_layer_ = nullptr;
  TestController* test_controller_ = nullptr;
  TestAclManager* test_acl_manager_ = nullptr;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  LeScanningManager* le_scanning_manager = nullptr;
  os::Handler* client_handler_ = nullptr;

  MockCallbacks mock_callbacks_;
};

class LeScanningManagerAndroidHciTest : public LeScanningManagerTest {
 protected:
  void SetUp() override {
    LeScanningManagerTest::SetUp();
    test_controller_->AddSupported(OpCode::LE_EXTENDED_SCAN_PARAMS);
    test_controller_->AddSupported(OpCode::LE_ADV_FILTER);
    test_controller_->AddSupported(OpCode::LE_BATCH_SCAN);
    test_controller_->SetBlePeriodicAdvertisingSyncTransferSenderSupport(true);
    start_le_scanning_manager();
    ASSERT_TRUE(fake_registry_.IsStarted(&HciLayer::Factory));


    ASSERT_EQ(OpCode::LE_ADV_FILTER, test_hci_layer_->GetCommand().GetOpCode());
    test_hci_layer_->IncomingEvent(
        LeAdvFilterReadExtendedFeaturesCompleteBuilder::Create(1, ErrorCode::SUCCESS, 0x01, 0x01));
  }

  void TearDown() override {
    LeScanningManagerTest::TearDown();
  }
};

class LeScanningManagerExtendedTest : public LeScanningManagerTest {
 protected:
  void SetUp() override {
    LeScanningManagerTest::SetUp();
    test_controller_->AddSupported(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS);
    test_controller_->AddSupported(OpCode::LE_SET_EXTENDED_SCAN_ENABLE);
    test_controller_->SetBleExtendedAdvertisingSupport(true);
    start_le_scanning_manager();
  }
};

TEST_F(LeScanningManagerTest, startup_teardown) {}

TEST_F(LeScanningManagerTest, start_scan_test) {
  start_le_scanning_manager();

  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  LeAdvertisingResponse report = make_advertising_report();
  EXPECT_CALL(mock_callbacks_, OnScanResult);

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({report}));
}

TEST_F(LeScanningManagerTest, legacy_adv_scan_ind_report_with_scan_response) {
  start_le_scanning_manager();

  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  LeAdvertisingResponse report = make_advertising_report();
  // Scannable & not connectable!
  report.event_type_ = AdvertisingEventType::ADV_SCAN_IND;

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({report}));

  LeAdvertisingResponse scan_response = make_advertising_report();
  scan_response.event_type_ = AdvertisingEventType::SCAN_RESPONSE;

  // The 'connectable' bit should NOT be set.
  uint16_t extended_event_type = kLegacy | kScannable | kScanResponse;
  if (!IS_FLAG_ENABLED(fix_nonconnectable_scannable_advertisement)) {
    extended_event_type |= kConnectable;
  }
  EXPECT_CALL(mock_callbacks_, OnScanResult(extended_event_type, _, _, _, _, _, _, _, _, _));

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({scan_response}));
}

TEST_F(LeScanningManagerTest, legacy_adv_ind_report_with_scan_response) {
  start_le_scanning_manager();

  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  LeAdvertisingResponse report = make_advertising_report();
  // Scannable & connectable!
  report.event_type_ = AdvertisingEventType::ADV_IND;

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({report}));

  LeAdvertisingResponse scan_response = make_advertising_report();
  scan_response.event_type_ = AdvertisingEventType::SCAN_RESPONSE;

  uint16_t extended_event_type = kLegacy | kScannable | kConnectable | kScanResponse;
  EXPECT_CALL(mock_callbacks_, OnScanResult(extended_event_type, _, _, _, _, _, _, _, _, _));

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({scan_response}));
}

TEST_F(LeScanningManagerTest, is_ad_type_filter_supported_false_test) {
  start_le_scanning_manager();
  ASSERT_TRUE(fake_registry_.IsStarted(&HciLayer::Factory));
  ASSERT_FALSE(le_scanning_manager->IsAdTypeFilterSupported());
}

TEST_F(LeScanningManagerTest, scan_filter_add_ad_type_not_supported_test) {
  start_le_scanning_manager();
  ASSERT_TRUE(fake_registry_.IsStarted(&HciLayer::Factory));

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(hci::ApcfFilterType::AD_TYPE));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
}

TEST_F(LeScanningManagerAndroidHciTest, startup_teardown) {}

TEST_F(LeScanningManagerAndroidHciTest, start_scan_test) {
  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_EXTENDED_SCAN_PARAMS, test_hci_layer_->GetCommand().GetOpCode());

  LeAdvertisingResponse report = make_advertising_report();

  EXPECT_CALL(mock_callbacks_, OnScanResult);

  test_hci_layer_->IncomingLeMetaEvent(LeAdvertisingReportBuilder::Create({report}));
}

TEST_F(LeScanningManagerAndroidHciTest, is_ad_type_filter_supported_true_test) {
  sync_client_handler();
  client_handler_->Post(common::BindOnce(
      [](LeScanningManager* le_scanning_manager) { ASSERT_TRUE(le_scanning_manager->IsAdTypeFilterSupported()); },
      le_scanning_manager));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_enable_test) {
  le_scanning_manager->ScanFilterEnable(true);
  sync_client_handler();

  EXPECT_CALL(mock_callbacks_, OnFilterEnable);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, Enable::ENABLED));
  sync_client_handler();
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_parameter_test) {

  AdvertisingFilterParameter advertising_filter_parameter{};
  advertising_filter_parameter.delivery_mode = DeliveryMode::IMMEDIATE;
  le_scanning_manager->ScanFilterParameterSetup(ApcfAction::ADD, 0x01, advertising_filter_parameter);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view = LeAdvFilterSetFilteringParametersView::Create(
      LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::SET_FILTERING_PARAMETERS);

  EXPECT_CALL(mock_callbacks_, OnFilterParamSetup);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterSetFilteringParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
  sync_client_handler();
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_broadcaster_address_test) {

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(ApcfFilterType::BROADCASTER_ADDRESS));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view =
      LeAdvFilterBroadcasterAddressView::Create(LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::BROADCASTER_ADDRESS);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterBroadcasterAddressCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_service_uuid_test) {

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(ApcfFilterType::SERVICE_UUID));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view =
      LeAdvFilterServiceUuidView::Create(LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::SERVICE_UUID);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterServiceUuidCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_local_name_test) {

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(ApcfFilterType::LOCAL_NAME));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view =
      LeAdvFilterLocalNameView::Create(LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::LOCAL_NAME);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterLocalNameCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_manufacturer_data_test) {

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(ApcfFilterType::MANUFACTURER_DATA));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view =
      LeAdvFilterManufacturerDataView::Create(LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::MANUFACTURER_DATA);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterManufacturerDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_service_data_test) {

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(hci::ApcfFilterType::SERVICE_DATA));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view =
      LeAdvFilterServiceDataView::Create(LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));
  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::SERVICE_DATA);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterServiceDataCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_transport_discovery_data_test) {
  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  filters.push_back(make_filter(hci::ApcfFilterType::TRANSPORT_DISCOVERY_DATA));
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  auto commandView = test_hci_layer_->GetCommand();
  ASSERT_EQ(OpCode::LE_ADV_FILTER, commandView.GetOpCode());
  auto filter_command_view = LeAdvFilterTransportDiscoveryDataView::Create(
      LeAdvFilterView::Create(LeScanningCommandView::Create(commandView)));

  ASSERT_TRUE(filter_command_view.IsValid());
  ASSERT_EQ(filter_command_view.GetApcfOpcode(), ApcfOpcode::TRANSPORT_DISCOVERY_DATA);

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(LeAdvFilterTransportDiscoveryDataCompleteBuilder::Create(
      uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, scan_filter_add_ad_type_test) {
  sync_client_handler();
  client_handler_->Post(common::BindOnce(
      [](LeScanningManager* le_scanning_manager) { ASSERT_TRUE(le_scanning_manager->IsAdTypeFilterSupported()); },
      le_scanning_manager));

  std::vector<AdvertisingPacketContentFilterCommand> filters = {};
  hci::AdvertisingPacketContentFilterCommand filter = make_filter(hci::ApcfFilterType::AD_TYPE);
  filters.push_back(filter);
  le_scanning_manager->ScanFilterAdd(0x01, filters);
  sync_client_handler();

  EXPECT_CALL(mock_callbacks_, OnFilterConfigCallback);
  test_hci_layer_->IncomingEvent(
      LeAdvFilterADTypeCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS, ApcfAction::ADD, 0x0a));
}

TEST_F(LeScanningManagerAndroidHciTest, read_batch_scan_result) {
  le_scanning_manager->BatchScanConifgStorage(100, 0, 95, 0x00);
  sync_client_handler();
  ASSERT_EQ(OpCode::LE_BATCH_SCAN, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeBatchScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_BATCH_SCAN, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeBatchScanSetStorageParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Enable batch scan

  le_scanning_manager->BatchScanEnable(BatchScanMode::FULL, 2400, 2400, BatchScanDiscardRule::OLDEST);
  ASSERT_EQ(OpCode::LE_BATCH_SCAN, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeBatchScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Read batch scan data

  le_scanning_manager->BatchScanReadReport(0x01, BatchScanMode::FULL);
  ASSERT_EQ(OpCode::LE_BATCH_SCAN, test_hci_layer_->GetCommand().GetOpCode());

  // We will send read command while num_of_record != 0
  std::vector<uint8_t> raw_data = {0x5c, 0x1f, 0xa2, 0xc3, 0x63, 0x5d, 0x01, 0xf5, 0xb3, 0x5e, 0x00, 0x0c, 0x02,
                                   0x01, 0x02, 0x05, 0x09, 0x6d, 0x76, 0x38, 0x76, 0x02, 0x0a, 0xf5, 0x00};

  test_hci_layer_->IncomingEvent(LeBatchScanReadResultParametersCompleteRawBuilder::Create(
      uint8_t{1}, ErrorCode::SUCCESS, BatchScanDataRead::FULL_MODE_DATA, 1, raw_data));
  ASSERT_EQ(OpCode::LE_BATCH_SCAN, test_hci_layer_->GetCommand().GetOpCode());

  // OnBatchScanReports will be trigger when num_of_record == 0
  EXPECT_CALL(mock_callbacks_, OnBatchScanReports);
  test_hci_layer_->IncomingEvent(LeBatchScanReadResultParametersCompleteRawBuilder::Create(
      uint8_t{1}, ErrorCode::SUCCESS, BatchScanDataRead::FULL_MODE_DATA, 0, {}));
}

TEST_F(LeScanningManagerAndroidHciTest, start_sync_test) {
  Address address;
  const uint16_t handle = 0x0001;
  const uint16_t service_data = 0x0000;
  const uint16_t sync_handle = 0x0002;
  const int pa_source = 3;

  Address::FromString("12:34:56:78:9a:bc", address);

  le_scanning_manager->TransferSync(address, handle, service_data, sync_handle, pa_source);
  sync_client_handler();

  ASSERT_EQ(
      OpCode::LE_PERIODIC_ADVERTISING_SYNC_TRANSFER, test_hci_layer_->GetCommand().GetOpCode());
}

TEST_F(LeScanningManagerAndroidHciTest, start_sync_invalid_handle_test) {
  Address address;
  const uint16_t handle = 0xFFFF;
  const uint16_t service_data = 0x0000;
  const uint16_t sync_handle = 0x0002;
  const int pa_source = 3;

  Address::FromString("12:34:56:78:9a:bc", address);

  EXPECT_CALL(
      mock_callbacks_,
      OnPeriodicSyncTransferred(
          pa_source, static_cast<int>(ErrorCode::UNKNOWN_CONNECTION), address));
  le_scanning_manager->TransferSync(address, handle, service_data, sync_handle, pa_source);
  sync_client_handler();
}

TEST_F(LeScanningManagerAndroidHciTest, set_info_test) {
  Address address;
  const uint16_t handle = 0x0001;
  const uint16_t service_data = 0x0000;
  const uint16_t sync_handle = 0x0002;
  const int pa_source = 3;

  Address::FromString("12:34:56:78:9a:bc", address);

  le_scanning_manager->TransferSetInfo(address, handle, service_data, sync_handle, pa_source);
  sync_client_handler();

  ASSERT_EQ(
      OpCode::LE_PERIODIC_ADVERTISING_SET_INFO_TRANSFER, test_hci_layer_->GetCommand().GetOpCode());
}

TEST_F(LeScanningManagerAndroidHciTest, set_info_invalid_handle_test) {
  Address address;
  const uint16_t handle = 0xFFFF;
  const uint16_t service_data = 0x0000;
  const uint16_t sync_handle = 0x0002;
  const int pa_source = 3;

  Address::FromString("12:34:56:78:9a:bc", address);

  EXPECT_CALL(
      mock_callbacks_,
      OnPeriodicSyncTransferred(
          pa_source, static_cast<int>(ErrorCode::UNKNOWN_CONNECTION), address));
  le_scanning_manager->TransferSetInfo(address, handle, service_data, sync_handle, pa_source);
  sync_client_handler();
}

TEST_F(LeScanningManagerExtendedTest, startup_teardown) {}

TEST_F(LeScanningManagerExtendedTest, start_scan_test) {
  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  LeExtendedAdvertisingResponse report{};
  report.connectable_ = 1;
  report.scannable_ = 0;
  report.address_type_ = DirectAdvertisingAddressType::PUBLIC_DEVICE_ADDRESS;
  Address::FromString("12:34:56:78:9a:bc", report.address_);
  std::vector<LengthAndData> adv_data{};
  LengthAndData data_item{};
  data_item.data_.push_back(static_cast<uint8_t>(GapDataType::FLAGS));
  data_item.data_.push_back(0x34);
  adv_data.push_back(data_item);
  data_item.data_.push_back(static_cast<uint8_t>(GapDataType::COMPLETE_LOCAL_NAME));
  for (auto octet : {'r', 'a', 'n', 'd', 'o', 'm', ' ', 'd', 'e', 'v', 'i', 'c', 'e'}) {
    data_item.data_.push_back(octet);
  }
  adv_data.push_back(data_item);

  report.advertising_data_ = adv_data;

  EXPECT_CALL(mock_callbacks_, OnScanResult);

  test_hci_layer_->IncomingLeMetaEvent(LeExtendedAdvertisingReportBuilder::Create({report}));
}

TEST_F(LeScanningManagerExtendedTest, start_scan_on_resume_conflict_test) {
  TestLeAddressManager* test_le_address_manager =
      (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();

  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // Pause scan
  test_le_address_manager->client_->OnPause();
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Disable scan
  le_scanning_manager->Scan(false);
  test_hci_layer_->AssertNoQueuedCommand();

  // Enable Scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Ensure there is no double enable commands on resume
  test_le_address_manager->client_->OnResume();
  sync_client_handler();
  test_hci_layer_->AssertNoQueuedCommand();
}

TEST_F(LeScanningManagerExtendedTest, on_pause_on_resume_test) {
  TestLeAddressManager* test_le_address_manager =
      (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();

  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // Pause scan
  test_le_address_manager->client_->OnPause();
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Ensure scan is resumed (enabled)
  test_le_address_manager->client_->OnResume();
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(
      LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
}

TEST_F(LeScanningManagerExtendedTest, ignore_on_pause_on_resume_after_unregistered) {
  TestLeAddressManager* test_le_address_manager = (TestLeAddressManager*)test_acl_manager_->GetLeAddressManager();
  test_le_address_manager->ignore_unregister_for_testing = true;

  // Register LeAddressManager
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // Unregister LeAddressManager
  le_scanning_manager->Scan(false);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  sync_client_handler();

  // Unregistered client should ignore OnPause/OnResume
  ASSERT_NE(test_le_address_manager->client_, nullptr);
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
  test_le_address_manager->client_->OnPause();
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
  test_le_address_manager->client_->OnResume();
  ASSERT_EQ(test_le_address_manager->test_client_state_, TestLeAddressManager::TestClientState::UNREGISTERED);
}

TEST_F(LeScanningManagerExtendedTest, drop_insignificant_bytes_test) {
  // Enable scan
  le_scanning_manager->Scan(true);
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_PARAMETERS, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanParametersCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));
  ASSERT_EQ(OpCode::LE_SET_EXTENDED_SCAN_ENABLE, test_hci_layer_->GetCommand().GetOpCode());
  test_hci_layer_->IncomingEvent(LeSetExtendedScanEnableCompleteBuilder::Create(uint8_t{1}, ErrorCode::SUCCESS));

  // Prepare advertisement report
  LeExtendedAdvertisingResponse advertisement_report{};
  advertisement_report.connectable_ = 1;
  advertisement_report.scannable_ = 1;
  advertisement_report.address_type_ = DirectAdvertisingAddressType::PUBLIC_DEVICE_ADDRESS;
  Address::FromString("12:34:56:78:9a:bc", advertisement_report.address_);
  std::vector<LengthAndData> adv_data{};
  LengthAndData flags_data{};
  flags_data.data_.push_back(static_cast<uint8_t>(GapDataType::FLAGS));
  flags_data.data_.push_back(0x34);
  adv_data.push_back(flags_data);
  LengthAndData name_data{};
  name_data.data_.push_back(static_cast<uint8_t>(GapDataType::COMPLETE_LOCAL_NAME));
  for (auto octet : "random device") {
    name_data.data_.push_back(octet);
  }
  adv_data.push_back(name_data);
  for (int i = 0; i != 5; ++i) {
    adv_data.push_back({});  // pad with a few insigificant zeros
  }
  advertisement_report.advertising_data_ = adv_data;

  // Prepare scan response report
  auto scan_response_report = advertisement_report;
  scan_response_report.scan_response_ = true;
  LengthAndData extra_data{};
  extra_data.data_.push_back(static_cast<uint8_t>(GapDataType::MANUFACTURER_SPECIFIC_DATA));
  for (auto octet : "manufacturer specific") {
    extra_data.data_.push_back(octet);
  }
  adv_data = {extra_data};
  for (int i = 0; i != 5; ++i) {
    adv_data.push_back({});  // pad with a few insigificant zeros
  }
  scan_response_report.advertising_data_ = adv_data;

  // We expect the two reports to be concatenated, excluding the zero-padding
  auto result = std::vector<uint8_t>();
  packet::BitInserter it(result);
  flags_data.Serialize(it);
  name_data.Serialize(it);
  extra_data.Serialize(it);
  EXPECT_CALL(mock_callbacks_, OnScanResult(_, _, _, _, _, _, _, _, _, result));

  // Send both reports
  test_hci_layer_->IncomingLeMetaEvent(LeExtendedAdvertisingReportBuilder::Create({advertisement_report}));
  test_hci_layer_->IncomingLeMetaEvent(LeExtendedAdvertisingReportBuilder::Create({scan_response_report}));
}

}  // namespace
}  // namespace hci
}  // namespace bluetooth
