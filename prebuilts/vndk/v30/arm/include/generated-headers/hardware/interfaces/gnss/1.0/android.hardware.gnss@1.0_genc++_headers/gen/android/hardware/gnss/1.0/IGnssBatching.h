#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSBATCHING_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSBATCHING_H

#include <android/hardware/gnss/1.0/IGnssBatchingCallback.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

/**
 * Extended interface for GNSS Batching support.
 *
 * If this interface is supported, this batching request must be able to run in
 * parallel with, or without, non-batched location requested by the
 * IGnss start() & stop() - i.e. both requests must be handled independently,
 * and not interfere with each other.
 *
 * For example, if a 1Hz continuous output is underway on the IGnssCallback,
 * due to an IGnss start() operation,
 * and then a IGnssBatching start() is called for a location every 10
 * seconds, the newly added batching request must not disrupt the 1Hz
 * continuous location output on the IGnssCallback.
 *
 * As with GNSS Location outputs, source of location must be GNSS satellite
 * measurements, optionally using interial and baro sensors to improve
 * relative motion filtering. No additional absolute positioning information,
 * such as WiFi derived location, may be mixed with the GNSS information.
 */
struct IGnssBatching : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IGnssBatching"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class Flag : uint8_t;
    struct Options;

    /**
     * Enum which holds the bit masks for batching control.
     */
    enum class Flag : uint8_t {
        /**
         * If this flag is set, the hardware implementation
         * must wake up the application processor when the FIFO is full, and
         * call IGnssBatchingCallback to return the locations.
         *
         * If the flag is not set, the hardware implementation must drop
         * the oldest data when the FIFO is full.
         */
        WAKEUP_ON_FIFO_FULL = 1 /* 0x01 */,
    };

    struct Options final {
        /**
         * Time interval between samples in the location batch, in nano
         * seconds.
         */
        int64_t periodNanos __attribute__ ((aligned(8)));
        /**
         * Flags controlling how batching should behave.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssBatching::Flag> flags __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssBatching::Options, periodNanos) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssBatching::Options, flags) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssBatching::Options) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssBatching::Options) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Opens the interface and provides the callback routines
     * to the implementation of this interface.
     *
     * @param callback Callback interface for IGnssBatching.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> init(const ::android::sp<::android::hardware::gnss::V1_0::IGnssBatchingCallback>& callback) = 0;

    /**
     * Return the batch size (in number of GnssLocation objects)
     * available in this hardware implementation.
     *
     * If the available size is variable, for example, based on other operations
     * consuming memory, this is the minimum size guaranteed to be available
     * for batching operations.
     *
     * This may, for example, be used by the upper layer, to decide on the
     * batching interval and whether the AP should be woken up or not.
     *
     * @return batchSize number of location objects supported per batch
     */
    virtual ::android::hardware::Return<uint16_t> getBatchSize() = 0;

    /**
     * Start batching locations. This API is primarily used when the AP is
     * asleep and the device can batch locations in the hardware.
     *
     * IGnssBatchingCallback is used to return the locations.
     *
     * When the buffer is full and WAKEUP_ON_FIFO_FULL is used,
     * IGnssBatchingCallback must be called to return the locations.
     *
     * When the buffer is full and WAKEUP_ON_FIFO_FULL is not set,
     * the oldest location object is dropped. In this case the AP must not be
     * woken up. The AP would then generally be responsible for using
     * flushBatchedLocation to explicitly ask for the location as needed,
     * to avoid it being dropped.
     *
     * @param options See struct Options definition.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> start(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& options) = 0;

    /**
     * Retrieve all batched locations currently stored.
     *
     * IGnssBatchingCallback is used to return the location.
     *
     * IGnssBatchingCallback must be called in response, even if there are
     * no locations to flush (in which case the Location vector must be empty).
     *
     * Subsequent calls to flushBatchedLocation
     * must not return any of the locations returned in this call.
     */
    virtual ::android::hardware::Return<void> flush() = 0;

    /**
     * Stop batching.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> stop() = 0;

    /**
     * Closes the interface. If any batch operations are in progress,
     * they must be stopped.  If any locations are in the hardware batch, they
     * must be deleted (and not sent via callback.)
     *
     * init() may be called again, after this, if the interface is to be restored
     */
    virtual ::android::hardware::Return<void> cleanup() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssBatching> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssBatching> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssBatching> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssBatching> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssBatching> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssBatching> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssBatching> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssBatching> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssBatching::Flag o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssBatching::Flag o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssBatching::Flag lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssBatching::Flag lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssBatching::Flag lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssBatching::Flag lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssBatching::Flag e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Options& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Options& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssBatching::Flag>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssBatching::Flag> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssBatching::Flag::WAKEUP_ON_FIFO_FULL) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssBatching::Flag::WAKEUP_ON_FIFO_FULL)) {
        os += (first ? "" : " | ");
        os += "WAKEUP_ON_FIFO_FULL";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssBatching::Flag::WAKEUP_ON_FIFO_FULL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssBatching::Flag o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssBatching::Flag::WAKEUP_ON_FIFO_FULL) {
        return "WAKEUP_ON_FIFO_FULL";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssBatching::Flag o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".periodNanos = ";
    os += ::android::hardware::toString(o.periodNanos);
    os += ", .flags = ";
    os += ::android::hardware::gnss::V1_0::toString<::android::hardware::gnss::V1_0::IGnssBatching::Flag>(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Options& rhs) {
    if (lhs.periodNanos != rhs.periodNanos) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssBatching::Options& lhs, const ::android::hardware::gnss::V1_0::IGnssBatching::Options& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IGnssBatching::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace gnss
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssBatching::Flag, 1> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssBatching::Flag> = {
    ::android::hardware::gnss::V1_0::IGnssBatching::Flag::WAKEUP_ON_FIFO_FULL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSBATCHING_H
