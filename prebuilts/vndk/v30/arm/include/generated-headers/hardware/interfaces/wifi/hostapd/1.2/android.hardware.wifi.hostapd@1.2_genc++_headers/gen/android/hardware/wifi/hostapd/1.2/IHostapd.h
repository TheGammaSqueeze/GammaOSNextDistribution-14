#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHOSTAPD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHOSTAPD_H

#include <android/hardware/wifi/hostapd/1.0/IHostapd.h>
#include <android/hardware/wifi/hostapd/1.1/IHostapd.h>
#include <android/hardware/wifi/hostapd/1.2/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_2 {

/**
 * Top-level object for managing SoftAPs.
 */
struct IHostapd : public ::android::hardware::wifi::hostapd::V1_1::IHostapd {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.hostapd@1.2::IHostapd"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class EncryptionType : uint32_t;
    enum class BandMask : uint32_t;
    struct HwModeParams;
    struct AcsFrequencyRange;
    struct ChannelParams;
    struct IfaceParams;
    struct NetworkParams;

    /**
     * Possible Security types.
     */
    enum class EncryptionType : uint32_t {
        NONE = 0u,
        WPA = 1u /* ::android::hardware::wifi::hostapd::V1_0::IHostapd::EncryptionType.NONE implicitly + 1 */,
        WPA2 = 2u /* ::android::hardware::wifi::hostapd::V1_0::IHostapd::EncryptionType.WPA implicitly + 1 */,
        WPA3_SAE_TRANSITION = 3u /* ::android::hardware::wifi::hostapd::V1_0::IHostapd::EncryptionType.WPA2 implicitly + 1 */,
        WPA3_SAE = 4u /* ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType.WPA3_SAE_TRANSITION implicitly + 1 */,
    };

    /**
     * Band bitmMask to use for the SoftAp operations.
     * A combinatoin of these bits are used to identify the allowed bands
     * to start the softAp
     */
    enum class BandMask : uint32_t {
        /**
         * 2.4 GHz band.
         */
        BAND_2_GHZ = 1u /* 1 << 0 */,
        /**
         * 5 GHz band.
         */
        BAND_5_GHZ = 2u /* 1 << 1 */,
        /**
         * 6 GHz band.
         */
        BAND_6_GHZ = 4u /* 1 << 2 */,
    };

