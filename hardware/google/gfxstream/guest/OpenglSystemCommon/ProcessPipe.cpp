/*
* Copyright (C) 2016 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#include "ProcessPipe.h"

#include <errno.h>
#include <log/log.h>
#include <pthread.h>
#include <qemu_pipe_bp.h>

#include "HostConnection.h"
#include "renderControl_enc.h"

#ifndef __Fuchsia__

#include "VirtioGpuPipeStream.h"
static VirtioGpuPipeStream* sVirtioGpuPipeStream = 0;
static int sStreamHandle = -1;

#endif // !__Fuchsia__

static QEMU_PIPE_HANDLE sProcPipe = 0;
// sProcUID is a unique ID per process assigned by the host.
// It is different from getpid().
static uint64_t           sProcUID = 0;
static HostConnectionType sConnType = HOST_CONNECTION_VIRTIO_GPU_PIPE;

static uint32_t* sSeqnoPtr = 0;

// Meant to be called only once per process.
static void initSeqno(void) {
    // So why do we reinitialize here? It's for testing purposes only;
    // we have a unit test that exercise the case where this sequence
    // number is reset as a result of guest process kill.
    if (sSeqnoPtr) delete sSeqnoPtr;
    sSeqnoPtr = new uint32_t;
    *sSeqnoPtr = 0;
}

namespace {

static std::mutex sNeedInitMutex;
static bool sNeedInit = true;

}  // namespace

#ifndef __Fuchsia__

static void sQemuPipeInit() {
    sProcPipe = qemu_pipe_open("GLProcessPipe");
    if (!qemu_pipe_valid(sProcPipe)) {
        sProcPipe = 0;
        ALOGW("Process pipe failed");
        return;
    }
    // Send a confirmation int to the host
    int32_t confirmInt = 100;
    if (qemu_pipe_write_fully(sProcPipe, &confirmInt, sizeof(confirmInt))) { // failed
        qemu_pipe_close(sProcPipe);
        sProcPipe = 0;
        ALOGW("Process pipe failed");
        return;
    }

    // Ask the host for per-process unique ID
    if (qemu_pipe_read_fully(sProcPipe, &sProcUID, sizeof(sProcUID))) {
        qemu_pipe_close(sProcPipe);
        sProcPipe = 0;
        sProcUID = 0;
        ALOGW("Process pipe failed");
        return;
    }
}

#endif // !__Fuchsia__

static void processPipeDoInit(uint32_t noRenderControlEnc) {
    initSeqno();

    // No need to setup auxiliary pipe stream in this case
    if (noRenderControlEnc) return;

#if defined(__Fuchsia__)
    // Note: sProcUID is not initialized.
    ALOGE("Fuchsia: requires noRenderControlEnc");
    abort();
#else
    switch (sConnType) {
        // TODO: Move those over too
        case HOST_CONNECTION_QEMU_PIPE:
        case HOST_CONNECTION_ADDRESS_SPACE:
            sQemuPipeInit();
            break;
        case HOST_CONNECTION_VIRTIO_GPU_PIPE:
        case HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE: {
            sVirtioGpuPipeStream = new VirtioGpuPipeStream(4096, sStreamHandle);
            sProcUID = sVirtioGpuPipeStream->initProcessPipe();
            break;
        }
    }
#endif
}

bool processPipeInit(int streamHandle, HostConnectionType connType, uint32_t noRenderControlEnc) {
    sConnType = connType;
#ifndef __Fuchsia__
    sStreamHandle = streamHandle;
#endif // !__Fuchsia

    {
        std::lock_guard<std::mutex> lock(sNeedInitMutex);

        if (sNeedInit) {
            sNeedInit = false;
            processPipeDoInit(noRenderControlEnc);

            if (noRenderControlEnc) {
                return true;
            }

#ifndef __Fuchsia__
            if (!sProcPipe && !sVirtioGpuPipeStream) {
                return false;
            }
#endif
        }
    }

    return true;
}

uint64_t getPuid() {
    return sProcUID;
}

void processPipeRestart() {
    std::lock_guard<std::mutex> lock(sNeedInitMutex);

    ALOGW("%s: restarting process pipe\n", __func__);
    bool isPipe = false;

    switch (sConnType) {
        // TODO: Move those over too
        case HOST_CONNECTION_QEMU_PIPE:
        case HOST_CONNECTION_ADDRESS_SPACE:
            isPipe = true;
            break;
        case HOST_CONNECTION_VIRTIO_GPU_PIPE:
        case HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE: {
            isPipe = false;
            break;
        }
    }

    sProcUID = 0;

    if (isPipe) {
        if (qemu_pipe_valid(sProcPipe)) {
            qemu_pipe_close(sProcPipe);
            sProcPipe = 0;
        }
    } else {
#ifndef __Fuchsia__
        if (sVirtioGpuPipeStream) {
            delete sVirtioGpuPipeStream;
            sVirtioGpuPipeStream = nullptr;
        }
#endif
    }

    if (sConnType == HOST_CONNECTION_VIRTIO_GPU_PIPE ||
        sConnType == HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE) {
        VirtGpuDevice::resetInstance();
    }

    sNeedInit = true;
}

void refreshHostConnection() {
    HostConnection* hostConn = HostConnection::get();
    ExtendedRCEncoderContext* rcEnc = hostConn->rcEncoder();
    rcEnc->rcSetPuid(rcEnc, sProcUID);
}

uint32_t* getSeqnoPtrForProcess() {
    // It's assumed process pipe state has already been initialized.
    return sSeqnoPtr;
}
