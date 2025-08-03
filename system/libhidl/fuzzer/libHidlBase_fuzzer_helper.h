/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#ifndef _LIBHIDLBASE_FUZZER_HELPER_H
#define _LIBHIDLBASE_FUZZER_HELPER_H

#include <fuzzer/FuzzedDataProvider.h>
#include <hidl/HidlBinderSupport.h>

using namespace android;
using namespace hardware;
using namespace details;

constexpr uint32_t kMaxBytes = 256;
constexpr uint32_t kMin = 0;

hidl_string createHidlString(FuzzedDataProvider& fdp) {
    auto invokeHidlString = fdp.PickValueInArray<const std::function<hidl_string()>>({
            [&]() { return hidl_string(fdp.ConsumeRandomLengthString(kMaxBytes)); },
            [&]() { return hidl_string((fdp.ConsumeRandomLengthString(kMaxBytes)).c_str()); },
            [&]() {
                std::string testString = fdp.ConsumeRandomLengthString(kMaxBytes);
                return hidl_string(testString.c_str(),
                                   fdp.ConsumeIntegralInRange<uint32_t>(kMin, testString.length()));
            },
            [&]() { return fdp.ConsumeRandomLengthString(kMaxBytes); },
    });
    return invokeHidlString();
}

hidl_memory createHidlMemory(FuzzedDataProvider& fdp) {
    if (fdp.ConsumeBool()) {
        return hidl_memory();
    }
    return hidl_memory(createHidlString(fdp), hidl_handle(),
                       fdp.ConsumeIntegral<uint64_t>() /* size */);
}

Status createStatus(FuzzedDataProvider& fdp) {
    auto invokeStatus = fdp.PickValueInArray<const std::function<Status()>>({
            [&]() { return Status::fromExceptionCode(fdp.ConsumeIntegral<uint32_t>()); },
            [&]() {
                return Status::fromExceptionCode(
                        fdp.ConsumeIntegral<uint32_t>(),
                        (fdp.ConsumeRandomLengthString(kMaxBytes)).c_str());
            },
            [&]() { return Status::fromStatusT(fdp.ConsumeIntegral<uint32_t>()); },
            [&]() { return Status(); },
    });
    return invokeStatus();
}

#endif  // _LIBHIDLBASE_FUZZER_HELPER_H
