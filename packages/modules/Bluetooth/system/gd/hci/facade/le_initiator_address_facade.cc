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

#include "hci/facade/le_initiator_address_facade.h"

#include "blueberry/facade/hci/le_initiator_address_facade.grpc.pb.h"
#include "blueberry/facade/hci/le_initiator_address_facade.pb.h"
#include "hci/acl_manager.h"
#include "hci/hci_packets.h"
#include "hci/octets.h"
#include "packet/raw_builder.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

using ::bluetooth::packet::RawBuilder;

namespace bluetooth {
namespace hci {
namespace facade {

using namespace blueberry::facade::hci;

class LeInitiatorAddressFacadeService : public LeInitiatorAddressFacade::Service {
 public:
  LeInitiatorAddressFacadeService(AclManager* acl_manager, ::bluetooth::os::Handler* facade_handler)
      : acl_manager_(acl_manager),
        address_manager_(acl_manager_->GetLeAddressManager()),
        facade_handler_(facade_handler) {
    ASSERT(facade_handler_ != nullptr);
  }

  ::grpc::Status SetPrivacyPolicyForInitiatorAddress(
      ::grpc::ServerContext* /* context */,
      const PrivacyPolicy* request,
      ::google::protobuf::Empty* /* writer */) override {
    Address address = Address::kEmpty;
    LeAddressManager::AddressPolicy address_policy =
        static_cast<LeAddressManager::AddressPolicy>(request->address_policy());
    if (address_policy == LeAddressManager::AddressPolicy::USE_STATIC_ADDRESS) {
      ASSERT(Address::FromString(request->address_with_type().address().address(), address));
    }
    AddressWithType address_with_type(address, static_cast<AddressType>(request->address_with_type().type()));
    auto minimum_rotation_time = std::chrono::milliseconds(request->minimum_rotation_time());
    auto maximum_rotation_time = std::chrono::milliseconds(request->maximum_rotation_time());
    Octet16 irk = {};
    auto request_irk_length = request->rotation_irk().end() - request->rotation_irk().begin();
    if (request_irk_length == kOctet16Length) {
      std::vector<uint8_t> irk_data(request->rotation_irk().begin(), request->rotation_irk().end());
      std::copy_n(irk_data.begin(), kOctet16Length, irk.begin());
      acl_manager_->SetPrivacyPolicyForInitiatorAddressForTest(
          address_policy, address_with_type, irk, minimum_rotation_time, maximum_rotation_time);
    } else {
      acl_manager_->SetPrivacyPolicyForInitiatorAddress(
          address_policy, address_with_type, minimum_rotation_time, maximum_rotation_time);
      ASSERT(request_irk_length == 0);
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetCurrentInitiatorAddress(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      ::blueberry::facade::BluetoothAddressWithType* response) override {
    AddressWithType current = address_manager_->GetInitiatorAddress();
    auto bluetooth_address = new ::blueberry::facade::BluetoothAddress();
    bluetooth_address->set_address(current.GetAddress().ToString());
    response->set_type(static_cast<::blueberry::facade::BluetoothAddressTypeEnum>(current.GetAddressType()));
    response->set_allocated_address(bluetooth_address);
    return ::grpc::Status::OK;
  }

  ::grpc::Status NewResolvableAddress(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      ::blueberry::facade::BluetoothAddressWithType* response) override {
    AddressWithType another = address_manager_->NewResolvableAddress();
    auto bluetooth_address = new ::blueberry::facade::BluetoothAddress();
    bluetooth_address->set_address(another.GetAddress().ToString());
    response->set_type(static_cast<::blueberry::facade::BluetoothAddressTypeEnum>(another.GetAddressType()));
    response->set_allocated_address(bluetooth_address);
    return ::grpc::Status::OK;
  }

 private:
  AclManager* acl_manager_;
  LeAddressManager* address_manager_;
  ::bluetooth::os::Handler* facade_handler_;
};

void LeInitiatorAddressFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<AclManager>();
}

void LeInitiatorAddressFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new LeInitiatorAddressFacadeService(GetDependency<AclManager>(), GetHandler());
}

void LeInitiatorAddressFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* LeInitiatorAddressFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory LeInitiatorAddressFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new LeInitiatorAddressFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
