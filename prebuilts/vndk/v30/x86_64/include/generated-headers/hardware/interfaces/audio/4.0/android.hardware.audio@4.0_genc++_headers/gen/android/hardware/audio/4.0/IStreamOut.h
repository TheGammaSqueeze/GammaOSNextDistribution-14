#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_ISTREAMOUT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_ISTREAMOUT_H

#include <android/hardware/audio/common/4.0/types.h>
#include <android/hardware/audio/4.0/IStream.h>
#include <android/hardware/audio/4.0/IStreamOutCallback.h>
#include <android/hardware/audio/4.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace V4_0 {

struct IStreamOut : public ::android::hardware::audio::V4_0::IStream {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio@4.0::IStreamOut"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class WriteCommand : int32_t;
    struct WriteStatus;

    /**
     * Commands that can be executed on the driver writer thread.
     */
    enum class WriteCommand : int32_t {
        WRITE = 0,
        GET_PRESENTATION_POSITION = 1 /* ::android::hardware::audio::V4_0::IStreamOut::WriteCommand.WRITE implicitly + 1 */,
        GET_LATENCY = 2 /* ::android::hardware::audio::V4_0::IStreamOut::WriteCommand.GET_PRESENTATION_POSITION implicitly + 1 */,
    };

    /**
     * Data structure passed back to the client via status message queue
     * of 'write' operation.
     *
     * Possible values of 'retval' field:
     *  - OK, write operation was successful;
     *  - INVALID_ARGUMENTS, stream was not configured properly;
     *  - INVALID_STATE, stream is in a state that doesn't allow writes;
     *  - INVALID_OPERATION, retrieving presentation position isn't supported.
     */
    struct WriteStatus final {
        // Forward declaration for forward reference support:
        union Reply;

        union Reply final {
            // Forward declaration for forward reference support:
            struct PresentationPosition;

            struct PresentationPosition final {
                uint64_t frames __attribute__ ((aligned(8)));
                ::android::hardware::audio::V4_0::TimeSpec timeStamp __attribute__ ((aligned(8)));
            };

