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
#pragma once

#include <aidl/android/system/net/netd/BnNetd.h>

namespace android {
namespace net {
namespace aidl {
using NetdHw = ::aidl::android::system::net::netd::BnNetd;
using OemNetwork = ::aidl::android::system::net::netd::INetd::OemNetwork;
using ScopedAStatus = ::ndk::ScopedAStatus;

class NetdHwAidlService : public NetdHw {
  public:
    // Start and run the AIDL service.
    // This blocks when joining the threadpool so start this in a separate thread.
    static void run();
    ScopedAStatus createOemNetwork(OemNetwork* network) override;
    ScopedAStatus destroyOemNetwork(int64_t netHandle) override;
    ScopedAStatus addRouteToOemNetwork(int64_t networkHandle, const std::string& ifname,
                                       const std::string& destination,
                                       const std::string& nexthop) override;
    ScopedAStatus removeRouteFromOemNetwork(int64_t networkHandle, const std::string& ifname,
                                            const std::string& destination,
                                            const std::string& nexthop) override;
    ScopedAStatus addInterfaceToOemNetwork(int64_t networkHandle,
                                           const std::string& ifname) override;
    ScopedAStatus removeInterfaceFromOemNetwork(int64_t networkHandle,
                                                const std::string& ifname) override;
    ScopedAStatus setIpForwardEnable(bool enable) override;
    ScopedAStatus setForwardingBetweenInterfaces(const std::string& inputIfName,
                                                 const std::string& outputIfName,
                                                 bool enable) override;
};

}  // namespace aidl
}  // namespace net
}  // namespace android
