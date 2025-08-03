// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
// Note: ported from Chromium commit head: 2f13d62f0c0d
// Note: Added some missing defines that are only defined in newer kernel
//       versions (e.g. V4L2_PIX_FMT_VP8_FRAME)

//#define LOG_NDEBUG 0
#define ATRACE_TAG ATRACE_TAG_VIDEO
#define LOG_TAG "V4L2Device"

#include <linux/v4l2-controls.h>
#include <v4l2_codec2/v4l2/V4L2Device.h>

#include <fcntl.h>
#include <inttypes.h>
#include <linux/media.h>
#include <linux/videodev2.h>
#include <poll.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <utils/Trace.h>

#include <algorithm>
#include <mutex>
#include <set>
#include <sstream>

#include <base/bind.h>
#include <base/numerics/safe_conversions.h>
#include <base/posix/eintr_wrapper.h>
#include <base/strings/stringprintf.h>
#include <base/thread_annotations.h>
#include <utils/Log.h>

#include <v4l2_codec2/common/Fourcc.h>
#include <v4l2_codec2/common/VideoPixelFormat.h>

namespace android {

bool isValidPixFmtForCodec(VideoCodec codec, uint32_t pixFmt) {
    switch (pixFmt) {
    case V4L2_PIX_FMT_H264:
    case V4L2_PIX_FMT_H264_SLICE:
        return codec == VideoCodec::H264;
        break;
    case V4L2_PIX_FMT_VP8:
    case V4L2_PIX_FMT_VP8_FRAME:
        return codec == VideoCodec::VP8;
        break;
    case V4L2_PIX_FMT_VP9:
    case V4L2_PIX_FMT_VP9_FRAME:
        return codec == VideoCodec::VP9;
        break;
    case V4L2_PIX_FMT_HEVC:
    case V4L2_PIX_FMT_HEVC_SLICE:
        return codec == VideoCodec::HEVC;
        break;
    default:
        ALOGE("Unhandled pixelformat %s", fourccToString(pixFmt).c_str());
        return false;
    }
}

struct v4l2_format buildV4L2Format(const enum v4l2_buf_type type, uint32_t fourcc,
                                   const ui::Size& size, size_t buffer_size, uint32_t stride) {
    struct v4l2_format format;
    memset(&format, 0, sizeof(format));
    format.type = type;
    format.fmt.pix_mp.pixelformat = fourcc;
    format.fmt.pix_mp.width = size.width;
    format.fmt.pix_mp.height = size.height;
    format.fmt.pix_mp.num_planes = V4L2Device::getNumPlanesOfV4L2PixFmt(fourcc);
    format.fmt.pix_mp.plane_fmt[0].sizeimage = buffer_size;

    // When the image format is planar the bytesperline value applies to the first plane and is
    // divided by the same factor as the width field for the other planes.
    format.fmt.pix_mp.plane_fmt[0].bytesperline = stride;

    return format;
}

V4L2ExtCtrl::V4L2ExtCtrl(uint32_t id) {
    memset(&ctrl, 0, sizeof(ctrl));
    ctrl.id = id;
}

V4L2ExtCtrl::V4L2ExtCtrl(uint32_t id, int32_t val) : V4L2ExtCtrl(id) {
    ctrl.value = val;
}

// Class used to store the state of a buffer that should persist between reference creations. This
// includes:
// * Result of initial VIDIOC_QUERYBUF ioctl,
// * Plane mappings.
//
// Also provides helper functions.
class V4L2Buffer {
public:
    static std::unique_ptr<V4L2Buffer> create(scoped_refptr<V4L2Device> device,
                                              enum v4l2_buf_type type, enum v4l2_memory memory,
                                              const struct v4l2_format& format, size_t bufferId);
    ~V4L2Buffer();

    V4L2Buffer(const V4L2Buffer&) = delete;
    V4L2Buffer& operator=(const V4L2Buffer&) = delete;

    void* getPlaneMapping(const size_t plane);
    size_t getMemoryUsage() const;
    const struct v4l2_buffer& v4l2_buffer() const { return mV4l2Buffer; }

private:
    V4L2Buffer(scoped_refptr<V4L2Device> device, enum v4l2_buf_type type, enum v4l2_memory memory,
               const struct v4l2_format& format, size_t bufferId);
    bool query();

    scoped_refptr<V4L2Device> mDevice;
    std::vector<void*> mPlaneMappings;

    // V4L2 data as queried by QUERYBUF.
    struct v4l2_buffer mV4l2Buffer;
    // WARNING: do not change this to a vector or something smaller than VIDEO_MAX_PLANES, otherwise
    // the Tegra libv4l2 will write data beyond the number of allocated planes, resulting in memory
    // corruption.
    struct v4l2_plane mV4l2Planes[VIDEO_MAX_PLANES];

    struct v4l2_format mFormat __attribute__((unused));
};

std::unique_ptr<V4L2Buffer> V4L2Buffer::create(scoped_refptr<V4L2Device> device,
                                               enum v4l2_buf_type type, enum v4l2_memory memory,
                                               const struct v4l2_format& format, size_t bufferId) {
    // Not using std::make_unique because constructor is private.
    std::unique_ptr<V4L2Buffer> buffer(new V4L2Buffer(device, type, memory, format, bufferId));

    if (!buffer->query()) return nullptr;

    return buffer;
}

V4L2Buffer::V4L2Buffer(scoped_refptr<V4L2Device> device, enum v4l2_buf_type type,
                       enum v4l2_memory memory, const struct v4l2_format& format, size_t bufferId)
      : mDevice(device), mFormat(format) {
    ALOG_ASSERT(V4L2_TYPE_IS_MULTIPLANAR(type));
    ALOG_ASSERT(format.fmt.pix_mp.num_planes <= base::size(mV4l2Planes));

    memset(mV4l2Planes, 0, sizeof(mV4l2Planes));
    memset(&mV4l2Buffer, 0, sizeof(mV4l2Buffer));
    mV4l2Buffer.m.planes = mV4l2Planes;
    // Just in case we got more planes than we want.
    mV4l2Buffer.length =
            std::min(static_cast<size_t>(format.fmt.pix_mp.num_planes), base::size(mV4l2Planes));
    mV4l2Buffer.index = bufferId;
    mV4l2Buffer.type = type;
    mV4l2Buffer.memory = memory;
    mV4l2Buffer.memory = V4L2_MEMORY_DMABUF;
    mPlaneMappings.resize(mV4l2Buffer.length);
}

V4L2Buffer::~V4L2Buffer() {
    if (mV4l2Buffer.memory == V4L2_MEMORY_MMAP) {
        for (size_t i = 0; i < mPlaneMappings.size(); i++) {
            if (mPlaneMappings[i] != nullptr) {
                mDevice->munmap(mPlaneMappings[i], mV4l2Buffer.m.planes[i].length);
            }
        }
    }
}

bool V4L2Buffer::query() {
    int ret = mDevice->ioctl(VIDIOC_QUERYBUF, &mV4l2Buffer);
    if (ret) {
        ALOGE("VIDIOC_QUERYBUF failed");
        return false;
    }

    DCHECK(mPlaneMappings.size() == mV4l2Buffer.length);

    return true;
}

void* V4L2Buffer::getPlaneMapping(const size_t plane) {
    if (plane >= mPlaneMappings.size()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return nullptr;
    }

    void* p = mPlaneMappings[plane];
    if (p) {
        return p;
    }

    // Do this check here to avoid repeating it after a buffer has been successfully mapped (we know
    // we are of MMAP type by then).
    if (mV4l2Buffer.memory != V4L2_MEMORY_MMAP) {
        ALOGE("Cannot create mapping on non-MMAP buffer");
        return nullptr;
    }

    p = mDevice->mmap(NULL, mV4l2Buffer.m.planes[plane].length, PROT_READ | PROT_WRITE, MAP_SHARED,
                      mV4l2Buffer.m.planes[plane].m.mem_offset);
    if (p == MAP_FAILED) {
        ALOGE("mmap() failed: ");
        return nullptr;
    }

    mPlaneMappings[plane] = p;
    return p;
}

size_t V4L2Buffer::getMemoryUsage() const {
    size_t usage = 0;
    for (size_t i = 0; i < mV4l2Buffer.length; i++) {
        usage += mV4l2Buffer.m.planes[i].length;
    }
    return usage;
}

// A thread-safe pool of buffer indexes, allowing buffers to be obtained and returned from different
// threads. All the methods of this class are thread-safe. Users should keep a scoped_refptr to
// instances of this class in order to ensure the list remains alive as long as they need it.
class V4L2BuffersList : public base::RefCountedThreadSafe<V4L2BuffersList> {
public:
    V4L2BuffersList() = default;

    V4L2BuffersList(const V4L2BuffersList&) = delete;
    V4L2BuffersList& operator=(const V4L2BuffersList&) = delete;

    // Return a buffer to this list. Also can be called to set the initial pool of buffers.
    // Note that it is illegal to return the same buffer twice.
    void returnBuffer(size_t bufferId);
    // Get any of the buffers in the list. There is no order guarantee whatsoever.
    std::optional<size_t> getFreeBuffer();
    // Get the buffer with specified index.
    std::optional<size_t> getFreeBuffer(size_t requestedBufferId);
    // Number of buffers currently in this list.
    size_t size() const;

private:
    friend class base::RefCountedThreadSafe<V4L2BuffersList>;
    ~V4L2BuffersList() = default;

    mutable std::mutex mLock;
    std::set<size_t> mFreeBuffers GUARDED_BY(mLock);
};

void V4L2BuffersList::returnBuffer(size_t bufferId) {
    std::lock_guard<std::mutex> lock(mLock);

    auto inserted = mFreeBuffers.emplace(bufferId);
    if (!inserted.second) {
        ALOGE("Returning buffer failed");
    }
}

std::optional<size_t> V4L2BuffersList::getFreeBuffer() {
    std::lock_guard<std::mutex> lock(mLock);

    auto iter = mFreeBuffers.begin();
    if (iter == mFreeBuffers.end()) {
        ALOGV("No free buffer available!");
        return std::nullopt;
    }

    size_t bufferId = *iter;
    mFreeBuffers.erase(iter);

    return bufferId;
}

std::optional<size_t> V4L2BuffersList::getFreeBuffer(size_t requestedBufferId) {
    std::lock_guard<std::mutex> lock(mLock);

    return (mFreeBuffers.erase(requestedBufferId) > 0) ? std::make_optional(requestedBufferId)
                                                       : std::nullopt;
}

size_t V4L2BuffersList::size() const {
    std::lock_guard<std::mutex> lock(mLock);

    return mFreeBuffers.size();
}

// Module-private class that let users query/write V4L2 buffer information. It also makes some
// private V4L2Queue methods available to this module only.
class V4L2BufferRefBase {
public:
    V4L2BufferRefBase(const struct v4l2_buffer& v4l2Buffer, base::WeakPtr<V4L2Queue> queue);
    ~V4L2BufferRefBase();

    V4L2BufferRefBase(const V4L2BufferRefBase&) = delete;
    V4L2BufferRefBase& operator=(const V4L2BufferRefBase&) = delete;

    bool queueBuffer();
    void* getPlaneMapping(const size_t plane);

    // Checks that the number of passed FDs is adequate for the current format and buffer
    // configuration. Only useful for DMABUF buffers.
    bool checkNumFDsForFormat(const size_t numFds) const;

    // Data from the buffer, that users can query and/or write.
    struct v4l2_buffer mV4l2Buffer;
    // WARNING: do not change this to a vector or something smaller than VIDEO_MAX_PLANES, otherwise
    // the Tegra libv4l2 will write data beyond the number of allocated planes, resulting in memory
    // corruption.
    struct v4l2_plane mV4l2Planes[VIDEO_MAX_PLANES];

private:
    size_t bufferId() const { return mV4l2Buffer.index; }

