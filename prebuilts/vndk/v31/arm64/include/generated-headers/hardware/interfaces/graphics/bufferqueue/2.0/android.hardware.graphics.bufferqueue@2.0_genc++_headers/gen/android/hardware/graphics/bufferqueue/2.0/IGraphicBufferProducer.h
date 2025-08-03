#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_BUFFERQUEUE_V2_0_IGRAPHICBUFFERPRODUCER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_BUFFERQUEUE_V2_0_IGRAPHICBUFFERPRODUCER_H

#include <android/hardware/graphics/bufferqueue/2.0/IProducerListener.h>
#include <android/hardware/graphics/bufferqueue/2.0/types.h>
#include <android/hardware/graphics/common/1.2/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace graphics {
namespace bufferqueue {
namespace V2_0 {

/**
 * Ref: frameworks/native/include/gui/IGraphicBufferProducer.h:
 *      IGraphicBufferProducer
 * This is a wrapper/wrapped HAL interface for the actual binder interface.
 */
struct IGraphicBufferProducer : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.graphics.bufferqueue@2.0::IGraphicBufferProducer"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct DequeueBufferInput;
    struct DequeueBufferOutput;
    struct QueueBufferInput;
    struct QueueBufferOutput;

    /**
     * Input data for dequeueBuffer() specifying desired attributes of a buffer
     * to dequeue.
     *
     * This structure contains 4 fields from
     * +llndk libnativewindow#AHardwareBuffer_Desc.
     *
     * The `width` and `height` parameters must be no greater than the minimum
     * of `GL_MAX_VIEWPORT_DIMS` and `GL_MAX_TEXTURE_SIZE` (see:
     * glGetIntegerv()). An error due to invalid dimensions may not be reported
     * until updateTexImage() is called.
     *
     * If `width` and `height` are both zero, the default dimensions shall be
     * used. If only one of `width` and `height` is zero, dequeueBuffer() shall
     * return `BAD_VALUE` in `status`.
     *
     * If `format` is zero, the default format shall be used.
     *
     * `usage` shall be merged with the usage flags set from the consumer side.
     *
     * @sa +llndk libnativewindow#AHardwareBuffer_Desc.
     */
    struct DequeueBufferInput final {
        uint32_t width __attribute__ ((aligned(4)));
        uint32_t height __attribute__ ((aligned(4)));
        uint32_t format __attribute__ ((aligned(4)));
        uint64_t usage __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput, width) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput, height) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput, format) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput, usage) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput) == 8, "wrong alignment");

    /**
     * Output data for dequeueBuffer().
     *
     * A `DequeueBufferOutput` object returned from dequeueBuffer() shall be
     * valid if and only if `status` returned from the same call is `OK`.
     */
    struct DequeueBufferOutput final {
        /**
         * The number of frames that have elapsed since the buffer was last
         * queued.
         */
        uint64_t bufferAge __attribute__ ((aligned(8)));
        /**
         * Whether the client must call requestBuffer().
         */
        bool bufferNeedsReallocation __attribute__ ((aligned(1)));
        /**
         * Whether the client must discard the mirrored slot-to-buffer
         * mapping.
         */
        bool releaseAllBuffers __attribute__ ((aligned(1)));
        /**
         * Fence associated with the buffer.
         *
         * If this is an empty fence, the buffer may be written immediately;
         * otherwise, the buffer must not be written to until the fence signals.
         */
        ::android::hardware::hidl_handle fence __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput, bufferAge) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput, bufferNeedsReallocation) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput, releaseAllBuffers) == 9, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput, fence) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput) == 8, "wrong alignment");

    struct QueueBufferInput final {
        /**
         * Timestamp in nanoseconds.
         */
        int64_t timestamp __attribute__ ((aligned(8)));
        /**
         * Whether the timestamp was synthesized at queue time.
         */
        bool isAutoTimestamp __attribute__ ((aligned(1)));
        /**
         * Dataspace of the contents.
         *
         * @sa +ndk libnativewindow#ADataSpace.
         */
        int32_t dataSpace __attribute__ ((aligned(4)));
        /**
         * Crop rectangle that is used as a hint to the consumer.
         */
        ::android::hardware::hidl_array<int32_t, 4> crop __attribute__ ((aligned(4)));
        /**
         * Transformation flags.
         *
         * @sa +ndk libnativewindow#ANativeWindowTransform.
         */
        int32_t transform __attribute__ ((aligned(4)));
        /**
         * The sticky transform set in Surface (only used by the LEGACY camera
         * mode).
         *
         * @sa +ndk libnativewindow#ANativeWindowTransform.
         */
        int32_t stickyTransform __attribute__ ((aligned(4)));
        /**
         * Fence that the consumer must wait on before reading the buffer. An
         * empty fence indicates that the buffer is ready immediately.
         */
        ::android::hardware::hidl_handle fence __attribute__ ((aligned(8)));
        /**
         * List of rectangular pieces covering the damage region.
         */
        ::android::hardware::hidl_vec<::android::hardware::hidl_array<int32_t, 4>> surfaceDamage __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, timestamp) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, isAutoTimestamp) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, dataSpace) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, crop) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, transform) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, stickyTransform) == 36, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, fence) == 40, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput, surfaceDamage) == 56, "wrong offset");
    static_assert(sizeof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput) == 8, "wrong alignment");

    /**
     * Information about the queued buffer. `QueueBufferOutput` is used in both
     * queueBuffer() and connect().
     */
    struct QueueBufferOutput final {
        /**
         * Default width of a buffer in the buffer queue.
         */
        uint32_t width __attribute__ ((aligned(4)));
        /**
         * Default height of a buffer in the buffer queue.
         */
        uint32_t height __attribute__ ((aligned(4)));
        /**
         * The transform hint of the buffer queue.
         *
         * @sa +ndk libnativewindow#ANativeWindowTransform.
         */
        int32_t transformHint __attribute__ ((aligned(4)));
        /**
         * The number of pending buffers in the buffer queue. If this is
         * returned from queueBuffer(), the number shall include the buffer that
         * has just been queued.
         */
        uint32_t numPendingBuffers __attribute__ ((aligned(4)));
        /**
         * The frame number of the next frame. The buffer queue maintains this
         * number and makes sure that it is increasing for every successful
         * queueBuffer() call.
         */
        uint64_t nextFrameNumber __attribute__ ((aligned(8)));
        /**
         * After a successful queueBuffer() call, #bufferReplaced shall be set to
         * true if the queued buffer replaced a previously queued buffer that
         * has not been consumed.
         */
        bool bufferReplaced __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, width) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, height) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, transformHint) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, numPendingBuffers) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, nextFrameNumber) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput, bufferReplaced) == 24, "wrong offset");
    static_assert(sizeof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Sets the maximum number of buffers that can be dequeued at one time. If
     * this method succeeds, any new buffer slots shall be both unallocated and
     * owned by the buffer queue, i.e., they are not owned by the producer or
     * the consumer. Calling this may cause some buffer slots to be emptied. If
     * the caller is caching the contents of the buffer slots, it must empty
     * that cache after calling this method.
     *
     * @p maxDequeuedBuffers must not be less than the number of currently
     * dequeued buffer slots; otherwise, the returned @p status shall be
     * `BAD_VALUE`.
     *
     * @p maxDequeuedBuffers must be at least 1 (inclusive), but at most
     * (`NUM_BUFFER_SLOTS` - the minimum undequeued buffer count) (exclusive).
     * The minimum undequeued buffer count can be obtained by calling
     * `query(ANATIVEWINDOW_QUERY_MIN_UNDEQUEUED_BUFFERS)`.
     *
     * Before calling setMaxDequeuedBufferCount(), the caller must make sure
     * that
     *   - @p maxDequeuedBuffers is greater than or equal to 1.
     *   - @p maxDequeuedBuffers is greater than or equal to the number of
     *     currently dequeued buffer slots.
     * If any of these conditions do not hold, or if the request to set the new
     * maximum number of dequeued buffers cannot be accomplished for any other
     * reasons, `BAD_VALUE` shall be returned in @p status.
     *
     * @param maxDequeuedBuffers The desired number of buffers that can be
     *     dequeued at one time.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> setMaxDequeuedBufferCount(int32_t maxDequeuedBuffers) = 0;

    /**
     * Return callback for requestBuffer
     */
    using requestBuffer_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, const ::android::hardware::graphics::common::V1_2::HardwareBuffer& buffer, uint32_t generationNumber)>;
    /**
     * Assigns a newly created buffer to the given slot index. The client is
     * expected to mirror the slot-to-buffer mapping so that it is not necessary
     * to transfer a `HardwareBuffer` object for every dequeue operation.
     *
     * If @p slot is not a valid slot index corresponding to a dequeued buffer,
     * the call shall fail with @p status set to `BAD_VALUE`.
     *
     * @param slot Slot index.
     * @return status Status of the call.
     * @return buffer New buffer associated to the given slot index.
     * @return generationNumber Generation number of the buffer. If
     *     requestBuffer() is called immediately after dequeueBuffer() returns
     *     with `bufferNeedsReallocation` set to `true`, @p generationNumber must
     *     match the current generation number of the buffer queue previously
     *     set by setGenerationNumber(). Otherwise, @p generationNumber may not
     *     match the current generation number of the buffer queue.
     */
    virtual ::android::hardware::Return<void> requestBuffer(int32_t slot, requestBuffer_cb _hidl_cb) = 0;

    /**
     * Sets the async flag: whether the producer intends to asynchronously queue
     * buffers without blocking. Typically this is used for triple-buffering
     * and/or when the swap interval is set to zero.
     *
     * Enabling async mode may internally allocate an additional buffer to allow
     * for the asynchronous behavior. If it is not enabled, queue/dequeue calls
     * may block.
     *
     * Changing the async flag may affect the number of available slots. If the
     * adjustment to the number of slots cannot be made, @p status shall be set
     * to `BAD_VALUE`.
     *
     * @param async True if the asynchronous operation is desired; false
     *     otherwise.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> setAsyncMode(bool async) = 0;

    /**
     * Return callback for dequeueBuffer
     */
    using dequeueBuffer_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, int32_t slot, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput& output)>;
    /**
     * Requests a new buffer slot for the client to use. Ownership of the slot
     * is transfered to the client, meaning that the server shall not use the
     * contents of the buffer associated with that slot.
     *
     * On success, @p status shall be `OK`, and @p output shall contain valid
     * information of the call. Otherwise, the contents of @p output are
     * meaningless.
     *
     * The slot index returned in @p slot may or may not contain a buffer
     * (client-side). If the slot is empty, the client must call
     * requestBuffer() to assign a new buffer to that slot.
     *
     * Once the client is done filling this buffer, it is expected to transfer
     * buffer ownership back to the server with either cancelBuffer() on
     * the dequeued slot or to fill in the contents of its associated buffer
     * contents and call queueBuffer().
     *
     * If dequeueBuffer() returns with `output.releaseAllBuffers` set to `true`,
     * the client is expected to release all of the mirrored slot-to-buffer
     * mappings.
     *
     * If dequeueBuffer() returns with `output.bufferNeedsReallocation` set to
     * `true`, the client is expected to call requestBuffer() immediately.
     *
     * The returned `output.fence` shall be updated to hold the fence associated
     * with the buffer. The contents of the buffer must not be overwritten until
     * the fence signals. If the fence is an empty fence, the buffer may be
     * written immediately.
     *
     * This call shall block until a buffer is available to be dequeued. If
     * both the producer and consumer are controlled by the app, then this call
     * can never block and shall return `WOULD_BLOCK` in @p status if no buffer
     * is available.
     *
     * If a dequeue operation shall cause certain conditions on the number of
     * buffers to be violated (such as the maximum number of dequeued buffers),
     * @p status shall be set to `INVALID_OPERATION` to indicate failure.
     *
     * If a dequeue operation cannot be completed within the time period
     * previously set by setDequeueTimeout(), the return @p status shall
     * `TIMED_OUT`.
     *
     * See @ref DequeueBufferInput for more information on the @p input
     * parameter.
     *
     * @param input See #DequeueBufferInput for more information.
     * @return status Status of the call.
     * @return slot Slot index.
     * @return output See #DequeueBufferOutput for more information.
     *
     * @sa queueBuffer(), requestBuffer().
     */
    virtual ::android::hardware::Return<void> dequeueBuffer(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& input, dequeueBuffer_cb _hidl_cb) = 0;

    /**
     * Attempts to remove all ownership of the buffer in the given slot from the
     * buffer queue.
     *
     * If this call succeeds, the slot shall be freed, and there shall be no way
     * to obtain the buffer from this interface. The freed slot shall remain
     * unallocated until either it is selected to hold a freshly allocated
     * buffer in dequeueBuffer() or a buffer is attached to the slot. The buffer
     * must have already been dequeued, and the caller must already possesses
     * the buffer (i.e., must have called requestBuffer()).
     *
     * @param slot Slot index.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> detachBuffer(int32_t slot) = 0;

    /**
     * Return callback for detachNextBuffer
     */
    using detachNextBuffer_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, const ::android::hardware::graphics::common::V1_2::HardwareBuffer& buffer, const ::android::hardware::hidl_handle& fence)>;
    /**
     * Dequeues a buffer slot, requests the buffer associated to the slot, and
     * detaches it from the buffer queue. This is equivalent to calling
     * dequeueBuffer(), requestBuffer(), and detachBuffer() in succession except
     * for two things:
     *   1. It is unnecessary to provide a #DequeueBufferInput object.
     *   2. The call shall not block, since if it cannot find an appropriate
     *      buffer to return, it shall return an error instead.
     *
     * Only slots that are free but still contain a buffer shall be considered,
     * and the oldest of those shall be returned. @p buffer is equivalent to the
     * buffer that would be returned from requestBuffer(), and @p fence is
     * equivalent to the fence that would be returned from dequeueBuffer().
     *
     * @return status Status of the call.
     * @return buffer Buffer just released from the buffer queue.
     * @return fence Fence associated to @p buffer.
     *
     * @sa dequeueBuffer(), requestBuffer(), detachBuffer().
     */
    virtual ::android::hardware::Return<void> detachNextBuffer(detachNextBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for attachBuffer
     */
    using attachBuffer_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, int32_t slot, bool releaseAllBuffers)>;
    /**
     * Attempts to transfer ownership of a buffer to the buffer queue.
     *
     * If this call succeeds, it shall be as if this buffer was dequeued from the
     * returned slot index. As such, this call shall fail if attaching this
     * buffer would cause too many buffers to be simultaneously dequeued.
     *
     * If the returned @p releaseAllBuffers is `true`, the caller is expected to
     * release all of the mirrored slot-to-buffer mappings.
     *
     * See dequeueBuffer() for conditions that may cause the call to fail.
     *
     * @param buffer Buffer to attach to the buffer queue.
     * @param generationNumber Generation number of the buffer. If this does not
     *     match the current generation number of the buffer queue, the call
     *     must fail with @p status set to `BAD_VALUE`.
     * @return status Status of the call.
     * @return slot Slot index assigned to @p buffer.
     * @return releaseAllBuffers Whether the caller is expected to release all
     *     of the mirrored slot-to-buffer mappings.
     *
     * @sa dequeueBuffer().
     */
    virtual ::android::hardware::Return<void> attachBuffer(const ::android::hardware::graphics::common::V1_2::HardwareBuffer& buffer, uint32_t generationNumber, attachBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for queueBuffer
     */
    using queueBuffer_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& output)>;
    /**
     * Indicates that the client has finished filling in the contents of the
     * buffer associated with slot and transfers ownership of that slot back to
     * the buffer queue.
     *
     * @p status may be set to `BAD_VALUE` if any of the following conditions
     * hold:
     *   - The buffer queue is operating in the asynchronous mode, and the
     *     buffer count was smaller than the maximum number of buffers that can
     *     be allocated at once.
     *   - @p slot is an invalid slot index, i.e., the slot is not owned by the
     *     client by previously calling dequeueBuffer(), requestBuffer() or
     *     attachBuffer().
     *   - The crop rectangle is not contained in the buffer.
     *
     * Upon success, the output shall be filled with meaningful values
     * (refer to the documentation of @ref QueueBufferOutput).
     *
     * @param slot Slot index.
     * @param input See @ref QueueBufferInput.
     * @return status Status of the call.
     * @return output See @ref QueueBufferOutput.
     *
     * @sa #QueueBufferInput, #QueueBufferOutput, dequeueBuffer().
     */
    virtual ::android::hardware::Return<void> queueBuffer(int32_t slot, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput& input, queueBuffer_cb _hidl_cb) = 0;

    /**
     * Indicates that the client does not wish to fill in the buffer associated
     * with the slot and transfers ownership of the slot back to the server. The
     * buffer is not queued for use by the consumer.
     *
     * If @p fence is not an empty fence, the buffer shall not be overwritten
     * until the fence signals. @p fence is usually obtained from
     * dequeueBuffer().
     *
     * @param slot Slot index.
     * @param fence Fence for the canceled buffer.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> cancelBuffer(int32_t slot, const ::android::hardware::hidl_handle& fence) = 0;

    /**
     * Return callback for query
     */
    using query_cb = std::function<void(int32_t result, int32_t value)>;
    /**
     * Retrieves information for this surface.
     *
     * @param what What to query. @p what must be one of the values in
     *     +llndk libnativewindow#ANativeWindowQuery.
     * @return status Status of the call.
     * @return value The value queried. The set of possible values depends on
     *     the value of @p what.
     *
     * @sa +llndk libnativewindow#ANativeWindowQuery.
     */
    virtual ::android::hardware::Return<void> query(int32_t what, query_cb _hidl_cb) = 0;

    /**
     * Return callback for connect
     */
    using connect_cb = std::function<void(::android::hardware::graphics::bufferqueue::V2_0::Status status, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& output)>;
    /**
     * Attempts to connect the client as a producer of the buffer queue.
     * This method must be called before any other methods in this interface.
     *
     * If the buffer queue does not have a consumer ready (connected), the
     * return @p status shall be `NO_INIT`.
     *
     * If any of the following conditions hold, the error code `BAD_VALUE` shall
     * be reported in @p status:
     *   - The producer is already connected.
     *   - The number of available slots cannot be adjusted to accommodate the
     *     supplied value of @p producerControlledByApp.
     *
     * @param listener An optional callback object that can be provided if the
     *     client wants to be notified when the consumer releases a buffer back
     *     to the buffer queue.
     * @param api How the client shall write to buffers.
     * @param producerControlledByApp `true` if the producer is hosted by an
     *     untrusted process (typically application-forked processes). If both
     *     the producer and the consumer are controlled by app, the buffer queue
     *     shall operate in the asynchronous mode regardless of the async flag
     *     set by setAsyncMode().
     * @return status Status of the call.
     * @return output See #QueueBufferOutput for more information.
     *
     * @sa #QueueBufferOutput, disconnect(), setAsyncMode().
     */
    virtual ::android::hardware::Return<void> connect(const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IProducerListener>& listener, ::android::hardware::graphics::bufferqueue::V2_0::ConnectionType api, bool producerControlledByApp, connect_cb _hidl_cb) = 0;

    /**
     * Attempts to disconnect the client from the producer end of the buffer
     * queue.
     *
     * Calling this method shall cause any subsequent calls to other
     * @ref IGraphicBufferProducer methods apart from connect() to fail.
     * A successful connect() call afterwards may allow other methods to succeed
     * again.
     *
     * Disconnecting from an abandoned buffer queue is legal and is considered a
     * no-op.
     *
     * @param api The type of connection to disconnect. Supplying the value of
     *     `CURRENTLY_CONNECTED` to @p api has the same effect as supplying the
     *     current connection type. If the producer end is not connected,
     *     supplying `CURRENTLY_CONNECTED` shall result in a successful no-op
     *     call.
     * @return status Status of the call.
     *
     * @sa connect().
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> disconnect(::android::hardware::graphics::bufferqueue::V2_0::ConnectionType api) = 0;

    /**
     * Allocates buffers based on the given dimensions, format and usage.
     *
     * This function shall allocate up to the maximum number of buffers
     * permitted by the current buffer queue configuration. It shall use the
     * given format, dimensions, and usage bits, which are interpreted in the
     * same way as for dequeueBuffer(), and the async flag must be set the same
     * way as for dequeueBuffer() to ensure that the correct number of buffers
     * are allocated. This is most useful to avoid an allocation delay during
     * dequeueBuffer(). If there are already the maximum number of buffers
     * allocated, this function has no effect.
     *
     * A value of 0 in @p width, @p height or @p format indicates that the
     * buffer queue can pick the default value.
     *
     * @param width Width of buffers to allocate.
     * @param height Height of buffers to allocate.
     * @param format Format of buffers to allocate.
     * @param usage Usage of bufferes to allocate.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> allocateBuffers(uint32_t width, uint32_t height, uint32_t format, uint64_t usage) = 0;

    /**
     * Sets whether dequeueBuffer() is allowed to allocate new buffers.
     *
     * Normally dequeueBuffer() does not discriminate between free slots which
     * already have an allocated buffer and those which do not, and shall
     * allocate a new buffer if the slot doesn't have a buffer or if the slot's
     * buffer doesn't match the requested size, format, or usage. This method
     * allows the producer to restrict the eligible slots to those which already
     * have an allocated buffer of the correct size, format, and usage. If no
     * eligible slot is available, dequeueBuffer() shall block or return an
     * error.
     *
     * @param allow Whether to allow new buffers to be allocated in
     *     dequeueBuffer().
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> allowAllocation(bool allow) = 0;

    /**
     * Sets the current generation number of the buffer queue.
     *
     * This generation number shall be inserted into any buffers allocated by the
     * buffer queue, and any attempts to attach a buffer with a different
     * generation number shall fail. Buffers already in the queue are not
     * affected and shall retain their current generation number. The generation
     * number defaults to 0, i.e., buffers allocated before the first call to
     * setGenerationNumber() shall be given 0 as their generation numbers.
     *
     * @param generationNumber New generation number. The client must make sure
     *     that @p generationNumber is different from the previous generation
     *     number if it wants to deprecate old buffers.
     * @return status Status of the call.
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> setGenerationNumber(uint32_t generationNumber) = 0;

    /**
     * Sets how long dequeueBuffer() shall wait for a buffer to become available
     * before returning an error `TIMED_OUT`.
     *
     * This timeout also affects the attachBuffer() call, which shall block if
     * there is not a free slot available into which the attached buffer can be
     * placed.
     *
     * By default, the buffer queue shall wait forever, which is equivalent to
     * setting @p timeoutNs equal to any negative number (such as `-1`). If
     * @p timeoutNs is non-negative, setDequeueTimeout() shall disable
     * non-blocking mode and its corresponding spare buffer (which is used to
     * ensure a buffer is always available).
     *
     * Changing the dequeue timeout may affect the number of buffers. (See
     * setAsyncMode().) If the adjustment to the number of buffers inside the
     * buffer queue is not feasible, @p status shall be set to `BAD_VALUE`.
     *
     * @param timeoutNs Amount of time dequeueBuffer() is allowed to block
     *     before returning `TIMED_OUT`. If @p timeoutNs is negative,
     *     dequeueBuffer() shall not be able to return `TIMED_OUT`. Instead, it
     *     may block forever or return `WOULD_BLOCK`.
     * @return status Status of the call.
     *
     * @sa dequeueBuffer(), setAsyncMode(), query().
     */
    virtual ::android::hardware::Return<::android::hardware::graphics::bufferqueue::V2_0::Status> setDequeueTimeout(int64_t timeoutNs) = 0;

    /**
     * Returns a unique id for this buffer queue.
     *
     * @return id System-wide unique id of the buffer queue.
     */
    virtual ::android::hardware::Return<uint64_t> getUniqueId() = 0;

    /**
     * Return callback for getConsumerName
     */
    using getConsumerName_cb = std::function<void(const ::android::hardware::hidl_string& name)>;
    /**
     * Returns the name of the connected consumer.
     *
     * \note This is used for debugging only.
     *
     * @return name Name of the consumer.
     */
    virtual ::android::hardware::Return<void> getConsumerName(getConsumerName_cb _hidl_cb) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>> castFrom(const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<IGraphicBufferProducer> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGraphicBufferProducer> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGraphicBufferProducer> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGraphicBufferProducer> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGraphicBufferProducer> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGraphicBufferProducer> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGraphicBufferProducer> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGraphicBufferProducer> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& o);
