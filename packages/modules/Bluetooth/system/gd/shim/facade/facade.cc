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

#include "shim/facade/facade.h"

#include <memory>

#include "blueberry/facade/shim/facade.grpc.pb.h"
#include "blueberry/facade/shim/facade.pb.h"
#include "common/bind.h"
#include "flatbuffers/idl.h"
#include "flatbuffers/reflection_generated.h"
#include "grpc/grpc_event_queue.h"
#include "os/log.h"
#include "shim/dumpsys.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

namespace bluetooth {
namespace shim {
namespace facade {

using namespace blueberry::facade::shim;

class ShimFacadeService : public ShimFacade::Service {
 public:
  ShimFacadeService(shim::Dumpsys* dumpsys_layer, ::bluetooth::os::Handler* facade_handler)
      : dumpsys_layer_(dumpsys_layer), facade_handler_(facade_handler) {}

  virtual ~ShimFacadeService() {}

  ::grpc::Status Dump(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<DumpsysMsg>* /* writer */) override {
    dumpsys_layer_->Dump(0, nullptr);
    return ::grpc::Status::OK;
  }

 private:
  shim::Dumpsys* dumpsys_layer_{nullptr};
  [[maybe_unused]] ::bluetooth::os::Handler* facade_handler_{nullptr};
};

void ShimFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<Dumpsys>();
}

void ShimFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new ShimFacadeService(GetDependency<Dumpsys>(), GetHandler());
}

void ShimFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* ShimFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory ShimFacadeModule::Factory = ::bluetooth::ModuleFactory([]() { return new ShimFacadeModule(); });

}  // namespace facade
}  // namespace shim
}  // namespace bluetooth
