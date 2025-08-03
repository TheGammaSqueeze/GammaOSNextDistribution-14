/*
 * Copyright 2020 The Android Open Source Project
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

#include "l2cap/le/facade.h"

#include "blueberry/facade/l2cap/le/facade.grpc.pb.h"
#include "grpc/grpc_event_queue.h"
#include "l2cap/le/dynamic_channel.h"
#include "l2cap/le/dynamic_channel_manager.h"
#include "l2cap/le/dynamic_channel_service.h"
#include "l2cap/le/l2cap_le_module.h"
#include "l2cap/le/security_policy.h"
#include "l2cap/psm.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace l2cap {
namespace le {

using namespace blueberry::facade::l2cap::le;

SecurityPolicy SecurityLevelToPolicy(SecurityLevel level) {
  switch (level) {
    case SecurityLevel::NO_SECURITY:
      return SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK;
    case SecurityLevel::UNAUTHENTICATED_PAIRING_WITH_ENCRYPTION:
      return SecurityPolicy::ENCRYPTED_TRANSPORT;
    case SecurityLevel::AUTHENTICATED_PAIRING_WITH_ENCRYPTION:
      return SecurityPolicy::AUTHENTICATED_ENCRYPTED_TRANSPORT;
    case SecurityLevel::AUTHENTICATED_PAIRING_WITH_128_BIT_KEY:
      return SecurityPolicy::_NOT_FOR_YOU__AUTHENTICATED_PAIRING_WITH_128_BIT_KEY;
    case SecurityLevel::AUTHORIZATION:
      return SecurityPolicy::_NOT_FOR_YOU__AUTHORIZATION;
    default:
      return SecurityPolicy::NO_SECURITY_WHATSOEVER_PLAINTEXT_TRANSPORT_OK;
  }
}

static constexpr auto kChannelOpenTimeout = std::chrono::seconds(4);

class L2capLeModuleFacadeService : public L2capLeModuleFacade::Service {
 public:
  L2capLeModuleFacadeService(L2capLeModule* l2cap_layer, os::Handler* facade_handler)
      : l2cap_layer_(l2cap_layer), facade_handler_(facade_handler) {
    ASSERT(l2cap_layer_ != nullptr);
    ASSERT(facade_handler_ != nullptr);
  }

  ::grpc::Status FetchL2capData(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::bluetooth::l2cap::le::L2capPacket>* writer) override {
    return pending_l2cap_data_.RunLoop(context, writer);
  }

  ::grpc::Status OpenDynamicChannel(
      ::grpc::ServerContext* /* context */,
      const OpenDynamicChannelRequest* request,
      OpenDynamicChannelResponse* response) override {
    auto service_helper = dynamic_channel_helper_map_.find(request->psm());
    if (service_helper == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    hci::Address peer_address;
    ASSERT(hci::Address::FromString(request->remote().address().address(), peer_address));
    // TODO: Support different address type
    hci::AddressWithType peer(peer_address, hci::AddressType::RANDOM_DEVICE_ADDRESS);
    service_helper->second->Connect(peer);
    response->set_status(
        static_cast<int>(service_helper->second->channel_open_fail_reason_.l2cap_connection_response_result));
    return ::grpc::Status::OK;
  }

  ::grpc::Status CloseDynamicChannel(
      ::grpc::ServerContext* /* context */,
      const CloseDynamicChannelRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    auto service_helper = dynamic_channel_helper_map_.find(request->psm());
    if (service_helper == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    if (service_helper->second->channel_ == nullptr) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Channel not open");
    }
    auto address = service_helper->second->channel_->GetDevice().GetAddress();
    hci::Address peer_address;
    ASSERT(hci::Address::FromString(request->remote().address().address(), peer_address));
    if (address != peer_address) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Remote address doesn't match");
    }
    service_helper->second->channel_->Close();
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetDynamicChannel(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::l2cap::le::SetEnableDynamicChannelRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    if (request->enable()) {
      dynamic_channel_helper_map_.emplace(request->psm(), std::make_unique<L2capDynamicChannelHelper>(
                                                              this, l2cap_layer_, facade_handler_, request->psm(),
                                                              SecurityLevelToPolicy(request->security_level())));
      return ::grpc::Status::OK;
    } else {
      auto service_helper = dynamic_channel_helper_map_.find(request->psm());
      if (service_helper == dynamic_channel_helper_map_.end()) {
        return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
      }
      service_helper->second->service_->Unregister(common::BindOnce([] {}), facade_handler_);
      return ::grpc::Status::OK;
    }
  }

  ::grpc::Status SendDynamicChannelPacket(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::l2cap::le::DynamicChannelPacket* request,
      ::google::protobuf::Empty* /* response */) override {
    std::unique_lock<std::mutex> lock(channel_map_mutex_);
    if (dynamic_channel_helper_map_.find(request->psm()) == dynamic_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Psm not registered");
    }
    std::vector<uint8_t> packet(request->payload().begin(), request->payload().end());
    if (!dynamic_channel_helper_map_[request->psm()]->SendPacket(packet)) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Channel not open");
    }
    return ::grpc::Status::OK;
  }

  class L2capDynamicChannelHelper {
   public:
    L2capDynamicChannelHelper(L2capLeModuleFacadeService* service, L2capLeModule* l2cap_layer, os::Handler* handler,
                              Psm psm, SecurityPolicy security_policy)
        : facade_service_(service), l2cap_layer_(l2cap_layer), handler_(handler), psm_(psm) {
      dynamic_channel_manager_ = l2cap_layer_->GetDynamicChannelManager();
      dynamic_channel_manager_->RegisterService(
          psm, {}, security_policy,
          common::BindOnce(&L2capDynamicChannelHelper::on_l2cap_service_registration_complete,
                           common::Unretained(this)),
          common::Bind(&L2capDynamicChannelHelper::on_connection_open, common::Unretained(this)), handler_);
    }

    ~L2capDynamicChannelHelper() {
      if (channel_ != nullptr) {
        channel_->GetQueueUpEnd()->UnregisterDequeue();
        channel_ = nullptr;
      }
    }

    void Connect(hci::AddressWithType address) {
      dynamic_channel_manager_->ConnectChannel(
          address, {}, psm_, common::Bind(&L2capDynamicChannelHelper::on_connection_open, common::Unretained(this)),
          common::Bind(&L2capDynamicChannelHelper::on_connect_fail, common::Unretained(this)), handler_);
      std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
      if (!channel_open_cv_.wait_for(lock, kChannelOpenTimeout, [this] { return channel_ != nullptr; })) {
        LOG_WARN("Channel is not open for psm %d", psm_);
      }
    }

    void on_l2cap_service_registration_complete(DynamicChannelManager::RegistrationResult registration_result,
                                                std::unique_ptr<DynamicChannelService> service) {
      if (registration_result != DynamicChannelManager::RegistrationResult::SUCCESS) {
        LOG_ERROR("Service registration failed");
      } else {
        service_ = std::move(service);
      }
    }

    // invoked from Facade Handler
    void on_connection_open(std::unique_ptr<DynamicChannel> channel) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_ = std::move(channel);
      }
      channel_open_cv_.notify_all();
      channel_->RegisterOnCloseCallback(
          facade_service_->facade_handler_->BindOnceOn(this, &L2capDynamicChannelHelper::on_close_callback));
      channel_->GetQueueUpEnd()->RegisterDequeue(
          facade_service_->facade_handler_,
          common::Bind(&L2capDynamicChannelHelper::on_incoming_packet, common::Unretained(this)));
    }

    void on_close_callback(hci::ErrorCode /* error_code */) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_->GetQueueUpEnd()->UnregisterDequeue();
      }
      channel_ = nullptr;
    }

    void on_connect_fail(DynamicChannelManager::ConnectionResult result) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_ = nullptr;
        channel_open_fail_reason_ = result;
      }
      channel_open_cv_.notify_all();
    }

    void on_incoming_packet() {
      auto packet = channel_->GetQueueUpEnd()->TryDequeue();
      std::string data = std::string(packet->begin(), packet->end());
      L2capPacket l2cap_data;
      l2cap_data.set_psm(psm_);
      l2cap_data.set_payload(data);
      facade_service_->pending_l2cap_data_.OnIncomingEvent(l2cap_data);
    }

    bool SendPacket(std::vector<uint8_t> packet) {
      if (channel_ == nullptr) {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        if (!channel_open_cv_.wait_for(lock, kChannelOpenTimeout, [this] { return channel_ != nullptr; })) {
          LOG_WARN("Channel is not open for psm %d", psm_);
          return false;
        }
      }
      std::promise<void> promise;
      auto future = promise.get_future();
      channel_->GetQueueUpEnd()->RegisterEnqueue(
          handler_, common::Bind(&L2capDynamicChannelHelper::enqueue_callback, common::Unretained(this), packet,
                                 common::Passed(std::move(promise))));
      auto status = future.wait_for(std::chrono::milliseconds(500));
      if (status != std::future_status::ready) {
        LOG_ERROR("Can't send packet because the previous packet wasn't sent yet");
        return false;
      }
      return true;
    }

    std::unique_ptr<packet::BasePacketBuilder> enqueue_callback(std::vector<uint8_t> packet,
                                                                std::promise<void> promise) {
      auto packet_one = std::make_unique<packet::RawBuilder>(2000);
      packet_one->AddOctets(packet);
      channel_->GetQueueUpEnd()->UnregisterEnqueue();
      promise.set_value();
      return packet_one;
    }

    L2capLeModuleFacadeService* facade_service_;
    L2capLeModule* l2cap_layer_;
    os::Handler* handler_;
    std::unique_ptr<DynamicChannelManager> dynamic_channel_manager_;
    std::unique_ptr<DynamicChannelService> service_;
    std::unique_ptr<DynamicChannel> channel_ = nullptr;
    Psm psm_;
    DynamicChannelManager::ConnectionResult channel_open_fail_reason_;
    std::condition_variable channel_open_cv_;
    std::mutex channel_open_cv_mutex_;
  };

  ::grpc::Status SetFixedChannel(
      ::grpc::ServerContext* /* context */,
      const SetEnableFixedChannelRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    if (request->enable()) {
      fixed_channel_helper_map_.emplace(request->cid(), std::make_unique<L2capFixedChannelHelper>(
                                                            this, l2cap_layer_, facade_handler_, request->cid()));
      return ::grpc::Status::OK;
    } else {
      auto service_helper = fixed_channel_helper_map_.find(request->cid());
      if (service_helper == fixed_channel_helper_map_.end()) {
        return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Cid not registered");
      }
      service_helper->second->channel_->Release();
      service_helper->second->service_->Unregister(common::BindOnce([] {}), facade_handler_);
      return ::grpc::Status::OK;
    }
  }

  ::grpc::Status SendFixedChannelPacket(
      ::grpc::ServerContext* /* context */,
      const FixedChannelPacket* request,
      ::google::protobuf::Empty* /* response */) override {
    std::unique_lock<std::mutex> lock(channel_map_mutex_);
    if (fixed_channel_helper_map_.find(request->cid()) == fixed_channel_helper_map_.end()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Cid not registered");
    }
    std::vector<uint8_t> packet(request->payload().begin(), request->payload().end());
    if (!fixed_channel_helper_map_[request->cid()]->SendPacket(packet)) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Channel not open");
    }
    return ::grpc::Status::OK;
  }

  class L2capFixedChannelHelper {
   public:
    L2capFixedChannelHelper(L2capLeModuleFacadeService* service, L2capLeModule* l2cap_layer, os::Handler* handler,
                            Cid cid)
        : facade_service_(service), l2cap_layer_(l2cap_layer), handler_(handler), cid_(cid) {
      fixed_channel_manager_ = l2cap_layer_->GetFixedChannelManager();
      fixed_channel_manager_->RegisterService(
          cid_,
          common::BindOnce(&L2capFixedChannelHelper::on_l2cap_service_registration_complete, common::Unretained(this)),
          common::Bind(&L2capFixedChannelHelper::on_connection_open, common::Unretained(this)), handler_);
    }

    ~L2capFixedChannelHelper() {
      if (channel_ != nullptr) {
        channel_->GetQueueUpEnd()->UnregisterDequeue();
        channel_->Release();
        channel_ = nullptr;
      }
    }

    void Connect(hci::AddressWithType address) {
      fixed_channel_manager_->ConnectServices(
          address, common::BindOnce(&L2capFixedChannelHelper::on_connect_fail, common::Unretained(this)), handler_);
      std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
      if (!channel_open_cv_.wait_for(lock, kChannelOpenTimeout, [this] { return channel_ != nullptr; })) {
        LOG_WARN("Channel is not open for cid %d", cid_);
      }
    }

    void on_l2cap_service_registration_complete(FixedChannelManager::RegistrationResult registration_result,
                                                std::unique_ptr<FixedChannelService> service) {
      if (registration_result != FixedChannelManager::RegistrationResult::SUCCESS) {
        LOG_ERROR("Service registration failed");
      } else {
        service_ = std::move(service);
      }
    }

    // invoked from Facade Handler
    void on_connection_open(std::unique_ptr<FixedChannel> channel) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_ = std::move(channel);
        channel_->RegisterOnCloseCallback(
            handler_, common::BindOnce(&L2capFixedChannelHelper::on_close_callback, common::Unretained(this)));
        channel_->Acquire();
      }
      channel_open_cv_.notify_all();
      channel_->GetQueueUpEnd()->RegisterDequeue(
          facade_service_->facade_handler_,
          common::Bind(&L2capFixedChannelHelper::on_incoming_packet, common::Unretained(this)));
    }

    void on_close_callback(hci::ErrorCode /* error_code */) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_->GetQueueUpEnd()->UnregisterDequeue();
      }
      channel_ = nullptr;
    }

    void on_connect_fail(FixedChannelManager::ConnectionResult /* result */) {
      {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        channel_ = nullptr;
      }
      channel_open_cv_.notify_all();
    }

    void on_incoming_packet() {
      auto packet = channel_->GetQueueUpEnd()->TryDequeue();
      std::string data = std::string(packet->begin(), packet->end());
      L2capPacket l2cap_data;
      l2cap_data.set_fixed_cid(cid_);
      l2cap_data.set_payload(data);
      facade_service_->pending_l2cap_data_.OnIncomingEvent(l2cap_data);
    }

    bool SendPacket(std::vector<uint8_t> packet) {
      if (channel_ == nullptr) {
        std::unique_lock<std::mutex> lock(channel_open_cv_mutex_);
        if (!channel_open_cv_.wait_for(lock, kChannelOpenTimeout, [this] { return channel_ != nullptr; })) {
          LOG_WARN("Channel is not open for cid %d", cid_);
          return false;
        }
      }
      std::promise<void> promise;
      auto future = promise.get_future();
      channel_->GetQueueUpEnd()->RegisterEnqueue(
          handler_, common::Bind(&L2capFixedChannelHelper::enqueue_callback, common::Unretained(this), packet,
                                 common::Passed(std::move(promise))));
      auto status = future.wait_for(std::chrono::milliseconds(500));
      if (status != std::future_status::ready) {
        LOG_ERROR("Can't send packet because the previous packet wasn't sent yet");
        return false;
      }
      return true;
    }

    std::unique_ptr<packet::BasePacketBuilder> enqueue_callback(std::vector<uint8_t> packet,
                                                                std::promise<void> promise) {
      auto packet_one = std::make_unique<packet::RawBuilder>(2000);
      packet_one->AddOctets(packet);
      channel_->GetQueueUpEnd()->UnregisterEnqueue();
      promise.set_value();
      return packet_one;
    }

    L2capLeModuleFacadeService* facade_service_;
    L2capLeModule* l2cap_layer_;
    os::Handler* handler_;
    std::unique_ptr<FixedChannelManager> fixed_channel_manager_;
    std::unique_ptr<FixedChannelService> service_;
    std::unique_ptr<FixedChannel> channel_ = nullptr;
    Cid cid_;
    std::condition_variable channel_open_cv_;
    std::mutex channel_open_cv_mutex_;
  };

  ::grpc::Status SendConnectionParameterUpdate(
      ::grpc::ServerContext* /* context */,
      const ConnectionParameter* request,
      ::google::protobuf::Empty* /* response */) override {
    if (dynamic_channel_helper_map_.empty()) {
      return ::grpc::Status(::grpc::StatusCode::FAILED_PRECONDITION, "Need to open at least one dynamic channel first");
    }
    auto& dynamic_channel_helper = dynamic_channel_helper_map_.begin()->second;
    dynamic_channel_helper->channel_->GetLinkOptions()->UpdateConnectionParameter(
        request->conn_interval_min(), request->conn_interval_max(), request->conn_latency(),
        request->supervision_timeout(), request->min_ce_length(), request->max_ce_length());

    return ::grpc::Status::OK;
  }

  L2capLeModule* l2cap_layer_;
  os::Handler* facade_handler_;
  std::mutex channel_map_mutex_;
  std::map<Psm, std::unique_ptr<L2capDynamicChannelHelper>> dynamic_channel_helper_map_;
  std::map<Cid, std::unique_ptr<L2capFixedChannelHelper>> fixed_channel_helper_map_;
  ::bluetooth::grpc::GrpcEventQueue<L2capPacket> pending_l2cap_data_{"FetchL2capData"};
};

void L2capLeModuleFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<l2cap::le::L2capLeModule>();
}

void L2capLeModuleFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new L2capLeModuleFacadeService(GetDependency<l2cap::le::L2capLeModule>(), GetHandler());
}

void L2capLeModuleFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* L2capLeModuleFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory L2capLeModuleFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new L2capLeModuleFacadeModule(); });

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth
