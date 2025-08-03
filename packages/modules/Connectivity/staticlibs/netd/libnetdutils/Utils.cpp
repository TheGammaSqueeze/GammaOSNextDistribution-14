/*
 * Copyright (C) 2021 The Android Open Source Project
 *
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

#include <map>
#include <vector>

#include <net/if.h>

#include "dirent.h"
#include "netdutils/Status.h"
#include "netdutils/Utils.h"

namespace android {
namespace netdutils {

StatusOr<std::vector<std::string>> getIfaceNames() {
    std::vector<std::string> ifaceNames;
    DIR* d;
    struct dirent* de;

    if (!(d = opendir("/sys/class/net"))) {
        return statusFromErrno(errno, "Cannot open iface directory");
    }
    while ((de = readdir(d))) {
        if ((de->d_type != DT_DIR) && (de->d_type != DT_LNK)) continue;
        if (de->d_name[0] == '.') continue;
        ifaceNames.push_back(std::string(de->d_name));
    }
    closedir(d);
    return ifaceNames;
}

StatusOr<std::map<std::string, uint32_t>> getIfaceList() {
    std::map<std::string, uint32_t> ifacePairs;

    ASSIGN_OR_RETURN(auto ifaceNames, getIfaceNames());

    for (const auto& name : ifaceNames) {
        uint32_t ifaceIndex = if_nametoindex(name.c_str());
        if (ifaceIndex) {
            ifacePairs.insert(std::pair<std::string, uint32_t>(name, ifaceIndex));
        }
    }
    return ifacePairs;
}

}  // namespace netdutils
}  // namespace android
