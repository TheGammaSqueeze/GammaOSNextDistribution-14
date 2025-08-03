// Copyright (C) 2022 The Android Open Source Project
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

#include "ServiceConfig.h"

#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <getopt.h>

#include <utility>
#include <vector>

namespace audio_proxy::service {
namespace {
std::pair<std::string, StreamConfig> parseStreamConfig(const char* optarg) {
  std::vector<std::string> tokens = android::base::Split(optarg, ":");
  if (tokens.size() != 3) {
    return {};
  }

  StreamConfig config;
  if (!android::base::ParseUint(tokens[1].c_str(), &config.bufferSizeMs)) {
    return {};
  }

  if (!android::base::ParseUint(tokens[2].c_str(), &config.latencyMs)) {
    return {};
  }

  return {tokens[0], config};
}
}  // namespace

std::optional<ServiceConfig> parseServiceConfigFromCommandLine(int argc,
                                                               char** argv) {
  // $command --name service_name
  //   --stream address1:buffer_size:latency
  //   --stream address2:buffer_size:latency
  static option options[] = {
      {"name", required_argument, nullptr, 'n'},
      {"stream", required_argument, nullptr, 's'},
      {nullptr, 0, nullptr, 0},
  };

  // Reset, this is useful in unittest.
  optind = 0;

  ServiceConfig config;
  int val = 0;
  while ((val = getopt_long(argc, argv, "n:s:", options, nullptr)) != -1) {
    switch (val) {
      case 'n':
        config.name = optarg;
        break;

      case 's': {
        std::pair<std::string, StreamConfig> streamConfig =
            parseStreamConfig(optarg);
        if (streamConfig.first.empty()) {
          return std::nullopt;
        }

        auto it = config.streams.emplace(std::move(streamConfig));
        if (!it.second) {
          return std::nullopt;
        }

        break;
      }

      default:
        break;
    }
  }

  if (config.name.empty() || config.streams.empty()) {
    return std::nullopt;
  }

  return config;
}

}  // namespace audio_proxy::service