    /**
     * Parameters to control the HW mode for the interface.
     */
    struct HwModeParams final {
        /**
         * Whether IEEE 802.11ax (HE) is enabled or not.
         * Note: hw_mode=a is used to specify that 5 GHz band or 6 GHz band is
         * used with HE.
         */
        bool enable80211AX __attribute__ ((aligned(1)));
        /**
         * Whether 6GHz band enabled or not on softAp.
         * Note: hw_mode=a is used to specify that 5 GHz band or 6 GHz band is
         * used.
         */
        bool enable6GhzBand __attribute__ ((aligned(1)));
        /**
         * Whether HE single user beamformer in enabled or not on softAp.
         * Note: this is only applicable if 802.11ax is supported for softAp
         */
        bool enableHeSingleUserBeamformer __attribute__ ((aligned(1)));
        /**
         * Whether HE single user beamformee is enabled or not on softAp.
         * Note: this is only applicable if 802.11ax is supported for softAp
         */
        bool enableHeSingleUserBeamformee __attribute__ ((aligned(1)));
        /**
         * Whether HE multiple user beamformer is enabled or not on softAp.
         * Note: this is only applicable if 802.11ax is supported for softAp
         */
        bool enableHeMultiUserBeamformer __attribute__ ((aligned(1)));
        /**
         * Whether HE Target Wait Time (TWT) is enabled or not on softAp.
         * Note: this is only applicable if 802.11ax is supported for softAp
         */
        bool enableHeTargetWakeTime __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enable80211AX) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enable6GhzBand) == 1, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enableHeSingleUserBeamformer) == 2, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enableHeSingleUserBeamformee) == 3, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enableHeMultiUserBeamformer) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams, enableHeTargetWakeTime) == 5, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams) == 6, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams) == 1, "wrong alignment");

    /**
     * Parameters to specify the channel frequency range for ACS.
     */
    struct AcsFrequencyRange final {
        /**
         * Channel Frequency (in MHz) at the start of the range.
         */
        uint32_t start __attribute__ ((aligned(4)));
        /**
         * Channel Frequency (in MHz) at the end of the range.
         */
        uint32_t end __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange, start) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange, end) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange) == 4, "wrong alignment");

    /**
     * Parameters to control the channel selection for the interface.
     */
    struct ChannelParams final {
        /**
         * Band to use for the SoftAp operations.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask> bandMask __attribute__ ((aligned(4)));
        /**
         * This option can be used to specify the channel frequencies (in MHz) selected by ACS.
         * If this is an empty list, all channels allowed in selected HW mode
         * are specified implicitly.
         * Note: channels may be overridden by firmware.
         * Note: this option is ignored if ACS is disabled.
         */
        ::android::hardware::hidl_vec<::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange> acsChannelFreqRangesMhz __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams, bandMask) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams, acsChannelFreqRangesMhz) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams) == 8, "wrong alignment");

    /**
     * Parameters to use for setting up the access point interface.
     */
    struct IfaceParams final {
        /**
         * Baseline information as defined in HAL 1.1.
         */
        ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams V1_1 __attribute__ ((aligned(8)));
        /**
         * Additional Hw mode params for the interface
         */
        ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams hwModeParams __attribute__ ((aligned(1)));
        /**
         * Additional Channel params for the interface
         */
        ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams channelParams __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams, V1_1) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams, hwModeParams) == 48, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams, channelParams) == 56, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams) == 80, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams) == 8, "wrong alignment");

    /**
     * Parameters to use for setting up the access point network.
     */
    struct NetworkParams final {
        /**
         * Baseline information as defined in HAL 1.0.
         */
        ::android::hardware::wifi::hostapd::V1_0::IHostapd::NetworkParams V1_0 __attribute__ ((aligned(8)));
        /**
         * Key management mask for the replace V1_0.encryptionType.
         */
        ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType encryptionType __attribute__ ((aligned(4)));
        /**
         * Passphrase for WPA3_SAE network, WPA3_SAE_TRANSITION and
         * WPA2_PSK. Replaces @1.0::IHostapd.NetworkParams.pskPassphrase.
         */
        ::android::hardware::hidl_string passphrase __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams, V1_0) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams, encryptionType) == 40, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams, passphrase) == 48, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams) == 64, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for addAccessPoint
     */
    using addAccessPoint_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Adds a new access point for hostapd to control.
     *
     * This should trigger the setup of an access point with the specified
     * interface and network params.
     *
     * @param ifaceParams AccessPoint Params for the access point.
     * @param nwParams Network Params for the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_ARGS_INVALID|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_EXISTS|
     */
    virtual ::android::hardware::Return<void> addAccessPoint(const ::android::hardware::wifi::hostapd::V1_0::IHostapd::IfaceParams& ifaceParams, const ::android::hardware::wifi::hostapd::V1_0::IHostapd::NetworkParams& nwParams, addAccessPoint_cb _hidl_cb) = 0;

    /**
     * Return callback for removeAccessPoint
     */
    using removeAccessPoint_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Removes an existing access point from hostapd.
     *
     * This should bring down the access point previously setup on the
     * interface.
     *
     * @param ifaceName Name of the interface.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> removeAccessPoint(const ::android::hardware::hidl_string& ifaceName, removeAccessPoint_cb _hidl_cb) = 0;

    /**
     * Terminate the service.
     * This must de-register the service and clear all state. If this HAL
     * supports the lazy HAL protocol, then this may trigger daemon to exit and
     * wait to be restarted.
     */
    virtual ::android::hardware::Return<void> terminate() = 0;

    /**
     * Return callback for addAccessPoint_1_1
     */
    using addAccessPoint_1_1_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Adds a new access point for hostapd to control.
     *
     * This should trigger the setup of an access point with the specified
     * interface and network params.
     *
     * @param ifaceParams AccessPoint Params for the access point.
     * @param nwParams Network Params for the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_ARGS_INVALID|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_EXISTS|
     */
    virtual ::android::hardware::Return<void> addAccessPoint_1_1(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& ifaceParams, const ::android::hardware::wifi::hostapd::V1_0::IHostapd::NetworkParams& nwParams, addAccessPoint_1_1_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback
     */
    using registerCallback_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Register for callbacks from the hostapd service.
     *
     * These callbacks are invoked for global events that are not specific
     * to any interface or network. Registration of multiple callback
     * objects is supported. These objects must be deleted when the corresponding
     * client process is dead.
     *
     * @param callback An instance of the |IHostapdCallback| HIDL interface
     *     object.
     * @return status Status of the operation.
     *     Possible status codes:
     *     |HostapdStatusCode.SUCCESS|,
     *     |HostapdStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapdCallback>& callback, registerCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for addAccessPoint_1_2
     */
    using addAccessPoint_1_2_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& status)>;
    /**
     * Adds a new access point for hostapd to control.
     *
     * This should trigger the setup of an access point with the specified
     * interface and network params.
     *
     * @param ifaceParams AccessPoint Params for the access point.
     * @param nwParams Network Params for the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_ARGS_INVALID|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_EXISTS|
     */
    virtual ::android::hardware::Return<void> addAccessPoint_1_2(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& ifaceParams, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& nwParams, addAccessPoint_1_2_cb _hidl_cb) = 0;

    /**
     * Return callback for forceClientDisconnect
     */
    using forceClientDisconnect_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& status)>;
    /**
     * force one of the hotspot clients disconnect..
     *
     * @param ifaceName Name of the interface.
     * @param clientAddress Mac Address of the hotspot client.
     * @param reasonCode One of disconnect reason code which defined by 802.11.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_IFACE_UNKNOWN|
     *         |HostapdStatusCode.FAILURE_CLIENT_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> forceClientDisconnect(const ::android::hardware::hidl_string& ifaceName, const ::android::hardware::hidl_array<uint8_t, 6>& clientAddress, ::android::hardware::wifi::hostapd::V1_2::Ieee80211ReasonCode reasonCode, forceClientDisconnect_cb _hidl_cb) = 0;

    /**
     * Return callback for setDebugParams
     */
    using setDebugParams_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_2::HostapdStatus& status)>;
    /**
     * Set debug parameters for the hostapd.
     *
     * @param level Debug logging level for the hostapd.
     *        (one of |DebugLevel| values).
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setDebugParams(::android::hardware::wifi::hostapd::V1_2::DebugLevel level, setDebugParams_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>> castFrom(const ::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>> castFrom(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>> castFrom(const ::android::sp<::android::hardware::wifi::hostapd::V1_0::IHostapd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IHostapd> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHostapd> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHostapd> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHostapd> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IHostapd> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHostapd> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHostapd> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHostapd> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask o);
static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::NONE) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::NONE;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA)) {
        os += (first ? "" : " | ");
        os += "WPA";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA2) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA2)) {
        os += (first ? "" : " | ");
        os += "WPA2";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA2;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE_TRANSITION) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE_TRANSITION)) {
        os += (first ? "" : " | ");
        os += "WPA3_SAE_TRANSITION";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE_TRANSITION;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE)) {
        os += (first ? "" : " | ");
        os += "WPA3_SAE";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA) {
        return "WPA";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA2) {
        return "WPA2";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE_TRANSITION) {
        return "WPA3_SAE_TRANSITION";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE) {
        return "WPA3_SAE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_2_GHZ) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_2_GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_2_GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_2_GHZ;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_5_GHZ) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_5_GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_5_GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_5_GHZ;
    }
    if ((o & ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_6_GHZ) == static_cast<uint32_t>(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_6_GHZ)) {
        os += (first ? "" : " | ");
        os += "BAND_6_GHZ";
        first = false;
        flipped |= ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_6_GHZ;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_2_GHZ) {
        return "BAND_2_GHZ";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_5_GHZ) {
        return "BAND_5_GHZ";
    }
    if (o == ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_6_GHZ) {
        return "BAND_6_GHZ";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".enable80211AX = ";
    os += ::android::hardware::toString(o.enable80211AX);
    os += ", .enable6GhzBand = ";
    os += ::android::hardware::toString(o.enable6GhzBand);
    os += ", .enableHeSingleUserBeamformer = ";
    os += ::android::hardware::toString(o.enableHeSingleUserBeamformer);
    os += ", .enableHeSingleUserBeamformee = ";
    os += ::android::hardware::toString(o.enableHeSingleUserBeamformee);
    os += ", .enableHeMultiUserBeamformer = ";
    os += ::android::hardware::toString(o.enableHeMultiUserBeamformer);
    os += ", .enableHeTargetWakeTime = ";
    os += ::android::hardware::toString(o.enableHeTargetWakeTime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& rhs) {
    if (lhs.enable80211AX != rhs.enable80211AX) {
        return false;
    }
    if (lhs.enable6GhzBand != rhs.enable6GhzBand) {
        return false;
    }
    if (lhs.enableHeSingleUserBeamformer != rhs.enableHeSingleUserBeamformer) {
        return false;
    }
    if (lhs.enableHeSingleUserBeamformee != rhs.enableHeSingleUserBeamformee) {
        return false;
    }
    if (lhs.enableHeMultiUserBeamformer != rhs.enableHeMultiUserBeamformer) {
        return false;
    }
    if (lhs.enableHeTargetWakeTime != rhs.enableHeTargetWakeTime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::HwModeParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".start = ";
    os += ::android::hardware::toString(o.start);
    os += ", .end = ";
    os += ::android::hardware::toString(o.end);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& rhs) {
    if (lhs.start != rhs.start) {
        return false;
    }
    if (lhs.end != rhs.end) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::AcsFrequencyRange& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bandMask = ";
    os += ::android::hardware::wifi::hostapd::V1_2::toString<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask>(o.bandMask);
    os += ", .acsChannelFreqRangesMhz = ";
    os += ::android::hardware::toString(o.acsChannelFreqRangesMhz);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& rhs) {
    if (lhs.bandMask != rhs.bandMask) {
        return false;
    }
    if (lhs.acsChannelFreqRangesMhz != rhs.acsChannelFreqRangesMhz) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::ChannelParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".V1_1 = ";
    os += ::android::hardware::wifi::hostapd::V1_1::toString(o.V1_1);
    os += ", .hwModeParams = ";
    os += ::android::hardware::wifi::hostapd::V1_2::toString(o.hwModeParams);
    os += ", .channelParams = ";
    os += ::android::hardware::wifi::hostapd::V1_2::toString(o.channelParams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& rhs) {
    if (lhs.V1_1 != rhs.V1_1) {
        return false;
    }
    if (lhs.hwModeParams != rhs.hwModeParams) {
        return false;
    }
    if (lhs.channelParams != rhs.channelParams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::IfaceParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".V1_0 = ";
    os += ::android::hardware::wifi::hostapd::V1_0::toString(o.V1_0);
    os += ", .encryptionType = ";
    os += ::android::hardware::wifi::hostapd::V1_2::toString(o.encryptionType);
    os += ", .passphrase = ";
    os += ::android::hardware::toString(o.passphrase);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& rhs) {
    if (lhs.V1_0 != rhs.V1_0) {
        return false;
    }
    if (lhs.encryptionType != rhs.encryptionType) {
        return false;
    }
    if (lhs.passphrase != rhs.passphrase) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& lhs, const ::android::hardware::wifi::hostapd::V1_2::IHostapd::NetworkParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::hostapd::V1_2::IHostapd>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::hostapd::V1_2::IHostapd::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_2
}  // namespace hostapd
}  // namespace wifi
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType, 5> hidl_enum_values<::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType> = {
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::NONE,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA2,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE_TRANSITION,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::EncryptionType::WPA3_SAE,
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
template<> inline constexpr std::array<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask, 3> hidl_enum_values<::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask> = {
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_2_GHZ,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_5_GHZ,
    ::android::hardware::wifi::hostapd::V1_2::IHostapd::BandMask::BAND_6_GHZ,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_2_IHOSTAPD_H
