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

#include "hci/facade/le_scanning_manager_facade.h"

#include <cstdint>
#include <unordered_map>
#include <utility>

#include "blueberry/facade/hci/le_scanning_manager_facade.grpc.pb.h"
#include "blueberry/facade/hci/le_scanning_manager_facade.pb.h"
#include "common/bidi_queue.h"
#include "common/bind.h"
#include "grpc/grpc_event_queue.h"
#include "hci/le_scanning_manager.h"
#include "os/log.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace hci {
namespace facade {

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;
using ::grpc::ServerWriter;
using ::grpc::Status;

using namespace blueberry::facade::hci;

class LeScanningManagerFacadeService : public LeScanningManagerFacade::Service, ScanningCallback {
 public:
  LeScanningManagerFacadeService(LeScanningManager* le_scanning_manager, os::Handler* facade_handler)
      : le_scanning_manager_(le_scanning_manager), facade_handler_(facade_handler) {
    ASSERT(le_scanning_manager_ != nullptr);
    ASSERT(facade_handler_ != nullptr);
    le_scanning_manager_->RegisterScanningCallback(this);
  }

  ::grpc::Status RegisterScanner(
      ::grpc::ServerContext* /* context */,
      const RegisterScannerRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    uint32_t uuid_raw = request->uuid();
    bluetooth::hci::Uuid uuid = bluetooth::hci::Uuid::From32Bit(uuid_raw);
    le_scanning_manager_->RegisterScanner(uuid);
    return ::grpc::Status::OK;
  }

  ::grpc::Status Unregister(
      ::grpc::ServerContext* /* context */,
      const UnregisterRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    le_scanning_manager_->Unregister(request->scanner_id());
    return ::grpc::Status::OK;
  }

  ::grpc::Status Scan(
      ::grpc::ServerContext* /* context */,
      const ScanRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    le_scanning_manager_->Scan(request->start());
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetScanParameters(
      ::grpc::ServerContext* /* context */,
      const SetScanParametersRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    auto scan_type = static_cast<hci::LeScanType>(request->scan_type());
    le_scanning_manager_->SetScanParameters(
        request->scanner_id(), scan_type, request->scan_interval(), request->scan_window());
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchCallbackEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<ScanningCallbackMsg>* writer) override {
    return callback_events_.RunLoop(context, writer);
  }

  ::grpc::Status FetchAdvertisingReports(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<AdvertisingReportMsg>* writer) override {
    return advertising_reports_.RunLoop(context, writer);
  }

  void OnScannerRegistered(
      const bluetooth::hci::Uuid app_uuid, ScannerId /* scanner_id */, ScanningStatus status) {
    ScanningCallbackMsg msg;
    msg.set_message_type(ScanningCallbackMsgType::SCANNER_REGISTERED);
    msg.set_status(static_cast<facade::ScanningStatus>(status));
    msg.set_data(app_uuid.As32Bit());
    callback_events_.OnIncomingEvent(msg);
  };

  void OnSetScannerParameterComplete(ScannerId scanner_id, ScanningStatus status) {
    ScanningCallbackMsg msg;
    msg.set_message_type(ScanningCallbackMsgType::SET_SCANNER_PARAMETER_COMPLETE);
    msg.set_status(static_cast<facade::ScanningStatus>(status));
    msg.set_data(static_cast<uint32_t>(scanner_id));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnScanResult(
      uint16_t /* event_type */,
      uint8_t address_type,
      Address address,
      uint8_t /* primary_phy */,
      uint8_t /* secondary_phy */,
      uint8_t /* advertising_sid */,
      int8_t /* tx_power */,
      int8_t rssi,
      uint16_t /* periodic_advertising_interval */,
      std::vector<uint8_t> advertising_data) {
    AdvertisingReportMsg advertising_report_msg;
    std::vector<LeExtendedAdvertisingResponseRaw> advertisements;
    LeExtendedAdvertisingResponseRaw le_extended_advertising_report;
    le_extended_advertising_report.address_type_ = (DirectAdvertisingAddressType)address_type;
    le_extended_advertising_report.address_ = address;
    le_extended_advertising_report.advertising_data_ = advertising_data;
    le_extended_advertising_report.rssi_ = rssi;
    advertisements.push_back(le_extended_advertising_report);

    auto builder = LeExtendedAdvertisingReportRawBuilder::Create(advertisements);
    std::vector<uint8_t> bytes;
    BitInserter bit_inserter(bytes);
    builder->Serialize(bit_inserter);
    advertising_report_msg.set_event(std::string(bytes.begin(), bytes.end()));
    advertising_reports_.OnIncomingEvent(std::move(advertising_report_msg));
  };
  void OnTrackAdvFoundLost(AdvertisingFilterOnFoundOnLostInfo /* on_found_on_lost_info */){};
  void OnBatchScanReports(
      int /* client_if */,
      int /* status */,
      int /* report_format */,
      int /* num_records */,
      std::vector<uint8_t> /* data */){};
  void OnBatchScanThresholdCrossed(int /* client_if */){};
  void OnTimeout(){};
  void OnFilterEnable(Enable /* enable */, uint8_t /* status */){};
  void OnFilterParamSetup(
      uint8_t /* available_spaces */, ApcfAction /* action */, uint8_t /* status */){};
  void OnFilterConfigCallback(
      ApcfFilterType /* filter_type */,
      uint8_t /* available_spaces */,
      ApcfAction /* action */,
      uint8_t /* status */){};

  void OnPeriodicSyncStarted(
      int /* reg_id */,
      uint8_t /* status */,
      uint16_t /* sync_handle */,
      uint8_t /* advertising_sid */,
      AddressWithType /* address_with_type */,
      uint8_t /* phy */,
      uint16_t /* interval */) override {
    LOG_INFO("OnPeriodicSyncStarted in LeScanningManagerFacadeService");
  };

  void OnPeriodicSyncReport(
      uint16_t /* sync_handle */,
      int8_t /* tx_power */,
      int8_t /* rssi */,
      uint8_t /* status */,
      std::vector<uint8_t> /* data */) override {
    LOG_INFO("OnPeriodicSyncReport in LeScanningManagerFacadeService");
  };

  void OnPeriodicSyncLost(uint16_t /* sync_handle */) override {
    LOG_INFO("OnPeriodicSyncLost in LeScanningManagerFacadeService");
  };

  void OnPeriodicSyncTransferred(
      int /* pa_source */, uint8_t /* status */, Address /* address */) override {
    LOG_INFO("OnPeriodicSyncTransferred in LeScanningManagerFacadeService");
  };

  void OnBigInfoReport(uint16_t /* sync_handle */, bool /* encrypted */) override {
    LOG_INFO("OnBigInfoReport in LeScanningManagerFacadeService");
  };

  LeScanningManager* le_scanning_manager_;
  os::Handler* facade_handler_;
  ::bluetooth::grpc::GrpcEventQueue<AdvertisingReportMsg> advertising_reports_{"advertising reports"};
  ::bluetooth::grpc::GrpcEventQueue<ScanningCallbackMsg> callback_events_{"callback events"};
};

void LeScanningManagerFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<hci::LeScanningManager>();
}

void LeScanningManagerFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new LeScanningManagerFacadeService(GetDependency<hci::LeScanningManager>(), GetHandler());
}

void LeScanningManagerFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* LeScanningManagerFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory LeScanningManagerFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new LeScanningManagerFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
