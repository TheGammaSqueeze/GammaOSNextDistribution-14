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
#pragma once

#include "aemu/base/files/MemStream.h"
#include "aemu/base/Optional.h"
#include "host-common/address_space_graphics_types.h"
#include "aemu/base/synchronization/ConditionVariable.h"
#include "aemu/base/synchronization/Lock.h"
#include "aemu/base/threads/Thread.h"

#include <atomic>
#include <memory>

namespace gfxstream {

class RenderChannelImpl;
class RendererImpl;
class ReadBuffer;
class RingStream;

// A class used to model a thread of the RenderServer. Each one of them
// handles a single guest client / protocol byte stream.
class RenderThread : public android::base::Thread {
    using MemStream = android::base::MemStream;

public:
    // Create a new RenderThread instance.
    RenderThread(RenderChannelImpl* channel,
                 android::base::Stream* loadStream = nullptr,
                 uint32_t virtioGpuContextId = -1);

    // Create a new RenderThread instance tied to the address space device.
    RenderThread(
        struct asg_context context,
        android::base::Stream* loadStream,
        android::emulation::asg::ConsumerCallbacks callbacks,
        uint32_t contextId, uint32_t capsetId,
        std::optional<std::string> nameOpt);
    virtual ~RenderThread();

    // Returns true iff the thread has finished.
    bool isFinished() const { return mFinished.load(std::memory_order_relaxed); }

    void pausePreSnapshot();
    void resume(bool waitForSave);
    void save(android::base::Stream* stream);

private:
    virtual intptr_t main();
    void setFinished();

    // Snapshot support.
    enum class SnapshotState {
        Empty,
        StartSaving,
        StartLoading,
        InProgress,
        Finished,
    };

    // Whether using RenderChannel or a ring buffer.
    enum TransportMode {
        Channel,
        Ring,
    };

    template <class OpImpl>
    void snapshotOperation(android::base::AutoLock* lock, OpImpl&& impl);

    struct SnapshotObjects;

    bool doSnapshotOperation(const SnapshotObjects& objects,
                             SnapshotState operation);
    void waitForSnapshotCompletion(android::base::AutoLock* lock);
    void loadImpl(android::base::AutoLock* lock, const SnapshotObjects& objects);
    void saveImpl(android::base::AutoLock* lock, const SnapshotObjects& objects);

    bool isPausedForSnapshotLocked() const;

    RenderChannelImpl* mChannel = nullptr;
    std::unique_ptr<RingStream> mRingStream;

    SnapshotState mState = SnapshotState::Empty;
    std::atomic<bool> mFinished { false };
    android::base::Lock mLock;
    android::base::ConditionVariable mCondVar;
    android::base::Optional<android::base::MemStream> mStream;

    bool mRunInLimitedMode = false;
    uint32_t mContextId = 0;
    uint32_t mCapsetId = 0;
    // If we need to reload process resources.
    // This happens in snapshot testing where we don't snapshot render threads.
    bool mNeedReloadProcessResources = false;
};

}  // namespace gfxstream