            static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition, frames) == 0, "wrong offset");
            static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition, timeStamp) == 8, "wrong offset");
            static_assert(sizeof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition) == 24, "wrong size");
            static_assert(__alignof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition) == 8, "wrong alignment");

            uint64_t written __attribute__ ((aligned(8)));
            ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition presentationPosition __attribute__ ((aligned(8)));
            uint32_t latencyMs __attribute__ ((aligned(4)));
        };

        static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply, written) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply, presentationPosition) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply, latencyMs) == 0, "wrong offset");
        static_assert(sizeof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply) == 24, "wrong size");
        static_assert(__alignof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply) == 8, "wrong alignment");

        ::android::hardware::audio::V4_0::Result retval __attribute__ ((aligned(4)));
        ::android::hardware::audio::V4_0::IStreamOut::WriteCommand replyTo __attribute__ ((aligned(4)));
        ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply reply __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus, retval) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus, replyTo) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus, reply) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::audio::V4_0::IStreamOut::WriteStatus) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return the frame size (number of bytes per sample).
     *
     * @return frameSize frame size in bytes.
     */
    virtual ::android::hardware::Return<uint64_t> getFrameSize() = 0;

    /**
     * Return the frame count of the buffer. Calling this method is equivalent
     * to getting AUDIO_PARAMETER_STREAM_FRAME_COUNT on the legacy HAL.
     *
     * @return count frame count.
     */
    virtual ::android::hardware::Return<uint64_t> getFrameCount() = 0;

    /**
     * Return the size of input/output buffer in bytes for this stream.
     * It must be a multiple of the frame size.
     *
     * @return buffer buffer size in bytes.
     */
    virtual ::android::hardware::Return<uint64_t> getBufferSize() = 0;

    /**
     * Return the sampling rate in Hz.
     *
     * @return sampleRateHz sample rate in Hz.
     */
    virtual ::android::hardware::Return<uint32_t> getSampleRate() = 0;

    /**
     * Return callback for getSupportedSampleRates
     */
    using getSupportedSampleRates_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::hidl_vec<uint32_t>& sampleRates)>;
    /**
     * Return supported native sampling rates of the stream for a given format.
     * A supported native sample rate is a sample rate that can be efficiently
     * played by the hardware (typically without sample-rate conversions).
     *
     * This function is only called for dynamic profile. If called for
     * non-dynamic profile is should return NOT_SUPPORTED or the same list
     * as in audio_policy_configuration.xml.
     *
     * Calling this method is equivalent to getting
     * AUDIO_PARAMETER_STREAM_SUP_SAMPLING_RATES on the legacy HAL.
     *
     *
     * @param format audio format for which the sample rates are supported.
     * @return retval operation completion status.
     *                Must be OK if the format is supported.
     * @return sampleRateHz supported sample rates.
     */
    virtual ::android::hardware::Return<void> getSupportedSampleRates(::android::hardware::audio::common::V4_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb) = 0;

    /**
     * Sets the sampling rate of the stream. Calling this method is equivalent
     * to setting AUDIO_PARAMETER_STREAM_SAMPLING_RATE on the legacy HAL.
     * Optional method. If implemented, only called on a stopped stream.
     *
     * @param sampleRateHz sample rate in Hz.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setSampleRate(uint32_t sampleRateHz) = 0;

    /**
     * Return the channel mask of the stream.
     *
     * @return mask channel mask.
     */
    virtual ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask>> getChannelMask() = 0;

    /**
     * Return callback for getSupportedChannelMasks
     */
    using getSupportedChannelMasks_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask>>& masks)>;
    /**
     * Return supported channel masks of the stream. Calling this method is
     * equivalent to getting AUDIO_PARAMETER_STREAM_SUP_CHANNELS on the legacy
     * HAL.
     *
     * @param format audio format for which the channel masks are supported.
     * @return retval operation completion status.
     *                Must be OK if the format is supported.
     * @return masks supported audio masks.
     */
    virtual ::android::hardware::Return<void> getSupportedChannelMasks(::android::hardware::audio::common::V4_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) = 0;

    /**
     * Sets the channel mask of the stream. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_STREAM_CHANNELS on the legacy HAL.
     * Optional method
     *
     * @param format audio format.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setChannelMask(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask> mask) = 0;

    /**
     * Return the audio format of the stream.
     *
     * @return format audio format.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::common::V4_0::AudioFormat> getFormat() = 0;

    /**
     * Return callback for getSupportedFormats
     */
    using getSupportedFormats_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V4_0::AudioFormat>& formats)>;
    /**
     * Return supported audio formats of the stream. Calling this method is
     * equivalent to getting AUDIO_PARAMETER_STREAM_SUP_FORMATS on the legacy
     * HAL.
     *
     * @return formats supported audio formats.
     */
    virtual ::android::hardware::Return<void> getSupportedFormats(getSupportedFormats_cb _hidl_cb) = 0;

    /**
     * Sets the audio format of the stream. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_STREAM_FORMAT on the legacy HAL.
     * Optional method
     *
     * @param format audio format.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setFormat(::android::hardware::audio::common::V4_0::AudioFormat format) = 0;

    /**
     * Return callback for getAudioProperties
     */
    using getAudioProperties_cb = std::function<void(uint32_t sampleRateHz, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask> mask, ::android::hardware::audio::common::V4_0::AudioFormat format)>;
    /**
     * Convenience method for retrieving several stream parameters in
     * one transaction.
     *
     * @return sampleRateHz sample rate in Hz.
     * @return mask channel mask.
     * @return format audio format.
     */
    virtual ::android::hardware::Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb) = 0;

    /**
     * Applies audio effect to the stream.
     *
     * @param effectId effect ID (obtained from IEffectsFactory.createEffect) of
     *                 the effect to apply.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> addEffect(uint64_t effectId) = 0;

    /**
     * Stops application of the effect to the stream.
     *
     * @param effectId effect ID (obtained from IEffectsFactory.createEffect) of
     *                 the effect to remove.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> removeEffect(uint64_t effectId) = 0;

    /**
     * Put the audio hardware input/output into standby mode.
     * Driver must exit from standby mode at the next I/O operation.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> standby() = 0;

    /**
     * Return callback for getDevices
     */
    using getDevices_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::DeviceAddress>& devices)>;
    /**
     * Return the set of devices which this stream is connected to.
     * Optional method
     *
     * @return retval operation completion status: OK or NOT_SUPPORTED.
     * @return device set of devices which this stream is connected to.
     */
    virtual ::android::hardware::Return<void> getDevices(getDevices_cb _hidl_cb) = 0;

    /**
     * Connects the stream to one or multiple devices.
     *
     * This method must only be used for HALs that do not support
     * 'IDevice.createAudioPatch' method. Calling this method is
     * equivalent to setting AUDIO_PARAMETER_STREAM_ROUTING preceeded
     * with a device address in the legacy HAL interface.
     *
     * @param address device to connect the stream to.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setDevices(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::DeviceAddress>& devices) = 0;

    /**
     * Sets the HW synchronization source. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_STREAM_HW_AV_SYNC on the legacy HAL.
     * Optional method
     *
     * @param hwAvSync HW synchronization source
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setHwAvSync(uint32_t hwAvSync) = 0;

    /**
     * Return callback for getParameters
     */
    using getParameters_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& parameters)>;
    /**
     * Generic method for retrieving vendor-specific parameter values.
     * The framework does not interpret the parameters, they are passed
     * in an opaque manner between a vendor application and HAL.
     *
     * Multiple parameters can be retrieved at the same time.
     * The implementation should return as many requested parameters
     * as possible, even if one or more is not supported
     *
     * @param context provides more information about the request
     * @param keys keys of the requested parameters
     * @return retval operation completion status.
     *         OK must be returned if keys is empty.
     *         NOT_SUPPORTED must be returned if at least one key is unknown.
     * @return parameters parameter key value pairs.
     *         Must contain the value of all requested keys if retval == OK
     */
    virtual ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) = 0;

    /**
     * Generic method for setting vendor-specific parameter values.
     * The framework does not interpret the parameters, they are passed
     * in an opaque manner between a vendor application and HAL.
     *
     * Multiple parameters can be set at the same time though this is
     * discouraged as it make failure analysis harder.
     *
     * If possible, a failed setParameters should not impact the platform state.
     *
     * @param context provides more information about the request
     * @param parameters parameter key value pairs.
     * @return retval operation completion status.
     *         All parameters must be successfully set for OK to be returned
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& parameters) = 0;

    /**
     * Called by the framework to start a stream operating in mmap mode.
     * createMmapBuffer() must be called before calling start().
     * Function only implemented by streams operating in mmap mode.
     *
     * @return retval OK in case the success.
     *                NOT_SUPPORTED on non mmap mode streams
     *                INVALID_STATE if called out of sequence
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> start() = 0;

    /**
     * Called by the framework to stop a stream operating in mmap mode.
     * Function only implemented by streams operating in mmap mode.
     *
     * @return retval OK in case the succes.
     *                NOT_SUPPORTED on non mmap mode streams
     *                INVALID_STATE if called out of sequence
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> stop() = 0;

    /**
     * Return callback for createMmapBuffer
     */
    using createMmapBuffer_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::audio::V4_0::MmapBufferInfo& info)>;
    /**
     * Called by the framework to retrieve information on the mmap buffer used for audio
     * samples transfer.
     * Function only implemented by streams operating in mmap mode.
     *
     * @param minSizeFrames minimum buffer size requested. The actual buffer
     *                     size returned in struct MmapBufferInfo can be larger.
     * @return retval OK in case the success.
     *                NOT_SUPPORTED on non mmap mode streams
     *                NOT_INITIALIZED in case of memory allocation error
     *                INVALID_ARGUMENTS if the requested buffer size is too large
     *                INVALID_STATE if called out of sequence
     * @return info    a MmapBufferInfo struct containing information on the MMMAP buffer created.
     */
    virtual ::android::hardware::Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for getMmapPosition
     */
    using getMmapPosition_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::audio::V4_0::MmapPosition& position)>;
    /**
     * Called by the framework to read current read/write position in the mmap buffer
     * with associated time stamp.
     * Function only implemented by streams operating in mmap mode.
     *
     * @return retval OK in case the success.
     *                NOT_SUPPORTED on non mmap mode streams
     *                INVALID_STATE if called out of sequence
     * @return position a MmapPosition struct containing current HW read/write position in frames
     *                  with associated time stamp.
     */
    virtual ::android::hardware::Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) = 0;

    /**
     * Called by the framework to deinitialize the stream and free up
     * all the currently allocated resources. It is recommended to close
     * the stream on the client side as soon as it is becomes unused.
     *
     * @return retval OK in case the success.
     *                NOT_SUPPORTED if called on IStream instead of input or
     *                              output stream interface.
     *                INVALID_STATE if the stream was already closed.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> close() = 0;

    /**
     * Return the audio hardware driver estimated latency in milliseconds.
     *
     * @return latencyMs latency in milliseconds.
     */
    virtual ::android::hardware::Return<uint32_t> getLatency() = 0;

    /**
     * This method is used in situations where audio mixing is done in the
     * hardware. This method serves as a direct interface with hardware,
     * allowing to directly set the volume as apposed to via the framework.
     * This method might produce multiple PCM outputs or hardware accelerated
     * codecs, such as MP3 or AAC.
     * Optional method
     *
     * @param left left channel attenuation, 1.0f is unity, 0.0f is zero.
     * @param right right channel attenuation, 1.0f is unity, 0.0f is zero.
     * @return retval operation completion status.
     *        If a volume is outside [0,1], return INVALID_ARGUMENTS
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setVolume(float left, float right) = 0;

    /**
     * Called when the metadata of the stream's source has been changed.
     * @param sourceMetadata Description of the audio that is played by the clients.
     */
    virtual ::android::hardware::Return<void> updateSourceMetadata(const ::android::hardware::audio::V4_0::SourceMetadata& sourceMetadata) = 0;

    /**
     * Return callback for prepareForWriting
     */
    using prepareForWriting_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::MQDescriptorSync<::android::hardware::audio::V4_0::IStreamOut::WriteCommand>& commandMQ, const ::android::hardware::MQDescriptorSync<uint8_t>& dataMQ, const ::android::hardware::MQDescriptorSync<::android::hardware::audio::V4_0::IStreamOut::WriteStatus>& statusMQ, const ::android::hardware::audio::common::V4_0::ThreadInfo& threadInfo)>;
    /**
     * Set up required transports for passing audio buffers to the driver.
     *
     * The transport consists of three message queues:
     *  -- command queue is used to instruct the writer thread what operation
     *     to perform;
     *  -- data queue is used for passing audio data from the client
     *     to the driver;
     *  -- status queue is used for reporting operation status
     *     (e.g. amount of bytes actually written or error code).
     *
     * The driver operates on a dedicated thread. The client must ensure that
     * the thread is given an appropriate priority and assigned to correct
     * scheduler and cgroup. For this purpose, the method returns identifiers
     * of the driver thread.
     *
     * @param frameSize the size of a single frame, in bytes.
     * @param framesCount the number of frames in a buffer.
     * @return retval OK if both message queues were created successfully.
     *                INVALID_STATE if the method was already called.
     *                INVALID_ARGUMENTS if there was a problem setting up
     *                                  the queues.
     * @return commandMQ a message queue used for passing commands.
     * @return dataMQ a message queue used for passing audio data in the format
     *                specified at the stream opening.
     * @return statusMQ a message queue used for passing status from the driver
     *                  using WriteStatus structures.
     * @return threadInfo identifiers of the driver's dedicated thread.
     */
    virtual ::android::hardware::Return<void> prepareForWriting(uint32_t frameSize, uint32_t framesCount, prepareForWriting_cb _hidl_cb) = 0;

    /**
     * Return callback for getRenderPosition
     */
    using getRenderPosition_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, uint32_t dspFrames)>;
    /**
     * Return the number of audio frames written by the audio DSP to DAC since
     * the output has exited standby.
     * Optional method
     *
     * @return retval operation completion status.
     * @return dspFrames number of audio frames written.
     */
    virtual ::android::hardware::Return<void> getRenderPosition(getRenderPosition_cb _hidl_cb) = 0;

    /**
     * Return callback for getNextWriteTimestamp
     */
    using getNextWriteTimestamp_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, int64_t timestampUs)>;
    /**
     * Get the local time at which the next write to the audio driver will be
     * presented. The units are microseconds, where the epoch is decided by the
     * local audio HAL.
     * Optional method
     *
     * @return retval operation completion status.
     * @return timestampUs time of the next write.
     */
    virtual ::android::hardware::Return<void> getNextWriteTimestamp(getNextWriteTimestamp_cb _hidl_cb) = 0;

    /**
     * Set the callback interface for notifying completion of non-blocking
     * write and drain.
     *
     * Calling this function implies that all future 'write' and 'drain'
     * must be non-blocking and use the callback to signal completion.
     *
     * 'clearCallback' method needs to be called in order to release the local
     * callback proxy on the server side and thus dereference the callback
     * implementation on the client side.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setCallback(const ::android::sp<::android::hardware::audio::V4_0::IStreamOutCallback>& callback) = 0;

    /**
     * Clears the callback previously set via 'setCallback' method.
     *
     * Warning: failure to call this method results in callback implementation
     * on the client side being held until the HAL server termination.
     *
     * If no callback was previously set, the method should be a no-op
     * and return OK.
     *
     * @return retval operation completion status: OK or NOT_SUPPORTED.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> clearCallback() = 0;

    /**
     * Return callback for supportsPauseAndResume
     */
    using supportsPauseAndResume_cb = std::function<void(bool supportsPause, bool supportsResume)>;
    /**
     * Returns whether HAL supports pausing and resuming of streams.
     *
     * @return supportsPause true if pausing is supported.
     * @return supportsResume true if resume is supported.
     */
    virtual ::android::hardware::Return<void> supportsPauseAndResume(supportsPauseAndResume_cb _hidl_cb) = 0;

    /**
     * Notifies to the audio driver to stop playback however the queued buffers
     * are retained by the hardware. Useful for implementing pause/resume. Empty
     * implementation if not supported however must be implemented for hardware
     * with non-trivial latency. In the pause state, some audio hardware may
     * still be using power. Client code may consider calling 'suspend' after a
     * timeout to prevent that excess power usage.
     *
     * Implementation of this function is mandatory for offloaded playback.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> pause() = 0;

    /**
     * Notifies to the audio driver to resume playback following a pause.
     * Returns error INVALID_STATE if called without matching pause.
     *
     * Implementation of this function is mandatory for offloaded playback.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> resume() = 0;

    /**
     * Returns whether HAL supports draining of streams.
     *
     * @return supports true if draining is supported.
     */
    virtual ::android::hardware::Return<bool> supportsDrain() = 0;

    /**
     * Requests notification when data buffered by the driver/hardware has been
     * played. If 'setCallback' has previously been called to enable
     * non-blocking mode, then 'drain' must not block, instead it must return
     * quickly and completion of the drain is notified through the callback. If
     * 'setCallback' has not been called, then 'drain' must block until
     * completion.
     *
     * If 'type' is 'ALL', the drain completes when all previously written data
     * has been played.
     *
     * If 'type' is 'EARLY_NOTIFY', the drain completes shortly before all data
     * for the current track has played to allow time for the framework to
     * perform a gapless track switch.
     *
     * Drain must return immediately on 'stop' and 'flush' calls.
     *
     * Implementation of this function is mandatory for offloaded playback.
     *
     * @param type type of drain.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> drain(::android::hardware::audio::V4_0::AudioDrain type) = 0;

    /**
     * Notifies to the audio driver to flush the queued data. Stream must
     * already be paused before calling 'flush'.
     * Optional method
     *
     * Implementation of this function is mandatory for offloaded playback.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> flush() = 0;

    /**
     * Return callback for getPresentationPosition
     */
    using getPresentationPosition_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, uint64_t frames, const ::android::hardware::audio::V4_0::TimeSpec& timeStamp)>;
    /**
     * Return a recent count of the number of audio frames presented to an
     * external observer. This excludes frames which have been written but are
     * still in the pipeline. The count is not reset to zero when output enters
     * standby. Also returns the value of CLOCK_MONOTONIC as of this
     * presentation count. The returned count is expected to be 'recent', but
     * does not need to be the most recent possible value. However, the
     * associated time must correspond to whatever count is returned.
     *
     * Example: assume that N+M frames have been presented, where M is a 'small'
     * number. Then it is permissible to return N instead of N+M, and the
     * timestamp must correspond to N rather than N+M. The terms 'recent' and
     * 'small' are not defined. They reflect the quality of the implementation.
     *
     * Optional method
     *
     * @return retval operation completion status.
     * @return frames count of presented audio frames.
     * @return timeStamp associated clock time.
     */
    virtual ::android::hardware::Return<void> getPresentationPosition(getPresentationPosition_cb _hidl_cb) = 0;

    /**
     * Selects a presentation for decoding from a next generation media stream
     * (as defined per ETSI TS 103 190-2) and a program within the presentation.
     * Optional method
     *
     * @param presentationId selected audio presentation.
     * @param programId refinement for the presentation.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> selectPresentation(int32_t presentationId, int32_t programId) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IStreamOut>> castFrom(const ::android::sp<::android::hardware::audio::V4_0::IStreamOut>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IStreamOut>> castFrom(const ::android::sp<::android::hardware::audio::V4_0::IStream>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IStreamOut>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IStreamOut> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStreamOut> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStreamOut> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStreamOut> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IStreamOut> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStreamOut> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStreamOut> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStreamOut> getService(bool getStub) { return getService("default", getStub); }
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

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::IStreamOut::WriteCommand o);
static inline void PrintTo(::android::hardware::audio::V4_0::IStreamOut::WriteCommand o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V4_0::IStreamOut::WriteCommand e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& rhs);
static inline bool operator!=(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& rhs);

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply& o, ::std::ostream*);
// operator== and operator!= are not generated for Reply

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus& o);
static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus& o, ::std::ostream*);
// operator== and operator!= are not generated for WriteStatus

static inline std::string toString(const ::android::sp<::android::hardware::audio::V4_0::IStreamOut>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::V4_0::IStreamOut::WriteCommand>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::IStreamOut::WriteCommand> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::WRITE) == static_cast<int32_t>(::android::hardware::audio::V4_0::IStreamOut::WriteCommand::WRITE)) {
        os += (first ? "" : " | ");
        os += "WRITE";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::WRITE;
    }
    if ((o & ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_PRESENTATION_POSITION) == static_cast<int32_t>(::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_PRESENTATION_POSITION)) {
        os += (first ? "" : " | ");
        os += "GET_PRESENTATION_POSITION";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_PRESENTATION_POSITION;
    }
    if ((o & ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_LATENCY) == static_cast<int32_t>(::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_LATENCY)) {
        os += (first ? "" : " | ");
        os += "GET_LATENCY";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_LATENCY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::IStreamOut::WriteCommand o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::WRITE) {
        return "WRITE";
    }
    if (o == ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_PRESENTATION_POSITION) {
        return "GET_PRESENTATION_POSITION";
    }
    if (o == ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_LATENCY) {
        return "GET_LATENCY";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::IStreamOut::WriteCommand o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".frames = ";
    os += ::android::hardware::toString(o.frames);
    os += ", .timeStamp = ";
    os += ::android::hardware::audio::V4_0::toString(o.timeStamp);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& rhs) {
    if (lhs.frames != rhs.frames) {
        return false;
    }
    if (lhs.timeStamp != rhs.timeStamp) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& lhs, const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply::PresentationPosition& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".written = ";
    os += ::android::hardware::toString(o.written);
    os += ", .presentationPosition = ";
    os += ::android::hardware::audio::V4_0::toString(o.presentationPosition);
    os += ", .latencyMs = ";
    os += ::android::hardware::toString(o.latencyMs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus::Reply& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Reply

static inline std::string toString(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".retval = ";
    os += ::android::hardware::audio::V4_0::toString(o.retval);
    os += ", .replyTo = ";
    os += ::android::hardware::audio::V4_0::toString(o.replyTo);
    os += ", .reply = ";
    os += ::android::hardware::audio::V4_0::toString(o.reply);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::V4_0::IStreamOut::WriteStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for WriteStatus

static inline std::string toString(const ::android::sp<::android::hardware::audio::V4_0::IStreamOut>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::V4_0::IStreamOut::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V4_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::audio::V4_0::IStreamOut::WriteCommand, 3> hidl_enum_values<::android::hardware::audio::V4_0::IStreamOut::WriteCommand> = {
    ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::WRITE,
    ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_PRESENTATION_POSITION,
    ::android::hardware::audio::V4_0::IStreamOut::WriteCommand::GET_LATENCY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_ISTREAMOUT_H
