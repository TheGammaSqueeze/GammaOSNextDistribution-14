#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIP_H

#include <android/hardware/wifi/1.0/types.h>
#include <android/hardware/wifi/1.1/IWifiChip.h>
#include <android/hardware/wifi/1.2/IWifiChipEventCallback.h>

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
 * Interface that represents a chip that must be configured as a single unit.
 * The HAL/driver/firmware will be responsible for determining which phy is used
 * to perform operations like NAN, RTT, etc.
 */
struct IWifiChip : public ::android::hardware::wifi::V1_1::IWifiChip {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi@1.2::IWifiChip"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class ChipCapabilityMask : uint32_t;
    enum class TxPowerScenario : uint32_t;

    /**
     * Capabilities exposed by this chip.
     */
    enum class ChipCapabilityMask : uint32_t {
        /**
         * Memory dump of Firmware.
         */
        DEBUG_MEMORY_FIRMWARE_DUMP = 1u /* 1 << 0 */,
        /**
         * Memory dump of Driver.
         */
        DEBUG_MEMORY_DRIVER_DUMP = 2u /* 1 << 1 */,
        /**
         * Connectivity events reported via debug ring buffer.
         */
        DEBUG_RING_BUFFER_CONNECT_EVENT = 4u /* 1 << 2 */,
        /**
         * Power events reported via debug ring buffer.
         */
        DEBUG_RING_BUFFER_POWER_EVENT = 8u /* 1 << 3 */,
        /**
         * Wakelock events reported via debug ring buffer.
         */
        DEBUG_RING_BUFFER_WAKELOCK_EVENT = 16u /* 1 << 4 */,
        /**
         * Vendor data reported via debug ring buffer.
         * This mostly contains firmware event logs.
         */
        DEBUG_RING_BUFFER_VENDOR_DATA = 32u /* 1 << 5 */,
        /**
         * Host wake reasons stats collection.
         */
        DEBUG_HOST_WAKE_REASON_STATS = 64u /* 1 << 6 */,
        /**
         * Error alerts.
         */
        DEBUG_ERROR_ALERTS = 128u /* 1 << 7 */,
        /**
         * Set/Reset Tx Power limits.
         */
        SET_TX_POWER_LIMIT = 256u /* 1 << 8 */,
        /**
         * Device to Device RTT.
         */
        D2D_RTT = 512u /* 1 << 9 */,
        /**
         * Device to AP RTT.
         */
        D2AP_RTT = 1024u /* 1 << 10 */,
        /**
         * Set/Reset Tx Power limits.
         */
        USE_BODY_HEAD_SAR = 2048u /* 1 << 11 */,
    };

    /**
     * List of preset wifi radio TX power levels for different scenarios.
     * The actual power values (typically varies based on the channel,
     * 802.11 connection type, number of MIMO streams, etc) for each scenario
     * is defined by the OEM as a BDF file since it varies for each wifi chip
     * vendor and device.
     */
    enum class TxPowerScenario : uint32_t {
        VOICE_CALL = 0u,
        ON_HEAD_CELL_OFF = 1u,
        ON_HEAD_CELL_ON = 2u,
        ON_BODY_CELL_OFF = 3u,
        ON_BODY_CELL_ON = 4u,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getId
     */
    using getId_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, uint32_t id)>;
    /**
     * Get the id assigned to this chip.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return id Assigned chip Id.
     */
    virtual ::android::hardware::Return<void> getId(getId_cb _hidl_cb) = 0;

