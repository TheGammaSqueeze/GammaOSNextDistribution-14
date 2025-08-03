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

#include "hci/facade/le_advertising_manager_facade.h"

#include <cstdint>
#include <unordered_map>
#include <utility>

#include "blueberry/facade/hci/le_advertising_manager_facade.grpc.pb.h"
#include "blueberry/facade/hci/le_advertising_manager_facade.pb.h"
#include "common/bidi_queue.h"
#include "common/bind.h"
#include "grpc/grpc_event_queue.h"
#include "hardware/ble_advertiser.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/le_advertising_manager.h"
#include "os/log.h"

namespace bluetooth {
namespace hci {
namespace facade {

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;
using ::grpc::ServerWriter;
using ::grpc::Status;

using ::blueberry::facade::BluetoothAddress;
using ::blueberry::facade::BluetoothAddressTypeEnum;
using ::blueberry::facade::BluetoothOwnAddressTypeEnum;
using ::blueberry::facade::hci::AdvertisingConfig;
using ::blueberry::facade::hci::ExtendedAdvertisingConfig;
using ::blueberry::facade::hci::GapDataMsg;
using ::blueberry::facade::hci::PeriodicAdvertisingParameters;

hci::GapData GapDataFromProto(const GapDataMsg& gap_data_proto) {
  hci::GapData gap_data;
  auto data_copy = std::make_shared<std::vector<uint8_t>>(gap_data_proto.data().begin(), gap_data_proto.data().end());
  packet::PacketView<packet::kLittleEndian> packet(data_copy);
  auto after = hci::GapData::Parse(&gap_data, packet.begin());
  ASSERT(after != packet.begin());
  return gap_data;
}

bool AdvertisingConfigFromProto(
    const AdvertisingConfig& config_proto, hci::AdvertisingConfig* config) {
  for (const auto& elem : config_proto.advertisement()) {
    config->advertisement.push_back(GapDataFromProto(elem));
  }

  for (const auto& elem : config_proto.scan_response()) {
    config->scan_response.push_back(GapDataFromProto(elem));
  }

  if (config_proto.interval_min() > UINT16_MAX || config_proto.interval_min() < 0) {
    LOG_WARN("Bad interval_min: %d", config_proto.interval_min());
    return false;
  }
  config->interval_min = static_cast<uint16_t>(config_proto.interval_min());

  if (config_proto.interval_max() > UINT16_MAX || config_proto.interval_max() < 0) {
    LOG_WARN("Bad interval_max: %d", config_proto.interval_max());
    return false;
  }
  config->interval_max = static_cast<uint16_t>(config_proto.interval_max());

  config->advertising_type = static_cast<hci::AdvertisingType>(config_proto.advertising_type());

  config->requested_advertiser_address_type =
      config_proto.own_address_type() == BluetoothOwnAddressTypeEnum::USE_PUBLIC_DEVICE_ADDRESS
          ? AdvertiserAddressType::PUBLIC
          : AdvertiserAddressType::RESOLVABLE_RANDOM;

  config->peer_address_type = static_cast<::bluetooth::hci::PeerAddressType>(config_proto.peer_address_type());

  hci::Address::FromString(config_proto.peer_address().address(), config->peer_address);

  if (config_proto.channel_map() > UINT8_MAX || config_proto.channel_map() < 0) {
    LOG_WARN("Bad channel_map: %d", config_proto.channel_map());
    return false;
  }
  config->channel_map = static_cast<uint8_t>(config_proto.channel_map());

  if (config_proto.tx_power() > UINT8_MAX || config_proto.tx_power() < 0) {
    LOG_WARN("Bad tx_power: %d", config_proto.tx_power());
    return false;
  }

  config->filter_policy = static_cast<hci::AdvertisingFilterPolicy>(config_proto.filter_policy());

  config->tx_power = static_cast<uint8_t>(config_proto.tx_power());

  config->legacy_pdus = true;

  auto advertising_type = static_cast<::bluetooth::hci::AdvertisingType>(config_proto.advertising_type());

  switch (advertising_type) {
    case AdvertisingType::ADV_IND: {
      config->connectable = true;
      config->scannable = true;
    } break;
    case AdvertisingType::ADV_DIRECT_IND_HIGH: {
      config->connectable = true;
      config->directed = true;
      config->high_duty_directed_connectable = true;
    } break;
    case AdvertisingType::ADV_SCAN_IND: {
      config->scannable = true;
    } break;
    case AdvertisingType::ADV_NONCONN_IND: {
    } break;
    case AdvertisingType::ADV_DIRECT_IND_LOW: {
      config->directed = true;
      config->connectable = true;
    } break;
  }

  return true;
}

bool ExtendedAdvertisingConfigFromProto(
    const ExtendedAdvertisingConfig& config_proto, hci::AdvertisingConfig* config) {
  if (!AdvertisingConfigFromProto(config_proto.advertising_config(), config)) {
    LOG_WARN("Error parsing advertising config");
    return false;
  }
  config->connectable = config_proto.connectable();
  config->scannable = config_proto.scannable();
  config->directed = config_proto.directed();
  config->high_duty_directed_connectable = config_proto.high_duty_directed_connectable();
  config->legacy_pdus = config_proto.legacy_pdus();
  config->anonymous = config_proto.anonymous();
  config->include_tx_power = config_proto.include_tx_power();
  config->use_le_coded_phy = config_proto.use_le_coded_phy();
  config->secondary_max_skip = static_cast<uint8_t>(config_proto.secondary_max_skip());
  config->secondary_advertising_phy = static_cast<hci::SecondaryPhyType>(config_proto.secondary_advertising_phy());
  config->sid = static_cast<uint8_t>(config_proto.sid());
  config->enable_scan_request_notifications =
      static_cast<hci::Enable>(config_proto.enable_scan_request_notifications());
  return true;
}

bool PeriodicAdvertisingParametersFromProto(
    const PeriodicAdvertisingParameters& config_proto, hci::PeriodicAdvertisingParameters* config) {
  if (config_proto.min_interval() > UINT16_MAX || config_proto.min_interval() < 0) {
    LOG_WARN("Bad interval_min: %d", config_proto.min_interval());
    return false;
  }
  config->min_interval = static_cast<uint16_t>(config_proto.min_interval());
  if (config_proto.max_interval() > UINT16_MAX || config_proto.max_interval() < 0) {
    LOG_WARN("Bad interval_max: %d", config_proto.max_interval());
    return false;
  }
  config->max_interval = static_cast<uint16_t>(config_proto.max_interval());
  config->properties =
      static_cast<hci::PeriodicAdvertisingParameters::AdvertisingProperty>(config_proto.advertising_property());
  return true;
}

class LeAdvertiser {
 public:
  LeAdvertiser(hci::AdvertisingConfig config) : config_(std::move(config)) {}

