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

#include <hidl/HidlBinderSupport.h>
#include <libHidlBase_fuzzer_helper.h>

constexpr size_t kMinByte = 0;
constexpr size_t kMaxByte = 256;
constexpr uint32_t kNumFds = 0;
constexpr uint32_t kNumInts = 1;
constexpr uint32_t kMinSize = 1;
constexpr uint32_t kMaxSize = 1000;
constexpr uint32_t kMax = 1024;

class HidlBaseParcelFuzzer {
  public:
    HidlBaseParcelFuzzer(const uint8_t* data, size_t size) : mFdp(data, size){};
    void process();

  private:
    void createRandomParcel(Parcel& parcel);

    FuzzedDataProvider mFdp;
};

void HidlBaseParcelFuzzer::createRandomParcel(Parcel& parcel) {
    uint32_t iterCount = mFdp.ConsumeIntegralInRange<uint32_t>(kMin, kMax);
    for (uint32_t idx = 0; idx < iterCount; ++idx) {
        auto invokeHidlBaseParcelWriteAPI = mFdp.PickValueInArray<const std::function<void()>>({
                [&]() {
                    hidl_memory memory;
                    native_handle_t* testNativeHandle = nullptr;
                    if (mFdp.ConsumeBool()) {
                        memory = createHidlMemory(mFdp);
                    } else {
                        hidl_string hidlString = createHidlString(mFdp);
                        testNativeHandle = native_handle_create(
                                mFdp.ConsumeIntegralInRange<uint32_t>(kMin, NATIVE_HANDLE_MAX_FDS),
                                mFdp.ConsumeIntegralInRange<uint32_t>(kMin,
                                                                      NATIVE_HANDLE_MAX_INTS));
                        memory = hidl_memory(hidlString, testNativeHandle,
                                             mFdp.ConsumeIntegral<uint64_t>());
                    }
                    writeEmbeddedToParcel(
                            memory, &parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                    native_handle_close(testNativeHandle);
                    native_handle_delete(testNativeHandle);
                },
                [&]() {
                    MQDescriptorSync<uint8_t> descriptorSync;
                    auto testHandle = native_handle_create(kNumFds, kNumInts);
                    uint32_t size = sizeof(uint8_t);
                    testHandle->data[0] = size;
                    uint32_t bufferSize = mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize);
                    if (mFdp.ConsumeBool()) {
                        std::vector<GrantorDescriptor> grantDescriptor(bufferSize);
                        for (size_t idx = 0; idx < bufferSize; ++idx) {
                            grantDescriptor[idx] = {mFdp.ConsumeIntegral<uint32_t>() /* flags */,
                                                    mFdp.ConsumeIntegral<uint32_t>() /* fdIndex */,
                                                    mFdp.ConsumeIntegral<uint32_t>() /* offset */,
                                                    mFdp.ConsumeIntegral<uint64_t>() /* extent */};
                        }
                        descriptorSync =
                                MQDescriptorSync<uint8_t>{grantDescriptor, testHandle, size};
                    } else {
                        descriptorSync = MQDescriptorSync<uint8_t>{bufferSize, testHandle, size,
                                                                   mFdp.ConsumeBool()};
                    }
                    writeEmbeddedToParcel(
                            descriptorSync, &parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                },
                [&]() {
                    native_handle_t* testNativeHandle = native_handle_create(
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMin, NATIVE_HANDLE_MAX_FDS),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMin, NATIVE_HANDLE_MAX_INTS));
                    writeEmbeddedToParcel(
                            testNativeHandle, &parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                    native_handle_close(testNativeHandle);
                    native_handle_delete(testNativeHandle);
                },
                [&]() {
                    Status status = createStatus(mFdp);
                    writeToParcel(status, &parcel);
                },
                [&]() {
                    auto parcelSize = mFdp.ConsumeIntegralInRange<size_t>(kMinByte, kMaxByte);
                    std::vector<uint8_t> data = mFdp.ConsumeBytes<uint8_t>(parcelSize);
                    parcel.write(data.data(), data.size());
                },
        });
        invokeHidlBaseParcelWriteAPI();
    }
}

void HidlBaseParcelFuzzer::process() {
    Parcel parcel;
    size_t originalPosition = parcel.dataPosition();
    createRandomParcel(parcel);
    parcel.setDataPosition(originalPosition);
    while (mFdp.remaining_bytes()) {
        auto invokeHidlBaseParcelAPI = mFdp.PickValueInArray<const std::function<void()>>({
                [&]() {
                    hidl_memory memory = createHidlMemory(mFdp);
                    readEmbeddedFromParcel(
                            memory, parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                },
                [&]() {
                    MQDescriptorSync<uint8_t> descriptorSync;
                    readEmbeddedFromParcel(
                            descriptorSync, parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                },
                [&]() {
                    hidl_handle handle;
                    readEmbeddedFromParcel(
                            handle, parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                },
                [&]() {
                    hidl_string hidlString = createHidlString(mFdp);
                    readEmbeddedFromParcel(
                            hidlString, parcel,
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize),
                            mFdp.ConsumeIntegralInRange<uint32_t>(kMinSize, kMaxSize));
                },
                [&]() {
                    Status status = createStatus(mFdp);
                    readFromParcel(&status, parcel);
                },
        });
        invokeHidlBaseParcelAPI();
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    HidlBaseParcelFuzzer hidlBaseParcelFuzzer(data, size);
    hidlBaseParcelFuzzer.process();
    return 0;
}
