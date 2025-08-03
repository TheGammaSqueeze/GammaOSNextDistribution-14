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
#ifndef __COMMON_HOST_CONNECTION_H
#define __COMMON_HOST_CONNECTION_H

#if defined(ANDROID)
#include "gfxstream/guest/ANativeWindow.h"
#include "gfxstream/guest/Gralloc.h"
#endif

#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

#include "EmulatorFeatureInfo.h"
#include "Sync.h"
#include "VirtGpu.h"
#include "gfxstream/guest/ChecksumCalculator.h"
#include "gfxstream/guest/IOStream.h"
#include "renderControl_enc.h"

class GLEncoder;
struct gl_client_context_t;
class GL2Encoder;
struct gl2_client_context_t;

namespace gfxstream {
namespace vk {
class VkEncoder;
}  // namespace vk
}  // namespace gfxstream

// ExtendedRCEncoderContext is an extended version of renderControl_encoder_context_t
// that will be used to track available emulator features.
class ExtendedRCEncoderContext : public renderControl_encoder_context_t {
public:
    ExtendedRCEncoderContext(gfxstream::guest::IOStream *stream,
                             gfxstream::guest::ChecksumCalculator *checksumCalculator)
        : renderControl_encoder_context_t(stream, checksumCalculator) {}
    void setSyncImpl(SyncImpl syncImpl) { m_featureInfo.syncImpl = syncImpl; }
    void setDmaImpl(DmaImpl dmaImpl) { m_featureInfo.dmaImpl = dmaImpl; }
    void setHostComposition(HostComposition hostComposition) {
        m_featureInfo.hostComposition = hostComposition; }
    bool hasNativeSync() const { return m_featureInfo.syncImpl >= SYNC_IMPL_NATIVE_SYNC_V2; }
    bool hasNativeSyncV3() const { return m_featureInfo.syncImpl >= SYNC_IMPL_NATIVE_SYNC_V3; }
    bool hasNativeSyncV4() const { return m_featureInfo.syncImpl >= SYNC_IMPL_NATIVE_SYNC_V4; }
    bool hasVirtioGpuNativeSync() const { return m_featureInfo.hasVirtioGpuNativeSync; }
    bool hasHostCompositionV1() const {
        return m_featureInfo.hostComposition == HOST_COMPOSITION_V1; }
    bool hasHostCompositionV2() const {
        return m_featureInfo.hostComposition == HOST_COMPOSITION_V2; }
    bool hasYUVCache() const {
        return m_featureInfo.hasYUVCache; }
    bool hasAsyncUnmapBuffer() const {
        return m_featureInfo.hasAsyncUnmapBuffer; }
    bool hasHostSideTracing() const {
        return m_featureInfo.hasHostSideTracing;
    }
    bool hasAsyncFrameCommands() const {
        return m_featureInfo.hasAsyncFrameCommands;
    }
    bool hasSyncBufferData() const {
        return m_featureInfo.hasSyncBufferData; }
    bool hasHWCMultiConfigs() const {
        return m_featureInfo.hasHWCMultiConfigs;
    }
    void bindDmaDirectly(void* dmaPtr, uint64_t dmaPhysAddr) {
        m_dmaPtr = dmaPtr;
        m_dmaPhysAddr = dmaPhysAddr;
    }
    virtual uint64_t lockAndWriteDma(void* data, uint32_t size) {
        if (m_dmaPtr && m_dmaPhysAddr) {
            if (data != m_dmaPtr) {
                memcpy(m_dmaPtr, data, size);
            }
            return m_dmaPhysAddr;
        } else {
            ALOGE("%s: ERROR: No DMA context bound!", __func__);
            return 0;
        }
    }
    void setGLESMaxVersion(GLESMaxVersion ver) { m_featureInfo.glesMaxVersion = ver; }
    GLESMaxVersion getGLESMaxVersion() const { return m_featureInfo.glesMaxVersion; }
    bool hasDirectMem() const {
        return m_featureInfo.hasDirectMem;
    }

    const EmulatorFeatureInfo* featureInfo_const() const { return &m_featureInfo; }
    EmulatorFeatureInfo* featureInfo() { return &m_featureInfo; }

private:
    EmulatorFeatureInfo m_featureInfo;
    void* m_dmaPtr = nullptr;
    uint64_t m_dmaPhysAddr = 0;
};

