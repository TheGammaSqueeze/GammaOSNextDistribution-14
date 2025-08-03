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

#include "host-common/DeviceContextRunner.h"
#include "host-common/goldfish_sync.h"
#include "host-common/VmLock.h"
#include "aemu/base/files/Stream.h"

#include <vector>

#define DEBUG 0

#if DEBUG
#define DPRINT(...) do { \
    if (!VERBOSE_CHECK(goldfishsync)) VERBOSE_ENABLE(goldfishsync); \
    VERBOSE_TID_FUNCTION_DPRINT(goldfishsync, __VA_ARGS__); } while(0)
#else
#define DPRINT(...)
#endif

#define ERR(...) do { \
    derror(__VA_ARGS__); \
} while(0)

namespace android {

////////////////////////////////////////////////////////////////////////////////
// GoldfishSyncCommandQueue ensures that commands sent to the Goldfish Sync
// virtual device take place in "device context"; that is, the commands
// are only executed while the main loop has the VM lock. Like PipeWaker
// of AndroidPipe, this class derives from DeviceContextRunner for this
// functionality.
// This class is only used for host->guest commands for goldfish sync device,
// and mainly timeline increment at that. The way to use
// GoldfishSyncCommandQueue in general is to call |hostSignal| with the
// particular details of the host->guest command being issued.
//
// However, make sure that |setQueueCommand| has been called on
// goldifish sync device initialization, which properly hooks up the
// GoldfishSyncCommandQueue with the particular virtual device function
// that actually raises IRQ's, and that |initThreading| has been called
// in a main loop context, preferably in qemu-setup.cpp.

struct GoldfishSyncWakeInfo {
    uint64_t handle;
    uint64_t hostcmd_handle;
    uint32_t cmd;
    uint32_t time_arg;
};

class GoldfishSyncCommandQueue final :
    public DeviceContextRunner<GoldfishSyncWakeInfo> {

public:
    // Like with the PipeWaker for AndroidPipe,
    // we need to process all commands
    // in a context where we hold the VM lock.
    static void initThreading(VmLock* vmLock);

    // Goldfish sync virtual device will give out its own
    // callback for queueing commands to it.
    static void setQueueCommand(queue_device_command_t fx);

    // Main interface for all Goldfish sync device
    // communications.
    static void hostSignal(uint32_t cmd,
                           uint64_t handle,
                           uint32_t time_arg,
                           uint64_t hostcmd_handle);

    // Save/load pending operations.
    static void save(android::base::Stream* stream);
    static void load(android::base::Stream* stream);

private:

    virtual void performDeviceOperation(const GoldfishSyncWakeInfo& cmd) override;

    queue_device_command_t tellSyncDevice = nullptr;
};

} // namespace android
