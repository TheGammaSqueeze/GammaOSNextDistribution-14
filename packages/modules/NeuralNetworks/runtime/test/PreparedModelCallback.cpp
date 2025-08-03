/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "PreparedModelCallback.h"

namespace android::nn {

hardware::Return<void> PreparedModelCallback::notifyInternal(
        bool deadObject, ErrorStatus errorStatus, const sp<V1_0::IPreparedModel>& preparedModel) {
    {
        std::lock_guard<std::mutex> hold(mMutex);

        // quick-return if object has already been notified
        if (mNotified) {
            return hardware::Void();
        }

        // store results and mark as notified
        mDeadObject = deadObject;
        mErrorStatus = errorStatus;
        mPreparedModel = preparedModel;
        mNotified = true;
    }

    mCondition.notify_all();
    return hardware::Void();
}

hardware::Return<void> PreparedModelCallback::notify(
        V1_0::ErrorStatus errorStatus, const sp<V1_0::IPreparedModel>& preparedModel) {
    return notifyInternal(false, uncheckedConvert(errorStatus), preparedModel);
}

hardware::Return<void> PreparedModelCallback::notify_1_2(
        V1_0::ErrorStatus errorStatus, const sp<V1_2::IPreparedModel>& preparedModel) {
    return notifyInternal(false, uncheckedConvert(errorStatus), preparedModel);
}

hardware::Return<void> PreparedModelCallback::notify_1_3(
        V1_3::ErrorStatus errorStatus, const sp<V1_3::IPreparedModel>& preparedModel) {
    return notifyInternal(false, uncheckedConvert(errorStatus), preparedModel);
}

void PreparedModelCallback::notifyAsDeadObject() {
    notifyInternal(true, ErrorStatus::GENERAL_FAILURE, nullptr);
}

void PreparedModelCallback::wait() const {
    std::unique_lock<std::mutex> lock(mMutex);
    mCondition.wait(lock, [this] { return mNotified; });
}

ErrorStatus PreparedModelCallback::getStatus() const {
    wait();
    return mErrorStatus;
}

sp<V1_0::IPreparedModel> PreparedModelCallback::getPreparedModel() const {
    wait();
    return mPreparedModel;
}

bool PreparedModelCallback::isDeadObject() const {
    wait();
    return mDeadObject;
}

}  // namespace android::nn
