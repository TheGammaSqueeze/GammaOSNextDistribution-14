// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
//
// This file defines the V4L2Device which is used by the V4L2Decoder and V4L2Encoder classes to
// delegate/pass the device specific handling of any of the functionalities.
// Note: ported from Chromium commit head: 2f13d62f0c0d, but some parts have been removed.

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_H

#include <linux/videodev2.h>
#include <stddef.h>
#include <stdint.h>

#include <cstdint>
#include <optional>
#include <vector>

#include <C2Config.h>
#include <base/containers/flat_map.h>
#include <base/files/scoped_file.h>
#include <base/memory/ref_counted.h>

#include <ui/Size.h>
#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/common/VideoTypes.h>
#include <v4l2_codec2/v4l2/V4L2DevicePoller.h>

// VP8 parsed frames
#ifndef V4L2_PIX_FMT_VP8_FRAME
#define V4L2_PIX_FMT_VP8_FRAME v4l2_fourcc('V', 'P', '8', 'F')
#endif

// VP9 parsed frames
#ifndef V4L2_PIX_FMT_VP9_FRAME
#define V4L2_PIX_FMT_VP9_FRAME v4l2_fourcc('V', 'P', '9', 'F')
#endif

// H264 parsed slices
#ifndef V4L2_PIX_FMT_H264_SLICE
#define V4L2_PIX_FMT_H264_SLICE v4l2_fourcc('S', '2', '6', '4')
#endif

// HEVC parsed slices
#ifndef V4L2_PIX_FMT_HEVC_SLICE
#define V4L2_PIX_FMT_HEVC_SLICE v4l2_fourcc('S', '2', '6', '5')
#endif

namespace android {

class V4L2Queue;
class V4L2BufferRefBase;
class V4L2BuffersList;
class V4L2DecodeSurface;

// Wrapper for the 'v4l2_ext_control' structure.
struct V4L2ExtCtrl {
    V4L2ExtCtrl(uint32_t id);
    V4L2ExtCtrl(uint32_t id, int32_t val);
    struct v4l2_ext_control ctrl;
};

bool isValidPixFmtForCodec(VideoCodec codec, uint32_t pixFmt);

// A unique reference to a buffer for clients to prepare and submit.
//
// Clients can prepare a buffer for queuing using the methods of this class, and then either queue
// it using the Queue() method corresponding to the memory type of the buffer, or drop the reference
// to make the buffer available again.
class V4L2WritableBufferRef {
public:
    V4L2WritableBufferRef(V4L2WritableBufferRef&& other);
    V4L2WritableBufferRef() = delete;
    V4L2WritableBufferRef& operator=(V4L2WritableBufferRef&& other);

    // Return the memory type of the buffer. Useful to e.g. decide which Queue() method to use.
    enum v4l2_memory memory() const;

    // Queue a MMAP buffer. If successful, true is returned and the reference to the buffer is
    // dropped so this reference becomes invalid. In case of error, false is returned and the buffer
    // is returned to the free list.
    bool queueMMap() &&;
    // Queue a USERPTR buffer, assigning |ptrs| as pointer for each plane. The size of |ptrs| must
    // be equal to the number of planes of this buffer. If successful, true is returned and the
    // reference to the buffer is dropped so this reference becomes invalid. In case of error, false
    // is returned and the buffer is returned to the free list.
    bool queueUserPtr(const std::vector<void*>& ptrs) &&;
    // Queue a DMABUF buffer, assigning |fds| as file descriptors for each plane. It is allowed the
    // number of |fds| might be greater than the number of planes of this buffer. It happens when
    // the v4l2 pixel format is single planar. The fd of the first plane is only used in that case.
    // If successful, true is returned and the reference to the buffer is dropped so this reference
    // becomes invalid. In case of error, false is returned and the buffer is returned to the free
    // list.
    bool queueDMABuf(const std::vector<int>& fds) &&;