    friend class V4L2WritableBufferRef;
    // A weak pointer to the queue this buffer belongs to. Will remain valid as long as the
    // underlying V4L2 buffer is valid too. This can only be accessed from the sequence protected by
    // sequence_checker_. Thread-safe methods (like ~V4L2BufferRefBase) must *never* access this.
    base::WeakPtr<V4L2Queue> mQueue;
    // Where to return this buffer if it goes out of scope without being queued.
    scoped_refptr<V4L2BuffersList> mReturnTo;
    bool queued = false;

    SEQUENCE_CHECKER(mSequenceChecker);
};

V4L2BufferRefBase::V4L2BufferRefBase(const struct v4l2_buffer& v4l2Buffer,
                                     base::WeakPtr<V4L2Queue> queue)
      : mQueue(std::move(queue)), mReturnTo(mQueue->mFreeBuffers) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(V4L2_TYPE_IS_MULTIPLANAR(v4l2Buffer.type));
    ALOG_ASSERT(v4l2Buffer.length <= base::size(mV4l2Planes));
    ALOG_ASSERT(mReturnTo);

    memcpy(&mV4l2Buffer, &v4l2Buffer, sizeof(mV4l2Buffer));
    memcpy(mV4l2Planes, v4l2Buffer.m.planes, sizeof(struct v4l2_plane) * v4l2Buffer.length);
    mV4l2Buffer.m.planes = mV4l2Planes;
}

V4L2BufferRefBase::~V4L2BufferRefBase() {
    // We are the last reference and are only accessing the thread-safe mReturnTo, so we are safe
    // to call from any sequence. If we have been queued, then the queue is our owner so we don't
    // need to return to the free buffers list.
    if (!queued) mReturnTo->returnBuffer(bufferId());
}

bool V4L2BufferRefBase::queueBuffer() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (!mQueue) return false;

    queued = mQueue->queueBuffer(&mV4l2Buffer);

    return queued;
}

void* V4L2BufferRefBase::getPlaneMapping(const size_t plane) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (!mQueue) return nullptr;

    return mQueue->mBuffers[bufferId()]->getPlaneMapping(plane);
}

bool V4L2BufferRefBase::checkNumFDsForFormat(const size_t numFds) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (!mQueue) return false;

    // We have not used SetFormat(), assume this is ok.
    // Hopefully we standardize SetFormat() in the future.
    if (!mQueue->mCurrentFormat) return true;

    const size_t requiredFds = mQueue->mCurrentFormat->fmt.pix_mp.num_planes;
    // Sanity check.
    ALOG_ASSERT(mV4l2Buffer.length == requiredFds);
    if (numFds < requiredFds) {
        ALOGE("Insufficient number of FDs given for the current format. "
              "%zu provided, %zu required.",
              numFds, requiredFds);
        return false;
    }

    const auto* planes = mV4l2Buffer.m.planes;
    for (size_t i = mV4l2Buffer.length - 1; i >= numFds; --i) {
        // Assume that an fd is a duplicate of a previous plane's fd if offset != 0. Otherwise, if
        // offset == 0, return error as it is likely pointing to a new plane.
        if (planes[i].data_offset == 0) {
            ALOGE("Additional dmabuf fds point to a new buffer.");
            return false;
        }
    }

    return true;
}

V4L2WritableBufferRef::V4L2WritableBufferRef(const struct v4l2_buffer& v4l2Buffer,
                                             base::WeakPtr<V4L2Queue> queue)
      : mBufferData(std::make_unique<V4L2BufferRefBase>(v4l2Buffer, std::move(queue))) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
}

V4L2WritableBufferRef::V4L2WritableBufferRef(V4L2WritableBufferRef&& other)
      : mBufferData(std::move(other.mBufferData)) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    DCHECK_CALLED_ON_VALID_SEQUENCE(other.mSequenceChecker);
}

V4L2WritableBufferRef::~V4L2WritableBufferRef() {
    // Only valid references should be sequence-checked
    if (mBufferData) {
        DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    }
}

V4L2WritableBufferRef& V4L2WritableBufferRef::operator=(V4L2WritableBufferRef&& other) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    DCHECK_CALLED_ON_VALID_SEQUENCE(other.mSequenceChecker);

    if (this == &other) return *this;

    mBufferData = std::move(other.mBufferData);

    return *this;
}

enum v4l2_memory V4L2WritableBufferRef::memory() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return static_cast<enum v4l2_memory>(mBufferData->mV4l2Buffer.memory);
}

bool V4L2WritableBufferRef::doQueue() && {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    bool queued = mBufferData->queueBuffer();

    // Clear our own reference.
    mBufferData.reset();

    return queued;
}

bool V4L2WritableBufferRef::queueMMap() && {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    // Move ourselves so our data gets freed no matter when we return
    V4L2WritableBufferRef self(std::move(*this));

    if (self.memory() != V4L2_MEMORY_MMAP) {
        ALOGE("Called on invalid buffer type!");
        return false;
    }

    return std::move(self).doQueue();
}

bool V4L2WritableBufferRef::queueUserPtr(const std::vector<void*>& ptrs) && {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    // Move ourselves so our data gets freed no matter when we return
    V4L2WritableBufferRef self(std::move(*this));

    if (self.memory() != V4L2_MEMORY_USERPTR) {
        ALOGE("Called on invalid buffer type!");
        return false;
    }

    if (ptrs.size() != self.planesCount()) {
        ALOGE("Provided %zu pointers while we require %u.", ptrs.size(),
              self.mBufferData->mV4l2Buffer.length);
        return false;
    }

    for (size_t i = 0; i < ptrs.size(); i++) {
        self.mBufferData->mV4l2Buffer.m.planes[i].m.userptr =
                reinterpret_cast<unsigned long>(ptrs[i]);
    }

    return std::move(self).doQueue();
}

bool V4L2WritableBufferRef::queueDMABuf(const std::vector<int>& fds) && {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    // Move ourselves so our data gets freed no matter when we return
    V4L2WritableBufferRef self(std::move(*this));

    if (self.memory() != V4L2_MEMORY_DMABUF) {
        ALOGE("Called on invalid buffer type!");
        return false;
    }

    if (!self.mBufferData->checkNumFDsForFormat(fds.size())) return false;

    size_t numPlanes = self.planesCount();
    for (size_t i = 0; i < numPlanes; i++) self.mBufferData->mV4l2Buffer.m.planes[i].m.fd = fds[i];

    return std::move(self).doQueue();
}

size_t V4L2WritableBufferRef::planesCount() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.length;
}

size_t V4L2WritableBufferRef::getPlaneSize(const size_t plane) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return 0;
    }

    return mBufferData->mV4l2Buffer.m.planes[plane].length;
}

void V4L2WritableBufferRef::setPlaneSize(const size_t plane, const size_t size) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    enum v4l2_memory mem = memory();
    if (mem == V4L2_MEMORY_MMAP) {
        ALOG_ASSERT(mBufferData->mV4l2Buffer.m.planes[plane].length == size);
        return;
    }
    ALOG_ASSERT(mem == V4L2_MEMORY_USERPTR || mem == V4L2_MEMORY_DMABUF);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return;
    }

    mBufferData->mV4l2Buffer.m.planes[plane].length = size;
}

void* V4L2WritableBufferRef::getPlaneMapping(const size_t plane) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->getPlaneMapping(plane);
}

void V4L2WritableBufferRef::setTimeStamp(const struct timeval& timestamp) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    mBufferData->mV4l2Buffer.timestamp = timestamp;
}

const struct timeval& V4L2WritableBufferRef::getTimeStamp() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.timestamp;
}

void V4L2WritableBufferRef::setPlaneBytesUsed(const size_t plane, const size_t bytesUsed) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return;
    }

    if (bytesUsed > getPlaneSize(plane)) {
        ALOGE("Set bytes used %zu larger than plane size %zu.", bytesUsed, getPlaneSize(plane));
        return;
    }

    mBufferData->mV4l2Buffer.m.planes[plane].bytesused = bytesUsed;
}

size_t V4L2WritableBufferRef::getPlaneBytesUsed(const size_t plane) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return 0;
    }

    return mBufferData->mV4l2Buffer.m.planes[plane].bytesused;
}

void V4L2WritableBufferRef::setPlaneDataOffset(const size_t plane, const size_t dataOffset) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return;
    }

    mBufferData->mV4l2Buffer.m.planes[plane].data_offset = dataOffset;
}

size_t V4L2WritableBufferRef::bufferId() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.index;
}

V4L2ReadableBuffer::V4L2ReadableBuffer(const struct v4l2_buffer& v4l2Buffer,
                                       base::WeakPtr<V4L2Queue> queue)
      : mBufferData(std::make_unique<V4L2BufferRefBase>(v4l2Buffer, std::move(queue))) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
}

V4L2ReadableBuffer::~V4L2ReadableBuffer() {
    // This method is thread-safe. Since we are the destructor, we are guaranteed to be called from
    // the only remaining reference to us. Also, we are just calling the destructor of buffer_data_,
    // which is also thread-safe.
    ALOG_ASSERT(mBufferData);
}

bool V4L2ReadableBuffer::isLast() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.flags & V4L2_BUF_FLAG_LAST;
}

bool V4L2ReadableBuffer::isKeyframe() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.flags & V4L2_BUF_FLAG_KEYFRAME;
}

struct timeval V4L2ReadableBuffer::getTimeStamp() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.timestamp;
}

size_t V4L2ReadableBuffer::planesCount() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.length;
}

const void* V4L2ReadableBuffer::getPlaneMapping(const size_t plane) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    DCHECK(mBufferData);

    return mBufferData->getPlaneMapping(plane);
}

size_t V4L2ReadableBuffer::getPlaneBytesUsed(const size_t plane) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return 0;
    }

    return mBufferData->mV4l2Planes[plane].bytesused;
}

size_t V4L2ReadableBuffer::getPlaneDataOffset(const size_t plane) const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    if (plane >= planesCount()) {
        ALOGE("Invalid plane %zu requested.", plane);
        return 0;
    }

    return mBufferData->mV4l2Planes[plane].data_offset;
}

size_t V4L2ReadableBuffer::bufferId() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(mBufferData);

    return mBufferData->mV4l2Buffer.index;
}

// This class is used to expose buffer reference classes constructors to this module. This is to
// ensure that nobody else can create buffer references.
class V4L2BufferRefFactory {
public:
    static V4L2WritableBufferRef CreateWritableRef(const struct v4l2_buffer& v4l2Buffer,
                                                   base::WeakPtr<V4L2Queue> queue) {
        return V4L2WritableBufferRef(v4l2Buffer, std::move(queue));
    }

    static V4L2ReadableBufferRef CreateReadableRef(const struct v4l2_buffer& v4l2Buffer,
                                                   base::WeakPtr<V4L2Queue> queue) {
        return new V4L2ReadableBuffer(v4l2Buffer, std::move(queue));
    }
};

//// Helper macros that print the queue type with logs.
#define ALOGEQ(fmt, ...) ALOGE("(%s)" fmt, V4L2Device::v4L2BufferTypeToString(mType), ##__VA_ARGS__)
#define ALOGVQ(fmt, ...) ALOGD("(%s)" fmt, V4L2Device::v4L2BufferTypeToString(mType), ##__VA_ARGS__)

V4L2Queue::V4L2Queue(scoped_refptr<V4L2Device> dev, enum v4l2_buf_type type,
                     base::OnceClosure destroyCb)
      : mType(type), mDevice(dev), mDestroyCb(std::move(destroyCb)) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
}

V4L2Queue::~V4L2Queue() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (mIsStreaming) {
        ALOGEQ("Queue is still streaming, trying to stop it...");
        streamoff();
    }

    ALOG_ASSERT(mQueuedBuffers.empty());
    ALOG_ASSERT(!mFreeBuffers);

    if (!mBuffers.empty()) {
        ALOGEQ("Buffers are still allocated, trying to deallocate them...");
        deallocateBuffers();
    }

    std::move(mDestroyCb).Run();
}

