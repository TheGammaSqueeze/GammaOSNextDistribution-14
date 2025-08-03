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

#define LOG_TAG "MDnsService"

#include "MDnsService.h"

#include <android-base/properties.h>
#include <binder/Status.h>
#include <binder_utils/BinderUtil.h>

using android::net::mdns::aidl::DiscoveryInfo;
using android::net::mdns::aidl::GetAddressInfo;
using android::net::mdns::aidl::IMDnsEventListener;
using android::net::mdns::aidl::RegistrationInfo;
using android::net::mdns::aidl::ResolutionInfo;

using std::literals::chrono_literals::operator""s;

namespace android::net {

#define MDNS_SERVICE_NAME "mdnsd"
#define MDNS_SERVICE_STATUS "init.svc.mdnsd"

// TODO: DnsResolver has same macro definition but returns ScopedAStatus. Move these macros to
// BinderUtil.h to do the same permission check.
#define ENFORCE_ANY_PERMISSION(...)                                \
    do {                                                           \
        binder::Status status = checkAnyPermission({__VA_ARGS__}); \
        if (!status.isOk()) {                                      \
            return status;                                         \
        }                                                          \
    } while (0)

#define ENFORCE_NETWORK_STACK_PERMISSIONS() \
    ENFORCE_ANY_PERMISSION(PERM_NETWORK_STACK, PERM_MAINLINE_NETWORK_STACK)

status_t MDnsService::start() {
    IPCThreadState::self()->disableBackgroundScheduling(true);
    const status_t ret = BinderService<MDnsService>::publish();
    if (ret != android::OK) {
        return ret;
    }
    return android::OK;
}

binder::Status MDnsService::startDaemon() {
    ENFORCE_NETWORK_STACK_PERMISSIONS();
    if (android::base::GetProperty(MDNS_SERVICE_STATUS, "") == "running") {
        return android::binder::Status::fromServiceSpecificError(EBUSY, strerror(EBUSY));
    }

    ALOGD("Starting MDNSD");
    android::base::SetProperty("ctl.start", MDNS_SERVICE_NAME);
    // To maintain the same behavior as before, the returned value is not checked.
    android::base::WaitForProperty(MDNS_SERVICE_STATUS, "running", 5s);
    return binder::Status::ok();
}

binder::Status MDnsService::stopDaemon() {
    ENFORCE_NETWORK_STACK_PERMISSIONS();
    ALOGD("Stopping MDNSD");
    android::base::SetProperty("ctl.stop", MDNS_SERVICE_NAME);
    android::base::WaitForProperty(MDNS_SERVICE_STATUS, "stopped", 5s);
    return binder::Status::ok();
}

binder::Status MDnsService::registerService(const RegistrationInfo&) {
    // TODO(b/298594687): switch from EX_SERVICE_SPECIFIC to DEPRECATED when tethering module
    // for 2024-02 release is fully rolled out and prebuilt updated in AP1A.xxxxxx.yy build.
    // Return EX_SERVICE_SPECIFIC for short-term only because callers in tethering module do not
    // catch the EX_UNSUPPORTED_OPERATION. It will throw an exception and cause a fatal exception.
    // The EX_UNSUPPORTED_OPERATION has been catched in tethering module since 2024-02 release.
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::discover(const DiscoveryInfo&) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::resolve(const ResolutionInfo&) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::getServiceAddress(const GetAddressInfo&) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::stopOperation(int32_t) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::registerEventListener(const android::sp<IMDnsEventListener>&) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

binder::Status MDnsService::unregisterEventListener(const android::sp<IMDnsEventListener>&) {
    // TODO(b/298594687): switch to DEPRECATED.
    return binder::Status::fromExceptionCode(binder::Status::EX_SERVICE_SPECIFIC);
    // DEPRECATED;
}

}  // namespace android::net