    // Returns the number of planes in this buffer.
    size_t planesCount() const;
    // Returns the size of the requested |plane|, in bytes.
    size_t getPlaneSize(const size_t plane) const;
    // Set the size of the requested |plane|, in bytes. It is only valid for USERPTR and DMABUF
    // buffers. When using an MMAP buffer, this method triggers an assert and is a no-op for release
    // builds.
    void setPlaneSize(const size_t plane, const size_t size);
    // This method can only be used with MMAP buffers. It will return a pointer to the data of the
    // |plane|th plane. In case of error (invalid plane index or mapping failed), a nullptr is
    // returned.
    void* getPlaneMapping(const size_t plane);
    // Set the timestamp field for this buffer.
    void setTimeStamp(const struct timeval& timestamp);
    // Return the previously-set timestamp field for this buffer.
    const struct timeval& getTimeStamp() const;
    // Set the number of bytes used for |plane|.
    void setPlaneBytesUsed(const size_t plane, const size_t bytesUsed);
    // Returns the previously-set number of bytes used for |plane|.
    size_t getPlaneBytesUsed(const size_t plane) const;
    // Set the data offset for |plane|, in bytes.
    void setPlaneDataOffset(const size_t plane, const size_t dataOffset);

    // Return the V4L2 buffer ID of the underlying buffer.
    size_t bufferId() const;

    ~V4L2WritableBufferRef();

private:
    friend class V4L2BufferRefFactory;

    // Do the actual queue operation once the v4l2_buffer structure is properly filled.
    bool doQueue() &&;

    V4L2WritableBufferRef(const struct v4l2_buffer& v4l2Buffer, ::base::WeakPtr<V4L2Queue> queue);

    V4L2WritableBufferRef(const V4L2WritableBufferRef&) = delete;
    V4L2WritableBufferRef& operator=(const V4L2WritableBufferRef&) = delete;

    std::unique_ptr<V4L2BufferRefBase> mBufferData;

    SEQUENCE_CHECKER(mSequenceChecker);
};

// A reference to a read-only, dequeued buffer.
//
// Clients use this class to query the buffer state and content, and are guaranteed that the buffer
// will not be reused until all references are destroyed.
// All methods of this class must be called from the same sequence, but instances of
// V4L2ReadableBuffer objects can be destroyed from any sequence. They can even outlive the V4L2
// buffers they originate from. This flexibility is required because V4L2ReadableBufferRefs can be
// embedded into VideoFrames, which are then passed to other threads and not necessarily destroyed
// before the V4L2Queue buffers are freed.
class V4L2ReadableBuffer : public ::base::RefCountedThreadSafe<V4L2ReadableBuffer> {
public:
    // Returns whether the V4L2_BUF_FLAG_LAST flag is set for this buffer.
    bool isLast() const;
    // Returns whether the V4L2_BUF_FLAG_KEYFRAME flag is set for this buffer.
    bool isKeyframe() const;
    // Return the timestamp set by the driver on this buffer.
    struct timeval getTimeStamp() const;
    // Returns the number of planes in this buffer.
    size_t planesCount() const;
    // Returns the number of bytes used for |plane|.
    size_t getPlaneBytesUsed(size_t plane) const;
    // Returns the data offset for |plane|.
    size_t getPlaneDataOffset(size_t plane) const;
    // This method can only be used with MMAP buffers. It will return a pointer to the data of the
    // |plane|th plane. In case of error (invalid plane index or mapping failed), a nullptr is
    // returned.
    const void* getPlaneMapping(const size_t plane) const;

    // Return the V4L2 buffer ID of the underlying buffer.
    size_t bufferId() const;

private:
    friend class V4L2BufferRefFactory;
    friend class ::base::RefCountedThreadSafe<V4L2ReadableBuffer>;

    ~V4L2ReadableBuffer();

    V4L2ReadableBuffer(const struct v4l2_buffer& v4l2Buffer, ::base::WeakPtr<V4L2Queue> queue);

    V4L2ReadableBuffer(const V4L2ReadableBuffer&) = delete;
    V4L2ReadableBuffer& operator=(const V4L2ReadableBuffer&) = delete;

    std::unique_ptr<V4L2BufferRefBase> mBufferData;