  void ScanCallback(Address /* address */, AddressType /* address_type */) {}

  void TerminatedCallback(ErrorCode /* error_code */, uint8_t, uint8_t) {}

  hci::AdvertiserId GetAdvertiserId() {
    return id_;
  }

  void SetAdvertiserId(hci::AdvertiserId id) {
    id_ = id;
  }

 private:
  hci::AdvertiserId id_ = LeAdvertisingManager::kInvalidId;
  hci::AdvertisingConfig config_;
};

using ::blueberry::facade::hci::AddressMsg;
using ::blueberry::facade::hci::AdvertisingCallbackMsg;
using ::blueberry::facade::hci::AdvertisingCallbackMsgType;
using ::blueberry::facade::hci::AdvertisingStatus;
using ::blueberry::facade::hci::CreateAdvertiserRequest;
using ::blueberry::facade::hci::CreateAdvertiserResponse;
using ::blueberry::facade::hci::EnableAdvertiserRequest;
using ::blueberry::facade::hci::EnablePeriodicAdvertisingRequest;
using ::blueberry::facade::hci::ExtendedCreateAdvertiserRequest;
using ::blueberry::facade::hci::ExtendedCreateAdvertiserResponse;
using ::blueberry::facade::hci::GetNumberOfAdvertisingInstancesResponse;
using ::blueberry::facade::hci::GetOwnAddressRequest;
using ::blueberry::facade::hci::LeAdvertisingManagerFacade;
using ::blueberry::facade::hci::RemoveAdvertiserRequest;
using ::blueberry::facade::hci::SetDataRequest;
using ::blueberry::facade::hci::SetParametersRequest;
using ::blueberry::facade::hci::SetPeriodicDataRequest;
using ::blueberry::facade::hci::SetPeriodicParametersRequest;

class LeAdvertisingManagerFacadeService : public LeAdvertisingManagerFacade::Service, AdvertisingCallback {
 public:
  LeAdvertisingManagerFacadeService(LeAdvertisingManager* le_advertising_manager, os::Handler* facade_handler)
      : le_advertising_manager_(le_advertising_manager), facade_handler_(facade_handler) {
    ASSERT(le_advertising_manager_ != nullptr);
    ASSERT(facade_handler_ != nullptr);
    le_advertising_manager_->RegisterAdvertisingCallback(this);
  }

