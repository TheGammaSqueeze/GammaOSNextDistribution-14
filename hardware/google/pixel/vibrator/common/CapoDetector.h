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

#include <chre_host/host_protocol_host.h>
#include <chre_host/socket_client.h>

#include <chrono>

#include "proto/capo.pb.h"

using android::sp;
using android::chre::HostProtocolHost;
using android::chre::IChreMessageHandlers;
using android::chre::SocketClient;

// following convention of CHRE code.
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

#define NS_FROM_MS(x) ((x)*1000000)

struct CapoMDParams {
    uint64_t still_time_threshold_ns;
    uint32_t window_width_ns;
    float motion_confidence_threshold;
    float still_confidence_threshold;
    float var_threshold;
    float var_threshold_delta;
};

class CapoDetector : public android::chre::SocketClient::ICallbacks,
                     public android::chre::IChreMessageHandlers,
                     public android::chre::SocketClient {
  public:
    // Typedef declaration for callback function.
    typedef std::function<void(uint8_t)> cb_fn_t;

    // Called when initializing connection with CHRE socket.
    static android::sp<CapoDetector> start();
    // Common getTime function to share
    static uint32_t getCurrentTimeInMs() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                .count();
    }
    // Called when the socket is successfully (re-)connected.
    // Reset the position and try to send NanoappList request.
    void onConnected() override;
    // Called when we have failed to (re-)connect the socket after many attempts
    // and are giving up.
    void onConnectionAborted() override;
    // Invoked when the socket is disconnected, and this connection loss
    // was not the result of an explicit call to disconnect().
    // Reset the position while disconnecting.
    void onDisconnected() override;
    // Decode unix socket msgs to CHRE messages, and call the appropriate
    // callback depending on the CHRE message.
    void onMessageReceived(const void *data, size_t length) override;
    // Listen for messages from capo nanoapp and handle the message.
    void handleNanoappMessage(const ::chre::fbs::NanoappMessageT &message) override;
    // Handle the response of a NanoappList request.
    // Ensure that capo nanoapp is running.
    void handleNanoappListResponse(const ::chre::fbs::NanoappListResponseT &response) override;
    // Send enabling message to the nanoapp.
    void enable();
    // Get last carried position type and time simultaneously.
    void getCarriedPositionInfo(uint8_t *position, uint32_t *time);
    // Get last carried position type.
    uint8_t getCarriedPosition() { return last_position_type_; }
    // Get the host endpoint.
    uint16_t getHostEndPoint() { return kHostEndpoint; }
    // Get the capo nanoapp ID.
    uint64_t getNanoppAppId() { return kCapoNanoappId; }
    // Set up callback_func_ if needed.
    void setCallback(cb_fn_t cb) { callback_func_ = cb; }

  private:
    // Nanoapp ID of capo, ref: go/nanoapp-id-tracker.
    static constexpr uint64_t kCapoNanoappId = 0x476f6f676c001020ULL;
    // String of socket name for connecting chre.
    static constexpr char kChreSocketName[] = "chre";
    // The host endpoint we use when sending message.
    // Set with 0x9020 based on 0x8000 AND capo_app_id(1020).
    // Ref: go/host-endpoint-id-tracker.
    static constexpr uint16_t kHostEndpoint = 0x9020;
    // Using for hal layer callback function.
    cb_fn_t callback_func_ = nullptr;
    // Last carried position received from the nano app
    capo::PositionType last_position_type_ = capo::PositionType::UNKNOWN;
    // Last face up event
    uint32_t mLastFaceUpEvent = 0;
    // Mutex for time + position tuple
    std::mutex mCapoMutex;
    // Motion detector parameters for host-driven capo config
    const struct CapoMDParams mCapoDetectorMDParameters {
        .still_time_threshold_ns = NS_FROM_MS(500), .window_width_ns = NS_FROM_MS(100),
        .motion_confidence_threshold = 0.98f, .still_confidence_threshold = 0.99f,
        .var_threshold = 0.0125f, .var_threshold_delta = 0.0125f,
    };
};

}  // namespace chre
}  // namespace android
