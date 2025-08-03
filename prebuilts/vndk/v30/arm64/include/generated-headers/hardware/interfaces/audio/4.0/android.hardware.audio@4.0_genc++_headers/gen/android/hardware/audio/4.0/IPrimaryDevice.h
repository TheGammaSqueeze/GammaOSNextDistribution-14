#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_IPRIMARYDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_IPRIMARYDEVICE_H

#include <android/hardware/audio/common/4.0/types.h>
#include <android/hardware/audio/4.0/IDevice.h>
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

struct IPrimaryDevice : public ::android::hardware::audio::V4_0::IDevice {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio@4.0::IPrimaryDevice"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class TtyMode : int32_t;
    enum class Rotation : int32_t;

    enum class TtyMode : int32_t {
        OFF = 0,
        VCO = 1 /* ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode.OFF implicitly + 1 */,
        HCO = 2 /* ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode.VCO implicitly + 1 */,
        FULL = 3 /* ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode.HCO implicitly + 1 */,
    };

    enum class Rotation : int32_t {
        DEG_0 = 0,
        DEG_90 = 1 /* ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation.DEG_0 implicitly + 1 */,
        DEG_180 = 2 /* ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation.DEG_90 implicitly + 1 */,
        DEG_270 = 3 /* ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation.DEG_180 implicitly + 1 */,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Returns whether the audio hardware interface has been initialized.
     *
     * @return retval OK on success, NOT_INITIALIZED on failure.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> initCheck() = 0;

    /**
     * Sets the audio volume for all audio activities other than voice call. If
     * NOT_SUPPORTED is returned, the software mixer will emulate this
     * capability.
     *
     * @param volume 1.0f means unity, 0.0f is zero.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setMasterVolume(float volume) = 0;

    /**
     * Return callback for getMasterVolume
     */
    using getMasterVolume_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, float volume)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setMicMute(bool mute) = 0;