    SEQUENCE_CHECKER(mSequenceChecker);
};

// Shortcut for naming consistency.
using V4L2ReadableBufferRef = scoped_refptr<V4L2ReadableBuffer>;

class V4L2Device;
class V4L2Buffer;

// Interface representing a specific queue of a |V4L2Device|. It provides free and queued buffer
// management that is commonly required by clients.
//
// Buffers managed by this class undergo the following cycle:
// 1) Allocated buffers are put into a free buffers pool, indicating that they are used neither by
//    the client nor the hardware.
// 2) The client obtains a unique, writable reference to one of the free  buffers in order to set
//    its content and other parameters.
// 3) The client then queues the buffer obtained in 2), which invalidates its reference. The buffer
//    is now prepared to be processed by the hardware.
// 4) Once the hardware is done with the buffer, it is ready to be dequeued by the client. The
//    client obtains a read-only, counted reference to the buffer and can read its content and
//    metadata, as well as making other references to it. The buffer will not be reused until all
//    the references are dropped. Once this happens, the buffer goes back to the free list described
//    in 1).
class V4L2Queue : public ::base::RefCountedThreadSafe<V4L2Queue> {
public:
    // Set |fourcc| as the current format on this queue. |size| corresponds to the desired buffer's
    // dimensions (i.e. width and height members of v4l2_pix_format_mplane (if not applicable, pass
    // Size()).
    // |bufferSize| is the desired size in bytes of the buffer for single-planar formats (i.e.
    // sizeimage of the first plane). It can be set to 0 if not relevant for the desired format.
    // |stride| is the desired stride in bytes of the buffer (i.e. bytesperline). It can be set to 0
    // if not relevant or to let the driver decide. If the format could be set, then the
    // |v4l2_format| reflecting the actual format is returned. It is guaranteed to feature the
    // specified |fourcc|, but any other parameter (including |size| and |bufferSize| may have been
    // adjusted by the driver, so the caller must check their values.
    std::optional<struct v4l2_format> setFormat(uint32_t fourcc, const ui::Size& size,
                                                size_t bufferSize,
                                                uint32_t stride = 0) WARN_UNUSED_RESULT;

    // Identical to |setFormat|, but does not actually apply the format, and can be called anytime.
    // Returns an adjusted V4L2 format if |fourcc| is supported by the queue, or |nullopt| if
    // |fourcc| is not supported or an ioctl error happened.
    std::optional<struct v4l2_format> tryFormat(uint32_t fourcc, const ui::Size& size,
                                                size_t bufferSize) WARN_UNUSED_RESULT;

    // Returns the currently set format on the queue. The result is returned as a std::pair where
    // the first member is the format, or ::base::nullopt if the format could not be obtained due
    // to an ioctl error. The second member is only used in case of an error and contains the
    // |errno| set by the failing ioctl. If the first member is not ::base::nullopt, the second
    // member will always be zero.
    //
    // If the second member is 0, then the first member is guaranteed to have a valid value. So
    // clients that are not interested in the precise error message can just check that the first
    // member is valid and go on.
    //
    // This pair is used because not all failures to get the format are necessarily errors, so we
    // need to way to let the use decide whether it is one or not.
    std::pair<std::optional<struct v4l2_format>, int> getFormat();

    // Allocate |count| buffers for the current format of this queue, with a specific |memory|
    // allocation, and returns the number of buffers allocated or zero if an error occurred, or if
    // references to any previously allocated buffers are still held by any clients.
    //
    // The number of allocated buffers may be larger than the number requested, so callers must
    // always check the return value.
    //
    // Calling this method while buffers are still allocated results in an error.
    size_t allocateBuffers(size_t count, enum v4l2_memory memory) WARN_UNUSED_RESULT;

    // Deallocate all buffers previously allocated by |allocateBuffers|. Any references to buffers
    // previously allocated held by the client must be released, or this call will fail.
    bool deallocateBuffers();

    // Returns the memory usage of v4l2 buffers owned by this V4L2Queue which are mapped in user
    // space memory.
    size_t getMemoryUsage() const;

    // Returns |mMemory|, memory type of last buffers allocated by this V4L2Queue.
    v4l2_memory getMemoryType() const;

    // Return a reference to a free buffer for the caller to prepare and submit, or nullopt if no
    // buffer is currently free.
    //
    // If the caller discards the returned reference, the underlying buffer is made available to
    // clients again.
    std::optional<V4L2WritableBufferRef> getFreeBuffer();
    std::optional<V4L2WritableBufferRef> getFreeBuffer(size_t requestedBufferId);