static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& rhs);
static inline bool operator!=(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& rhs);

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput& o);
static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput& o, ::std::ostream*);
// operator== and operator!= are not generated for DequeueBufferOutput

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput& o);
static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput& o, ::std::ostream*);
// operator== and operator!= are not generated for QueueBufferInput

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& o);
static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& rhs);
static inline bool operator!=(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .format = ";
    os += ::android::hardware::toString(o.format);
    os += ", .usage = ";
    os += ::android::hardware::toString(o.usage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.format != rhs.format) {
        return false;
    }
    if (lhs.usage != rhs.usage) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferInput& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bufferAge = ";
    os += ::android::hardware::toString(o.bufferAge);
    os += ", .bufferNeedsReallocation = ";
    os += ::android::hardware::toString(o.bufferNeedsReallocation);
    os += ", .releaseAllBuffers = ";
    os += ::android::hardware::toString(o.releaseAllBuffers);
    os += ", .fence = ";
    os += ::android::hardware::toString(o.fence);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::DequeueBufferOutput& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for DequeueBufferOutput

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .isAutoTimestamp = ";
    os += ::android::hardware::toString(o.isAutoTimestamp);
    os += ", .dataSpace = ";
    os += ::android::hardware::toString(o.dataSpace);
    os += ", .crop = ";
    os += ::android::hardware::toString(o.crop);
    os += ", .transform = ";
    os += ::android::hardware::toString(o.transform);
    os += ", .stickyTransform = ";
    os += ::android::hardware::toString(o.stickyTransform);
    os += ", .fence = ";
    os += ::android::hardware::toString(o.fence);
    os += ", .surfaceDamage = ";
    os += ::android::hardware::toString(o.surfaceDamage);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferInput& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for QueueBufferInput

static inline std::string toString(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".width = ";
    os += ::android::hardware::toString(o.width);
    os += ", .height = ";
    os += ::android::hardware::toString(o.height);
    os += ", .transformHint = ";
    os += ::android::hardware::toString(o.transformHint);
    os += ", .numPendingBuffers = ";
    os += ::android::hardware::toString(o.numPendingBuffers);
    os += ", .nextFrameNumber = ";
    os += ::android::hardware::toString(o.nextFrameNumber);
    os += ", .bufferReplaced = ";
    os += ::android::hardware::toString(o.bufferReplaced);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& rhs) {
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.transformHint != rhs.transformHint) {
        return false;
    }
    if (lhs.numPendingBuffers != rhs.numPendingBuffers) {
        return false;
    }
    if (lhs.nextFrameNumber != rhs.nextFrameNumber) {
        return false;
    }
    if (lhs.bufferReplaced != rhs.bufferReplaced) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& lhs, const ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::QueueBufferOutput& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::graphics::bufferqueue::V2_0::IGraphicBufferProducer::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace bufferqueue
}  // namespace graphics
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GRAPHICS_BUFFERQUEUE_V2_0_IGRAPHICBUFFERPRODUCER_H
