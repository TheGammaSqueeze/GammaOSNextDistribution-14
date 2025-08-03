#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSGEOFENCECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSGEOFENCECALLBACK_H

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
 * GNSS Geofence.
 * There are 3 states associated with a Geofence: Inside, Outside, Unknown.
 * There are 3 transitions: ENTERED, EXITED, UNCERTAIN.
 *
 * An example state diagram with confidence level: 95% and Unknown time limit
 * set as 30 secs is shown below. (confidence level and Unknown time limit are
 * explained latter).
 *                         ____________________________
 *                        |       Unknown (30 secs)   |
 *                         """"""""""""""""""""""""""""
 *                            ^ |                  |  ^
 *                   UNCERTAIN| |ENTERED     EXITED|  |UNCERTAIN
 *                            | v                  v  |
 *                        ________    EXITED     _________
 *                       | Inside | -----------> | Outside |
 *                       |        | <----------- |         |
 *                        """"""""    ENTERED    """""""""
 *
 * Inside state: We are 95% confident that the user is inside the geofence.
 * Outside state: We are 95% confident that the user is outside the geofence
 * Unknown state: Rest of the time.
 *
 * The Unknown state is better explained with an example:
 *
 *                            __________
 *                           |         c|
 *                           |  ___     |    _______
 *                           |  |a|     |   |   b   |
 *                           |  """     |    """""""
 *                           |          |
 *                            """"""""""
 * In the diagram above, "a" and "b" are 2 geofences and "c" is the accuracy
 * circle reported by the GNSS subsystem. Now with regard to "b", the system is
 * confident that the user is outside. But with regard to "a" is not confident
 * whether it is inside or outside the geofence. If the accuracy remains the
 * same for a sufficient period of time, the UNCERTAIN transition must be
 * triggered with the state set to Unknown. If the accuracy improves later, an
 * appropriate transition must be triggered.  This "sufficient period of time"
 * is defined by the parameter in the addGeofenceArea API.
 * In other words, Unknown state can be interpreted as a state in which the
 * GNSS subsystem isn't confident enough that the user is either inside or
 * outside the Geofence. It moves to Unknown state only after the expiry of the
 * timeout.
 *
 * The geofence callback needs to be triggered for the ENTERED and EXITED
 * transitions, when the GNSS system is confident that the user has entered
 * (Inside state) or exited (Outside state) the Geofence. An implementation
 * which uses a value of 95% as the confidence is recommended. The callback
 * must be triggered only for the transitions requested by the
 * addGeofenceArea method.
 *
 * Even though the diagram and explanation talks about states and transitions,
 * the callee is only interested in the transitions. The states are mentioned
 * here for illustrative purposes.
 *
 * Startup Scenario: When the device boots up, if an application adds geofences,
 * and then we get an accurate GNSS location fix, it needs to trigger the
 * appropriate (ENTERED or EXITED) transition for every Geofence it knows about.
 * By default, all the Geofences will be in the Unknown state.
 *
 * When the GNSS system is unavailable, gnssGeofenceStatusCb must be
 * called to inform the upper layers of the same. Similarly, when it becomes
 * available the callback must be called. This is a global state while the
 * UNKNOWN transition described above is per geofence.
 *
 * An important aspect to note is that users of this API (framework), will use
 * other subsystems like wifi, sensors, cell to handle Unknown case and
 * hopefully provide a definitive state transition to the third party
 * application. GNSS Geofence will just be a signal indicating what the GNSS
 * subsystem knows about the Geofence.
 *
 */
