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

#include "hci/facade/controller_facade.h"

#include <condition_variable>
#include <memory>
#include <mutex>

#include "blueberry/facade/hci/controller_facade.grpc.pb.h"
#include "blueberry/facade/hci/controller_facade.pb.h"
#include "common/bind.h"
#include "common/blocking_queue.h"
#include "grpc/grpc_event_queue.h"
#include "hci/address.h"
#include "hci/controller.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

namespace bluetooth {
namespace hci {
namespace facade {

using namespace blueberry::facade::hci;
using blueberry::facade::BluetoothAddress;

class ControllerFacadeService : public ControllerFacade::Service {
 public:
  ControllerFacadeService(Controller* controller, ::bluetooth::os::Handler*) : controller_(controller) {}

  ::grpc::Status GetMacAddress(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      BluetoothAddress* response) override {
    Address local_address = controller_->GetMacAddress();
    response->set_address(local_address.ToString());
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetLocalName(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      NameMsg* response) override {
    std::string local_name = controller_->GetLocalName();
    response->set_name(local_name);
    return ::grpc::Status::OK;
  }

  ::grpc::Status WriteLocalName(
      ::grpc::ServerContext* /* context */,
      const NameMsg* request,
      ::google::protobuf::Empty* /* response */) override {
    controller_->WriteLocalName(request->name());
    return ::grpc::Status::OK;
  }

  ::grpc::Status IsSupportedCommand(
      ::grpc::ServerContext* /* context */,
      const OpCodeMsg* request,
      SupportedMsg* response) override {
    bool ret = controller_->IsSupported(static_cast<OpCode>(request->op_code()));
    response->set_supported(ret);
    return ::grpc::Status::OK;
  }

#define SUPPORTED_API(name)                           \
  ::grpc::Status name(                                \
      ::grpc::ServerContext* /* context */,           \
      const ::google::protobuf::Empty* /* request */, \
      SupportedMsg* response) override {              \
    response->set_supported(controller_->name());     \
    return ::grpc::Status::OK;                        \
  }

  SUPPORTED_API(SupportsSimplePairing)
  SUPPORTED_API(SupportsSecureConnections)
  SUPPORTED_API(SupportsSimultaneousLeBrEdr)
  SUPPORTED_API(SupportsInterlacedInquiryScan)
  SUPPORTED_API(SupportsRssiWithInquiryResults)
  SUPPORTED_API(SupportsExtendedInquiryResponse)
  SUPPORTED_API(SupportsRoleSwitch)
  SUPPORTED_API(Supports3SlotPackets)
  SUPPORTED_API(Supports5SlotPackets)
  SUPPORTED_API(SupportsClassic2mPhy)
  SUPPORTED_API(SupportsClassic3mPhy)
  SUPPORTED_API(Supports3SlotEdrPackets)
  SUPPORTED_API(Supports5SlotEdrPackets)
  SUPPORTED_API(SupportsSco)
  SUPPORTED_API(SupportsHv2Packets)
  SUPPORTED_API(SupportsHv3Packets)
  SUPPORTED_API(SupportsEv3Packets)
  SUPPORTED_API(SupportsEv4Packets)
  SUPPORTED_API(SupportsEv5Packets)
  SUPPORTED_API(SupportsEsco2mPhy)
  SUPPORTED_API(SupportsEsco3mPhy)
  SUPPORTED_API(Supports3SlotEscoEdrPackets)
  SUPPORTED_API(SupportsHoldMode)
  SUPPORTED_API(SupportsSniffMode)
  SUPPORTED_API(SupportsParkMode)
  SUPPORTED_API(SupportsNonFlushablePb)
  SUPPORTED_API(SupportsSniffSubrating)
  SUPPORTED_API(SupportsEncryptionPause)
  SUPPORTED_API(SupportsBle)
  SUPPORTED_API(SupportsBleEncryption)
  SUPPORTED_API(SupportsBleConnectionParametersRequest)
  SUPPORTED_API(SupportsBleExtendedReject)
  SUPPORTED_API(SupportsBlePeripheralInitiatedFeaturesExchange)
  SUPPORTED_API(SupportsBlePing)
  SUPPORTED_API(SupportsBleDataPacketLengthExtension)
  SUPPORTED_API(SupportsBlePrivacy)
  SUPPORTED_API(SupportsBleExtendedScannerFilterPolicies)
  SUPPORTED_API(SupportsBle2mPhy)
  SUPPORTED_API(SupportsBleStableModulationIndexTx)
  SUPPORTED_API(SupportsBleStableModulationIndexRx)
  SUPPORTED_API(SupportsBleCodedPhy)
  SUPPORTED_API(SupportsBleExtendedAdvertising)
  SUPPORTED_API(SupportsBlePeriodicAdvertising)
  SUPPORTED_API(SupportsBleChannelSelectionAlgorithm2)
  SUPPORTED_API(SupportsBlePowerClass1)
  SUPPORTED_API(SupportsBleMinimumUsedChannels)
  SUPPORTED_API(SupportsBleConnectionCteRequest)
  SUPPORTED_API(SupportsBleConnectionCteResponse)
  SUPPORTED_API(SupportsBleConnectionlessCteTransmitter)
  SUPPORTED_API(SupportsBleConnectionlessCteReceiver)
  SUPPORTED_API(SupportsBleAntennaSwitchingDuringCteTx)
  SUPPORTED_API(SupportsBleAntennaSwitchingDuringCteRx)
  SUPPORTED_API(SupportsBleReceivingConstantToneExtensions)
  SUPPORTED_API(SupportsBlePeriodicAdvertisingSyncTransferSender)
  SUPPORTED_API(SupportsBlePeriodicAdvertisingSyncTransferRecipient)
  SUPPORTED_API(SupportsBleSleepClockAccuracyUpdates)
  SUPPORTED_API(SupportsBleRemotePublicKeyValidation)
  SUPPORTED_API(SupportsBleConnectedIsochronousStreamCentral)
  SUPPORTED_API(SupportsBleConnectedIsochronousStreamPeripheral)
  SUPPORTED_API(SupportsBleIsochronousBroadcaster)
  SUPPORTED_API(SupportsBleSynchronizedReceiver)
  SUPPORTED_API(SupportsBleIsochronousChannelsHostSupport)
  SUPPORTED_API(SupportsBlePowerControlRequest)
  SUPPORTED_API(SupportsBlePowerChangeIndication)
  SUPPORTED_API(SupportsBlePathLossMonitoring)
  SUPPORTED_API(SupportsBlePeriodicAdvertisingAdi)

  ::grpc::Status GetLeNumberOfSupportedAdvertisingSets(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      SingleValueMsg* response) override {
    uint8_t ret = controller_->GetLeNumberOfSupportedAdverisingSets();
    response->set_value(ret);
    return ::grpc::Status::OK;
  }

 private:
  Controller* controller_;
};

void ControllerFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<Controller>();
}

void ControllerFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new ControllerFacadeService(GetDependency<Controller>(), GetHandler());
}

void ControllerFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* ControllerFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory ControllerFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new ControllerFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
