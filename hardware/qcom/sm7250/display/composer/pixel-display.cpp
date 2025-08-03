/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "pixel-display.h"

#include <utils/Errors.h>
#include <sys/types.h>
#include <hwc_session.h>

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using ::aidl::com::google::hardware::pixel::display::Display;

void DisplayInit(sdm::HWCSession *hwc_session) {
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    std::shared_ptr<::aidl::com::google::hardware::pixel::display::Display> display =
        ndk::SharedRefBase::make<Display>(hwc_session);
    LOG(INFO) << "pixel display service start...";
    const std::string instance = std::string() + Display::descriptor + "/default";
    binder_status_t status =
        AServiceManager_addService(display->asBinder().get(), instance.c_str());
    CHECK(status == STATUS_OK);

    ABinderProcess_startThreadPool();
}

namespace aidl {
namespace com {
namespace google {
namespace hardware {
namespace pixel {
namespace display {

// ----------------------------------------------------------------------------
Display::Display(HWCSession *hwc_session):mHWCSession(hwc_session) {
}

ndk::ScopedAStatus Display::isHbmSupported(bool *_aidl_return) {
    if (mHWCSession) {
        *_aidl_return = mHWCSession->IsHbmSupported();

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setHbmState(HbmState state) {
    if (mHWCSession) {
        mHWCSession->SetHbmState(state);

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::getHbmState(HbmState *_aidl_return) {
    if (mHWCSession) {
        *_aidl_return = mHWCSession->GetHbmState();

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::isLbeSupported(bool *_aidl_return) {
    if (mHWCSession) {
        *_aidl_return = mHWCSession->IsLbeSupported();

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setLbeState(LbeState  state) {
    if (mHWCSession) {
        mHWCSession->SetLbeState(state);

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setLbeAmbientLight(int ambientLux) {
    if (mHWCSession) {
        mHWCSession->SetLbeAmbientLight(ambientLux);

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::getLbeState(LbeState* _aidl_return) {
    if (mHWCSession) {
        *_aidl_return = mHWCSession->GetLbeState();

        return ndk::ScopedAStatus::ok();
    }

    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}
}  // namespace display
}  // namespace pixel
}  // namespace hardware
}  // namespace google
}  // namespace com
}  // namespace aidl
