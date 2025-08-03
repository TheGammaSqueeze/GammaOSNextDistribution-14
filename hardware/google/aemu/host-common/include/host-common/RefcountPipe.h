// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include "AndroidPipe.h"

#include <fstream>
#include <functional>

namespace android {
namespace emulation {

using OnLastColorBufferRef = std::function<void(uint32_t)>;

// This is a pipe for refcounting guest side objects.
class RefcountPipe final : public AndroidPipe {
public:
    class Service final : public AndroidPipe::Service {
    public:
        Service();
        AndroidPipe* create(void* hwPipe, const char* args, enum AndroidPipeFlags flags) override;
        AndroidPipe* load(void* hwPipe,
                          const char* args,
                          android::base::Stream* stream) override;
        bool canLoad() const override;
    };

    RefcountPipe(void* hwPipe,
                 Service* svc,
                 base::Stream* loadStream = nullptr);
    ~RefcountPipe();
    void onGuestClose(PipeCloseReason reason) override;
    unsigned onGuestPoll() const override;
    int onGuestRecv(AndroidPipeBuffer* buffers, int numBuffers) override;
    int onGuestSend(const AndroidPipeBuffer* buffers, int numBuffers,
                    void** newPipePtr) override;
    void onGuestWantWakeOn(int flags) override {}
    void onSave(base::Stream* stream) override;

private:
    uint32_t mHandle;
};

void registerRefcountPipeService();

void registerOnLastRefCallback(OnLastColorBufferRef func);

}  // namespace emulation
}  // namespace android