std::optional<struct v4l2_format> V4L2Queue::setFormat(uint32_t fourcc, const ui::Size& size,
                                                       size_t bufferSize, uint32_t stride) {
    ATRACE_CALL();
    struct v4l2_format format = buildV4L2Format(mType, fourcc, size, bufferSize, stride);
    if (mDevice->ioctl(VIDIOC_S_FMT, &format) != 0 || format.fmt.pix_mp.pixelformat != fourcc) {
        ALOGEQ("Failed to set format (format_fourcc=0x%" PRIx32 ")", fourcc);
        return std::nullopt;
    }

    mCurrentFormat = format;
    return mCurrentFormat;
}

std::optional<struct v4l2_format> V4L2Queue::tryFormat(uint32_t fourcc, const ui::Size& size,
                                                       size_t bufferSize) {
    struct v4l2_format format = buildV4L2Format(mType, fourcc, size, bufferSize, 0);
    if (mDevice->ioctl(VIDIOC_TRY_FMT, &format) != 0 || format.fmt.pix_mp.pixelformat != fourcc) {
        ALOGEQ("Tried format not supported (format_fourcc=0x%" PRIx32 ")", fourcc);
        return std::nullopt;
    }

    return format;
}

std::pair<std::optional<struct v4l2_format>, int> V4L2Queue::getFormat() {
    struct v4l2_format format;
    memset(&format, 0, sizeof(format));
    format.type = mType;
    if (mDevice->ioctl(VIDIOC_G_FMT, &format) != 0) {
        ALOGEQ("Failed to get format");
        return std::make_pair(std::nullopt, errno);
    }

    return std::make_pair(format, 0);
}

size_t V4L2Queue::allocateBuffers(size_t count, enum v4l2_memory memory) {
    ATRACE_CALL();
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    ALOG_ASSERT(!mFreeBuffers);
    ALOG_ASSERT(mQueuedBuffers.size() == 0u);

    if (isStreaming()) {
        ALOGEQ("Cannot allocate buffers while streaming.");
        return 0;
    }

    if (mBuffers.size() != 0) {
        ALOGEQ("Cannot allocate new buffers while others are still allocated.");
        return 0;
    }

    if (count == 0) {
        ALOGEQ("Attempting to allocate 0 buffers.");
        return 0;
    }

    // First query the number of planes in the buffers we are about to request. This should not be
    // required, but Tegra's VIDIOC_QUERYBUF will fail on output buffers if the number of specified
    // planes does not exactly match the format.
    struct v4l2_format format = {.type = mType};
    int ret = mDevice->ioctl(VIDIOC_G_FMT, &format);
    if (ret) {
        ALOGEQ("VIDIOC_G_FMT failed");
        return 0;
    }
    mPlanesCount = format.fmt.pix_mp.num_planes;
    ALOG_ASSERT(mPlanesCount <= static_cast<size_t>(VIDEO_MAX_PLANES));

    struct v4l2_requestbuffers reqbufs;
    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.count = count;
    reqbufs.type = mType;
    reqbufs.memory = memory;
    ALOGVQ("Requesting %zu buffers.", count);

    ret = mDevice->ioctl(VIDIOC_REQBUFS, &reqbufs);
    if (ret) {
        ALOGEQ("VIDIOC_REQBUFS failed");
        return 0;
    }
    ALOGVQ("Queue %u: got %u buffers.", mType, reqbufs.count);

    mMemory = memory;

    mFreeBuffers = new V4L2BuffersList();

    // Now query all buffer information.
    for (size_t i = 0; i < reqbufs.count; i++) {
        auto buffer = V4L2Buffer::create(mDevice, mType, mMemory, format, i);

        if (!buffer) {
            deallocateBuffers();

            return 0;
        }

        mBuffers.emplace_back(std::move(buffer));
        mFreeBuffers->returnBuffer(i);
    }

    ALOG_ASSERT(mFreeBuffers);
    ALOG_ASSERT(mFreeBuffers->size() == mBuffers.size());
    ALOG_ASSERT(mQueuedBuffers.size() == 0u);
    reportTraceMetrics();

    return mBuffers.size();
}

bool V4L2Queue::deallocateBuffers() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (isStreaming()) {
        ALOGEQ("Cannot deallocate buffers while streaming.");
        return false;
    }

    if (mBuffers.size() == 0) return true;

    mWeakThisFactory.InvalidateWeakPtrs();
    mBuffers.clear();
    mFreeBuffers = nullptr;

    // Free all buffers.
    struct v4l2_requestbuffers reqbufs;
    memset(&reqbufs, 0, sizeof(reqbufs));
    reqbufs.count = 0;
    reqbufs.type = mType;
    reqbufs.memory = mMemory;

    int ret = mDevice->ioctl(VIDIOC_REQBUFS, &reqbufs);
    if (ret) {
        ALOGEQ("VIDIOC_REQBUFS failed");
        return false;
    }

    ALOG_ASSERT(!mFreeBuffers);
    ALOG_ASSERT(mQueuedBuffers.size() == 0u);
    reportTraceMetrics();

    return true;
}

size_t V4L2Queue::getMemoryUsage() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);
    size_t usage = 0;
    for (const auto& buf : mBuffers) {
        usage += buf->getMemoryUsage();
    }
    return usage;
}

v4l2_memory V4L2Queue::getMemoryType() const {
    return mMemory;
}

std::optional<V4L2WritableBufferRef> V4L2Queue::getFreeBuffer() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    // No buffers allocated at the moment?
    if (!mFreeBuffers) return std::nullopt;

    auto bufferId = mFreeBuffers->getFreeBuffer();
    if (!bufferId.has_value()) return std::nullopt;

    return V4L2BufferRefFactory::CreateWritableRef(mBuffers[bufferId.value()]->v4l2_buffer(),
                                                   mWeakThisFactory.GetWeakPtr());
}

std::optional<V4L2WritableBufferRef> V4L2Queue::getFreeBuffer(size_t requestedBufferIid) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    // No buffers allocated at the moment?
    if (!mFreeBuffers) return std::nullopt;

    auto bufferId = mFreeBuffers->getFreeBuffer(requestedBufferIid);
    if (!bufferId.has_value()) return std::nullopt;

    return V4L2BufferRefFactory::CreateWritableRef(mBuffers[bufferId.value()]->v4l2_buffer(),
                                                   mWeakThisFactory.GetWeakPtr());
}

void V4L2Queue::reportTraceMetrics() {
    // Don't printf labels if ATrace is not enabled
    if (!ATRACE_ENABLED()) return;

    std::string atraceLabel;

    atraceLabel =
            V4L2Device::v4L2BufferTypeToATraceLabel(mDevice->getDebugStreamId(), mType, "streamon");
    ATRACE_INT(atraceLabel.c_str(), isStreaming());

    atraceLabel = V4L2Device::v4L2BufferTypeToATraceLabel(mDevice->getDebugStreamId(), mType,
                                                          "buffers free");
    ATRACE_INT64(atraceLabel.c_str(), freeBuffersCount());

    atraceLabel = V4L2Device::v4L2BufferTypeToATraceLabel(mDevice->getDebugStreamId(), mType,
                                                          "buffers queued");
    ATRACE_INT64(atraceLabel.c_str(), queuedBuffersCount());

    atraceLabel = V4L2Device::v4L2BufferTypeToATraceLabel(mDevice->getDebugStreamId(), mType,
                                                          "buffers allocated");
    ATRACE_INT64(atraceLabel.c_str(), allocatedBuffersCount());
}

bool V4L2Queue::queueBuffer(struct v4l2_buffer* v4l2Buffer) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    int ret = mDevice->ioctl(VIDIOC_QBUF, v4l2Buffer);
    if (ret) {
        ALOGEQ("VIDIOC_QBUF failed");
        return false;
    }

    if (ATRACE_ENABLED()) {
        std::string atraceLabel = V4L2Device::v4L2BufferTypeToATraceLabel(
                mDevice->getDebugStreamId(), mType, "enqueued buffer");
        ATRACE_ASYNC_BEGIN(atraceLabel.c_str(), v4l2Buffer->index);
    }

    auto inserted = mQueuedBuffers.emplace(v4l2Buffer->index);
    if (!inserted.second) {
        ALOGE("Queuing buffer failed");
        return false;
    }

    mDevice->schedulePoll();

    reportTraceMetrics();

    return true;
}

std::pair<bool, V4L2ReadableBufferRef> V4L2Queue::dequeueBuffer() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    // No need to dequeue if no buffers queued.
    if (queuedBuffersCount() == 0) return std::make_pair(true, nullptr);

    if (!isStreaming()) {
        ALOGEQ("Attempting to dequeue a buffer while not streaming.");
        return std::make_pair(true, nullptr);
    }

    struct v4l2_buffer v4l2Buffer;
    memset(&v4l2Buffer, 0, sizeof(v4l2Buffer));
    // WARNING: do not change this to a vector or something smaller than VIDEO_MAX_PLANES, otherwise
    // the Tegra libv4l2 will write data beyond the number of allocated planes, resulting in memory
    // corruption.
    struct v4l2_plane planes[VIDEO_MAX_PLANES];
    memset(planes, 0, sizeof(planes));
    v4l2Buffer.type = mType;
    v4l2Buffer.memory = mMemory;
    v4l2Buffer.m.planes = planes;
    v4l2Buffer.length = mPlanesCount;
    int ret = mDevice->ioctl(VIDIOC_DQBUF, &v4l2Buffer);
    if (ret) {
        // TODO(acourbot): we should not have to check for EPIPE as codec clients should not call
        // this method after the last buffer is dequeued.
        switch (errno) {
        case EAGAIN:
        case EPIPE:
            // This is not an error so we'll need to continue polling but won't provide a buffer.
            mDevice->schedulePoll();
            return std::make_pair(true, nullptr);
        default:
            ALOGEQ("VIDIOC_DQBUF failed");
            return std::make_pair(false, nullptr);
        }
    }

    if (ATRACE_ENABLED()) {
        std::string atraceLabel = V4L2Device::v4L2BufferTypeToATraceLabel(
                mDevice->getDebugStreamId(), mType, "enqueued buffer");
        ATRACE_ASYNC_END(atraceLabel.c_str(), v4l2Buffer.index);
    }

    auto it = mQueuedBuffers.find(v4l2Buffer.index);
    ALOG_ASSERT(it != mQueuedBuffers.end());
    mQueuedBuffers.erase(*it);

    if (queuedBuffersCount() > 0) mDevice->schedulePoll();

    reportTraceMetrics();

    ALOG_ASSERT(mFreeBuffers);
    return std::make_pair(true, V4L2BufferRefFactory::CreateReadableRef(
                                        v4l2Buffer, mWeakThisFactory.GetWeakPtr()));
}

bool V4L2Queue::isStreaming() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    return mIsStreaming;
}

bool V4L2Queue::streamon() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    if (mIsStreaming) return true;

    int arg = static_cast<int>(mType);
    int ret = mDevice->ioctl(VIDIOC_STREAMON, &arg);
    if (ret) {
        ALOGEQ("VIDIOC_STREAMON failed");
        return false;
    }

    mIsStreaming = true;
    reportTraceMetrics();

    return true;
}

bool V4L2Queue::streamoff() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    // We do not check the value of IsStreaming(), because we may have queued buffers to the queue
    // and wish to get them back - in such as case, we may need to do a VIDIOC_STREAMOFF on a
    // stopped queue.

    int arg = static_cast<int>(mType);
    int ret = mDevice->ioctl(VIDIOC_STREAMOFF, &arg);
    if (ret) {
        ALOGEQ("VIDIOC_STREAMOFF failed");
        return false;
    }

    for (const auto& bufferId : mQueuedBuffers) {
        ALOG_ASSERT(mFreeBuffers);
        mFreeBuffers->returnBuffer(bufferId);
    }

    mQueuedBuffers.clear();

    mIsStreaming = false;

    reportTraceMetrics();

    return true;
}

size_t V4L2Queue::allocatedBuffersCount() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    return mBuffers.size();
}

size_t V4L2Queue::freeBuffersCount() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    return mFreeBuffers ? mFreeBuffers->size() : 0;
}

size_t V4L2Queue::queuedBuffersCount() const {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mSequenceChecker);

    return mQueuedBuffers.size();
}

