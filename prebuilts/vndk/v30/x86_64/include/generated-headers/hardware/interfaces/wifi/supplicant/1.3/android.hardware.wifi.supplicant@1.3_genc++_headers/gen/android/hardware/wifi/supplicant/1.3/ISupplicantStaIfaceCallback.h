#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_ISUPPLICANTSTAIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_ISUPPLICANTSTAIFACECALLBACK_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaIfaceCallback.h>
#include <android/hardware/wifi/supplicant/1.0/types.h>
#include <android/hardware/wifi/supplicant/1.2/ISupplicantStaIfaceCallback.h>
#include <android/hardware/wifi/supplicant/1.3/types.h>

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
namespace V1_3 {

/**
 * Callback Interface exposed by the supplicant service
 * for each station mode interface (ISupplicantStaIface).
 *
 * Clients need to host an instance of this HIDL interface object and
 * pass a reference of the object to the supplicant via the
 * corresponding |ISupplicantStaIface.registerCallback_1_3| method.
 */
struct ISupplicantStaIfaceCallback : public ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.3::ISupplicantStaIfaceCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class BssTmStatusCode : uint8_t;
    enum class BssTmDataFlagsMask : uint32_t;
    enum class MboTransitionReasonCode : uint8_t;
    enum class MboCellularDataConnectionPrefValue : uint8_t;
    struct BssTmData;

    /**
     * IEEE Std 802.11-2016 - Table 9-357.
     * BTM status code filled in BSS transition management response frame.
     */
    enum class BssTmStatusCode : uint8_t {
        ACCEPT = 0,
        REJECT_UNSPECIFIED = 1,
        REJECT_INSUFFICIENT_BEACON = 2,
        REJECT_INSUFFICIENT_CAPABITY = 3,
        REJECT_BSS_TERMINATION_UNDESIRED = 4,
        REJECT_BSS_TERMINATION_DELAY_REQUEST = 5,
        REJECT_STA_CANDIDATE_LIST_PROVIDED = 6,
        REJECT_NO_SUITABLE_CANDIDATES = 7,
        REJECT_LEAVING_ESS = 8,
    };

    /**
     * Bitmask of various information retrieved from BSS transition management request frame.
     */
    enum class BssTmDataFlagsMask : uint32_t {
        /**
         * Preferred candidate list included.
         */
        WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED = 1u /* 1 << 0 */,
        /**
         * Abridged.
         */
        WNM_MODE_ABRIDGED = 2u /* 1 << 1 */,
        /**
         * Disassociation Imminent.
         */
        WNM_MODE_DISASSOCIATION_IMMINENT = 4u /* 1 << 2 */,
        /**
         * BSS termination included.
         */
        WNM_MODE_BSS_TERMINATION_INCLUDED = 8u /* 1 << 3 */,
        /**
         * ESS Disassociation Imminent.
         */
        WNM_MODE_ESS_DISASSOCIATION_IMMINENT = 16u /* 1 << 4 */,
        /**
         * MBO transition reason code included.
         */
        MBO_TRANSITION_REASON_CODE_INCLUDED = 32u /* 1 << 5 */,
        /**
         * MBO retry delay time included.
         */
        MBO_ASSOC_RETRY_DELAY_INCLUDED = 64u /* 1 << 6 */,
        /**
         * MBO cellular data connection preference value included.
         */
        MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED = 128u /* 1 << 7 */,
    };

    /**
     *  MBO spec v1.2, 4.2.6 Table 18: MBO transition reason code attribute
     *  values.
     */
    enum class MboTransitionReasonCode : uint8_t {
        UNSPECIFIED = 0,
        EXCESSIVE_FRAME_LOSS = 1,
        EXCESSIVE_TRAFFIC_DELAY = 2,
        INSUFFICIENT_BANDWIDTH = 3,
        LOAD_BALANCING = 4,
        LOW_RSSI = 5,
        RX_EXCESSIVE_RETRIES = 6,
        HIGH_INTERFERENCE = 7,
        GRAY_ZONE = 8,
        TRANSITION_TO_PREMIUM_AP = 9,
    };

    /**
     *  MBO spec v1.2, 4.2.5 Table 16: MBO Cellular Data connection preference
     *  attribute values. AP use this to indicate STA, its preference for the
     *  STA to move from BSS to cellular network.
     */
    enum class MboCellularDataConnectionPrefValue : uint8_t {
        EXCLUDED = 0,
        NOT_PREFERRED = 1,
        /*
         * 2-254 Reserved.
         */
        PREFERRED = 255,
    };

    /**
     * Data retrieved from received BSS transition management request frame.
     */
    struct BssTmData final {
        /*
         * Status code filled in BSS transition management response frame
         */
        ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode status __attribute__ ((aligned(1)));
        /*
         * Bitmask of BssTmDataFlagsMask
         */
        ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask> flags __attribute__ ((aligned(4)));
        /*
         * Duration for which STA shouldn't try to re-associate.
         */
        uint32_t assocRetryDelayMs __attribute__ ((aligned(4)));
        /*
         * Reason for BSS transition request.
         */
        ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode mboTransitionReason __attribute__ ((aligned(1)));
        /*
         * Cellular Data Connection preference value.
         */
        ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue mboCellPreference __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData, status) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData, flags) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData, assocRetryDelayMs) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData, mboTransitionReason) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData, mboCellPreference) == 13, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData) == 4, "wrong alignment");

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
     * Used to indicate an EAP authentication failure.
     */
    virtual ::android::hardware::Return<void> onEapFailure_1_1(::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback::EapErrorCode errorCode) = 0;

    /**
     * Indicates DPP configuration received success event (Enrolee mode).
     */
    virtual ::android::hardware::Return<void> onDppSuccessConfigReceived(const ::android::hardware::hidl_vec<uint8_t>& ssid, const ::android::hardware::hidl_string& password, const ::android::hardware::hidl_array<uint8_t, 32>& psk, ::android::hardware::wifi::supplicant::V1_2::DppAkm securityAkm) = 0;

    /**
     * Indicates DPP configuration sent success event (Configurator mode).
     */
    virtual ::android::hardware::Return<void> onDppSuccessConfigSent() = 0;

    /**
     * Indicates a DPP progress event.
     */
    virtual ::android::hardware::Return<void> onDppProgress(::android::hardware::wifi::supplicant::V1_2::DppProgressCode code) = 0;

    /**
     * Indicates a DPP failure event.
     */
    virtual ::android::hardware::Return<void> onDppFailure(::android::hardware::wifi::supplicant::V1_2::DppFailureCode code) = 0;

    /**
     * Indicates pairwise master key (PMK) cache added event.
     *
     * @param expirationTimeInSec expiration time in seconds
     * @param serializedEntry is serialized PMK cache entry, the content is
     *              opaque for the framework and depends on the native implementation.
     */
    virtual ::android::hardware::Return<void> onPmkCacheAdded(int64_t expirationTimeInSec, const ::android::hardware::hidl_vec<uint8_t>& serializedEntry) = 0;

    /**
     * Indicates a DPP success event.
     */
    virtual ::android::hardware::Return<void> onDppSuccess(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode code) = 0;

    /**
     * Indicates a DPP progress event.
     */
    virtual ::android::hardware::Return<void> onDppProgress_1_3(::android::hardware::wifi::supplicant::V1_3::DppProgressCode code) = 0;

    /**
     * Indicates a DPP failure event.
     *
     * ssid: A string indicating the SSID for the AP that the Enrollee attempted to connect.
     * channelList: A string containing a list of operating channels and operating classes
     *     indicating the channels that the Enrollee scanned in attempting to discover the AP.
     *     The list conforms to the following ABNF syntax:
     *         channel-list2 = class-and-channels *(“,” class-and-channels)
     *         class-and-channels = class “/” channel *(“,” channel)
     *         class = 1*3DIGIT
     *         channel = 1*3DIGIT
     * bandList: A list of band parameters that are supported by the Enrollee expressed as the
     *     Operating Class.
     */
    virtual ::android::hardware::Return<void> onDppFailure_1_3(::android::hardware::wifi::supplicant::V1_3::DppFailureCode code, const ::android::hardware::hidl_string& ssid, const ::android::hardware::hidl_string& channelList, const ::android::hardware::hidl_vec<uint16_t>& bandList) = 0;

    /**
     * Indicates BTM request frame handling status.
     *
     * @param BssTmData Data retrieved from received BSS transition management
     * request frame.
     */
    virtual ::android::hardware::Return<void> onBssTmHandlingDone(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& tmData) = 0;

    /**
     * Indicates an EAP authentication failure.
     * @param errorCode Error code for EAP authentication failure.
     *        Either standard error code (enum EapErrorCode) or
     *        private error code defined by network provider.
     */
    virtual ::android::hardware::Return<void> onEapFailure_1_3(uint32_t errorCode) = 0;

    /**
     * Used to indicate a state change event on this particular iface. If this
     * event is triggered by a particular network, the |SupplicantNetworkId|,
     * |ssid|, |bssid| parameters must indicate the parameters of the network/AP
     * which caused this state transition.
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
     * @param filsHlpSent If FILS HLP IEs were included in this association.
     */
    virtual ::android::hardware::Return<void> onStateChanged_1_3(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State newState, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& ssid, bool filsHlpSent) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::ACCEPT) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::ACCEPT)) {
        os += (first ? "" : " | ");
        os += "ACCEPT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::ACCEPT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_UNSPECIFIED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "REJECT_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_BEACON) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_BEACON)) {
        os += (first ? "" : " | ");
        os += "REJECT_INSUFFICIENT_BEACON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_BEACON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY)) {
        os += (first ? "" : " | ");
        os += "REJECT_INSUFFICIENT_CAPABITY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED)) {
        os += (first ? "" : " | ");
        os += "REJECT_BSS_TERMINATION_UNDESIRED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST)) {
        os += (first ? "" : " | ");
        os += "REJECT_BSS_TERMINATION_DELAY_REQUEST";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED)) {
        os += (first ? "" : " | ");
        os += "REJECT_STA_CANDIDATE_LIST_PROVIDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES)) {
        os += (first ? "" : " | ");
        os += "REJECT_NO_SUITABLE_CANDIDATES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_LEAVING_ESS) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_LEAVING_ESS)) {
        os += (first ? "" : " | ");
        os += "REJECT_LEAVING_ESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_LEAVING_ESS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::ACCEPT) {
        return "ACCEPT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_UNSPECIFIED) {
        return "REJECT_UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_BEACON) {
        return "REJECT_INSUFFICIENT_BEACON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY) {
        return "REJECT_INSUFFICIENT_CAPABITY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED) {
        return "REJECT_BSS_TERMINATION_UNDESIRED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST) {
        return "REJECT_BSS_TERMINATION_DELAY_REQUEST";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED) {
        return "REJECT_STA_CANDIDATE_LIST_PROVIDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES) {
        return "REJECT_NO_SUITABLE_CANDIDATES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_LEAVING_ESS) {
        return "REJECT_LEAVING_ESS";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED)) {
        os += (first ? "" : " | ");
        os += "WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ABRIDGED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ABRIDGED)) {
        os += (first ? "" : " | ");
        os += "WNM_MODE_ABRIDGED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ABRIDGED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT)) {
        os += (first ? "" : " | ");
        os += "WNM_MODE_DISASSOCIATION_IMMINENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED)) {
        os += (first ? "" : " | ");
        os += "WNM_MODE_BSS_TERMINATION_INCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT)) {
        os += (first ? "" : " | ");
        os += "WNM_MODE_ESS_DISASSOCIATION_IMMINENT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED)) {
        os += (first ? "" : " | ");
        os += "MBO_TRANSITION_REASON_CODE_INCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED)) {
        os += (first ? "" : " | ");
        os += "MBO_ASSOC_RETRY_DELAY_INCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED)) {
        os += (first ? "" : " | ");
        os += "MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED) {
        return "WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ABRIDGED) {
        return "WNM_MODE_ABRIDGED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT) {
        return "WNM_MODE_DISASSOCIATION_IMMINENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED) {
        return "WNM_MODE_BSS_TERMINATION_INCLUDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT) {
        return "WNM_MODE_ESS_DISASSOCIATION_IMMINENT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED) {
        return "MBO_TRANSITION_REASON_CODE_INCLUDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED) {
        return "MBO_ASSOC_RETRY_DELAY_INCLUDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED) {
        return "MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::UNSPECIFIED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::UNSPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS)) {
        os += (first ? "" : " | ");
        os += "EXCESSIVE_FRAME_LOSS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY)) {
        os += (first ? "" : " | ");
        os += "EXCESSIVE_TRAFFIC_DELAY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH)) {
        os += (first ? "" : " | ");
        os += "INSUFFICIENT_BANDWIDTH";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOAD_BALANCING) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOAD_BALANCING)) {
        os += (first ? "" : " | ");
        os += "LOAD_BALANCING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOAD_BALANCING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOW_RSSI) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOW_RSSI)) {
        os += (first ? "" : " | ");
        os += "LOW_RSSI";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOW_RSSI;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES)) {
        os += (first ? "" : " | ");
        os += "RX_EXCESSIVE_RETRIES";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::HIGH_INTERFERENCE) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::HIGH_INTERFERENCE)) {
        os += (first ? "" : " | ");
        os += "HIGH_INTERFERENCE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::HIGH_INTERFERENCE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::GRAY_ZONE) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::GRAY_ZONE)) {
        os += (first ? "" : " | ");
        os += "GRAY_ZONE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::GRAY_ZONE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP)) {
        os += (first ? "" : " | ");
        os += "TRANSITION_TO_PREMIUM_AP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::UNSPECIFIED) {
        return "UNSPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS) {
        return "EXCESSIVE_FRAME_LOSS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY) {
        return "EXCESSIVE_TRAFFIC_DELAY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH) {
        return "INSUFFICIENT_BANDWIDTH";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOAD_BALANCING) {
        return "LOAD_BALANCING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOW_RSSI) {
        return "LOW_RSSI";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES) {
        return "RX_EXCESSIVE_RETRIES";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::HIGH_INTERFERENCE) {
        return "HIGH_INTERFERENCE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::GRAY_ZONE) {
        return "GRAY_ZONE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP) {
        return "TRANSITION_TO_PREMIUM_AP";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::EXCLUDED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::EXCLUDED)) {
        os += (first ? "" : " | ");
        os += "EXCLUDED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::EXCLUDED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::NOT_PREFERRED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::NOT_PREFERRED)) {
        os += (first ? "" : " | ");
        os += "NOT_PREFERRED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::NOT_PREFERRED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::PREFERRED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::PREFERRED)) {
        os += (first ? "" : " | ");
        os += "PREFERRED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::PREFERRED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::EXCLUDED) {
        return "EXCLUDED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::NOT_PREFERRED) {
        return "NOT_PREFERRED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::PREFERRED) {
        return "PREFERRED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::wifi::supplicant::V1_3::toString(o.status);
    os += ", .flags = ";
    os += ::android::hardware::wifi::supplicant::V1_3::toString<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask>(o.flags);
    os += ", .assocRetryDelayMs = ";
    os += ::android::hardware::toString(o.assocRetryDelayMs);
    os += ", .mboTransitionReason = ";
    os += ::android::hardware::wifi::supplicant::V1_3::toString(o.mboTransitionReason);
    os += ", .mboCellPreference = ";
    os += ::android::hardware::wifi::supplicant::V1_3::toString(o.mboCellPreference);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    if (lhs.assocRetryDelayMs != rhs.assocRetryDelayMs) {
        return false;
    }
    if (lhs.mboTransitionReason != rhs.mboTransitionReason) {
        return false;
    }
    if (lhs.mboCellPreference != rhs.mboCellPreference) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& lhs, const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_3
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode, 9> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode> = {
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::ACCEPT,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_UNSPECIFIED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_BEACON,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_INSUFFICIENT_CAPABITY,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_UNDESIRED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_BSS_TERMINATION_DELAY_REQUEST,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_STA_CANDIDATE_LIST_PROVIDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_NO_SUITABLE_CANDIDATES,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmStatusCode::REJECT_LEAVING_ESS,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask, 8> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask> = {
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_PREFERRED_CANDIDATE_LIST_INCLUDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ABRIDGED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_DISASSOCIATION_IMMINENT,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_BSS_TERMINATION_INCLUDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::WNM_MODE_ESS_DISASSOCIATION_IMMINENT,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_TRANSITION_REASON_CODE_INCLUDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_ASSOC_RETRY_DELAY_INCLUDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmDataFlagsMask::MBO_CELLULAR_DATA_CONNECTION_PREFERENCE_INCLUDED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode, 10> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode> = {
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::UNSPECIFIED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_FRAME_LOSS,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::EXCESSIVE_TRAFFIC_DELAY,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::INSUFFICIENT_BANDWIDTH,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOAD_BALANCING,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::LOW_RSSI,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::RX_EXCESSIVE_RETRIES,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::HIGH_INTERFERENCE,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::GRAY_ZONE,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboTransitionReasonCode::TRANSITION_TO_PREMIUM_AP,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue, 3> hidl_enum_values<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue> = {
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::EXCLUDED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::NOT_PREFERRED,
    ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::MboCellularDataConnectionPrefValue::PREFERRED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_ISUPPLICANTSTAIFACECALLBACK_H
