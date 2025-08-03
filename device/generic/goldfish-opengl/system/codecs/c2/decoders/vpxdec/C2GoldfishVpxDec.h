/*
 * Copyright (C) 2018 The Android Open Source Project
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

#pragma once

#include "goldfish_media_utils.h"
#include "goldfish_vpx_defs.h"
#include <SimpleC2Component.h>

namespace android {

struct C2GoldfishVpxDec : public SimpleC2Component {
    class IntfImpl;

    C2GoldfishVpxDec(const char *name, c2_node_id_t id,
                     const std::shared_ptr<IntfImpl> &intfImpl);
    virtual ~C2GoldfishVpxDec();

    // From SimpleC2Component
    c2_status_t onInit() override;
    c2_status_t onStop() override;
    void onReset() override;
    void onRelease() override;
    c2_status_t onFlush_sm() override;
    void process(const std::unique_ptr<C2Work> &work,
                 const std::shared_ptr<C2BlockPool> &pool) override;
    c2_status_t drain(uint32_t drainMode,
                      const std::shared_ptr<C2BlockPool> &pool) override;

  private:
    enum {
        MODE_VP8,
        MODE_VP9,
    } mMode;

    struct ConversionQueue;

    class ConverterThread : public Thread {
      public:
        explicit ConverterThread(
            const std::shared_ptr<Mutexed<ConversionQueue>> &queue);
        ~ConverterThread() override = default;
        bool threadLoop() override;

      private:
        std::shared_ptr<Mutexed<ConversionQueue>> mQueue;
    };

    // create context that talks to host decoder: it needs to use
    // pool to decide whether decoding to host color buffer ot
    // decode to guest bytebuffer when pool cannot fetch valid host
    // color buffer id
    void checkContext(const std::shared_ptr<C2BlockPool> &pool);
    bool mEnableAndroidNativeBuffers{true};

    void setup_ctx_parameters(vpx_codec_ctx_t *ctx, int hostColorBufferId = -1);

    std::shared_ptr<IntfImpl> mIntf;
    vpx_codec_ctx_t *mCtx;
    bool mFrameParallelMode; // Frame parallel is only supported by VP9 decoder.
    vpx_image_t *mImg;

    uint32_t mWidth;
    uint32_t mHeight;
    bool mSignalledOutputEos;
    bool mSignalledError;

    struct ConversionQueue {
        std::list<std::function<void()>> entries;
        Condition cond;
        size_t numPending{0u};
    };
    std::shared_ptr<Mutexed<ConversionQueue>> mQueue;
    std::vector<sp<ConverterThread>> mConverterThreads;

    status_t initDecoder();
    status_t destroyDecoder();
    void finishWork(uint64_t index, const std::unique_ptr<C2Work> &work,
                    const std::shared_ptr<C2GraphicBlock> &block);
    status_t outputBuffer(const std::shared_ptr<C2BlockPool> &pool,
                          const std::unique_ptr<C2Work> &work);
    c2_status_t drainInternal(uint32_t drainMode,
                              const std::shared_ptr<C2BlockPool> &pool,
                              const std::unique_ptr<C2Work> &work);

    MetaDataColorAspects mSentMetadata = {1, 0, 0, 0};
    void sendMetadata();

    C2_DO_NOT_COPY(C2GoldfishVpxDec);
};

} // namespace android
