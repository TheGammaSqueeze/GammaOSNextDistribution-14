#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIPEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIPEVENTCALLBACK_H

#include <android/hardware/wifi/1.0/IWifiChipEventCallback.h>
#include <android/hardware/wifi/1.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

/**
 * Wifi chip event callbacks.
 */
struct IWifiChipEventCallback : public ::android::hardware::wifi::V1_0::IWifiChipEventCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi@1.2::IWifiChipEventCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct IfaceInfo;
    struct RadioModeInfo;

    /**
     * Struct describing the state of each iface operating on the radio chain
     * (hardware MAC) on the device.
     */
    struct IfaceInfo final {
        /**
         * Name of the interface (For ex: "wlan0").
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
        /**
         * Wifi channel on which this interface is operating.
         */
        uint32_t channel __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo, name) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo, channel) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo) == 8, "wrong alignment");

    /**
     * Struct describing the state of each hardware radio chain (hardware MAC)
     * on the device.
     */
    struct RadioModeInfo final {
        /**
         * Identifier for this radio chain. This is vendor dependent & used
         * only for debugging purposes.
         */
        uint32_t radioId __attribute__ ((aligned(4)));
        /**
         * List of bands on which this radio chain is operating.
         * Can be one of:
         * a) WifiBand.BAND_24GHZ => 2.4Ghz.
         * b) WifiBand.BAND_5GHZ => 5Ghz.
         * c) WifiBand.BAND_24GHZ_5GHZ = 2.4Ghz + 5Ghz (Radio is time sharing
         * across the 2 bands).
         */
        ::android::hardware::wifi::V1_0::WifiBand bandInfo __attribute__ ((aligned(4)));
        /**
         * List of interfaces on this radio chain (hardware MAC).
         */
        ::android::hardware::hidl_vec<::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo> ifaceInfos __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo, radioId) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo, bandInfo) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo, ifaceInfos) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Callback indicating that the chip has been reconfigured successfully. At
     * this point the interfaces available in the mode must be able to be
     * configured. When this is called any previous iface objects must be
     * considered invalid.
     *
     * @param modeId The mode that the chip switched to, corresponding to the id
     *        property of the target ChipMode.
     */
    virtual ::android::hardware::Return<void> onChipReconfigured(uint32_t modeId) = 0;

    /**
     * Callback indicating that a chip reconfiguration failed. This is a fatal
     * error and any iface objects available previously must be considered
     * invalid. The client can attempt to recover by trying to reconfigure the
     * chip again using |IWifiChip.configureChip|.
     *
     * @param status Failure reason code.
     */
    virtual ::android::hardware::Return<void> onChipReconfigureFailure(const ::android::hardware::wifi::V1_0::WifiStatus& status) = 0;

    /**
     * Callback indicating that a new iface has been added to the chip.
     *
     * @param type Type of iface added.
     * @param name Name of iface added.
     */
    virtual ::android::hardware::Return<void> onIfaceAdded(::android::hardware::wifi::V1_0::IfaceType type, const ::android::hardware::hidl_string& name) = 0;

    /**
     * Callback indicating that an existing iface has been removed from the chip.
     *
     * @param type Type of iface removed.
     * @param name Name of iface removed.
     */
    virtual ::android::hardware::Return<void> onIfaceRemoved(::android::hardware::wifi::V1_0::IfaceType type, const ::android::hardware::hidl_string& name) = 0;

    /**
     * Callbacks for reporting debug ring buffer data.
     *
     * The ring buffer data collection is event based:
     * - Driver calls this callback when new records are available, the
     *   |WifiDebugRingBufferStatus| passed up to framework in the callback
     *   indicates to framework if more data is available in the ring buffer.
     *   It is not expected that driver will necessarily always empty the ring
     *   immediately as data is available, instead driver will report data
     *   every X seconds or if N bytes are available based on the parameters
     *   set via |startLoggingToDebugRingBuffer|.
     * - In the case where a bug report has to be captured, framework will
     *   require driver to upload all data immediately. This is indicated to
     *   driver when framework calls |forceDumpToDebugRingBuffer|.  The driver
     *   will start sending all available data in the indicated ring by repeatedly
     *   invoking this callback.
     *
     * @return status Status of the corresponding ring buffer. This should
     *         contain the name of the ring buffer on which the data is
     *         available.
     * @return data Raw bytes of data sent by the driver. Must be dumped
     *         out to a bugreport and post processed.
     */
    virtual ::android::hardware::Return<void> onDebugRingBufferDataAvailable(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& status, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Callback indicating that the chip has encountered a fatal error.
     * Client must not attempt to parse either the errorCode or debugData.
     * Must only be captured in a bugreport.
     *
     * @param errorCode Vendor defined error code.
     * @param debugData Vendor defined data used for debugging.
     */
    virtual ::android::hardware::Return<void> onDebugErrorAlert(int32_t errorCode, const ::android::hardware::hidl_vec<uint8_t>& debugData) = 0;

    /**
     * Asynchronous callback indicating a radio mode change.
     * Radio mode change could be a result of:
     * a) Bringing up concurrent interfaces (For ex: STA + AP).
     * b) Change in operating band of one of the concurrent interfaces (For ex:
     * STA connection moved from 2.4G to 5G)
     *
     * @param radioModeInfos List of RadioModeInfo structures for each
     * radio chain (hardware MAC) on the device.
     */
    virtual ::android::hardware::Return<void> onRadioModeChange(const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo>& radioModeInfos) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>> castFrom(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IWifiChipEventCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiChipEventCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiChipEventCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiChipEventCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IWifiChipEventCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiChipEventCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiChipEventCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiChipEventCallback> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .channel = ";
    os += ::android::hardware::toString(o.channel);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& rhs) {
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.channel != rhs.channel) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::IfaceInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".radioId = ";
    os += ::android::hardware::toString(o.radioId);
    os += ", .bandInfo = ";
    os += ::android::hardware::wifi::V1_0::toString(o.bandInfo);
    os += ", .ifaceInfos = ";
    os += ::android::hardware::toString(o.ifaceInfos);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& rhs) {
    if (lhs.radioId != rhs.radioId) {
        return false;
    }
    if (lhs.bandInfo != rhs.bandInfo) {
        return false;
    }
    if (lhs.ifaceInfos != rhs.ifaceInfos) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& lhs, const ::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::V1_2::IWifiChipEventCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_2
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIPEVENTCALLBACK_H
