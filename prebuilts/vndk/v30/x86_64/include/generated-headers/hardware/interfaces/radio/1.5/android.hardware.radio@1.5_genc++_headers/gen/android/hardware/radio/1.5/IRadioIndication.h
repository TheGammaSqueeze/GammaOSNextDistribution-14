#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_IRADIOINDICATION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_IRADIOINDICATION_H

#include <android/hardware/radio/1.0/types.h>
#include <android/hardware/radio/1.4/IRadioIndication.h>
#include <android/hardware/radio/1.5/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_5 {

/**
 * Interface declaring unsolicited radio indications.
 */
struct IRadioIndication : public ::android::hardware::radio::V1_4::IRadioIndication {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.radio@1.5::IRadioIndication"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Indicates when radio state changes.
     *
     * @param type Type of radio indication
     * @param radioState Current radio state
     */
    virtual ::android::hardware::Return<void> radioStateChanged(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::RadioState radioState) = 0;

    /**
     * Indicates when call state has changed.
     * Callee must invoke IRadio.getCurrentCalls()
     * Must be invoked on, for example,
     * "RING", "BUSY", "NO CARRIER", and also call state
     * transitions (DIALING->ALERTING ALERTING->ACTIVE)
     *
     * Redundent or extraneous invocations are tolerated
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> callStateChanged(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when voice or data network state changed
     * Callee must invoke IRadio.getVoiceRegistrationState(), IRadio.getDataRegistrationState(),
     * and IRadio.getOperator()
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> networkStateChanged(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when new SMS is received.
     * Callee must subsequently confirm the receipt of the SMS with a
     * acknowledgeLastIncomingGsmSms()
     *
     * Server must not send newSms() nor newSmsStatusReport() messages until a
     * acknowledgeLastIncomingGsmSms() has been received
     *
     * @param type Type of radio indication
     * @param pdu PDU of SMS-DELIVER represented as byte array.
     *        The PDU starts with the SMSC address per TS 27.005 (+CMT:)
     */
    virtual ::android::hardware::Return<void> newSms(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<uint8_t>& pdu) = 0;

    /**
     * Indicates when new SMS Status Report is received.
     * Callee must subsequently confirm the receipt of the SMS with a
     * acknowledgeLastIncomingGsmSms()
     *
     * Server must not send newSms() nor newSmsStatusReport() messages until a
     * acknowledgeLastIncomingGsmSms() has been received
     *
     * @param type Type of radio indication
     * @param pdu PDU of SMS-STATUS-REPORT represented as byte array.
     *        The PDU starts with the SMSC address per TS 27.005 (+CMT:)
     */
    virtual ::android::hardware::Return<void> newSmsStatusReport(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<uint8_t>& pdu) = 0;

    /**
     * Indicates when new SMS has been stored on SIM card
     *
     * @param type Type of radio indication
     * @param recordNumber Record number on the sim
     */
    virtual ::android::hardware::Return<void> newSmsOnSim(::android::hardware::radio::V1_0::RadioIndicationType type, int32_t recordNumber) = 0;

    /**
     * Indicates when a new USSD message is received.
     * The USSD session is assumed to persist if the type code is REQUEST, otherwise
     * the current session (if any) is assumed to have terminated.
     *
     * @param type Type of radio indication
     * @param modeType USSD type code
     * @param msg Message string in UTF-8, if applicable
     */
    virtual ::android::hardware::Return<void> onUssd(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::UssdModeType modeType, const ::android::hardware::hidl_string& msg) = 0;

    /**
     * Indicates when radio has received a NITZ time message.
     *
     * @param type Type of radio indication
     * @param nitzTime NITZ time string in the form "yy/mm/dd,hh:mm:ss(+/-)tz,dt"
     * @param receivedTime milliseconds since boot that the NITZ time was received
     */
    virtual ::android::hardware::Return<void> nitzTimeReceived(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_string& nitzTime, uint64_t receivedTime) = 0;

