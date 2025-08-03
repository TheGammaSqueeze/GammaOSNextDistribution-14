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

#include <libProxyConfig/libProxyConfig.h>

#include <json/json.h>

#include <fstream>
#include <map>
#include <mutex>

namespace android::automotive::proxyconfig {

static std::map<std::string, Service> serviceConfigs;
std::once_flag flag;

static std::string_view proxyConfig =
    "/etc/automotive/proxy_config.json";

void setProxyConfigFile(std::string_view configFile) {
    proxyConfig = configFile;
}

static Service readVmService(Json::Value service) {
    Service vmService;

    vmService.name = service["name"].asString();
    vmService.port = service["port"].asInt();

    return vmService;
}

std::vector<VmProxyConfig> getAllVmProxyConfigs() {
    std::ifstream file(proxyConfig);
    Json::Value jsonConfig;
    file >> jsonConfig;
    std::vector<VmProxyConfig> vmConfigs;

    for (const auto& vm: jsonConfig) {
        VmProxyConfig vmConfig;
        vmConfig.cid = vm["CID"].asInt();
        for (const auto& service: vm["Services"]) {
            vmConfig.services.push_back(readVmService(service));
        }
        vmConfigs.push_back(vmConfig);
    }
    return vmConfigs;
}

static void lazyLoadConfig() {
    std::call_once(flag, [](){
        std::vector<VmProxyConfig> vmConfigs = getAllVmProxyConfigs();
        for (const auto& vmConfig: vmConfigs) {
            for (const auto& service: vmConfig.services) {
                serviceConfigs.insert({service.name, service});
            }
        }
    });
}

std::optional<Service> getServiceConfig(std::string_view name) {
    lazyLoadConfig();
    if (auto entry = serviceConfigs.find(std::string(name)); entry != serviceConfigs.end()) {
        return entry->second;
    }
    return std::nullopt;
}

}  // namespace android::automotive::proxyconfig