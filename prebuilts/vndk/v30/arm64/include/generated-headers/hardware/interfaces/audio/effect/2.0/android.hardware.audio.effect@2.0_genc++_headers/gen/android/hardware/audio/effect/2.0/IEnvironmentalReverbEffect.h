#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_IENVIRONMENTALREVERBEFFECT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_IENVIRONMENTALREVERBEFFECT_H

#include <android/hardware/audio/effect/2.0/IEffect.h>
#include <android/hardware/audio/effect/2.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {

struct IEnvironmentalReverbEffect : public ::android::hardware::audio::effect::V2_0::IEffect {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio.effect@2.0::IEnvironmentalReverbEffect"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class ParamRange : int16_t;
    struct AllProperties;

    enum class ParamRange : int16_t {
        ROOM_LEVEL_MIN = -6000 /* -6000 */,
        ROOM_LEVEL_MAX = 0,
        ROOM_HF_LEVEL_MIN = -4000 /* -4000 */,
        ROOM_HF_LEVEL_MAX = 0,
        DECAY_TIME_MIN = 100,
        DECAY_TIME_MAX = 20000,
        DECAY_HF_RATIO_MIN = 100,
        DECAY_HF_RATIO_MAX = 1000,
        REFLECTIONS_LEVEL_MIN = -6000 /* -6000 */,
        REFLECTIONS_LEVEL_MAX = 0,
        REFLECTIONS_DELAY_MIN = 0,
        REFLECTIONS_DELAY_MAX = 65,
        REVERB_LEVEL_MIN = -6000 /* -6000 */,
        REVERB_LEVEL_MAX = 0,
        REVERB_DELAY_MIN = 0,
        REVERB_DELAY_MAX = 65,
        DIFFUSION_MIN = 0,
        DIFFUSION_MAX = 1000,
        DENSITY_MIN = 0,
        DENSITY_MAX = 1000,
    };

