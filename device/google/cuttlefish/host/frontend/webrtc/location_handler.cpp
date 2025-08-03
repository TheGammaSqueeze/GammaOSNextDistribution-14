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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "host/frontend/webrtc/location_handler.h"
#include <android-base/logging.h>
#include <unistd.h>
#include "host/libs/config/cuttlefish_config.h"
#include "host/libs/location/GnssClient.h"

#include <sstream>
#include <vector>
using namespace std;

namespace cuttlefish {
namespace webrtc_streaming {

LocationHandler::LocationHandler(
    std::function<void(const uint8_t *, size_t)> send_to_client) {}

LocationHandler::~LocationHandler() {}

void LocationHandler::HandleMessage(const float longitude,
                                          const float latitude,
                                          const float elevation) {
  auto config = CuttlefishConfig::Get();
  if (!config) {
    LOG(ERROR) << "Failed to obtain config object";
    return;
  }
  auto instance = config->ForDefaultInstance();
  auto server_port = instance.gnss_grpc_proxy_server_port();
  std::string socket_name =
      std::string("localhost:") + std::to_string(server_port);
  GnssClient gpsclient(
      grpc::CreateChannel(socket_name, grpc::InsecureChannelCredentials()));

  GpsFixArray coordinates;
  GpsFix location;
  location.longitude=longitude;
  location.latitude=latitude;
  location.elevation=elevation;
  coordinates.push_back(location);

  auto reply = gpsclient.SendGpsLocations(1000,coordinates);
  LOG(INFO) << "Server port: " << server_port << " socket: " << socket_name
            << std::endl;
}

}  // namespace webrtc_streaming
}  // namespace cuttlefish
