#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHW_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHW_H

#include <android/hardware/audio/common/2.0/types.h>
#include <android/hardware/soundtrigger/2.0/ISoundTriggerHwCallback.h>
#include <android/hardware/soundtrigger/2.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {

struct ISoundTriggerHw : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.soundtrigger@2.0::ISoundTriggerHw"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct Properties;
    struct SoundModel;
    struct Phrase;
    struct PhraseSoundModel;
    struct RecognitionConfig;

    /**
     * Sound trigger implementation descriptor read by the framework via
     * getProperties(). Used by SoundTrigger service to report to applications
     * and manage concurrency and policy.
     */
    struct Properties final {
        /**
         * Implementor name
         */
        ::android::hardware::hidl_string implementor __attribute__ ((aligned(8)));
        /**
         * Implementation description
         */
        ::android::hardware::hidl_string description __attribute__ ((aligned(8)));
        /**
         * Implementation version
         */
        uint32_t version __attribute__ ((aligned(4)));
        /**
         * Unique implementation ID. The UUID must change with each version of
         * the engine implementation
         */
        ::android::hardware::audio::common::V2_0::Uuid uuid __attribute__ ((aligned(4)));
        /**
         * Maximum number of concurrent sound models loaded
         */
        uint32_t maxSoundModels __attribute__ ((aligned(4)));
        /**
         * Maximum number of key phrases
         */
        uint32_t maxKeyPhrases __attribute__ ((aligned(4)));
        /**
         * Maximum number of concurrent users detected
         */
        uint32_t maxUsers __attribute__ ((aligned(4)));
        /**
         * All supported modes. e.g RecognitionMode.VOICE_TRIGGER
         */
        uint32_t recognitionModes __attribute__ ((aligned(4)));
        /**
         * Supports seamless transition from detection to capture
         */
        bool captureTransition __attribute__ ((aligned(1)));
        /**
         * Maximum buffering capacity in ms if captureTransition is true
         */
        uint32_t maxBufferMs __attribute__ ((aligned(4)));
        /**
         * Supports capture by other use cases while detection is active
         */
        bool concurrentCapture __attribute__ ((aligned(1)));
        /**
         * Returns the trigger capture in event
         */
        bool triggerInEvent __attribute__ ((aligned(1)));
        /**
         * Rated power consumption when detection is active with TDB
         * silence/sound/speech ratio
         */
        uint32_t powerConsumptionMw __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, implementor) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, description) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, version) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, uuid) == 36, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, maxSoundModels) == 52, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, maxKeyPhrases) == 56, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, maxUsers) == 60, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, recognitionModes) == 64, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, captureTransition) == 68, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, maxBufferMs) == 72, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, concurrentCapture) == 76, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, triggerInEvent) == 77, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties, powerConsumptionMw) == 80, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties) == 88, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties) == 8, "wrong alignment");

    /**
     * Base sound model descriptor. This struct is the header of a larger block
     * passed to loadSoundModel() and contains the binary data of the
     * sound model.
     */
    struct SoundModel final {
        /**
         * Model type. e.g. SoundModelType.KEYPHRASE
         */
        ::android::hardware::soundtrigger::V2_0::SoundModelType type __attribute__ ((aligned(4)));
        /**
         * Unique sound model ID.
         */
        ::android::hardware::audio::common::V2_0::Uuid uuid __attribute__ ((aligned(4)));
        /**
         * Unique vendor ID. Identifies the engine the sound model
         * was build for
         */
        ::android::hardware::audio::common::V2_0::Uuid vendorUuid __attribute__ ((aligned(4)));
        /**
         * Opaque data transparent to Android framework
         */
        ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel, uuid) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel, vendorUuid) == 20, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel, data) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel) == 8, "wrong alignment");

    /**
     * Key phrase descriptor
     */
    struct Phrase final {
        /**
         * Unique keyphrase ID assigned at enrollment time
         */
        uint32_t id __attribute__ ((aligned(4)));
        /**
         * Recognition modes supported by this key phrase
         */
        uint32_t recognitionModes __attribute__ ((aligned(4)));
        /**
         * List of users IDs associated with this key phrase
         */
        ::android::hardware::hidl_vec<uint32_t> users __attribute__ ((aligned(8)));
        /**
         * Locale - Java Locale style (e.g. en_US)
         */
        ::android::hardware::hidl_string locale __attribute__ ((aligned(8)));
        /**
         * Phrase text in UTF-8 format.
         */
        ::android::hardware::hidl_string text __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase, id) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase, recognitionModes) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase, users) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase, locale) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase, text) == 40, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase) == 8, "wrong alignment");

    /**
     * Specialized sound model for key phrase detection.
     * Proprietary representation of key phrases in binary data must match
     * information indicated by phrases field
     */
    struct PhraseSoundModel final {
        /**
         * Common part of sound model descriptor
         */
        ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel common __attribute__ ((aligned(8)));
        /**
         * List of descriptors for key phrases supported by this sound model
         */
        ::android::hardware::hidl_vec<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase> phrases __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel, common) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel, phrases) == 56, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel) == 72, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel) == 8, "wrong alignment");

    /**
     * Configuration for sound trigger capture session passed to
     * startRecognition() method
     */
    struct RecognitionConfig final {
        /**
         * IO handle that will be used for capture. N/A if captureRequested
         * is false
         */
        int32_t captureHandle __attribute__ ((aligned(4)));
        /**
         * Input device requested for detection capture
         */
        ::android::hardware::audio::common::V2_0::AudioDevice captureDevice __attribute__ ((aligned(4)));
        /**
         * Capture and buffer audio for this recognition instance
         */
        bool captureRequested __attribute__ ((aligned(1)));
        /**
         * Configuration for each key phrase
         */
        ::android::hardware::hidl_vec<::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra> phrases __attribute__ ((aligned(8)));
        /**
         * Opaque capture configuration data transparent to the framework
         */
        ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig, captureHandle) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig, captureDevice) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig, captureRequested) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig, phrases) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig, data) == 32, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getProperties
     */
    using getProperties_cb = std::function<void(int32_t retval, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& properties)>;
    /**
     * Retrieve implementation properties.
     * @return retval Operation completion status: 0 in case of success,
     *                -ENODEV in case of initialization error.
     * @return properties A Properties structure containing implementation
     *                    description and capabilities.
     */
    virtual ::android::hardware::Return<void> getProperties(getProperties_cb _hidl_cb) = 0;

    /**
     * Return callback for loadSoundModel
     */
    using loadSoundModel_cb = std::function<void(int32_t retval, int32_t modelHandle)>;
    /**
     * Load a sound model. Once loaded, recognition of this model can be
     * started and stopped. Only one active recognition per model at a time.
     * The SoundTrigger service must handle concurrent recognition requests by
     * different users/applications on the same model.
     * The implementation returns a unique handle used by other functions
     * (unloadSoundModel(), startRecognition(), etc...
     * @param soundModel A SoundModel structure describing the sound model to
     *                   load.
     * @param callback The callback interface on which the soundmodelCallback()
     *                 method will be called upon completion.
     * @param cookie The value of the cookie argument passed to the completion
     *               callback. This unique context information is assigned and
     *               used only by the framework.
     * @return retval Operation completion status: 0 in case of success,
     *                -EINVAL in case of invalid sound model (e.g 0 data size),
     *                -ENOSYS in case of invalid operation (e.g max number of
     *                models exceeded),
     *                -ENOMEM in case of memory allocation failure,
     *                -ENODEV in case of initialization error.
     * @return modelHandle A unique handle assigned by the HAL for use by the
     *                framework when controlling activity for this sound model.
     */
    virtual ::android::hardware::Return<void> loadSoundModel(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie, loadSoundModel_cb _hidl_cb) = 0;

    /**
     * Return callback for loadPhraseSoundModel
     */
    using loadPhraseSoundModel_cb = std::function<void(int32_t retval, int32_t modelHandle)>;
    /**
     * Load a key phrase sound model. Once loaded, recognition of this model can
     * be started and stopped. Only one active recognition per model at a time.
     * The SoundTrigger service must handle concurrent recognition requests by
     * different users/applications on the same model.
     * The implementation returns a unique handle used by other functions
     * (unloadSoundModel(), startRecognition(), etc...
     * @param soundModel A PhraseSoundModel structure describing the sound model
     *                   to load.
     * @param callback The callback interface on which the soundmodelCallback()
     *                 method will be called upon completion.
     * @param cookie The value of the cookie argument passed to the completion
     *               callback. This unique context information is assigned and
     *               used only by the framework.
     * @return retval Operation completion status: 0 in case of success,
     *                -EINVAL in case of invalid sound model (e.g 0 data size),
     *                -ENOSYS in case of invalid operation (e.g max number of
     *                models exceeded),
     *                -ENOMEM in case of memory allocation failure,
     *                -ENODEV in case of initialization error.
     * @return modelHandle A unique handle assigned by the HAL for use by the
     *                framework when controlling activity for this sound model.
     */
    virtual ::android::hardware::Return<void> loadPhraseSoundModel(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie, loadPhraseSoundModel_cb _hidl_cb) = 0;

    /**
     * Unload a sound model. A sound model may be unloaded to make room for a
     * new one to overcome implementation limitations.
     * @param modelHandle the handle of the sound model to unload
     * @return retval Operation completion status: 0 in case of success,
     *                -ENOSYS if the model is not loaded,
     *                -ENODEV in case of initialization error.
     */
    virtual ::android::hardware::Return<int32_t> unloadSoundModel(int32_t modelHandle) = 0;

    /**
     * Start recognition on a given model. Only one recognition active
     * at a time per model. Once recognition succeeds of fails, the callback
     * is called.
     * @param modelHandle the handle of the sound model to use for recognition
     * @param config A RecognitionConfig structure containing attributes of the
     *               recognition to perform
     * @param callback The callback interface on which the recognitionCallback()
     *                 method must be called upon recognition.
     * @param cookie The value of the cookie argument passed to the recognition
     *               callback. This unique context information is assigned and
     *               used only by the framework.
     * @return retval Operation completion status: 0 in case of success,
     *                -EINVAL in case of invalid recognition attributes,
     *                -ENOSYS in case of invalid model handle,
     *                -ENOMEM in case of memory allocation failure,
     *                -ENODEV in case of initialization error.
     */
    virtual ::android::hardware::Return<int32_t> startRecognition(int32_t modelHandle, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& config, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie) = 0;

    /**
     * Stop recognition on a given model.
     * The implementation must not call the recognition callback when stopped
     * via this method.
     * @param modelHandle The handle of the sound model to use for recognition
     * @return retval Operation completion status: 0 in case of success,
     *                -ENOSYS in case of invalid model handle,
     *                -ENODEV in case of initialization error.
     */
    virtual ::android::hardware::Return<int32_t> stopRecognition(int32_t modelHandle) = 0;

    /**
     * Stop recognition on all models.
     * @return retval Operation completion status: 0 in case of success,
     *                -ENODEV in case of initialization error.
     */
    virtual ::android::hardware::Return<int32_t> stopAllRecognitions() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw>> castFrom(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISoundTriggerHw> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHw> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHw> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISoundTriggerHw> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISoundTriggerHw> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHw> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHw> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISoundTriggerHw> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".implementor = ";
    os += ::android::hardware::toString(o.implementor);
    os += ", .description = ";
    os += ::android::hardware::toString(o.description);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .uuid = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.uuid);
    os += ", .maxSoundModels = ";
    os += ::android::hardware::toString(o.maxSoundModels);
    os += ", .maxKeyPhrases = ";
    os += ::android::hardware::toString(o.maxKeyPhrases);
    os += ", .maxUsers = ";
    os += ::android::hardware::toString(o.maxUsers);
    os += ", .recognitionModes = ";
    os += ::android::hardware::toString(o.recognitionModes);
    os += ", .captureTransition = ";
    os += ::android::hardware::toString(o.captureTransition);
    os += ", .maxBufferMs = ";
    os += ::android::hardware::toString(o.maxBufferMs);
    os += ", .concurrentCapture = ";
    os += ::android::hardware::toString(o.concurrentCapture);
    os += ", .triggerInEvent = ";
    os += ::android::hardware::toString(o.triggerInEvent);
    os += ", .powerConsumptionMw = ";
    os += ::android::hardware::toString(o.powerConsumptionMw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& rhs) {
    if (lhs.implementor != rhs.implementor) {
        return false;
    }
    if (lhs.description != rhs.description) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.uuid != rhs.uuid) {
        return false;
    }
    if (lhs.maxSoundModels != rhs.maxSoundModels) {
        return false;
    }
    if (lhs.maxKeyPhrases != rhs.maxKeyPhrases) {
        return false;
    }
    if (lhs.maxUsers != rhs.maxUsers) {
        return false;
    }
    if (lhs.recognitionModes != rhs.recognitionModes) {
        return false;
    }
    if (lhs.captureTransition != rhs.captureTransition) {
        return false;
    }
    if (lhs.maxBufferMs != rhs.maxBufferMs) {
        return false;
    }
    if (lhs.concurrentCapture != rhs.concurrentCapture) {
        return false;
    }
    if (lhs.triggerInEvent != rhs.triggerInEvent) {
        return false;
    }
    if (lhs.powerConsumptionMw != rhs.powerConsumptionMw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Properties& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.type);
    os += ", .uuid = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.uuid);
    os += ", .vendorUuid = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.vendorUuid);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.uuid != rhs.uuid) {
        return false;
    }
    if (lhs.vendorUuid != rhs.vendorUuid) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .recognitionModes = ";
    os += ::android::hardware::toString(o.recognitionModes);
    os += ", .users = ";
    os += ::android::hardware::toString(o.users);
    os += ", .locale = ";
    os += ::android::hardware::toString(o.locale);
    os += ", .text = ";
    os += ::android::hardware::toString(o.text);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.recognitionModes != rhs.recognitionModes) {
        return false;
    }
    if (lhs.users != rhs.users) {
        return false;
    }
    if (lhs.locale != rhs.locale) {
        return false;
    }
    if (lhs.text != rhs.text) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::Phrase& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".common = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.common);
    os += ", .phrases = ";
    os += ::android::hardware::toString(o.phrases);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& rhs) {
    if (lhs.common != rhs.common) {
        return false;
    }
    if (lhs.phrases != rhs.phrases) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".captureHandle = ";
    os += ::android::hardware::toString(o.captureHandle);
    os += ", .captureDevice = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.captureDevice);
    os += ", .captureRequested = ";
    os += ::android::hardware::toString(o.captureRequested);
    os += ", .phrases = ";
    os += ::android::hardware::toString(o.phrases);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& rhs) {
    if (lhs.captureHandle != rhs.captureHandle) {
        return false;
    }
    if (lhs.captureDevice != rhs.captureDevice) {
        return false;
    }
    if (lhs.captureRequested != rhs.captureRequested) {
        return false;
    }
    if (lhs.phrases != rhs.phrases) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHw>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHW_H
