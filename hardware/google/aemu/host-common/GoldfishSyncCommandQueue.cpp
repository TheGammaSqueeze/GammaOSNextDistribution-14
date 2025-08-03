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

#include "GoldfishSyncCommandQueue.h"

#include <memory>
#include <string>
#include <vector>

namespace android {

using base::Stream;

static GoldfishSyncCommandQueue* sCommandQueue() {
    static GoldfishSyncCommandQueue* s = new GoldfishSyncCommandQueue;
    return s;
}

// static
void GoldfishSyncCommandQueue::initThreading(VmLock* vmLock) {
    // TODO: trivial timer implementation for now.
    sCommandQueue()->init(vmLock, {
            [](DeviceContextRunner<GoldfishSyncWakeInfo>* dcr, std::function<void()> installedCallback) {
                (void)dcr;
                (void)installedCallback;
            },
            [](DeviceContextRunner<GoldfishSyncWakeInfo>* dcr) {
                (void)dcr;
            },
            [](DeviceContextRunner<GoldfishSyncWakeInfo>* dcr, uint64_t timeout) {
                (void)dcr;
                (void)timeout;
            },
     });
}

// static
void GoldfishSyncCommandQueue::setQueueCommand(queue_device_command_t fx) {
    GoldfishSyncCommandQueue* cmdQueue = sCommandQueue();
    cmdQueue->tellSyncDevice = fx;
}

// static
void GoldfishSyncCommandQueue::hostSignal(uint32_t cmd,
                                          uint64_t handle,
                                          uint32_t time_arg,
                                          uint64_t hostcmd_handle) {

    GoldfishSyncCommandQueue* queue = sCommandQueue();

    GoldfishSyncWakeInfo sync_data;
    sync_data.cmd = cmd;
    sync_data.handle = handle;
    sync_data.time_arg = time_arg;
    sync_data.hostcmd_handle = hostcmd_handle;

    queue->queueDeviceOperation(sync_data);
}

// static
void GoldfishSyncCommandQueue::save(Stream* stream) {
    GoldfishSyncCommandQueue* queue = sCommandQueue();
    stream->putBe32(queue->numPending());
    queue->forEachPendingOperation([stream](const GoldfishSyncWakeInfo& wakeInfo) {
            stream->putBe64(wakeInfo.handle);
            stream->putBe64(wakeInfo.hostcmd_handle);
            stream->putBe32(wakeInfo.cmd);
            stream->putBe32(wakeInfo.time_arg);
    });
}

// static
void GoldfishSyncCommandQueue::load(Stream* stream) {
    GoldfishSyncCommandQueue* queue = sCommandQueue();
    queue->removeAllPendingOperations(
        [](const GoldfishSyncWakeInfo&) { return true; });
    uint32_t pending = stream->getBe32();
    for (uint32_t i = 0; i < pending; i++) {
        GoldfishSyncWakeInfo cmd = {
            stream->getBe64(), // handle
            stream->getBe64(), // hostcmd_handle
            stream->getBe32(), // cmd
            stream->getBe32(), // time_arg
        };
        queue->queueDeviceOperation(cmd);
    }
}

void GoldfishSyncCommandQueue::performDeviceOperation
    (const GoldfishSyncWakeInfo& wakeInfo) {
    tellSyncDevice(wakeInfo.cmd,
                   wakeInfo.handle,
                   wakeInfo.time_arg,
                   wakeInfo.hostcmd_handle);
}

} // namespace android