    // Attempt to dequeue a buffer, and return a reference to it if one was available.
    //
    // The first element of the returned pair will be false if an error occurred, in which case the
    // second element will be nullptr. If no error occurred, then the first element will be true and
    // the second element will contain a reference to the dequeued buffer if one was available, or
    // nullptr otherwise. Dequeued buffers will not be reused by the driver until all references to
    // them are dropped.
    std::pair<bool, V4L2ReadableBufferRef> dequeueBuffer();

    // Returns true if this queue is currently streaming.
    bool isStreaming() const;
    // If not currently streaming, starts streaming. Returns true if we started streaming, or were
    // already streaming, or false if we were not streaming and an error occurred when attempting to
    // start the stream. On failure, any previously-queued buffers will be dequeued without
    // processing and made available to the client, while any buffers held by the client will remain
    // unchanged and their ownership will remain with the client.
    bool streamon();
    // If currently streaming, stops streaming. Also make all queued buffers available to the client
    // again regardless of the streaming state. If an error occurred while attempting to stop
    // streaming, then false is returned and queued buffers are left untouched since the V4L2 queue
    // may still be using them.
    bool streamoff();

    // Returns the number of buffers currently allocated for this queue.
    size_t allocatedBuffersCount() const;
    // Returns the number of currently free buffers on this queue.
    size_t freeBuffersCount() const;
    // Returns the number of buffers currently queued on this queue.
    size_t queuedBuffersCount() const;

private:
    ~V4L2Queue();

    V4L2Queue(const V4L2Queue&) = delete;
    V4L2Queue& operator=(const V4L2Queue&) = delete;

    // Called when clients request a buffer to be queued.
    bool queueBuffer(struct v4l2_buffer* v4l2Buffer);

    void reportTraceMetrics();

    const enum v4l2_buf_type mType;
    enum v4l2_memory mMemory = V4L2_MEMORY_MMAP;
    bool mIsStreaming = false;
    size_t mPlanesCount = 0;
    // Current format as set by SetFormat.
    std::optional<struct v4l2_format> mCurrentFormat;

    std::vector<std::unique_ptr<V4L2Buffer>> mBuffers;

    // Buffers that are available for client to get and submit. Buffers in this list are not
    // referenced by anyone else than ourselves.
    scoped_refptr<V4L2BuffersList> mFreeBuffers;
    // Buffers that have been queued by the client, and not dequeued yet.
    std::set<size_t> mQueuedBuffers;

    scoped_refptr<V4L2Device> mDevice;
    // Callback to call in this queue's destructor.
    ::base::OnceClosure mDestroyCb;

    V4L2Queue(scoped_refptr<V4L2Device> dev, enum v4l2_buf_type type,
              ::base::OnceClosure destroyCb);
    friend class V4L2QueueFactory;
    friend class V4L2BufferRefBase;
    friend class ::base::RefCountedThreadSafe<V4L2Queue>;

    SEQUENCE_CHECKER(mSequenceChecker);

    ::base::WeakPtrFactory<V4L2Queue> mWeakThisFactory{this};
};

class V4L2Device : public ::base::RefCountedThreadSafe<V4L2Device> {
public:
    // Utility format conversion functions
    // If there is no corresponding single- or multi-planar format, returns 0.
    static uint32_t c2ProfileToV4L2PixFmt(C2Config::profile_t profile, bool sliceBased);
    static C2Config::level_t v4L2LevelToC2Level(VideoCodec codec, uint32_t level);
    static C2Config::profile_t v4L2ProfileToC2Profile(VideoCodec codec, uint32_t profile);
    static uint32_t videoCodecToPixFmt(VideoCodec codec);
    // Calculates the largest plane's allocation size requested by a V4L2 device.
    static ui::Size allocatedSizeFromV4L2Format(const struct v4l2_format& format);

    // Convert required H264 profile and level to V4L2 enums.
    static int32_t c2ProfileToV4L2H264Profile(C2Config::profile_t profile);
    static int32_t h264LevelIdcToV4L2H264Level(uint8_t levelIdc);
    static v4l2_mpeg_video_bitrate_mode c2BitrateModeToV4L2BitrateMode(
            C2Config::bitrate_mode_t bitrateMode);

    // Converts v4l2_memory to a string.
    static const char* v4L2MemoryToString(const v4l2_memory memory);

    // Returns the printable name of a v4l2_buf_type.
    static const char* v4L2BufferTypeToString(const enum v4l2_buf_type bufType);