    struct AllProperties final {
        int16_t roomLevel __attribute__ ((aligned(2)));
        int16_t roomHfLevel __attribute__ ((aligned(2)));
        uint32_t decayTime __attribute__ ((aligned(4)));
        int16_t decayHfRatio __attribute__ ((aligned(2)));
        int16_t reflectionsLevel __attribute__ ((aligned(2)));
        uint32_t reflectionsDelay __attribute__ ((aligned(4)));
        int16_t reverbLevel __attribute__ ((aligned(2)));
        uint32_t reverbDelay __attribute__ ((aligned(4)));
        int16_t diffusion __attribute__ ((aligned(2)));
        int16_t density __attribute__ ((aligned(2)));
    };

    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, roomLevel) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, roomHfLevel) == 2, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, decayTime) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, decayHfRatio) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, reflectionsLevel) == 10, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, reflectionsDelay) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, reverbLevel) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, reverbDelay) == 20, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, diffusion) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties, density) == 26, "wrong offset");
    static_assert(sizeof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties) == 28, "wrong size");
    static_assert(__alignof(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties) == 4, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Initialize effect engine--all configurations return to default.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> init() = 0;

    /**
     * Apply new audio parameters configurations for input and output buffers.
     * The provider callbacks may be empty, but in this case the buffer
     * must be provided in the EffectConfig structure.
     *
     * @param config configuration descriptor.
     * @param inputBufferProvider optional buffer provider reference.
     * @param outputBufferProvider optional buffer provider reference.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setConfig(const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider) = 0;

    /**
     * Reset the effect engine. Keep configuration but resets state and buffer
     * content.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> reset() = 0;

    /**
     * Enable processing.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> enable() = 0;

    /**
     * Disable processing.
     *
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> disable() = 0;

    /**
     * Set the rendering device the audio output path is connected to.  The
     * effect implementation must set EFFECT_FLAG_DEVICE_IND flag in its
     * descriptor to receive this command when the device changes.
     *
     * Note: this method is only supported for effects inserted into
     *       the output chain.
     *
     * @param device output device specification.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDevice(::android::hardware::audio::common::V2_0::AudioDevice device) = 0;

    /**
     * Return callback for setAndGetVolume
     */
    using setAndGetVolume_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::hidl_vec<uint32_t>& result)>;
    /**
     * Set and get volume. Used by audio framework to delegate volume control to
     * effect engine. The effect implementation must set EFFECT_FLAG_VOLUME_CTRL
     * flag in its descriptor to receive this command. The effect engine must
     * return the volume that should be applied before the effect is
     * processed. The overall volume (the volume actually applied by the effect
     * engine multiplied by the returned value) should match the value indicated
     * in the command.
     *
     * @param volumes vector containing volume for each channel defined in
     *                EffectConfig for output buffer expressed in 8.24 fixed
     *                point format.
     * @return result updated volume values.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<void> setAndGetVolume(const ::android::hardware::hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) = 0;

    /**
     * Notify the effect of the volume change. The effect implementation must
     * set EFFECT_FLAG_VOLUME_IND flag in its descriptor to receive this
     * command.
     *
     * @param volumes vector containing volume for each channel defined in
     *                EffectConfig for output buffer expressed in 8.24 fixed
     *                point format.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> volumeChangeNotification(const ::android::hardware::hidl_vec<uint32_t>& volumes) = 0;

    /**
     * Set the audio mode. The effect implementation must set
     * EFFECT_FLAG_AUDIO_MODE_IND flag in its descriptor to receive this command
     * when the audio mode changes.
     *
     * @param mode desired audio mode.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAudioMode(::android::hardware::audio::common::V2_0::AudioMode mode) = 0;

    /**
     * Apply new audio parameters configurations for input and output buffers of
     * reverse stream.  An example of reverse stream is the echo reference
     * supplied to an Acoustic Echo Canceler.
     *
     * @param config configuration descriptor.
     * @param inputBufferProvider optional buffer provider reference.
     * @param outputBufferProvider optional buffer provider reference.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setConfigReverse(const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider) = 0;

    /**
     * Set the capture device the audio input path is connected to. The effect
     * implementation must set EFFECT_FLAG_DEVICE_IND flag in its descriptor to
     * receive this command when the device changes.
     *
     * Note: this method is only supported for effects inserted into
     *       the input chain.
     *
     * @param device input device specification.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setInputDevice(::android::hardware::audio::common::V2_0::AudioDevice device) = 0;

    /**
     * Return callback for getConfig
     */
    using getConfig_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::audio::effect::V2_0::EffectConfig& config)>;
    /**
     * Read audio parameters configurations for input and output buffers.
     *
     * @return retval operation completion status.
     * @return config configuration descriptor.
     */
    virtual ::android::hardware::Return<void> getConfig(getConfig_cb _hidl_cb) = 0;

    /**
     * Return callback for getConfigReverse
     */
    using getConfigReverse_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::audio::effect::V2_0::EffectConfig& config)>;
    /**
     * Read audio parameters configurations for input and output buffers of
     * reverse stream.
     *
     * @return retval operation completion status.
     * @return config configuration descriptor.
     */
    virtual ::android::hardware::Return<void> getConfigReverse(getConfigReverse_cb _hidl_cb) = 0;

    /**
     * Return callback for getSupportedAuxChannelsConfigs
     */
    using getSupportedAuxChannelsConfigs_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::hidl_vec<::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig>& result)>;
    /**
     * Queries for supported combinations of main and auxiliary channels
     * (e.g. for a multi-microphone noise suppressor).
     *
     * @param maxConfigs maximum number of the combinations to return.
     * @return retval absence of the feature support is indicated using
     *                NOT_SUPPORTED code. RESULT_TOO_BIG is returned if
     *                the number of supported combinations exceeds 'maxConfigs'.
     * @return result list of configuration descriptors.
     */
    virtual ::android::hardware::Return<void> getSupportedAuxChannelsConfigs(uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) = 0;

    /**
     * Return callback for getAuxChannelsConfig
     */
    using getAuxChannelsConfig_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig& result)>;
    /**
     * Retrieves the current configuration of main and auxiliary channels.
     *
     * @return retval absence of the feature support is indicated using
     *                NOT_SUPPORTED code.
     * @return result configuration descriptor.
     */
    virtual ::android::hardware::Return<void> getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) = 0;

    /**
     * Sets the current configuration of main and auxiliary channels.
     *
     * @return retval operation completion status; absence of the feature
     *                support is indicated using NOT_SUPPORTED code.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAuxChannelsConfig(const ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig& config) = 0;

    /**
     * Set the audio source the capture path is configured for (Camcorder, voice
     * recognition...).
     *
     * Note: this method is only supported for effects inserted into
     *       the input chain.
     *
     * @param source source descriptor.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAudioSource(::android::hardware::audio::common::V2_0::AudioSource source) = 0;

    /**
     * This command indicates if the playback thread the effect is attached to
     * is offloaded or not, and updates the I/O handle of the playback thread
     * the effect is attached to.
     *
     * @param param effect offload descriptor.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> offload(const ::android::hardware::audio::effect::V2_0::EffectOffloadParameter& param) = 0;

    /**
     * Return callback for getDescriptor
     */
    using getDescriptor_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::audio::effect::V2_0::EffectDescriptor& descriptor)>;
    /**
     * Returns the effect descriptor.
     *
     * @return retval operation completion status.
     * @return descriptor effect descriptor.
     */
    virtual ::android::hardware::Return<void> getDescriptor(getDescriptor_cb _hidl_cb) = 0;

    /**
     * Return callback for prepareForProcessing
     */
    using prepareForProcessing_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::MQDescriptorSync<::android::hardware::audio::effect::V2_0::Result>& statusMQ)>;
    /**
     * Set up required transports for passing audio buffers to the effect.
     *
     * The transport consists of shared memory and a message queue for reporting
     * effect processing operation status. The shared memory is set up
     * separately using 'setProcessBuffers' method.
     *
     * Processing is requested by setting 'REQUEST_PROCESS' or
     * 'REQUEST_PROCESS_REVERSE' EventFlags associated with the status message
     * queue. The result of processing may be one of the following:
     *   OK if there were no errors during processing;
     *   INVALID_ARGUMENTS if audio buffers are invalid;
     *   INVALID_STATE if the engine has finished the disable phase;
     *   NOT_INITIALIZED if the audio buffers were not set;
     *   NOT_SUPPORTED if the requested processing type is not supported by
     *                 the effect.
     *
     * @return retval OK if both message queues were created successfully.
     *                INVALID_STATE if the method was already called.
     *                INVALID_ARGUMENTS if there was a problem setting up
     *                                  the queue.
     * @return statusMQ a message queue used for passing status from the effect.
     */
    virtual ::android::hardware::Return<void> prepareForProcessing(prepareForProcessing_cb _hidl_cb) = 0;

    /**
     * Set up input and output buffers for processing audio data. The effect
     * may modify both the input and the output buffer during the operation.
     * Buffers may be set multiple times during effect lifetime.
     *
     * The input and the output buffer may be reused between different effects,
     * and the input buffer may be used as an output buffer. Buffers are
     * distinguished using 'AudioBuffer.id' field.
     *
     * @param inBuffer input audio buffer.
     * @param outBuffer output audio buffer.
     * @return retval OK if both buffers were mapped successfully.
     *                INVALID_ARGUMENTS if there was a problem with mapping
     *                                  any of the buffers.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setProcessBuffers(const ::android::hardware::audio::effect::V2_0::AudioBuffer& inBuffer, const ::android::hardware::audio::effect::V2_0::AudioBuffer& outBuffer) = 0;

    /**
     * Return callback for command
     */
    using command_cb = std::function<void(int32_t status, const ::android::hardware::hidl_vec<uint8_t>& result)>;
    /**
     * Execute a vendor specific command on the effect. The command code
     * and data, as well as result data are not interpreted by Android
     * Framework and are passed as-is between the application and the effect.
     *
     * The effect must use standard POSIX.1-2001 error codes for the operation
     * completion status.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it. This method only works for effects
     * implemented in software.
     *
     * @param commandId the ID of the command.
     * @param data command data.
     * @param resultMaxSize maximum size in bytes of the result; can be 0.
     * @return status command completion status.
     * @return result result data.
     */
    virtual ::android::hardware::Return<void> command(uint32_t commandId, const ::android::hardware::hidl_vec<uint8_t>& data, uint32_t resultMaxSize, command_cb _hidl_cb) = 0;

    /**
     * Set a vendor-specific parameter and apply it immediately. The parameter
     * code and data are not interpreted by Android Framework and are passed
     * as-is between the application and the effect.
     *
     * The effect must use INVALID_ARGUMENTS return code if the parameter ID is
     * unknown or if provided parameter data is invalid. If the effect does not
     * support setting vendor-specific parameters, it must return NOT_SUPPORTED.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it. This method only works for effects
     * implemented in software.
     *
     * @param parameter identifying data of the parameter.
     * @param value the value of the parameter.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, const ::android::hardware::hidl_vec<uint8_t>& value) = 0;

    /**
     * Return callback for getParameter
     */
    using getParameter_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::hidl_vec<uint8_t>& value)>;
    /**
     * Get a vendor-specific parameter value. The parameter code and returned
     * data are not interpreted by Android Framework and are passed as-is
     * between the application and the effect.
     *
     * The effect must use INVALID_ARGUMENTS return code if the parameter ID is
     * unknown. If the effect does not support setting vendor-specific
     * parameters, it must return NOT_SUPPORTED.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it.  This method only works for effects
     * implemented in software.
     *
     * @param parameter identifying data of the parameter.
     * @param valueMaxSize maximum size in bytes of the value.
     * @return retval operation completion status.
     * @return result the value of the parameter.
     */
    virtual ::android::hardware::Return<void> getParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, uint32_t valueMaxSize, getParameter_cb _hidl_cb) = 0;

    /**
     * Return callback for getSupportedConfigsForFeature
     */
    using getSupportedConfigsForFeature_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, uint32_t configsCount, const ::android::hardware::hidl_vec<uint8_t>& configsData)>;
    /**
     * Get supported configs for a vendor-specific feature. The configs returned
     * are not interpreted by Android Framework and are passed as-is between the
     * application and the effect.
     *
     * The effect must use INVALID_ARGUMENTS return code if the feature ID is
     * unknown. If the effect does not support getting vendor-specific feature
     * configs, it must return NOT_SUPPORTED. If the feature is supported but
     * the total number of supported configurations exceeds the maximum number
     * indicated by the caller, the method must return RESULT_TOO_BIG.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it.  This method only works for effects
     * implemented in software.
     *
     * @param featureId feature identifier.
     * @param maxConfigs maximum number of configs to return.
     * @param configSize size of each config in bytes.
     * @return retval operation completion status.
     * @return configsCount number of configs returned.
     * @return configsData data for all the configs returned.
     */
    virtual ::android::hardware::Return<void> getSupportedConfigsForFeature(uint32_t featureId, uint32_t maxConfigs, uint32_t configSize, getSupportedConfigsForFeature_cb _hidl_cb) = 0;

    /**
     * Return callback for getCurrentConfigForFeature
     */
    using getCurrentConfigForFeature_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::hidl_vec<uint8_t>& configData)>;
    /**
     * Get the current config for a vendor-specific feature. The config returned
     * is not interpreted by Android Framework and is passed as-is between the
     * application and the effect.
     *
     * The effect must use INVALID_ARGUMENTS return code if the feature ID is
     * unknown. If the effect does not support getting vendor-specific
     * feature configs, it must return NOT_SUPPORTED.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it.  This method only works for effects
     * implemented in software.
     *
     * @param featureId feature identifier.
     * @param configSize size of the config in bytes.
     * @return retval operation completion status.
     * @return configData config data.
     */
    virtual ::android::hardware::Return<void> getCurrentConfigForFeature(uint32_t featureId, uint32_t configSize, getCurrentConfigForFeature_cb _hidl_cb) = 0;

    /**
     * Set the current config for a vendor-specific feature. The config data
     * is not interpreted by Android Framework and is passed as-is between the
     * application and the effect.
     *
     * The effect must use INVALID_ARGUMENTS return code if the feature ID is
     * unknown. If the effect does not support getting vendor-specific
     * feature configs, it must return NOT_SUPPORTED.
     *
     * Use this method only if the effect is provided by a third party, and
     * there is no interface defined for it.  This method only works for effects
     * implemented in software.
     *
     * @param featureId feature identifier.
     * @param configData config data.
     * @return retval operation completion status.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setCurrentConfigForFeature(uint32_t featureId, const ::android::hardware::hidl_vec<uint8_t>& configData) = 0;

    /**
     * Called by the framework to deinitialize the effect and free up
     * all the currently allocated resources. It is recommended to close
     * the effect on the client side as soon as it is becomes unused.
     *
     * @return retval OK in case the success.
     *                INVALID_STATE if the effect was already closed.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> close() = 0;

    /**
     * Sets whether the effect should be bypassed.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setBypass(bool bypass) = 0;

    /**
     * Return callback for getBypass
     */
    using getBypass_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, bool bypass)>;
    /**
     * Gets whether the effect should be bypassed.
     */
    virtual ::android::hardware::Return<void> getBypass(getBypass_cb _hidl_cb) = 0;

    /**
     * Sets the room level.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setRoomLevel(int16_t roomLevel) = 0;

    /**
     * Return callback for getRoomLevel
     */
    using getRoomLevel_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t roomLevel)>;
    /**
     * Gets the room level.
     */
    virtual ::android::hardware::Return<void> getRoomLevel(getRoomLevel_cb _hidl_cb) = 0;

    /**
     * Sets the room high frequencies level.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setRoomHfLevel(int16_t roomHfLevel) = 0;

    /**
     * Return callback for getRoomHfLevel
     */
    using getRoomHfLevel_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t roomHfLevel)>;
    /**
     * Gets the room high frequencies level.
     */
    virtual ::android::hardware::Return<void> getRoomHfLevel(getRoomHfLevel_cb _hidl_cb) = 0;

    /**
     * Sets the room decay time.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDecayTime(uint32_t decayTime) = 0;

    /**
     * Return callback for getDecayTime
     */
    using getDecayTime_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, uint32_t decayTime)>;
    /**
     * Gets the room decay time.
     */
    virtual ::android::hardware::Return<void> getDecayTime(getDecayTime_cb _hidl_cb) = 0;

    /**
     * Sets the ratio of high frequencies decay.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDecayHfRatio(int16_t decayHfRatio) = 0;

    /**
     * Return callback for getDecayHfRatio
     */
    using getDecayHfRatio_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t decayHfRatio)>;
    /**
     * Gets the ratio of high frequencies decay.
     */
    virtual ::android::hardware::Return<void> getDecayHfRatio(getDecayHfRatio_cb _hidl_cb) = 0;

    /**
     * Sets the level of reflections in the room.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setReflectionsLevel(int16_t reflectionsLevel) = 0;

    /**
     * Return callback for getReflectionsLevel
     */
    using getReflectionsLevel_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t reflectionsLevel)>;
    /**
     * Gets the level of reflections in the room.
     */
    virtual ::android::hardware::Return<void> getReflectionsLevel(getReflectionsLevel_cb _hidl_cb) = 0;

    /**
     * Sets the reflections delay in the room.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setReflectionsDelay(uint32_t reflectionsDelay) = 0;

    /**
     * Return callback for getReflectionsDelay
     */
    using getReflectionsDelay_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, uint32_t reflectionsDelay)>;
    /**
     * Gets the reflections delay in the room.
     */
    virtual ::android::hardware::Return<void> getReflectionsDelay(getReflectionsDelay_cb _hidl_cb) = 0;

    /**
     * Sets the reverb level of the room.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setReverbLevel(int16_t reverbLevel) = 0;

    /**
     * Return callback for getReverbLevel
     */
    using getReverbLevel_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t reverbLevel)>;
    /**
     * Gets the reverb level of the room.
     */
    virtual ::android::hardware::Return<void> getReverbLevel(getReverbLevel_cb _hidl_cb) = 0;

    /**
     * Sets the reverb delay of the room.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setReverbDelay(uint32_t reverDelay) = 0;

    /**
     * Return callback for getReverbDelay
     */
    using getReverbDelay_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, uint32_t reverbDelay)>;
    /**
     * Gets the reverb delay of the room.
     */
    virtual ::android::hardware::Return<void> getReverbDelay(getReverbDelay_cb _hidl_cb) = 0;

    /**
     * Sets room diffusion.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDiffusion(int16_t diffusion) = 0;

    /**
     * Return callback for getDiffusion
     */
    using getDiffusion_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t diffusion)>;
    /**
     * Gets room diffusion.
     */
    virtual ::android::hardware::Return<void> getDiffusion(getDiffusion_cb _hidl_cb) = 0;

    /**
     * Sets room wall density.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDensity(int16_t density) = 0;

    /**
     * Return callback for getDensity
     */
    using getDensity_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, int16_t density)>;
    /**
     * Gets room wall density.
     */
    virtual ::android::hardware::Return<void> getDensity(getDensity_cb _hidl_cb) = 0;

    /**
     * Sets all properties at once.
     */
    virtual ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAllProperties(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& properties) = 0;

    /**
     * Return callback for getAllProperties
     */
    using getAllProperties_cb = std::function<void(::android::hardware::audio::effect::V2_0::Result retval, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& properties)>;
    /**
     * Gets all properties at once.
     */
    virtual ::android::hardware::Return<void> getAllProperties(getAllProperties_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>> castFrom(const ::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>> castFrom(const ::android::sp<::android::hardware::audio::effect::V2_0::IEffect>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IEnvironmentalReverbEffect> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEnvironmentalReverbEffect> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEnvironmentalReverbEffect> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEnvironmentalReverbEffect> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IEnvironmentalReverbEffect> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEnvironmentalReverbEffect> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEnvironmentalReverbEffect> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEnvironmentalReverbEffect> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(int16_t o);
static inline std::string toString(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange o);
static inline void PrintTo(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange o, ::std::ostream* os);
constexpr int16_t operator|(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange rhs) {
    return static_cast<int16_t>(static_cast<int16_t>(lhs) | static_cast<int16_t>(rhs));
}
constexpr int16_t operator|(const int16_t lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange rhs) {
    return static_cast<int16_t>(lhs | static_cast<int16_t>(rhs));
}
constexpr int16_t operator|(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange lhs, const int16_t rhs) {
    return static_cast<int16_t>(static_cast<int16_t>(lhs) | rhs);
}
constexpr int16_t operator&(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange rhs) {
    return static_cast<int16_t>(static_cast<int16_t>(lhs) & static_cast<int16_t>(rhs));
}
constexpr int16_t operator&(const int16_t lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange rhs) {
    return static_cast<int16_t>(lhs & static_cast<int16_t>(rhs));
}
constexpr int16_t operator&(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange lhs, const int16_t rhs) {
    return static_cast<int16_t>(static_cast<int16_t>(lhs) & rhs);
}
constexpr int16_t &operator|=(int16_t& v, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange e) {
    v |= static_cast<int16_t>(e);
    return v;
}
constexpr int16_t &operator&=(int16_t& v, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange e) {
    v &= static_cast<int16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& o);
static inline void PrintTo(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& rhs);
static inline bool operator!=(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange>(int16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MIN)) {
        os += (first ? "" : " | ");
        os += "ROOM_LEVEL_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MAX)) {
        os += (first ? "" : " | ");
        os += "ROOM_LEVEL_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MIN)) {
        os += (first ? "" : " | ");
        os += "ROOM_HF_LEVEL_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MAX)) {
        os += (first ? "" : " | ");
        os += "ROOM_HF_LEVEL_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MIN)) {
        os += (first ? "" : " | ");
        os += "DECAY_TIME_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MAX)) {
        os += (first ? "" : " | ");
        os += "DECAY_TIME_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MIN)) {
        os += (first ? "" : " | ");
        os += "DECAY_HF_RATIO_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MAX)) {
        os += (first ? "" : " | ");
        os += "DECAY_HF_RATIO_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MIN)) {
        os += (first ? "" : " | ");
        os += "REFLECTIONS_LEVEL_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MAX)) {
        os += (first ? "" : " | ");
        os += "REFLECTIONS_LEVEL_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MIN)) {
        os += (first ? "" : " | ");
        os += "REFLECTIONS_DELAY_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MAX)) {
        os += (first ? "" : " | ");
        os += "REFLECTIONS_DELAY_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MIN)) {
        os += (first ? "" : " | ");
        os += "REVERB_LEVEL_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MAX)) {
        os += (first ? "" : " | ");
        os += "REVERB_LEVEL_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MIN)) {
        os += (first ? "" : " | ");
        os += "REVERB_DELAY_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MAX)) {
        os += (first ? "" : " | ");
        os += "REVERB_DELAY_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MIN)) {
        os += (first ? "" : " | ");
        os += "DIFFUSION_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MAX)) {
        os += (first ? "" : " | ");
        os += "DIFFUSION_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MAX;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MIN) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MIN)) {
        os += (first ? "" : " | ");
        os += "DENSITY_MIN";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MIN;
    }
    if ((o & ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MAX) == static_cast<int16_t>(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MAX)) {
        os += (first ? "" : " | ");
        os += "DENSITY_MAX";
        first = false;
        flipped |= ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MIN) {
        return "ROOM_LEVEL_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MAX) {
        return "ROOM_LEVEL_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MIN) {
        return "ROOM_HF_LEVEL_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MAX) {
        return "ROOM_HF_LEVEL_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MIN) {
        return "DECAY_TIME_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MAX) {
        return "DECAY_TIME_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MIN) {
        return "DECAY_HF_RATIO_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MAX) {
        return "DECAY_HF_RATIO_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MIN) {
        return "REFLECTIONS_LEVEL_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MAX) {
        return "REFLECTIONS_LEVEL_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MIN) {
        return "REFLECTIONS_DELAY_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MAX) {
        return "REFLECTIONS_DELAY_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MIN) {
        return "REVERB_LEVEL_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MAX) {
        return "REVERB_LEVEL_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MIN) {
        return "REVERB_DELAY_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MAX) {
        return "REVERB_DELAY_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MIN) {
        return "DIFFUSION_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MAX) {
        return "DIFFUSION_MAX";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MIN) {
        return "DENSITY_MIN";
    }
    if (o == ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MAX) {
        return "DENSITY_MAX";
    }
    std::string os;
    os += toHexString(static_cast<int16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".roomLevel = ";
    os += ::android::hardware::toString(o.roomLevel);
    os += ", .roomHfLevel = ";
    os += ::android::hardware::toString(o.roomHfLevel);
    os += ", .decayTime = ";
    os += ::android::hardware::toString(o.decayTime);
    os += ", .decayHfRatio = ";
    os += ::android::hardware::toString(o.decayHfRatio);
    os += ", .reflectionsLevel = ";
    os += ::android::hardware::toString(o.reflectionsLevel);
    os += ", .reflectionsDelay = ";
    os += ::android::hardware::toString(o.reflectionsDelay);
    os += ", .reverbLevel = ";
    os += ::android::hardware::toString(o.reverbLevel);
    os += ", .reverbDelay = ";
    os += ::android::hardware::toString(o.reverbDelay);
    os += ", .diffusion = ";
    os += ::android::hardware::toString(o.diffusion);
    os += ", .density = ";
    os += ::android::hardware::toString(o.density);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& rhs) {
    if (lhs.roomLevel != rhs.roomLevel) {
        return false;
    }
    if (lhs.roomHfLevel != rhs.roomHfLevel) {
        return false;
    }
    if (lhs.decayTime != rhs.decayTime) {
        return false;
    }
    if (lhs.decayHfRatio != rhs.decayHfRatio) {
        return false;
    }
    if (lhs.reflectionsLevel != rhs.reflectionsLevel) {
        return false;
    }
    if (lhs.reflectionsDelay != rhs.reflectionsDelay) {
        return false;
    }
    if (lhs.reverbLevel != rhs.reverbLevel) {
        return false;
    }
    if (lhs.reverbDelay != rhs.reverbDelay) {
        return false;
    }
    if (lhs.diffusion != rhs.diffusion) {
        return false;
    }
    if (lhs.density != rhs.density) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& lhs, const ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::AllProperties& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace effect
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
template<> inline constexpr std::array<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange, 20> hidl_enum_values<::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange> = {
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_LEVEL_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::ROOM_HF_LEVEL_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_TIME_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DECAY_HF_RATIO_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_LEVEL_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REFLECTIONS_DELAY_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_LEVEL_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::REVERB_DELAY_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DIFFUSION_MAX,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MIN,
    ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect::ParamRange::DENSITY_MAX,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_IENVIRONMENTALREVERBEFFECT_H
