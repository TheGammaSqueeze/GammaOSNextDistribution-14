#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSMEASUREMENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSMEASUREMENTCALLBACK_H

#include <android/hardware/gnss/1.0/IGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/IGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/types.h>
#include <android/hardware/gnss/2.1/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {

/**
 * The callback interface to report measurements from the HAL.
 */
struct IGnssMeasurementCallback : public ::android::hardware::gnss::V2_0::IGnssMeasurementCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@2.1::IGnssMeasurementCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class GnssMeasurementFlags : uint32_t;
    struct GnssMeasurement;
    struct GnssClock;
    struct GnssData;

    /**
     * Flags to indicate what fields in GnssMeasurement are valid.
     */
    enum class GnssMeasurementFlags : uint32_t {
        /**
         * A valid 'snr' is stored in the data structure.
         */
        HAS_SNR = 1u /* 1 << 0 */,
        /**
         * A valid 'carrier frequency' is stored in the data structure.
         */
        HAS_CARRIER_FREQUENCY = 512u /* 1 << 9 */,
        /**
         * A valid 'carrier cycles' is stored in the data structure.
         */
        HAS_CARRIER_CYCLES = 1024u /* 1 << 10 */,
        /**
         * A valid 'carrier phase' is stored in the data structure.
         */
        HAS_CARRIER_PHASE = 2048u /* 1 << 11 */,
        /**
         * A valid 'carrier phase uncertainty' is stored in the data structure.
         */
        HAS_CARRIER_PHASE_UNCERTAINTY = 4096u /* 1 << 12 */,
        /**
         * A valid automatic gain control is stored in the data structure.
         */
        HAS_AUTOMATIC_GAIN_CONTROL = 8192u /* 1 << 13 */,
        /**
         * A valid full inter-signal bias is stored in the data structure.
         */
        HAS_FULL_ISB = 65536u /* 1 << 16 */,
        /**
         * A valid full inter-signal bias uncertainty is stored in the data structure.
         */
        HAS_FULL_ISB_UNCERTAINTY = 131072u /* 1 << 17 */,
        /**
         * A valid satellite inter-signal bias is stored in the data structure.
         */
        HAS_SATELLITE_ISB = 262144u /* 1 << 18 */,
        /**
         * A valid satellite inter-signal bias uncertainty is stored in the data structure.
         */
        HAS_SATELLITE_ISB_UNCERTAINTY = 524288u /* 1 << 19 */,
    };

    /**
     * Extends a GNSS Measurement, adding basebandCN0DbHz, GnssMeasurementFlags,
     * receiverInterSignalBiasNs, receiverInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs
     * and satelliteInterSignalBiasUncertaintyNs.
     */
    struct GnssMeasurement final {
        /**
         * GNSS measurement information for a single satellite and frequency, as in the 2.0 version
         * of the HAL.
         *
         * In this version of the HAL, the field 'flags' in the v2_0.v1_1.v1_0 struct is deprecated,
         * and is no longer used by the framework. The GNSS measurement flags are instead reported
         * in @2.1::IGnssMeasurementCallback.GnssMeasurement.flags.
         *
         */
        ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement v2_0 __attribute__ ((aligned(8)));
        /**
         * A set of flags indicating the validity of the fields in this data
         * structure.
         *
         * Fields for which there is no corresponding flag must be filled in
         * with a valid value.  For convenience, these are marked as mandatory.
         *
         * Others fields may have invalid information in them, if not marked as
         * valid by the corresponding bit in flags.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags> flags __attribute__ ((aligned(4)));
        /**
         * The full inter-signal bias (ISB) in nanoseconds.
         *
         * This value is the sum of the estimated receiver-side and the space-segment-side
         * inter-system bias, inter-frequency bias and inter-code bias, including
         *
         * - Receiver inter-constellation bias (with respect to the constellation in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Receiver inter-frequency bias (with respect to the carrier frequency in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Receiver inter-code bias (with respect to the code type in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Master clock bias (e.g., GPS-GAL Time Offset (GGTO), GPS-UTC Time Offset
         *   (TauGps), BDS-GLO Time Offset (BGTO)) (with respect to the constellation in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Group delay (e.g., Total Group Delay (TGD))
         * - Satellite inter-frequency bias (GLO only) (with respect to the carrier frequency in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Satellite inter-code bias (e.g., Differential Code Bias (DCB)) (with respect to the
         *   code type in GnssClock.referenceSignalTypeForIsb)
         *
         * If a component of the above is already compensated in the provided
         * GnssMeasurement.receivedSvTimeInNs, then it must not be included in the reported full
         * ISB.
         *
         * The value does not include the inter-frequency Ionospheric bias.
         *
         * The full ISB of GnssClock.referenceSignalTypeForIsb is defined to be 0.0 nanoseconds.
         */
        double fullInterSignalBiasNs __attribute__ ((aligned(8)));
        /**
         * 1-sigma uncertainty associated with the full inter-signal bias in nanoseconds.
         */
        double fullInterSignalBiasUncertaintyNs __attribute__ ((aligned(8)));
        /**
         * The satellite inter-signal bias in nanoseconds.
         *
         * This value is the sum of the space-segment-side inter-system bias, inter-frequency bias
         * and inter-code bias, including
         *
         * - Master clock bias (e.g., GPS-GAL Time Offset (GGTO), GPS-UTC Time Offset
         *   (TauGps), BDS-GLO Time Offset (BGTO)) (with respect to the constellation in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Group delay (e.g., Total Group Delay (TGD))
         * - Satellite inter-frequency bias (GLO only) (with respect to the carrier frequency in
         *   GnssClock.referenceSignalTypeForIsb)
         * - Satellite inter-code bias (e.g., Differential Code Bias (DCB)) (with respect to the
         *   code type in GnssClock.referenceSignalTypeForIsb)
         *
         * The satellite ISB of GnssClock.referenceSignalTypeForIsb is defined to be 0.0
         * nanoseconds.
         */
        double satelliteInterSignalBiasNs __attribute__ ((aligned(8)));
        /**
         * 1-sigma uncertainty associated with the satellite inter-signal bias in nanoseconds.
         */
        double satelliteInterSignalBiasUncertaintyNs __attribute__ ((aligned(8)));
        /**
         * Baseband Carrier-to-noise density in dB-Hz, typically in the range [0, 63]. It contains
         * the measured C/N0 value for the signal measured at the baseband.
         *
         * This is typically a few dB weaker than the value estimated for C/N0 at the antenna port,
         * which is reported in cN0DbHz.
         *
         * If a signal has separate components (e.g. Pilot and Data channels) and the receiver only
         * processes one of the components, then the reported basebandCN0DbHz reflects only the
         * component that is processed.
         *
         * This value is mandatory.
         */
        double basebandCN0DbHz __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, v2_0) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, flags) == 176, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, fullInterSignalBiasNs) == 184, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, fullInterSignalBiasUncertaintyNs) == 192, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, satelliteInterSignalBiasNs) == 200, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, satelliteInterSignalBiasUncertaintyNs) == 208, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement, basebandCN0DbHz) == 216, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement) == 224, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement) == 8, "wrong alignment");

    /**
     * Extends a GNSS clock time, adding a referenceSignalTypeForIsb.
     */
    struct GnssClock final {
        /**
         * GNSS clock time information, as in the 1.0 version of the HAL.
         */
        ::android::hardware::gnss::V1_0::IGnssMeasurementCallback::GnssClock v1_0 __attribute__ ((aligned(8)));
        /**
         * Reference GNSS signal type for inter-signal bias.
         */
        ::android::hardware::gnss::V2_1::GnssSignalType referenceSignalTypeForIsb __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock, v1_0) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock, referenceSignalTypeForIsb) == 72, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock) == 104, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock) == 8, "wrong alignment");

    /**
     * Complete set of GNSS Measurement data, same as 2.0 with additional fields in measurements.
     */
    struct GnssData final {
        /**
         * The full set of satellite measurement observations.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement> measurements __attribute__ ((aligned(8)));
        /**
         * The GNSS clock time reading.
         */
        ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock clock __attribute__ ((aligned(8)));
        /**
         * Timing information of the GNSS data synchronized with SystemClock.elapsedRealtimeNanos()
         * clock.
         */
        ::android::hardware::gnss::V2_0::ElapsedRealtime elapsedRealtime __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData, measurements) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData, clock) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData, elapsedRealtime) == 120, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData) == 144, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> GnssMeasurementCb(const ::android::hardware::gnss::V1_0::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> gnssMeasurementCb(const ::android::hardware::gnss::V1_1::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> gnssMeasurementCb_2_0(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> gnssMeasurementCb_2_1(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& data) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_1::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssMeasurementCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags o);
static inline void PrintTo(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SNR) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SNR)) {
        os += (first ? "" : " | ");
        os += "HAS_SNR";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SNR;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_FREQUENCY) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_FREQUENCY)) {
        os += (first ? "" : " | ");
        os += "HAS_CARRIER_FREQUENCY";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_FREQUENCY;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_CYCLES) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_CYCLES)) {
        os += (first ? "" : " | ");
        os += "HAS_CARRIER_CYCLES";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_CYCLES;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE)) {
        os += (first ? "" : " | ");
        os += "HAS_CARRIER_PHASE";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE_UNCERTAINTY) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE_UNCERTAINTY)) {
        os += (first ? "" : " | ");
        os += "HAS_CARRIER_PHASE_UNCERTAINTY";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE_UNCERTAINTY;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_AUTOMATIC_GAIN_CONTROL) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_AUTOMATIC_GAIN_CONTROL)) {
        os += (first ? "" : " | ");
        os += "HAS_AUTOMATIC_GAIN_CONTROL";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_AUTOMATIC_GAIN_CONTROL;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB)) {
        os += (first ? "" : " | ");
        os += "HAS_FULL_ISB";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB_UNCERTAINTY) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB_UNCERTAINTY)) {
        os += (first ? "" : " | ");
        os += "HAS_FULL_ISB_UNCERTAINTY";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB_UNCERTAINTY;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB)) {
        os += (first ? "" : " | ");
        os += "HAS_SATELLITE_ISB";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB;
    }
    if ((o & ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB_UNCERTAINTY) == static_cast<uint32_t>(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB_UNCERTAINTY)) {
        os += (first ? "" : " | ");
        os += "HAS_SATELLITE_ISB_UNCERTAINTY";
        first = false;
        flipped |= ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB_UNCERTAINTY;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SNR) {
        return "HAS_SNR";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_FREQUENCY) {
        return "HAS_CARRIER_FREQUENCY";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_CYCLES) {
        return "HAS_CARRIER_CYCLES";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE) {
        return "HAS_CARRIER_PHASE";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE_UNCERTAINTY) {
        return "HAS_CARRIER_PHASE_UNCERTAINTY";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_AUTOMATIC_GAIN_CONTROL) {
        return "HAS_AUTOMATIC_GAIN_CONTROL";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB) {
        return "HAS_FULL_ISB";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB_UNCERTAINTY) {
        return "HAS_FULL_ISB_UNCERTAINTY";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB) {
        return "HAS_SATELLITE_ISB";
    }
    if (o == ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB_UNCERTAINTY) {
        return "HAS_SATELLITE_ISB_UNCERTAINTY";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v2_0 = ";
    os += ::android::hardware::gnss::V2_0::toString(o.v2_0);
    os += ", .flags = ";
    os += ::android::hardware::gnss::V2_1::toString<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags>(o.flags);
    os += ", .fullInterSignalBiasNs = ";
    os += ::android::hardware::toString(o.fullInterSignalBiasNs);
    os += ", .fullInterSignalBiasUncertaintyNs = ";
    os += ::android::hardware::toString(o.fullInterSignalBiasUncertaintyNs);
    os += ", .satelliteInterSignalBiasNs = ";
    os += ::android::hardware::toString(o.satelliteInterSignalBiasNs);
    os += ", .satelliteInterSignalBiasUncertaintyNs = ";
    os += ::android::hardware::toString(o.satelliteInterSignalBiasUncertaintyNs);
    os += ", .basebandCN0DbHz = ";
    os += ::android::hardware::toString(o.basebandCN0DbHz);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& rhs) {
    if (lhs.v2_0 != rhs.v2_0) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.fullInterSignalBiasNs != rhs.fullInterSignalBiasNs) {
        return false;
    }
    if (lhs.fullInterSignalBiasUncertaintyNs != rhs.fullInterSignalBiasUncertaintyNs) {
        return false;
    }
    if (lhs.satelliteInterSignalBiasNs != rhs.satelliteInterSignalBiasNs) {
        return false;
    }
    if (lhs.satelliteInterSignalBiasUncertaintyNs != rhs.satelliteInterSignalBiasUncertaintyNs) {
        return false;
    }
    if (lhs.basebandCN0DbHz != rhs.basebandCN0DbHz) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurement& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_0 = ";
    os += ::android::hardware::gnss::V1_0::toString(o.v1_0);
    os += ", .referenceSignalTypeForIsb = ";
    os += ::android::hardware::gnss::V2_1::toString(o.referenceSignalTypeForIsb);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& rhs) {
    if (lhs.v1_0 != rhs.v1_0) {
        return false;
    }
    if (lhs.referenceSignalTypeForIsb != rhs.referenceSignalTypeForIsb) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssClock& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".measurements = ";
    os += ::android::hardware::toString(o.measurements);
    os += ", .clock = ";
    os += ::android::hardware::gnss::V2_1::toString(o.clock);
    os += ", .elapsedRealtime = ";
    os += ::android::hardware::gnss::V2_0::toString(o.elapsedRealtime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& rhs) {
    if (lhs.measurements != rhs.measurements) {
        return false;
    }
    if (lhs.clock != rhs.clock) {
        return false;
    }
    if (lhs.elapsedRealtime != rhs.elapsedRealtime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_1::IGnssMeasurementCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_1
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
template<> inline constexpr std::array<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags, 10> hidl_enum_values<::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags> = {
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SNR,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_FREQUENCY,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_CYCLES,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_CARRIER_PHASE_UNCERTAINTY,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_AUTOMATIC_GAIN_CONTROL,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_FULL_ISB_UNCERTAINTY,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB,
    ::android::hardware::gnss::V2_1::IGnssMeasurementCallback::GnssMeasurementFlags::HAS_SATELLITE_ISB_UNCERTAINTY,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_IGNSSMEASUREMENTCALLBACK_H