    // Converts v4l2_buf_type to a string, used for tracing.
    static std::string v4L2BufferTypeToATraceLabel(uint32_t debugStreamId,
                                                   const enum v4l2_buf_type type,
                                                   const char* label);

    // Composes human readable string of v4l2_format.
    static std::string v4L2FormatToString(const struct v4l2_format& format);

    // Composes human readable string of v4l2_buffer.
    static std::string v4L2BufferToString(const struct v4l2_buffer& buffer);

    // Composes VideoFrameLayout based on v4l2_format. If error occurs, it returns ::base::nullopt.
    static std::optional<VideoFrameLayout> v4L2FormatToVideoFrameLayout(
            const struct v4l2_format& format);

    // Returns number of planes of |pixFmt|.
    static size_t getNumPlanesOfV4L2PixFmt(uint32_t pixFmt);

    enum class Type { kDecoder, kEncoder };

    // Gets supported coding formats for |type| device and |pixelFormats|
    static SupportedProfiles getSupportedProfiles(Type type,
                                                  const std::vector<uint32_t>& pixelFormats);

    // Gets supported levels for all decoder devices
    static std::vector<C2Config::level_t> getSupportedDecodeLevels(VideoCodec videoCodecType);

    // Get first current profile for any device
    static C2Config::profile_t getDefaultProfile(VideoCodec codec);

    // Gets first current profile for any device
    static C2Config::level_t getDefaultLevel(VideoCodec codec);

    // Gets all capabilites of the decoder devices.
    static SupportedCapabilities queryDecodingCapabilities(VideoCodec codec);

    // Gets all capabilites of the encoder devices.
    static SupportedCapabilities queryEncodingCapabilities(VideoCodec codec);

    // Create and initialize an appropriate V4L2Device instance for the current platform, or return
    // nullptr if not available.
    static scoped_refptr<V4L2Device> create(uint32_t debugStreamId = -1);

    // Open a V4L2 device of |type| for use with |v4l2PixFmt|. Return true on success. The device
    // will be closed in the destructor.
    bool open(Type type, uint32_t v4l2PixFmt);

    // Returns the V4L2Queue corresponding to the requested |type|, or nullptr if the requested
    // queue type is not supported.
    scoped_refptr<V4L2Queue> getQueue(enum v4l2_buf_type type);

    // Parameters and return value are the same as for the standard ioctl() system call.
    int ioctl(int request, void* arg);

    // This method sleeps until either:
    // - SetDevicePollInterrupt() is called (on another thread),
    // - |pollDevice| is true, and there is new event from the device has arrived;
    //   in this case |*eventPending| will be set to true.
    // - |pollBuffers| is true and |pollDevice| is true and there is new data to
    //   be read from the device; in this case |*buffersPending| will be set to true.
    // Returns false on error, true otherwise. This method should be called from a separate thread.
    bool poll(bool pollDevice, bool pollBuffers, bool* eventPending, bool* buffersPending);

    // These methods are used to interrupt the thread sleeping on poll() and force it to return
    // regardless of device state, which is usually when the client is no longer interested in what
    // happens with the device (on cleanup, client state change, etc.). When
    // setDevicePollInterrupt() is called, poll() will return immediately, and any subsequent calls
    // to it will also do so until clearDevicePollInterrupt() is called.
    bool setDevicePollInterrupt();
    bool clearDevicePollInterrupt();

    // Wrappers for standard mmap/munmap system calls.
    void* mmap(void* addr, unsigned int len, int prot, int flags, unsigned int offset);
    void munmap(void* addr, unsigned int len);

    // Return a vector of dmabuf file descriptors, exported for V4L2 buffer with |index|, assuming
    // the buffer contains |numPlanes| V4L2 planes and is of |bufType|. Return an empty vector on
    // failure. The caller is responsible for closing the file descriptors after use.
    std::vector<::base::ScopedFD> getDmabufsForV4L2Buffer(int index, size_t numPlanes,
                                                          enum v4l2_buf_type bufType);

    // Returns the preferred V4L2 input formats for |type| or empty if none.
    std::vector<uint32_t> preferredInputFormat(Type type);

    // NOTE: The below methods to query capabilities have a side effect of closing the
    // previously-open device, if any, and should not be called after Open().

