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

#include <functional>
#include <memory>

#include "fuzzer/FuzzedDataProvider.h"
#include "keymaster/serializable.h"

static constexpr uint16_t kMinBufferSize = 1;
static constexpr uint16_t kMaxBufferSize = 2048;
static constexpr uint16_t kMaxOperations = 1000;

std::vector<std::function<void(keymaster::Buffer*, FuzzedDataProvider*)>> operations = {

    [](keymaster::Buffer* buf, FuzzedDataProvider*) -> void {
        // Just reading values, but there's some interesting
        // integer manipulation here.
        buf->begin();
        buf->end();
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider*) -> void { buf->Clear(); },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        buf->reserve(fdp->ConsumeIntegralInRange<int>(kMinBufferSize, kMaxBufferSize));
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        buf->advance_write(fdp->ConsumeIntegral<int>());
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        buf->Reinitialize(fdp->ConsumeIntegralInRange<size_t>(kMinBufferSize, kMaxBufferSize));
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        size_t buf_size = fdp->ConsumeIntegralInRange<size_t>(kMinBufferSize, kMaxBufferSize);
        std::unique_ptr<uint8_t[]> in_buf = std::unique_ptr<uint8_t[]>(new uint8_t[buf_size]);
        buf->Reinitialize(in_buf.get(), buf_size);
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        uint16_t buf_size = fdp->ConsumeIntegralInRange<uint16_t>(kMinBufferSize, kMaxBufferSize);
        std::unique_ptr<uint8_t[]> in_buf = std::unique_ptr<uint8_t[]>(new uint8_t[buf_size]);
        const uint8_t* data_ptr = in_buf.get();
        int32_t end = fdp->ConsumeIntegralInRange<int32_t>(0, buf_size);
        buf->Deserialize(&data_ptr, data_ptr + end);
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        uint16_t buf_size = buf->SerializedSize();
        std::unique_ptr<uint8_t[]> out_buf = std::unique_ptr<uint8_t[]>(new uint8_t[buf_size]);
        int32_t end = fdp->ConsumeIntegralInRange<int32_t>(0, buf_size);
        buf->Serialize(out_buf.get(), out_buf.get() + end);
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        uint16_t buf_size = fdp->ConsumeIntegralInRange<uint16_t>(kMinBufferSize, kMaxBufferSize);
        std::vector<uint8_t> in_buf = fdp->ConsumeBytes<uint8_t>(buf_size);
        buf->write(in_buf.data(), fdp->ConsumeIntegralInRange<int16_t>(0, buf_size));
    },
    [](keymaster::Buffer* buf, FuzzedDataProvider* fdp) -> void {
        uint16_t buf_size = fdp->ConsumeIntegralInRange<uint16_t>(kMinBufferSize, kMaxBufferSize);
        std::unique_ptr<uint8_t[]> out = std::unique_ptr<uint8_t[]>(new uint8_t[buf_size]);
        buf->read(out.get(), fdp->ConsumeIntegralInRange<int16_t>(0, buf_size));
    }};

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);
    uint16_t buf_size = fdp.ConsumeIntegralInRange<uint16_t>(kMinBufferSize, kMaxBufferSize);
    keymaster::Buffer fuzzBuffer(buf_size);
    for (size_t i = 0; i < kMaxOperations && fdp.remaining_bytes() > 0; i++) {
        uint8_t op = fdp.ConsumeIntegralInRange<uint8_t>(0, operations.size() - 1);
        operations[op](&fuzzBuffer, &fdp);
    }
    return 0;
}
