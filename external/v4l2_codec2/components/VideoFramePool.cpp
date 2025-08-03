// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "VideoFramePool"

#include <v4l2_codec2/components/VideoFramePool.h>

#include <stdint.h>
#include <memory>

#include <C2BlockInternal.h>
#include <bufferpool/BufferPoolTypes.h>

#include <android/hardware/graphics/common/1.0/types.h>
#include <base/bind.h>
#include <base/memory/ptr_util.h>
#include <base/time/time.h>
#include <log/log.h>

#include <v4l2_codec2/common/VideoTypes.h>
#include <v4l2_codec2/plugin_store/DmabufHelpers.h>
#include <v4l2_codec2/plugin_store/V4L2AllocatorId.h>

using android::hardware::graphics::common::V1_0::BufferUsage;
using android::hardware::media::bufferpool::BufferPoolData;

namespace android {

// static
std::optional<uint32_t> VideoFramePool::getBufferIdFromGraphicBlock(C2BlockPool& blockPool,
                                                                    const C2Block2D& block) {
    ALOGV("%s() blockPool.getAllocatorId() = %u", __func__, blockPool.getAllocatorId());

    switch (blockPool.getAllocatorId()) {
    case V4L2AllocatorId::SECURE_GRAPHIC:
        FALLTHROUGH;
    case C2PlatformAllocatorStore::BUFFERQUEUE: {
        auto dmabufId = android::getDmabufId(block.handle()->data[0]);
        if (!dmabufId) {
            return std::nullopt;
        }
        return dmabufId.value();
    }
    case C2PlatformAllocatorStore::GRALLOC:
        FALLTHROUGH;
    case V4L2AllocatorId::SECURE_LINEAR: {
        std::shared_ptr<_C2BlockPoolData> blockPoolData =
                _C2BlockFactory::GetGraphicBlockPoolData(block);
        if (blockPoolData->getType() != _C2BlockPoolData::TYPE_BUFFERPOOL) {
            ALOGE("Obtained C2GraphicBlock is not bufferpool-backed.");
            return std::nullopt;
        }
        std::shared_ptr<BufferPoolData> bpData;
        if (!_C2BlockFactory::GetBufferPoolData(blockPoolData, &bpData) || !bpData) {
            ALOGE("BufferPoolData unavailable in block.");
            return std::nullopt;
        }
        return bpData->mId;
    }
    }

    ALOGE("%s(): unknown allocator ID: %u", __func__, blockPool.getAllocatorId());
    return std::nullopt;
}

// static
std::unique_ptr<VideoFramePool> VideoFramePool::Create(
        std::shared_ptr<C2BlockPool> blockPool, const size_t numBuffers, const ui::Size& size,
        HalPixelFormat pixelFormat, bool isSecure,
        scoped_refptr<::base::SequencedTaskRunner> taskRunner) {
    ALOG_ASSERT(blockPool != nullptr);

    uint64_t usage = static_cast<uint64_t>(BufferUsage::VIDEO_DECODER);
    if (isSecure) {
        usage |= C2MemoryUsage::READ_PROTECTED;
    } else if (blockPool->getAllocatorId() == C2PlatformAllocatorStore::GRALLOC) {
        // CPU access to buffers is only required in byte buffer mode.
        usage |= C2MemoryUsage::CPU_READ;
    }
    const C2MemoryUsage memoryUsage(usage);

    std::unique_ptr<VideoFramePool> pool =
            ::base::WrapUnique(new VideoFramePool(std::move(blockPool), numBuffers, size,
                                                  pixelFormat, memoryUsage, std::move(taskRunner)));
    if (!pool->initialize()) return nullptr;
    return pool;
}

VideoFramePool::VideoFramePool(std::shared_ptr<C2BlockPool> blockPool, const size_t maxBufferCount,
                               const ui::Size& size, HalPixelFormat pixelFormat,
                               C2MemoryUsage memoryUsage,
                               scoped_refptr<::base::SequencedTaskRunner> taskRunner)
      : mBlockPool(std::move(blockPool)),
        mMaxBufferCount(maxBufferCount),
        mSize(size),
        mPixelFormat(pixelFormat),
        mMemoryUsage(memoryUsage),
        mClientTaskRunner(std::move(taskRunner)) {
    ALOGV("%s(size=%dx%d)", __func__, size.width, size.height);
    ALOG_ASSERT(mClientTaskRunner->RunsTasksInCurrentSequence());
    DCHECK(mBlockPool);
    DCHECK(mClientTaskRunner);
}

bool VideoFramePool::initialize() {
    if (!mFetchThread.Start()) {
        ALOGE("Fetch thread failed to start.");
        return false;
    }
    mFetchTaskRunner = mFetchThread.task_runner();

    mClientWeakThis = mClientWeakThisFactory.GetWeakPtr();
    mFetchWeakThis = mFetchWeakThisFactory.GetWeakPtr();

    return true;
}

VideoFramePool::~VideoFramePool() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mClientTaskRunner->RunsTasksInCurrentSequence());

    mClientWeakThisFactory.InvalidateWeakPtrs();

    if (mFetchThread.IsRunning()) {
        mFetchTaskRunner->PostTask(FROM_HERE,
                                   ::base::BindOnce(&VideoFramePool::destroyTask, mFetchWeakThis));
        mFetchThread.Stop();
    }
}

void VideoFramePool::destroyTask() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mFetchTaskRunner->RunsTasksInCurrentSequence());

    mFetchWeakThisFactory.InvalidateWeakPtrs();
}

bool VideoFramePool::shouldDropBuffer(uint32_t bufferId) {
    if (mBuffers.size() < mMaxBufferCount) {
        return false;
    }

    if (mBuffers.find(bufferId) != mBuffers.end()) {
        return false;
    }

    return true;
}

bool VideoFramePool::getVideoFrame(GetVideoFrameCB cb) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mClientTaskRunner->RunsTasksInCurrentSequence());

    if (mOutputCb) {
        return false;
    }

    mOutputCb = std::move(cb);
    mFetchTaskRunner->PostTask(
            FROM_HERE, ::base::BindOnce(&VideoFramePool::getVideoFrameTask, mFetchWeakThis));
    return true;
}

// static
void VideoFramePool::getVideoFrameTaskThunk(
        scoped_refptr<::base::SequencedTaskRunner> taskRunner,
        std::optional<::base::WeakPtr<VideoFramePool>> weakPool) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(weakPool);

    taskRunner->PostTask(FROM_HERE,
                         ::base::BindOnce(&VideoFramePool::getVideoFrameTask, *weakPool));
}

void VideoFramePool::getVideoFrameTask() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mFetchTaskRunner->RunsTasksInCurrentSequence());

    // Variables used to exponential backoff retry when buffer fetching times out.
    constexpr size_t kFetchRetryDelayInit = 256;   // Initial delay: 256us
    constexpr size_t kFetchRetryDelayMax = 16384;  // Max delay: 16ms (1 frame at 60fps)
    constexpr size_t kFenceWaitTimeoutNs = 16000000;  // 16ms (1 frame at 60fps)
    static size_t sNumRetries = 0;
    static size_t sDelay = kFetchRetryDelayInit;

    C2Fence fence;
    std::shared_ptr<C2GraphicBlock> block;
    c2_status_t err = mBlockPool->fetchGraphicBlock(mSize.width, mSize.height,
                                                    static_cast<uint32_t>(mPixelFormat),
                                                    mMemoryUsage, &block, &fence);
    // C2_BLOCKING can be returned either based on the state of the block pool itself
    // or the state of the underlying buffer queue. If the cause is the underlying
    // buffer queue, then the block pool returns a null fence. Since a null fence is
    // immediately ready, we need to delay instead of trying to wait on the fence, to
    // avoid spinning.
    //
    // Unfortunately, a null fence is considered a valid fence, so the best we can do
    // to detect a null fence is to assume that any fence that is immediately ready
    // is the null fence. A false positive by racing with a real fence can result in
    // an unnecessary delay, but the only alternative is to ignore fences altogether
    // and always delay.
    if (err == C2_BLOCKING && !fence.ready()) {
        err = fence.wait(kFenceWaitTimeoutNs);
        if (err == C2_OK) {
            ALOGV("%s(): fence wait succeded, retrying now", __func__);
            mFetchTaskRunner->PostTask(
                    FROM_HERE,
                    ::base::BindOnce(&VideoFramePool::getVideoFrameTask, mFetchWeakThis));
            return;
        }
        ALOGV("%s(): fence wait unsucessful err=%d", __func__, err);
    } else if (err == C2_OMITTED) {
        // Fenced version is not supported, try legacy version.
        err = mBlockPool->fetchGraphicBlock(mSize.width, mSize.height,
                                            static_cast<uint32_t>(mPixelFormat), mMemoryUsage,
                                            &block);
    }

    std::optional<uint32_t> bufferId;
    if (err == C2_OK) {
        bufferId = getBufferIdFromGraphicBlock(*mBlockPool, *block);

        if (bufferId) {
            ALOGV("%s(): Got buffer with id = %u", __func__, *bufferId);

            if (shouldDropBuffer(*bufferId)) {
                // We drop buffer, since we got more then needed.
                ALOGV("%s(): Dropping allocated buffer with id = %u", __func__, *bufferId);
                bufferId = std::nullopt;
                block.reset();
                err = C2_TIMED_OUT;
            }
        }
    }

    if (err == C2_TIMED_OUT || err == C2_BLOCKING) {
        ALOGV("%s(): fetchGraphicBlock() timeout, waiting %zuus (%zu retry)", __func__, sDelay,
              sNumRetries + 1);
        mFetchTaskRunner->PostDelayedTask(
                FROM_HERE, ::base::BindOnce(&VideoFramePool::getVideoFrameTask, mFetchWeakThis),
                ::base::TimeDelta::FromMicroseconds(sDelay));

        sDelay = std::min(sDelay * 4, kFetchRetryDelayMax);  // Exponential backoff
        sNumRetries++;
        return;
    }

    // Reset to the default value.
    sNumRetries = 0;
    sDelay = kFetchRetryDelayInit;

    if (err != C2_OK) {
        ALOGE("%s(): Failed to fetch block, err=%d", __func__, err);
        return;
    }

    ALOG_ASSERT(block != nullptr);
    std::unique_ptr<VideoFrame> frame = VideoFrame::Create(std::move(block));
    std::optional<FrameWithBlockId> frameWithBlockId;

    if (bufferId && frame) {
        // Only pass the frame + id pair if both have successfully been obtained.
        // Otherwise exit the loop so a nullopt is passed to the client.
        frameWithBlockId = std::make_pair(std::move(frame), *bufferId);
        mBuffers.insert(*bufferId);
    } else {
        ALOGE("%s(): Failed to generate VideoFrame or get the buffer id.", __func__);
    }

    mClientTaskRunner->PostTask(
            FROM_HERE, ::base::BindOnce(&VideoFramePool::onVideoFrameReady, mClientWeakThis,
                                        std::move(frameWithBlockId)));
}

void VideoFramePool::onVideoFrameReady(std::optional<FrameWithBlockId> frameWithBlockId) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mClientTaskRunner->RunsTasksInCurrentSequence());

    if (!frameWithBlockId) {
        ALOGE("Failed to get GraphicBlock, abandoning all pending requests.");
        mClientWeakThisFactory.InvalidateWeakPtrs();
        mClientWeakThis = mClientWeakThisFactory.GetWeakPtr();
    }

    ALOG_ASSERT(mOutputCb);
    std::move(mOutputCb).Run(std::move(frameWithBlockId));
}

}  // namespace android