    /**
     * Return callback for getMicMute
     */
    using getMicMute_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool mute)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setMasterMute(bool mute) = 0;

    /**
     * Return callback for getMasterMute
     */
    using getMasterMute_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool mute)>;
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
    using getInputBufferSize_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, uint64_t bufferSize)>;
    /**
     * Returns audio input buffer size according to parameters passed or
     * INVALID_ARGUMENTS if one of the parameters is not supported.
     *
     * @param config audio configuration.
     * @return retval operation completion status.
     * @return bufferSize input buffer size in bytes.
     */
    virtual ::android::hardware::Return<void> getInputBufferSize(const ::android::hardware::audio::common::V4_0::AudioConfig& config, getInputBufferSize_cb _hidl_cb) = 0;

    /**
     * Return callback for openOutputStream
     */
    using openOutputStream_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::sp<::android::hardware::audio::V4_0::IStreamOut>& outStream, const ::android::hardware::audio::common::V4_0::AudioConfig& suggestedConfig)>;
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
    virtual ::android::hardware::Return<void> openOutputStream(int32_t ioHandle, const ::android::hardware::audio::V4_0::DeviceAddress& device, const ::android::hardware::audio::common::V4_0::AudioConfig& config, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioOutputFlag> flags, const ::android::hardware::audio::V4_0::SourceMetadata& sourceMetadata, openOutputStream_cb _hidl_cb) = 0;

    /**
     * Return callback for openInputStream
     */
    using openInputStream_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::sp<::android::hardware::audio::V4_0::IStreamIn>& inStream, const ::android::hardware::audio::common::V4_0::AudioConfig& suggestedConfig)>;
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
     *                     May be used by implementations to configure hardware effects.
     * @return retval operation completion status.
     * @return inStream in case of success, created input stream.
     * @return suggestedConfig in case of invalid parameters, suggested config.
     */
    virtual ::android::hardware::Return<void> openInputStream(int32_t ioHandle, const ::android::hardware::audio::V4_0::DeviceAddress& device, const ::android::hardware::audio::common::V4_0::AudioConfig& config, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioInputFlag> flags, const ::android::hardware::audio::V4_0::SinkMetadata& sinkMetadata, openInputStream_cb _hidl_cb) = 0;

    /**
     * Returns whether HAL supports audio patches.
     *
     * @return supports true if audio patches are supported.
     */
    virtual ::android::hardware::Return<bool> supportsAudioPatches() = 0;

    /**
     * Return callback for createAudioPatch
     */
    using createAudioPatch_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, int32_t patch)>;
    /**
     * Creates an audio patch between several source and sink ports.  The handle
     * is allocated by the HAL and must be unique for this audio HAL module.
     *
     * @param sources patch sources.
     * @param sinks patch sinks.
     * @return retval operation completion status.
     * @return patch created patch handle.
     */
    virtual ::android::hardware::Return<void> createAudioPatch(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V4_0::AudioPortConfig>& sources, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V4_0::AudioPortConfig>& sinks, createAudioPatch_cb _hidl_cb) = 0;

    /**
     * Release an audio patch.
     *
     * @param patch patch handle.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> releaseAudioPatch(int32_t patch) = 0;

    /**
     * Return callback for getAudioPort
     */
    using getAudioPort_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::audio::common::V4_0::AudioPort& resultPort)>;
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
    virtual ::android::hardware::Return<void> getAudioPort(const ::android::hardware::audio::common::V4_0::AudioPort& port, getAudioPort_cb _hidl_cb) = 0;

    /**
     * Set audio port configuration.
     *
     * @param config audio port configuration.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setAudioPortConfig(const ::android::hardware::audio::common::V4_0::AudioPortConfig& config) = 0;

    /**
     * Return callback for getHwAvSync
     */
    using getHwAvSync_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, uint32_t hwAvSync)>;
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
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setScreenState(bool turnedOn) = 0;

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
     * Return callback for getMicrophones
     */
    using getMicrophones_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::MicrophoneInfo>& microphones)>;
    /**
     * Returns an array with available microphones in device.
     *
     * @return retval INVALID_STATE if the call is not successful,
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
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setConnectedState(const ::android::hardware::audio::V4_0::DeviceAddress& address, bool connected) = 0;

    /**
     * Sets the audio volume of a voice call.
     *
     * @param volume 1.0f means unity, 0.0f is zero.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setVoiceVolume(float volume) = 0;

    /**
     * This method is used to notify the HAL about audio mode changes.
     *
     * @param mode new mode.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setMode(::android::hardware::audio::common::V4_0::AudioMode mode) = 0;

    /**
     * Sets the name of the current BT SCO headset. Calling this method
     * is equivalent to setting legacy "bt_headset_name" parameter.
     * The BT SCO headset name must only be used for debugging purposes.
     * Optional method
     *
     * @param name the name of the current BT SCO headset (can be empty).
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtScoHeadsetDebugName(const ::android::hardware::hidl_string& name) = 0;

    /**
     * Return callback for getBtScoNrecEnabled
     */
    using getBtScoNrecEnabled_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool enabled)>;
    /**
     * Gets whether BT SCO Noise Reduction and Echo Cancellation are enabled.
     * Calling this method is equivalent to getting AUDIO_PARAMETER_KEY_BT_NREC
     * on the legacy HAL.
     *
     * @return retval operation completion status.
     * @return enabled whether BT SCO NR + EC are enabled.
     */
    virtual ::android::hardware::Return<void> getBtScoNrecEnabled(getBtScoNrecEnabled_cb _hidl_cb) = 0;

    /**
     * Sets whether BT SCO Noise Reduction and Echo Cancellation are enabled.
     * Calling this method is equivalent to setting AUDIO_PARAMETER_KEY_BT_NREC
     * on the legacy HAL.
     * Optional method
     *
     * @param enabled whether BT SCO NR + EC are enabled.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtScoNrecEnabled(bool enabled) = 0;

    /**
     * Return callback for getBtScoWidebandEnabled
     */
    using getBtScoWidebandEnabled_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool enabled)>;
    /**
     * Gets whether BT SCO Wideband mode is enabled. Calling this method is
     * equivalent to getting AUDIO_PARAMETER_KEY_BT_SCO_WB on the legacy HAL.
     *
     * @return retval operation completion status.
     * @return enabled whether BT Wideband is enabled.
     */
    virtual ::android::hardware::Return<void> getBtScoWidebandEnabled(getBtScoWidebandEnabled_cb _hidl_cb) = 0;

    /**
     * Sets whether BT SCO Wideband mode is enabled. Calling this method is
     * equivalent to setting AUDIO_PARAMETER_KEY_BT_SCO_WB on the legacy HAL.
     * Optional method
     *
     * @param enabled whether BT Wideband is enabled.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtScoWidebandEnabled(bool enabled) = 0;

    /**
     * Return callback for getBtHfpEnabled
     */
    using getBtHfpEnabled_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool enabled)>;
    /**
     * Gets whether BT HFP (Hands-Free Profile) is enabled. Calling this method
     * is equivalent to getting "hfp_enable" parameter value on the legacy HAL.
     *
     * @return retval operation completion status.
     * @return enabled whether BT HFP is enabled.
     */
    virtual ::android::hardware::Return<void> getBtHfpEnabled(getBtHfpEnabled_cb _hidl_cb) = 0;

    /**
     * Sets whether BT HFP (Hands-Free Profile) is enabled. Calling this method
     * is equivalent to setting "hfp_enable" parameter on the legacy HAL.
     * Optional method
     *
     * @param enabled whether BT HFP is enabled.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtHfpEnabled(bool enabled) = 0;

    /**
     * Sets the sampling rate of BT HFP (Hands-Free Profile). Calling this
     * method is equivalent to setting "hfp_set_sampling_rate" parameter
     * on the legacy HAL.
     * Optional method
     *
     * @param sampleRateHz sample rate in Hz.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtHfpSampleRate(uint32_t sampleRateHz) = 0;

    /**
     * Sets the current output volume Hz for BT HFP (Hands-Free Profile).
     * Calling this method is equivalent to setting "hfp_volume" parameter value
     * on the legacy HAL (except that legacy HAL implementations expect
     * an integer value in the range from 0 to 15.)
     * Optional method
     *
     * @param volume 1.0f means unity, 0.0f is zero.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setBtHfpVolume(float volume) = 0;

    /**
     * Return callback for getTtyMode
     */
    using getTtyMode_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode mode)>;
    /**
     * Gets current TTY mode selection. Calling this method is equivalent to
     * getting AUDIO_PARAMETER_KEY_TTY_MODE on the legacy HAL.
     *
     * @return retval operation completion status.
     * @return mode TTY mode.
     */
    virtual ::android::hardware::Return<void> getTtyMode(getTtyMode_cb _hidl_cb) = 0;

    /**
     * Sets current TTY mode. Calling this method is equivalent to setting
     * AUDIO_PARAMETER_KEY_TTY_MODE on the legacy HAL.
     *
     * @param mode TTY mode.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setTtyMode(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode mode) = 0;

    /**
     * Return callback for getHacEnabled
     */
    using getHacEnabled_cb = std::function<void(::android::hardware::audio::V4_0::Result retval, bool enabled)>;
    /**
     * Gets whether Hearing Aid Compatibility - Telecoil (HAC-T) mode is
     * enabled. Calling this method is equivalent to getting
     * AUDIO_PARAMETER_KEY_HAC on the legacy HAL.
     *
     * @return retval operation completion status.
     * @return enabled whether HAC mode is enabled.
     */
    virtual ::android::hardware::Return<void> getHacEnabled(getHacEnabled_cb _hidl_cb) = 0;

    /**
     * Sets whether Hearing Aid Compatibility - Telecoil (HAC-T) mode is
     * enabled. Calling this method is equivalent to setting
     * AUDIO_PARAMETER_KEY_HAC on the legacy HAL.
     * Optional method
     *
     * @param enabled whether HAC mode is enabled.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setHacEnabled(bool enabled) = 0;

    /**
     * Updates HAL on the current rotation of the device relative to natural
     * orientation. Calling this method is equivalent to setting legacy
     * parameter "rotation".
     *
     * @param rotation rotation in degrees relative to natural device
     *     orientation.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::V4_0::Result> updateRotation(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation rotation) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>> castFrom(const ::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>> castFrom(const ::android::sp<::android::hardware::audio::V4_0::IDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IPrimaryDevice> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IPrimaryDevice> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IPrimaryDevice> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IPrimaryDevice> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IPrimaryDevice> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IPrimaryDevice> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IPrimaryDevice> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IPrimaryDevice> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode o);
static inline void PrintTo(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation o);
static inline void PrintTo(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::OFF) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::OFF;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::VCO) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::VCO)) {
        os += (first ? "" : " | ");
        os += "VCO";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::VCO;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::HCO) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::HCO)) {
        os += (first ? "" : " | ");
        os += "HCO";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::HCO;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::FULL) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::FULL)) {
        os += (first ? "" : " | ");
        os += "FULL";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::VCO) {
        return "VCO";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::HCO) {
        return "HCO";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::FULL) {
        return "FULL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::audio::V4_0::IPrimaryDevice::Rotation>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::V4_0::IPrimaryDevice::Rotation> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_0) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_0)) {
        os += (first ? "" : " | ");
        os += "DEG_0";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_0;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_90) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_90)) {
        os += (first ? "" : " | ");
        os += "DEG_90";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_90;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_180) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_180)) {
        os += (first ? "" : " | ");
        os += "DEG_180";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_180;
    }
    if ((o & ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_270) == static_cast<int32_t>(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_270)) {
        os += (first ? "" : " | ");
        os += "DEG_270";
        first = false;
        flipped |= ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_270;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_0) {
        return "DEG_0";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_90) {
        return "DEG_90";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_180) {
        return "DEG_180";
    }
    if (o == ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_270) {
        return "DEG_270";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::V4_0::IPrimaryDevice::Rotation o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::audio::V4_0::IPrimaryDevice>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::V4_0::IPrimaryDevice::descriptor;
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
template<> inline constexpr std::array<::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode, 4> hidl_enum_values<::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode> = {
    ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::OFF,
    ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::VCO,
    ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::HCO,
    ::android::hardware::audio::V4_0::IPrimaryDevice::TtyMode::FULL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::audio::V4_0::IPrimaryDevice::Rotation, 4> hidl_enum_values<::android::hardware::audio::V4_0::IPrimaryDevice::Rotation> = {
    ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_0,
    ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_90,
    ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_180,
    ::android::hardware::audio::V4_0::IPrimaryDevice::Rotation::DEG_270,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_IPRIMARYDEVICE_H
