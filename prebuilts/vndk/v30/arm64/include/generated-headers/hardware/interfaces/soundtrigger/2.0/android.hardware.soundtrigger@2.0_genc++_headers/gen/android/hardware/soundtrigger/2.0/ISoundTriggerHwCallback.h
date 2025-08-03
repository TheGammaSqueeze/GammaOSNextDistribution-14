#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHWCALLBACK_H

#include <android/hardware/audio/common/2.0/types.h>
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

struct ISoundTriggerHwCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.soundtrigger@2.0::ISoundTriggerHwCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class RecognitionStatus : uint32_t;
    enum class SoundModelStatus : uint32_t;
    struct RecognitionEvent;
    struct PhraseRecognitionEvent;
    struct ModelEvent;

    enum class RecognitionStatus : uint32_t {
        SUCCESS = 0u,
        ABORT = 1u,
        FAILURE = 2u,
    };

    enum class SoundModelStatus : uint32_t {
        UPDATED = 0u,
    };

    /**
     * Generic recognition event sent via recognition callback
     */
    struct RecognitionEvent final {
        /**
         * Recognition status e.g. SUCCESS
         */
        ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus status __attribute__ ((aligned(4)));
        /**
         * Sound model type for this event. e.g SoundModelType.TYPE_KEYPHRASE
         */
        ::android::hardware::soundtrigger::V2_0::SoundModelType type __attribute__ ((aligned(4)));
        /**
         * Handle of loaded sound model which triggered the event
         */
        int32_t model __attribute__ ((aligned(4)));
        /**
         * It is possible to capture audio from this
         *
         *
         * utterance buffered by the implementation
         */
        bool captureAvailable __attribute__ ((aligned(1)));
        /**
         * Audio session ID. framework use
         */
        int32_t captureSession __attribute__ ((aligned(4)));
        /**
         * Delay in ms between end of model detection and start of audio
         * available for capture. A negative value is possible
         * (e.g. if key phrase is also available for capture
         */
        int32_t captureDelayMs __attribute__ ((aligned(4)));
        /**
         * Duration in ms of audio captured before the start of the trigger.
         * 0 if none.
         */
        int32_t capturePreambleMs __attribute__ ((aligned(4)));
        /**
         * The opaque data is the capture of the trigger sound
         */
        bool triggerInData __attribute__ ((aligned(1)));
        /**
         * Audio format of either the trigger in event data or to use for
         * capture of the rest of the utterance
         */
        ::android::hardware::audio::common::V2_0::AudioConfig audioConfig __attribute__ ((aligned(8)));
        /**
         * Opaque event data
         */
        ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, status) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, type) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, model) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, captureAvailable) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, captureSession) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, captureDelayMs) == 20, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, capturePreambleMs) == 24, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, triggerInData) == 28, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, audioConfig) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent, data) == 104, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent) == 120, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent) == 8, "wrong alignment");

    /**
     * Specialized recognition event for key phrase recognitions
     */
    struct PhraseRecognitionEvent final {
        /**
         * Common part of the recognition event
         */
        ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent common __attribute__ ((aligned(8)));
        /**
         * List of descriptors for each recognized key phrase
         */
        ::android::hardware::hidl_vec<::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra> phraseExtras __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent, common) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent, phraseExtras) == 120, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent) == 136, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent) == 8, "wrong alignment");

    /**
     * Event sent via load sound model callback
     */
    struct ModelEvent final {
        /**
         * Sound model status e.g. SoundModelStatus.UPDATED
         */
        ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus status __attribute__ ((aligned(4)));
        /**
         * Loaded sound model that triggered the event
         */
        int32_t model __attribute__ ((aligned(4)));
        /**
         * Opaque event data, passed transparently by the framework
         */
        ::android::hardware::hidl_vec<uint8_t> data __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent, status) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent, model) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent, data) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent) == 8, "wrong alignment");

    typedef int32_t CallbackCookie;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Callback method called by the HAL when the sound recognition triggers
     * @param event A RecognitionEvent structure containing detailed results
     *              of the recognition triggered
     * @param cookie The cookie passed by the framework when recognition was
     *               started (see ISoundtriggerHw.startRecognition()
     */
    virtual ::android::hardware::Return<void> recognitionCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& event, int32_t cookie) = 0;

    /**
     * Callback method called by the HAL when the sound recognition triggers
     * for a key phrase sound model.
     * @param event A RecognitionEvent structure containing detailed results
     *              of the recognition triggered
     * @param cookie The cookie passed by the framework when recognition was
     *               started (see ISoundtriggerHw.startRecognition()
     */
    virtual ::android::hardware::Return<void> phraseRecognitionCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& event, int32_t cookie) = 0;

    /**
     * Callback method called by the HAL when the sound model loading completes
     * @param event A ModelEvent structure containing detailed results of the
     *              model loading operation
     * @param cookie The cookie passed by the framework when loading was
     *               initiated (see ISoundtriggerHw.loadSoundModel()
     */
    virtual ::android::hardware::Return<void> soundModelCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& event, int32_t cookie) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>> castFrom(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISoundTriggerHwCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHwCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHwCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISoundTriggerHwCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISoundTriggerHwCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHwCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISoundTriggerHwCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISoundTriggerHwCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus o);
