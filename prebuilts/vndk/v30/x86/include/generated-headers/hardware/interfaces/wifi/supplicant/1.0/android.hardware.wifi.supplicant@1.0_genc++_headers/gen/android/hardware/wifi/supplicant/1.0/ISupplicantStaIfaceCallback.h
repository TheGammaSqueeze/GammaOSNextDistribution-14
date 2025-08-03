#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTSTAIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTSTAIFACECALLBACK_H

#include <android/hardware/wifi/supplicant/1.0/types.h>
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
namespace supplicant {
namespace V1_0 {

/**
 * Callback Interface exposed by the supplicant service
 * for each station mode interface (ISupplicantStaIface).
 *
 * Clients need to host an instance of this HIDL interface object and
 * pass a reference of the object to the supplicant via the
 * corresponding |ISupplicantStaIface.registerCallback| method.
 */
struct ISupplicantStaIfaceCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.0::ISupplicantStaIfaceCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class State : uint32_t;
    enum class OsuMethod : uint8_t;
    struct AnqpData;
    struct Hs20AnqpData;
    enum class WpsConfigError : uint16_t;
    enum class WpsErrorIndication : uint16_t;
    enum class StatusCode : uint32_t;
    enum class ReasonCode : uint32_t;
    enum class BssidChangeReason : uint8_t;

    /**
     * Various states of the interface reported by |onStateChanged|.
     */
    enum class State : uint32_t {
        /**
         * This state indicates that client is not associated, but is likely to
         * start looking for an access point. This state is entered when a
         * connection is lost.
         */
        DISCONNECTED = 0u,
        /**
         * This state is entered if the network interface is disabled, e.g.,
         * due to rfkill. the supplicant refuses any new operations that would
         * use the radio until the interface has been enabled.
         */
        IFACE_DISABLED = 1u,
        /**
         * This state is entered if there are no enabled networks in the
         * configuration. the supplicant is not trying to associate with a new
         * network and external interaction (e.g., ctrl_iface call to add or
         * enable a network) is needed to start association.
         */
        INACTIVE = 2u,
        /**
         * This state is entered when the supplicant starts scanning for a
         * network.
         */
        SCANNING = 3u,
        /**
         * This state is entered when the supplicant has found a suitable BSS
         * to authenticate with and the driver is configured to try to
         * authenticate with this BSS. This state is used only with drivers
         * that use the supplicant as the SME.
         */
        AUTHENTICATING = 4u,
        /**
         * This state is entered when the supplicant has found a suitable BSS
         * to associate with and the driver is configured to try to associate
         * with this BSS in ap_scan=1 mode. When using ap_scan=2 mode, this
         * state is entered when the driver is configured to try to associate
         * with a network using the configured SSID and security policy.
         */
        ASSOCIATING = 5u,
        /**
         * This state is entered when the driver reports that association has
         * been successfully completed with an AP. If IEEE 802.1X is used
         * (with or without WPA/WPA2), the supplicant remains in this state
         * until the IEEE 802.1X/EAPOL authentication has been completed.
         */
        ASSOCIATED = 6u,
        /**
         * This state is entered when WPA/WPA2 4-Way Handshake is started. In
         * case of WPA-PSK, this happens when receiving the first EAPOL-Key
         * frame after association. In case of WPA-EAP, this state is entered
         * when the IEEE 802.1X/EAPOL authentication has been completed.
         */
        FOURWAY_HANDSHAKE = 7u,
        /**
         * This state is entered when 4-Way Key Handshake has been completed
         * (i.e., when the supplicant sends out message 4/4) and when Group
         * Key rekeying is started by the AP (i.e., when supplicant receives
         * message 1/2).
         */
        GROUP_HANDSHAKE = 8u,
        /**
         * This state is entered when the full authentication process is
         * completed. In case of WPA2, this happens when the 4-Way Handshake is
         * successfully completed. With WPA, this state is entered after the
         * Group Key Handshake; with IEEE 802.1X (non-WPA) connection is
         * completed after dynamic keys are received (or if not used, after
         * the EAP authentication has been completed). With static WEP keys and
         * plaintext connections, this state is entered when an association
         * has been completed.
         *
         * This state indicates that the supplicant has completed its
         * processing for the association phase and that data connection is
         * fully configured.
         */
        COMPLETED = 9u,
    };

    /**
     * OSU Method. Refer to section 4.8.1.3 of the Hotspot 2.0 spec.
     */
    enum class OsuMethod : uint8_t {
        OMA_DM = 0,
        SOAP_XML_SPP = 1,
    };

