/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "ShimBufferTracker"

#include "ShimBufferTracker.h"
#include "ShimDevice.h"

#include <android-base/logging.h>

#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

using namespace ::android::nn::sl_wrapper;

namespace aidl::android::hardware::neuralnetworks {

std::unique_ptr<ShimBufferTracker::Token> ShimBufferTracker::add(
        std::shared_ptr<::android::nn::sl_wrapper::Memory> buffer) {
    if (buffer == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::mutex> guard(mMutex);
    uint32_t token = 0;
    if (mFreeTokens.empty()) {
        token = mTokenToBuffers.size();
        mTokenToBuffers.push_back(std::move(buffer));
    } else {
        token = mFreeTokens.top();
        mFreeTokens.pop();
        mTokenToBuffers[token] = std::move(buffer);
    }
    return std::make_unique<Token>(token, shared_from_this());
}

std::shared_ptr<::android::nn::sl_wrapper::Memory> ShimBufferTracker::get(uint32_t token) const {
    std::lock_guard<std::mutex> guard(mMutex);
    if (mTokenToBuffers.size() <= token || mTokenToBuffers[token] == nullptr) {
        LOG(ERROR) << "ShimBufferTracker::get -- unknown token " << token;
        return nullptr;
    }
    return mTokenToBuffers[token];
}

void ShimBufferTracker::free(uint32_t token) {
    std::lock_guard<std::mutex> guard(mMutex);
    CHECK_LT(token, mTokenToBuffers.size());
    CHECK(mTokenToBuffers[token] != nullptr);
    mTokenToBuffers[token] = nullptr;
    mFreeTokens.push(token);
}

}  // namespace aidl::android::hardware::neuralnetworks
