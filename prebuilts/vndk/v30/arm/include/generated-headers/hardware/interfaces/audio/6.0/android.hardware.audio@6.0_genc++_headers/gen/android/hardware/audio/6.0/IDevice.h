#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_IDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_IDEVICE_H

#include <android/hardware/audio/common/6.0/types.h>
#include <android/hardware/audio/6.0/IStreamIn.h>
#include <android/hardware/audio/6.0/IStreamOut.h>
#include <android/hardware/audio/6.0/types.h>
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
namespace V6_0 {

struct IDevice : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio@6.0::IDevice"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Returns whether the audio hardware interface has been initialized.
     *
     * @return retval OK on success, NOT_INITIALIZED on failure.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> initCheck() = 0;

    /**
     * Sets the audio volume for all audio activities other than voice call. If
     * NOT_SUPPORTED is returned, the software mixer will emulate this
     * capability.
     *
     * @param volume 1.0f means unity, 0.0f is zero.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setMasterVolume(float volume) = 0;

    /**
     * Return callback for getMasterVolume
     */
    using getMasterVolume_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, float volume)>;
    /**
     * Get the current master volume value for the HAL, if the HAL supports
     * master volume control. For example, AudioFlinger will query this value
     * from the primary audio HAL when the service starts and use the value for
     * setting the initial master volume across all HALs. HALs which do not
     * support this method must return NOT_SUPPORTED in 'retval'.
     *
     * @return retval operation completion status.
     * @return volume 1.0f means unity, 0.0f is zero.
     */
    virtual ::android::hardware::Return<void> getMasterVolume(getMasterVolume_cb _hidl_cb) = 0;

    /**
     * Sets microphone muting state.
     *
     * @param mute whether microphone is muted.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setMicMute(bool mute) = 0;

    /**
     * Return callback for getMicMute
     */
    using getMicMute_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, bool mute)>;
    /**
     * Gets whether microphone is muted.
     *
     * @return retval operation completion status.
     * @return mute whether microphone is muted.
     */
    virtual ::android::hardware::Return<void> getMicMute(getMicMute_cb _hidl_cb) = 0;

    /**
     * Set the audio mute status for all audio activities. If the return value
     * is NOT_SUPPORTED, the software mixer will emulate this capability.
     *
     * @param mute whether audio is muted.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setMasterMute(bool mute) = 0;

    /**
     * Return callback for getMasterMute
     */
    using getMasterMute_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, bool mute)>;
    /**
     * Get the current master mute status for the HAL, if the HAL supports
     * master mute control. AudioFlinger will query this value from the primary
     * audio HAL when the service starts and use the value for setting the
     * initial master mute across all HALs. HAL must indicate that the feature
     * is not supported by returning NOT_SUPPORTED status.
     *
     * @return retval operation completion status.
     * @return mute whether audio is muted.
     */
    virtual ::android::hardware::Return<void> getMasterMute(getMasterMute_cb _hidl_cb) = 0;

    /**
     * Return callback for getInputBufferSize
     */
    using getInputBufferSize_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, uint64_t bufferSize)>;
    /**
     * Returns audio input buffer size according to parameters passed or
     * INVALID_ARGUMENTS if one of the parameters is not supported.
     *
     * @param config audio configuration.
     * @return retval operation completion status.
     * @return bufferSize input buffer size in bytes.
     */
    virtual ::android::hardware::Return<void> getInputBufferSize(const ::android::hardware::audio::common::V6_0::AudioConfig& config, getInputBufferSize_cb _hidl_cb) = 0;

    /**
     * Return callback for openOutputStream
     */
    using openOutputStream_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, const ::android::sp<::android::hardware::audio::V6_0::IStreamOut>& outStream, const ::android::hardware::audio::common::V6_0::AudioConfig& suggestedConfig)>;
    /**
     * This method creates and opens the audio hardware output stream.
     * If the stream can not be opened with the proposed audio config,
     * HAL must provide suggested values for the audio config.
     *
     * @param ioHandle handle assigned by AudioFlinger.
     * @param device device type and (if needed) address.
     * @param config stream configuration.
     * @param flags additional flags.
     * @param sourceMetadata Description of the audio that will be played.
     * May be used by implementations to configure hardware effects.
     * @return retval operation completion status.
     * @return outStream created output stream.
     * @return suggestedConfig in case of invalid parameters, suggested config.
     */
    virtual ::android::hardware::Return<void> openOutputStream(int32_t ioHandle, const ::android::hardware::audio::common::V6_0::DeviceAddress& device, const ::android::hardware::audio::common::V6_0::AudioConfig& config, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioOutputFlag> flags, const ::android::hardware::audio::common::V6_0::SourceMetadata& sourceMetadata, openOutputStream_cb _hidl_cb) = 0;

    /**
     * Return callback for openInputStream
     */
    using openInputStream_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, const ::android::sp<::android::hardware::audio::V6_0::IStreamIn>& inStream, const ::android::hardware::audio::common::V6_0::AudioConfig& suggestedConfig)>;
    /**
     * This method creates and opens the audio hardware input stream.
     * If the stream can not be opened with the proposed audio config,
     * HAL must provide suggested values for the audio config.
     *
     * @param ioHandle handle assigned by AudioFlinger.
     * @param device device type and (if needed) address.
     * @param config stream configuration.
     * @param flags additional flags.
     * @param sinkMetadata Description of the audio that is suggested by the client.
     *                     May be used by implementations to configure processing effects.
     * @return retval operation completion status.
     * @return inStream in case of success, created input stream.
     * @return suggestedConfig in case of invalid parameters, suggested config.
     */
    virtual ::android::hardware::Return<void> openInputStream(int32_t ioHandle, const ::android::hardware::audio::common::V6_0::DeviceAddress& device, const ::android::hardware::audio::common::V6_0::AudioConfig& config, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioInputFlag> flags, const ::android::hardware::audio::common::V6_0::SinkMetadata& sinkMetadata, openInputStream_cb _hidl_cb) = 0;

    /**
     * Returns whether HAL supports audio patches. Patch represents a connection
     * between signal source(s) and signal sink(s). If HAL doesn't support
     * patches natively (in hardware) then audio system will need to establish
     * them in software.
     *
     * @return supports true if audio patches are supported.
     */
    virtual ::android::hardware::Return<bool> supportsAudioPatches() = 0;

    /**
     * Return callback for createAudioPatch
     */
    using createAudioPatch_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, int32_t patch)>;
    /**
     * Creates an audio patch between several source and sink ports.  The handle
     * is allocated by the HAL and must be unique for this audio HAL module.
     *
     * @param sources patch sources.
     * @param sinks patch sinks.
     * @return retval operation completion status.
     * @return patch created patch handle.
     */
    virtual ::android::hardware::Return<void> createAudioPatch(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::AudioPortConfig>& sources, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::AudioPortConfig>& sinks, createAudioPatch_cb _hidl_cb) = 0;

    /**
     * Return callback for updateAudioPatch
     */
    using updateAudioPatch_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, int32_t patch)>;
    /**
     * Updates an audio patch.
     *
     * Use of this function is preferred to releasing and re-creating a patch
     * as the HAL module can figure out a way of switching the route without
     * causing audio disruption.
     *
     * @param previousPatch handle of the previous patch to update.
     * @param sources new patch sources.
     * @param sinks new patch sinks.
     * @return retval operation completion status.
     * @return patch updated patch handle.
     */
    virtual ::android::hardware::Return<void> updateAudioPatch(int32_t previousPatch, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::AudioPortConfig>& sources, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::AudioPortConfig>& sinks, updateAudioPatch_cb _hidl_cb) = 0;

    /**
     * Release an audio patch.
     *
     * @param patch patch handle.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> releaseAudioPatch(int32_t patch) = 0;

    /**
     * Return callback for getAudioPort
     */
    using getAudioPort_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, const ::android::hardware::audio::common::V6_0::AudioPort& resultPort)>;
    /**
     * Returns the list of supported attributes for a given audio port.
     *
     * As input, 'port' contains the information (type, role, address etc...)
     * needed by the HAL to identify the port.
     *
     * As output, 'resultPort' contains possible attributes (sampling rates,
     * formats, channel masks, gain controllers...) for this port.
     *
     * @param port port identifier.
     * @return retval operation completion status.
     * @return resultPort port descriptor with all parameters filled up.
     */
    virtual ::android::hardware::Return<void> getAudioPort(const ::android::hardware::audio::common::V6_0::AudioPort& port, getAudioPort_cb _hidl_cb) = 0;

    /**
     * Set audio port configuration.
     *
     * @param config audio port configuration.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setAudioPortConfig(const ::android::hardware::audio::common::V6_0::AudioPortConfig& config) = 0;

    /**
     * Return callback for getHwAvSync
     */
    using getHwAvSync_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, uint32_t hwAvSync)>;
    /**
     * Gets the HW synchronization source of the device. Calling this method is
     * equivalent to getting AUDIO_PARAMETER_HW_AV_SYNC on the legacy HAL.
     * Optional method
     *
     * @return retval operation completion status: OK or NOT_SUPPORTED.
     * @return hwAvSync HW synchronization source
     */
    virtual ::android::hardware::Return<void> getHwAvSync(getHwAvSync_cb _hidl_cb) = 0;

    /**
     * Sets whether the screen is on. Calling this method is equivalent to
     * setting AUDIO_PARAMETER_KEY_SCREEN_STATE on the legacy HAL.
     * Optional method
     *
     * @param turnedOn whether the screen is turned on.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setScreenState(bool turnedOn) = 0;

    /**
     * Return callback for getParameters
     */
    using getParameters_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& parameters)>;
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
    virtual ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) = 0;

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
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& parameters) = 0;

    /**
     * Return callback for getMicrophones
     */
    using getMicrophones_cb = std::function<void(::android::hardware::audio::V6_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::MicrophoneInfo>& microphones)>;
    /**
     * Returns an array with available microphones in device.
     *
     * @return retval NOT_SUPPORTED if there are no microphones on this device
     *                INVALID_STATE if the call is not successful,
     *                OK otherwise.
     *
     * @return microphones array with microphones info
     */
    virtual ::android::hardware::Return<void> getMicrophones(getMicrophones_cb _hidl_cb) = 0;

    /**
     * Notifies the device module about the connection state of an input/output
     * device attached to it. Calling this method is equivalent to setting
     * AUDIO_PARAMETER_DEVICE_[DIS]CONNECT on the legacy HAL.
     *
     * @param address audio device specification.
     * @param connected whether the device is connected.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setConnectedState(const ::android::hardware::audio::common::V6_0::DeviceAddress& address, bool connected) = 0;

    /**
     * Called by the framework to deinitialize the device and free up
     * all currently allocated resources. It is recommended to close
     * the device on the client side as soon as it is becomes unused.
     *
     * Note that all streams must be closed by the client before
     * attempting to close the device they belong to.
     *
     * @return retval OK in case the success.
     *                INVALID_STATE if the device was already closed
     *                or there are streams currently opened.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> close() = 0;

    /**
     * Applies an audio effect to an audio device. The effect is inserted
     * according to its insertion preference specified by INSERT_... EffectFlags
     * in the EffectDescriptor.
     *
     * @param device identifies the sink or source device this effect must be applied to.
     *               "device" is the AudioPortHandle indicated for the device when the audio
     *                patch connecting that device was created.
     * @param effectId effect ID (obtained from IEffectsFactory.createEffect) of
     *                 the effect to add.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> addDeviceEffect(int32_t device, uint64_t effectId) = 0;

    /**
     * Stops applying an audio effect to an audio device.
     *
     * @param device identifies the sink or source device this effect was applied to.
     *               "device" is the AudioPortHandle indicated for the device when the audio
     *               patch is created at the audio HAL.
     * @param effectId effect ID (obtained from IEffectsFactory.createEffect) of
     *                 the effect.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V6_0::Result> removeDeviceEffect(int32_t device, uint64_t effectId) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V6_0::IDevice>> castFrom(const ::android::sp<::android::hardware::audio::V6_0::IDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V6_0::IDevice>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IDevice> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDevice> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDevice> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDevice> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IDevice> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDevice> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDevice> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDevice> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::audio::V6_0::IDevice>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::audio::V6_0::IDevice>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::V6_0::IDevice::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V6_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_IDEVICE_H
