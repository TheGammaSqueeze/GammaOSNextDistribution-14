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

#include "facade/grpc_root_server.h"

#include <string>

#include "blueberry/facade/rootservice.grpc.pb.h"
#include "facade/read_only_property_server.h"
#include "grpc/grpc_module.h"
#include "hal/facade.h"
#include "hci/facade/acl_manager_facade.h"
#include "hci/facade/controller_facade.h"
#include "hci/facade/facade.h"
#include "hci/facade/le_acl_manager_facade.h"
#include "hci/facade/le_advertising_manager_facade.h"
#include "hci/facade/le_initiator_address_facade.h"
#include "hci/facade/le_scanning_manager_facade.h"
#include "iso/facade.h"
#include "l2cap/classic/facade.h"
#include "l2cap/le/facade.h"
#include "neighbor/facade/facade.h"
#include "os/log.h"
#include "os/thread.h"
#include "security/facade.h"
#include "shim/facade/facade.h"
#include "stack_manager.h"

namespace bluetooth {
namespace facade {

using ::blueberry::facade::BluetoothModule;
using ::bluetooth::grpc::GrpcModule;
using ::bluetooth::os::Thread;

class RootFacadeService : public ::blueberry::facade::RootFacade::Service {
 public:
  explicit RootFacadeService(int grpc_port) : grpc_port_(grpc_port) {}

  ::grpc::Status StartStack(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::StartStackRequest* request,
      ::blueberry::facade::StartStackResponse* /* response */) override {
    if (is_running_) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "stack is running");
    }

    ModuleList modules;
    modules.add<::bluetooth::grpc::GrpcModule>();

    BluetoothModule module_under_test = request->module_under_test();
    switch (module_under_test) {
      case BluetoothModule::HAL:
        modules.add<::bluetooth::hal::HciHalFacadeModule>();
        break;
      case BluetoothModule::HCI:
        modules.add<::bluetooth::facade::ReadOnlyPropertyServerModule>();
        modules.add<::bluetooth::hci::facade::HciFacadeModule>();
        break;
      case BluetoothModule::HCI_INTERFACES:
        modules.add<::bluetooth::facade::ReadOnlyPropertyServerModule>();
        modules.add<::bluetooth::hci::facade::HciFacadeModule>();
        modules.add<::bluetooth::hci::facade::AclManagerFacadeModule>();
        modules.add<::bluetooth::hci::facade::ControllerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeAclManagerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeAdvertisingManagerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeInitiatorAddressFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeScanningManagerFacadeModule>();
        modules.add<::bluetooth::neighbor::facade::NeighborFacadeModule>();
        modules.add<::bluetooth::iso::IsoModuleFacadeModule>();
        break;
      case BluetoothModule::L2CAP:
        modules.add<::bluetooth::hci::facade::ControllerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeAdvertisingManagerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeInitiatorAddressFacadeModule>();
        modules.add<::bluetooth::neighbor::facade::NeighborFacadeModule>();
        modules.add<::bluetooth::facade::ReadOnlyPropertyServerModule>();
        modules.add<::bluetooth::l2cap::classic::L2capClassicModuleFacadeModule>();
        modules.add<::bluetooth::l2cap::le::L2capLeModuleFacadeModule>();
        modules.add<::bluetooth::hci::facade::HciFacadeModule>();
        modules.add<::bluetooth::iso::IsoModuleFacadeModule>();
        break;
      case BluetoothModule::SECURITY:
        modules.add<::bluetooth::facade::ReadOnlyPropertyServerModule>();
        modules.add<::bluetooth::hci::facade::ControllerFacadeModule>();
        modules.add<::bluetooth::security::SecurityModuleFacadeModule>();
        modules.add<::bluetooth::neighbor::facade::NeighborFacadeModule>();
        modules.add<::bluetooth::l2cap::classic::L2capClassicModuleFacadeModule>();
        modules.add<::bluetooth::hci::facade::HciFacadeModule>();
        modules.add<::bluetooth::hci::facade::ControllerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeAdvertisingManagerFacadeModule>();
        modules.add<::bluetooth::hci::facade::LeScanningManagerFacadeModule>();
        break;
      case BluetoothModule::SHIM:
        modules.add<::bluetooth::shim::facade::ShimFacadeModule>();
        break;
      default:
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "invalid module under test");
    }

    stack_thread_ = std::make_unique<Thread>("stack_thread", Thread::Priority::NORMAL);
    stack_manager_.StartUp(&modules, stack_thread_.get());

    GrpcModule* grpc_module = stack_manager_.GetInstance<GrpcModule>();
    grpc_module->StartServer("0.0.0.0", grpc_port_);

    grpc_loop_thread_ = std::make_unique<std::thread>([grpc_module] { grpc_module->RunGrpcLoop(); });
    is_running_ = true;

    return ::grpc::Status::OK;
  }

  ::grpc::Status StopStack(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::StopStackRequest* /* request */,
      ::blueberry::facade::StopStackResponse* /* response */) override {
    if (!is_running_) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "stack is not running");
    }

    stack_manager_.GetInstance<GrpcModule>()->StopServer();
    grpc_loop_thread_->join();
    grpc_loop_thread_.reset();

    stack_manager_.ShutDown();
    stack_thread_.reset();
    is_running_ = false;
    return ::grpc::Status::OK;
  }

 private:
  std::unique_ptr<Thread> stack_thread_ = nullptr;
  bool is_running_ = false;
  std::unique_ptr<std::thread> grpc_loop_thread_ = nullptr;
  StackManager stack_manager_;
  int grpc_port_ = 8898;
};

struct GrpcRootServer::impl {
  bool started_ = false;
  std::unique_ptr<RootFacadeService> root_facade_service_ = nullptr;
  std::unique_ptr<::grpc::Server> server_ = nullptr;
};

GrpcRootServer::GrpcRootServer() : pimpl_(new impl()) {}

GrpcRootServer::~GrpcRootServer() = default;

void GrpcRootServer::StartServer(const std::string& address, int grpc_root_server_port, int grpc_port) {
  ASSERT(!pimpl_->started_);
  pimpl_->started_ = true;

  std::string listening_port = address + ":" + std::to_string(grpc_root_server_port);
  ::grpc::ServerBuilder builder;

  pimpl_->root_facade_service_ = std::make_unique<RootFacadeService>(grpc_port);
  builder.RegisterService(pimpl_->root_facade_service_.get());
  builder.AddListeningPort(listening_port, ::grpc::InsecureServerCredentials());
  pimpl_->server_ = builder.BuildAndStart();

  ASSERT(pimpl_->server_ != nullptr);
}

void GrpcRootServer::StopServer() {
  ASSERT(pimpl_->started_);
  pimpl_->server_->Shutdown();
  pimpl_->started_ = false;
}

void GrpcRootServer::RunGrpcLoop() {
  ASSERT(pimpl_->started_);
  pimpl_->server_->Wait();
}

}  // namespace facade
}  // namespace bluetooth
