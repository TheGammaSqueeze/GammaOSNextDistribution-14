// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Frontend client for netsimd.

#include "frontend/frontend_client_stub.h"

#include <chrono>
#include <memory>

#include "grpcpp/create_channel.h"
#include "grpcpp/security/credentials.h"
#include "netsim/frontend.grpc.pb.h"
#include "netsim/frontend.pb.h"
#include "util/os_utils.h"

namespace netsim {
namespace frontend {
namespace {
const std::chrono::duration kConnectionDeadline = std::chrono::seconds(1);

std::unique_ptr<frontend::FrontendService::Stub> NewFrontendClient(
    uint16_t instance_num) {
  auto port = netsim::osutils::GetServerAddress(instance_num);
  if (!port.has_value()) {
    return nullptr;
  }
  auto server = "localhost:" + port.value();
  std::shared_ptr<grpc::Channel> channel =
      grpc::CreateChannel(server, grpc::InsecureChannelCredentials());

  auto deadline = std::chrono::system_clock::now() + kConnectionDeadline;
  if (!channel->WaitForConnected(deadline)) {
    return nullptr;
  }

  return frontend::FrontendService::NewStub(channel);
}
}  // namespace

bool IsNetsimdAlive(uint16_t instance_num) {
  return NewFrontendClient(instance_num) != nullptr;
}

}  // namespace frontend
}  // namespace netsim
