/*
 * Copyright (c) 2021, The Android Open Source Project
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

#include "CarTelemetryInternalImpl.h"

#include <aidl/android/automotive/telemetry/internal/BnCarDataListener.h>
#include <aidl/android/automotive/telemetry/internal/CarDataInternal.h>
#include <aidl/android/automotive/telemetry/internal/ICarDataListener.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

namespace android {
namespace automotive {
namespace telemetry {

using ::aidl::android::automotive::telemetry::internal::BnCarDataListener;
using ::aidl::android::automotive::telemetry::internal::CarDataInternal;
using ::aidl::android::automotive::telemetry::internal::ICarDataListener;
using ::android::base::StringPrintf;

CarTelemetryInternalImpl::CarTelemetryInternalImpl(TelemetryServer* server) :
      mTelemetryServer(server),
      mBinderDeathRecipient(
              ::AIBinder_DeathRecipient_new(CarTelemetryInternalImpl::listenerBinderDied)) {}

ndk::ScopedAStatus CarTelemetryInternalImpl::setListener(
        const std::shared_ptr<ICarDataListener>& listener) {
    LOG(VERBOSE) << "Received a setListener call";
    mTelemetryServer->setListener(listener);

    // If passed a local binder, AIBinder_linkToDeath will do nothing and return
    // STATUS_INVALID_OPERATION. We ignore this case because we only use local binders in tests
    // where this is not an error.
    if (!listener->isRemote()) {
        return ndk::ScopedAStatus::ok();
    }

    auto status = ndk::ScopedAStatus::fromStatus(
            ::AIBinder_linkToDeath(listener->asBinder().get(), mBinderDeathRecipient.get(), this));
    if (!status.isOk()) {
        LOG(WARNING) << __func__ << ": Failed to linkToDeath: " << status.getMessage();
        mTelemetryServer->clearListener();
        return ndk::ScopedAStatus::fromExceptionCodeWithMessage(::EX_ILLEGAL_STATE,
                                                                status.getMessage());
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus CarTelemetryInternalImpl::clearListener() {
    auto listener = mTelemetryServer->getListener();
    mTelemetryServer->clearListener();
    if (listener == nullptr) {
        return ndk::ScopedAStatus::ok();
    }
    auto status = ndk::ScopedAStatus::fromStatus(
            ::AIBinder_unlinkToDeath(listener->asBinder().get(), mBinderDeathRecipient.get(),
                                     this));
    if (!status.isOk()) {
        LOG(WARNING) << __func__
                     << ": unlinkToDeath failed, continuing anyway: " << status.getMessage();
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus CarTelemetryInternalImpl::addCarDataIds(const std::vector<int32_t>& ids) {
    mTelemetryServer->addCarDataIds(ids);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus CarTelemetryInternalImpl::removeCarDataIds(const std::vector<int32_t>& ids) {
    mTelemetryServer->removeCarDataIds(ids);
    return ndk::ScopedAStatus::ok();
}

binder_status_t CarTelemetryInternalImpl::dump(int fd, const char** args, uint32_t numArgs) {
    dprintf(fd, "ICarTelemetryInternal:\n");
    mTelemetryServer->dump(fd);
    return ::STATUS_OK;
}

// Removes the listener if its binder dies.
void CarTelemetryInternalImpl::listenerBinderDiedImpl() {
    LOG(WARNING) << "A ICarDataListener died, clearing the listener.";
    mTelemetryServer->clearListener();
}

// static
void CarTelemetryInternalImpl::listenerBinderDied(void* cookie) {
    // We expect the pointer to be alive as there is only a single instance of
    // CarTelemetryInternalImpl and if it dies, the whole process should die too.
    auto thiz = static_cast<CarTelemetryInternalImpl*>(cookie);
    thiz->listenerBinderDiedImpl();
}

}  // namespace telemetry
}  // namespace automotive
}  // namespace android
