/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "CloseHandleWrapper.h"

#include <android-base/logging.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {
namespace utils {

CloseHandleWrapper::CloseHandleWrapper() : mIsClosed(true) {}

CloseHandleWrapper::CloseHandleWrapper(sp<ICloseHandle> handle) : mHandle(handle) {
    if (handle == nullptr) mIsClosed = true;
}

CloseHandleWrapper::~CloseHandleWrapper() {
    close();
}

CloseHandleWrapper& CloseHandleWrapper::operator=(CloseHandleWrapper&& other) {
    if (this != &other) {
        close();

        mHandle = other.mHandle;
        other.mHandle = nullptr;
        mIsClosed = other.mIsClosed.load();
        other.mIsClosed = true;
    }

    return *this;
}

void CloseHandleWrapper::close() {
    const auto wasClosed = mIsClosed.exchange(true);
    if (wasClosed) return;

    mHandle->close();
    mHandle = nullptr;
}

}  // namespace utils
}  // namespace V1_0
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android
