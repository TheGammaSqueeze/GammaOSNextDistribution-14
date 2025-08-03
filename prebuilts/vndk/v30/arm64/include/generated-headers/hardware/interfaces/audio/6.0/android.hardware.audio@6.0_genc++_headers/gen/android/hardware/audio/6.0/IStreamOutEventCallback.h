#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_ISTREAMOUTEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_ISTREAMOUTEVENTCALLBACK_H

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

/**
 * Asynchronous stream out event callback interface. The interface provides
 * a way for the HAL to notify platform when there are changes, e.g. codec
 * format change, from the lower layer.
 */
struct IStreamOutEventCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.audio@6.0::IStreamOutEventCallback"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Codec format changed.
     *
     * onCodecFormatChanged returns an AudioMetadata object in read-only ByteString format.
     * It represents the most recent codec format decoded by a HW audio decoder.
     *
     * Codec format is an optional message from HW audio decoders. It serves to
     * notify the application about the codec format and audio objects contained
     * within the compressed audio stream for control, informational,
     * and display purposes.
     *
     * audioMetadata ByteString is convertible to an AudioMetadata object through
     * both a C++ and a C API present in Metadata.h [1], or through a Java API present
     * in AudioMetadata.java [2].
     *
     * The ByteString format is a stable format used for parcelling (marshalling) across
     * JNI, AIDL, and HIDL interfaces.  The test for R compatibility for native marshalling
     * is TEST(metadata_tests, compatibility_R) [3].  The test for R compatibility for JNI
     * marshalling is android.media.cts.AudioMetadataTest#testCompatibilityR [4].
     *
     * R (audio HAL 6.0) defined keys are as follows [2]:
     * "bitrate", int32
     * "channel-mask", int32
     * "mime", string
     * "sample-rate", int32
     * "bit-width", int32
     * "has-atmos", int32
     * "audio-encoding", int32
     *
     * Parceling Format:
     * All values are native endian order. [1]
     *
     * using type_size_t = uint32_t;
     * using index_size_t = uint32_t;
     * using datum_size_t = uint32_t;
     *
     * Permitted type indexes are
     * TYPE_NONE = 0, // Reserved
     * TYPE_INT32 = 1,
     * TYPE_INT64 = 2,
     * TYPE_FLOAT = 3,
     * TYPE_DOUBLE = 4,
     * TYPE_STRING = 5,
     * TYPE_DATA = 6,  // A data table of <String, Datum>
     *
     * Datum = {
     *           (type_size_t)  Type (the type index from type_as_value<T>.)
     *           (datum_size_t) Size (size of the Payload)
     *           (byte string)  Payload<Type>
     *         }
     *
     * The data is specified in native endian order.
     * Since the size of the Payload is always present, unknown types may be skipped.
     *
     * Payload<Fixed-size Primitive_Value>
     * [ sizeof(Primitive_Value) in raw bytes ]
     *
     * Example of Payload<Int32> of 123:
     * Payload<Int32>
     * [ value of 123                   ] =  0x7b 0x00 0x00 0x00       123
     *
     * Payload<String>
     * [ (index_size_t) length, not including zero terminator.]
     * [ (length) raw bytes ]
     *
     * Example of Payload<String> of std::string("hi"):
     * [ (index_size_t) length          ] = 0x02 0x00 0x00 0x00        2 strlen("hi")
     * [ raw bytes "hi"                 ] = 0x68 0x69                  "hi"
     *
     * Payload<Data>
     * [ (index_size_t) entries ]
     * [ raw bytes   (entry 1) Key   (Payload<String>)
     *                         Value (Datum)
     *                ...  (until #entries) ]
     *
     * Example of Payload<Data> of {{"hello", "world"},
     *                              {"value", (int32_t)1000}};
     * [ (index_size_t) #entries        ] = 0x02 0x00 0x00 0x00        2 entries
     *    Key (Payload<String>)
     *    [ index_size_t length         ] = 0x05 0x00 0x00 0x00        5 strlen("hello")
     *    [ raw bytes "hello"           ] = 0x68 0x65 0x6c 0x6c 0x6f   "hello"
     *    Value (Datum)
     *    [ (type_size_t) type          ] = 0x05 0x00 0x00 0x00        5 (TYPE_STRING)
     *    [ (datum_size_t) size         ] = 0x09 0x00 0x00 0x00        sizeof(index_size_t) +
     *                                                                 strlen("world")
     *       Payload<String>
     *       [ (index_size_t) length    ] = 0x05 0x00 0x00 0x00        5 strlen("world")
     *       [ raw bytes "world"        ] = 0x77 0x6f 0x72 0x6c 0x64   "world"
     *    Key (Payload<String>)
     *    [ index_size_t length         ] = 0x05 0x00 0x00 0x00        5 strlen("value")
     *    [ raw bytes "value"           ] = 0x76 0x61 0x6c 0x75 0x65   "value"
     *    Value (Datum)
     *    [ (type_size_t) type          ] = 0x01 0x00 0x00 0x00        1 (TYPE_INT32)
     *    [ (datum_size_t) size         ] = 0x04 0x00 0x00 0x00        4 sizeof(int32_t)
     *        Payload<Int32>
     *        [ raw bytes 1000          ] = 0xe8 0x03 0x00 0x00        1000
     *
     * The contents of audioMetadata is a Payload<Data>.
     * An implementation dependent detail is that the Keys are always
     * stored sorted, so the byte string representation generated is unique.
     *
     * Vendor keys are allowed for informational and debugging purposes.
     * Vendor keys should consist of the vendor company name followed
     * by a dot; for example, "vendorCompany.someVolume" [2].
     *
     * [1] system/media/audio_utils/include/audio_utils/Metadata.h
     * [2] frameworks/base/media/java/android/media/AudioMetadata.java
     * [3] system/media/audio_utils/tests/metadata_tests.cpp
     * [4] cts/tests/tests/media/src/android/media/cts/AudioMetadataTest.java
     *
     * @param audioMetadata is a buffer containing decoded format changes
     *     reported by codec. The buffer contains data that can be transformed
     *     to audio metadata, which is a C++ object based map.
     */
    virtual ::android::hardware::Return<void> onCodecFormatChanged(const ::android::hardware::hidl_vec<uint8_t>& audioMetadata) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V6_0::IStreamOutEventCallback>> castFrom(const ::android::sp<::android::hardware::audio::V6_0::IStreamOutEventCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::audio::V6_0::IStreamOutEventCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IStreamOutEventCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStreamOutEventCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IStreamOutEventCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStreamOutEventCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IStreamOutEventCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStreamOutEventCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IStreamOutEventCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IStreamOutEventCallback> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::audio::V6_0::IStreamOutEventCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::audio::V6_0::IStreamOutEventCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::audio::V6_0::IStreamOutEventCallback::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_ISTREAMOUTEVENTCALLBACK_H
