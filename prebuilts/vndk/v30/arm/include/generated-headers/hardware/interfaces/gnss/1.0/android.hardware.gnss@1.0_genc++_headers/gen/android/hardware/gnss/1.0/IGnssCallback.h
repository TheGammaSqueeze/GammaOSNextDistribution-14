#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSCALLBACK_H

#include <android/hardware/gnss/1.0/types.h>
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
 * The interface is required for the HAL to communicate certain information
 * like status and location info back to the platform, the platform implements
 * the interfaces and passes a handle to the HAL.
 */
struct IGnssCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IGnssCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class Capabilities : uint32_t;
    enum class GnssStatusValue : uint8_t;
    enum class GnssSvFlags : uint8_t;
    struct GnssSvInfo;
    struct GnssSvStatus;
    struct GnssSystemInfo;

    /**
     * Flags for the gnssSetCapabilities callback.
     */
    enum class Capabilities : uint32_t {
        /**
         * GNSS HAL schedules fixes for RECURRENCE_PERIODIC mode.
         * If this is not set, then the framework will use 1000ms for
         * minInterval and will call start() and stop() to schedule the GNSS.
         */
        SCHEDULING = 1u /* 1 << 0 */,
        /**
         * GNSS supports MS-Based AGNSS mode
         */
        MSB = 2u /* 1 << 1 */,
        /**
         * GNSS supports MS-Assisted AGNSS mode
         */
        MSA = 4u /* 1 << 2 */,
        /**
         * GNSS supports single-shot fixes
         */
        SINGLE_SHOT = 8u /* 1 << 3 */,
        /**
         * GNSS supports on demand time injection
         */
        ON_DEMAND_TIME = 16u /* 1 << 4 */,
        /**
         * GNSS supports Geofencing
         */
        GEOFENCING = 32u /* 1 << 5 */,
        /**
         * GNSS supports Measurements for at least GPS.
         */
        MEASUREMENTS = 64u /* 1 << 6 */,
        /**
         * GNSS supports Navigation Messages
         */
        NAV_MESSAGES = 128u /* 1 << 7 */,
    };

    /**
     * GNSS status event values.
     */
    enum class GnssStatusValue : uint8_t {
        /**
         * GNSS status unknown.
         */
        NONE = 0,
        /**
         * GNSS has begun navigating.
         */
        SESSION_BEGIN = 1,
        /**
         * GNSS has stopped navigating.
         */
        SESSION_END = 2,
        /**
         * GNSS has powered on but is not navigating.
         */
        ENGINE_ON = 3,
        /**
         * GNSS is powered off.
         */
        ENGINE_OFF = 4,
    };

    /**
     * Flags that indicate information about the satellite
     */
    enum class GnssSvFlags : uint8_t {
        NONE = 0,
        HAS_EPHEMERIS_DATA = 1 /* 1 << 0 */,
        HAS_ALMANAC_DATA = 2 /* 1 << 1 */,
        USED_IN_FIX = 4 /* 1 << 2 */,
        HAS_CARRIER_FREQUENCY = 8 /* 1 << 3 */,
    };

    struct GnssSvInfo final {
        /**
         * Pseudo-random or satellite ID number for the satellite, a.k.a. Space Vehicle (SV), or
         * FCN/OSN number for Glonass. The distinction is made by looking at constellation field.
         * Values must be in the range of:
         *
         * - GNSS:    1-32
         * - SBAS:    120-151, 183-192
         * - GLONASS: 1-24, the orbital slot number (OSN), if known.  Or, if not:
         *            93-106, the frequency channel number (FCN) (-7 to +6) offset by
         *            + 100
         *            i.e. report an FCN of -7 as 93, FCN of 0 as 100, and FCN of +6
         *            as 106.
         * - QZSS:    193-200
         * - Galileo: 1-36
         * - Beidou:  1-37
         * - IRNSS:   1-14
         */
        int16_t svid __attribute__ ((aligned(2)));
        /**
         * Defines the constellation of the given SV.
         */
        ::android::hardware::gnss::V1_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
        /**
         * Carrier-to-noise density in dB-Hz, typically in the range [0, 63].
         * It contains the measured C/N0 value for the signal at the antenna port.
         *
         * This is a mandatory value.
         */
        float cN0Dbhz __attribute__ ((aligned(4)));
        /**
         * Elevation of SV in degrees.
         */
        float elevationDegrees __attribute__ ((aligned(4)));
        /**
         * Azimuth of SV in degrees.
         */
        float azimuthDegrees __attribute__ ((aligned(4)));
        /**
         * Carrier frequency of the signal tracked, for example it can be the
         * GPS central frequency for L1 = 1575.45 MHz, or L2 = 1227.60 MHz, L5 =
         * 1176.45 MHz, varying GLO channels, etc. If the field is zero, it is
         * the primary common use central frequency, e.g. L1 = 1575.45 MHz for
         * GPS.
         *
         * For an L1, L5 receiver tracking a satellite on L1 and L5 at the same
         * time, two GnssSvInfo structs must be reported for this same
         * satellite, in one of the structs, all the values related
         * to L1 must be filled, and in the other all of the values related to
         * L5 must be filled.
         *
         * If the data is available, svFlag must contain HAS_CARRIER_FREQUENCY.
         */
        float carrierFrequencyHz __attribute__ ((aligned(4)));
        /**
         * Contains additional data about the given SV.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags> svFlag __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, svid) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, constellation) == 2, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, cN0Dbhz) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, elevationDegrees) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, azimuthDegrees) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, carrierFrequencyHz) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, svFlag) == 20, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo) == 4, "wrong alignment");

    /**
     * Represents SV status.
     */
    struct GnssSvStatus final {
        /**
         * Number of GNSS SVs currently visible, refers to the SVs stored in sv_list
         */
        uint32_t numSvs __attribute__ ((aligned(4)));
        /**
         * Pointer to an array of SVs information for all GNSS constellations,
         * except GNSS, which is reported using svList
         */
        ::android::hardware::hidl_array<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo, 64 /* GnssMax:SVS_COUNT */> gnssSvList __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus, numSvs) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus, gnssSvList) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus) == 1540, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus) == 4, "wrong alignment");

    /**
     * Provides information about how new the underlying GPS/GNSS hardware and
     * software is.
     *
     * This information will be available for Android Test Applications. If a GNSS
     * HAL does not provide this information, it will be considered "2015 or
     * earlier".
     *
     * If a GNSS HAL does provide this information, then newer years will need to
     * meet newer CTS standards. E.g. if the date are 2016 or above, then N+ level
     * GnssMeasurement support will be verified.
     */
    struct GnssSystemInfo final {
        /**
         * year in which the last update was made to the underlying hardware/firmware
         * used to capture GNSS signals, e.g. 2016
         */
        uint16_t yearOfHw __attribute__ ((aligned(2)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo, yearOfHw) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo) == 2, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo) == 2, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Called when a GNSS location is available.
     *
     * @param location Location information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssLocationCb(const ::android::hardware::gnss::V1_0::GnssLocation& location) = 0;

    /**
     * Called to communicate the status of the GNSS engine.
     *
     * @param status Status information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssStatusCb(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue status) = 0;

    /**
     * @param svInfo SV status information from HAL.
     */
    virtual ::android::hardware::Return<void> gnssSvStatusCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& svInfo) = 0;

    /**
     * Called when NMEA data is available.
     * Callback for reporting NMEA sentences.
     *
     * @param timestamp Marks the instance of reporting.
     * @param nmea Follows standard NMEA 0183. Each sentence begins with a '$'
     * and ends with a carriage return/line feed sequence and can be no longer
     * than 80 characters of visible text (plus the line terminators). The data
     * is contained within this single line with data items separated by commas.
     * The data itself is just ascii text and may extend over multiple sentences
     * in certain specialized instances but is normally fully contained in one
     * variable length sentence. The data may vary in the amount of precision
     * contained in the message. For example time might be indicated to decimal
     * parts of a second or location may be shown with 3 or even 4 digits after
     * the decimal point. Programs that read the data must only use the commas
     * to determine the field boundaries and not depend on column positions.
     * There is a provision for a checksum at the end of each sentence which may
     * or may not be checked by the unit that reads the data. The checksum field
     * consists of a '*' and two hex digits representing an 8 bit exclusive OR
     * of all characters between, but not including, the '$' and '*'.
     */
    virtual ::android::hardware::Return<void> gnssNmeaCb(int64_t timestamp, const ::android::hardware::hidl_string& nmea) = 0;

    /**
     * Callback to inform framework of the GNSS engine's capabilities.
     *
     * @param capabilities Capability parameter is a bit field of
     * the Capabilities enum.
     */
    virtual ::android::hardware::Return<void> gnssSetCapabilitesCb(::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities> capabilities) = 0;

    /**
     * Callback utility for acquiring the GNSS wakelock. This can be used to prevent
     * the CPU from suspending while handling GNSS events.
     */
    virtual ::android::hardware::Return<void> gnssAcquireWakelockCb() = 0;

    /**
     * Callback utility for releasing the GNSS wakelock.
     */
    virtual ::android::hardware::Return<void> gnssReleaseWakelockCb() = 0;

    /**
     * Callback for requesting NTP time
     */
    virtual ::android::hardware::Return<void> gnssRequestTimeCb() = 0;

    /**
     * Callback to inform framework of the engine's hardware version information.
     *
     * @param info GnssSystemInfo about the GPS/GNSS hardware.
     */
    virtual ::android::hardware::Return<void> gnssSetSystemInfoCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& info) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SCHEDULING) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SCHEDULING)) {
        os += (first ? "" : " | ");
        os += "SCHEDULING";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SCHEDULING;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSB) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSB)) {
        os += (first ? "" : " | ");
        os += "MSB";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSB;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSA) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSA)) {
        os += (first ? "" : " | ");
        os += "MSA";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSA;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SINGLE_SHOT) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SINGLE_SHOT)) {
        os += (first ? "" : " | ");
        os += "SINGLE_SHOT";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SINGLE_SHOT;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::ON_DEMAND_TIME) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::ON_DEMAND_TIME)) {
        os += (first ? "" : " | ");
        os += "ON_DEMAND_TIME";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::ON_DEMAND_TIME;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::GEOFENCING) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::GEOFENCING)) {
        os += (first ? "" : " | ");
        os += "GEOFENCING";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::GEOFENCING;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MEASUREMENTS) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MEASUREMENTS)) {
        os += (first ? "" : " | ");
        os += "MEASUREMENTS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MEASUREMENTS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::NAV_MESSAGES) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::NAV_MESSAGES)) {
        os += (first ? "" : " | ");
        os += "NAV_MESSAGES";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::NAV_MESSAGES;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SCHEDULING) {
        return "SCHEDULING";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSB) {
        return "MSB";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSA) {
        return "MSA";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SINGLE_SHOT) {
        return "SINGLE_SHOT";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::ON_DEMAND_TIME) {
        return "ON_DEMAND_TIME";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::GEOFENCING) {
        return "GEOFENCING";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MEASUREMENTS) {
        return "MEASUREMENTS";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::NAV_MESSAGES) {
        return "NAV_MESSAGES";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::Capabilities o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::NONE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::NONE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_BEGIN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_BEGIN)) {
        os += (first ? "" : " | ");
        os += "SESSION_BEGIN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_BEGIN;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_END) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_END)) {
        os += (first ? "" : " | ");
        os += "SESSION_END";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_END;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_ON) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_ON)) {
        os += (first ? "" : " | ");
        os += "ENGINE_ON";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_ON;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_OFF) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_OFF)) {
        os += (first ? "" : " | ");
        os += "ENGINE_OFF";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_OFF;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_BEGIN) {
        return "SESSION_BEGIN";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_END) {
        return "SESSION_END";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_ON) {
        return "ENGINE_ON";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_OFF) {
        return "ENGINE_OFF";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::NONE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::NONE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA)) {
        os += (first ? "" : " | ");
        os += "HAS_EPHEMERIS_DATA";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA)) {
        os += (first ? "" : " | ");
        os += "HAS_ALMANAC_DATA";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::USED_IN_FIX) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::USED_IN_FIX)) {
        os += (first ? "" : " | ");
        os += "USED_IN_FIX";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::USED_IN_FIX;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "HAS_CARRIER_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA) {
        return "HAS_EPHEMERIS_DATA";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA) {
        return "HAS_ALMANAC_DATA";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::USED_IN_FIX) {
        return "USED_IN_FIX";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY) {
        return "HAS_CARRIER_FREQUENCY";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".svid = ";
    os += ::android::hardware::toString(o.svid);
    os += ", .constellation = ";
    os += ::android::hardware::gnss::V1_0::toString(o.constellation);
    os += ", .cN0Dbhz = ";
    os += ::android::hardware::toString(o.cN0Dbhz);
    os += ", .elevationDegrees = ";
    os += ::android::hardware::toString(o.elevationDegrees);
    os += ", .azimuthDegrees = ";
    os += ::android::hardware::toString(o.azimuthDegrees);
    os += ", .carrierFrequencyHz = ";
    os += ::android::hardware::toString(o.carrierFrequencyHz);
    os += ", .svFlag = ";
    os += ::android::hardware::gnss::V1_0::toString<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags>(o.svFlag);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& rhs) {
    if (lhs.svid != rhs.svid) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    if (lhs.cN0Dbhz != rhs.cN0Dbhz) {
        return false;
    }
    if (lhs.elevationDegrees != rhs.elevationDegrees) {
        return false;
    }
    if (lhs.azimuthDegrees != rhs.azimuthDegrees) {
        return false;
    }
    if (lhs.carrierFrequencyHz != rhs.carrierFrequencyHz) {
        return false;
    }
    if (lhs.svFlag != rhs.svFlag) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".numSvs = ";
    os += ::android::hardware::toString(o.numSvs);
    os += ", .gnssSvList = ";
    os += ::android::hardware::toString(o.gnssSvList);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& rhs) {
    if (lhs.numSvs != rhs.numSvs) {
        return false;
    }
    if (lhs.gnssSvList != rhs.gnssSvList) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".yearOfHw = ";
    os += ::android::hardware::toString(o.yearOfHw);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& rhs) {
    if (lhs.yearOfHw != rhs.yearOfHw) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& lhs, const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IGnssCallback::descriptor;
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities, 8> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities> = {
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SCHEDULING,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSB,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MSA,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::SINGLE_SHOT,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::ON_DEMAND_TIME,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::GEOFENCING,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::MEASUREMENTS,
    ::android::hardware::gnss::V1_0::IGnssCallback::Capabilities::NAV_MESSAGES,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue, 5> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue> = {
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::NONE,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_BEGIN,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::SESSION_END,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_ON,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue::ENGINE_OFF,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags, 5> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags> = {
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::NONE,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_EPHEMERIS_DATA,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_ALMANAC_DATA,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::USED_IN_FIX,
    ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSCALLBACK_H
