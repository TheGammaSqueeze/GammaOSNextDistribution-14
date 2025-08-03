/*
 * Copyright (C) 2023 The Android Open Source Project
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

/*
 *  Manage the producer and consumer buffers needed by FrameProviders.
 */
#pragma once

#include <Utils.h>
#include <linux/videodev2.h>
#include <condition_variable>
#include <map>
#include <mutex>
#include <queue>
#include <variant>
#include <vector>

namespace android {
namespace webcam {

enum BufferType {
    V4L2 = 0,
};

struct YuvHardwareBufferDesc {
    uint8_t* yData = nullptr;
    uint32_t yDataLength = 0;
    uint32_t yRowStride = 0;

    uint8_t* uData = nullptr;
    uint32_t uDataLength = 0;
    uint32_t uRowStride = 0;

    uint8_t* vData = nullptr;
    uint32_t vDataLength = 0;
    uint32_t vRowStride = 0;

    uint32_t uvPixelStride = 0;
};

struct ARGBHardwareBufferDesc {
    uint8_t *buf = nullptr;
    uint32_t rowStride = 0;
};

struct HardwareBufferDesc {
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t format = 0;
    uint32_t bufferId = 0;
    std::variant<ARGBHardwareBufferDesc, YuvHardwareBufferDesc> bufferDesc;
};

class BufferManager;

// Base class for Buffer, for future types apart from V4L2
// Thin wrapper over struct v4l2_buffer. The client should not free the memory obtained from
// getMem(). All Buffers are created by BufferManager through BufferCreatorAndDestroyer which is
// transport specific.
// TODO(b/267794640): Change Buffer to have either a std::shared_ptr to BufferManager or have
//                    BufferManager be a singleton. BufferManager should implement
//                    counting 'handout' buffers before freeing. BufferManager should explicitly
//                    check that there are no Buffers alive before freeing the handed out buffers.
//                    This will also ensure that the lifetime of BufferManager >= lifetime of
//                    Buffer.
class Buffer {
  public:
    Buffer() = default;
    virtual BufferType getBufferType() = 0;
    [[nodiscard]] virtual void* getMem() = 0;
    [[nodiscard]] virtual size_t getLength() const = 0;
    virtual void setBytesUsed(uint32_t bytesUsed) = 0;
    virtual ~Buffer() = default;

    void setTimestamp(uint64_t ts) { mTimestamp = ts; }

    [[nodiscard]] uint64_t getTimestamp() const { return mTimestamp; }
    [[nodiscard]] virtual uint32_t getIndex() const = 0;

    friend class BufferManager;

  private:
    uint64_t mTimestamp = 0;
};

class V4L2Buffer : public Buffer {
  public:
    V4L2Buffer(void* mem, const struct v4l2_buffer* buffer) : mMem(mem), mBuffer(*buffer) {}
    V4L2Buffer() { memset(&mBuffer, 0, sizeof(mBuffer)); }
    ~V4L2Buffer() override = default;

    BufferType getBufferType() override { return BufferType::V4L2; }

    // Memory will be freed by BufferManager. Do not free.
    [[nodiscard]] void* getMem() override { return mMem; }

    [[nodiscard]] size_t getLength() const override { return mBuffer.length; }

    void setBytesUsed(uint32_t bytesUsed) override { mBuffer.bytesused = bytesUsed; }

    struct v4l2_buffer* getV4L2Buffer() {
        return &mBuffer;
    }

    [[nodiscard]] uint32_t getIndex() const override { return mBuffer.index; }

  private:
    void* mMem = nullptr;
    struct v4l2_buffer mBuffer {};
};

class BufferProducer {
  public:
    // Returns a free buffer if it is available. This method does not wait for a free
    // buffer. Buffer is owned by BufferProducer. Caller should not manage the lifetime of the
    // object and should ensure that all buffer references are dropped when BufferProducer goes out
    // of scope.
    virtual Buffer* getFreeBufferIfAvailable() = 0;

    // Queues a filled buffer to the BufferManager.
    virtual Status queueFilledBuffer(Buffer* buffer) = 0;

    // Cancels a queued Buffer
    virtual Status cancelBuffer(Buffer* buffer) = 0;
    virtual ~BufferProducer() = default;
};

class BufferConsumer {
  public:
    // Gets a filled buffer from BufferManager (waits if one is not available) and returns the
    // consumer side buffer for the BufferManager to give away to the producer to use.
    // Buffer is owned by BufferConsumer. Caller should not manage the lifetime of the object.
    virtual Buffer* getFilledBufferAndSwap() = 0;
    virtual ~BufferConsumer() = default;
};

class BufferCreatorAndDestroyer {
  public:
    virtual ~BufferCreatorAndDestroyer() = default;
    virtual Status allocateAndMapBuffers(std::shared_ptr<Buffer>* consumerBuffer,
                                         std::vector<std::shared_ptr<Buffer>>* producerBuffer) = 0;
    virtual void destroyBuffers(std::shared_ptr<Buffer>& consumerBuffer,
                                std::vector<std::shared_ptr<Buffer>>& producerBuffers) = 0;
};

class BufferManager : public BufferConsumer, public BufferProducer {
    // There are 2 types of buffers : the consumer side buffer and the producer side buffers.
    // The consumer side needs only 1.
    // The producer side is typically some component which fills in frames such as a FrameProvider
    // class instance.
    // The consumer side is typically some component that fetches filled buffers and sends them over
    // some transport method to the host.

  public:
    // TODO(b/267794640): Look into better memory management
    // BufferCreatorAndDestroyer is owned by the caller, it must be active throughout the lifetime
    // of BufferManager
    explicit BufferManager(BufferCreatorAndDestroyer* crD);
    ~BufferManager() override;
    [[nodiscard]] bool isInited() const { return mInited; }
    Buffer* getFreeBufferIfAvailable() override;
    Status queueFilledBuffer(Buffer* buffer) override;
    Status cancelBuffer(Buffer* buffer) override;
    Buffer* getFilledBufferAndSwap() override;

  private:
    enum BufferState {
        IN_USE = 0,
        FILLED = 1,
        FREE = 2,
    };

    struct BufferItem {
        BufferItem() : buffer(nullptr), state(BufferState::FREE) {}
        BufferItem(std::shared_ptr<Buffer>& buf, BufferState st) : buffer(buf), state(st) {}
        std::shared_ptr<Buffer> buffer;
        BufferState state = BufferState::FREE;
    };

    // Checks if a filled buffer has been made available by the producer and gets the vector index,
    // of the latest filled buffer. Cancels buffers older than the one referenced by index.
    bool filledProducerBufferAvailableLocked(uint32_t* index);
    bool changeProducerBufferStateLocked(Buffer* buffer, BufferState newState);

    bool mInited = false;
    BufferCreatorAndDestroyer* mCrD = nullptr;

    std::mutex mBufferLock;  // guards all operations relating to Buffer and BufferItems
    std::condition_variable mProducerBufferFilled;  // guarded by mBufferLock
    BufferItem mConsumerBufferItem;                 // guarded by mBufferLock
    // Using a simple vector here as we don't expect to have more than 3-4 buffers in circulation
    // We have multiple producer buffers so that skews between other producers being used by the
    // producer side - eg: buffer from camera doesn't get blocked from getting encoded while
    // consumer is still consuming the consumer side buffer (this could happen if we had only 1
    // producer buffer and 1 consumer buffer and there's a skew between camera frame production and
    // consumer (UVC gadget driver etc) frame consumption.
    std::vector<BufferItem> mProducerBufferItems;  // guarded by mBufferLock
};

}  // namespace webcam
}  // namespace android