    // Get minimum and maximum resolution for fourcc |pixelFormat| and store to |minResolution| and
    // |maxResolution|.
    void getSupportedResolution(uint32_t pixelFormat, ui::Size* minResolution,
                                ui::Size* maxResolution);

    // Queries supported levels for |pixFmt| pixel format
    std::vector<C2Config::level_t> queryC2Levels(uint32_t pixFmt);

    // Queries supported profiles for |pixFmt| pixel format
    std::vector<C2Config::profile_t> queryC2Profiles(uint32_t pixFmt);

    // Queries supported pixel format for a |bufType| queue type
    std::vector<uint32_t> enumerateSupportedPixelformats(v4l2_buf_type bufType);

    // Queries supported levels for |videoCodecType|
    std::vector<C2Config::level_t> enumerateSupportedDecodeLevels(VideoCodec videoCodecType);

    // Start polling on this V4L2Device. |eventCallback| will be posted to the caller's sequence if
    // a buffer is ready to be dequeued and/or a V4L2 event has been posted. |errorCallback| will
    // be posted to the client's
    // sequence if a polling error has occurred.
    bool startPolling(scoped_refptr<::base::SequencedTaskRunner> taskRunner,
                      android::V4L2DevicePoller::EventCallback eventCallback,
                      ::base::RepeatingClosure errorCallback);
    // Stop polling this V4L2Device if polling was active. No new events will be posted after this
    // method has returned.
    bool stopPolling();
    // Schedule a polling event if polling is enabled. This method is intended to be called from
    // V4L2Queue, clients should not need to call it directly.
    void schedulePoll();

    // Check whether the V4L2 control with specified |ctrlId| is supported.
    bool isCtrlExposed(uint32_t ctrlId);
    // Set the specified list of |ctrls| for the specified |ctrlClass|, returns whether the
    // operation succeeded.
    bool setExtCtrls(uint32_t ctrlClass, std::vector<V4L2ExtCtrl> ctrls);

    // Check whether the V4L2 command with specified |commandId| is supported.
    bool isCommandSupported(uint32_t commandId);
    // Check whether the V4L2 device has the specified |capabilities|.
    bool hasCapabilities(uint32_t capabilities);

    // Returns identifier used for debugging purposes.
    uint32_t getDebugStreamId() { return mDebugStreamId; }

private:
    // Vector of video device node paths and corresponding pixelformats supported by each device node.
    using DeviceInfos = std::vector<std::pair<std::string, std::vector<uint32_t>>>;

    // Enumerate all V4L2 devices on the system for |type| and return them
    static const DeviceInfos& getDeviceInfosForType(V4L2Device::Type type);

    friend class ::base::RefCountedThreadSafe<V4L2Device>;
    V4L2Device(uint32_t debugStreamId);
    ~V4L2Device();

    V4L2Device(const V4L2Device&) = delete;
    V4L2Device& operator=(const V4L2Device&) = delete;

    SupportedProfiles enumerateSupportedProfiles(V4L2Device::Type type,
                                                 const std::vector<uint32_t>& pixelFormats);

    // Open device node for |path| as a device of |type|.
    bool openDevicePath(const std::string& path, Type type);

    // Close the currently open device.
    void closeDevice();

    // Return device node path for device of |type| supporting |pixFmt|, or an empty string if the
    // given combination is not supported by the system.
    std::string getDevicePathFor(V4L2Device::Type type, uint32_t pixFmt);

    // Callback that is called upon a queue's destruction, to cleanup its pointer in mQueues.
    void onQueueDestroyed(v4l2_buf_type buf_type);

    // Identifier used for debugging purposes.
    uint32_t mDebugStreamId;

    // The actual device fd.
    ::base::ScopedFD mDeviceFd;

    // eventfd fd to signal device poll thread when its poll() should be interrupted.
    ::base::ScopedFD mDevicePollInterruptFd;

    // Associates a v4l2_buf_type to its queue.
    ::base::flat_map<enum v4l2_buf_type, V4L2Queue*> mQueues;

    // Used if EnablePolling() is called to signal the user that an event happened or a buffer is
    // ready to be dequeued.
    std::unique_ptr<android::V4L2DevicePoller> mDevicePoller;

    SEQUENCE_CHECKER(mClientSequenceChecker);
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_DEVICE_H