#undef ALOGEQ
#undef ALOGVQ

// This class is used to expose V4L2Queue's constructor to this module. This is to ensure that
// nobody else can create instances of it.
class V4L2QueueFactory {
public:
    static scoped_refptr<V4L2Queue> createQueue(scoped_refptr<V4L2Device> dev,
                                                enum v4l2_buf_type type,
                                                base::OnceClosure destroyCb) {
        return new V4L2Queue(std::move(dev), type, std::move(destroyCb));
    }
};

V4L2Device::V4L2Device(uint32_t debugStreamId) : mDebugStreamId(debugStreamId) {
    DETACH_FROM_SEQUENCE(mClientSequenceChecker);
}

V4L2Device::~V4L2Device() {
    closeDevice();
}

scoped_refptr<V4L2Queue> V4L2Device::getQueue(enum v4l2_buf_type type) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    switch (type) {
    // Supported queue types.
    case V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE:
    case V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE:
        break;
    default:
        ALOGE("Unsupported V4L2 queue type: %u", type);
        return nullptr;
    }

    // TODO(acourbot): we should instead query the device for available queues, and allocate them
    // accordingly. This will do for now though.
    auto it = mQueues.find(type);
    if (it != mQueues.end()) return scoped_refptr<V4L2Queue>(it->second);

    scoped_refptr<V4L2Queue> queue = V4L2QueueFactory::createQueue(
            this, type, base::BindOnce(&V4L2Device::onQueueDestroyed, this, type));

    mQueues[type] = queue.get();
    return queue;
}

void V4L2Device::onQueueDestroyed(v4l2_buf_type bufType) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    auto it = mQueues.find(bufType);
    ALOG_ASSERT(it != mQueues.end());
    mQueues.erase(it);
}

// static
scoped_refptr<V4L2Device> V4L2Device::create(uint32_t debugStreamId) {
    ALOGV("%s()", __func__);
    return scoped_refptr<V4L2Device>(new V4L2Device(debugStreamId));
}

bool V4L2Device::open(Type type, uint32_t v4l2PixFmt) {
    ALOGV("%s()", __func__);

    std::string path = getDevicePathFor(type, v4l2PixFmt);

    if (path.empty()) {
        ALOGE("No devices supporting %s for type: %u", fourccToString(v4l2PixFmt).c_str(),
              static_cast<uint32_t>(type));
        return false;
    }

    if (!openDevicePath(path, type)) {
        ALOGE("Failed opening %s", path.c_str());
        return false;
    }

    mDevicePollInterruptFd.reset(eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC));
    if (!mDevicePollInterruptFd.is_valid()) {
        ALOGE("Failed creating a poll interrupt fd");
        return false;
    }

    return true;
}

int V4L2Device::ioctl(int request, void* arg) {
    ALOG_ASSERT(mDeviceFd.is_valid());
    return HANDLE_EINTR(::ioctl(mDeviceFd.get(), request, arg));
}

bool V4L2Device::poll(bool pollDevice, bool pollBuffers, bool* eventPending, bool* buffersPending) {
    struct pollfd pollfds[2];
    nfds_t nfds;
    int pollfd = -1;

    pollfds[0].fd = mDevicePollInterruptFd.get();
    pollfds[0].events = POLLIN | POLLERR;
    nfds = 1;

    if (pollDevice) {
        ALOGV("adding device fd to poll() set");
        pollfds[nfds].fd = mDeviceFd.get();
        pollfds[nfds].events = POLLERR | POLLPRI;
        if (pollBuffers) {
            ALOGV("will poll buffers");
            pollfds[nfds].events |= POLLIN | POLLOUT;
        }
        pollfd = nfds;
        nfds++;
    }

    if (HANDLE_EINTR(::poll(pollfds, nfds, -1)) == -1) {
        ALOGE("poll() failed");
        return false;
    }
    *eventPending = (pollfd != -1 && pollfds[pollfd].revents & POLLPRI);
    *buffersPending = (pollfd != -1 && pollfds[pollfd].revents & (POLLIN | POLLOUT));
    return true;
}

void* V4L2Device::mmap(void* addr, unsigned int len, int prot, int flags, unsigned int offset) {
    DCHECK(mDeviceFd.is_valid());
    return ::mmap(addr, len, prot, flags, mDeviceFd.get(), offset);
}

void V4L2Device::munmap(void* addr, unsigned int len) {
    ::munmap(addr, len);
}

bool V4L2Device::setDevicePollInterrupt() {
    ALOGV("%s()", __func__);

    const uint64_t buf = 1;
    if (HANDLE_EINTR(write(mDevicePollInterruptFd.get(), &buf, sizeof(buf))) == -1) {
        ALOGE("write() failed");
        return false;
    }
    return true;
}

bool V4L2Device::clearDevicePollInterrupt() {
    ALOGV("%s()", __func__);

    uint64_t buf;
    if (HANDLE_EINTR(read(mDevicePollInterruptFd.get(), &buf, sizeof(buf))) == -1) {
        if (errno == EAGAIN) {
            // No interrupt flag set, and we're reading nonblocking.  Not an error.
            return true;
        } else {
            ALOGE("read() failed");
            return false;
        }
    }
    return true;
}

std::vector<base::ScopedFD> V4L2Device::getDmabufsForV4L2Buffer(int index, size_t numPlanes,
                                                                enum v4l2_buf_type bufType) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(V4L2_TYPE_IS_MULTIPLANAR(bufType));

    std::vector<base::ScopedFD> dmabufFds;
    for (size_t i = 0; i < numPlanes; ++i) {
        struct v4l2_exportbuffer expbuf;
        memset(&expbuf, 0, sizeof(expbuf));
        expbuf.type = bufType;
        expbuf.index = index;
        expbuf.plane = i;
        expbuf.flags = O_CLOEXEC;
        if (ioctl(VIDIOC_EXPBUF, &expbuf) != 0) {
            dmabufFds.clear();
            break;
        }

        dmabufFds.push_back(base::ScopedFD(expbuf.fd));
    }

    return dmabufFds;
}

std::vector<uint32_t> V4L2Device::preferredInputFormat(Type type) {
    if (type == Type::kEncoder) return {V4L2_PIX_FMT_NV12M, V4L2_PIX_FMT_NV12};

    return {};
}

// static
uint32_t V4L2Device::c2ProfileToV4L2PixFmt(C2Config::profile_t profile, bool sliceBased) {
    if (profile >= C2Config::PROFILE_AVC_BASELINE &&
        profile <= C2Config::PROFILE_AVC_ENHANCED_MULTIVIEW_DEPTH_HIGH) {
        if (sliceBased) {
            return V4L2_PIX_FMT_H264_SLICE;
        } else {
            return V4L2_PIX_FMT_H264;
        }
    } else if (profile >= C2Config::PROFILE_VP8_0 && profile <= C2Config::PROFILE_VP8_3) {
        if (sliceBased) {
            return V4L2_PIX_FMT_VP8_FRAME;
        } else {
            return V4L2_PIX_FMT_VP8;
        }
    } else if (profile >= C2Config::PROFILE_VP9_0 && profile <= C2Config::PROFILE_VP9_3) {
        if (sliceBased) {
            return V4L2_PIX_FMT_VP9_FRAME;
        } else {
            return V4L2_PIX_FMT_VP9;
        }
    } else if (profile >= C2Config::PROFILE_HEVC_MAIN &&
               profile <= C2Config::PROFILE_HEVC_3D_MAIN) {
        if (sliceBased) {
            return V4L2_PIX_FMT_HEVC_SLICE;
        } else {
            return V4L2_PIX_FMT_HEVC;
        }
    } else {
        ALOGE("Unknown profile: %s", profileToString(profile));
        return 0;
    }
}

// static
C2Config::level_t V4L2Device::v4L2LevelToC2Level(VideoCodec codec, uint32_t level) {
    switch (codec) {
    case VideoCodec::H264:
        switch (level) {
        case V4L2_MPEG_VIDEO_H264_LEVEL_1_0:
            return C2Config::LEVEL_AVC_1;
        case V4L2_MPEG_VIDEO_H264_LEVEL_1B:
            return C2Config::LEVEL_AVC_1B;
        case V4L2_MPEG_VIDEO_H264_LEVEL_1_1:
            return C2Config::LEVEL_AVC_1_1;
        case V4L2_MPEG_VIDEO_H264_LEVEL_1_2:
            return C2Config::LEVEL_AVC_1_2;
        case V4L2_MPEG_VIDEO_H264_LEVEL_1_3:
            return C2Config::LEVEL_AVC_1_3;
        case V4L2_MPEG_VIDEO_H264_LEVEL_2_0:
            return C2Config::LEVEL_AVC_2;
        case V4L2_MPEG_VIDEO_H264_LEVEL_2_1:
            return C2Config::LEVEL_AVC_2_1;
        case V4L2_MPEG_VIDEO_H264_LEVEL_2_2:
            return C2Config::LEVEL_AVC_2_2;
        case V4L2_MPEG_VIDEO_H264_LEVEL_3_0:
            return C2Config::LEVEL_AVC_3;
        case V4L2_MPEG_VIDEO_H264_LEVEL_3_1:
            return C2Config::LEVEL_AVC_3_1;
        case V4L2_MPEG_VIDEO_H264_LEVEL_3_2:
            return C2Config::LEVEL_AVC_3_2;
        case V4L2_MPEG_VIDEO_H264_LEVEL_4_0:
            return C2Config::LEVEL_AVC_4;
        case V4L2_MPEG_VIDEO_H264_LEVEL_4_1:
            return C2Config::LEVEL_AVC_4_1;
        case V4L2_MPEG_VIDEO_H264_LEVEL_4_2:
            return C2Config::LEVEL_AVC_4_2;
        case V4L2_MPEG_VIDEO_H264_LEVEL_5_0:
            return C2Config::LEVEL_AVC_5;
#ifdef V4L2_MPEG_VIDEO_H264_LEVEL_5_1
        case V4L2_MPEG_VIDEO_H264_LEVEL_5_1:
            return C2Config::LEVEL_AVC_5_1;
#endif
#ifdef V4L2_MPEG_VIDEO_H264_LEVEL_5_2
        case V4L2_MPEG_VIDEO_H264_LEVEL_5_2:
            return C2Config::LEVEL_AVC_5_2;
#endif
#ifdef V4L2_MPEG_VIDEO_H264_LEVEL_6_0
        case V4L2_MPEG_VIDEO_H264_LEVEL_6_0:
            return C2Config::LEVEL_AVC_6;
#endif
#ifdef V4L2_MPEG_VIDEO_H264_LEVEL_6_1
        case V4L2_MPEG_VIDEO_H264_LEVEL_6_1:
            return C2Config::LEVEL_AVC_6_1;
#endif
#ifdef V4L2_MPEG_VIDEO_H264_LEVEL_6_2
        case V4L2_MPEG_VIDEO_H264_LEVEL_6_2:
            return C2Config::LEVEL_AVC_6_2;
#endif
        }
        break;
    case VideoCodec::VP8:
        return C2Config::LEVEL_UNUSED;
        break;
    case VideoCodec::VP9:
        switch (level) {
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_1_0
        case V4L2_MPEG_VIDEO_VP9_LEVEL_1_0:
            return C2Config::LEVEL_VP9_1;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_1_1:
            return C2Config::LEVEL_VP9_1_1;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_2_0:
            return C2Config::LEVEL_VP9_2;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_2_1:
            return C2Config::LEVEL_VP9_2_1;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_3_0:
            return C2Config::LEVEL_VP9_3;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_3_1:
            return C2Config::LEVEL_VP9_3_1;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_4_0:
            return C2Config::LEVEL_VP9_4;
        case V4L2_MPEG_VIDEO_VP9_LEVEL_4_1:
            return C2Config::LEVEL_VP9_4_1;
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_5_0
        case V4L2_MPEG_VIDEO_VP9_LEVEL_5_0:
            return C2Config::LEVEL_VP9_5;
#endif
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_5_1
        case V4L2_MPEG_VIDEO_VP9_LEVEL_5_1:
            return C2Config::LEVEL_VP9_5_1;
#endif
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_5_2
        case V4L2_MPEG_VIDEO_VP9_LEVEL_5_2:
            return C2Config::LEVEL_VP9_5_2;
#endif
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_6_0
        case V4L2_MPEG_VIDEO_VP9_LEVEL_6_0:
            return C2Config::LEVEL_VP9_6;
#endif
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_6_1
        case V4L2_MPEG_VIDEO_VP9_LEVEL_6_1:
            return C2Config::LEVEL_VP9_6_1;
#endif
#ifdef V4L2_MPEG_VIDEO_VP9_LEVEL_6_2
        case V4L2_MPEG_VIDEO_VP9_LEVEL_6_2:
            return C2Config::LEVEL_VP9_6_2;
#endif
#endif
        default:
            return C2Config::LEVEL_UNUSED;
        }
        break;
    case VideoCodec::HEVC:
        switch (level) {
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_1:
            return C2Config::LEVEL_HEVC_MAIN_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_2:
            return C2Config::LEVEL_HEVC_MAIN_2;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_2_1:
            return C2Config::LEVEL_HEVC_MAIN_2_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_3:
            return C2Config::LEVEL_HEVC_MAIN_3;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_3_1:
            return C2Config::LEVEL_HEVC_MAIN_3_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_4:
            return C2Config::LEVEL_HEVC_MAIN_4;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_4_1:
            return C2Config::LEVEL_HEVC_MAIN_4_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_5:
            return C2Config::LEVEL_HEVC_MAIN_5;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_5_1:
            return C2Config::LEVEL_HEVC_MAIN_5_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_5_2:
            return C2Config::LEVEL_HEVC_MAIN_5_2;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_6:
            return C2Config::LEVEL_HEVC_MAIN_6;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_6_1:
            return C2Config::LEVEL_HEVC_MAIN_6_1;
        case V4L2_MPEG_VIDEO_HEVC_LEVEL_6_2:
            return C2Config::LEVEL_HEVC_MAIN_6_2;
        }
        break;
    default:
        ALOGE("Unknown codec: %u", codec);
    }
    ALOGE("Unknown level: %u", level);
    return C2Config::LEVEL_UNUSED;
}

// static
C2Config::profile_t V4L2Device::v4L2ProfileToC2Profile(VideoCodec codec, uint32_t profile) {
    switch (codec) {
    case VideoCodec::H264:
        switch (profile) {
        case V4L2_MPEG_VIDEO_H264_PROFILE_BASELINE:
            return C2Config::PROFILE_AVC_BASELINE;
        case V4L2_MPEG_VIDEO_H264_PROFILE_CONSTRAINED_BASELINE:
            return C2Config::PROFILE_AVC_CONSTRAINED_BASELINE;
        case V4L2_MPEG_VIDEO_H264_PROFILE_MAIN:
            return C2Config::PROFILE_AVC_MAIN;
        case V4L2_MPEG_VIDEO_H264_PROFILE_EXTENDED:
            return C2Config::PROFILE_AVC_EXTENDED;
        case V4L2_MPEG_VIDEO_H264_PROFILE_HIGH:
            return C2Config::PROFILE_AVC_HIGH;
        }
        break;
    case VideoCodec::VP8:
        switch (profile) {
        case V4L2_MPEG_VIDEO_VP8_PROFILE_0:
            return C2Config::PROFILE_VP8_0;
        case V4L2_MPEG_VIDEO_VP8_PROFILE_1:
            return C2Config::PROFILE_VP8_1;
        case V4L2_MPEG_VIDEO_VP8_PROFILE_2:
            return C2Config::PROFILE_VP8_2;
        case V4L2_MPEG_VIDEO_VP8_PROFILE_3:
            return C2Config::PROFILE_VP8_3;
        }
        break;
    case VideoCodec::VP9:
        switch (profile) {
        case V4L2_MPEG_VIDEO_VP9_PROFILE_0:
            return C2Config::PROFILE_VP9_0;
        case V4L2_MPEG_VIDEO_VP9_PROFILE_1:
            return C2Config::PROFILE_VP9_1;
        case V4L2_MPEG_VIDEO_VP9_PROFILE_2:
            return C2Config::PROFILE_VP9_2;
        case V4L2_MPEG_VIDEO_VP9_PROFILE_3:
            return C2Config::PROFILE_VP9_3;
        }
        break;
    case VideoCodec::HEVC:
        switch (profile) {
        case V4L2_MPEG_VIDEO_HEVC_PROFILE_MAIN:
            return C2Config::PROFILE_HEVC_MAIN;
        case V4L2_MPEG_VIDEO_HEVC_PROFILE_MAIN_STILL_PICTURE:
            return C2Config::PROFILE_HEVC_MAIN_STILL;
        case V4L2_MPEG_VIDEO_HEVC_PROFILE_MAIN_10:
            return C2Config::PROFILE_HEVC_MAIN_10;
        }
        break;
    default:
        ALOGE("Unknown codec: %u", codec);
    }
    ALOGE("Unknown profile: %u", profile);
    return C2Config::PROFILE_UNUSED;
}

// static
uint32_t V4L2Device::videoCodecToPixFmt(VideoCodec codec) {
    switch (codec) {
    case VideoCodec::H264:
        return V4L2_PIX_FMT_H264;
    case VideoCodec::VP8:
        return V4L2_PIX_FMT_VP8;
    case VideoCodec::VP9:
        return V4L2_PIX_FMT_VP9;
    case VideoCodec::HEVC:
        return V4L2_PIX_FMT_HEVC;
    }
}

std::vector<C2Config::level_t> V4L2Device::queryC2Levels(uint32_t pixFmt) {
    auto getSupportedLevels = [this](VideoCodec codec, std::vector<C2Config::level_t>* levels) {
        uint32_t queryId = 0;
        switch (codec) {
        case VideoCodec::H264:
            queryId = V4L2_CID_MPEG_VIDEO_H264_LEVEL;
            break;
#ifdef V4L2_CID_MPEG_VIDEO_VP9_LEVEL
        case VideoCodec::VP9:
            queryId = V4L2_CID_MPEG_VIDEO_VP9_LEVEL;
            break;
#endif
        case VideoCodec::HEVC:
            queryId = V4L2_CID_MPEG_VIDEO_HEVC_LEVEL;
            break;
        default:
            return false;
        }

        v4l2_queryctrl queryCtrl = {};
        queryCtrl.id = queryId;
        if (ioctl(VIDIOC_QUERYCTRL, &queryCtrl) != 0) {
            return false;
        }
        v4l2_querymenu queryMenu = {};
        queryMenu.id = queryCtrl.id;
        for (queryMenu.index = queryCtrl.minimum;
             static_cast<int>(queryMenu.index) <= queryCtrl.maximum; queryMenu.index++) {
            if (ioctl(VIDIOC_QUERYMENU, &queryMenu) == 0) {
                const C2Config::level_t level =
                        V4L2Device::v4L2LevelToC2Level(codec, queryMenu.index);
                if (level != C2Config::LEVEL_UNUSED) levels->push_back(level);
            }
        }
        return true;
    };

    std::vector<C2Config::level_t> levels;
    switch (pixFmt) {
    case V4L2_PIX_FMT_H264:
    case V4L2_PIX_FMT_H264_SLICE:
        if (!getSupportedLevels(VideoCodec::H264, &levels)) {
            ALOGW("Driver doesn't support QUERY H264 levels, "
                  "use default values, 1-5_2");
            levels = {C2Config::LEVEL_AVC_1,   C2Config::LEVEL_AVC_1B,  C2Config::LEVEL_AVC_1_1,
                      C2Config::LEVEL_AVC_1_2, C2Config::LEVEL_AVC_1_3, C2Config::LEVEL_AVC_2,
                      C2Config::LEVEL_AVC_2_1, C2Config::LEVEL_AVC_2_2, C2Config::LEVEL_AVC_3,
                      C2Config::LEVEL_AVC_3_1, C2Config::LEVEL_AVC_3_2, C2Config::LEVEL_AVC_4,
                      C2Config::LEVEL_AVC_4_1, C2Config::LEVEL_AVC_4_2, C2Config::LEVEL_AVC_5,
                      C2Config::LEVEL_AVC_5_1, C2Config::LEVEL_AVC_5_2};
        }
        break;
    case V4L2_PIX_FMT_VP8:
    case V4L2_PIX_FMT_VP8_FRAME:
        if (!getSupportedLevels(VideoCodec::VP8, &levels)) {
            ALOGW("Driver doesn't support QUERY VP8 levels, use default values, unused");
            levels = {C2Config::LEVEL_UNUSED};
        }
        break;
    case V4L2_PIX_FMT_VP9:
    case V4L2_PIX_FMT_VP9_FRAME:
        if (!getSupportedLevels(VideoCodec::VP9, &levels)) {
            ALOGW("Driver doesn't support QUERY VP9 levels, use default values, 1-5");
            levels = {C2Config::LEVEL_VP9_1,   C2Config::LEVEL_VP9_1_1, C2Config::LEVEL_VP9_2,
                      C2Config::LEVEL_VP9_2_1, C2Config::LEVEL_VP9_3,   C2Config::LEVEL_VP9_3_1,
                      C2Config::LEVEL_VP9_4,   C2Config::LEVEL_VP9_4_1, C2Config::LEVEL_VP9_5};
        }
        break;
    case V4L2_PIX_FMT_HEVC:
    case V4L2_PIX_FMT_HEVC_SLICE:
        if (!getSupportedLevels(VideoCodec::VP9, &levels)) {
            ALOGW("Driver doesn't support QUERY HEVC levels, use default values");
            levels = {C2Config::LEVEL_HEVC_MAIN_1,   C2Config::LEVEL_HEVC_MAIN_2,
                      C2Config::LEVEL_HEVC_MAIN_2_1, C2Config::LEVEL_HEVC_MAIN_3,
                      C2Config::LEVEL_HEVC_MAIN_3_1, C2Config::LEVEL_HEVC_MAIN_4,
                      C2Config::LEVEL_HEVC_MAIN_4_1, C2Config::LEVEL_HEVC_MAIN_5,
                      C2Config::LEVEL_HEVC_MAIN_5_1, C2Config::LEVEL_HEVC_MAIN_5_2,
                      C2Config::LEVEL_HEVC_MAIN_6,   C2Config::LEVEL_HEVC_MAIN_6_1,
                      C2Config::LEVEL_HEVC_MAIN_6_2};
        }
        break;
    default:
        ALOGE("Unhandled pixelformat %s", fourccToString(pixFmt).c_str());
        return {};
    }

    std::sort(levels.begin(), levels.end());
    levels.erase(std::unique(levels.begin(), levels.end()), levels.end());
    return levels;
}

std::vector<C2Config::profile_t> V4L2Device::queryC2Profiles(uint32_t pixFmt) {
    auto getSupportedProfiles = [this](VideoCodec codec,
                                       std::vector<C2Config::profile_t>* profiles) {
        uint32_t queryId = 0;
        switch (codec) {
        case VideoCodec::H264:
            queryId = V4L2_CID_MPEG_VIDEO_H264_PROFILE;
            break;
        case VideoCodec::VP8:
            queryId = V4L2_CID_MPEG_VIDEO_VP8_PROFILE;
            break;
        case VideoCodec::VP9:
            queryId = V4L2_CID_MPEG_VIDEO_VP9_PROFILE;
            break;
        case VideoCodec::HEVC:
            queryId = V4L2_CID_MPEG_VIDEO_HEVC_PROFILE;
            break;
        default:
            return false;
        }

        v4l2_queryctrl queryCtrl = {};
        queryCtrl.id = queryId;
        if (ioctl(VIDIOC_QUERYCTRL, &queryCtrl) != 0) {
            return false;
        }
        v4l2_querymenu queryMenu = {};
        queryMenu.id = queryCtrl.id;
        for (queryMenu.index = queryCtrl.minimum;
             static_cast<int>(queryMenu.index) <= queryCtrl.maximum; queryMenu.index++) {
            if (ioctl(VIDIOC_QUERYMENU, &queryMenu) == 0) {
                const C2Config::profile_t profile =
                        V4L2Device::v4L2ProfileToC2Profile(codec, queryMenu.index);
                if (profile != C2Config::PROFILE_UNUSED) profiles->push_back(profile);
            }
        }
        return true;
    };

    std::vector<C2Config::profile_t> profiles;
    switch (pixFmt) {
    case V4L2_PIX_FMT_H264:
    case V4L2_PIX_FMT_H264_SLICE:
        if (!getSupportedProfiles(VideoCodec::H264, &profiles)) {
            ALOGW("Driver doesn't support QUERY H264 profiles, "
                  "use default values, Base, Main, High");
            profiles = {
                    C2Config::PROFILE_AVC_BASELINE,
                    C2Config::PROFILE_AVC_CONSTRAINED_BASELINE,
                    C2Config::PROFILE_AVC_MAIN,
                    C2Config::PROFILE_AVC_HIGH,
            };
        }
        break;
    case V4L2_PIX_FMT_VP8:
    case V4L2_PIX_FMT_VP8_FRAME:
        if (!getSupportedProfiles(VideoCodec::VP8, &profiles)) {
            ALOGW("Driver doesn't support QUERY VP8 profiles, use default values, Profile0");
            profiles = {C2Config::PROFILE_VP8_0};
        }
        break;
    case V4L2_PIX_FMT_VP9:
    case V4L2_PIX_FMT_VP9_FRAME:
        if (!getSupportedProfiles(VideoCodec::VP9, &profiles)) {
            ALOGW("Driver doesn't support QUERY VP9 profiles, use default values, Profile0");
            profiles = {C2Config::PROFILE_VP9_0};
        }
        break;
    case V4L2_PIX_FMT_HEVC:
    case V4L2_PIX_FMT_HEVC_SLICE:
        if (!getSupportedProfiles(VideoCodec::HEVC, &profiles)) {
            ALOGW("Driver doesn't support QUERY HEVC profiles, "
                  "use default values, Main");
            profiles = {
                    C2Config::PROFILE_HEVC_MAIN,
            };
        }
        break;
    default:
        ALOGE("Unhandled pixelformat %s", fourccToString(pixFmt).c_str());
        return {};
    }

    // Erase duplicated profiles.
    std::sort(profiles.begin(), profiles.end());
    profiles.erase(std::unique(profiles.begin(), profiles.end()), profiles.end());
    return profiles;
}

// static
int32_t V4L2Device::c2ProfileToV4L2H264Profile(C2Config::profile_t profile) {
    switch (profile) {
    case C2Config::PROFILE_AVC_BASELINE:
        return V4L2_MPEG_VIDEO_H264_PROFILE_BASELINE;
    case C2Config::PROFILE_AVC_CONSTRAINED_BASELINE:
        return V4L2_MPEG_VIDEO_H264_PROFILE_CONSTRAINED_BASELINE;
    case C2Config::PROFILE_AVC_MAIN:
        return V4L2_MPEG_VIDEO_H264_PROFILE_MAIN;
    case C2Config::PROFILE_AVC_EXTENDED:
        return V4L2_MPEG_VIDEO_H264_PROFILE_EXTENDED;
    case C2Config::PROFILE_AVC_HIGH:
        return V4L2_MPEG_VIDEO_H264_PROFILE_HIGH;
    case C2Config::PROFILE_AVC_HIGH_10:
        return V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_10;
    case C2Config::PROFILE_AVC_HIGH_422:
        return V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_422;
    case C2Config::PROFILE_AVC_HIGH_444_PREDICTIVE:
        return V4L2_MPEG_VIDEO_H264_PROFILE_HIGH_444_PREDICTIVE;
    case C2Config::PROFILE_AVC_SCALABLE_BASELINE:
        return V4L2_MPEG_VIDEO_H264_PROFILE_SCALABLE_BASELINE;
    case C2Config::PROFILE_AVC_SCALABLE_HIGH:
        return V4L2_MPEG_VIDEO_H264_PROFILE_SCALABLE_HIGH;
    case C2Config::PROFILE_AVC_STEREO_HIGH:
        return V4L2_MPEG_VIDEO_H264_PROFILE_STEREO_HIGH;
    case C2Config::PROFILE_AVC_MULTIVIEW_HIGH:
        return V4L2_MPEG_VIDEO_H264_PROFILE_MULTIVIEW_HIGH;
    default:
        ALOGE("Add more cases as needed");
        return -1;
    }
}

// static
int32_t V4L2Device::h264LevelIdcToV4L2H264Level(uint8_t levelIdc) {
    switch (levelIdc) {
    case 10:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_0;
    case 9:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1B;
    case 11:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_1;
    case 12:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_2;
    case 13:
        return V4L2_MPEG_VIDEO_H264_LEVEL_1_3;
    case 20:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_0;
    case 21:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_1;
    case 22:
        return V4L2_MPEG_VIDEO_H264_LEVEL_2_2;
    case 30:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_0;
    case 31:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_1;
    case 32:
        return V4L2_MPEG_VIDEO_H264_LEVEL_3_2;
    case 40:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_0;
    case 41:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_1;
    case 42:
        return V4L2_MPEG_VIDEO_H264_LEVEL_4_2;
    case 50:
        return V4L2_MPEG_VIDEO_H264_LEVEL_5_0;
    case 51:
        return V4L2_MPEG_VIDEO_H264_LEVEL_5_1;
    default:
        ALOGE("Unrecognized levelIdc: %u", static_cast<uint32_t>(levelIdc));
        return -1;
    }
}

// static
v4l2_mpeg_video_bitrate_mode V4L2Device::c2BitrateModeToV4L2BitrateMode(
        C2Config::bitrate_mode_t bitrateMode) {
    switch (bitrateMode) {
    case C2Config::bitrate_mode_t::BITRATE_CONST_SKIP_ALLOWED:
        ALOGW("BITRATE_CONST_SKIP_ALLOWED not supported, defaulting to BITRATE_CONST");
        FALLTHROUGH;
    case C2Config::bitrate_mode_t::BITRATE_CONST:
        return V4L2_MPEG_VIDEO_BITRATE_MODE_CBR;
    case C2Config::bitrate_mode_t::BITRATE_VARIABLE_SKIP_ALLOWED:
        ALOGW("BITRATE_VARIABLE_SKIP_ALLOWED not supported, defaulting to BITRATE_VARIABLE");
        FALLTHROUGH;
    case C2Config::bitrate_mode_t::BITRATE_VARIABLE:
        return V4L2_MPEG_VIDEO_BITRATE_MODE_VBR;
    default:
        ALOGW("Unsupported bitrate mode %u, defaulting to BITRATE_VARIABLE",
              static_cast<uint32_t>(bitrateMode));
        return V4L2_MPEG_VIDEO_BITRATE_MODE_VBR;
    }
}

// static
ui::Size V4L2Device::allocatedSizeFromV4L2Format(const struct v4l2_format& format) {
    ui::Size codedSize;
    ui::Size visibleSize;
    VideoPixelFormat frameFormat = VideoPixelFormat::UNKNOWN;
    size_t bytesPerLine = 0;
    // Total bytes in the frame.
    size_t sizeimage = 0;

    if (V4L2_TYPE_IS_MULTIPLANAR(format.type)) {
        ALOG_ASSERT(format.fmt.pix_mp.num_planes > 0);
        bytesPerLine = base::checked_cast<int>(format.fmt.pix_mp.plane_fmt[0].bytesperline);
        for (size_t i = 0; i < format.fmt.pix_mp.num_planes; ++i) {
            sizeimage += base::checked_cast<int>(format.fmt.pix_mp.plane_fmt[i].sizeimage);
        }
        visibleSize.set(base::checked_cast<int>(format.fmt.pix_mp.width),
                        base::checked_cast<int>(format.fmt.pix_mp.height));
        const uint32_t pixFmt = format.fmt.pix_mp.pixelformat;
        const auto frameFourcc = Fourcc::fromV4L2PixFmt(pixFmt);
        if (!frameFourcc) {
            ALOGE("Unsupported format %s", fourccToString(pixFmt).c_str());
            return codedSize;
        }
        frameFormat = frameFourcc->toVideoPixelFormat();
    } else {
        bytesPerLine = base::checked_cast<int>(format.fmt.pix.bytesperline);
        sizeimage = base::checked_cast<int>(format.fmt.pix.sizeimage);
        visibleSize.set(base::checked_cast<int>(format.fmt.pix.width),
                        base::checked_cast<int>(format.fmt.pix.height));
        const uint32_t fourcc = format.fmt.pix.pixelformat;
        const auto frameFourcc = Fourcc::fromV4L2PixFmt(fourcc);
        if (!frameFourcc) {
            ALOGE("Unsupported format %s", fourccToString(fourcc).c_str());
            return codedSize;
        }
        frameFormat = frameFourcc ? frameFourcc->toVideoPixelFormat() : VideoPixelFormat::UNKNOWN;
    }

    // V4L2 does not provide per-plane bytesperline (bpl) when different components are sharing one
    // physical plane buffer. In this case, it only provides bpl for the first component in the
    // plane. So we can't depend on it for calculating height, because bpl may vary within one
    // physical plane buffer. For example, YUV420 contains 3 components in one physical plane, with
    // Y at 8 bits per pixel, and Cb/Cr at 4 bits per pixel per component, but we only get 8 pits
    // per pixel from bytesperline in physical plane 0. So we need to get total frame bpp from
    // elsewhere to calculate coded height.

    // We need bits per pixel for one component only to calculate the coded width from bytesperline.
    int planeHorizBitsPerPixel = planeHorizontalBitsPerPixel(frameFormat, 0);

    // Adding up bpp for each component will give us total bpp for all components.
    int totalBpp = 0;
    for (size_t i = 0; i < numPlanes(frameFormat); ++i)
        totalBpp += planeBitsPerPixel(frameFormat, i);

    if (sizeimage == 0 || bytesPerLine == 0 || planeHorizBitsPerPixel == 0 || totalBpp == 0 ||
        (bytesPerLine * 8) % planeHorizBitsPerPixel != 0) {
        ALOGE("Invalid format provided");
        return codedSize;
    }

    // Coded width can be calculated by taking the first component's bytesperline, which in V4L2
    // always applies to the first component in physical plane buffer.
    int codedWidth = bytesPerLine * 8 / planeHorizBitsPerPixel;
    // Sizeimage is codedWidth * codedHeight * totalBpp.
    int codedHeight = sizeimage * 8 / codedWidth / totalBpp;

    codedSize.set(codedWidth, codedHeight);
    ALOGV("codedSize=%s", toString(codedSize).c_str());

    // Sanity checks. Calculated coded size has to contain given visible size and fulfill buffer
    // byte size requirements.
    ALOG_ASSERT(contains(Rect(codedSize), Rect(visibleSize)));
    ALOG_ASSERT(sizeimage <= allocationSize(frameFormat, codedSize));

    return codedSize;
}

// static
const char* V4L2Device::v4L2MemoryToString(const v4l2_memory memory) {
    switch (memory) {
    case V4L2_MEMORY_MMAP:
        return "V4L2_MEMORY_MMAP";
    case V4L2_MEMORY_USERPTR:
        return "V4L2_MEMORY_USERPTR";
    case V4L2_MEMORY_DMABUF:
        return "V4L2_MEMORY_DMABUF";
    case V4L2_MEMORY_OVERLAY:
        return "V4L2_MEMORY_OVERLAY";
    default:
        return "UNKNOWN";
    }
}

// static
const char* V4L2Device::v4L2BufferTypeToString(const enum v4l2_buf_type bufType) {
    switch (bufType) {
    case V4L2_BUF_TYPE_VIDEO_OUTPUT:
        return "OUTPUT";
    case V4L2_BUF_TYPE_VIDEO_CAPTURE:
        return "CAPTURE";
    case V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE:
        return "OUTPUT_MPLANE";
    case V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE:
        return "CAPTURE_MPLANE";
    default:
        return "UNKNOWN";
    }
}

