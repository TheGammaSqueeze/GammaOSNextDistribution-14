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

#include "hal/facade.h"

#include <memory>
#include <mutex>

#include "blueberry/facade/hal/hal_facade.grpc.pb.h"
#include "grpc/grpc_event_queue.h"
#include "hal/hci_hal.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

namespace bluetooth {
namespace hal {

class HciHalFacadeService : public blueberry::facade::hal::HciHalFacade::Service,
                            public ::bluetooth::hal::HciHalCallbacks {
 public:
  explicit HciHalFacadeService(HciHal* hal) : hal_(hal) {
    hal->registerIncomingPacketCallback(this);
  }

  ~HciHalFacadeService() {
    hal_->unregisterIncomingPacketCallback();
  }

  ::grpc::Status SendCommand(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::Data* request,
      ::google::protobuf::Empty* /* response */) override {
    std::unique_lock<std::mutex> lock(mutex_);
    can_send_hci_command_ = false;
    std::string req_string = request->payload();
    hal_->sendHciCommand(std::vector<uint8_t>(req_string.begin(), req_string.end()));
    while (!can_send_hci_command_) {
      cv_.wait(lock);
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status SendAcl(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::Data* request,
      ::google::protobuf::Empty* /* response */) override {
    std::string req_string = request->payload();
    hal_->sendAclData(std::vector<uint8_t>(req_string.begin(), req_string.end()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status SendSco(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::Data* request,
      ::google::protobuf::Empty* /* response */) override {
    std::string req_string = request->payload();
    hal_->sendScoData(std::vector<uint8_t>(req_string.begin(), req_string.end()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status StreamEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_hci_events_.RunLoop(context, writer);
  };

  ::grpc::Status StreamAcl(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_acl_events_.RunLoop(context, writer);
  };

  ::grpc::Status StreamSco(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_sco_events_.RunLoop(context, writer);
  };

  ::grpc::Status StreamIso(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_iso_events_.RunLoop(context, writer);
  };

  void hciEventReceived(bluetooth::hal::HciPacket event) override {
    {
      ::blueberry::facade::Data response;
      response.set_payload(std::string(event.begin(), event.end()));
      pending_hci_events_.OnIncomingEvent(std::move(response));
    }
    can_send_hci_command_ = true;
    cv_.notify_one();
  }

  void aclDataReceived(bluetooth::hal::HciPacket data) override {
    ::blueberry::facade::Data response;
    response.set_payload(std::string(data.begin(), data.end()));
    pending_acl_events_.OnIncomingEvent(std::move(response));
  }

  void scoDataReceived(bluetooth::hal::HciPacket data) override {
    ::blueberry::facade::Data response;
    response.set_payload(std::string(data.begin(), data.end()));
    pending_sco_events_.OnIncomingEvent(std::move(response));
  }

  void isoDataReceived(bluetooth::hal::HciPacket data) override {
    ::blueberry::facade::Data response;
    response.set_payload(std::string(data.begin(), data.end()));
    pending_iso_events_.OnIncomingEvent(std::move(response));
  }

 private:
  HciHal* hal_;
  bool can_send_hci_command_ = true;
  mutable std::mutex mutex_;
  std::condition_variable cv_;
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_hci_events_{"StreamEvents"};
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_acl_events_{"StreamAcl"};
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_sco_events_{"StreamSco"};
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_iso_events_{"StreamIso"};
};

void HciHalFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<HciHal>();
}

void HciHalFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new HciHalFacadeService(GetDependency<HciHal>());
}

void HciHalFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* HciHalFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory HciHalFacadeModule::Factory = ::bluetooth::ModuleFactory([]() { return new HciHalFacadeModule(); });

}  // namespace hal
}  // namespace bluetooth
