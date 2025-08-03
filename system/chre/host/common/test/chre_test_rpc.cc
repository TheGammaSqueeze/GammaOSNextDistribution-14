/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <inttypes.h>
#include <utils/StrongPointer.h>
#include <chrono>
#include <cstdint>
#include <future>
#include <thread>

#include "chre/util/nanoapp/app_id.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/pigweed/hal_rpc_client.h"
#include "chre_host/socket_client.h"
#include "rpc_world.pb.h"
#include "rpc_world.rpc.pb.h"

/**
 * @file
 * Test RPC by calling a service provided by the rpc_world nanoapp.
 *
 * Usage:
 * 1. Compile and push the rpc_world nanoapp to the device.
 *
 * 2. Load the nanoapp:
 *    adb shell chre_test_client load_with_header \
 *      /vendor/etc/chre/rpc_world.napp_header \
 *      /vendor/etc/chre/rpc_world.so
 *
 * 3. Build this test and push it to the device:
 *    m chre_test_rpc
 *    adb push \
 *      out/target/product/<product>/vendor/bin/chre_test_rpc \
 *      /vendor/bin/chre_test_rpc
 *
 * 4. Launch the test:
 *    adb shell chre_test_rpc
 */

namespace {

using ::android::sp;
using ::android::chre::HalRpcClient;
using ::android::chre::HostProtocolHost;
using ::android::chre::IChreMessageHandlers;
using ::android::chre::SocketClient;
using ::flatbuffers::FlatBufferBuilder;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

constexpr uint16_t kHostEndpoint = 0x8006;

constexpr uint32_t kRequestNumber = 10;
std::promise<uint32_t> gResponsePromise;

class SocketCallbacks : public SocketClient::ICallbacks,
                        public IChreMessageHandlers {
 public:
  void onMessageReceived(const void *data, size_t length) override {
    if (!HostProtocolHost::decodeMessageFromChre(data, length, *this)) {
      LOGE("Failed to decode message");
    }
  }

  void handleNanoappListResponse(
      const fbs::NanoappListResponseT &response) override {
    LOGI("Got nanoapp list response with %zu apps", response.nanoapps.size());
  }
};

}  // namespace

void incrementResponse(const chre_rpc_NumberMessage &response,
                       pw::Status status) {
  if (status.ok()) {
    gResponsePromise.set_value(response.number);
  } else {
    LOGE("Increment failed with status %d", static_cast<int>(status.code()));
  }
}

int main(int argc, char *argv[]) {
  UNUSED_VAR(argc);
  UNUSED_VAR(argv);

  SocketClient socketClient;

  auto callbacks = sp<SocketCallbacks>::make();

  std::unique_ptr<HalRpcClient> rpcClient =
      HalRpcClient::createClient("chre_test_rpc", socketClient, callbacks,
                                 kHostEndpoint, chre::kRpcWorldAppId);

  if (rpcClient == nullptr) {
    LOGE("Failed to create the RPC client");
    return -1;
  }

  if (!rpcClient->hasService(/* id= */ 0xca8f7150a3f05847,
                             /* version= */ 0x01020034)) {
    LOGE("RpcWorld service not found");
    return -1;
  }

  auto client =
      rpcClient->get<chre::rpc::pw_rpc::nanopb::RpcWorldService::Client>();

  chre_rpc_NumberMessage incrementRequest;
  incrementRequest.number = kRequestNumber;

  pw::rpc::NanopbUnaryReceiver<chre_rpc_NumberMessage> call =
      client->Increment(incrementRequest, incrementResponse);

  if (!call.active()) {
    LOGE("Failed to call the service");
    return -1;
  }

  std::future<uint32_t> response = gResponsePromise.get_future();

  if (response.wait_for(std::chrono::seconds(2)) != std::future_status::ready) {
    LOGE("No response received from RPC");
  } else {
    const uint32_t value = response.get();
    LOGI("The RPC service says %" PRIu32 " + 1 = %" PRIu32, kRequestNumber,
         value);
  }

  rpcClient->close();

  return 0;
}