static inline void PrintTo(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& rhs);

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& o);
static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& rhs);
static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::SUCCESS) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::SUCCESS;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::ABORT) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::ABORT)) {
        os += (first ? "" : " | ");
        os += "ABORT";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::ABORT;
    }
    if ((o & ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::FAILURE) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::FAILURE)) {
        os += (first ? "" : " | ");
        os += "FAILURE";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::ABORT) {
        return "ABORT";
    }
    if (o == ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::FAILURE) {
        return "FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus::UPDATED) == static_cast<uint32_t>(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus::UPDATED)) {
        os += (first ? "" : " | ");
        os += "UPDATED";
        first = false;
        flipped |= ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus::UPDATED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus::UPDATED) {
        return "UPDATED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.status);
    os += ", .type = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.type);
    os += ", .model = ";
    os += ::android::hardware::toString(o.model);
    os += ", .captureAvailable = ";
    os += ::android::hardware::toString(o.captureAvailable);
    os += ", .captureSession = ";
    os += ::android::hardware::toString(o.captureSession);
    os += ", .captureDelayMs = ";
    os += ::android::hardware::toString(o.captureDelayMs);
    os += ", .capturePreambleMs = ";
    os += ::android::hardware::toString(o.capturePreambleMs);
    os += ", .triggerInData = ";
    os += ::android::hardware::toString(o.triggerInData);
    os += ", .audioConfig = ";
    os += ::android::hardware::audio::common::V2_0::toString(o.audioConfig);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.model != rhs.model) {
        return false;
    }
    if (lhs.captureAvailable != rhs.captureAvailable) {
        return false;
    }
    if (lhs.captureSession != rhs.captureSession) {
        return false;
    }
    if (lhs.captureDelayMs != rhs.captureDelayMs) {
        return false;
    }
    if (lhs.capturePreambleMs != rhs.capturePreambleMs) {
        return false;
    }
    if (lhs.triggerInData != rhs.triggerInData) {
        return false;
    }
    if (lhs.audioConfig != rhs.audioConfig) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".common = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.common);
    os += ", .phraseExtras = ";
    os += ::android::hardware::toString(o.phraseExtras);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& rhs) {
    if (lhs.common != rhs.common) {
        return false;
    }
    if (lhs.phraseExtras != rhs.phraseExtras) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::soundtrigger::V2_0::toString(o.status);
    os += ", .model = ";
    os += ::android::hardware::toString(o.model);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.model != rhs.model) {
        return false;
    }
    if (lhs.data != rhs.data) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& lhs, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::descriptor;
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

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus, 3> hidl_enum_values<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus> = {
    ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::SUCCESS,
    ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::ABORT,
    ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionStatus::FAILURE,
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
template<> inline constexpr std::array<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus, 1> hidl_enum_values<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus> = {
    ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::SoundModelStatus::UPDATED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_ISOUNDTRIGGERHWCALLBACK_H