// static
std::string V4L2Device::v4L2BufferTypeToATraceLabel(uint32_t debugStreamId,
                                                    const enum v4l2_buf_type type,
                                                    const char* label) {
    const char* queueName;
    switch (type) {
    case V4L2_BUF_TYPE_VIDEO_CAPTURE:
        FALLTHROUGH;
    case V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE:
        queueName = "CAPTURE";
        break;
    case V4L2_BUF_TYPE_VIDEO_OUTPUT:
        FALLTHROUGH;
    case V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE:
        queueName = "OUTPUT";
        break;
    default:
        queueName = "";
        break;
    }

    return base::StringPrintf("#%u V4L2 %s %s", debugStreamId, queueName, label);
}

// static
std::string V4L2Device::v4L2FormatToString(const struct v4l2_format& format) {
    std::ostringstream s;
    s << "v4l2_format type: " << format.type;
    if (format.type == V4L2_BUF_TYPE_VIDEO_CAPTURE || format.type == V4L2_BUF_TYPE_VIDEO_OUTPUT) {
        //  single-planar
        const struct v4l2_pix_format& pix = format.fmt.pix;
        s << ", width_height: " << toString(ui::Size(pix.width, pix.height))
          << ", pixelformat: " << fourccToString(pix.pixelformat) << ", field: " << pix.field
          << ", bytesperline: " << pix.bytesperline << ", sizeimage: " << pix.sizeimage;
    } else if (V4L2_TYPE_IS_MULTIPLANAR(format.type)) {
        const struct v4l2_pix_format_mplane& pixMp = format.fmt.pix_mp;
        // As long as num_planes's type is uint8_t, ostringstream treats it as a char instead of an
        // integer, which is not what we want. Casting pix_mp.num_planes unsigned int solves the
        // issue.
        s << ", width_height: " << toString(ui::Size(pixMp.width, pixMp.height))
          << ", pixelformat: " << fourccToString(pixMp.pixelformat) << ", field: " << pixMp.field
          << ", num_planes: " << static_cast<unsigned int>(pixMp.num_planes);
        for (size_t i = 0; i < pixMp.num_planes; ++i) {
            const struct v4l2_plane_pix_format& plane_fmt = pixMp.plane_fmt[i];
            s << ", plane_fmt[" << i << "].sizeimage: " << plane_fmt.sizeimage << ", plane_fmt["
              << i << "].bytesperline: " << plane_fmt.bytesperline;
        }
    } else {
        s << " unsupported yet.";
    }
    return s.str();
}

// static
std::string V4L2Device::v4L2BufferToString(const struct v4l2_buffer& buffer) {
    std::ostringstream s;
    s << "v4l2_buffer type: " << buffer.type << ", memory: " << buffer.memory
      << ", index: " << buffer.index << " bytesused: " << buffer.bytesused
      << ", length: " << buffer.length;
    if (buffer.type == V4L2_BUF_TYPE_VIDEO_CAPTURE || buffer.type == V4L2_BUF_TYPE_VIDEO_OUTPUT) {
        //  single-planar
        if (buffer.memory == V4L2_MEMORY_MMAP) {
            s << ", m.offset: " << buffer.m.offset;
        } else if (buffer.memory == V4L2_MEMORY_USERPTR) {
            s << ", m.userptr: " << buffer.m.userptr;
        } else if (buffer.memory == V4L2_MEMORY_DMABUF) {
            s << ", m.fd: " << buffer.m.fd;
        };
    } else if (V4L2_TYPE_IS_MULTIPLANAR(buffer.type)) {
        for (size_t i = 0; i < buffer.length; ++i) {
            const struct v4l2_plane& plane = buffer.m.planes[i];
            s << ", m.planes[" << i << "](bytesused: " << plane.bytesused
              << ", length: " << plane.length << ", data_offset: " << plane.data_offset;
            if (buffer.memory == V4L2_MEMORY_MMAP) {
                s << ", m.mem_offset: " << plane.m.mem_offset;
            } else if (buffer.memory == V4L2_MEMORY_USERPTR) {
                s << ", m.userptr: " << plane.m.userptr;
            } else if (buffer.memory == V4L2_MEMORY_DMABUF) {
                s << ", m.fd: " << plane.m.fd;
            }
            s << ")";
        }
    } else {
        s << " unsupported yet.";
    }
    return s.str();
}

// static
std::optional<VideoFrameLayout> V4L2Device::v4L2FormatToVideoFrameLayout(
        const struct v4l2_format& format) {
    if (!V4L2_TYPE_IS_MULTIPLANAR(format.type)) {
        ALOGE("v4l2_buf_type is not multiplanar: 0x%" PRIx32, format.type);
        return std::nullopt;
    }
    const v4l2_pix_format_mplane& pixMp = format.fmt.pix_mp;
    const uint32_t& pixFmt = pixMp.pixelformat;
    const auto videoFourcc = Fourcc::fromV4L2PixFmt(pixFmt);
    if (!videoFourcc) {
        ALOGE("Failed to convert pixel format to VideoPixelFormat: %s",
              fourccToString(pixFmt).c_str());
        return std::nullopt;
    }
    const VideoPixelFormat videoFormat = videoFourcc->toVideoPixelFormat();
    const size_t numBuffers = pixMp.num_planes;
    const size_t numColorPlanes = numPlanes(videoFormat);
    if (numColorPlanes == 0) {
        ALOGE("Unsupported video format for NumPlanes(): %s",
              videoPixelFormatToString(videoFormat).c_str());
        return std::nullopt;
    }
    if (numBuffers > numColorPlanes) {
        ALOGE("pix_mp.num_planes: %zu should not be larger than NumPlanes(%s): %zu", numBuffers,
              videoPixelFormatToString(videoFormat).c_str(), numColorPlanes);
        return std::nullopt;
    }
    // Reserve capacity in advance to prevent unnecessary vector reallocation.
    std::vector<VideoFramePlane> planes;
    planes.reserve(numColorPlanes);
    for (size_t i = 0; i < numBuffers; ++i) {
        const v4l2_plane_pix_format& planeFormat = pixMp.plane_fmt[i];
        planes.push_back(VideoFramePlane{planeFormat.bytesperline, 0u, planeFormat.sizeimage});
    }
    // For the case that #color planes > #buffers, it fills stride of color plane which does not map
    // to buffer. Right now only some pixel formats are supported: NV12, YUV420, YVU420.
    if (numColorPlanes > numBuffers) {
        const uint32_t yStride = planes[0].mStride;
        // Note that y_stride is from v4l2 bytesperline and its type is uint32_t. It is safe to cast
        // to size_t.
        const size_t yStrideAbs = static_cast<size_t>(yStride);
        switch (pixFmt) {
        case V4L2_PIX_FMT_NV12:
            // The stride of UV is the same as Y in NV12. The height is half of Y plane.
            planes.push_back(VideoFramePlane{yStride, yStrideAbs * pixMp.height,
                                             yStrideAbs * pixMp.height / 2});
            ALOG_ASSERT(2u == planes.size());
            break;
        case V4L2_PIX_FMT_YUV420:
        case V4L2_PIX_FMT_YVU420: {
            // The spec claims that two Cx rows (including padding) is exactly as long as one Y row
            // (including padding). So stride of Y must be even number.
            if (yStride % 2 != 0 || pixMp.height % 2 != 0) {
                ALOGE("Plane-Y stride and height should be even; stride: %u, height: %u", yStride,
                      pixMp.height);
                return std::nullopt;
            }
            const uint32_t halfStride = yStride / 2;
            const size_t plane0Area = yStrideAbs * pixMp.height;
            const size_t plane1Area = plane0Area / 4;
            planes.push_back(VideoFramePlane{halfStride, plane0Area, plane1Area});
            planes.push_back(VideoFramePlane{halfStride, plane0Area + plane1Area, plane1Area});
            ALOG_ASSERT(3u == planes.size());
            break;
        }
        default:
            ALOGE("Cannot derive stride for each plane for pixel format %s",
                  fourccToString(pixFmt).c_str());
            return std::nullopt;
        }
    }

    return VideoFrameLayout{videoFormat, ui::Size(pixMp.width, pixMp.height), std::move(planes),
                            (numBuffers > 1)};
}

// static
size_t V4L2Device::getNumPlanesOfV4L2PixFmt(uint32_t pixFmt) {
    std::optional<Fourcc> fourcc = Fourcc::fromV4L2PixFmt(pixFmt);
    if (fourcc && fourcc->isMultiPlanar()) {
        return numPlanes(fourcc->toVideoPixelFormat());
    }
    return 1u;
}

void V4L2Device::getSupportedResolution(uint32_t pixelFormat, ui::Size* minResolution,
                                        ui::Size* maxResolution) {
    maxResolution->set(0, 0);
    minResolution->set(0, 0);
    v4l2_frmsizeenum frameSize;
    memset(&frameSize, 0, sizeof(frameSize));
    frameSize.pixel_format = pixelFormat;
    for (; ioctl(VIDIOC_ENUM_FRAMESIZES, &frameSize) == 0; ++frameSize.index) {
        if (frameSize.type == V4L2_FRMSIZE_TYPE_DISCRETE) {
            if (frameSize.discrete.width >= base::checked_cast<uint32_t>(maxResolution->width) &&
                frameSize.discrete.height >= base::checked_cast<uint32_t>(maxResolution->height)) {
                maxResolution->set(frameSize.discrete.width, frameSize.discrete.height);
            }
            if (isEmpty(*minResolution) ||
                (frameSize.discrete.width <= base::checked_cast<uint32_t>(minResolution->width) &&
                 frameSize.discrete.height <=
                         base::checked_cast<uint32_t>(minResolution->height))) {
                minResolution->set(frameSize.discrete.width, frameSize.discrete.height);
            }
        } else if (frameSize.type == V4L2_FRMSIZE_TYPE_STEPWISE ||
                   frameSize.type == V4L2_FRMSIZE_TYPE_CONTINUOUS) {
            maxResolution->set(frameSize.stepwise.max_width, frameSize.stepwise.max_height);
            minResolution->set(frameSize.stepwise.min_width, frameSize.stepwise.min_height);
            break;
        }
    }
    if (isEmpty(*maxResolution)) {
        maxResolution->set(1920, 1088);
        ALOGE("GetSupportedResolution failed to get maximum resolution for fourcc %s, "
              "fall back to %s",
              fourccToString(pixelFormat).c_str(), toString(*maxResolution).c_str());
    }
    if (isEmpty(*minResolution)) {
        minResolution->set(16, 16);
        ALOGE("GetSupportedResolution failed to get minimum resolution for fourcc %s, "
              "fall back to %s",
              fourccToString(pixelFormat).c_str(), toString(*minResolution).c_str());
    }
}

std::vector<uint32_t> V4L2Device::enumerateSupportedPixelformats(v4l2_buf_type bufType) {
    std::vector<uint32_t> pixelFormats;

    v4l2_fmtdesc fmtDesc;
    memset(&fmtDesc, 0, sizeof(fmtDesc));
    fmtDesc.type = bufType;

    for (; ioctl(VIDIOC_ENUM_FMT, &fmtDesc) == 0; ++fmtDesc.index) {
        ALOGV("Found %s (0x%" PRIx32 ")", fmtDesc.description, fmtDesc.pixelformat);
        pixelFormats.push_back(fmtDesc.pixelformat);
    }

    return pixelFormats;
}

// static
std::vector<C2Config::level_t> V4L2Device::getSupportedDecodeLevels(VideoCodec videoCodecType) {
    std::vector<C2Config::level_t> supportedLevels;
    Type type = Type::kDecoder;

    for (const auto& info : getDeviceInfosForType(type)) {
        scoped_refptr<V4L2Device> device = V4L2Device::create();
        if (!device->openDevicePath(info.first, type)) {
            ALOGV("Failed opening %s", info.first.c_str());
            continue;
        }

        const auto& levels = device->enumerateSupportedDecodeLevels(videoCodecType);
        supportedLevels.insert(supportedLevels.end(), levels.begin(), levels.end());
        device->closeDevice();
    }

    return supportedLevels;
}

