/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TEST_SIMULATION_RPC_TEST
#define TEST_SIMULATION_RPC_TEST

#include <stdint.h>

#include "chre/util/pigweed/rpc_client.h"
#include "chre/util/pigweed/rpc_server.h"
#include "chre/util/singleton.h"
#include "rpc_test.pb.h"
#include "rpc_test.rpc.pb.h"

namespace chre {

constexpr uint64_t kPwRcpServerAppId = 0x0123456789000001;
constexpr uint64_t kPwRcpClientAppId = 0x0123456789000002;

class RpcTestService final
    : public chre::rpc::pw_rpc::nanopb::RpcTestService::Service<
          RpcTestService> {
 public:
  // Increment RPC unary service definition.
  // See generated IncrementService::Service for more details.
  pw::Status Increment(const chre_rpc_NumberMessage &request,
                       chre_rpc_NumberMessage &response);
};

class Env {
 public:
  RpcTestService mRpcTestService;
  chre::RpcServer mServer;
  chre::RpcClient mClient{kPwRcpServerAppId};
  pw::rpc::NanopbUnaryReceiver<chre_rpc_NumberMessage> mIncrementCall;
  uint32_t mNumber;

  void closeServer() {
    mServer.close();
  }

  void closeClient() {
    mClient.close();
  }
};

typedef Singleton<Env> EnvSingleton;

}  // namespace chre

#endif  // TEST_SIMULATION_RPC_TEST