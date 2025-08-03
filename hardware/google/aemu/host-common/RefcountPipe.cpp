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

#include "RefcountPipe.h"

#include "aemu/base/ArraySize.h"
#include "refcount-pipe.h"

#include <string.h>

using android::base::arraySize;

namespace android {
namespace emulation {

static OnLastColorBufferRef* sOnLastColorBufferRef = new OnLastColorBufferRef;

RefcountPipe::RefcountPipe(void* hwPipe, Service* svc, base::Stream* loadStream)
    : AndroidPipe(hwPipe, svc) {
    if (loadStream) {
        mHandle = loadStream->getBe32();
    } else {
        mHandle = 0;
    }
}

RefcountPipe::~RefcountPipe() {
    OnLastColorBufferRef func = *sOnLastColorBufferRef;
    if (func != nullptr)
        func(mHandle);
}

void RefcountPipe::onGuestClose(PipeCloseReason reason) {
    delete this;
}

unsigned RefcountPipe::onGuestPoll() const {
    // Guest can always write
    return PIPE_POLL_OUT;
}

int RefcountPipe::onGuestRecv(AndroidPipeBuffer* buffers, int numBuffers) {
    // Guest is not supposed to read
    return PIPE_ERROR_IO;
}

int RefcountPipe::onGuestSend(const AndroidPipeBuffer* buffers,
                              int numBuffers,
                              void** newPipePtr) {
    int result = 0;
    char forRecv[4] = {};

    while (numBuffers > 0 && arraySize(forRecv) - result >= buffers->size) {
        memcpy(forRecv + result, buffers->data, buffers->size);
        result += static_cast<int>(buffers->size);
        buffers++;
        numBuffers--;
    }

    if (result == arraySize(forRecv)) {
        memcpy(&mHandle, forRecv, arraySize(forRecv));
    }

    return result;
}

void RefcountPipe::onSave(base::Stream* stream) {
    stream->putBe32(mHandle);
}

void registerRefcountPipeService() {
    AndroidPipe::Service::add(std::make_unique<RefcountPipe::Service>());
}

////////////////////////////////////////////////////////////////////////////////

RefcountPipe::Service::Service() : AndroidPipe::Service("refcount") {}

AndroidPipe* RefcountPipe::Service::create(void* hwPipe, const char* args,
                                           enum AndroidPipeFlags flags) {
    return new RefcountPipe(hwPipe, this);
}

AndroidPipe* RefcountPipe::Service::load(void* hwPipe,
                                         const char* args,
                                         android::base::Stream* stream) {
    return new RefcountPipe(hwPipe, this, stream);
}

bool RefcountPipe::Service::canLoad() const {
    return true;
}

void registerOnLastRefCallback(OnLastColorBufferRef func) {
    *sOnLastColorBufferRef = func;
}

}  // namespace emulation
}  // namespace android

void android_init_refcount_pipe(void) {
    android::emulation::registerRefcountPipeService();
}