// static
SupportedProfiles V4L2Device::getSupportedProfiles(V4L2Device::Type type,
                                                   const std::vector<uint32_t>& pixelFormats) {
    SupportedProfiles supportedProfiles;

    for (const auto& info : getDeviceInfosForType(type)) {
        scoped_refptr<V4L2Device> device = V4L2Device::create();
        if (!device->openDevicePath(info.first, type)) {
            ALOGV("Failed opening %s", info.first.c_str());
            continue;
        }

        const auto& profiles = device->enumerateSupportedProfiles(type, pixelFormats);
        supportedProfiles.insert(supportedProfiles.end(), profiles.begin(), profiles.end());

        device->closeDevice();
    }

    return supportedProfiles;
}

// static
C2Config::profile_t V4L2Device::getDefaultProfile(VideoCodec codec) {
    uint32_t queryId = 0;

    switch (codec) {
    case VideoCodec::H264:
        queryId = V4L2_CID_MPEG_VIDEO_H264_PROFILE;
        break;
    case VideoCodec::VP8:
        queryId = V4L2_CID_MPEG_VIDEO_VP8_PROFILE;
        break;
    case VideoCodec::VP9:
        queryId = V4L2_CID_MPEG_VIDEO_VP9_PROFILE;
        break;
    case VideoCodec::HEVC:
        queryId = V4L2_CID_MPEG_VIDEO_HEVC_PROFILE;
        break;
    default:
        return C2Config::PROFILE_UNUSED;
    }

    for (const auto& info : getDeviceInfosForType(Type::kDecoder)) {
        scoped_refptr<V4L2Device> device = V4L2Device::create();
        if (!device->openDevicePath(info.first, Type::kDecoder)) {
            ALOGV("Failed opening %s", info.first.c_str());
            continue;
        }

        // Call to query control which will return structure including
        // index of default profile
        v4l2_queryctrl queryCtrl = {};
        queryCtrl.id = queryId;
        if (device->ioctl(VIDIOC_QUERYCTRL, &queryCtrl) != 0) {
            device->closeDevice();
            continue;
        }

        v4l2_querymenu queryMenu = {};
        queryMenu.id = queryCtrl.id;
        queryMenu.index = queryCtrl.default_value;
        if (device->ioctl(VIDIOC_QUERYMENU, &queryMenu) == 0) {
            device->closeDevice();
            return v4L2ProfileToC2Profile(codec, queryMenu.index);
        }

        device->closeDevice();
    }
    return C2Config::PROFILE_UNUSED;
}

// static
C2Config::level_t V4L2Device::getDefaultLevel(VideoCodec codec) {
    uint32_t queryId = 0;

    switch (codec) {
    case VideoCodec::H264:
        queryId = V4L2_CID_MPEG_VIDEO_H264_LEVEL;
        break;
#ifdef V4L2_CID_MPEG_VIDEO_VP9_LEVEL
    case VideoCodec::VP9:
        queryId = V4L2_CID_MPEG_VIDEO_VP9_LEVEL;
        break;
#endif
    case VideoCodec::HEVC:
        queryId = V4L2_CID_MPEG_VIDEO_HEVC_LEVEL;
        break;
    default:
        return C2Config::LEVEL_UNUSED;
    }

    for (const auto& info : getDeviceInfosForType(Type::kDecoder)) {
        scoped_refptr<V4L2Device> device = V4L2Device::create();
        if (!device->openDevicePath(info.first, Type::kDecoder)) {
            ALOGV("Failed opening %s", info.first.c_str());
            continue;
        }

        v4l2_queryctrl queryCtrl = {};
        queryCtrl.id = queryId;
        if (device->ioctl(VIDIOC_QUERYCTRL, &queryCtrl) != 0) {  // gets index of default profile
            device->closeDevice();
            continue;
        }

        v4l2_querymenu queryMenu = {};
        queryMenu.id = queryCtrl.id;
        queryMenu.index = queryCtrl.default_value;
        if (device->ioctl(VIDIOC_QUERYMENU, &queryMenu) == 0) {
            device->closeDevice();
            return v4L2LevelToC2Level(codec, queryMenu.index);
        }

        device->closeDevice();
    }

    return C2Config::LEVEL_UNUSED;
}

// static
SupportedCapabilities V4L2Device::queryDecodingCapabilities(VideoCodec codec) {
    SupportedCapabilities caps;
    caps.codec = codec;
    caps.supportedLevels = V4L2Device::getSupportedDecodeLevels(codec);
    caps.defaultLevel = V4L2Device::getDefaultLevel(codec);
    caps.supportedProfiles = V4L2Device::getSupportedProfiles(
            V4L2Device::Type::kDecoder, {V4L2Device::videoCodecToPixFmt(codec)});
    caps.defaultLevel = V4L2Device::getDefaultLevel(codec);

    return caps;
}

// static
SupportedCapabilities V4L2Device::queryEncodingCapabilities(VideoCodec codec) {
    SupportedCapabilities caps;
    caps.codec = codec;
    caps.supportedProfiles = V4L2Device::getSupportedProfiles(
            V4L2Device::Type::kEncoder, {V4L2Device::videoCodecToPixFmt(codec)});
    return caps;
}

std::vector<C2Config::level_t> V4L2Device::enumerateSupportedDecodeLevels(
        VideoCodec videoCodecType) {
    std::vector<C2Config::level_t> supportedLevels;

    const auto& supportedPixelformats =
            enumerateSupportedPixelformats(V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE);

    for (uint32_t pixelFormat : supportedPixelformats) {
        if (isValidPixFmtForCodec(videoCodecType, pixelFormat)) {
            std::vector<C2Config::level_t> levels = queryC2Levels(pixelFormat);
            supportedLevels.insert(supportedLevels.end(), levels.begin(), levels.end());
        }
    }

    return supportedLevels;
}

SupportedProfiles V4L2Device::enumerateSupportedProfiles(
        V4L2Device::Type type, const std::vector<uint32_t>& pixelFormats) {
    SupportedProfiles profiles;

    v4l2_buf_type bufType;
    switch (type) {
    case Type::kDecoder:
        bufType = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        break;
    case Type::kEncoder:
        bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        break;
    }

    const auto& supportedPixelformats = enumerateSupportedPixelformats(bufType);

    for (uint32_t pixelFormat : supportedPixelformats) {
        if (std::find(pixelFormats.begin(), pixelFormats.end(), pixelFormat) == pixelFormats.end())
            continue;

        SupportedProfile profile;
        if (type == Type::kEncoder) {
            profile.max_framerate_numerator = 30;
            profile.max_framerate_denominator = 1;
        }

        getSupportedResolution(pixelFormat, &profile.min_resolution, &profile.max_resolution);

        const auto videoCodecProfiles = queryC2Profiles(pixelFormat);

        for (const auto& videoCodecProfile : videoCodecProfiles) {
            profile.profile = videoCodecProfile;
            profiles.push_back(profile);

            ALOGV("Found profile %s, resolutions: %s %s", profileToString(profile.profile),
                  toString(profile.min_resolution).c_str(),
                  toString(profile.max_resolution).c_str());
        }
    }

    return profiles;
}

bool V4L2Device::startPolling(scoped_refptr<base::SequencedTaskRunner> taskRunner,
                              android::V4L2DevicePoller::EventCallback eventCallback,
                              base::RepeatingClosure errorCallback) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    if (!mDevicePoller) {
        mDevicePoller = std::make_unique<android::V4L2DevicePoller>(this, "V4L2DeviceThreadPoller",
                                                                    std::move(taskRunner));
    }

    bool ret = mDevicePoller->startPolling(std::move(eventCallback), std::move(errorCallback));

    if (!ret) mDevicePoller = nullptr;

    return ret;
}

bool V4L2Device::stopPolling() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    return !mDevicePoller || mDevicePoller->stopPolling();
}

void V4L2Device::schedulePoll() {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    if (!mDevicePoller || !mDevicePoller->isPolling()) return;

    mDevicePoller->schedulePoll();
}

bool V4L2Device::isCtrlExposed(uint32_t ctrlId) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    struct v4l2_queryctrl queryCtrl;
    memset(&queryCtrl, 0, sizeof(queryCtrl));
    queryCtrl.id = ctrlId;

    return ioctl(VIDIOC_QUERYCTRL, &queryCtrl) == 0;
}

bool V4L2Device::setExtCtrls(uint32_t ctrlClass, std::vector<V4L2ExtCtrl> ctrls) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    if (ctrls.empty()) return true;

    struct v4l2_ext_controls extCtrls;
    memset(&extCtrls, 0, sizeof(extCtrls));
    extCtrls.ctrl_class = ctrlClass;
    extCtrls.count = ctrls.size();
    extCtrls.controls = &ctrls[0].ctrl;
    return ioctl(VIDIOC_S_EXT_CTRLS, &extCtrls) == 0;
}

bool V4L2Device::isCommandSupported(uint32_t commandId) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    struct v4l2_encoder_cmd cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.cmd = commandId;

    return ioctl(VIDIOC_TRY_ENCODER_CMD, &cmd) == 0;
}

bool V4L2Device::hasCapabilities(uint32_t capabilities) {
    DCHECK_CALLED_ON_VALID_SEQUENCE(mClientSequenceChecker);

    struct v4l2_capability caps;
    memset(&caps, 0, sizeof(caps));
    if (ioctl(VIDIOC_QUERYCAP, &caps) != 0) {
        ALOGE("Failed to query capabilities");
        return false;
    }

    return (caps.capabilities & capabilities) == capabilities;
}

bool V4L2Device::openDevicePath(const std::string& path, Type /*type*/) {
    ALOG_ASSERT(!mDeviceFd.is_valid());

    mDeviceFd.reset(HANDLE_EINTR(::open(path.c_str(), O_RDWR | O_NONBLOCK | O_CLOEXEC)));
    if (!mDeviceFd.is_valid()) return false;

    return true;
}

void V4L2Device::closeDevice() {
    ALOGV("%s()", __func__);

    mDeviceFd.reset();
}

// static
const V4L2Device::DeviceInfos& V4L2Device::getDeviceInfosForType(V4L2Device::Type type) {
    // video input/output devices are registered as /dev/videoX in V4L2.
    static constexpr const char* kVideoDevicePattern = "/dev/video";
    static const DeviceInfos sNoDevices = {};
    static std::mutex sDeviceInfosCacheLock;
    static std::map<Type, DeviceInfos> sDeviceInfosCache;

    std::lock_guard lock(sDeviceInfosCacheLock);
    if (sDeviceInfosCache.find(type) != sDeviceInfosCache.end()) {
        return sDeviceInfosCache[type];
    }

    v4l2_buf_type bufType;
    switch (type) {
    case Type::kDecoder:
        bufType = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        break;
    case Type::kEncoder:
        bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        break;
    default:
        ALOGE("Only decoder and encoder types are supported!!");
        return sNoDevices;
    }

    DeviceInfos deviceInfos;
    for (int i = 0; i < 10; ++i) {
        std::string path = base::StringPrintf("%s%d", kVideoDevicePattern, i);

        scoped_refptr<V4L2Device> device = V4L2Device::create();
        if (!device->openDevicePath(path, type)) {
            continue;
        }

        const auto& supportedPixelformats = device->enumerateSupportedPixelformats(bufType);
        if (!supportedPixelformats.empty()) {
            ALOGV("Found device: %s", path.c_str());
            deviceInfos.push_back(std::make_pair(path, supportedPixelformats));
        }

        device->closeDevice();
    }

    sDeviceInfosCache[type] = deviceInfos;

    return sDeviceInfosCache[type];
}

std::string V4L2Device::getDevicePathFor(Type type, uint32_t pixFmt) {
    for (const auto& info : getDeviceInfosForType(type)) {
        if (std::find(info.second.begin(), info.second.end(), pixFmt) != info.second.end())
            return info.first;
    }

    return std::string();
}

}  // namespace android
