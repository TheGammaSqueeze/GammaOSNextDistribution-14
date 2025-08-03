#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACE_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicantIface.h>
#include <android/hardware/wifi/supplicant/1.0/ISupplicantP2pIfaceCallback.h>
#include <android/hardware/wifi/supplicant/1.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

/**
 * Interface exposed by the supplicant for each P2P mode network
 * interface (e.g p2p0) it controls.
 */
struct ISupplicantP2pIface : public ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.0::ISupplicantP2pIface"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class WpsProvisionMethod : uint32_t;
    struct FreqRange;
    enum class MiracastMode : uint8_t;

    enum class WpsProvisionMethod : uint32_t {
        /**
         * Push button method.
         */
        PBC = 0u,
        /**
         * Display pin method configuration - pin is generated and displayed on
         * device.
         */
        DISPLAY = 1u /* ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod.PBC implicitly + 1 */,
        /**
         * Keypad pin method configuration - pin is entered on device.
         */
        KEYPAD = 2u /* ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod.DISPLAY implicitly + 1 */,
    };

    /**
     * Use to specify a range of frequencies.
     * For example: 2412-2432,2462,5000-6000, etc.
     */
    struct FreqRange final {
        uint32_t min __attribute__ ((aligned(4)));
        uint32_t max __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange, min) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange, max) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange) == 4, "wrong alignment");

    /**
     * Enum describing the modes of Miracast supported
     * via driver commands.
     */
    enum class MiracastMode : uint8_t {
        DISABLED = 0,
        /**
         * Operating as source.
         */
        SOURCE = 1,
        /**
         * Operating as sink.
         */
        SINK = 2,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getName
     */
    using getName_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_string& name)>;
    /**
     * Retrieves the name of the network interface.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return name Name of the network interface, e.g., wlan0
     */
    virtual ::android::hardware::Return<void> getName(getName_cb _hidl_cb) = 0;

    /**
     * Return callback for getType
     */
    using getType_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, ::android::hardware::wifi::supplicant::V1_0::IfaceType type)>;
    /**
     * Retrieves the type of the network interface.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return type Type of the network interface, e.g., STA.
     */
    virtual ::android::hardware::Return<void> getType(getType_cb _hidl_cb) = 0;

    /**
     * Return callback for addNetwork
     */
    using addNetwork_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork>& network)>;
    /**
     * Add a new network to the interface.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return network HIDL interface object representing the new network if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> addNetwork(addNetwork_cb _hidl_cb) = 0;

    /**
     * Return callback for removeNetwork
     */
    using removeNetwork_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Remove a network from the interface.
     *
     * Use |ISupplicantNetwork.getId()| on the corresponding network HIDL
     * interface object to retrieve the ID.
     *
     * @param id Network ID allocated to the corresponding network.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> removeNetwork(uint32_t id, removeNetwork_cb _hidl_cb) = 0;

    /**
     * Return callback for getNetwork
     */
    using getNetwork_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork>& network)>;
    /**
     * Gets a HIDL interface object for the network corresponding to the
     * |SupplicantNetworkId|.
     *
     * Use |ISupplicantNetwork.getId()| on the corresponding network HIDL
     * interface object to retrieve the ID.
     *
     * @param id Network ID allocated to the corresponding network.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_UNKNOWN|
     * @return network HIDL interface object representing the new network if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> getNetwork(uint32_t id, getNetwork_cb _hidl_cb) = 0;

    /**
     * Return callback for listNetworks
     */
    using listNetworks_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_vec<uint32_t>& networkIds)>;
    /**
     * Retrieve a list of all the network Id's controlled by the supplicant.
     *
     * The corresponding |ISupplicantNetwork| object for any network can be
     * retrieved using |getNetwork| method.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     * @return networkIds List of all network Id's controlled by the supplicant.
     */
    virtual ::android::hardware::Return<void> listNetworks(listNetworks_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsDeviceName
     */
    using setWpsDeviceName_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the device name for WPS operations.
     * User-friendly description of device (up to |WPS_DEVICE_NAME_MAX_LEN|
     * octets encoded in UTF-8).
     *
     * @parm name Name to be set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsDeviceName(const ::android::hardware::hidl_string& name, setWpsDeviceName_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsDeviceType
     */
    using setWpsDeviceType_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the device type for WPS operations.
     *
     * @parm type Type of device. Refer to section B.1 of Wifi P2P
     *       Technical specification v1.2.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsDeviceType(const ::android::hardware::hidl_array<uint8_t, 8>& type, setWpsDeviceType_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsManufacturer
     */
    using setWpsManufacturer_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the manufacturer for WPS operations.
     * The manufacturer of the device (up to |WPS_MANUFACTURER_MAX_LEN| ASCII
     * characters).
     *
     * @parm manufacturer Manufacture to be set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsManufacturer(const ::android::hardware::hidl_string& manufacturer, setWpsManufacturer_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsModelName
     */
    using setWpsModelName_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the model name for WPS operations.
     * Model of the device (up to |WPS_MODEL_NAME_MAX_LEN| ASCII characters).
     *
     * @parm modelName Model name to be set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsModelName(const ::android::hardware::hidl_string& modelName, setWpsModelName_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsModelNumber
     */
    using setWpsModelNumber_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the model number for WPS operations.
     * Additional device description (up to |WPS_MODEL_NUMBER_MAX_LEN| ASCII
     * characters).
     *
     * @parm modelNumber Model number to be set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsModelNumber(const ::android::hardware::hidl_string& modelNumber, setWpsModelNumber_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsSerialNumber
     */
    using setWpsSerialNumber_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the serial number for WPS operations.
     * Serial number of the device (up to |WPS_SERIAL_NUMBER_MAX_LEN| characters)
     *
     * @parm serialNumber Serial number to be set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsSerialNumber(const ::android::hardware::hidl_string& serialNumber, setWpsSerialNumber_cb _hidl_cb) = 0;

    /**
     * Return callback for setWpsConfigMethods
     */
    using setWpsConfigMethods_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the list of supported config methods for WPS operations.
     *
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setWpsConfigMethods(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, setWpsConfigMethods_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback
     */
    using registerCallback_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Register for callbacks from this interface.
     *
     * These callbacks are invoked for events that are specific to this interface.
     * Registration of multiple callback objects is supported. These objects must
     * be automatically deleted when the corresponding client process is dead or
     * if this interface is removed.
     *
     * @param callback An instance of the |ISupplicantP2pIfaceCallback| HIDL
     *        interface object.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>& callback, registerCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for getDeviceAddress
     */
    using getDeviceAddress_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_array<uint8_t, 6>& deviceAddress)>;
    /**
     * Gets the MAC address of the device.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return deviceAddress MAC address of the device.
     */
    virtual ::android::hardware::Return<void> getDeviceAddress(getDeviceAddress_cb _hidl_cb) = 0;

    /**
     * Return callback for setSsidPostfix
     */
    using setSsidPostfix_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the postfix to be used for P2P SSID's.
     *
     * @param postfix String to be appended to SSID.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setSsidPostfix(const ::android::hardware::hidl_vec<uint8_t>& postfix, setSsidPostfix_cb _hidl_cb) = 0;

    /**
     * Return callback for setGroupIdle
     */
    using setGroupIdle_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set the Maximum idle time in seconds for P2P groups.
     * This value controls how long a P2P group is maintained after there
     * is no other members in the group. As a group owner, this means no
     * associated stations in the group. As a P2P client, this means no
     * group owner seen in scan results.
     *
     * @param groupIfName Group interface name to use.
     * @param timeoutInSec Timeout value in seconds.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setGroupIdle(const ::android::hardware::hidl_string& groupIfName, uint32_t timeoutInSec, setGroupIdle_cb _hidl_cb) = 0;

    /**
     * Return callback for setPowerSave
     */
    using setPowerSave_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Turn on/off power save mode for the interface.
     *
     * @param groupIfName Group interface name to use.
     * @param enable Indicate if power save is to be turned on/off.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> setPowerSave(const ::android::hardware::hidl_string& groupIfName, bool enable, setPowerSave_cb _hidl_cb) = 0;

    /**
     * Return callback for find
     */
    using find_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate a P2P service discovery with an optional timeout.
     *
     * @param timeoutInSec Max time to be spent is peforming discovery.
     *        Set to 0 to indefinely continue discovery untill and explicit
     *        |stopFind| is sent.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> find(uint32_t timeoutInSec, find_cb _hidl_cb) = 0;

    /**
     * Return callback for stopFind
     */
    using stopFind_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Stop an ongoing P2P service discovery.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> stopFind(stopFind_cb _hidl_cb) = 0;

    /**
     * Return callback for flush
     */
    using flush_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Flush P2P peer table and state.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> flush(flush_cb _hidl_cb) = 0;

    /**
     * Return callback for connect
     */
    using connect_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_string& generatedPin)>;
    /**
     * Start P2P group formation with a discovered P2P peer. This includes
     * optional group owner negotiation, group interface setup, provisioning,
     * and establishing data connection.
     *
     * @param peerAddress MAC address of the device to connect to.
     * @method provisionMethod Provisioning method to use.
     * @param preSelectedPin Pin to be used, if |provisionMethod| uses one of the
     *        preselected |PIN*| methods.
     * @param joinExistingGroup Indicates that this is a command to join an
     *        existing group as a client. It skips the group owner negotiation
     *        part. This must send a Provision Discovery Request message to the
     *        target group owner before associating for WPS provisioning.
     * @param persistent Used to request a persistent group to be formed.
     * @param goIntent Used to override the default Intent for this group owner
     *        negotiation (Values from 1-15). Refer to section 4.1.6 in
     *        Wi-Fi Peer-to-Peer (P2P) Technical Specification Version 1.7.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return generatedPin Pin generated, if |provisionMethod| uses one of the
     *         generated |PIN*| methods.
     */
    virtual ::android::hardware::Return<void> connect(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod provisionMethod, const ::android::hardware::hidl_string& preSelectedPin, bool joinExistingGroup, bool persistent, uint32_t goIntent, connect_cb _hidl_cb) = 0;

    /**
     * Return callback for cancelConnect
     */
    using cancelConnect_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Cancel an ongoing P2P group formation and joining-a-group related
     * operation. This operation unauthorizes the specific peer device (if any
     * had been authorized to start group formation), stops P2P find (if in
     * progress), stops pending operations for join-a-group, and removes the
     * P2P group interface (if one was used) that is in the WPS provisioning
     * step. If the WPS provisioning step has been completed, the group is not
     * terminated.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_NOT_STARTED|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> cancelConnect(cancelConnect_cb _hidl_cb) = 0;

    /**
     * Return callback for provisionDiscovery
     */
    using provisionDiscovery_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send P2P provision discovery request to the specified peer. The
     * parameters for this command are the P2P device address of the peer and the
     * desired configuration method.
     *
     * @param peerAddress MAC address of the device to send discovery.
     * @method provisionMethod Provisioning method to use.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> provisionDiscovery(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod provisionMethod, provisionDiscovery_cb _hidl_cb) = 0;

    /**
     * Return callback for addGroup
     */
    using addGroup_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set up a P2P group owner manually (i.e., without group owner
     * negotiation with a specific peer). This is also known as autonomous
     * group owner. Optional |persistentNetworkId| may be used to specify
     * restart of a persistent group.
     *
     * @param persistent Used to request a persistent group to be formed.
     * @param persistentNetworkId Used to specify the restart of a persistent
     *        group. Set to UINT32_MAX for a non-persistent group.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> addGroup(bool persistent, uint32_t persistentNetworkId, addGroup_cb _hidl_cb) = 0;

    /**
     * Return callback for removeGroup
     */
    using removeGroup_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Terminate a P2P group. If a new virtual network interface was used for
     * the group, it must also be removed. The network interface name of the
     * group interface is used as a parameter for this command.
     *
     * @param groupIfName Group interface name to use.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> removeGroup(const ::android::hardware::hidl_string& groupIfName, removeGroup_cb _hidl_cb) = 0;

    /**
     * Return callback for reject
     */
    using reject_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Reject connection attempt from a peer (specified with a device
     * address). This is a mechanism to reject a pending group owner negotiation
     * with a peer and request to automatically block any further connection or
     * discovery of the peer.
     *
     * @param peerAddress MAC address of the device to reject.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> reject(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, reject_cb _hidl_cb) = 0;

    /**
     * Return callback for invite
     */
    using invite_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Invite a device to a persistent group.
     * If the peer device is the group owner of the persistent group, the peer
     * parameter is not needed. Otherwise it is used to specify which
     * device to invite. |goDeviceAddress| parameter may be used to override
     * the group owner device address for Invitation Request should it not be
     * known for some reason (this should not be needed in most cases).
     *
     * @param groupIfName Group interface name to use.
     * @param goDeviceAddress MAC address of the group owner device.
     * @param peerAddress MAC address of the device to invite.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> invite(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, invite_cb _hidl_cb) = 0;

    /**
     * Return callback for reinvoke
     */
    using reinvoke_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Reinvoke a device from a persistent group.
     *
     * @param persistentNetworkId Used to specify the persistent group.
     * @param peerAddress MAC address of the device to reinvoke.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> reinvoke(uint32_t persistentNetworkId, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, reinvoke_cb _hidl_cb) = 0;

    /**
     * Return callback for configureExtListen
     */
    using configureExtListen_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Configure Extended Listen Timing.
     *
     * If enabled, listen state must be entered every |intervalInMillis| for at
     * least |periodInMillis|. Both values have acceptable range of 1-65535
     * (with interval obviously having to be larger than or equal to duration).
     * If the P2P module is not idle at the time the Extended Listen Timing
     * timeout occurs, the Listen State operation must be skipped.
     *
     * @param periodInMillis Period in milliseconds.
     * @param intervalInMillis Interval in milliseconds.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> configureExtListen(uint32_t periodInMillis, uint32_t intervalInMillis, configureExtListen_cb _hidl_cb) = 0;

    /**
     * Return callback for setListenChannel
     */
    using setListenChannel_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set P2P Listen channel.
     *
     * When specifying a social channel on the 2.4 GHz band (1/6/11) there is no
     * need to specify the operating class since it defaults to 81. When
     * specifying a social channel on the 60 GHz band (2), specify the 60 GHz
     * operating class (180).
     *
     * @param channel Wifi channel. eg, 1, 6, 11.
     * @param operatingClass Operating Class indicates the channel set of the AP
     *        indicated by this BSSID
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setListenChannel(uint32_t channel, uint32_t operatingClass, setListenChannel_cb _hidl_cb) = 0;

    /**
     * Return callback for setDisallowedFrequencies
     */
    using setDisallowedFrequencies_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set P2P disallowed frequency ranges.
     *
     * Specify ranges of frequencies that are disallowed for any p2p operations.
     *
     * @param ranges List of ranges which needs to be disallowed.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setDisallowedFrequencies(const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange>& ranges, setDisallowedFrequencies_cb _hidl_cb) = 0;

    /**
     * Return callback for getSsid
     */
    using getSsid_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_vec<uint8_t>& ssid)>;
    /**
     * Gets the operational SSID of the device.
     *
     * @param peerAddress MAC address of the peer.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return ssid SSID of the device
     */
    virtual ::android::hardware::Return<void> getSsid(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, getSsid_cb _hidl_cb) = 0;

    /**
     * Return callback for getGroupCapability
     */
    using getGroupCapability_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> capabilities)>;
    /**
     * Gets the capability of the group which the device is a
     * member of.
     *
     * @param peerAddress MAC address of the peer.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return capabilityMask Combination of |P2pGroupCapabilityMask| values.
     */
    virtual ::android::hardware::Return<void> getGroupCapability(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, getGroupCapability_cb _hidl_cb) = 0;

    /**
     * Return callback for addBonjourService
     */
    using addBonjourService_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * This command can be used to add a bonjour service.
     *
     * @param query Hex dump of the query data.
     * @param return Hex dump of the response data.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> addBonjourService(const ::android::hardware::hidl_vec<uint8_t>& query, const ::android::hardware::hidl_vec<uint8_t>& response, addBonjourService_cb _hidl_cb) = 0;

    /**
     * Return callback for removeBonjourService
     */
    using removeBonjourService_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * This command can be used to remove a bonjour service.
     *
     * @param query Hex dump of the query data.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NOT_STARTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> removeBonjourService(const ::android::hardware::hidl_vec<uint8_t>& query, removeBonjourService_cb _hidl_cb) = 0;

    /**
     * Return callback for addUpnpService
     */
    using addUpnpService_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * This command can be used to add a UPNP service.
     *
     * @param version Version to be used.
     * @package serviceName Service name to be used.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> addUpnpService(uint32_t version, const ::android::hardware::hidl_string& serviceName, addUpnpService_cb _hidl_cb) = 0;

    /**
     * Return callback for removeUpnpService
     */
    using removeUpnpService_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * This command can be used to remove a UPNP service.
     *
     * @param version Version to be used.
     * @package serviceName Service name to be used.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NOT_STARTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> removeUpnpService(uint32_t version, const ::android::hardware::hidl_string& serviceName, removeUpnpService_cb _hidl_cb) = 0;

    /**
     * Return callback for flushServices
     */
    using flushServices_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * This command can be used to flush all services from the
     * device.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> flushServices(flushServices_cb _hidl_cb) = 0;

    /**
     * Return callback for requestServiceDiscovery
     */
    using requestServiceDiscovery_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, uint64_t identifier)>;
    /**
     * Schedule a P2P service discovery request. The parameters for this command
     * are the device address of the peer device (or 00:00:00:00:00:00 for
     * wildcard query that is sent to every discovered P2P peer that supports
     * service discovery) and P2P Service Query TLV(s) as hexdump.
     *
     * @param peerAddress MAC address of the device to discover.
     * @param query Hex dump of the query data.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return identifier Identifier for the request. Can be used to cancel the
     *         request.
     */
    virtual ::android::hardware::Return<void> requestServiceDiscovery(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, const ::android::hardware::hidl_vec<uint8_t>& query, requestServiceDiscovery_cb _hidl_cb) = 0;

    /**
     * Return callback for cancelServiceDiscovery
     */
    using cancelServiceDiscovery_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Cancel a previous service discovery request.
     *
     * @return identifier Identifier for the request to cancel.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_NOT_STARTED|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> cancelServiceDiscovery(uint64_t identifier, cancelServiceDiscovery_cb _hidl_cb) = 0;

    /**
     * Return callback for setMiracastMode
     */
    using setMiracastMode_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to set Miracast mode.
     *
     * @param mode Mode of Miracast.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setMiracastMode(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode mode, setMiracastMode_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPbc
     */
    using startWpsPbc_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate WPS Push Button setup.
     * The PBC operation requires that a button is also pressed at the
     * AP/Registrar at about the same time (2 minute window).
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startWpsPbc(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPbc_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPinKeypad
     */
    using startWpsPinKeypad_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate WPS Pin Keypad setup.
     *
     * @param groupIfName Group interface name to use.
     * @param pin 8 digit pin to be used.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startWpsPinKeypad(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_string& pin, startWpsPinKeypad_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPinDisplay
     */
    using startWpsPinDisplay_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_string& generatedPin)>;
    /**
     * Initiate WPS Pin Display setup.
     *
     * @param groupIfName Group interface name to use.
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return generatedPin 8 digit pin generated.
     */
    virtual ::android::hardware::Return<void> startWpsPinDisplay(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPinDisplay_cb _hidl_cb) = 0;

    /**
     * Return callback for cancelWps
     */
    using cancelWps_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Cancel any ongoing WPS operations.
     *
     * @param groupIfName Group interface name to use.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> cancelWps(const ::android::hardware::hidl_string& groupIfName, cancelWps_cb _hidl_cb) = 0;

    /**
     * Return callback for enableWfd
     */
    using enableWfd_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Enable/Disable Wifi Display.
     *
     * @param enable true to enable, false to disable.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> enableWfd(bool enable, enableWfd_cb _hidl_cb) = 0;

    /**
     * Return callback for setWfdDeviceInfo
     */
    using setWfdDeviceInfo_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set Wifi Display device info.
     *
     * @param info WFD device info as described in section 5.1.2 of WFD technical
     *        specification v1.0.0.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setWfdDeviceInfo(const ::android::hardware::hidl_array<uint8_t, 6>& info, setWfdDeviceInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for createNfcHandoverRequestMessage
     */
    using createNfcHandoverRequestMessage_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_vec<uint8_t>& request)>;
    /**
     * Creates a NFC handover request message.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return request Bytes representing the handover request as specified in
     *         section 3.1.1 of NFC Connection Handover 1.2 Technical
     *         Specification.
     */
    virtual ::android::hardware::Return<void> createNfcHandoverRequestMessage(createNfcHandoverRequestMessage_cb _hidl_cb) = 0;

    /**
     * Return callback for createNfcHandoverSelectMessage
     */
    using createNfcHandoverSelectMessage_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_vec<uint8_t>& select)>;
    /**
     * Creates a NFC handover select message.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return select Bytes representing the handover select as specified in
     *         section 3.1.2 of NFC Connection Handover 1.2 Technical
     *         Specification.
     */
    virtual ::android::hardware::Return<void> createNfcHandoverSelectMessage(createNfcHandoverSelectMessage_cb _hidl_cb) = 0;

    /**
     * Return callback for reportNfcHandoverResponse
     */
    using reportNfcHandoverResponse_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Report the response of the NFC handover request.
     *
     * @param request Bytes representing the handover request as specified in
     *        section 3.1.1 of NFC Connection Handover 1.2 Technical
     *        Specification.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> reportNfcHandoverResponse(const ::android::hardware::hidl_vec<uint8_t>& request, reportNfcHandoverResponse_cb _hidl_cb) = 0;

    /**
     * Return callback for reportNfcHandoverInitiation
     */
    using reportNfcHandoverInitiation_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Report the initiation of the NFC handover select.
     *
     * @param select Bytes representing the handover select as specified in
     *        section 3.1.2 of NFC Connection Handover 1.2 Technical
     *        Specification.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> reportNfcHandoverInitiation(const ::android::hardware::hidl_vec<uint8_t>& select, reportNfcHandoverInitiation_cb _hidl_cb) = 0;

    /**
     * Return callback for saveConfig
     */
    using saveConfig_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Persist the current configuration to disk.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> saveConfig(saveConfig_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISupplicantP2pIface> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIface> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIface> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantP2pIface> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISupplicantP2pIface> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIface> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIface> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantP2pIface> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& rhs);

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::PBC) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::PBC)) {
        os += (first ? "" : " | ");
        os += "PBC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::PBC;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::DISPLAY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::DISPLAY)) {
        os += (first ? "" : " | ");
        os += "DISPLAY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::DISPLAY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::KEYPAD) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::KEYPAD)) {
        os += (first ? "" : " | ");
        os += "KEYPAD";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::KEYPAD;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::PBC) {
        return "PBC";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::DISPLAY) {
        return "DISPLAY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::KEYPAD) {
        return "KEYPAD";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".min = ";
    os += ::android::hardware::toString(o.min);
    os += ", .max = ";
    os += ::android::hardware::toString(o.max);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& rhs) {
    if (lhs.min != rhs.min) {
        return false;
    }
    if (lhs.max != rhs.max) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::DISABLED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::DISABLED)) {
        os += (first ? "" : " | ");
        os += "DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::DISABLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SOURCE) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SOURCE)) {
        os += (first ? "" : " | ");
        os += "SOURCE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SOURCE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SINK) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SINK)) {
        os += (first ? "" : " | ");
        os += "SINK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SINK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::DISABLED) {
        return "DISABLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SOURCE) {
        return "SOURCE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SINK) {
        return "SINK";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace supplicant
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod, 3> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::PBC,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::DISPLAY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod::KEYPAD,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode, 3> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::DISABLED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SOURCE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode::SINK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACE_H
