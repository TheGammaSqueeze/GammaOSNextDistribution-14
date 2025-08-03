/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <android/net/mdns/aidl/BnMDns.h>
#include <binder/BinderService.h>

namespace android::net {

class MDnsService : public BinderService<MDnsService>, public android::net::mdns::aidl::BnMDns {
  public:
    static status_t start();
    static char const* getServiceName() { return "mdns"; }

    binder::Status startDaemon() override;
    binder::Status stopDaemon() override;
    binder::Status registerService(
            const ::android::net::mdns::aidl::RegistrationInfo& info) override;
    binder::Status discover(const ::android::net::mdns::aidl::DiscoveryInfo& info) override;
    binder::Status resolve(const ::android::net::mdns::aidl::ResolutionInfo& info) override;
    binder::Status getServiceAddress(
            const ::android::net::mdns::aidl::GetAddressInfo& info) override;
    binder::Status stopOperation(int32_t id) override;
    binder::Status registerEventListener(
            const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener) override;
    binder::Status unregisterEventListener(
            const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener) override;
};

}  // namespace android::net