  ::grpc::Status CreateAdvertiser(
      ::grpc::ServerContext* /* context */,
      const CreateAdvertiserRequest* request,
      CreateAdvertiserResponse* response) override {
    hci::AdvertisingConfig config = {};
    if (!AdvertisingConfigFromProto(request->config(), &config)) {
      LOG_WARN("Error parsing advertising config %s", request->SerializeAsString().c_str());
      response->set_advertiser_id(LeAdvertisingManager::kInvalidId);
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Error while parsing advertising config");
    }
    LeAdvertiser le_advertiser(config);

    pending_advertiser_id_ = std::promise<AdvertiserId>();
    auto future = pending_advertiser_id_->get_future();
    le_advertising_manager_->ExtendedCreateAdvertiser(
        kAdvertiserClientIdJni,
        0,
        config,
        common::Bind(&LeAdvertiser::ScanCallback, common::Unretained(&le_advertiser)),
        common::Bind(&LeAdvertiser::TerminatedCallback, common::Unretained(&le_advertiser)),
        0,
        0,
        facade_handler_);

    auto advertiser_id = future.get();
    if (advertiser_id != LeAdvertisingManager::kInvalidId) {
      le_advertiser.SetAdvertiserId(advertiser_id);
      le_advertisers_.push_back(le_advertiser);
    } else {
      LOG_WARN("Failed to create advertiser");
    }
    response->set_advertiser_id(advertiser_id);
    return ::grpc::Status::OK;
  }

  ::grpc::Status ExtendedCreateAdvertiser(
      ::grpc::ServerContext* /* context */,
      const ExtendedCreateAdvertiserRequest* request,
      ExtendedCreateAdvertiserResponse* response) override {
    hci::AdvertisingConfig config = {};
    if (!ExtendedAdvertisingConfigFromProto(request->config(), &config)) {
      LOG_WARN("Error parsing advertising config %s", request->SerializeAsString().c_str());
      response->set_advertiser_id(LeAdvertisingManager::kInvalidId);
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Error while parsing advertising config");
    }
    LeAdvertiser le_advertiser(config);
    pending_advertiser_id_ = std::promise<AdvertiserId>();
    auto future = pending_advertiser_id_->get_future();
    le_advertising_manager_->ExtendedCreateAdvertiser(
        kAdvertiserClientIdJni,
        0,
        config,
        common::Bind(&LeAdvertiser::ScanCallback, common::Unretained(&le_advertiser)),
        common::Bind(&LeAdvertiser::TerminatedCallback, common::Unretained(&le_advertiser)),
        0,
        0,
        facade_handler_);

    auto advertiser_id = future.get();
    if (advertiser_id != LeAdvertisingManager::kInvalidId) {
      le_advertiser.SetAdvertiserId(advertiser_id);
      le_advertisers_.push_back(le_advertiser);
    } else {
      LOG_WARN("Failed to create advertiser");
    }
    response->set_advertiser_id(advertiser_id);
    return ::grpc::Status::OK;
  }

