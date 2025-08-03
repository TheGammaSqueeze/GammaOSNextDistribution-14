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

#pragma once

#include <aidl/android/hardware/neuralnetworks/BnBuffer.h>
#include <aidl/android/hardware/neuralnetworks/BnDevice.h>
#include <android/binder_auto_utils.h>

#include <memory>
#include <stack>
#include <string>
#include <utility>
#include <vector>

#include "NeuralNetworksShim.h"
#include "SupportLibrary.h"
#include "SupportLibraryWrapper.h"

#include <android-base/macros.h>
#include <android-base/thread_annotations.h>

namespace aidl::android::hardware::neuralnetworks {

// TODO: This can be templetized.
// Keep track of all sl_rapper::Memory and assign each with a unique token.
class ShimBufferTracker : public std::enable_shared_from_this<ShimBufferTracker> {
    DISALLOW_COPY_AND_ASSIGN(ShimBufferTracker);

   public:
    // A RAII class to help manage the lifetime of the token.
    // It is only supposed to be constructed in ShimBufferTracker::add.
    class Token {
        DISALLOW_COPY_AND_ASSIGN(Token);

       public:
        Token(uint32_t token, std::shared_ptr<ShimBufferTracker> tracker)
            : kToken(token), kBufferTracker(std::move(tracker)) {}
        ~Token() { kBufferTracker->free(kToken); }
        uint32_t get() const { return kToken; }

       private:
        const uint32_t kToken;
        const std::shared_ptr<ShimBufferTracker> kBufferTracker;
    };

    // The factory of ShimBufferTracker. This ensures that the ShimBufferTracker is always managed
    // by a shared_ptr.
    static std::shared_ptr<ShimBufferTracker> create() {
        return std::make_shared<ShimBufferTracker>();
    }

    // Prefer ShimBufferTracker::create.
    ShimBufferTracker() : mTokenToBuffers(1) {}

    std::unique_ptr<Token> add(std::shared_ptr<::android::nn::sl_wrapper::Memory> buffer);
    std::shared_ptr<::android::nn::sl_wrapper::Memory> get(uint32_t token) const;

   private:
    void free(uint32_t token);

    mutable std::mutex mMutex;
    std::stack<uint32_t, std::vector<uint32_t>> mFreeTokens GUARDED_BY(mMutex);

    // Since the tokens are allocated in a non-sparse way, we use a vector to represent the mapping.
    // The index of the vector is the token. When the token gets freed, the corresponding entry is
    // set to nullptr. mTokenToBuffers[0] is always set to nullptr because 0 is an invalid token.
    std::vector<std::shared_ptr<::android::nn::sl_wrapper::Memory>> mTokenToBuffers
            GUARDED_BY(mMutex);
};

}  // namespace aidl::android::hardware::neuralnetworks
