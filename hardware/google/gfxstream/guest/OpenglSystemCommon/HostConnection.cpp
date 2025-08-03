/*
* Copyright (C) 2011 The Android Open Source Project
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
#include "HostConnection.h"

#include "GoldfishAddressSpaceStream.h"
#include "VirtioGpuAddressSpaceStream.h"
#include "aemu/base/AndroidHealthMonitor.h"
#include "aemu/base/AndroidHealthMonitorConsumerBasic.h"
#include "aemu/base/threads/AndroidThread.h"
#if defined(__ANDROID__)
#include "android-base/properties.h"
#endif
#include "renderControl_types.h"

#include "aemu/base/Process.h"

#define DEBUG_HOSTCONNECTION 0

#if DEBUG_HOSTCONNECTION
#define DPRINT(fmt,...) ALOGD("%s: " fmt, __FUNCTION__, ##__VA_ARGS__);
#else
#define DPRINT(...)
#endif

using gfxstream::guest::ChecksumCalculator;
using gfxstream::guest::CreateHealthMonitor;
using gfxstream::guest::HealthMonitor;
using gfxstream::guest::HealthMonitorConsumerBasic;
using gfxstream::guest::IOStream;

#ifdef GOLDFISH_NO_GL
struct gl_client_context_t {
    int placeholder;
};
class GLEncoder : public gl_client_context_t {
public:
    GLEncoder(IOStream*, ChecksumCalculator*) { }
    void setContextAccessor(gl_client_context_t *()) { }
};
struct gl2_client_context_t {
    int placeholder;
};
class GL2Encoder : public gl2_client_context_t {
public:
    GL2Encoder(IOStream*, ChecksumCalculator*) { }
    void setContextAccessor(gl2_client_context_t *()) { }
    void setNoHostError(bool) { }
    void setDrawCallFlushInterval(uint32_t) { }
    void setHasAsyncUnmapBuffer(int) { }
    void setHasSyncBufferData(int) { }
};
#else
#include "GLEncoder.h"
#include "GL2Encoder.h"
#endif

#include "VkEncoder.h"
#include "AddressSpaceStream.h"

using gfxstream::vk::VkEncoder;

#include <unistd.h>

#include "ProcessPipe.h"
#include "QemuPipeStream.h"
#include "ThreadInfo.h"

using gfxstream::guest::getCurrentThreadId;

#include "VirtGpu.h"
#include "VirtioGpuPipeStream.h"

#if defined(__linux__) || defined(__ANDROID__)
#include "virtgpu_drm.h"
#include <fstream>
#include <string>
#include <unistd.h>

static const size_t kPageSize = getpagesize();
#else
constexpr size_t kPageSize = PAGE_SIZE;
#endif

#undef LOG_TAG
#define LOG_TAG "HostConnection"
#include <cutils/log.h>

#define STREAM_BUFFER_SIZE  (4*1024*1024)
#define STREAM_PORT_NUM     22468

constexpr const auto kEglProp = "ro.hardware.egl";

HealthMonitor<>* getGlobalHealthMonitor() {
    // Initialize HealthMonitor
    // Rather than inject as a construct arg, we keep it as a static variable in the .cpp
    // to avoid setting up dependencies in other repos (external/qemu)
    static HealthMonitorConsumerBasic sHealthMonitorConsumerBasic;
    static std::unique_ptr<HealthMonitor<>> sHealthMonitor = CreateHealthMonitor(sHealthMonitorConsumerBasic);
    return sHealthMonitor.get();
}

static HostConnectionType getConnectionTypeFromProperty(enum VirtGpuCapset capset) {
#if defined(__Fuchsia__) || defined(LINUX_GUEST_BUILD)
    return HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE;
#else
    std::string transport;

#if defined(__ANDROID__)
    transport = android::base::GetProperty("ro.boot.hardware.gltransport", "");
#else
    const char* transport_envvar = getenv("GFXSTREAM_TRANSPORT");
    if (transport_envvar != nullptr) {
        transport = std::string(transport_envvar);
    }
#endif

    if (transport.empty()) {
#if defined(__ANDROID__)
        return HOST_CONNECTION_QEMU_PIPE;
#else
        return HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE;
#endif
    }

    if (transport == "asg") {
        return HOST_CONNECTION_ADDRESS_SPACE;
    }
    if (transport == "pipe") {
        return HOST_CONNECTION_QEMU_PIPE;
    }

    if (transport == "virtio-gpu-asg" || transport == "virtio-gpu-pipe") {
        std::string egl;
#if defined(__ANDROID__)
        egl = android::base::GetProperty(kEglProp, "");
#endif
        // ANGLE doesn't work well without ASG, particularly if HostComposer uses a pipe
        // transport and VK uses ASG.
        if (capset == kCapsetGfxStreamVulkan || egl == "angle") {
            return HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE;
        } else {
            return HOST_CONNECTION_VIRTIO_GPU_PIPE;
        }
    }

    return HOST_CONNECTION_QEMU_PIPE;
#endif
}

static uint32_t getDrawCallFlushIntervalFromProperty() {
    constexpr uint32_t kDefaultValue = 800;
    uint32_t value = kDefaultValue;

#if defined(__ANDROID__)
    value = android::base::GetUintProperty("ro.boot.qemu.gltransport.drawFlushInterval",
                                           kDefaultValue);
#endif
    return value;
}

HostConnection::HostConnection()
    : exitUncleanly(false),
      m_checksumHelper(),
      m_hostExtensions(),
      m_noHostError(true),
      m_rendernodeFd(-1) { }

HostConnection::~HostConnection()
{
    // round-trip to ensure that queued commands have been processed
    // before process pipe closure is detected.
    if (m_rcEnc && !exitUncleanly) {
        (void)m_rcEnc->rcGetRendererVersion(m_rcEnc.get());
    }

    if (m_vkEnc) {
        m_vkEnc->decRef();
    }

    if (m_stream) {
        m_stream->decRef();
    }
}


// static
std::unique_ptr<HostConnection> HostConnection::connect(enum VirtGpuCapset capset) {
    const enum HostConnectionType connType = getConnectionTypeFromProperty(capset);
    uint32_t noRenderControlEnc = 0;

    // Use "new" to access a non-public constructor.
    auto con = std::unique_ptr<HostConnection>(new HostConnection);
    con->m_connectionType = connType;

    switch (connType) {
        case HOST_CONNECTION_ADDRESS_SPACE: {
#if defined(__ANDROID__)
            auto stream = createGoldfishAddressSpaceStream(STREAM_BUFFER_SIZE, getGlobalHealthMonitor());
            if (!stream) {
                ALOGE("Failed to create AddressSpaceStream for host connection\n");
                return nullptr;
            }
            con->m_stream = stream;
#else
            ALOGE("Fatal: HOST_CONNECTION_ADDRESS_SPACE not supported on this host.");
            abort();
#endif

            break;
        }
#if !defined(__Fuchsia__)
        case HOST_CONNECTION_QEMU_PIPE: {
            auto stream = new QemuPipeStream(STREAM_BUFFER_SIZE);
            if (!stream) {
                ALOGE("Failed to create QemuPipeStream for host connection\n");
                return nullptr;
            }
            if (stream->connect() < 0) {
                ALOGE("Failed to connect to host (QemuPipeStream)\n");
                return nullptr;
            }
            con->m_stream = stream;
            break;
        }
#endif
        case HOST_CONNECTION_VIRTIO_GPU_PIPE: {
            auto stream = new VirtioGpuPipeStream(STREAM_BUFFER_SIZE);
            if (!stream) {
                ALOGE("Failed to create VirtioGpu for host connection\n");
                return nullptr;
            }
            if (stream->connect() < 0) {
                ALOGE("Failed to connect to host (VirtioGpu)\n");
                return nullptr;
            }
            auto rendernodeFd = stream->getRendernodeFd();
            con->m_stream = stream;
            con->m_rendernodeFd = rendernodeFd;
            break;
        }
        case HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE: {
            // Use kCapsetGfxStreamVulkan for now, Ranchu HWC needs to be modified to pass in
            // right capset.
            auto device = VirtGpuDevice::getInstance(kCapsetGfxStreamVulkan);
            auto deviceHandle = device->getDeviceHandle();
            auto stream =
                createVirtioGpuAddressSpaceStream(kCapsetGfxStreamVulkan, getGlobalHealthMonitor());
            if (!stream) {
                ALOGE("Failed to create virtgpu AddressSpaceStream\n");
                return nullptr;
            }
            con->m_stream = stream;
            con->m_rendernodeFd = deviceHandle;
            break;
        }
        default:
            break;
    }

#if defined(ANDROID)
    con->m_grallocHelper.reset(gfxstream::createPlatformGralloc(con->m_rendernodeFd));
    if (!con->m_grallocHelper) {
        ALOGE("Failed to create platform Gralloc!");
        abort();
    }

    con->m_anwHelper.reset(gfxstream::createPlatformANativeWindowHelper());
    if (!con->m_anwHelper) {
        ALOGE("Failed to create platform ANativeWindowHelper!");
        abort();
    }
#endif

    con->m_syncHelper.reset(gfxstream::createPlatformSyncHelper());

    // send zero 'clientFlags' to the host.
    unsigned int *pClientFlags =
            (unsigned int *)con->m_stream->allocBuffer(sizeof(unsigned int));
    *pClientFlags = 0;
    con->m_stream->commitBuffer(sizeof(unsigned int));

    if (capset == kCapsetGfxStreamMagma) {
        noRenderControlEnc = 1;
    } else if (capset == kCapsetGfxStreamVulkan) {
        VirtGpuDevice* instance = VirtGpuDevice::getInstance(kCapsetGfxStreamVulkan);
        auto caps = instance->getCaps();
        noRenderControlEnc = caps.vulkanCapset.noRenderControlEnc;
    }

    auto fd = (connType == HOST_CONNECTION_VIRTIO_GPU_ADDRESS_SPACE) ? con->m_rendernodeFd : -1;
    processPipeInit(fd, connType, noRenderControlEnc);
    if (!noRenderControlEnc && capset == kCapsetGfxStreamVulkan) {
        con->rcEncoder();
    }

    return con;
}

HostConnection* HostConnection::get() { return getWithThreadInfo(getEGLThreadInfo(), kCapsetNone); }

HostConnection* HostConnection::getOrCreate(enum VirtGpuCapset capset) {
    return getWithThreadInfo(getEGLThreadInfo(), capset);
}

HostConnection* HostConnection::getWithThreadInfo(EGLThreadInfo* tinfo, enum VirtGpuCapset capset) {
    // Get thread info
    if (!tinfo) {
        return NULL;
    }

    if (tinfo->hostConn == NULL) {
        tinfo->hostConn = HostConnection::createUnique(capset);
    }

    return tinfo->hostConn.get();
}

void HostConnection::exit() {
    EGLThreadInfo *tinfo = getEGLThreadInfo();
    if (!tinfo) {
        return;
    }

    tinfo->hostConn.reset();
}

void HostConnection::exitUnclean() {
    EGLThreadInfo *tinfo = getEGLThreadInfo();
    if (!tinfo) {
        return;
    }

    tinfo->hostConn->exitUncleanly = true;
    tinfo->hostConn.reset();
}

// static
std::unique_ptr<HostConnection> HostConnection::createUnique(enum VirtGpuCapset capset) {
    return connect(capset);
}

GLEncoder *HostConnection::glEncoder()
{
    if (!m_glEnc) {
        m_glEnc = std::make_unique<GLEncoder>(m_stream, checksumHelper());
        DBG("HostConnection::glEncoder new encoder %p, tid %lu", m_glEnc, getCurrentThreadId());
        m_glEnc->setContextAccessor(s_getGLContext);
    }
    return m_glEnc.get();
}

GL2Encoder *HostConnection::gl2Encoder()
{
    if (!m_gl2Enc) {
        m_gl2Enc =
            std::make_unique<GL2Encoder>(m_stream, checksumHelper());
        DBG("HostConnection::gl2Encoder new encoder %p, tid %lu", m_gl2Enc, getCurrentThreadId());
        m_gl2Enc->setContextAccessor(s_getGL2Context);
        m_gl2Enc->setNoHostError(m_noHostError);
        m_gl2Enc->setDrawCallFlushInterval(
            getDrawCallFlushIntervalFromProperty());
        m_gl2Enc->setHasAsyncUnmapBuffer(m_rcEnc->hasAsyncUnmapBuffer());
        m_gl2Enc->setHasSyncBufferData(m_rcEnc->hasSyncBufferData());
    }
    return m_gl2Enc.get();
}

VkEncoder* HostConnection::vkEncoder() {
    if (!m_vkEnc) {
        m_vkEnc = new VkEncoder(m_stream, getGlobalHealthMonitor());
    }
    return m_vkEnc;
}

ExtendedRCEncoderContext *HostConnection::rcEncoder()
{
    if (!m_rcEnc) {
        m_rcEnc = std::make_unique<ExtendedRCEncoderContext>(m_stream,
                                                             checksumHelper());

        ExtendedRCEncoderContext* rcEnc = m_rcEnc.get();
        setChecksumHelper(rcEnc);
        queryAndSetSyncImpl(rcEnc);
        queryAndSetDmaImpl(rcEnc);
        queryAndSetGLESMaxVersion(rcEnc);
        queryAndSetNoErrorState(rcEnc);
        queryAndSetHostCompositionImpl(rcEnc);
        queryAndSetDirectMemSupport(rcEnc);
        queryAndSetVulkanSupport(rcEnc);
        queryAndSetDeferredVulkanCommandsSupport(rcEnc);
        queryAndSetVulkanNullOptionalStringsSupport(rcEnc);
        queryAndSetVulkanCreateResourcesWithRequirementsSupport(rcEnc);
        queryAndSetVulkanIgnoredHandles(rcEnc);
        queryAndSetYUVCache(rcEnc);
        queryAndSetAsyncUnmapBuffer(rcEnc);
        queryAndSetVirtioGpuNext(rcEnc);
        queryHasSharedSlotsHostMemoryAllocator(rcEnc);
        queryAndSetVulkanFreeMemorySync(rcEnc);
        queryAndSetVirtioGpuNativeSync(rcEnc);
        queryAndSetVulkanShaderFloat16Int8Support(rcEnc);
        queryAndSetVulkanAsyncQueueSubmitSupport(rcEnc);
        queryAndSetHostSideTracingSupport(rcEnc);
        queryAndSetAsyncFrameCommands(rcEnc);
        queryAndSetVulkanQueueSubmitWithCommandsSupport(rcEnc);
        queryAndSetVulkanBatchedDescriptorSetUpdateSupport(rcEnc);
        queryAndSetSyncBufferData(rcEnc);
        queryAndSetVulkanAsyncQsri(rcEnc);
        queryAndSetReadColorBufferDma(rcEnc);
        queryAndSetHWCMultiConfigs(rcEnc);
        queryAndSetVulkanAuxCommandBufferMemory(rcEnc);
        queryVersion(rcEnc);

        rcEnc->rcSetPuid(rcEnc, getPuid());
    }
    return m_rcEnc.get();
}

gl_client_context_t *HostConnection::s_getGLContext()
{
    EGLThreadInfo *ti = getEGLThreadInfo();
    if (ti->hostConn) {
        return ti->hostConn->m_glEnc.get();
    }
    return NULL;
}

gl2_client_context_t *HostConnection::s_getGL2Context()
{
    EGLThreadInfo *ti = getEGLThreadInfo();
    if (ti->hostConn) {
        return ti->hostConn->m_gl2Enc.get();
    }
    return NULL;
}

const std::string& HostConnection::queryHostExtensions(ExtendedRCEncoderContext *rcEnc) {
    if (!m_hostExtensions.empty()) {
        return m_hostExtensions;
    }

    // Extensions strings are usually quite long, preallocate enough here.
    std::string extensionsBuffer(1023, '\0');

    // Returns the required size including the 0-terminator, so
    // account it when passing/using the sizes.
    int extensionSize = rcEnc->rcGetHostExtensionsString(rcEnc,
                                                         extensionsBuffer.size() + 1,
                                                         &extensionsBuffer[0]);
    if (extensionSize < 0) {
        extensionsBuffer.resize(-extensionSize);
        extensionSize = rcEnc->rcGetHostExtensionsString(rcEnc,
                                                         -extensionSize + 1,
                                                         &extensionsBuffer[0]);
    }

    if (extensionSize > 0) {
        extensionsBuffer.resize(extensionSize - 1);
        m_hostExtensions.swap(extensionsBuffer);
    }

    return m_hostExtensions;
}

void HostConnection::queryAndSetHostCompositionImpl(ExtendedRCEncoderContext *rcEnc) {
    const std::string& hostExtensions = queryHostExtensions(rcEnc);
    DPRINT("HostComposition ext %s", hostExtensions.c_str());
    // make sure V2 is checked first before V1, as host may declare supporting both
    if (hostExtensions.find(kHostCompositionV2) != std::string::npos) {
        rcEnc->setHostComposition(HOST_COMPOSITION_V2);
    }
    else if (hostExtensions.find(kHostCompositionV1) != std::string::npos) {
        rcEnc->setHostComposition(HOST_COMPOSITION_V1);
    }
    else {
        rcEnc->setHostComposition(HOST_COMPOSITION_NONE);
    }
}

void HostConnection::setChecksumHelper(ExtendedRCEncoderContext *rcEnc) {
    const std::string& hostExtensions = queryHostExtensions(rcEnc);
    // check the host supported version
    uint32_t checksumVersion = 0;
    const char* checksumPrefix = ChecksumCalculator::getMaxVersionStrPrefix();
    const char* glProtocolStr = strstr(hostExtensions.c_str(), checksumPrefix);
    if (glProtocolStr) {
        uint32_t maxVersion = ChecksumCalculator::getMaxVersion();
        sscanf(glProtocolStr+strlen(checksumPrefix), "%d", &checksumVersion);
        if (maxVersion < checksumVersion) {
            checksumVersion = maxVersion;
        }
        // The ordering of the following two commands matters!
        // Must tell the host first before setting it in the guest
        rcEnc->rcSelectChecksumHelper(rcEnc, checksumVersion, 0);
        m_checksumHelper.setVersion(checksumVersion);
    }
}

void HostConnection::queryAndSetSyncImpl(ExtendedRCEncoderContext *rcEnc) {
    const std::string& hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kRCNativeSyncV4) != std::string::npos) {
        rcEnc->setSyncImpl(SYNC_IMPL_NATIVE_SYNC_V4);
    } else if (hostExtensions.find(kRCNativeSyncV3) != std::string::npos) {
        rcEnc->setSyncImpl(SYNC_IMPL_NATIVE_SYNC_V3);
    } else if (hostExtensions.find(kRCNativeSyncV2) != std::string::npos) {
        rcEnc->setSyncImpl(SYNC_IMPL_NATIVE_SYNC_V2);
    } else {
        rcEnc->setSyncImpl(SYNC_IMPL_NONE);
    }
}

void HostConnection::queryAndSetDmaImpl(ExtendedRCEncoderContext *rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kDmaExtStr_v1) != std::string::npos) {
        rcEnc->setDmaImpl(DMA_IMPL_v1);
    } else {
        rcEnc->setDmaImpl(DMA_IMPL_NONE);
    }
}

void HostConnection::queryAndSetGLESMaxVersion(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kGLESMaxVersion_2) != std::string::npos) {
        rcEnc->setGLESMaxVersion(GLES_MAX_VERSION_2);
    } else if (hostExtensions.find(kGLESMaxVersion_3_0) != std::string::npos) {
        rcEnc->setGLESMaxVersion(GLES_MAX_VERSION_3_0);
    } else if (hostExtensions.find(kGLESMaxVersion_3_1) != std::string::npos) {
        rcEnc->setGLESMaxVersion(GLES_MAX_VERSION_3_1);
    } else if (hostExtensions.find(kGLESMaxVersion_3_2) != std::string::npos) {
        rcEnc->setGLESMaxVersion(GLES_MAX_VERSION_3_2);
    } else {
        ALOGW("Unrecognized GLES max version string in extensions: %s",
              hostExtensions.c_str());
        rcEnc->setGLESMaxVersion(GLES_MAX_VERSION_2);
    }
}

void HostConnection::queryAndSetNoErrorState(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kGLESUseHostError) != std::string::npos) {
        m_noHostError = false;
    }
}

void HostConnection::queryAndSetDirectMemSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kGLDirectMem) != std::string::npos) {
        rcEnc->featureInfo()->hasDirectMem = true;
    }
}

void HostConnection::queryAndSetVulkanSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkan) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkan = true;
    }
}

void HostConnection::queryAndSetDeferredVulkanCommandsSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kDeferredVulkanCommands) != std::string::npos) {
        rcEnc->featureInfo()->hasDeferredVulkanCommands = true;
    }
}

void HostConnection::queryAndSetVulkanNullOptionalStringsSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanNullOptionalStrings) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanNullOptionalStrings = true;
    }
}

void HostConnection::queryAndSetVulkanCreateResourcesWithRequirementsSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanCreateResourcesWithRequirements) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanCreateResourcesWithRequirements = true;
    }
}

void HostConnection::queryAndSetVulkanIgnoredHandles(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanIgnoredHandles) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanIgnoredHandles = true;
    }
}

void HostConnection::queryAndSetYUVCache(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kYUVCache) != std::string::npos) {
        rcEnc->featureInfo()->hasYUVCache = true;
    }
}

void HostConnection::queryAndSetAsyncUnmapBuffer(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kAsyncUnmapBuffer) != std::string::npos) {
        rcEnc->featureInfo()->hasAsyncUnmapBuffer = true;
    }
}

void HostConnection::queryAndSetVirtioGpuNext(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVirtioGpuNext) != std::string::npos) {
        rcEnc->featureInfo()->hasVirtioGpuNext = true;
    }
}

void HostConnection::queryHasSharedSlotsHostMemoryAllocator(ExtendedRCEncoderContext *rcEnc) {
    const std::string& hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kHasSharedSlotsHostMemoryAllocator) != std::string::npos) {
        rcEnc->featureInfo()->hasSharedSlotsHostMemoryAllocator = true;
    }
}

void HostConnection::queryAndSetVulkanFreeMemorySync(ExtendedRCEncoderContext *rcEnc) {
    const std::string& hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanFreeMemorySync) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanFreeMemorySync = true;
    }
}

void HostConnection::queryAndSetVirtioGpuNativeSync(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVirtioGpuNativeSync) != std::string::npos) {
        rcEnc->featureInfo()->hasVirtioGpuNativeSync = true;
    }
}

void HostConnection::queryAndSetVulkanShaderFloat16Int8Support(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanShaderFloat16Int8) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanShaderFloat16Int8 = true;
    }
}

void HostConnection::queryAndSetVulkanAsyncQueueSubmitSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanAsyncQueueSubmit) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanAsyncQueueSubmit = true;
    }
}

void HostConnection::queryAndSetHostSideTracingSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kHostSideTracing) != std::string::npos) {
        rcEnc->featureInfo()->hasHostSideTracing = true;
    }
}

void HostConnection::queryAndSetAsyncFrameCommands(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kAsyncFrameCommands) != std::string::npos) {
        rcEnc->featureInfo()->hasAsyncFrameCommands = true;
    }
}

void HostConnection::queryAndSetVulkanQueueSubmitWithCommandsSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanQueueSubmitWithCommands) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanQueueSubmitWithCommands = true;
    }
}

void HostConnection::queryAndSetVulkanBatchedDescriptorSetUpdateSupport(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanBatchedDescriptorSetUpdate) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanBatchedDescriptorSetUpdate = true;
    }
}

void HostConnection::queryAndSetSyncBufferData(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kSyncBufferData) != std::string::npos) {
        rcEnc->featureInfo()->hasSyncBufferData = true;
    }
}

void HostConnection::queryAndSetVulkanAsyncQsri(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kVulkanAsyncQsri) != std::string::npos) {
        rcEnc->featureInfo()->hasVulkanAsyncQsri = true;
    }
}

void HostConnection::queryAndSetReadColorBufferDma(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kReadColorBufferDma) != std::string::npos) {
        rcEnc->featureInfo()->hasReadColorBufferDma = true;
    }
}

void HostConnection::queryAndSetHWCMultiConfigs(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    if (hostExtensions.find(kHWCMultiConfigs) != std::string::npos) {
        rcEnc->featureInfo()->hasHWCMultiConfigs = true;
    }
}

void HostConnection::queryAndSetVulkanAuxCommandBufferMemory(ExtendedRCEncoderContext* rcEnc) {
    std::string hostExtensions = queryHostExtensions(rcEnc);
    rcEnc->featureInfo()->hasVulkanAuxCommandMemory = hostExtensions.find(kVulkanAuxCommandMemory) != std::string::npos;
}


GLint HostConnection::queryVersion(ExtendedRCEncoderContext* rcEnc) {
    GLint version = m_rcEnc->rcGetRendererVersion(m_rcEnc.get());
    return version;
}
