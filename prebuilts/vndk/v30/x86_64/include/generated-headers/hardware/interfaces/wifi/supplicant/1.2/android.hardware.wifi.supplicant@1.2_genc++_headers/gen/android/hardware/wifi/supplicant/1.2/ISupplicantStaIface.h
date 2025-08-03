#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_ISUPPLICANTSTAIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_ISUPPLICANTSTAIFACE_H

#include <android/hardware/wifi/supplicant/1.0/types.h>
#include <android/hardware/wifi/supplicant/1.1/ISupplicantStaIface.h>
#include <android/hardware/wifi/supplicant/1.2/ISupplicantStaIfaceCallback.h>
#include <android/hardware/wifi/supplicant/1.2/ISupplicantStaNetwork.h>
#include <android/hardware/wifi/supplicant/1.2/types.h>

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
namespace V1_2 {

/**
 * Interface exposed by the supplicant for each station mode network
 * interface (e.g wlan0) it controls.
 */
struct ISupplicantStaIface : public ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIface {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.2::ISupplicantStaIface"
     */
    static const char* descriptor;

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
     * @param callback An instance of the |ISupplicantStaIfaceCallback| HIDL
     *        interface object.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& callback, registerCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for reassociate
     */
    using reassociate_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Reconnect to the currently active network, even if we are already
     * connected.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> reassociate(reassociate_cb _hidl_cb) = 0;

    /**
     * Return callback for reconnect
     */
    using reconnect_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Reconnect to the currently active network, if we are currently
     * disconnected.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_NOT_DISCONNECTED|
     */
    virtual ::android::hardware::Return<void> reconnect(reconnect_cb _hidl_cb) = 0;

    /**
     * Return callback for disconnect
     */
    using disconnect_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Disconnect from the current active network.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> disconnect(disconnect_cb _hidl_cb) = 0;

    /**
     * Return callback for setPowerSave
     */
    using setPowerSave_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Turn on/off power save mode for the interface.
     *
     * @param enable Indicate if power save is to be turned on/off.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> setPowerSave(bool enable, setPowerSave_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateTdlsDiscover
     */
    using initiateTdlsDiscover_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate TDLS discover with the provided peer MAC address.
     *
     * @param macAddress MAC address of the peer.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> initiateTdlsDiscover(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsDiscover_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateTdlsSetup
     */
    using initiateTdlsSetup_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate TDLS setup with the provided peer MAC address.
     *
     * @param macAddress MAC address of the peer.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> initiateTdlsSetup(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsSetup_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateTdlsTeardown
     */
    using initiateTdlsTeardown_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate TDLS teardown with the provided peer MAC address.
     *
     * @param macAddress MAC address of the peer.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> initiateTdlsTeardown(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsTeardown_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateAnqpQuery
     */
    using initiateAnqpQuery_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate ANQP (for IEEE 802.11u Interworking/Hotspot 2.0) queries with the
     * specified access point.
     * The ANQP data fetched must be returned in the
     * |ISupplicantStaIfaceCallback.onAnqpQueryDone| callback.
     *
     * @param macAddress MAC address of the access point.
     * @param infoElements List of information elements to query for.
     * @param subtypes List of HS20 subtypes to query for.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> initiateAnqpQuery(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::AnqpInfoId>& infoElements, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::Hs20AnqpSubtypes>& subTypes, initiateAnqpQuery_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateHs20IconQuery
     */
    using initiateHs20IconQuery_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate the Hotspot 2.0 icon query with the specified accesss point.
     * The icon data fetched must be returned in the
     * |ISupplicantStaIfaceCallback.onHs20IconQueryDone| callback.
     *
     * @param macAddress MAC address of the access point.
     * @param fileName Name of the file to request from the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> initiateHs20IconQuery(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, const ::android::hardware::hidl_string& fileName, initiateHs20IconQuery_cb _hidl_cb) = 0;

    /**
     * Return callback for getMacAddress
     */
    using getMacAddress_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_array<uint8_t, 6>& macAddr)>;
    /**
     * Send driver command to get MAC address of the device.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return macAddr MAC address of the device.
     */
    virtual ::android::hardware::Return<void> getMacAddress(getMacAddress_cb _hidl_cb) = 0;

    /**
     * Return callback for startRxFilter
     */
    using startRxFilter_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to start RX filter.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startRxFilter(startRxFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for stopRxFilter
     */
    using stopRxFilter_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to stop RX filter.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> stopRxFilter(stopRxFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for addRxFilter
     */
    using addRxFilter_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to add the specified RX filter.
     *
     * @param type Type of filter.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> addRxFilter(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::RxFilterType type, addRxFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for removeRxFilter
     */
    using removeRxFilter_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to remove the specified RX filter.
     *
     * @param type Type of filter.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> removeRxFilter(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::RxFilterType type, removeRxFilter_cb _hidl_cb) = 0;

    /**
     * Return callback for setBtCoexistenceMode
     */
    using setBtCoexistenceMode_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to set Bluetooth coexistence mode.
     *
     * @param mode Mode of Bluetooth coexistence.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setBtCoexistenceMode(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::BtCoexistenceMode mode, setBtCoexistenceMode_cb _hidl_cb) = 0;

    /**
     * Return callback for setBtCoexistenceScanModeEnabled
     */
    using setBtCoexistenceScanModeEnabled_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to set Bluetooth coexistence scan mode.
     * When this mode is on, some of the low-level scan parameters
     * used by the driver are changed to reduce interference
     * with A2DP streaming.
     *
     * @param enable true to enable, false to disable.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setBtCoexistenceScanModeEnabled(bool enable, setBtCoexistenceScanModeEnabled_cb _hidl_cb) = 0;

    /**
     * Return callback for setSuspendModeEnabled
     */
    using setSuspendModeEnabled_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to set suspend optimizations for power save.
     *
     * @param enable true to enable, false to disable.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setSuspendModeEnabled(bool enable, setSuspendModeEnabled_cb _hidl_cb) = 0;

    /**
     * Return callback for setCountryCode
     */
    using setCountryCode_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Send driver command to set country code.
     *
     * @param code 2 byte country code (as defined in ISO 3166) to set.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setCountryCode(const ::android::hardware::hidl_array<int8_t, 2>& code, setCountryCode_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsRegistrar
     */
    using startWpsRegistrar_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate WPS setup in registrar role to learn the current AP configuration.
     *
     * @param bssid BSSID of the AP.
     * @param pin Pin of the AP.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startWpsRegistrar(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::hidl_string& pin, startWpsRegistrar_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPbc
     */
    using startWpsPbc_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate WPS Push Button setup.
     * The PBC operation requires that a button is also pressed at the
     * AP/Registrar at about the same time (2 minute window).
     *
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startWpsPbc(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPbc_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPinKeypad
     */
    using startWpsPinKeypad_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Initiate WPS Pin Keypad setup.
     *
     * @param pin 8 digit pin to be used.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> startWpsPinKeypad(const ::android::hardware::hidl_string& pin, startWpsPinKeypad_cb _hidl_cb) = 0;

    /**
     * Return callback for startWpsPinDisplay
     */
    using startWpsPinDisplay_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_string& generatedPin)>;
    /**
     * Initiate WPS Pin Display setup.
     *
     * @param bssid BSSID of the AP. Use zero'ed bssid to indicate wildcard.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     * @return generatedPin 8 digit pin generated.
     */
    virtual ::android::hardware::Return<void> startWpsPinDisplay(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPinDisplay_cb _hidl_cb) = 0;

    /**
     * Return callback for cancelWps
     */
    using cancelWps_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Cancel any ongoing WPS operations.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> cancelWps(cancelWps_cb _hidl_cb) = 0;

    /**
     * Return callback for setExternalSim
     */
    using setExternalSim_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Use external processing for SIM/USIM operations
     *
     * @param useExternalSim true to use external, false otherwise.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> setExternalSim(bool useExternalSim, setExternalSim_cb _hidl_cb) = 0;

    /**
     * Return callback for addExtRadioWork
     */
    using addExtRadioWork_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, uint32_t id)>;
    /**
     * External programs can request supplicant to not start offchannel
     * operations during other tasks that may need exclusive control of the
     * radio.
     *
     * This method can be used to reserve a slot for radio access. If freq is
     * specified, other radio work items on the same channel can be completed in
     * parallel. Otherwise, all other radio work items are blocked during
     * execution. Timeout must be set to |ExtRadioWorkDefaults.TIMEOUT_IN_SECS|
     * seconds by default to avoid blocking supplicant operations on the iface
     * for excessive time. If a longer (or shorter) safety timeout is needed,
     * that may be specified with the optional timeout parameter. This command
     * returns an identifier for the radio work item.
     *
     * Once the radio work item has been started,
     * |ISupplicant.onExtRadioWorkStart| callback is indicated that the external
     * processing can start.
     *
     * @param name Name for the radio work being added.
     * @param freqInMhz Frequency to specify. Set to 0 for all channels.
     * @param timeoutInSec Timeout tospecify. Set to 0 for default timeout.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     * @return id Identifier for this radio work.
     */
    virtual ::android::hardware::Return<void> addExtRadioWork(const ::android::hardware::hidl_string& name, uint32_t freqInMhz, uint32_t timeoutInSec, addExtRadioWork_cb _hidl_cb) = 0;

    /**
     * Return callback for removeExtRadioWork
     */
    using removeExtRadioWork_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Indicates to supplicant that the external radio work has completed.
     * This allows other radio works to be performed. If this method is not
     * invoked (e.g., due to the external program terminating), supplicant
     * must time out the radio work item on the iface and send
     * |ISupplicantCallback.onExtRadioWorkTimeout| event to indicate
     * that this has happened.
     *
     * This method may also be used to cancel items that have been scheduled
     * via |addExtRadioWork|, but have not yet been started (notified via
     * |ISupplicantCallback.onExtRadioWorkStart|).
     *
     * @return id Identifier generated for the radio work addition
     *         (using |addExtRadioWork|).
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> removeExtRadioWork(uint32_t id, removeExtRadioWork_cb _hidl_cb) = 0;

    /**
     * Return callback for enableAutoReconnect
     */
    using enableAutoReconnect_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Enable/Disable auto reconnect to networks.
     * Use this to prevent wpa_supplicant from trying to connect to networks
     * on its own.
     *
     * @param enable true to enable, false to disable.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DISABLED|
     */
    virtual ::android::hardware::Return<void> enableAutoReconnect(bool enable, enableAutoReconnect_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback_1_1
     */
    using registerCallback_1_1_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Register for callbacks from this interface.
     *
     * These callbacks are invoked for events that are specific to this interface.
     * Registration of multiple callback objects is supported. These objects must
     * be automatically deleted when the corresponding client process is dead or
     * if this interface is removed.
     *
     * @param callback An instance of the |ISupplicantStaIfaceCallback| HIDL
     *        interface object.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> registerCallback_1_1(const ::android::sp<::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback>& callback, registerCallback_1_1_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback_1_2
     */
    using registerCallback_1_2_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Register for callbacks from this interface.
     *
     * These callbacks are invoked for events that are specific to this interface.
     * Registration of multiple callback objects is supported. These objects must
     * be automatically deleted when the corresponding client process is dead or
     * if this interface is removed.
     *
     * @param callback An instance of the |ISupplicantStaIfaceCallback| HIDL
     *        interface object.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> registerCallback_1_2(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback>& callback, registerCallback_1_2_cb _hidl_cb) = 0;

    /**
     * Return callback for getKeyMgmtCapabilities
     */
    using getKeyMgmtCapabilities_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask)>;
    /**
     * Get Key management capabilities of the device
     *
     * @return status Status of the operation, and a bitmap of key management mask.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> getKeyMgmtCapabilities(getKeyMgmtCapabilities_cb _hidl_cb) = 0;

    /**
     * Return callback for addDppPeerUri
     */
    using addDppPeerUri_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, uint32_t id)>;
    /**
     * Add a DPP peer URI. URI is acquired externally, e.g. by scanning a QR code
     *
     * @param uri Peer's DPP URI.
     * @return status Status of the operation, and an ID for the URI.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> addDppPeerUri(const ::android::hardware::hidl_string& uri, addDppPeerUri_cb _hidl_cb) = 0;

    /**
     * Return callback for removeDppUri
     */
    using removeDppUri_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Remove a DPP peer URI.
     *
     * @param id The ID of the URI, as returned by |addDppPeerUri|.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> removeDppUri(uint32_t id, removeDppUri_cb _hidl_cb) = 0;

    /**
     * Return callback for startDppConfiguratorInitiator
     */
    using startDppConfiguratorInitiator_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Start DPP in Configurator-Initiator mode.
     *
     * @param peerBootstrapId Peer device's URI ID.
     * @param ownBootstrapId Local device's URI ID (0 for none, optional).
     * @param ssid Network SSID to send to peer (SAE/PSK mode).
     * @param password Network password to send to peer (SAE/PSK mode).
     * @param psk Network PSK to send to peer (PSK mode only). Either password or psk should be set.
     * @param netRole Role to configure the peer, |DppNetRole.DPP_NET_ROLE_STA| or
     *        |DppNetRole.DPP_NET_ROLE_AP|.
     * @param securityAkm Security AKM to use (See DppAkm).
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> startDppConfiguratorInitiator(uint32_t peerBootstrapId, uint32_t ownBootstrapId, const ::android::hardware::hidl_string& ssid, const ::android::hardware::hidl_string& password, const ::android::hardware::hidl_string& psk, ::android::hardware::wifi::supplicant::V1_2::DppNetRole netRole, ::android::hardware::wifi::supplicant::V1_2::DppAkm securityAkm, startDppConfiguratorInitiator_cb _hidl_cb) = 0;

    /**
     * Return callback for startDppEnrolleeInitiator
     */
    using startDppEnrolleeInitiator_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Start DPP in Enrollee-Initiator mode.
     *
     * @param peerBootstrapId Peer device's URI ID.
     * @param ownBootstrapId Local device's URI ID (0 for none, optional).
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> startDppEnrolleeInitiator(uint32_t peerBootstrapId, uint32_t ownBootstrapId, startDppEnrolleeInitiator_cb _hidl_cb) = 0;

    /**
     * Return callback for stopDppInitiator
     */
    using stopDppInitiator_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Stop DPP Initiator operation.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_NETWORK_INVALID|
     */
    virtual ::android::hardware::Return<void> stopDppInitiator(stopDppInitiator_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISupplicantStaIface> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIface> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIface> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantStaIface> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISupplicantStaIface> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIface> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIface> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantStaIface> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_2
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_ISUPPLICANTSTAIFACE_H