struct EGLThreadInfo;

class HostConnection
{
public:
    static HostConnection *get();
    static HostConnection* getOrCreate(enum VirtGpuCapset capset = kCapsetNone);

    static HostConnection* getWithThreadInfo(EGLThreadInfo* tInfo,
                                             enum VirtGpuCapset capset = kCapsetNone);
    static void exit();
    static void exitUnclean(); // for testing purposes

    static std::unique_ptr<HostConnection> createUnique(enum VirtGpuCapset capset = kCapsetNone);
    HostConnection(const HostConnection&) = delete;

    ~HostConnection();

    GLEncoder *glEncoder();
    GL2Encoder *gl2Encoder();
    gfxstream::vk::VkEncoder *vkEncoder();
    ExtendedRCEncoderContext *rcEncoder();

    int getRendernodeFd() { return m_rendernodeFd; }

    gfxstream::guest::ChecksumCalculator *checksumHelper() { return &m_checksumHelper; }

#if defined(ANDROID)
    gfxstream::ANativeWindowHelper* anwHelper() { return m_anwHelper.get(); }
    gfxstream::Gralloc* grallocHelper() { return m_grallocHelper.get(); }
#endif
    gfxstream::SyncHelper* syncHelper() { return m_syncHelper.get(); }

    void flush() {
        if (m_stream) {
            m_stream->flush();
        }
    }

#ifdef __clang__
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wthread-safety-analysis"
#endif
    void lock() const { m_lock.lock(); }
    void unlock() const { m_lock.unlock(); }
#ifdef __clang__
#pragma clang diagnostic pop
#endif

    bool exitUncleanly; // for testing purposes

private:
    // If the connection failed, |conn| is deleted.
    // Returns NULL if connection failed.
 static std::unique_ptr<HostConnection> connect(enum VirtGpuCapset capset);

 HostConnection();
 static gl_client_context_t* s_getGLContext();
 static gl2_client_context_t* s_getGL2Context();

 const std::string& queryHostExtensions(ExtendedRCEncoderContext* rcEnc);
 // setProtocol initializes GL communication protocol for checksums
 // should be called when m_rcEnc is created
 void setChecksumHelper(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetSyncImpl(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetDmaImpl(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetGLESMaxVersion(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetNoErrorState(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetHostCompositionImpl(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetDirectMemSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetDeferredVulkanCommandsSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanNullOptionalStringsSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanCreateResourcesWithRequirementsSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanIgnoredHandles(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetYUVCache(ExtendedRCEncoderContext* mrcEnc);
 void queryAndSetAsyncUnmapBuffer(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVirtioGpuNext(ExtendedRCEncoderContext* rcEnc);
 void queryHasSharedSlotsHostMemoryAllocator(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanFreeMemorySync(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVirtioGpuNativeSync(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanShaderFloat16Int8Support(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanAsyncQueueSubmitSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetHostSideTracingSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetAsyncFrameCommands(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanQueueSubmitWithCommandsSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanBatchedDescriptorSetUpdateSupport(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetSyncBufferData(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanAsyncQsri(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetReadColorBufferDma(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetHWCMultiConfigs(ExtendedRCEncoderContext* rcEnc);
 void queryAndSetVulkanAuxCommandBufferMemory(ExtendedRCEncoderContext* rcEnc);
 GLint queryVersion(ExtendedRCEncoderContext* rcEnc);

private:
 HostConnectionType m_connectionType;

 // intrusively refcounted
 gfxstream::guest::IOStream* m_stream = nullptr;

 std::unique_ptr<GLEncoder> m_glEnc;
 std::unique_ptr<GL2Encoder> m_gl2Enc;

 // intrusively refcounted
 gfxstream::vk::VkEncoder* m_vkEnc = nullptr;
 std::unique_ptr<ExtendedRCEncoderContext> m_rcEnc;

 gfxstream::guest::ChecksumCalculator m_checksumHelper;
#if defined(ANDROID)
 std::unique_ptr<gfxstream::ANativeWindowHelper> m_anwHelper;
 std::unique_ptr<gfxstream::Gralloc> m_grallocHelper;
#endif
 std::unique_ptr<gfxstream::SyncHelper> m_syncHelper;
 std::string m_hostExtensions;
 bool m_noHostError;
 mutable std::mutex m_lock;
 int m_rendernodeFd;
};

#endif