struct IGnssGeofenceCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IGnssGeofenceCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class GeofenceTransition : int32_t;
    enum class GeofenceAvailability : int32_t;
    enum class GeofenceStatus : int32_t;

    enum class GeofenceTransition : int32_t {
        ENTERED = 1 /* (1 << 0L) */,
        EXITED = 2 /* (1 << 1L) */,
        UNCERTAIN = 4 /* (1 << 2L) */,
    };

    enum class GeofenceAvailability : int32_t {
        UNAVAILABLE = 1 /* (1 << 0L) */,
        AVAILABLE = 2 /* (1 << 1L) */,
    };

    enum class GeofenceStatus : int32_t {
        OPERATION_SUCCESS = 0,
        ERROR_TOO_MANY_GEOFENCES = -100 /* -100 */,
        ERROR_ID_EXISTS = -101 /* -101 */,
        ERROR_ID_UNKNOWN = -102 /* -102 */,
        ERROR_INVALID_TRANSITION = -103 /* -103 */,
        ERROR_GENERIC = -149 /* -149 */,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * The callback associated with the geofence transition.
     * The callback must only be called when the caller is interested in that
     * particular transition. For instance, if the caller is interested only in
     * ENTERED transition, then the callback must not be called with the EXITED
     * transition.
     *
     * IMPORTANT: If a transition is triggered resulting in this callback, the
     * GNSS subsystem will wake up the application processor, if its in suspend
     * state.
     *
     * @param geofenceId The id associated with the addGeofenceArea.
     * @param location The current GNSS location.
     * @param transition Can be one of ENTERED, EXITED or UNCERTAIN.
     * @param timestamp Timestamp when the transition was detected.
     *
     */
    virtual ::android::hardware::Return<void> gnssGeofenceTransitionCb(int32_t geofenceId, const ::android::hardware::gnss::V1_0::GnssLocation& location, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition transition, int64_t timestamp) = 0;

    /**
     * The callback associated with the availability of the GNSS system for
     * geofencing monitoring. If the GNSS system determines that it cannot monitor
     * geofences because of lack of reliability or unavailability of the GNSS
     * signals, it will call this callback with UNAVAILABLE parameter.
     *
     * @param status - UNAVAILABLE or AVAILABLE.
     * @param lastLocation - Last known location.
     */
    virtual ::android::hardware::Return<void> gnssGeofenceStatusCb(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability status, const ::android::hardware::gnss::V1_0::GnssLocation& lastLocation) = 0;

    /**
     * The callback associated with the addGeofence call.
     *
     * @param geofenceId Id of the geofence.
     * @param status Will be OPERATION_SUCCESS if the geofence
     * add was successful. Will be ERROR_TOO_MANY_GEOFENCES if the
     * geofence limit has been reached.
     * Will be ERROR_ID_EXISTS if geofence with id already exists.
     * Will be ERROR_INVALID_TRANSITION if the monitorTransition contains an
     * invalid transition.
     * Will be ERROR_GENERIC for other errors.
     */
    virtual ::android::hardware::Return<void> gnssGeofenceAddCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) = 0;

    /**
     * The callback associated with the removeGeofence call.
     *
     * @param geofenceId Id of the geofence.
     * @param status Will return OPERATION_SUCCESS if successful.
     * Will be ERROR_ID_UNKNOWN for invalid id and
     * ERROR_GENERIC for others.
     */
    virtual ::android::hardware::Return<void> gnssGeofenceRemoveCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) = 0;

    /**
     * The callback associated with the pauseGeofence call.
     *
     * @param geofenceId Id of the geofence.
     * @param status Will be OPERATION_SUCCESS if success.
     * Will be ERROR_ID_UNKNOWN for invalid id. Will be
     * ERROR_INVALID_TRANSITION when monitorTransitions is invalid.
     * Will be ERROR_GENERIC for other err errors.
     */
    virtual ::android::hardware::Return<void> gnssGeofencePauseCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) = 0;

    /**
     * The callback associated with the resumeGeofence call.
     *
     * @param geofenceId - Id of the geofence.
     * @param status Will be OPERATION_SUCCESS if successful.
     * Will be ERROR_ID_UNKNOWN for invalid id and ERROR_GENERIC for others.
     */
    virtual ::android::hardware::Return<void> gnssGeofenceResumeCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnssGeofenceCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssGeofenceCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssGeofenceCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssGeofenceCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssGeofenceCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssGeofenceCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssGeofenceCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssGeofenceCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::ENTERED) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::ENTERED)) {
        os += (first ? "" : " | ");
        os += "ENTERED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::ENTERED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::EXITED) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::EXITED)) {
        os += (first ? "" : " | ");
        os += "EXITED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::EXITED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::UNCERTAIN) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::UNCERTAIN)) {
        os += (first ? "" : " | ");
        os += "UNCERTAIN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::UNCERTAIN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::ENTERED) {
        return "ENTERED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::EXITED) {
        return "EXITED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::UNCERTAIN) {
        return "UNCERTAIN";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::UNAVAILABLE) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::UNAVAILABLE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::AVAILABLE) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "AVAILABLE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::AVAILABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::UNAVAILABLE) {
        return "UNAVAILABLE";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::AVAILABLE) {
        return "AVAILABLE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::OPERATION_SUCCESS) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::OPERATION_SUCCESS)) {
        os += (first ? "" : " | ");
        os += "OPERATION_SUCCESS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::OPERATION_SUCCESS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_TOO_MANY_GEOFENCES) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_TOO_MANY_GEOFENCES)) {
        os += (first ? "" : " | ");
        os += "ERROR_TOO_MANY_GEOFENCES";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_TOO_MANY_GEOFENCES;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_EXISTS) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_EXISTS)) {
        os += (first ? "" : " | ");
        os += "ERROR_ID_EXISTS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_EXISTS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_UNKNOWN) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "ERROR_ID_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_UNKNOWN;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_INVALID_TRANSITION) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_INVALID_TRANSITION)) {
        os += (first ? "" : " | ");
        os += "ERROR_INVALID_TRANSITION";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_INVALID_TRANSITION;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_GENERIC) == static_cast<int32_t>(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_GENERIC)) {
        os += (first ? "" : " | ");
        os += "ERROR_GENERIC";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_GENERIC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::OPERATION_SUCCESS) {
        return "OPERATION_SUCCESS";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_TOO_MANY_GEOFENCES) {
        return "ERROR_TOO_MANY_GEOFENCES";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_EXISTS) {
        return "ERROR_ID_EXISTS";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_UNKNOWN) {
        return "ERROR_ID_UNKNOWN";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_INVALID_TRANSITION) {
        return "ERROR_INVALID_TRANSITION";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_GENERIC) {
        return "ERROR_GENERIC";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::descriptor;
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> = {
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::ENTERED,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::EXITED,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition::UNCERTAIN,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability, 2> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability> = {
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::UNAVAILABLE,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability::AVAILABLE,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus, 6> hidl_enum_values<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus> = {
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::OPERATION_SUCCESS,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_TOO_MANY_GEOFENCES,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_EXISTS,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_ID_UNKNOWN,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_INVALID_TRANSITION,
    ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus::ERROR_GENERIC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSSGEOFENCECALLBACK_H
