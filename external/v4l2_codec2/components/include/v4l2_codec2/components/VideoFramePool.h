// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_FRAME_POOL_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_FRAME_POOL_H

#include <atomic>
#include <memory>
#include <optional>
#include <queue>

#include <C2Buffer.h>
#include <base/callback.h>
#include <base/memory/weak_ptr.h>
#include <base/sequenced_task_runner.h>
#include <base/threading/thread.h>
#include <ui/Size.h>

#include <v4l2_codec2/common/VideoTypes.h>
#include <v4l2_codec2/components/VideoFrame.h>

namespace android {

// Fetch C2GraphicBlock from C2BlockPool and wrap to VideoFrame.
// Provide asynchronous call which avoid the caller busy-polling while
// C2BlockPool::fetchGraphicBlock() times out.
class VideoFramePool {
public:
    using FrameWithBlockId = std::pair<std::unique_ptr<VideoFrame>, uint32_t>;
    using GetVideoFrameCB = ::base::OnceCallback<void(std::optional<FrameWithBlockId>)>;

    static std::unique_ptr<VideoFramePool> Create(
            std::shared_ptr<C2BlockPool> blockPool, const size_t numBuffers, const ui::Size& size,
            HalPixelFormat pixelFormat, bool isSecure,
            scoped_refptr<::base::SequencedTaskRunner> taskRunner);
    ~VideoFramePool();

    // Get a VideoFrame instance, which will be passed via |cb|.
    // If any error occurs, then nullptr will be passed via |cb|.
    // Return false if the previous callback has not been called, and |cb| will
    // be dropped directly.
    bool getVideoFrame(GetVideoFrameCB cb);

private:
    // |blockPool| is the C2BlockPool that we fetch graphic blocks from.
    // |maxBufferCount| maximum number of buffer that should should provide to client
    // |size| is the resolution size of the required graphic blocks.
    // |pixelFormat| is the pixel format of the required graphic blocks.
    // |isSecure| indicates the video stream is encrypted or not.
    // All public methods and the callbacks should be run on |taskRunner|.
    VideoFramePool(std::shared_ptr<C2BlockPool> blockPool, const size_t maxBufferCount,
                   const ui::Size& size, HalPixelFormat pixelFormat, C2MemoryUsage memoryUsage,
                   scoped_refptr<::base::SequencedTaskRunner> taskRunner);
    bool initialize();
    void destroyTask();

    static void getVideoFrameTaskThunk(scoped_refptr<::base::SequencedTaskRunner> taskRunner,
                                       std::optional<::base::WeakPtr<VideoFramePool>> weakPool);
    void getVideoFrameTask();
    void onVideoFrameReady(std::optional<FrameWithBlockId> frameWithBlockId);

    // Returns true if a buffer shall not be handed to client.
    bool shouldDropBuffer(uint32_t bufferId);

    static std::optional<uint32_t> getBufferIdFromGraphicBlock(C2BlockPool& blockPool,
                                                               const C2Block2D& block);

    std::shared_ptr<C2BlockPool> mBlockPool;

    // Holds the number of maximum amount of buffers that VideoFramePool
    // should provide to client.
    size_t mMaxBufferCount;
    // Contains known buffer ids that are valid for the pool.
    std::set<uint32_t> mBuffers;

    const ui::Size mSize;
    const HalPixelFormat mPixelFormat;
    const C2MemoryUsage mMemoryUsage;

    GetVideoFrameCB mOutputCb;

    scoped_refptr<::base::SequencedTaskRunner> mClientTaskRunner;
    ::base::Thread mFetchThread{"VideoFramePoolFetchThread"};
    scoped_refptr<::base::SequencedTaskRunner> mFetchTaskRunner;

    ::base::WeakPtr<VideoFramePool> mClientWeakThis;
    ::base::WeakPtr<VideoFramePool> mFetchWeakThis;
    ::base::WeakPtrFactory<VideoFramePool> mClientWeakThisFactory{this};
    ::base::WeakPtrFactory<VideoFramePool> mFetchWeakThisFactory{this};
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_FRAME_POOL_H