    /**
     * Indicates current signal strength of the radio.
     *
     * @param type Type of radio indication
     * @param signalStrength SignalStrength information as defined in types.hal
     */
    virtual ::android::hardware::Return<void> currentSignalStrength(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::SignalStrength& signalStrength) = 0;

    /**
     * Indicates data call contexts have changed.
     *
     * @param type Type of radio indication
     * @param dcList array of SetupDataCallResult identical to that
     *        returned by IRadio.getDataCallList(). It is the complete list
     *        of current data contexts including new contexts that have been
     *        activated. A data call is only removed from this list when the
     *        framework sends a IRadio.deactivateDataCall() or the radio
     *        is powered off/on
     */
    virtual ::android::hardware::Return<void> dataCallListChanged(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::SetupDataCallResult>& dcList) = 0;

    /**
     * Reports supplementary service related notification from the network.
     *
     * @param type Type of radio indication
     * @param suppSvc SuppSvcNotification as defined in types.hal
     */
    virtual ::android::hardware::Return<void> suppSvcNotify(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::SuppSvcNotification& suppSvc) = 0;

    /**
     * Indicates when STK session is terminated by SIM.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> stkSessionEnd(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when SIM issue a STK proactive command to applications
     *
     * @param type Type of radio indication
     * @param cmd SAT/USAT proactive represented as byte array starting with command tag.
     *        Refer ETSI TS 102.223 section 9.4 for command types
     */
    virtual ::android::hardware::Return<void> stkProactiveCommand(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_string& cmd) = 0;

    /**
     * Indicates when SIM notifies applcations some event happens.
     *
     * @param type Type of radio indication
     * @param cmd SAT/USAT commands or responses
     *        sent by ME to SIM or commands handled by ME, represented as byte array
     *        starting with first byte of response data for command tag. Refer
     *        ETSI TS 102.223 section 9.4 for command types
     */
    virtual ::android::hardware::Return<void> stkEventNotify(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_string& cmd) = 0;

    /**
     * Indicates when SIM wants application to setup a voice call.
     *
     * @param type Type of radio indication
     * @param timeout Timeout value in millisec for setting up voice call
     */
    virtual ::android::hardware::Return<void> stkCallSetup(::android::hardware::radio::V1_0::RadioIndicationType type, int64_t timeout) = 0;

    /**
     * Indicates that SMS storage on the SIM is full. Sent when the network
     * attempts to deliver a new SMS message. Messages cannot be saved on the
     * SIM until space is freed. In particular, incoming Class 2 messages must not
     * be stored
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> simSmsStorageFull(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates that file(s) on the SIM have been updated, or the SIM
     * has been reinitialized.
     * Note: If the SIM state changes as a result of the SIM refresh (eg,
     * SIM_READY -> SIM_LOCKED_OR_ABSENT), simStatusChanged()
     * must be sent.
     *
     * @param type Type of radio indication
     * @param refreshResult Result of sim refresh
     */
    virtual ::android::hardware::Return<void> simRefresh(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::SimRefreshResult& refreshResult) = 0;

    /**
     * Ring indication for an incoming call (eg, RING or CRING event).
     * There must be at least one callRing() at the beginning
     * of a call and sending multiple is optional. If the system property
     * ro.telephony.call_ring.multiple is false then the upper layers
     * must generate the multiple events internally. Otherwise the vendor
     * code must generate multiple callRing() if
     * ro.telephony.call_ring.multiple is true or if it is absent.
     *
     * The rate of these events is controlled by ro.telephony.call_ring.delay
     * and has a default value of 3000 (3 seconds) if absent.
     *
     * @param type Type of radio indication
     * @param isGsm true for GSM & false for CDMA
     * @param record Cdma Signal Information
     */
    virtual ::android::hardware::Return<void> callRing(::android::hardware::radio::V1_0::RadioIndicationType type, bool isGsm, const ::android::hardware::radio::V1_0::CdmaSignalInfoRecord& record) = 0;

    /**
     * Indicates that SIM state changes.
     * Callee must invoke getIccCardStatus()
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> simStatusChanged(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when new CDMA SMS is received
     * Callee must subsequently confirm the receipt of the SMS with
     * acknowledgeLastIncomingCdmaSms()
     * Server must not send cdmaNewSms() messages until
     * acknowledgeLastIncomingCdmaSms() has been received
     *
     * @param type Type of radio indication
     * @param msg Cdma Sms Message
     */
    virtual ::android::hardware::Return<void> cdmaNewSms(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::CdmaSmsMessage& msg) = 0;

    /**
     * Indicates when new Broadcast SMS is received
     *
     * @param type Type of radio indication
     * @param data If received from GSM network, "data" is byte array of 88 bytes
     *        which indicates each page of a CBS Message sent to the MS by the
     *        BTS as coded in 3GPP 23.041 Section 9.4.1.2.
     *        If received from UMTS network, "data" is byte array of 90 up to 1252
     *        bytes which contain between 1 and 15 CBS Message pages sent as one
     *        packet to the MS by the BTS as coded in 3GPP 23.041 Section 9.4.2.2
     */
    virtual ::android::hardware::Return<void> newBroadcastSms(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<uint8_t>& data) = 0;

    /**
     * Indicates that SMS storage on the RUIM is full. Messages
     * cannot be saved on the RUIM until space is freed.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> cdmaRuimSmsStorageFull(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates a restricted state change (eg, for Domain Specific Access Control).
     * Radio must send this msg after radio off/on cycle no matter it is changed or not.
     *
     * @param type Type of radio indication
     * @param state Bitmask of restricted state as defined by PhoneRestrictedState
     */
    virtual ::android::hardware::Return<void> restrictedStateChanged(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::PhoneRestrictedState state) = 0;

    /**
     * Indicates that the radio system selection module has
     * autonomously entered emergency callback mode.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> enterEmergencyCallbackMode(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when CDMA radio receives a call waiting indication.
     *
     * @param type Type of radio indication
     * @param callWaitingRecord Cdma CallWaiting information
     */
    virtual ::android::hardware::Return<void> cdmaCallWaiting(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::CdmaCallWaiting& callWaitingRecord) = 0;

    /**
     * Indicates when CDMA radio receives an update of the progress of an OTASP/OTAPA call.
     *
     * @param type Type of radio indication
     * @param status Cdma OTA provision status
     */
    virtual ::android::hardware::Return<void> cdmaOtaProvisionStatus(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::CdmaOtaProvisionStatus status) = 0;

    /**
     * Indicates when CDMA radio receives one or more info recs.
     *
     * @param type Type of radio indication
     * @param records New Cdma Information
     */
    virtual ::android::hardware::Return<void> cdmaInfoRec(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::CdmaInformationRecords& records) = 0;

    /**
     * Indicates that nework doesn't have in-band information, need to
     * play out-band tone.
     *
     * @param type Type of radio indication
     * @param start true = start play ringback tone, false = stop playing ringback tone
     */
    virtual ::android::hardware::Return<void> indicateRingbackTone(::android::hardware::radio::V1_0::RadioIndicationType type, bool start) = 0;

    /**
     * Indicates that framework/application must reset the uplink mute state.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> resendIncallMute(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates when CDMA subscription source changed.
     *
     * @param type Type of radio indication
     * @param cdmaSource New Cdma SubscriptionSource
     */
    virtual ::android::hardware::Return<void> cdmaSubscriptionSourceChanged(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::CdmaSubscriptionSource cdmaSource) = 0;

    /**
     * Indicates when PRL (preferred roaming list) changes.
     *
     * @param type Type of radio indication
     * @param version PRL version after PRL changes
     */
    virtual ::android::hardware::Return<void> cdmaPrlChanged(::android::hardware::radio::V1_0::RadioIndicationType type, int32_t version) = 0;

    /**
     * Indicates when Emergency Callback Mode Ends.
     * Indicates that the radio system selection module has
     * proactively exited emergency callback mode.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> exitEmergencyCallbackMode(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates the ril connects and returns the version
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> rilConnected(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicates that voice technology has changed. Responds with new rat.
     *
     * @param type Type of radio indication
     * @param rat Current new voice rat
     */
    virtual ::android::hardware::Return<void> voiceRadioTechChanged(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::RadioTechnology rat) = 0;

    /**
     * Same information as returned by getCellInfoList().
     *
     * @param type Type of radio indication
     * @param records Current cell information known to radio
     */
    virtual ::android::hardware::Return<void> cellInfoList(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CellInfo>& records) = 0;

    /**
     * Indicates when IMS registration state has changed.
     * To get IMS registration state and IMS SMS format, callee needs to invoke
     * getImsRegistrationState()
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> imsNetworkStateChanged(::android::hardware::radio::V1_0::RadioIndicationType type) = 0;

    /**
     * Indicated when there is a change in subscription status.
     * This event must be sent in the following scenarios
     *  - subscription readiness at modem, which was selected by telephony layer
     *  - when subscription is deactivated by modem due to UICC card removal
     *  - when network invalidates the subscription i.e. attach reject due to authentication reject
     *
     * @param type Type of radio indication
     * @param activate false for subscription deactivated, true for subscription activated
     */
    virtual ::android::hardware::Return<void> subscriptionStatusChanged(::android::hardware::radio::V1_0::RadioIndicationType type, bool activate) = 0;

    /**
     * Indicates when Single Radio Voice Call Continuity (SRVCC)
     * progress state has changed
     *
     * @param type Type of radio indication
     * @param state New Srvcc State
     */
    virtual ::android::hardware::Return<void> srvccStateNotify(::android::hardware::radio::V1_0::RadioIndicationType type, ::android::hardware::radio::V1_0::SrvccState state) = 0;

    /**
     * Indicates when the hardware configuration associated with the RILd changes.
     *
     * @param type Type of radio indication
     * @param configs Array of hardware configs
     */
    virtual ::android::hardware::Return<void> hardwareConfigChanged(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::HardwareConfig>& configs) = 0;

    /**
     * Sent when setRadioCapability() completes.
     * Returns the phone radio capability exactly as
     * getRadioCapability() and must be the
     * same set as sent by setRadioCapability().
     *
     * @param type Type of radio indication
     * @param rc Current radio capability
     */
    virtual ::android::hardware::Return<void> radioCapabilityIndication(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::RadioCapability& rc) = 0;

    /**
     * Indicates when Supplementary service(SS) response is received when DIAL/USSD/SS is changed to
     * SS by call control.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> onSupplementaryServiceIndication(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::StkCcUnsolSsResult& ss) = 0;

    /**
     * Indicates when there is an ALPHA from UICC during Call Control.
     *
     * @param type Type of radio indication
     * @param alpha ALPHA string from UICC in UTF-8 format
     */
    virtual ::android::hardware::Return<void> stkCallControlAlphaNotify(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_string& alpha) = 0;

    /**
     * Indicates when there is an incoming Link Capacity Estimate (LCE) info report.
     *
     * @param type Type of radio indication
     * @param lce LceData information
     */
    virtual ::android::hardware::Return<void> lceData(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::LceDataInfo& lce) = 0;

    /**
     * Indicates when there is new Carrier PCO data received for a data call. Ideally
     * only new data must be forwarded, though this is not required. Multiple
     * boxes of carrier PCO data for a given call must result in a series of
     * pcoData() calls.
     *
     * @param type Type of radio indication
     * @param pco New PcoData
     */
    virtual ::android::hardware::Return<void> pcoData(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_0::PcoDataInfo& pco) = 0;

    /**
     * Indicates when there is a modem reset.
     *
     * When modem restarts, one of the following radio state transitions must happen
     * 1) RadioState:ON->RadioState:UNAVAILABLE->RadioState:ON or
     * 2) RadioState:OFF->RadioState:UNAVAILABLE->RadioState:OFF
     * This message must be sent either just before the Radio State changes to RadioState:UNAVAILABLE
     * or just after but must never be sent after the Radio State changes from RadioState:UNAVAILABLE
     * to RadioState:ON/RadioState:OFF again.
     * It must NOT be sent after the Radio state changes to RadioState:ON/RadioState:OFF after the
     * modem restart as that may be interpreted as a second modem reset by the
     * framework.
     *
     * @param type Type of radio indication
     * @param reason the reason for the reset. It
     *        may be a crash signature if the restart was due to a crash or some
     *        string such as "user-initiated restart" or "AT command initiated
     *        restart" that explains the cause of the modem restart
     */
    virtual ::android::hardware::Return<void> modemReset(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_string& reason) = 0;

    /*
     * Indicates that the modem requires the Carrier info for IMSI/IMPI encryption.
     * This might happen when the modem restarts or for some reason it's cache
     * has been invalidated.
     *
     * @param type Type of radio indication
     */
    virtual ::android::hardware::Return<void> carrierInfoForImsiEncryption(::android::hardware::radio::V1_0::RadioIndicationType info) = 0;

    /**
     * Incremental network scan results
     *
     * @param type Type of radio indication
     * @param result Network scan result as NetworkScanResult defined in types.hal
     */
    virtual ::android::hardware::Return<void> networkScanResult(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_1::NetworkScanResult& result) = 0;

    /**
     * Indicates a status update for a particular Keepalive session. This must include
     * a handle for a previous session and should include a status update regarding the
     * state of a keepalive. Unsolicited keepalive status reports should never be
     * PENDING as unsolicited status should only be sent when known.
     *
     * @param type Type of radio indication
     * @param status Status information for a Keepalive session
     */
    virtual ::android::hardware::Return<void> keepaliveStatus(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_1::KeepaliveStatus& status) = 0;

    /**
     * Incremental network scan results
     */
    virtual ::android::hardware::Return<void> networkScanResult_1_2(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_2::NetworkScanResult& result) = 0;

    /**
     * Request all of the current cell information known to the radio.
     * Same information as returned by getCellInfoList() in 1.0::IRadio.
     *
     * @param type Type of radio indication
     * @param records Current cell information
     */
    virtual ::android::hardware::Return<void> cellInfoList_1_2(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_2::CellInfo>& records) = 0;

    /**
     * Indicates current link capacity estimate.
     *
     * This replaces @1.0::IRadioIndication.lceData(). The framework must be able to handle
     * either this function or @1.0::IRadioIndication.lceData(). Implementations supporting
     * v1.2 must call this function instead of lceData().
     *
     * This indication is sent whenever the reporting criteria, as set by
     * @1.2::IRadio.setLinkCapacityReportingCriteria, are met and the indication is not
     * suppressed by @1.2::IRadio.setIndicationFilter_1_2().
     *
     * @param type Type of radio indication
     * @param lce LinkCapacityEstimate
     */
    virtual ::android::hardware::Return<void> currentLinkCapacityEstimate(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_2::LinkCapacityEstimate& lce) = 0;

    /**
     * Indicates physical channel configurations.
     *
     * An empty configs list indicates that the radio is in idle mode.
     *
     * @param type Type of radio indication
     * @param configs Vector of PhysicalChannelConfigs
     */
    virtual ::android::hardware::Return<void> currentPhysicalChannelConfigs(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_2::PhysicalChannelConfig>& configs) = 0;

    /**
     * Indicates current signal strength of the radio.
     *
     * @param type Type of radio indication
     * @param signalStrength SignalStrength information
     */
    virtual ::android::hardware::Return<void> currentSignalStrength_1_2(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_2::SignalStrength& signalStrength) = 0;

    /**
     * Report the current list of emergency numbers
     *
     * Each emergency number (@1.4::EmergencyNumber) in the emergency number list contains a
     * dialing number, zero or more service category(s), zero or more emergency uniform resource
     * names, mobile country code, mobile network code, and source(s) that indicate where it comes
     * from.
     *
     * Radio must report all the valid emergency numbers with known mobile country code, mobile
     * network code, emergency service categories, and emergency uniform resource names from all
     * available sources including network signaling, sim, modem/oem configuration, and default
     * configuration (112 and 911 must be always available; additionally, 000, 08, 110, 999, 118
     * and 119 must be available when sim is not present). Radio shall not report emergency numbers
     * that are invalid in the current locale. The reported emergency number list must not have
     * duplicate @1.4::EmergencyNumber entries. Please refer the documentation of
     * @1.4::EmergencyNumber to construct each emergency number to report.
     *
     * Radio must report the complete list of emergency numbers whenever the emergency numbers in
     * the list are changed or whenever the client and the radio server are connected.
     *
     * Reference: 3gpp 22.101, Section 10 - Emergency Calls;
     *            3gpp 24.008, Section 9.2.13.4 - Emergency Number List
     *
     * @param type Type of radio indication
     * @param emergencyNumberList Current list of emergency numbers known to radio.
     */
    virtual ::android::hardware::Return<void> currentEmergencyNumberList(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_4::EmergencyNumber>& emergencyNumberList) = 0;

    /**
     * Report all of the current cell information known to the radio.
     *
     * @param type Type of radio indication
     * @param records Current cell information
     */
    virtual ::android::hardware::Return<void> cellInfoList_1_4(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_4::CellInfo>& records) = 0;

    /**
     * Incremental network scan results
     */
    virtual ::android::hardware::Return<void> networkScanResult_1_4(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_4::NetworkScanResult& result) = 0;

    /**
     * Indicates physical channel configurations.
     *
     * An empty configs list indicates that the radio is in idle mode.
     *
     * @param type Type of radio indication
     * @param configs Vector of PhysicalChannelConfigs
     */
    virtual ::android::hardware::Return<void> currentPhysicalChannelConfigs_1_4(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_4::PhysicalChannelConfig>& configs) = 0;

    /**
     * Indicates data call contexts have changed.
     *
     * @param type Type of radio indication
     * @param dcList Array of SetupDataCallResult identical to that returned by
     *        IRadio.getDataCallList(). It is the complete list of current data contexts including
     *        new contexts that have been activated. A data call is only removed from this list
     *        when below conditions matched.
     *        1. The framework sends a IRadio.deactivateDataCall().
     *        2. The radio is powered off/on.
     *        3. Unsolicited disconnect from either modem or network side.
     */
    virtual ::android::hardware::Return<void> dataCallListChanged_1_4(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_4::SetupDataCallResult>& dcList) = 0;

    /**
     * Indicates current signal strength of the radio.
     *
     * @param type Type of radio indication
     * @param signalStrength SignalStrength information
     */
    virtual ::android::hardware::Return<void> currentSignalStrength_1_4(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_4::SignalStrength& signalStrength) = 0;

    /**
     * Report change of whether uiccApplications are enabled, or disabled.
     *
     * @param type Type of radio indication
     * @param enabled whether uiccApplications are enabled, or disabled
     */
    virtual ::android::hardware::Return<void> uiccApplicationsEnablementChanged(::android::hardware::radio::V1_0::RadioIndicationType type, bool enabled) = 0;

    /**
     * Report that Registration or a Location/Routing/Tracking Area update has failed.
     *
     * <p>Indicate whenever a registration procedure, including a location, routing, or tracking
     * area update fails. This includes procedures that do not necessarily result in a change of
     * the modem's registration status. If the modem's registration status changes, that is
     * reflected in the onNetworkStateChanged() and subsequent get{Voice/Data}RegistrationState().
     *
     * @param cellIdentity the CellIdentity, which must include the globally unique identifier for
     *        the cell (for example, all components of the CGI or ECGI).
     * @param chosenPlmn a 5 or 6 digit alphanumeric PLMN (MCC|MNC) among those broadcast by the
     *        cell that was chosen for the failed registration attempt.
     * @param domain Domain::CS, Domain::PS, or both in case of a combined procedure.
     * @param causeCode the primary failure cause code of the procedure.
     *        For GSM/UMTS (MM), values are in TS 24.008 Sec 10.5.95
     *        For GSM/UMTS (GMM), values are in TS 24.008 Sec 10.5.147
     *        For LTE (EMM), cause codes are TS 24.301 Sec 9.9.3.9
     *        For NR (5GMM), cause codes are TS 24.501 Sec 9.11.3.2
     *        MAX_INT if this value is unused.
     * @param additionalCauseCode the cause code of any secondary/combined procedure if appropriate.
     *        For UMTS, if a combined attach succeeds for PS only, then the GMM cause code shall be
     *        included as an additionalCauseCode.
     *        For LTE (ESM), cause codes are in TS 24.301 9.9.4.4
     *        MAX_INT if this value is unused.
     */
    virtual ::android::hardware::Return<void> registrationFailed(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_5::CellIdentity& cellIdentity, const ::android::hardware::hidl_string& chosenPlmn, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::Domain> domain, int32_t causeCode, int32_t additionalCauseCode) = 0;

    /**
     * Indicate barring information for the user’s access category / access class and PLMN.
     *
     * <p>Provide information about the barring status of the cell for the user. The information
     * provided should describe all barring configurations that are applicable to the current user,
     * even if the user is not currently barred (due to conditional barring). This informs Android
     * of likely future (statistical) barring for specific services.
     *
     * <p>This indication should be sent whenever the cell’s barring config changes for the current
     * user, or if the user’s conditional barring status changes due to re-evaluation of the
     * barring conditions. Barring status will likely change when the device camps for service,
     * when PLMN selection is completed, when the device attempts to access a conditionally barred
     * service, and when the System Information including barring info for a camped cell is updated.
     */
    virtual ::android::hardware::Return<void> barringInfoChanged(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_5::CellIdentity& cellIdentity, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::BarringInfo>& barringInfos) = 0;

    /**
     * Report all of the current cell information known to the radio.
     *
     * This indication is updated from IRadioIndication@1.4 to report the @1.5 version of
     * CellInfo.
     *
     * @param type Type of radio indication
     * @param records Current cell information
     */
    virtual ::android::hardware::Return<void> cellInfoList_1_5(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::CellInfo>& records) = 0;

    /**
     * Incremental network scan results.
     *
     * This indication is updated from IRadioIndication@1.4 to report the @1.5 version of
     * CellInfo.
     */
    virtual ::android::hardware::Return<void> networkScanResult_1_5(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::radio::V1_5::NetworkScanResult& result) = 0;

    /**
     * Indicates data call contexts have changed.
     *
     * This indication is updated from IRadioIndication@1.4 to report the @1.5 version of
     * SetupDataCallResult.
     *
     * @param type Type of radio indication
     * @param dcList Array of SetupDataCallResult identical to that returned by
     *        IRadio.getDataCallList(). It is the complete list of current data contexts including
     *        new contexts that have been activated. A data call is only removed from this list
     *        when below conditions matched.
     *        1. The framework sends a IRadio.deactivateDataCall().
     *        2. The radio is powered off/on.
     *        3. Unsolicited disconnect from either modem or network side.
     */
    virtual ::android::hardware::Return<void> dataCallListChanged_1_5(::android::hardware::radio::V1_0::RadioIndicationType type, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::SetupDataCallResult>& dcList) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_5::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_4::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_3::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_2::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_1::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hardware::radio::V1_0::IRadioIndication>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::radio::V1_5::IRadioIndication>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IRadioIndication> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IRadioIndication> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IRadioIndication> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IRadioIndication> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IRadioIndication> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IRadioIndication> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IRadioIndication> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IRadioIndication> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::radio::V1_5::IRadioIndication>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::radio::V1_5::IRadioIndication>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::radio::V1_5::IRadioIndication::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_5
}  // namespace radio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_IRADIOINDICATION_H