    /**
     * Return callback for registerEventCallback
     */
    using registerEventCallback_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Requests notifications of significant events on this chip. Multiple calls
     * to this must register multiple callbacks each of which must receive all
     * events.
     *
     * @param callback An instance of the |IWifiChipEventCallback| HIDL interface
     *        object.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     */
    virtual ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback>& callback, registerEventCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for getCapabilities
     */
    using getCapabilities_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask> capabilities)>;
    /**
     * Get the capabilities supported by this chip.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_AVAILABLE|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     * @return capabilities Bitset of |ChipCapabilityMask| values.
     */
    virtual ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) = 0;

    /**
     * Return callback for getAvailableModes
     */
    using getAvailableModes_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::IWifiChip::ChipMode>& modes)>;
    /**
     * Get the set of operation modes that the chip supports.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return modes List of modes supported by the device.
     */
    virtual ::android::hardware::Return<void> getAvailableModes(getAvailableModes_cb _hidl_cb) = 0;

    /**
     * Return callback for configureChip
     */
    using configureChip_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Configure the Chip.
     * This may NOT be called to reconfigure a chip due to an internal
     * limitation. Calling this when chip is already configured in a different
     * mode must trigger an ERROR_NOT_SUPPORTED failure.
     * If you want to do reconfiguration, please call IWifi.stop() and IWifi.start()
     * to restart Wifi HAL before calling this.
     * Any existing |IWifiIface| objects must be marked invalid after this call.
     * If this fails then the chips is now in an undefined state and
     * configureChip must be called again.
     * Must trigger |IWifiChipEventCallback.onChipReconfigured| on success.
     * Must trigger |IWifiEventCallback.onFailure| on failure.
     *
     * @param modeId The mode that the chip must switch to, corresponding to the
     *        id property of the target ChipMode.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_AVAILABLE|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> configureChip(uint32_t modeId, configureChip_cb _hidl_cb) = 0;

    /**
     * Return callback for getMode
     */
    using getMode_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, uint32_t modeId)>;
    /**
     * Get the current mode that the chip is in.
     *
     * @return modeId The mode that the chip is currently configured to,
     *         corresponding to the id property of the target ChipMode.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     */
    virtual ::android::hardware::Return<void> getMode(getMode_cb _hidl_cb) = 0;

    /**
     * Return callback for requestChipDebugInfo
     */
    using requestChipDebugInfo_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& chipDebugInfo)>;
    /**
     * Request information about the chip.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_AVAILABLE|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     * @return chipDebugInfo Instance of |ChipDebugInfo|.
     */
    virtual ::android::hardware::Return<void> requestChipDebugInfo(requestChipDebugInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for requestDriverDebugDump
     */
    using requestDriverDebugDump_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<uint8_t>& blob)>;
    /**
     * Request vendor debug info from the driver.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_AVAILABLE|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     * @param blob Vector of bytes retrieved from the driver.
     */
    virtual ::android::hardware::Return<void> requestDriverDebugDump(requestDriverDebugDump_cb _hidl_cb) = 0;

    /**
     * Return callback for requestFirmwareDebugDump
     */
    using requestFirmwareDebugDump_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<uint8_t>& blob)>;
    /**
     * Request vendor debug info from the firmware.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_AVAILABLE|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     * @param blob Vector of bytes retrieved from the driver.
     */
    virtual ::android::hardware::Return<void> requestFirmwareDebugDump(requestFirmwareDebugDump_cb _hidl_cb) = 0;

    /**
     * Return callback for createApIface
     */
    using createApIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiApIface>& iface)>;
    /**
     * Create an AP iface on the chip.
     *
     * Depending on the mode the chip is configured in, the interface creation
     * may fail (code: |ERROR_NOT_AVAILABLE|) if we've already reached the maximum
     * allowed (specified in |ChipIfaceCombination|) number of ifaces of the AP
     * type.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|
     * @return iface HIDL interface object representing the iface if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> createApIface(createApIface_cb _hidl_cb) = 0;

    /**
     * Return callback for getApIfaceNames
     */
    using getApIfaceNames_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& ifnames)>;
    /**
     * List all the AP iface names configured on the chip.
     * The corresponding |IWifiApIface| object for any iface are
     * retrieved using |getApIface| method.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return ifnames List of all AP iface names on the chip.
     */
    virtual ::android::hardware::Return<void> getApIfaceNames(getApIfaceNames_cb _hidl_cb) = 0;

    /**
     * Return callback for getApIface
     */
    using getApIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiApIface>& iface)>;
    /**
     * Gets a HIDL interface object for the AP Iface corresponding
     * to the provided ifname.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     * @return iface HIDL interface object representing the iface if
     *         it exists, null otherwise.
     */
    virtual ::android::hardware::Return<void> getApIface(const ::android::hardware::hidl_string& ifname, getApIface_cb _hidl_cb) = 0;

    /**
     * Return callback for removeApIface
     */
    using removeApIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Removes the AP Iface with the provided ifname.
     * Any further calls on the corresponding |IWifiApIface| HIDL interface
     * object must fail.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     */
    virtual ::android::hardware::Return<void> removeApIface(const ::android::hardware::hidl_string& ifname, removeApIface_cb _hidl_cb) = 0;

    /**
     * Return callback for createNanIface
     */
    using createNanIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>& iface)>;
    /**
     * Create a NAN iface on the chip.
     *
     * Depending on the mode the chip is configured in, the interface creation
     * may fail (code: |ERROR_NOT_AVAILABLE|) if we've already reached the maximum
     * allowed (specified in |ChipIfaceCombination|) number of ifaces of the NAN
     * type.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|
     * @return iface HIDL interface object representing the iface if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> createNanIface(createNanIface_cb _hidl_cb) = 0;

    /**
     * Return callback for getNanIfaceNames
     */
    using getNanIfaceNames_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& ifnames)>;
    /**
     * List all the NAN iface names configured on the chip.
     * The corresponding |IWifiNanIface| object for any iface are
     * retrieved using |getNanIface| method.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return ifnames List of all NAN iface names on the chip.
     */
    virtual ::android::hardware::Return<void> getNanIfaceNames(getNanIfaceNames_cb _hidl_cb) = 0;

    /**
     * Return callback for getNanIface
     */
    using getNanIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>& iface)>;
    /**
     * Gets a HIDL interface object for the NAN Iface corresponding
     * to the provided ifname.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     * @return iface HIDL interface object representing the iface if
     *         it exists, null otherwise.
     */
    virtual ::android::hardware::Return<void> getNanIface(const ::android::hardware::hidl_string& ifname, getNanIface_cb _hidl_cb) = 0;

    /**
     * Return callback for removeNanIface
     */
    using removeNanIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Removes the NAN Iface with the provided ifname.
     * Any further calls on the corresponding |IWifiNanIface| HIDL interface
     * object must fail.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     */
    virtual ::android::hardware::Return<void> removeNanIface(const ::android::hardware::hidl_string& ifname, removeNanIface_cb _hidl_cb) = 0;

    /**
     * Return callback for createP2pIface
     */
    using createP2pIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiP2pIface>& iface)>;
    /**
     * Create a P2P iface on the chip.
     *
     * Depending on the mode the chip is configured in, the interface creation
     * may fail (code: |ERROR_NOT_AVAILABLE|) if we've already reached the maximum
     * allowed (specified in |ChipIfaceCombination|) number of ifaces of the P2P
     * type.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|
     * @return iface HIDL interface object representing the iface if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> createP2pIface(createP2pIface_cb _hidl_cb) = 0;

    /**
     * Return callback for getP2pIfaceNames
     */
    using getP2pIfaceNames_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& ifnames)>;
    /**
     * List all the P2P iface names configured on the chip.
     * The corresponding |IWifiP2pIface| object for any iface are
     * retrieved using |getP2pIface| method.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return ifnames List of all P2P iface names on the chip.
     */
    virtual ::android::hardware::Return<void> getP2pIfaceNames(getP2pIfaceNames_cb _hidl_cb) = 0;

    /**
     * Return callback for getP2pIface
     */
    using getP2pIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiP2pIface>& iface)>;
    /**
     * Gets a HIDL interface object for the P2P Iface corresponding
     * to the provided ifname.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     * @return iface HIDL interface object representing the iface if
     *         it exists, null otherwise.
     */
    virtual ::android::hardware::Return<void> getP2pIface(const ::android::hardware::hidl_string& ifname, getP2pIface_cb _hidl_cb) = 0;

    /**
     * Return callback for removeP2pIface
     */
    using removeP2pIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Removes the P2P Iface with the provided ifname.
     * Any further calls on the corresponding |IWifiP2pIface| HIDL interface
     * object must fail.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     */
    virtual ::android::hardware::Return<void> removeP2pIface(const ::android::hardware::hidl_string& ifname, removeP2pIface_cb _hidl_cb) = 0;

    /**
     * Return callback for createStaIface
     */
    using createStaIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiStaIface>& iface)>;
    /**
     * Create an STA iface on the chip.
     *
     * Depending on the mode the chip is configured in, the interface creation
     * may fail (code: |ERROR_NOT_AVAILABLE|) if we've already reached the maximum
     * allowed (specified in |ChipIfaceCombination|) number of ifaces of the STA
     * type.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|
     * @return iface HIDL interface object representing the iface if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> createStaIface(createStaIface_cb _hidl_cb) = 0;

    /**
     * Return callback for getStaIfaceNames
     */
    using getStaIfaceNames_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& ifnames)>;
    /**
     * List all the STA iface names configured on the chip.
     * The corresponding |IWifiStaIface| object for any iface are
     * retrieved using |getStaIface| method.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     * @return ifnames List of all STA iface names on the chip.
     */
    virtual ::android::hardware::Return<void> getStaIfaceNames(getStaIfaceNames_cb _hidl_cb) = 0;

    /**
     * Return callback for getStaIface
     */
    using getStaIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiStaIface>& iface)>;
    /**
     * Gets a HIDL interface object for the STA Iface corresponding
     * to the provided ifname.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     * @return iface HIDL interface object representing the iface if
     *         it exists, null otherwise.
     */
    virtual ::android::hardware::Return<void> getStaIface(const ::android::hardware::hidl_string& ifname, getStaIface_cb _hidl_cb) = 0;

    /**
     * Return callback for removeStaIface
     */
    using removeStaIface_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Removes the STA Iface with the provided ifname.
     * Any further calls on the corresponding |IWifiStaIface| HIDL interface
     * object must fail.
     *
     * @param ifname Name of the iface.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|
     */
    virtual ::android::hardware::Return<void> removeStaIface(const ::android::hardware::hidl_string& ifname, removeStaIface_cb _hidl_cb) = 0;

    /**
     * Return callback for createRttController
     */
    using createRttController_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::sp<::android::hardware::wifi::V1_0::IWifiRttController>& rtt)>;
    /**
     * Create a RTTController instance.
     *
     * RTT controller can be either:
     * a) Bound to a specific iface by passing in the corresponding |IWifiIface|
     * object in |iface| param, OR
     * b) Let the implementation decide the iface to use for RTT operations by
     * passing null in |iface| param.
     *
     * @param boundIface HIDL interface object representing the iface if
     *        the responder must be bound to a specific iface, null otherwise.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     */
    virtual ::android::hardware::Return<void> createRttController(const ::android::sp<::android::hardware::wifi::V1_0::IWifiIface>& boundIface, createRttController_cb _hidl_cb) = 0;

    /**
     * Return callback for getDebugRingBuffersStatus
     */
    using getDebugRingBuffersStatus_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus>& ringBuffers)>;
    /**
     * WiFi debug ring buffer life cycle is as follow:
     * - At initialization time, framework must call |getDebugRingBuffersStatus|.
     *   to obtain the names and list of supported ring buffers.
     *   The driver may expose several different rings each holding a different
     *   type of data (connection events, power events, etc).
     * - When WiFi operations start framework must call
     *   |startLoggingToDebugRingBuffer| to trigger log collection for a specific
     *   ring. The vebose level for each ring buffer can be specified in this API.
     * - During wifi operations, driver must periodically report per ring data to
     *   framework by invoking the
     *   |IWifiChipEventCallback.onDebugRingBufferDataAvailable| callback.
     * - When capturing a bug report, framework must indicate to driver that all
     *   the data has to be uploaded urgently by calling
     *   |forceDumpToDebugRingBuffer|.
     *
     * The data uploaded by driver must be stored by framework in separate files,
     * with one stream of file per ring. Framework must store the files in pcapng
     * format, allowing for easy merging and parsing with network analyzer tools.
     * TODO: Since we're not longer dumping out the raw data, storing in separate
     * pcapng files for parsing later must not work anymore.
     *
     *
     * API to get the status of all ring buffers supported by driver.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     * @return ringBuffers Vector of |WifiDebugRingBufferStatus| corresponding to the
     *         status of each ring bufffer on the device.
     */
    virtual ::android::hardware::Return<void> getDebugRingBuffersStatus(getDebugRingBuffersStatus_cb _hidl_cb) = 0;

    /**
     * Return callback for startLoggingToDebugRingBuffer
     */
    using startLoggingToDebugRingBuffer_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to trigger the debug data collection.
     *
     * @param ringName represent the name of the ring for which data collection
     *        shall start. This can be retrieved via the corresponding
     *        |WifiDebugRingBufferStatus|.
     * @parm maxIntervalInSec Maximum interval in seconds for driver to invoke
     *       |onDebugRingBufferData|, ignore if zero.
     * @parm minDataSizeInBytes: Minimum data size in buffer for driver to invoke
     *       |onDebugRingBufferData|, ignore if zero.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> startLoggingToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel verboseLevel, uint32_t maxIntervalInSec, uint32_t minDataSizeInBytes, startLoggingToDebugRingBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for forceDumpToDebugRingBuffer
     */
    using forceDumpToDebugRingBuffer_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to force dump data into the corresponding ring buffer.
     * This is to be invoked during bugreport collection.
     *
     * @param ringName represent the name of the ring for which data collection
     *        shall be forced. This can be retrieved via the corresponding
     *        |WifiDebugRingBufferStatus|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.ERROR_NOT_STARTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> forceDumpToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, forceDumpToDebugRingBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for stopLoggingToDebugRingBuffer
     */
    using stopLoggingToDebugRingBuffer_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to stop the debug data collection for all ring buffers.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> stopLoggingToDebugRingBuffer(stopLoggingToDebugRingBuffer_cb _hidl_cb) = 0;

    /**
     * Return callback for getDebugHostWakeReasonStats
     */
    using getDebugHostWakeReasonStats_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats& stats)>;
    /**
     * API to retrieve the wifi wake up reason stats for debugging.
     * The driver is expected to start maintaining these stats once the chip
     * is configured using |configureChip|. These stats must be reset whenever
     * the chip is reconfigured or the HAL is stopped.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     * @return stats Instance of |WifiDebugHostWakeReasonStats|.
     */
    virtual ::android::hardware::Return<void> getDebugHostWakeReasonStats(getDebugHostWakeReasonStats_cb _hidl_cb) = 0;

    /**
     * Return callback for enableDebugErrorAlerts
     */
    using enableDebugErrorAlerts_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to enable/disable alert notifications from the chip.
     * These alerts must be used to notify framework of any fatal error events
     * that the chip encounters via |IWifiChipEventCallback.onDebugErrorAlert| method.
     * Must fail if |ChipCapabilityMask.DEBUG_ERROR_ALERTS| is not set.
     *
     * @param enable true to enable, false to disable.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> enableDebugErrorAlerts(bool enable, enableDebugErrorAlerts_cb _hidl_cb) = 0;

    /**
     * Return callback for selectTxPowerScenario
     */
    using selectTxPowerScenario_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to select one of the preset TX power scenarios.
     *
     * The framework must invoke this method with the appropriate scenario to let
     * the wifi chip change it's transmitting power levels.
     * OEM's should define various power profiles for each of the scenarios
     * above (defined in |TxPowerScenario|).
     *
     * @param scenario One of the preselected scenarios defined in
     *        |TxPowerScenario|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> selectTxPowerScenario(::android::hardware::wifi::V1_1::IWifiChip::TxPowerScenario scenario, selectTxPowerScenario_cb _hidl_cb) = 0;

    /**
     * Return callback for resetTxPowerScenario
     */
    using resetTxPowerScenario_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to reset TX power levels.
     * This is used to indicate the end of the previously selected TX power
     * scenario and let the wifi chip fall back to the default power values.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> resetTxPowerScenario(resetTxPowerScenario_cb _hidl_cb) = 0;

    /**
     * Return callback for selectTxPowerScenario_1_2
     */
    using selectTxPowerScenario_1_2_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * API to select one of the preset TX power scenarios.
     *
     * The framework must invoke this method with the appropriate scenario to let
     * the wifi chip change it's transmitting power levels.
     * OEM's should define various power profiles for each of the scenarios
     * above (defined in |TxPowerScenario|) in a vendor extension.
     *
     * @param scenario One of the preselected scenarios defined in
     *        |TxPowerScenario|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|,
     *         |WifiStatusCode.ERROR_NOT_SUPPORTED|,
     *         |WifiStatusCode.NOT_AVAILABLE|,
     *         |WifiStatusCode.UNKNOWN|
     */
    virtual ::android::hardware::Return<void> selectTxPowerScenario_1_2(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario scenario, selectTxPowerScenario_1_2_cb _hidl_cb) = 0;

    /**
     * Return callback for registerEventCallback_1_2
     */
    using registerEventCallback_1_2_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Requests notifications of significant events on this chip. Multiple calls
     * to this must register multiple callbacks each of which must receive all
     * events.
     *
     * @param callback An instance of the |IWifiChipEventCallback| HIDL interface
     *        object.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_CHIP_INVALID|
     */
    virtual ::android::hardware::Return<void> registerEventCallback_1_2(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>& callback, registerEventCallback_1_2_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChip>> castFrom(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChip>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChip>> castFrom(const ::android::sp<::android::hardware::wifi::V1_1::IWifiChip>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChip>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChip>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiChip>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IWifiChip> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiChip> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiChip> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiChip> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IWifiChip> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiChip> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiChip> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiChip> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask o);
static inline void PrintTo(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask lhs, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask lhs, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario o);
static inline void PrintTo(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario lhs, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario lhs, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChip>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP)) {
        os += (first ? "" : " | ");
        os += "DEBUG_MEMORY_FIRMWARE_DUMP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP)) {
        os += (first ? "" : " | ");
        os += "DEBUG_MEMORY_DRIVER_DUMP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_CONNECT_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_POWER_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_WAKELOCK_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_VENDOR_DATA";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS)) {
        os += (first ? "" : " | ");
        os += "DEBUG_HOST_WAKE_REASON_STATS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS)) {
        os += (first ? "" : " | ");
        os += "DEBUG_ERROR_ALERTS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT)) {
        os += (first ? "" : " | ");
        os += "SET_TX_POWER_LIMIT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2D_RTT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2D_RTT)) {
        os += (first ? "" : " | ");
        os += "D2D_RTT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2D_RTT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2AP_RTT) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2AP_RTT)) {
        os += (first ? "" : " | ");
        os += "D2AP_RTT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2AP_RTT;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::USE_BODY_HEAD_SAR) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::USE_BODY_HEAD_SAR)) {
        os += (first ? "" : " | ");
        os += "USE_BODY_HEAD_SAR";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::USE_BODY_HEAD_SAR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP) {
        return "DEBUG_MEMORY_FIRMWARE_DUMP";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP) {
        return "DEBUG_MEMORY_DRIVER_DUMP";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT) {
        return "DEBUG_RING_BUFFER_CONNECT_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT) {
        return "DEBUG_RING_BUFFER_POWER_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT) {
        return "DEBUG_RING_BUFFER_WAKELOCK_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA) {
        return "DEBUG_RING_BUFFER_VENDOR_DATA";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS) {
        return "DEBUG_HOST_WAKE_REASON_STATS";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS) {
        return "DEBUG_ERROR_ALERTS";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT) {
        return "SET_TX_POWER_LIMIT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2D_RTT) {
        return "D2D_RTT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2AP_RTT) {
        return "D2AP_RTT";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::USE_BODY_HEAD_SAR) {
        return "USE_BODY_HEAD_SAR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::VOICE_CALL) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::VOICE_CALL)) {
        os += (first ? "" : " | ");
        os += "VOICE_CALL";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::VOICE_CALL;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_OFF) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_OFF)) {
        os += (first ? "" : " | ");
        os += "ON_HEAD_CELL_OFF";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_OFF;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_ON) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_ON)) {
        os += (first ? "" : " | ");
        os += "ON_HEAD_CELL_ON";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_ON;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_OFF) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_OFF)) {
        os += (first ? "" : " | ");
        os += "ON_BODY_CELL_OFF";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_OFF;
    }
    if ((o & ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_ON) == static_cast<uint32_t>(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_ON)) {
        os += (first ? "" : " | ");
        os += "ON_BODY_CELL_ON";
        first = false;
        flipped |= ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::VOICE_CALL) {
        return "VOICE_CALL";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_OFF) {
        return "ON_HEAD_CELL_OFF";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_ON) {
        return "ON_HEAD_CELL_ON";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_OFF) {
        return "ON_BODY_CELL_OFF";
    }
    if (o == ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_ON) {
        return "ON_BODY_CELL_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChip>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::V1_2::IWifiChip::descriptor;
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

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask, 12> hidl_enum_values<::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask> = {
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2D_RTT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::D2AP_RTT,
    ::android::hardware::wifi::V1_2::IWifiChip::ChipCapabilityMask::USE_BODY_HEAD_SAR,
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario, 5> hidl_enum_values<::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario> = {
    ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::VOICE_CALL,
    ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_OFF,
    ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_HEAD_CELL_ON,
    ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_OFF,
    ::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario::ON_BODY_CELL_ON,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFICHIP_H
