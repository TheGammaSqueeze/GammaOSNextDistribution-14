#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_ISTREAM_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_ISTREAM_H

#include <android/hardware/audio/common/5.0/types.h>
#include <android/hardware/audio/5.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace V5_0 {

struct IStream : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio@5.0::IStream"
     */
    static const char* descriptor;

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
    using getSupportedSampleRates_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::hidl_vec<uint32_t>& sampleRates)>;
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
    virtual ::android::hardware::Return<void> getSupportedSampleRates(::android::hardware::audio::common::V5_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb) = 0;

    /**
     * Sets the sampling rate of the stream. Calling this method is equivalent
     * to setting AUDIO_PARAMETER_STREAM_SAMPLING_RATE on the legacy HAL.
     * Optional method. If implemented, only called on a stopped stream.
     *
     * @param sampleRateHz sample rate in Hz.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setSampleRate(uint32_t sampleRateHz) = 0;

    /**
     * Return the channel mask of the stream.
     *
     * @return mask channel mask.
     */
    virtual ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask>> getChannelMask() = 0;

    /**
     * Return callback for getSupportedChannelMasks
     */
    using getSupportedChannelMasks_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask>>& masks)>;
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
    virtual ::android::hardware::Return<void> getSupportedChannelMasks(::android::hardware::audio::common::V5_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) = 0;

    /**
     * Sets the channel mask of the stream. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_STREAM_CHANNELS on the legacy HAL.
     * Optional method
     *
     * @param format audio format.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setChannelMask(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask> mask) = 0;

    /**
     * Return the audio format of the stream.
     *
     * @return format audio format.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::common::V5_0::AudioFormat> getFormat() = 0;

    /**
     * Return callback for getSupportedFormats
     */
    using getSupportedFormats_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V5_0::AudioFormat>& formats)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setFormat(::android::hardware::audio::common::V5_0::AudioFormat format) = 0;

    /**
     * Return callback for getAudioProperties
     */
    using getAudioProperties_cb = std::function<void(uint32_t sampleRateHz, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask> mask, ::android::hardware::audio::common::V5_0::AudioFormat format)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> addEffect(uint64_t effectId) = 0;

    /**
     * Stops application of the effect to the stream.
     *
     * @param effectId effect ID (obtained from IEffectsFactory.createEffect) of
     *                 the effect to remove.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> removeEffect(uint64_t effectId) = 0;

    /**
     * Put the audio hardware input/output into standby mode.
     * Driver must exit from standby mode at the next I/O operation.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> standby() = 0;

    /**
     * Return callback for getDevices
     */
    using getDevices_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V5_0::DeviceAddress>& devices)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setDevices(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V5_0::DeviceAddress>& devices) = 0;

    /**
     * Sets the HW synchronization source. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_STREAM_HW_AV_SYNC on the legacy HAL.
     * Optional method
     *
     * @param hwAvSync HW synchronization source
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setHwAvSync(uint32_t hwAvSync) = 0;

    /**
     * Return callback for getParameters
     */
    using getParameters_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& parameters)>;
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
    virtual ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) = 0;

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
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& parameters) = 0;

    /**
     * Called by the framework to start a stream operating in mmap mode.
     * createMmapBuffer() must be called before calling start().
     * Function only implemented by streams operating in mmap mode.
     *
     * @return retval OK in case the success.
     *                NOT_SUPPORTED on non mmap mode streams
     *                INVALID_STATE if called out of sequence
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> start() = 0;

    /**
     * Called by the framework to stop a stream operating in mmap mode.
     * Function only implemented by streams operating in mmap mode.
     *
     * @return retval OK in case the succes.
     *                NOT_SUPPORTED on non mmap mode streams
     *                INVALID_STATE if called out of sequence
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> stop() = 0;

    /**
     * Return callback for createMmapBuffer
     */
    using createMmapBuffer_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::audio::V5_0::MmapBufferInfo& info)>;
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
    using getMmapPosition_cb = std::function<void(::android::hardware::audio::V5_0::Result retval, const ::android::hardware::audio::V5_0::MmapPosition& position)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V5_0::Result> close() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V5_0::IStream>> castFrom(const ::android::sp<::android::hardware::audio::V5_0::IStream>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V5_0::IStream>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IStream> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStream> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStream> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStream> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IStream> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStream> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStream> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStream> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::audio::V5_0::IStream>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::audio::V5_0::IStream>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::V5_0::IStream::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V5_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_ISTREAM_H