    /**
     * ANQP data for IEEE Std 802.11u-2011.
     * The format of the data within these elements follows the IEEE
     * Std 802.11u-2011 standard.
     */
    struct AnqpData final {
        ::android::hardware::hidl_vec<uint8_t> venueName __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> roamingConsortium __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> ipAddrTypeAvailability __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> naiRealm __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> anqp3gppCellularNetwork __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> domainName __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, venueName) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, roamingConsortium) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, ipAddrTypeAvailability) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, naiRealm) == 48, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, anqp3gppCellularNetwork) == 64, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData, domainName) == 80, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData) == 96, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData) == 8, "wrong alignment");

    /**
     * ANQP data for Hotspot 2.0.
     * The format of the data within these elements follows the Hotspot 2.0
     * standard.
     */
    struct Hs20AnqpData final {
        ::android::hardware::hidl_vec<uint8_t> operatorFriendlyName __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> wanMetrics __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> connectionCapability __attribute__ ((aligned(8)));
        ::android::hardware::hidl_vec<uint8_t> osuProvidersList __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData, operatorFriendlyName) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData, wanMetrics) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData, connectionCapability) == 32, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData, osuProvidersList) == 48, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData) == 64, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData) == 8, "wrong alignment");

    /**
     * WPS Configuration Error.
     */
    enum class WpsConfigError : uint16_t {
        NO_ERROR = 0,
        OOB_IFACE_READ_ERROR = 1,
        DECRYPTION_CRC_FAILURE = 2,
        CHAN_24_NOT_SUPPORTED = 3,
        CHAN_50_NOT_SUPPORTED = 4,
        SIGNAL_TOO_WEAK = 5,
        NETWORK_AUTH_FAILURE = 6,
        NETWORK_ASSOC_FAILURE = 7,
        NO_DHCP_RESPONSE = 8,
        FAILED_DHCP_CONFIG = 9,
        IP_ADDR_CONFLICT = 10,
        NO_CONN_TO_REGISTRAR = 11,
        MULTIPLE_PBC_DETECTED = 12,
        ROGUE_SUSPECTED = 13,
        DEVICE_BUSY = 14,
        SETUP_LOCKED = 15,
        MSG_TIMEOUT = 16,
        REG_SESS_TIMEOUT = 17,
        DEV_PASSWORD_AUTH_FAILURE = 18,
        CHAN_60G_NOT_SUPPORTED = 19,
        PUBLIC_KEY_HASH_MISMATCH = 20,
    };

    /**
     * Vendor specific Error Indication for WPS event messages.
     */
    enum class WpsErrorIndication : uint16_t {
        NO_ERROR = 0,
        SECURITY_TKIP_ONLY_PROHIBITED = 1,
        SECURITY_WEP_PROHIBITED = 2,
        AUTH_FAILURE = 3,
    };

    /**
     * Status codes (IEEE Std 802.11-2016, 9.4.1.9, Table 9-46).
     */
    enum class StatusCode : uint32_t {
        SUCCESS = 0u,
        UNSPECIFIED_FAILURE = 1u,
        TDLS_WAKEUP_ALTERNATE = 2u,
        TDLS_WAKEUP_REJECT = 3u,
        SECURITY_DISABLED = 5u,
        UNACCEPTABLE_LIFETIME = 6u,
        NOT_IN_SAME_BSS = 7u,
        CAPS_UNSUPPORTED = 10u,
        REASSOC_NO_ASSOC = 11u,
        ASSOC_DENIED_UNSPEC = 12u,
        NOT_SUPPORTED_AUTH_ALG = 13u,
        UNKNOWN_AUTH_TRANSACTION = 14u,
        CHALLENGE_FAIL = 15u,
        AUTH_TIMEOUT = 16u,
        AP_UNABLE_TO_HANDLE_NEW_STA = 17u,
        ASSOC_DENIED_RATES = 18u,
        ASSOC_DENIED_NOSHORT = 19u,
        SPEC_MGMT_REQUIRED = 22u,
        PWR_CAPABILITY_NOT_VALID = 23u,
        SUPPORTED_CHANNEL_NOT_VALID = 24u,
        ASSOC_DENIED_NO_SHORT_SLOT_TIME = 25u,
        ASSOC_DENIED_NO_HT = 27u,
        R0KH_UNREACHABLE = 28u,
        ASSOC_DENIED_NO_PCO = 29u,
        ASSOC_REJECTED_TEMPORARILY = 30u,
        ROBUST_MGMT_FRAME_POLICY_VIOLATION = 31u,
        UNSPECIFIED_QOS_FAILURE = 32u,
        DENIED_INSUFFICIENT_BANDWIDTH = 33u,
        DENIED_POOR_CHANNEL_CONDITIONS = 34u,
        DENIED_QOS_NOT_SUPPORTED = 35u,
        REQUEST_DECLINED = 37u,
        INVALID_PARAMETERS = 38u,
        REJECTED_WITH_SUGGESTED_CHANGES = 39u,
        INVALID_IE = 40u,
        GROUP_CIPHER_NOT_VALID = 41u,
        PAIRWISE_CIPHER_NOT_VALID = 42u,
        AKMP_NOT_VALID = 43u,
        UNSUPPORTED_RSN_IE_VERSION = 44u,
        INVALID_RSN_IE_CAPAB = 45u,
        CIPHER_REJECTED_PER_POLICY = 46u,
        TS_NOT_CREATED = 47u,
        DIRECT_LINK_NOT_ALLOWED = 48u,
        DEST_STA_NOT_PRESENT = 49u,
        DEST_STA_NOT_QOS_STA = 50u,
        ASSOC_DENIED_LISTEN_INT_TOO_LARGE = 51u,
        INVALID_FT_ACTION_FRAME_COUNT = 52u,
        INVALID_PMKID = 53u,
        INVALID_MDIE = 54u,
        INVALID_FTIE = 55u,
        REQUESTED_TCLAS_NOT_SUPPORTED = 56u,
        INSUFFICIENT_TCLAS_PROCESSING_RESOURCES = 57u,
        TRY_ANOTHER_BSS = 58u,
        GAS_ADV_PROTO_NOT_SUPPORTED = 59u,
        NO_OUTSTANDING_GAS_REQ = 60u,
        GAS_RESP_NOT_RECEIVED = 61u,
        STA_TIMED_OUT_WAITING_FOR_GAS_RESP = 62u,
        GAS_RESP_LARGER_THAN_LIMIT = 63u,
        REQ_REFUSED_HOME = 64u,
        ADV_SRV_UNREACHABLE = 65u,
        REQ_REFUSED_SSPN = 67u,
        REQ_REFUSED_UNAUTH_ACCESS = 68u,
        INVALID_RSNIE = 72u,
        U_APSD_COEX_NOT_SUPPORTED = 73u,
        U_APSD_COEX_MODE_NOT_SUPPORTED = 74u,
        BAD_INTERVAL_WITH_U_APSD_COEX = 75u,
        ANTI_CLOGGING_TOKEN_REQ = 76u,
        FINITE_CYCLIC_GROUP_NOT_SUPPORTED = 77u,
        CANNOT_FIND_ALT_TBTT = 78u,
        TRANSMISSION_FAILURE = 79u,
        REQ_TCLAS_NOT_SUPPORTED = 80u,
        TCLAS_RESOURCES_EXCHAUSTED = 81u,
        REJECTED_WITH_SUGGESTED_BSS_TRANSITION = 82u,
        REJECT_WITH_SCHEDULE = 83u,
        REJECT_NO_WAKEUP_SPECIFIED = 84u,
        SUCCESS_POWER_SAVE_MODE = 85u,
        PENDING_ADMITTING_FST_SESSION = 86u,
        PERFORMING_FST_NOW = 87u,
        PENDING_GAP_IN_BA_WINDOW = 88u,
        REJECT_U_PID_SETTING = 89u,
        REFUSED_EXTERNAL_REASON = 92u,
        REFUSED_AP_OUT_OF_MEMORY = 93u,
        REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED = 94u,
        QUERY_RESP_OUTSTANDING = 95u,
        REJECT_DSE_BAND = 96u,
        TCLAS_PROCESSING_TERMINATED = 97u,
        TS_SCHEDULE_CONFLICT = 98u,
        DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL = 99u,
        MCCAOP_RESERVATION_CONFLICT = 100u,
        MAF_LIMIT_EXCEEDED = 101u,
        MCCA_TRACK_LIMIT_EXCEEDED = 102u,
        DENIED_DUE_TO_SPECTRUM_MANAGEMENT = 103u,
        ASSOC_DENIED_NO_VHT = 104u,
        ENABLEMENT_DENIED = 105u,
        RESTRICTION_FROM_AUTHORIZED_GDB = 106u,
        AUTHORIZATION_DEENABLED = 107u,
        FILS_AUTHENTICATION_FAILURE = 112u,
        UNKNOWN_AUTHENTICATION_SERVER = 113u,
    };

    /**
     * Reason codes (IEEE Std 802.11-2016, 9.4.1.7, Table 9-45).
     */
    enum class ReasonCode : uint32_t {
        UNSPECIFIED = 1u,
        PREV_AUTH_NOT_VALID = 2u,
        DEAUTH_LEAVING = 3u,
        DISASSOC_DUE_TO_INACTIVITY = 4u,
        DISASSOC_AP_BUSY = 5u,
        CLASS2_FRAME_FROM_NONAUTH_STA = 6u,
        CLASS3_FRAME_FROM_NONASSOC_STA = 7u,
        DISASSOC_STA_HAS_LEFT = 8u,
        STA_REQ_ASSOC_WITHOUT_AUTH = 9u,
        PWR_CAPABILITY_NOT_VALID = 10u,
        SUPPORTED_CHANNEL_NOT_VALID = 11u,
        BSS_TRANSITION_DISASSOC = 12u,
        INVALID_IE = 13u,
        MICHAEL_MIC_FAILURE = 14u,
        FOURWAY_HANDSHAKE_TIMEOUT = 15u,
        GROUP_KEY_UPDATE_TIMEOUT = 16u,
        IE_IN_4WAY_DIFFERS = 17u,
        GROUP_CIPHER_NOT_VALID = 18u,
        PAIRWISE_CIPHER_NOT_VALID = 19u,
        AKMP_NOT_VALID = 20u,
        UNSUPPORTED_RSN_IE_VERSION = 21u,
        INVALID_RSN_IE_CAPAB = 22u,
        IEEE_802_1X_AUTH_FAILED = 23u,
        CIPHER_SUITE_REJECTED = 24u,
        TDLS_TEARDOWN_UNREACHABLE = 25u,
        TDLS_TEARDOWN_UNSPECIFIED = 26u,
        SSP_REQUESTED_DISASSOC = 27u,
        NO_SSP_ROAMING_AGREEMENT = 28u,
        BAD_CIPHER_OR_AKM = 29u,
        NOT_AUTHORIZED_THIS_LOCATION = 30u,
        SERVICE_CHANGE_PRECLUDES_TS = 31u,
        UNSPECIFIED_QOS_REASON = 32u,
        NOT_ENOUGH_BANDWIDTH = 33u,
        DISASSOC_LOW_ACK = 34u,
        EXCEEDED_TXOP = 35u,
        STA_LEAVING = 36u,
        END_TS_BA_DLS = 37u,
        UNKNOWN_TS_BA = 38u,
        TIMEOUT = 39u,
        PEERKEY_MISMATCH = 45u,
        AUTHORIZED_ACCESS_LIMIT_REACHED = 46u,
        EXTERNAL_SERVICE_REQUIREMENTS = 47u,
        INVALID_FT_ACTION_FRAME_COUNT = 48u,
        INVALID_PMKID = 49u,
        INVALID_MDE = 50u,
        INVALID_FTE = 51u,
        MESH_PEERING_CANCELLED = 52u,
        MESH_MAX_PEERS = 53u,
        MESH_CONFIG_POLICY_VIOLATION = 54u,
        MESH_CLOSE_RCVD = 55u,
        MESH_MAX_RETRIES = 56u,
        MESH_CONFIRM_TIMEOUT = 57u,
        MESH_INVALID_GTK = 58u,
        MESH_INCONSISTENT_PARAMS = 59u,
        MESH_INVALID_SECURITY_CAP = 60u,
        MESH_PATH_ERROR_NO_PROXY_INFO = 61u,
        MESH_PATH_ERROR_NO_FORWARDING_INFO = 62u,
        MESH_PATH_ERROR_DEST_UNREACHABLE = 63u,
        MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS = 64u,
        MESH_CHANNEL_SWITCH_REGULATORY_REQ = 65u,
        MESH_CHANNEL_SWITCH_UNSPECIFIED = 66u,
    };

    /**
     * BSSID change Reasons.
     */
    enum class BssidChangeReason : uint8_t {
        /**
         * Started association with new bssid.
         */
        ASSOC_START = 0,
        /**
         * Completed association with new bssid.
         */
        ASSOC_COMPLETE = 1,
        /**
         * Dis-association with current bssid.
         */
        DISASSOC = 2,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Used to indicate that a new network has been added.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    virtual ::android::hardware::Return<void> onNetworkAdded(uint32_t id) = 0;

    /**
     * Used to indicate that a network has been removed.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    virtual ::android::hardware::Return<void> onNetworkRemoved(uint32_t id) = 0;

    /**
     * Used to indicate a state change event on this particular iface. If this
     * event is triggered by a particular network, the |SupplicantNetworkId|,
     * |ssid|, |bssid| parameters must indicate the parameters of the network/AP
     * which cased this state transition.
     *
     * @param newState New State of the interface. This must be one of the |State|
     *        values above.
     * @param bssid BSSID of the corresponding AP which caused this state
     *        change event. This must be zero'ed if this event is not
     *        specific to a particular network.
     * @param id ID of the corresponding network which caused this
     *        state change event. This must be invalid (UINT32_MAX) if this
     *        event is not specific to a particular network.
     * @param ssid SSID of the corresponding network which caused this state
     *        change event. This must be empty if this event is not specific
     *        to a particular network.
     */
    virtual ::android::hardware::Return<void> onStateChanged(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State newState, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& ssid) = 0;

    /**
     * Used to indicate the result of ANQP (either for IEEE 802.11u Interworking
     * or Hotspot 2.0) query.
     *
     * @param bssid BSSID of the access point.
     * @param data ANQP data fetched from the access point.
     *        All the fields in this struct must be empty if the query failed.
     * @param hs20Data ANQP data fetched from the Hotspot 2.0 access point.
     *        All the fields in this struct must be empty if the query failed.
     */
    virtual ::android::hardware::Return<void> onAnqpQueryDone(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& data, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& hs20Data) = 0;

    /**
     * Used to indicate the result of Hotspot 2.0 Icon query.
     *
     * @param bssid BSSID of the access point.
     * @param fileName Name of the file that was requested.
     * @param data Icon data fetched from the access point.
     *        Must be empty if the query failed.
     */
    virtual ::android::hardware::Return<void> onHs20IconQueryDone(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::hidl_string& fileName, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Used to indicate a Hotspot 2.0 subscription remediation event.
     *
     * @param bssid BSSID of the access point.
     * @param osuMethod OSU method.
     * @param url URL of the server.
     */
    virtual ::android::hardware::Return<void> onHs20SubscriptionRemediation(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod osuMethod, const ::android::hardware::hidl_string& url) = 0;

    /**
     * Used to indicate a Hotspot 2.0 imminent deauth notice.
     *
     * @param bssid BSSID of the access point.
     * @param reasonCode Code to indicate the deauth reason.
     *        Refer to section 3.2.1.2 of the Hotspot 2.0 spec.
     * @param reAuthDelayInSec Delay before reauthenticating.
     * @param url URL of the server.
     */
    virtual ::android::hardware::Return<void> onHs20DeauthImminentNotice(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t reasonCode, uint32_t reAuthDelayInSec, const ::android::hardware::hidl_string& url) = 0;

    /**
     * Used to indicate the disconnection from the currently connected
     * network on this iface.
     *
     * @param bssid BSSID of the AP from which we disconnected.
     * @param locallyGenerated If the disconnect was triggered by
     *        wpa_supplicant.
     * @param reasonCode 802.11 code to indicate the disconnect reason
     *        from access point. Refer to section 8.4.1.7 of IEEE802.11 spec.
     */
    virtual ::android::hardware::Return<void> onDisconnected(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, bool locallyGenerated, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode reasonCode) = 0;

    /**
     * Used to indicate an association rejection recieved from the AP
     * to which the connection is being attempted.
     *
     * @param bssid BSSID of the corresponding AP which sent this
     *        reject.
     * @param statusCode 802.11 code to indicate the reject reason.
     *        Refer to section 8.4.1.9 of IEEE 802.11 spec.
     * @param timedOut Whether failure is due to timeout rather
     *        than explicit rejection response from the AP.
     */
    virtual ::android::hardware::Return<void> onAssociationRejected(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode statusCode, bool timedOut) = 0;

    /**
     * Used to indicate the timeout of authentication to an AP.
     *
     * @param bssid BSSID of the corresponding AP.
     */
    virtual ::android::hardware::Return<void> onAuthenticationTimeout(const ::android::hardware::hidl_array<uint8_t, 6>& bssid) = 0;

    /**
     * Used to indicate an EAP authentication failure.
     */
    virtual ::android::hardware::Return<void> onEapFailure() = 0;

    /**
     * Used to indicate the change of active bssid.
     * This is useful to figure out when the driver/firmware roams to a bssid
     * on its own.
     *
     * @param reason Reason why the bssid changed.
     * @param bssid BSSID of the corresponding AP.
     */
    virtual ::android::hardware::Return<void> onBssidChanged(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason reason, const ::android::hardware::hidl_array<uint8_t, 6>& bssid) = 0;

    /**
     * Used to indicate the success of a WPS connection attempt.
     */
    virtual ::android::hardware::Return<void> onWpsEventSuccess() = 0;

    /**
     * Used to indicate the failure of a WPS connection attempt.
     *
     * @param bssid BSSID of the AP to which we initiated WPS
     *        connection.
     * @param configError Configuration error code.
     * @param errorInd Error indication code.
     */
    virtual ::android::hardware::Return<void> onWpsEventFail(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError configError, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication errorInd) = 0;

    /**
     * Used to indicate the overlap of a WPS PBC connection attempt.
     */
    virtual ::android::hardware::Return<void> onWpsEventPbcOverlap() = 0;

    /**
     * Used to indicate that the external radio work can start now.
     *
     * @return id Identifier generated for the radio work request.
     */
    virtual ::android::hardware::Return<void> onExtRadioWorkStart(uint32_t id) = 0;

    /**
     * Used to indicate that the external radio work request has timed out.
     *
     * @return id Identifier generated for the radio work request.
     */
    virtual ::android::hardware::Return<void> onExtRadioWorkTimeout(uint32_t id) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISupplicantStaIfaceCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIfaceCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIfaceCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantStaIfaceCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISupplicantStaIfaceCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIfaceCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantStaIfaceCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantStaIfaceCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& rhs);

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& rhs);

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::DISCONNECTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "DISCONNECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::DISCONNECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::IFACE_DISABLED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::IFACE_DISABLED)) {
        os += (first ? "" : " | ");
        os += "IFACE_DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::IFACE_DISABLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::INACTIVE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::INACTIVE)) {
        os += (first ? "" : " | ");
        os += "INACTIVE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::INACTIVE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::SCANNING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::SCANNING)) {
        os += (first ? "" : " | ");
        os += "SCANNING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::SCANNING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::AUTHENTICATING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::AUTHENTICATING)) {
        os += (first ? "" : " | ");
        os += "AUTHENTICATING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::AUTHENTICATING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATING)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATED)) {
        os += (first ? "" : " | ");
        os += "ASSOCIATED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::FOURWAY_HANDSHAKE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::FOURWAY_HANDSHAKE)) {
        os += (first ? "" : " | ");
        os += "FOURWAY_HANDSHAKE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::FOURWAY_HANDSHAKE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::GROUP_HANDSHAKE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::GROUP_HANDSHAKE)) {
        os += (first ? "" : " | ");
        os += "GROUP_HANDSHAKE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::GROUP_HANDSHAKE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::COMPLETED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::COMPLETED)) {
        os += (first ? "" : " | ");
        os += "COMPLETED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::COMPLETED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::DISCONNECTED) {
        return "DISCONNECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::IFACE_DISABLED) {
        return "IFACE_DISABLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::INACTIVE) {
        return "INACTIVE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::SCANNING) {
        return "SCANNING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::AUTHENTICATING) {
        return "AUTHENTICATING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATING) {
        return "ASSOCIATING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATED) {
        return "ASSOCIATED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::FOURWAY_HANDSHAKE) {
        return "FOURWAY_HANDSHAKE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::GROUP_HANDSHAKE) {
        return "GROUP_HANDSHAKE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::COMPLETED) {
        return "COMPLETED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::OMA_DM) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::OMA_DM)) {
        os += (first ? "" : " | ");
        os += "OMA_DM";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::OMA_DM;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::SOAP_XML_SPP) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::SOAP_XML_SPP)) {
        os += (first ? "" : " | ");
        os += "SOAP_XML_SPP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::SOAP_XML_SPP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::OMA_DM) {
        return "OMA_DM";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::SOAP_XML_SPP) {
        return "SOAP_XML_SPP";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".venueName = ";
    os += ::android::hardware::toString(o.venueName);
    os += ", .roamingConsortium = ";
    os += ::android::hardware::toString(o.roamingConsortium);
    os += ", .ipAddrTypeAvailability = ";
    os += ::android::hardware::toString(o.ipAddrTypeAvailability);
    os += ", .naiRealm = ";
    os += ::android::hardware::toString(o.naiRealm);
    os += ", .anqp3gppCellularNetwork = ";
    os += ::android::hardware::toString(o.anqp3gppCellularNetwork);
    os += ", .domainName = ";
    os += ::android::hardware::toString(o.domainName);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& rhs) {
    if (lhs.venueName != rhs.venueName) {
        return false;
    }
    if (lhs.roamingConsortium != rhs.roamingConsortium) {
        return false;
    }
    if (lhs.ipAddrTypeAvailability != rhs.ipAddrTypeAvailability) {
        return false;
    }
    if (lhs.naiRealm != rhs.naiRealm) {
        return false;
    }
    if (lhs.anqp3gppCellularNetwork != rhs.anqp3gppCellularNetwork) {
        return false;
    }
    if (lhs.domainName != rhs.domainName) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".operatorFriendlyName = ";
    os += ::android::hardware::toString(o.operatorFriendlyName);
    os += ", .wanMetrics = ";
    os += ::android::hardware::toString(o.wanMetrics);
    os += ", .connectionCapability = ";
    os += ::android::hardware::toString(o.connectionCapability);
    os += ", .osuProvidersList = ";
    os += ::android::hardware::toString(o.osuProvidersList);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& rhs) {
    if (lhs.operatorFriendlyName != rhs.operatorFriendlyName) {
        return false;
    }
    if (lhs.wanMetrics != rhs.wanMetrics) {
        return false;
    }
    if (lhs.connectionCapability != rhs.connectionCapability) {
        return false;
    }
    if (lhs.osuProvidersList != rhs.osuProvidersList) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_ERROR) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_ERROR)) {
        os += (first ? "" : " | ");
        os += "NO_ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_ERROR;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::OOB_IFACE_READ_ERROR) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::OOB_IFACE_READ_ERROR)) {
        os += (first ? "" : " | ");
        os += "OOB_IFACE_READ_ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::OOB_IFACE_READ_ERROR;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DECRYPTION_CRC_FAILURE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DECRYPTION_CRC_FAILURE)) {
        os += (first ? "" : " | ");
        os += "DECRYPTION_CRC_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DECRYPTION_CRC_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_24_NOT_SUPPORTED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_24_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "CHAN_24_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_24_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_50_NOT_SUPPORTED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_50_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "CHAN_50_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_50_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SIGNAL_TOO_WEAK) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SIGNAL_TOO_WEAK)) {
        os += (first ? "" : " | ");
        os += "SIGNAL_TOO_WEAK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SIGNAL_TOO_WEAK;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_AUTH_FAILURE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_AUTH_FAILURE)) {
        os += (first ? "" : " | ");
        os += "NETWORK_AUTH_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_AUTH_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_ASSOC_FAILURE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_ASSOC_FAILURE)) {
        os += (first ? "" : " | ");
        os += "NETWORK_ASSOC_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_ASSOC_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_DHCP_RESPONSE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_DHCP_RESPONSE)) {
        os += (first ? "" : " | ");
        os += "NO_DHCP_RESPONSE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_DHCP_RESPONSE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::FAILED_DHCP_CONFIG) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::FAILED_DHCP_CONFIG)) {
        os += (first ? "" : " | ");
        os += "FAILED_DHCP_CONFIG";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::FAILED_DHCP_CONFIG;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::IP_ADDR_CONFLICT) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::IP_ADDR_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "IP_ADDR_CONFLICT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::IP_ADDR_CONFLICT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_CONN_TO_REGISTRAR) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_CONN_TO_REGISTRAR)) {
        os += (first ? "" : " | ");
        os += "NO_CONN_TO_REGISTRAR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_CONN_TO_REGISTRAR;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MULTIPLE_PBC_DETECTED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MULTIPLE_PBC_DETECTED)) {
        os += (first ? "" : " | ");
        os += "MULTIPLE_PBC_DETECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MULTIPLE_PBC_DETECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::ROGUE_SUSPECTED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::ROGUE_SUSPECTED)) {
        os += (first ? "" : " | ");
        os += "ROGUE_SUSPECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::ROGUE_SUSPECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEVICE_BUSY) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEVICE_BUSY)) {
        os += (first ? "" : " | ");
        os += "DEVICE_BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEVICE_BUSY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SETUP_LOCKED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SETUP_LOCKED)) {
        os += (first ? "" : " | ");
        os += "SETUP_LOCKED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SETUP_LOCKED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MSG_TIMEOUT) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MSG_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "MSG_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MSG_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::REG_SESS_TIMEOUT) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::REG_SESS_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "REG_SESS_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::REG_SESS_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE)) {
        os += (first ? "" : " | ");
        os += "DEV_PASSWORD_AUTH_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_60G_NOT_SUPPORTED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_60G_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "CHAN_60G_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_60G_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "PUBLIC_KEY_HASH_MISMATCH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_ERROR) {
        return "NO_ERROR";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::OOB_IFACE_READ_ERROR) {
        return "OOB_IFACE_READ_ERROR";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DECRYPTION_CRC_FAILURE) {
        return "DECRYPTION_CRC_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_24_NOT_SUPPORTED) {
        return "CHAN_24_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_50_NOT_SUPPORTED) {
        return "CHAN_50_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SIGNAL_TOO_WEAK) {
        return "SIGNAL_TOO_WEAK";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_AUTH_FAILURE) {
        return "NETWORK_AUTH_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_ASSOC_FAILURE) {
        return "NETWORK_ASSOC_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_DHCP_RESPONSE) {
        return "NO_DHCP_RESPONSE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::FAILED_DHCP_CONFIG) {
        return "FAILED_DHCP_CONFIG";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::IP_ADDR_CONFLICT) {
        return "IP_ADDR_CONFLICT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_CONN_TO_REGISTRAR) {
        return "NO_CONN_TO_REGISTRAR";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MULTIPLE_PBC_DETECTED) {
        return "MULTIPLE_PBC_DETECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::ROGUE_SUSPECTED) {
        return "ROGUE_SUSPECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEVICE_BUSY) {
        return "DEVICE_BUSY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SETUP_LOCKED) {
        return "SETUP_LOCKED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MSG_TIMEOUT) {
        return "MSG_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::REG_SESS_TIMEOUT) {
        return "REG_SESS_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE) {
        return "DEV_PASSWORD_AUTH_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_60G_NOT_SUPPORTED) {
        return "CHAN_60G_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH) {
        return "PUBLIC_KEY_HASH_MISMATCH";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::NO_ERROR) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::NO_ERROR)) {
        os += (first ? "" : " | ");
        os += "NO_ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::NO_ERROR;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED)) {
        os += (first ? "" : " | ");
        os += "SECURITY_TKIP_ONLY_PROHIBITED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_WEP_PROHIBITED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_WEP_PROHIBITED)) {
        os += (first ? "" : " | ");
        os += "SECURITY_WEP_PROHIBITED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_WEP_PROHIBITED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::AUTH_FAILURE) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::AUTH_FAILURE)) {
        os += (first ? "" : " | ");
        os += "AUTH_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::AUTH_FAILURE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::NO_ERROR) {
        return "NO_ERROR";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED) {
        return "SECURITY_TKIP_ONLY_PROHIBITED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_WEP_PROHIBITED) {
        return "SECURITY_WEP_PROHIBITED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::AUTH_FAILURE) {
        return "AUTH_FAILURE";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_FAILURE)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_ALTERNATE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_ALTERNATE)) {
        os += (first ? "" : " | ");
        os += "TDLS_WAKEUP_ALTERNATE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_ALTERNATE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_REJECT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_REJECT)) {
        os += (first ? "" : " | ");
        os += "TDLS_WAKEUP_REJECT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_REJECT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SECURITY_DISABLED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SECURITY_DISABLED)) {
        os += (first ? "" : " | ");
        os += "SECURITY_DISABLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SECURITY_DISABLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNACCEPTABLE_LIFETIME) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNACCEPTABLE_LIFETIME)) {
        os += (first ? "" : " | ");
        os += "UNACCEPTABLE_LIFETIME";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNACCEPTABLE_LIFETIME;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_IN_SAME_BSS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_IN_SAME_BSS)) {
        os += (first ? "" : " | ");
        os += "NOT_IN_SAME_BSS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_IN_SAME_BSS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CAPS_UNSUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CAPS_UNSUPPORTED)) {
        os += (first ? "" : " | ");
        os += "CAPS_UNSUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CAPS_UNSUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REASSOC_NO_ASSOC) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REASSOC_NO_ASSOC)) {
        os += (first ? "" : " | ");
        os += "REASSOC_NO_ASSOC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REASSOC_NO_ASSOC;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_UNSPEC) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_UNSPEC)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_UNSPEC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_UNSPEC;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_SUPPORTED_AUTH_ALG) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_SUPPORTED_AUTH_ALG)) {
        os += (first ? "" : " | ");
        os += "NOT_SUPPORTED_AUTH_ALG";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_SUPPORTED_AUTH_ALG;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTH_TRANSACTION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTH_TRANSACTION)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_AUTH_TRANSACTION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTH_TRANSACTION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CHALLENGE_FAIL) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CHALLENGE_FAIL)) {
        os += (first ? "" : " | ");
        os += "CHALLENGE_FAIL";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CHALLENGE_FAIL;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTH_TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTH_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "AUTH_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTH_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AP_UNABLE_TO_HANDLE_NEW_STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AP_UNABLE_TO_HANDLE_NEW_STA)) {
        os += (first ? "" : " | ");
        os += "AP_UNABLE_TO_HANDLE_NEW_STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AP_UNABLE_TO_HANDLE_NEW_STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_RATES) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_RATES)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_RATES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_RATES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NOSHORT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NOSHORT)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_NOSHORT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NOSHORT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SPEC_MGMT_REQUIRED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SPEC_MGMT_REQUIRED)) {
        os += (first ? "" : " | ");
        os += "SPEC_MGMT_REQUIRED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SPEC_MGMT_REQUIRED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PWR_CAPABILITY_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PWR_CAPABILITY_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "PWR_CAPABILITY_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PWR_CAPABILITY_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUPPORTED_CHANNEL_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUPPORTED_CHANNEL_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "SUPPORTED_CHANNEL_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUPPORTED_CHANNEL_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_NO_SHORT_SLOT_TIME";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_HT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_HT)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_NO_HT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_HT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::R0KH_UNREACHABLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::R0KH_UNREACHABLE)) {
        os += (first ? "" : " | ");
        os += "R0KH_UNREACHABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::R0KH_UNREACHABLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_PCO) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_PCO)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_NO_PCO";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_PCO;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_REJECTED_TEMPORARILY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_REJECTED_TEMPORARILY)) {
        os += (first ? "" : " | ");
        os += "ASSOC_REJECTED_TEMPORARILY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_REJECTED_TEMPORARILY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION)) {
        os += (first ? "" : " | ");
        os += "ROBUST_MGMT_FRAME_POLICY_VIOLATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_QOS_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_QOS_FAILURE)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED_QOS_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_QOS_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_INSUFFICIENT_BANDWIDTH) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_INSUFFICIENT_BANDWIDTH)) {
        os += (first ? "" : " | ");
        os += "DENIED_INSUFFICIENT_BANDWIDTH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_INSUFFICIENT_BANDWIDTH;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_POOR_CHANNEL_CONDITIONS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_POOR_CHANNEL_CONDITIONS)) {
        os += (first ? "" : " | ");
        os += "DENIED_POOR_CHANNEL_CONDITIONS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_POOR_CHANNEL_CONDITIONS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_QOS_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_QOS_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "DENIED_QOS_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_QOS_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUEST_DECLINED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUEST_DECLINED)) {
        os += (first ? "" : " | ");
        os += "REQUEST_DECLINED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUEST_DECLINED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PARAMETERS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PARAMETERS)) {
        os += (first ? "" : " | ");
        os += "INVALID_PARAMETERS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PARAMETERS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_CHANGES) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_CHANGES)) {
        os += (first ? "" : " | ");
        os += "REJECTED_WITH_SUGGESTED_CHANGES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_CHANGES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_IE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_IE)) {
        os += (first ? "" : " | ");
        os += "INVALID_IE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_IE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GROUP_CIPHER_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GROUP_CIPHER_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "GROUP_CIPHER_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GROUP_CIPHER_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PAIRWISE_CIPHER_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PAIRWISE_CIPHER_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "PAIRWISE_CIPHER_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PAIRWISE_CIPHER_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AKMP_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AKMP_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "AKMP_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AKMP_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSUPPORTED_RSN_IE_VERSION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSUPPORTED_RSN_IE_VERSION)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_RSN_IE_VERSION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSUPPORTED_RSN_IE_VERSION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSN_IE_CAPAB) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSN_IE_CAPAB)) {
        os += (first ? "" : " | ");
        os += "INVALID_RSN_IE_CAPAB";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSN_IE_CAPAB;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CIPHER_REJECTED_PER_POLICY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CIPHER_REJECTED_PER_POLICY)) {
        os += (first ? "" : " | ");
        os += "CIPHER_REJECTED_PER_POLICY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CIPHER_REJECTED_PER_POLICY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_NOT_CREATED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_NOT_CREATED)) {
        os += (first ? "" : " | ");
        os += "TS_NOT_CREATED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_NOT_CREATED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DIRECT_LINK_NOT_ALLOWED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DIRECT_LINK_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "DIRECT_LINK_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DIRECT_LINK_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_PRESENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_PRESENT)) {
        os += (first ? "" : " | ");
        os += "DEST_STA_NOT_PRESENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_PRESENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_QOS_STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_QOS_STA)) {
        os += (first ? "" : " | ");
        os += "DEST_STA_NOT_QOS_STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_QOS_STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_LISTEN_INT_TOO_LARGE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FT_ACTION_FRAME_COUNT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FT_ACTION_FRAME_COUNT)) {
        os += (first ? "" : " | ");
        os += "INVALID_FT_ACTION_FRAME_COUNT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FT_ACTION_FRAME_COUNT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PMKID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PMKID)) {
        os += (first ? "" : " | ");
        os += "INVALID_PMKID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PMKID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_MDIE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_MDIE)) {
        os += (first ? "" : " | ");
        os += "INVALID_MDIE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_MDIE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FTIE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FTIE)) {
        os += (first ? "" : " | ");
        os += "INVALID_FTIE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FTIE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUESTED_TCLAS_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUESTED_TCLAS_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "REQUESTED_TCLAS_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUESTED_TCLAS_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT_TCLAS_PROCESSING_RESOURCES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRY_ANOTHER_BSS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRY_ANOTHER_BSS)) {
        os += (first ? "" : " | ");
        os += "TRY_ANOTHER_BSS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRY_ANOTHER_BSS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_ADV_PROTO_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_ADV_PROTO_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "GAS_ADV_PROTO_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_ADV_PROTO_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NO_OUTSTANDING_GAS_REQ) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NO_OUTSTANDING_GAS_REQ)) {
        os += (first ? "" : " | ");
        os += "NO_OUTSTANDING_GAS_REQ";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NO_OUTSTANDING_GAS_REQ;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_NOT_RECEIVED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_NOT_RECEIVED)) {
        os += (first ? "" : " | ");
        os += "GAS_RESP_NOT_RECEIVED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_NOT_RECEIVED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP)) {
        os += (first ? "" : " | ");
        os += "STA_TIMED_OUT_WAITING_FOR_GAS_RESP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_LARGER_THAN_LIMIT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_LARGER_THAN_LIMIT)) {
        os += (first ? "" : " | ");
        os += "GAS_RESP_LARGER_THAN_LIMIT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_LARGER_THAN_LIMIT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_HOME) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_HOME)) {
        os += (first ? "" : " | ");
        os += "REQ_REFUSED_HOME";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_HOME;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ADV_SRV_UNREACHABLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ADV_SRV_UNREACHABLE)) {
        os += (first ? "" : " | ");
        os += "ADV_SRV_UNREACHABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ADV_SRV_UNREACHABLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_SSPN) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_SSPN)) {
        os += (first ? "" : " | ");
        os += "REQ_REFUSED_SSPN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_SSPN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_UNAUTH_ACCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_UNAUTH_ACCESS)) {
        os += (first ? "" : " | ");
        os += "REQ_REFUSED_UNAUTH_ACCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_UNAUTH_ACCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSNIE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSNIE)) {
        os += (first ? "" : " | ");
        os += "INVALID_RSNIE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSNIE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "U_APSD_COEX_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "U_APSD_COEX_MODE_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::BAD_INTERVAL_WITH_U_APSD_COEX) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::BAD_INTERVAL_WITH_U_APSD_COEX)) {
        os += (first ? "" : " | ");
        os += "BAD_INTERVAL_WITH_U_APSD_COEX";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::BAD_INTERVAL_WITH_U_APSD_COEX;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ANTI_CLOGGING_TOKEN_REQ) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ANTI_CLOGGING_TOKEN_REQ)) {
        os += (first ? "" : " | ");
        os += "ANTI_CLOGGING_TOKEN_REQ";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ANTI_CLOGGING_TOKEN_REQ;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "FINITE_CYCLIC_GROUP_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CANNOT_FIND_ALT_TBTT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CANNOT_FIND_ALT_TBTT)) {
        os += (first ? "" : " | ");
        os += "CANNOT_FIND_ALT_TBTT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CANNOT_FIND_ALT_TBTT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRANSMISSION_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRANSMISSION_FAILURE)) {
        os += (first ? "" : " | ");
        os += "TRANSMISSION_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRANSMISSION_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_TCLAS_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_TCLAS_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "REQ_TCLAS_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_TCLAS_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_RESOURCES_EXCHAUSTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_RESOURCES_EXCHAUSTED)) {
        os += (first ? "" : " | ");
        os += "TCLAS_RESOURCES_EXCHAUSTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_RESOURCES_EXCHAUSTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION)) {
        os += (first ? "" : " | ");
        os += "REJECTED_WITH_SUGGESTED_BSS_TRANSITION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_WITH_SCHEDULE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_WITH_SCHEDULE)) {
        os += (first ? "" : " | ");
        os += "REJECT_WITH_SCHEDULE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_WITH_SCHEDULE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_NO_WAKEUP_SPECIFIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_NO_WAKEUP_SPECIFIED)) {
        os += (first ? "" : " | ");
        os += "REJECT_NO_WAKEUP_SPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_NO_WAKEUP_SPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS_POWER_SAVE_MODE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS_POWER_SAVE_MODE)) {
        os += (first ? "" : " | ");
        os += "SUCCESS_POWER_SAVE_MODE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS_POWER_SAVE_MODE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_ADMITTING_FST_SESSION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_ADMITTING_FST_SESSION)) {
        os += (first ? "" : " | ");
        os += "PENDING_ADMITTING_FST_SESSION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_ADMITTING_FST_SESSION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PERFORMING_FST_NOW) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PERFORMING_FST_NOW)) {
        os += (first ? "" : " | ");
        os += "PERFORMING_FST_NOW";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PERFORMING_FST_NOW;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_GAP_IN_BA_WINDOW) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_GAP_IN_BA_WINDOW)) {
        os += (first ? "" : " | ");
        os += "PENDING_GAP_IN_BA_WINDOW";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_GAP_IN_BA_WINDOW;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_U_PID_SETTING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_U_PID_SETTING)) {
        os += (first ? "" : " | ");
        os += "REJECT_U_PID_SETTING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_U_PID_SETTING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_EXTERNAL_REASON) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_EXTERNAL_REASON)) {
        os += (first ? "" : " | ");
        os += "REFUSED_EXTERNAL_REASON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_EXTERNAL_REASON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_AP_OUT_OF_MEMORY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_AP_OUT_OF_MEMORY)) {
        os += (first ? "" : " | ");
        os += "REFUSED_AP_OUT_OF_MEMORY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_AP_OUT_OF_MEMORY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::QUERY_RESP_OUTSTANDING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::QUERY_RESP_OUTSTANDING)) {
        os += (first ? "" : " | ");
        os += "QUERY_RESP_OUTSTANDING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::QUERY_RESP_OUTSTANDING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_DSE_BAND) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_DSE_BAND)) {
        os += (first ? "" : " | ");
        os += "REJECT_DSE_BAND";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_DSE_BAND;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_PROCESSING_TERMINATED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_PROCESSING_TERMINATED)) {
        os += (first ? "" : " | ");
        os += "TCLAS_PROCESSING_TERMINATED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_PROCESSING_TERMINATED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_SCHEDULE_CONFLICT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_SCHEDULE_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "TS_SCHEDULE_CONFLICT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_SCHEDULE_CONFLICT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCAOP_RESERVATION_CONFLICT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCAOP_RESERVATION_CONFLICT)) {
        os += (first ? "" : " | ");
        os += "MCCAOP_RESERVATION_CONFLICT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCAOP_RESERVATION_CONFLICT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MAF_LIMIT_EXCEEDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MAF_LIMIT_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "MAF_LIMIT_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MAF_LIMIT_EXCEEDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCA_TRACK_LIMIT_EXCEEDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCA_TRACK_LIMIT_EXCEEDED)) {
        os += (first ? "" : " | ");
        os += "MCCA_TRACK_LIMIT_EXCEEDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCA_TRACK_LIMIT_EXCEEDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT)) {
        os += (first ? "" : " | ");
        os += "DENIED_DUE_TO_SPECTRUM_MANAGEMENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_VHT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_VHT)) {
        os += (first ? "" : " | ");
        os += "ASSOC_DENIED_NO_VHT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_VHT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ENABLEMENT_DENIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ENABLEMENT_DENIED)) {
        os += (first ? "" : " | ");
        os += "ENABLEMENT_DENIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ENABLEMENT_DENIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::RESTRICTION_FROM_AUTHORIZED_GDB) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::RESTRICTION_FROM_AUTHORIZED_GDB)) {
        os += (first ? "" : " | ");
        os += "RESTRICTION_FROM_AUTHORIZED_GDB";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::RESTRICTION_FROM_AUTHORIZED_GDB;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTHORIZATION_DEENABLED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTHORIZATION_DEENABLED)) {
        os += (first ? "" : " | ");
        os += "AUTHORIZATION_DEENABLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTHORIZATION_DEENABLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FILS_AUTHENTICATION_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FILS_AUTHENTICATION_FAILURE)) {
        os += (first ? "" : " | ");
        os += "FILS_AUTHENTICATION_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FILS_AUTHENTICATION_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTHENTICATION_SERVER) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTHENTICATION_SERVER)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_AUTHENTICATION_SERVER";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTHENTICATION_SERVER;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_FAILURE) {
        return "UNSPECIFIED_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_ALTERNATE) {
        return "TDLS_WAKEUP_ALTERNATE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_REJECT) {
        return "TDLS_WAKEUP_REJECT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SECURITY_DISABLED) {
        return "SECURITY_DISABLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNACCEPTABLE_LIFETIME) {
        return "UNACCEPTABLE_LIFETIME";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_IN_SAME_BSS) {
        return "NOT_IN_SAME_BSS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CAPS_UNSUPPORTED) {
        return "CAPS_UNSUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REASSOC_NO_ASSOC) {
        return "REASSOC_NO_ASSOC";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_UNSPEC) {
        return "ASSOC_DENIED_UNSPEC";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_SUPPORTED_AUTH_ALG) {
        return "NOT_SUPPORTED_AUTH_ALG";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTH_TRANSACTION) {
        return "UNKNOWN_AUTH_TRANSACTION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CHALLENGE_FAIL) {
        return "CHALLENGE_FAIL";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTH_TIMEOUT) {
        return "AUTH_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AP_UNABLE_TO_HANDLE_NEW_STA) {
        return "AP_UNABLE_TO_HANDLE_NEW_STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_RATES) {
        return "ASSOC_DENIED_RATES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NOSHORT) {
        return "ASSOC_DENIED_NOSHORT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SPEC_MGMT_REQUIRED) {
        return "SPEC_MGMT_REQUIRED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PWR_CAPABILITY_NOT_VALID) {
        return "PWR_CAPABILITY_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUPPORTED_CHANNEL_NOT_VALID) {
        return "SUPPORTED_CHANNEL_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME) {
        return "ASSOC_DENIED_NO_SHORT_SLOT_TIME";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_HT) {
        return "ASSOC_DENIED_NO_HT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::R0KH_UNREACHABLE) {
        return "R0KH_UNREACHABLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_PCO) {
        return "ASSOC_DENIED_NO_PCO";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_REJECTED_TEMPORARILY) {
        return "ASSOC_REJECTED_TEMPORARILY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION) {
        return "ROBUST_MGMT_FRAME_POLICY_VIOLATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_QOS_FAILURE) {
        return "UNSPECIFIED_QOS_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_INSUFFICIENT_BANDWIDTH) {
        return "DENIED_INSUFFICIENT_BANDWIDTH";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_POOR_CHANNEL_CONDITIONS) {
        return "DENIED_POOR_CHANNEL_CONDITIONS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_QOS_NOT_SUPPORTED) {
        return "DENIED_QOS_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUEST_DECLINED) {
        return "REQUEST_DECLINED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PARAMETERS) {
        return "INVALID_PARAMETERS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_CHANGES) {
        return "REJECTED_WITH_SUGGESTED_CHANGES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_IE) {
        return "INVALID_IE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GROUP_CIPHER_NOT_VALID) {
        return "GROUP_CIPHER_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PAIRWISE_CIPHER_NOT_VALID) {
        return "PAIRWISE_CIPHER_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AKMP_NOT_VALID) {
        return "AKMP_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSUPPORTED_RSN_IE_VERSION) {
        return "UNSUPPORTED_RSN_IE_VERSION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSN_IE_CAPAB) {
        return "INVALID_RSN_IE_CAPAB";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CIPHER_REJECTED_PER_POLICY) {
        return "CIPHER_REJECTED_PER_POLICY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_NOT_CREATED) {
        return "TS_NOT_CREATED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DIRECT_LINK_NOT_ALLOWED) {
        return "DIRECT_LINK_NOT_ALLOWED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_PRESENT) {
        return "DEST_STA_NOT_PRESENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_QOS_STA) {
        return "DEST_STA_NOT_QOS_STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE) {
        return "ASSOC_DENIED_LISTEN_INT_TOO_LARGE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FT_ACTION_FRAME_COUNT) {
        return "INVALID_FT_ACTION_FRAME_COUNT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PMKID) {
        return "INVALID_PMKID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_MDIE) {
        return "INVALID_MDIE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FTIE) {
        return "INVALID_FTIE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUESTED_TCLAS_NOT_SUPPORTED) {
        return "REQUESTED_TCLAS_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES) {
        return "INSUFFICIENT_TCLAS_PROCESSING_RESOURCES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRY_ANOTHER_BSS) {
        return "TRY_ANOTHER_BSS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_ADV_PROTO_NOT_SUPPORTED) {
        return "GAS_ADV_PROTO_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NO_OUTSTANDING_GAS_REQ) {
        return "NO_OUTSTANDING_GAS_REQ";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_NOT_RECEIVED) {
        return "GAS_RESP_NOT_RECEIVED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP) {
        return "STA_TIMED_OUT_WAITING_FOR_GAS_RESP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_LARGER_THAN_LIMIT) {
        return "GAS_RESP_LARGER_THAN_LIMIT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_HOME) {
        return "REQ_REFUSED_HOME";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ADV_SRV_UNREACHABLE) {
        return "ADV_SRV_UNREACHABLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_SSPN) {
        return "REQ_REFUSED_SSPN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_UNAUTH_ACCESS) {
        return "REQ_REFUSED_UNAUTH_ACCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSNIE) {
        return "INVALID_RSNIE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_NOT_SUPPORTED) {
        return "U_APSD_COEX_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED) {
        return "U_APSD_COEX_MODE_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::BAD_INTERVAL_WITH_U_APSD_COEX) {
        return "BAD_INTERVAL_WITH_U_APSD_COEX";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ANTI_CLOGGING_TOKEN_REQ) {
        return "ANTI_CLOGGING_TOKEN_REQ";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED) {
        return "FINITE_CYCLIC_GROUP_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CANNOT_FIND_ALT_TBTT) {
        return "CANNOT_FIND_ALT_TBTT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRANSMISSION_FAILURE) {
        return "TRANSMISSION_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_TCLAS_NOT_SUPPORTED) {
        return "REQ_TCLAS_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_RESOURCES_EXCHAUSTED) {
        return "TCLAS_RESOURCES_EXCHAUSTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION) {
        return "REJECTED_WITH_SUGGESTED_BSS_TRANSITION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_WITH_SCHEDULE) {
        return "REJECT_WITH_SCHEDULE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_NO_WAKEUP_SPECIFIED) {
        return "REJECT_NO_WAKEUP_SPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS_POWER_SAVE_MODE) {
        return "SUCCESS_POWER_SAVE_MODE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_ADMITTING_FST_SESSION) {
        return "PENDING_ADMITTING_FST_SESSION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PERFORMING_FST_NOW) {
        return "PERFORMING_FST_NOW";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_GAP_IN_BA_WINDOW) {
        return "PENDING_GAP_IN_BA_WINDOW";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_U_PID_SETTING) {
        return "REJECT_U_PID_SETTING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_EXTERNAL_REASON) {
        return "REFUSED_EXTERNAL_REASON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_AP_OUT_OF_MEMORY) {
        return "REFUSED_AP_OUT_OF_MEMORY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED) {
        return "REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::QUERY_RESP_OUTSTANDING) {
        return "QUERY_RESP_OUTSTANDING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_DSE_BAND) {
        return "REJECT_DSE_BAND";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_PROCESSING_TERMINATED) {
        return "TCLAS_PROCESSING_TERMINATED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_SCHEDULE_CONFLICT) {
        return "TS_SCHEDULE_CONFLICT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL) {
        return "DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCAOP_RESERVATION_CONFLICT) {
        return "MCCAOP_RESERVATION_CONFLICT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MAF_LIMIT_EXCEEDED) {
        return "MAF_LIMIT_EXCEEDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCA_TRACK_LIMIT_EXCEEDED) {
        return "MCCA_TRACK_LIMIT_EXCEEDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT) {
        return "DENIED_DUE_TO_SPECTRUM_MANAGEMENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_VHT) {
        return "ASSOC_DENIED_NO_VHT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ENABLEMENT_DENIED) {
        return "ENABLEMENT_DENIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::RESTRICTION_FROM_AUTHORIZED_GDB) {
        return "RESTRICTION_FROM_AUTHORIZED_GDB";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTHORIZATION_DEENABLED) {
        return "AUTHORIZATION_DEENABLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FILS_AUTHENTICATION_FAILURE) {
        return "FILS_AUTHENTICATION_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTHENTICATION_SERVER) {
        return "UNKNOWN_AUTHENTICATION_SERVER";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PREV_AUTH_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PREV_AUTH_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "PREV_AUTH_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PREV_AUTH_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DEAUTH_LEAVING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DEAUTH_LEAVING)) {
        os += (first ? "" : " | ");
        os += "DEAUTH_LEAVING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DEAUTH_LEAVING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_DUE_TO_INACTIVITY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_DUE_TO_INACTIVITY)) {
        os += (first ? "" : " | ");
        os += "DISASSOC_DUE_TO_INACTIVITY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_DUE_TO_INACTIVITY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_AP_BUSY) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_AP_BUSY)) {
        os += (first ? "" : " | ");
        os += "DISASSOC_AP_BUSY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_AP_BUSY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS2_FRAME_FROM_NONAUTH_STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS2_FRAME_FROM_NONAUTH_STA)) {
        os += (first ? "" : " | ");
        os += "CLASS2_FRAME_FROM_NONAUTH_STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS2_FRAME_FROM_NONAUTH_STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS3_FRAME_FROM_NONASSOC_STA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS3_FRAME_FROM_NONASSOC_STA)) {
        os += (first ? "" : " | ");
        os += "CLASS3_FRAME_FROM_NONASSOC_STA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS3_FRAME_FROM_NONASSOC_STA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_STA_HAS_LEFT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_STA_HAS_LEFT)) {
        os += (first ? "" : " | ");
        os += "DISASSOC_STA_HAS_LEFT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_STA_HAS_LEFT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_REQ_ASSOC_WITHOUT_AUTH) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_REQ_ASSOC_WITHOUT_AUTH)) {
        os += (first ? "" : " | ");
        os += "STA_REQ_ASSOC_WITHOUT_AUTH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_REQ_ASSOC_WITHOUT_AUTH;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PWR_CAPABILITY_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PWR_CAPABILITY_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "PWR_CAPABILITY_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PWR_CAPABILITY_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SUPPORTED_CHANNEL_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SUPPORTED_CHANNEL_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "SUPPORTED_CHANNEL_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SUPPORTED_CHANNEL_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BSS_TRANSITION_DISASSOC) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BSS_TRANSITION_DISASSOC)) {
        os += (first ? "" : " | ");
        os += "BSS_TRANSITION_DISASSOC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BSS_TRANSITION_DISASSOC;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_IE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_IE)) {
        os += (first ? "" : " | ");
        os += "INVALID_IE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_IE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MICHAEL_MIC_FAILURE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MICHAEL_MIC_FAILURE)) {
        os += (first ? "" : " | ");
        os += "MICHAEL_MIC_FAILURE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MICHAEL_MIC_FAILURE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::FOURWAY_HANDSHAKE_TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::FOURWAY_HANDSHAKE_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "FOURWAY_HANDSHAKE_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::FOURWAY_HANDSHAKE_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_KEY_UPDATE_TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_KEY_UPDATE_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "GROUP_KEY_UPDATE_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_KEY_UPDATE_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IE_IN_4WAY_DIFFERS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IE_IN_4WAY_DIFFERS)) {
        os += (first ? "" : " | ");
        os += "IE_IN_4WAY_DIFFERS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IE_IN_4WAY_DIFFERS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_CIPHER_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_CIPHER_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "GROUP_CIPHER_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_CIPHER_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PAIRWISE_CIPHER_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PAIRWISE_CIPHER_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "PAIRWISE_CIPHER_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PAIRWISE_CIPHER_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AKMP_NOT_VALID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AKMP_NOT_VALID)) {
        os += (first ? "" : " | ");
        os += "AKMP_NOT_VALID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AKMP_NOT_VALID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSUPPORTED_RSN_IE_VERSION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSUPPORTED_RSN_IE_VERSION)) {
        os += (first ? "" : " | ");
        os += "UNSUPPORTED_RSN_IE_VERSION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSUPPORTED_RSN_IE_VERSION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_RSN_IE_CAPAB) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_RSN_IE_CAPAB)) {
        os += (first ? "" : " | ");
        os += "INVALID_RSN_IE_CAPAB";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_RSN_IE_CAPAB;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IEEE_802_1X_AUTH_FAILED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IEEE_802_1X_AUTH_FAILED)) {
        os += (first ? "" : " | ");
        os += "IEEE_802_1X_AUTH_FAILED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IEEE_802_1X_AUTH_FAILED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CIPHER_SUITE_REJECTED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CIPHER_SUITE_REJECTED)) {
        os += (first ? "" : " | ");
        os += "CIPHER_SUITE_REJECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CIPHER_SUITE_REJECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNREACHABLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNREACHABLE)) {
        os += (first ? "" : " | ");
        os += "TDLS_TEARDOWN_UNREACHABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNREACHABLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNSPECIFIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "TDLS_TEARDOWN_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SSP_REQUESTED_DISASSOC) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SSP_REQUESTED_DISASSOC)) {
        os += (first ? "" : " | ");
        os += "SSP_REQUESTED_DISASSOC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SSP_REQUESTED_DISASSOC;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NO_SSP_ROAMING_AGREEMENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NO_SSP_ROAMING_AGREEMENT)) {
        os += (first ? "" : " | ");
        os += "NO_SSP_ROAMING_AGREEMENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NO_SSP_ROAMING_AGREEMENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BAD_CIPHER_OR_AKM) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BAD_CIPHER_OR_AKM)) {
        os += (first ? "" : " | ");
        os += "BAD_CIPHER_OR_AKM";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BAD_CIPHER_OR_AKM;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_AUTHORIZED_THIS_LOCATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_AUTHORIZED_THIS_LOCATION)) {
        os += (first ? "" : " | ");
        os += "NOT_AUTHORIZED_THIS_LOCATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_AUTHORIZED_THIS_LOCATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SERVICE_CHANGE_PRECLUDES_TS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SERVICE_CHANGE_PRECLUDES_TS)) {
        os += (first ? "" : " | ");
        os += "SERVICE_CHANGE_PRECLUDES_TS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SERVICE_CHANGE_PRECLUDES_TS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED_QOS_REASON) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED_QOS_REASON)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED_QOS_REASON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED_QOS_REASON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_ENOUGH_BANDWIDTH) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_ENOUGH_BANDWIDTH)) {
        os += (first ? "" : " | ");
        os += "NOT_ENOUGH_BANDWIDTH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_ENOUGH_BANDWIDTH;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_LOW_ACK) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_LOW_ACK)) {
        os += (first ? "" : " | ");
        os += "DISASSOC_LOW_ACK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_LOW_ACK;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXCEEDED_TXOP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXCEEDED_TXOP)) {
        os += (first ? "" : " | ");
        os += "EXCEEDED_TXOP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXCEEDED_TXOP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_LEAVING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_LEAVING)) {
        os += (first ? "" : " | ");
        os += "STA_LEAVING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_LEAVING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::END_TS_BA_DLS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::END_TS_BA_DLS)) {
        os += (first ? "" : " | ");
        os += "END_TS_BA_DLS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::END_TS_BA_DLS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNKNOWN_TS_BA) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNKNOWN_TS_BA)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN_TS_BA";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNKNOWN_TS_BA;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PEERKEY_MISMATCH) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PEERKEY_MISMATCH)) {
        os += (first ? "" : " | ");
        os += "PEERKEY_MISMATCH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PEERKEY_MISMATCH;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AUTHORIZED_ACCESS_LIMIT_REACHED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AUTHORIZED_ACCESS_LIMIT_REACHED)) {
        os += (first ? "" : " | ");
        os += "AUTHORIZED_ACCESS_LIMIT_REACHED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AUTHORIZED_ACCESS_LIMIT_REACHED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXTERNAL_SERVICE_REQUIREMENTS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXTERNAL_SERVICE_REQUIREMENTS)) {
        os += (first ? "" : " | ");
        os += "EXTERNAL_SERVICE_REQUIREMENTS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXTERNAL_SERVICE_REQUIREMENTS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FT_ACTION_FRAME_COUNT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FT_ACTION_FRAME_COUNT)) {
        os += (first ? "" : " | ");
        os += "INVALID_FT_ACTION_FRAME_COUNT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FT_ACTION_FRAME_COUNT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_PMKID) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_PMKID)) {
        os += (first ? "" : " | ");
        os += "INVALID_PMKID";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_PMKID;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_MDE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_MDE)) {
        os += (first ? "" : " | ");
        os += "INVALID_MDE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_MDE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FTE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FTE)) {
        os += (first ? "" : " | ");
        os += "INVALID_FTE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FTE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PEERING_CANCELLED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PEERING_CANCELLED)) {
        os += (first ? "" : " | ");
        os += "MESH_PEERING_CANCELLED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PEERING_CANCELLED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_PEERS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_PEERS)) {
        os += (first ? "" : " | ");
        os += "MESH_MAX_PEERS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_PEERS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIG_POLICY_VIOLATION) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIG_POLICY_VIOLATION)) {
        os += (first ? "" : " | ");
        os += "MESH_CONFIG_POLICY_VIOLATION";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIG_POLICY_VIOLATION;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CLOSE_RCVD) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CLOSE_RCVD)) {
        os += (first ? "" : " | ");
        os += "MESH_CLOSE_RCVD";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CLOSE_RCVD;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_RETRIES) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_RETRIES)) {
        os += (first ? "" : " | ");
        os += "MESH_MAX_RETRIES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_RETRIES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIRM_TIMEOUT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIRM_TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "MESH_CONFIRM_TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIRM_TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_GTK) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_GTK)) {
        os += (first ? "" : " | ");
        os += "MESH_INVALID_GTK";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_GTK;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INCONSISTENT_PARAMS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INCONSISTENT_PARAMS)) {
        os += (first ? "" : " | ");
        os += "MESH_INCONSISTENT_PARAMS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INCONSISTENT_PARAMS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_SECURITY_CAP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_SECURITY_CAP)) {
        os += (first ? "" : " | ");
        os += "MESH_INVALID_SECURITY_CAP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_SECURITY_CAP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_PROXY_INFO) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_PROXY_INFO)) {
        os += (first ? "" : " | ");
        os += "MESH_PATH_ERROR_NO_PROXY_INFO";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_PROXY_INFO;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_FORWARDING_INFO) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_FORWARDING_INFO)) {
        os += (first ? "" : " | ");
        os += "MESH_PATH_ERROR_NO_FORWARDING_INFO";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_FORWARDING_INFO;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_DEST_UNREACHABLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_DEST_UNREACHABLE)) {
        os += (first ? "" : " | ");
        os += "MESH_PATH_ERROR_DEST_UNREACHABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_DEST_UNREACHABLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS)) {
        os += (first ? "" : " | ");
        os += "MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_REGULATORY_REQ) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_REGULATORY_REQ)) {
        os += (first ? "" : " | ");
        os += "MESH_CHANNEL_SWITCH_REGULATORY_REQ";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_REGULATORY_REQ;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_UNSPECIFIED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "MESH_CHANNEL_SWITCH_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_UNSPECIFIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED) {
        return "UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PREV_AUTH_NOT_VALID) {
        return "PREV_AUTH_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DEAUTH_LEAVING) {
        return "DEAUTH_LEAVING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_DUE_TO_INACTIVITY) {
        return "DISASSOC_DUE_TO_INACTIVITY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_AP_BUSY) {
        return "DISASSOC_AP_BUSY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS2_FRAME_FROM_NONAUTH_STA) {
        return "CLASS2_FRAME_FROM_NONAUTH_STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS3_FRAME_FROM_NONASSOC_STA) {
        return "CLASS3_FRAME_FROM_NONASSOC_STA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_STA_HAS_LEFT) {
        return "DISASSOC_STA_HAS_LEFT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_REQ_ASSOC_WITHOUT_AUTH) {
        return "STA_REQ_ASSOC_WITHOUT_AUTH";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PWR_CAPABILITY_NOT_VALID) {
        return "PWR_CAPABILITY_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SUPPORTED_CHANNEL_NOT_VALID) {
        return "SUPPORTED_CHANNEL_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BSS_TRANSITION_DISASSOC) {
        return "BSS_TRANSITION_DISASSOC";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_IE) {
        return "INVALID_IE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MICHAEL_MIC_FAILURE) {
        return "MICHAEL_MIC_FAILURE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::FOURWAY_HANDSHAKE_TIMEOUT) {
        return "FOURWAY_HANDSHAKE_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_KEY_UPDATE_TIMEOUT) {
        return "GROUP_KEY_UPDATE_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IE_IN_4WAY_DIFFERS) {
        return "IE_IN_4WAY_DIFFERS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_CIPHER_NOT_VALID) {
        return "GROUP_CIPHER_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PAIRWISE_CIPHER_NOT_VALID) {
        return "PAIRWISE_CIPHER_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AKMP_NOT_VALID) {
        return "AKMP_NOT_VALID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSUPPORTED_RSN_IE_VERSION) {
        return "UNSUPPORTED_RSN_IE_VERSION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_RSN_IE_CAPAB) {
        return "INVALID_RSN_IE_CAPAB";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IEEE_802_1X_AUTH_FAILED) {
        return "IEEE_802_1X_AUTH_FAILED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CIPHER_SUITE_REJECTED) {
        return "CIPHER_SUITE_REJECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNREACHABLE) {
        return "TDLS_TEARDOWN_UNREACHABLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNSPECIFIED) {
        return "TDLS_TEARDOWN_UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SSP_REQUESTED_DISASSOC) {
        return "SSP_REQUESTED_DISASSOC";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NO_SSP_ROAMING_AGREEMENT) {
        return "NO_SSP_ROAMING_AGREEMENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BAD_CIPHER_OR_AKM) {
        return "BAD_CIPHER_OR_AKM";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_AUTHORIZED_THIS_LOCATION) {
        return "NOT_AUTHORIZED_THIS_LOCATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SERVICE_CHANGE_PRECLUDES_TS) {
        return "SERVICE_CHANGE_PRECLUDES_TS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED_QOS_REASON) {
        return "UNSPECIFIED_QOS_REASON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_ENOUGH_BANDWIDTH) {
        return "NOT_ENOUGH_BANDWIDTH";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_LOW_ACK) {
        return "DISASSOC_LOW_ACK";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXCEEDED_TXOP) {
        return "EXCEEDED_TXOP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_LEAVING) {
        return "STA_LEAVING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::END_TS_BA_DLS) {
        return "END_TS_BA_DLS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNKNOWN_TS_BA) {
        return "UNKNOWN_TS_BA";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PEERKEY_MISMATCH) {
        return "PEERKEY_MISMATCH";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AUTHORIZED_ACCESS_LIMIT_REACHED) {
        return "AUTHORIZED_ACCESS_LIMIT_REACHED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXTERNAL_SERVICE_REQUIREMENTS) {
        return "EXTERNAL_SERVICE_REQUIREMENTS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FT_ACTION_FRAME_COUNT) {
        return "INVALID_FT_ACTION_FRAME_COUNT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_PMKID) {
        return "INVALID_PMKID";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_MDE) {
        return "INVALID_MDE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FTE) {
        return "INVALID_FTE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PEERING_CANCELLED) {
        return "MESH_PEERING_CANCELLED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_PEERS) {
        return "MESH_MAX_PEERS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIG_POLICY_VIOLATION) {
        return "MESH_CONFIG_POLICY_VIOLATION";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CLOSE_RCVD) {
        return "MESH_CLOSE_RCVD";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_RETRIES) {
        return "MESH_MAX_RETRIES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIRM_TIMEOUT) {
        return "MESH_CONFIRM_TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_GTK) {
        return "MESH_INVALID_GTK";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INCONSISTENT_PARAMS) {
        return "MESH_INCONSISTENT_PARAMS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_SECURITY_CAP) {
        return "MESH_INVALID_SECURITY_CAP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_PROXY_INFO) {
        return "MESH_PATH_ERROR_NO_PROXY_INFO";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_FORWARDING_INFO) {
        return "MESH_PATH_ERROR_NO_FORWARDING_INFO";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_DEST_UNREACHABLE) {
        return "MESH_PATH_ERROR_DEST_UNREACHABLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS) {
        return "MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_REGULATORY_REQ) {
        return "MESH_CHANNEL_SWITCH_REGULATORY_REQ";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_UNSPECIFIED) {
        return "MESH_CHANNEL_SWITCH_UNSPECIFIED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_START) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_START)) {
        os += (first ? "" : " | ");
        os += "ASSOC_START";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_START;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_COMPLETE) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_COMPLETE)) {
        os += (first ? "" : " | ");
        os += "ASSOC_COMPLETE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_COMPLETE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::DISASSOC) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::DISASSOC)) {
        os += (first ? "" : " | ");
        os += "DISASSOC";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::DISASSOC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_START) {
        return "ASSOC_START";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_COMPLETE) {
        return "ASSOC_COMPLETE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::DISASSOC) {
        return "DISASSOC";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::descriptor;
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State, 10> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::DISCONNECTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::IFACE_DISABLED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::INACTIVE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::SCANNING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::AUTHENTICATING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::ASSOCIATED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::FOURWAY_HANDSHAKE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::GROUP_HANDSHAKE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State::COMPLETED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod, 2> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::OMA_DM,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod::SOAP_XML_SPP,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError, 21> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_ERROR,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::OOB_IFACE_READ_ERROR,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DECRYPTION_CRC_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_24_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_50_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SIGNAL_TOO_WEAK,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_AUTH_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NETWORK_ASSOC_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_DHCP_RESPONSE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::FAILED_DHCP_CONFIG,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::IP_ADDR_CONFLICT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::NO_CONN_TO_REGISTRAR,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MULTIPLE_PBC_DETECTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::ROGUE_SUSPECTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEVICE_BUSY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::SETUP_LOCKED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::MSG_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::REG_SESS_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::DEV_PASSWORD_AUTH_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::CHAN_60G_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError::PUBLIC_KEY_HASH_MISMATCH,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication, 4> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::NO_ERROR,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_TKIP_ONLY_PROHIBITED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::SECURITY_WEP_PROHIBITED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication::AUTH_FAILURE,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode, 97> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_ALTERNATE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TDLS_WAKEUP_REJECT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SECURITY_DISABLED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNACCEPTABLE_LIFETIME,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_IN_SAME_BSS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CAPS_UNSUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REASSOC_NO_ASSOC,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_UNSPEC,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NOT_SUPPORTED_AUTH_ALG,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTH_TRANSACTION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CHALLENGE_FAIL,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTH_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AP_UNABLE_TO_HANDLE_NEW_STA,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_RATES,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NOSHORT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SPEC_MGMT_REQUIRED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PWR_CAPABILITY_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUPPORTED_CHANNEL_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_SHORT_SLOT_TIME,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_HT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::R0KH_UNREACHABLE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_PCO,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_REJECTED_TEMPORARILY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ROBUST_MGMT_FRAME_POLICY_VIOLATION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSPECIFIED_QOS_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_INSUFFICIENT_BANDWIDTH,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_POOR_CHANNEL_CONDITIONS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_QOS_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUEST_DECLINED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PARAMETERS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_CHANGES,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_IE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GROUP_CIPHER_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PAIRWISE_CIPHER_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AKMP_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNSUPPORTED_RSN_IE_VERSION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSN_IE_CAPAB,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CIPHER_REJECTED_PER_POLICY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_NOT_CREATED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DIRECT_LINK_NOT_ALLOWED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_PRESENT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DEST_STA_NOT_QOS_STA,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_LISTEN_INT_TOO_LARGE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FT_ACTION_FRAME_COUNT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_PMKID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_MDIE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_FTIE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQUESTED_TCLAS_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INSUFFICIENT_TCLAS_PROCESSING_RESOURCES,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRY_ANOTHER_BSS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_ADV_PROTO_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::NO_OUTSTANDING_GAS_REQ,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_NOT_RECEIVED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::STA_TIMED_OUT_WAITING_FOR_GAS_RESP,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::GAS_RESP_LARGER_THAN_LIMIT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_HOME,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ADV_SRV_UNREACHABLE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_SSPN,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_REFUSED_UNAUTH_ACCESS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::INVALID_RSNIE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::U_APSD_COEX_MODE_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::BAD_INTERVAL_WITH_U_APSD_COEX,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ANTI_CLOGGING_TOKEN_REQ,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FINITE_CYCLIC_GROUP_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::CANNOT_FIND_ALT_TBTT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TRANSMISSION_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REQ_TCLAS_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_RESOURCES_EXCHAUSTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_WITH_SUGGESTED_BSS_TRANSITION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_WITH_SCHEDULE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_NO_WAKEUP_SPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::SUCCESS_POWER_SAVE_MODE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_ADMITTING_FST_SESSION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PERFORMING_FST_NOW,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::PENDING_GAP_IN_BA_WINDOW,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_U_PID_SETTING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_EXTERNAL_REASON,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REFUSED_AP_OUT_OF_MEMORY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECTED_EMERGENCY_SERVICE_NOT_SUPPORTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::QUERY_RESP_OUTSTANDING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::REJECT_DSE_BAND,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TCLAS_PROCESSING_TERMINATED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::TS_SCHEDULE_CONFLICT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_WITH_SUGGESTED_BAND_AND_CHANNEL,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCAOP_RESERVATION_CONFLICT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MAF_LIMIT_EXCEEDED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::MCCA_TRACK_LIMIT_EXCEEDED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::DENIED_DUE_TO_SPECTRUM_MANAGEMENT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ASSOC_DENIED_NO_VHT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::ENABLEMENT_DENIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::RESTRICTION_FROM_AUTHORIZED_GDB,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::AUTHORIZATION_DEENABLED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::FILS_AUTHENTICATION_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode::UNKNOWN_AUTHENTICATION_SERVER,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode, 61> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PREV_AUTH_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DEAUTH_LEAVING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_DUE_TO_INACTIVITY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_AP_BUSY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS2_FRAME_FROM_NONAUTH_STA,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CLASS3_FRAME_FROM_NONASSOC_STA,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_STA_HAS_LEFT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_REQ_ASSOC_WITHOUT_AUTH,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PWR_CAPABILITY_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SUPPORTED_CHANNEL_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BSS_TRANSITION_DISASSOC,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_IE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MICHAEL_MIC_FAILURE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::FOURWAY_HANDSHAKE_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_KEY_UPDATE_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IE_IN_4WAY_DIFFERS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::GROUP_CIPHER_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PAIRWISE_CIPHER_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AKMP_NOT_VALID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSUPPORTED_RSN_IE_VERSION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_RSN_IE_CAPAB,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::IEEE_802_1X_AUTH_FAILED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::CIPHER_SUITE_REJECTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNREACHABLE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TDLS_TEARDOWN_UNSPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SSP_REQUESTED_DISASSOC,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NO_SSP_ROAMING_AGREEMENT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::BAD_CIPHER_OR_AKM,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_AUTHORIZED_THIS_LOCATION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::SERVICE_CHANGE_PRECLUDES_TS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNSPECIFIED_QOS_REASON,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::NOT_ENOUGH_BANDWIDTH,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::DISASSOC_LOW_ACK,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXCEEDED_TXOP,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::STA_LEAVING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::END_TS_BA_DLS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::UNKNOWN_TS_BA,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::PEERKEY_MISMATCH,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::AUTHORIZED_ACCESS_LIMIT_REACHED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::EXTERNAL_SERVICE_REQUIREMENTS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FT_ACTION_FRAME_COUNT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_PMKID,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_MDE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::INVALID_FTE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PEERING_CANCELLED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_PEERS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIG_POLICY_VIOLATION,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CLOSE_RCVD,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_MAX_RETRIES,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CONFIRM_TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_GTK,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INCONSISTENT_PARAMS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_INVALID_SECURITY_CAP,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_PROXY_INFO,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_NO_FORWARDING_INFO,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_PATH_ERROR_DEST_UNREACHABLE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MAC_ADDRESS_ALREADY_EXISTS_IN_MBSS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_REGULATORY_REQ,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode::MESH_CHANNEL_SWITCH_UNSPECIFIED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason, 3> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_START,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::ASSOC_COMPLETE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason::DISASSOC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTSTAIFACECALLBACK_H
