#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFICHIP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFICHIP_H

#include <android/hardware/wifi/1.0/IWifiApIface.h>
#include <android/hardware/wifi/1.0/IWifiChipEventCallback.h>
#include <android/hardware/wifi/1.0/IWifiIface.h>
#include <android/hardware/wifi/1.0/IWifiNanIface.h>
#include <android/hardware/wifi/1.0/IWifiP2pIface.h>
#include <android/hardware/wifi/1.0/IWifiRttController.h>
#include <android/hardware/wifi/1.0/IWifiStaIface.h>
#include <android/hardware/wifi/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

/**
 * Interface that represents a chip that must be configured as a single unit.
 * The HAL/driver/firmware will be responsible for determining which phy is used
 * to perform operations like NAN, RTT, etc.
 */
struct IWifiChip : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi@1.0::IWifiChip"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct ChipIfaceCombinationLimit;
    struct ChipIfaceCombination;
    struct ChipMode;
    struct ChipDebugInfo;
    enum class ChipCapabilityMask : uint32_t;

    /**
     * Set of interface types with the maximum number of interfaces that can have
     * one of the specified type for a given ChipIfaceCombination. See
     * ChipIfaceCombination for examples.
     */
    struct ChipIfaceCombinationLimit final {
        ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::IfaceType> types __attribute__ ((aligned(8)));
        uint32_t maxIfaces __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit, types) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit, maxIfaces) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit) == 8, "wrong alignment");

    /**
     * Set of interfaces that can operate concurrently when in a given mode. See
     * ChipMode below.
     *
     * For example:
     *   [{STA} <= 2]
     *       At most two STA interfaces are supported
     *       [], [STA], [STA+STA]
     *
     *   [{STA} <= 1, {NAN} <= 1, {AP} <= 1]
     *       Any combination of STA, NAN, AP
     *       [], [STA], [NAN], [AP], [STA+NAN], [STA+AP], [NAN+AP], [STA+NAN+AP]
     *
     *   [{STA} <= 1, {NAN,P2P} <= 1]
     *       Optionally a STA and either NAN or P2P
     *       [], [STA], [STA+NAN], [STA+P2P], [NAN], [P2P]
     *       Not included [NAN+P2P], [STA+NAN+P2P]
     *
     *   [{STA} <= 1, {STA,NAN} <= 1]
     *       Optionally a STA and either a second STA or a NAN
     *       [], [STA], [STA+NAN], [STA+STA], [NAN]
     *       Not included [STA+STA+NAN]
     */
    struct ChipIfaceCombination final {
        ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit> limits __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination, limits) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination) == 8, "wrong alignment");

    /**
     * A mode that the chip can be put in. A mode defines a set of constraints on
     * the interfaces that can exist while in that mode. Modes define a unit of
     * configuration where all interfaces must be torn down to switch to a
     * different mode. Some HALs may only have a single mode, but an example where
     * multiple modes would be required is if a chip has different firmwares with
     * different capabilities.
     *
     * When in a mode, it must be possible to perform any combination of creating
     * and removing interfaces as long as at least one of the
     * ChipIfaceCombinations is satisfied. This means that if a chip has two
     * available combinations, [{STA} <= 1] and [{AP} <= 1] then it is expected
     * that exactly one STA interface or one AP interface can be created, but it
     * is not expected that both a STA and AP interface could be created. If it
     * was then there would be a single available combination
     * [{STA} <=1, {AP} <= 1].
     *
     * When switching between two available combinations it is expected that
     * interfaces only supported by the initial combination must be removed until
     * the target combination is also satisfied. At that point new interfaces
     * satisfying only the target combination can be added (meaning the initial
     * combination limits will no longer satisfied). The addition of these new
     * interfaces must not impact the existence of interfaces that satisfy both
     * combinations.
     *
     * For example, a chip with available combinations:
     *     [{STA} <= 2, {NAN} <=1] and [{STA} <=1, {NAN} <= 1, {AP} <= 1}]
     * If the chip currently has 3 interfaces STA, STA and NAN and wants to add an
     * AP interface in place of one of the STAs then first one of the STA
     * interfaces must be removed and then the AP interface can be created after
     * the STA had been torn down. During this process the remaining STA and NAN
     * interfaces must not be removed/recreated.
     *
     * If a chip does not support this kind of reconfiguration in this mode then
     * the combinations must be separated into two separate modes. Before
     * switching modes all interfaces must be torn down, the mode switch must be
     * enacted and when it completes the new interfaces must be brought up.
     */
    struct ChipMode final {
        /**
         * Id that can be used to put the chip in this mode.
         */
        uint32_t id __attribute__ ((aligned(4)));
        /**
         * A list of the possible interface combinations that the chip can have
         * while in this mode.
         */
        ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination> availableCombinations __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipMode, id) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipMode, availableCombinations) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_0::IWifiChip::ChipMode) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_0::IWifiChip::ChipMode) == 8, "wrong alignment");

    /**
     * Information about the version of the driver and firmware running this chip.
     *
     * The information in these ASCII strings are vendor specific and does not
     * need to follow any particular format. It may be dumped as part of the bug
     * report.
     */
    struct ChipDebugInfo final {
        ::android::hardware::hidl_string driverDescription __attribute__ ((aligned(8)));
        ::android::hardware::hidl_string firmwareDescription __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo, driverDescription) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo, firmwareDescription) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo) == 8, "wrong alignment");

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_0::IWifiChip>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChip>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_0::IWifiChip>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& rhs);

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask o);
static inline void PrintTo(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChip>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".types = ";
    os += ::android::hardware::toString(o.types);
    os += ", .maxIfaces = ";
    os += ::android::hardware::toString(o.maxIfaces);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& rhs) {
    if (lhs.types != rhs.types) {
        return false;
    }
    if (lhs.maxIfaces != rhs.maxIfaces) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombinationLimit& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".limits = ";
    os += ::android::hardware::toString(o.limits);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& rhs) {
    if (lhs.limits != rhs.limits) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipIfaceCombination& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".id = ";
    os += ::android::hardware::toString(o.id);
    os += ", .availableCombinations = ";
    os += ::android::hardware::toString(o.availableCombinations);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& rhs) {
    if (lhs.id != rhs.id) {
        return false;
    }
    if (lhs.availableCombinations != rhs.availableCombinations) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipMode& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".driverDescription = ";
    os += ::android::hardware::toString(o.driverDescription);
    os += ", .firmwareDescription = ";
    os += ::android::hardware::toString(o.firmwareDescription);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& rhs) {
    if (lhs.driverDescription != rhs.driverDescription) {
        return false;
    }
    if (lhs.firmwareDescription != rhs.firmwareDescription) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& lhs, const ::android::hardware::wifi::V1_0::IWifiChip::ChipDebugInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP)) {
        os += (first ? "" : " | ");
        os += "DEBUG_MEMORY_FIRMWARE_DUMP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP)) {
        os += (first ? "" : " | ");
        os += "DEBUG_MEMORY_DRIVER_DUMP";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_CONNECT_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_POWER_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_WAKELOCK_EVENT";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA)) {
        os += (first ? "" : " | ");
        os += "DEBUG_RING_BUFFER_VENDOR_DATA";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS)) {
        os += (first ? "" : " | ");
        os += "DEBUG_HOST_WAKE_REASON_STATS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS;
    }
    if ((o & ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS) == static_cast<uint32_t>(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS)) {
        os += (first ? "" : " | ");
        os += "DEBUG_ERROR_ALERTS";
        first = false;
        flipped |= ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP) {
        return "DEBUG_MEMORY_FIRMWARE_DUMP";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP) {
        return "DEBUG_MEMORY_DRIVER_DUMP";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT) {
        return "DEBUG_RING_BUFFER_CONNECT_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT) {
        return "DEBUG_RING_BUFFER_POWER_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT) {
        return "DEBUG_RING_BUFFER_WAKELOCK_EVENT";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA) {
        return "DEBUG_RING_BUFFER_VENDOR_DATA";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS) {
        return "DEBUG_HOST_WAKE_REASON_STATS";
    }
    if (o == ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS) {
        return "DEBUG_ERROR_ALERTS";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChip>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::V1_0::IWifiChip::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
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
template<> inline constexpr std::array<::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask, 8> hidl_enum_values<::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask> = {
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS,
    ::android::hardware::wifi::V1_0::IWifiChip::ChipCapabilityMask::DEBUG_ERROR_ALERTS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFICHIP_H