  ::grpc::Status EnableAdvertiser(
      ::grpc::ServerContext* /* context */,
      const EnableAdvertiserRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    le_advertising_manager_->EnableAdvertiser(request->advertiser_id(), request->enable(), 0, 0);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetData(
      ::grpc::ServerContext* /* context */,
      const SetDataRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<GapData> advertising_data = {};
    for (const auto& elem : request->data()) {
      advertising_data.push_back(GapDataFromProto(elem));
    }
    le_advertising_manager_->SetData(request->advertiser_id(), request->set_scan_rsp(), advertising_data);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetParameters(
      ::grpc::ServerContext* /* context */,
      const SetParametersRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::AdvertisingConfig config = {};
    if (!AdvertisingConfigFromProto(request->config(), &config)) {
      LOG_WARN("Error parsing advertising config %s", request->SerializeAsString().c_str());
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Error while parsing advertising config");
    }
    le_advertising_manager_->SetParameters(request->advertiser_id(), config);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetPeriodicParameters(
      ::grpc::ServerContext* /* context */,
      const SetPeriodicParametersRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::PeriodicAdvertisingParameters config = {};
    if (!PeriodicAdvertisingParametersFromProto(request->config(), &config)) {
      LOG_WARN("Error parsing periodic advertising parameters %s", request->SerializeAsString().c_str());
      return ::grpc::Status(
          ::grpc::StatusCode::INVALID_ARGUMENT, "Error while parsing periodic advertising parameters");
    }
    le_advertising_manager_->SetPeriodicParameters(request->advertiser_id(), config);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetPeriodicData(
      ::grpc::ServerContext* /* context */,
      const SetPeriodicDataRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<GapData> advertising_data = {};
    for (const auto& elem : request->data()) {
      advertising_data.push_back(GapDataFromProto(elem));
    }
    le_advertising_manager_->SetPeriodicData(request->advertiser_id(), advertising_data);
    return ::grpc::Status::OK;
  }

  ::grpc::Status EnablePeriodicAdvertising(
      ::grpc::ServerContext* /* context */,
      const EnablePeriodicAdvertisingRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    le_advertising_manager_->EnablePeriodicAdvertising(
        request->advertiser_id(), request->enable(), request->include_adi());
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetOwnAddress(
      ::grpc::ServerContext* /* context */,
      const GetOwnAddressRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    le_advertising_manager_->GetOwnAddress(request->advertiser_id());
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetNumberOfAdvertisingInstances(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      GetNumberOfAdvertisingInstancesResponse* response) override {
    response->set_num_advertising_instances(le_advertising_manager_->GetNumberOfAdvertisingInstances());
    return ::grpc::Status::OK;
  }

  ::grpc::Status RemoveAdvertiser(
      ::grpc::ServerContext* /* context */,
      const RemoveAdvertiserRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    if (request->advertiser_id() == LeAdvertisingManager::kInvalidId) {
      LOG_WARN("Invalid advertiser ID %d", request->advertiser_id());
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invlid advertiser ID received");
    }
    le_advertising_manager_->RemoveAdvertiser(request->advertiser_id());
    for (auto iter = le_advertisers_.begin(); iter != le_advertisers_.end();) {
      if (iter->GetAdvertiserId() == request->advertiser_id()) {
        iter = le_advertisers_.erase(iter);
      } else {
        ++iter;
      }
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchCallbackEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<AdvertisingCallbackMsg>* writer) override {
    return callback_events_.RunLoop(context, writer);
  }

  ::grpc::Status FetchAddressEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<AddressMsg>* writer) override {
    return address_events_.RunLoop(context, writer);
  }

  void OnAdvertisingSetStarted(
      int reg_id, uint8_t advertiser_id, int8_t /* tx_power */, AdvertisingStatus status) {
    if (pending_advertiser_id_.has_value()) {
      pending_advertiser_id_->set_value(advertiser_id);
      pending_advertiser_id_.reset();
    }
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::ADVERTISING_SET_STARTED);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    msg.set_data(reg_id);
    callback_events_.OnIncomingEvent(msg);
  };

  void OnAdvertisingEnabled(uint8_t advertiser_id, bool enable, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::ADVERTISING_ENABLED);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    msg.set_data(enable ? 1 : 0);
    callback_events_.OnIncomingEvent(msg);
  };

  void OnAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::ADVERTISING_DATA_SET);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnScanResponseDataSet(uint8_t advertiser_id, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::SCAN_RESPONSE_DATA_SET);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnAdvertisingParametersUpdated(
      uint8_t advertiser_id, int8_t /* tx_power */, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::ADVERTISING_PARAMETERS_UPDATED);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnPeriodicAdvertisingParametersUpdated(uint8_t advertiser_id, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::PERIODIC_ADVERTISING_PARAMETERS_UPDATED);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnPeriodicAdvertisingDataSet(uint8_t advertiser_id, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::PERIODIC_ADVERTISING_DATA_SET);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnPeriodicAdvertisingEnabled(uint8_t advertiser_id, bool /* enable */, uint8_t status) {
    AdvertisingCallbackMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::PERIODIC_ADVERTISING_ENABLED);
    msg.set_advertiser_id(advertiser_id);
    msg.set_status(static_cast<facade::AdvertisingStatus>(status));
    callback_events_.OnIncomingEvent(msg);
  };

  void OnOwnAddressRead(uint8_t advertiser_id, uint8_t address_type, Address address) {
    LOG_INFO("OnOwnAddressRead Address:%s, address_type:%d",
              ADDRESS_TO_LOGGABLE_CSTR(address), address_type);
    AddressMsg msg;
    msg.set_message_type(AdvertisingCallbackMsgType::OWN_ADDRESS_READ);
    msg.set_advertiser_id(advertiser_id);
    blueberry::facade::BluetoothAddressWithType facade_address;
    facade_address.mutable_address()->set_address(address.ToString());
    facade_address.set_type(static_cast<facade::BluetoothAddressTypeEnum>(address_type));
    *msg.mutable_address() = facade_address;
    address_events_.OnIncomingEvent(msg);
  };

  std::vector<LeAdvertiser> le_advertisers_;
  LeAdvertisingManager* le_advertising_manager_;
  std::optional<std::promise<AdvertiserId>> pending_advertiser_id_;
  os::Handler* facade_handler_;
  ::bluetooth::grpc::GrpcEventQueue<AdvertisingCallbackMsg> callback_events_{"callback events"};
  ::bluetooth::grpc::GrpcEventQueue<AddressMsg> address_events_{"address events"};
};

void LeAdvertisingManagerFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<hci::LeAdvertisingManager>();
}

void LeAdvertisingManagerFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new LeAdvertisingManagerFacadeService(GetDependency<hci::LeAdvertisingManager>(), GetHandler());
}

void LeAdvertisingManagerFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* LeAdvertisingManagerFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory LeAdvertisingManagerFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new LeAdvertisingManagerFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
