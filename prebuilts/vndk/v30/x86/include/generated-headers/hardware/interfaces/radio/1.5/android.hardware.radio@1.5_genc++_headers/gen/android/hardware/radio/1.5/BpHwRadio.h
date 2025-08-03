#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_BPHWRADIO_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_BPHWRADIO_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.5/IHwRadio.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_5 {

struct BpHwRadio : public ::android::hardware::BpInterface<IRadio>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwRadio(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IRadio Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::V1_5::IRadio follow.
    static ::android::hardware::Return<void>  _hidl_setSignalStrengthReportingCriteria_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_5::SignalThresholdInfo& signalThresholdInfo, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork);
    static ::android::hardware::Return<void>  _hidl_setLinkCapacityReportingCriteria_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t hysteresisMs, int32_t hysteresisDlKbps, int32_t hysteresisUlKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsDownlinkKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsUplinkKbps, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork);
    static ::android::hardware::Return<void>  _hidl_enableUiccApplications(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable);
    static ::android::hardware::Return<void>  _hidl_areUiccApplicationsEnabled(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setSystemSelectionChannels_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool specifyChannels, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::RadioAccessSpecifier>& specifiers);
    static ::android::hardware::Return<void>  _hidl_startNetworkScan_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_5::NetworkScanRequest& request);
    static ::android::hardware::Return<void>  _hidl_setupDataCall_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork, const ::android::hardware::radio::V1_5::DataProfileInfo& dataProfileInfo, bool roamingAllowed, ::android::hardware::radio::V1_2::DataRequestReason reason, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::LinkAddress>& addresses, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& dnses);
    static ::android::hardware::Return<void>  _hidl_setInitialAttachApn_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_5::DataProfileInfo& dataProfileInfo);
    static ::android::hardware::Return<void>  _hidl_setDataProfile_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::DataProfileInfo>& profiles);
    static ::android::hardware::Return<void>  _hidl_setRadioPower_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool powerOn, bool forEmergencyCall, bool preferredForEmergencyCall);
    static ::android::hardware::Return<void>  _hidl_setIndicationFilter_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::IndicationFilter> indicationFilter);
    static ::android::hardware::Return<void>  _hidl_getBarringInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getVoiceRegistrationState_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getDataRegistrationState_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setNetworkSelectionModeManual_1_5(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& operatorNumeric, ::android::hardware::radio::V1_5::RadioAccessNetworks ran);
    static ::android::hardware::Return<void>  _hidl_sendCdmaSmsExpectMore(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsMessage& sms);
    static ::android::hardware::Return<void>  _hidl_supplySimDepersonalization(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_5::PersoSubstate persoType, const ::android::hardware::hidl_string& controlKey);

    // Methods from ::android::hardware::radio::V1_0::IRadio follow.
    ::android::hardware::Return<void> setResponseFunctions(const ::android::sp<::android::hardware::radio::V1_0::IRadioResponse>& radioResponse, const ::android::sp<::android::hardware::radio::V1_0::IRadioIndication>& radioIndication) override;
    ::android::hardware::Return<void> getIccCardStatus(int32_t serial) override;
    ::android::hardware::Return<void> supplyIccPinForApp(int32_t serial, const ::android::hardware::hidl_string& pin, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> supplyIccPukForApp(int32_t serial, const ::android::hardware::hidl_string& puk, const ::android::hardware::hidl_string& pin, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> supplyIccPin2ForApp(int32_t serial, const ::android::hardware::hidl_string& pin2, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> supplyIccPuk2ForApp(int32_t serial, const ::android::hardware::hidl_string& puk2, const ::android::hardware::hidl_string& pin2, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> changeIccPinForApp(int32_t serial, const ::android::hardware::hidl_string& oldPin, const ::android::hardware::hidl_string& newPin, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> changeIccPin2ForApp(int32_t serial, const ::android::hardware::hidl_string& oldPin2, const ::android::hardware::hidl_string& newPin2, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> supplyNetworkDepersonalization(int32_t serial, const ::android::hardware::hidl_string& netPin) override;
    ::android::hardware::Return<void> getCurrentCalls(int32_t serial) override;
    ::android::hardware::Return<void> dial(int32_t serial, const ::android::hardware::radio::V1_0::Dial& dialInfo) override;
    ::android::hardware::Return<void> getImsiForApp(int32_t serial, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> hangup(int32_t serial, int32_t gsmIndex) override;
    ::android::hardware::Return<void> hangupWaitingOrBackground(int32_t serial) override;
    ::android::hardware::Return<void> hangupForegroundResumeBackground(int32_t serial) override;
    ::android::hardware::Return<void> switchWaitingOrHoldingAndActive(int32_t serial) override;
    ::android::hardware::Return<void> conference(int32_t serial) override;
    ::android::hardware::Return<void> rejectCall(int32_t serial) override;
    ::android::hardware::Return<void> getLastCallFailCause(int32_t serial) override;
    ::android::hardware::Return<void> getSignalStrength(int32_t serial) override;
    ::android::hardware::Return<void> getVoiceRegistrationState(int32_t serial) override;
    ::android::hardware::Return<void> getDataRegistrationState(int32_t serial) override;
    ::android::hardware::Return<void> getOperator(int32_t serial) override;
    ::android::hardware::Return<void> setRadioPower(int32_t serial, bool on) override;
    ::android::hardware::Return<void> sendDtmf(int32_t serial, const ::android::hardware::hidl_string& s) override;
    ::android::hardware::Return<void> sendSms(int32_t serial, const ::android::hardware::radio::V1_0::GsmSmsMessage& message) override;
    ::android::hardware::Return<void> sendSMSExpectMore(int32_t serial, const ::android::hardware::radio::V1_0::GsmSmsMessage& message) override;
    ::android::hardware::Return<void> setupDataCall(int32_t serial, ::android::hardware::radio::V1_0::RadioTechnology radioTechnology, const ::android::hardware::radio::V1_0::DataProfileInfo& dataProfileInfo, bool modemCognitive, bool roamingAllowed, bool isRoaming) override;
    ::android::hardware::Return<void> iccIOForApp(int32_t serial, const ::android::hardware::radio::V1_0::IccIo& iccIo) override;
    ::android::hardware::Return<void> sendUssd(int32_t serial, const ::android::hardware::hidl_string& ussd) override;
    ::android::hardware::Return<void> cancelPendingUssd(int32_t serial) override;
    ::android::hardware::Return<void> getClir(int32_t serial) override;
    ::android::hardware::Return<void> setClir(int32_t serial, int32_t status) override;
    ::android::hardware::Return<void> getCallForwardStatus(int32_t serial, const ::android::hardware::radio::V1_0::CallForwardInfo& callInfo) override;
    ::android::hardware::Return<void> setCallForward(int32_t serial, const ::android::hardware::radio::V1_0::CallForwardInfo& callInfo) override;
    ::android::hardware::Return<void> getCallWaiting(int32_t serial, int32_t serviceClass) override;
    ::android::hardware::Return<void> setCallWaiting(int32_t serial, bool enable, int32_t serviceClass) override;
    ::android::hardware::Return<void> acknowledgeLastIncomingGsmSms(int32_t serial, bool success, ::android::hardware::radio::V1_0::SmsAcknowledgeFailCause cause) override;
    ::android::hardware::Return<void> acceptCall(int32_t serial) override;
    ::android::hardware::Return<void> deactivateDataCall(int32_t serial, int32_t cid, bool reasonRadioShutDown) override;
    ::android::hardware::Return<void> getFacilityLockForApp(int32_t serial, const ::android::hardware::hidl_string& facility, const ::android::hardware::hidl_string& password, int32_t serviceClass, const ::android::hardware::hidl_string& appId) override;
    ::android::hardware::Return<void> setFacilityLockForApp(int32_t serial, const ::android::hardware::hidl_string& facility, bool lockState, const ::android::hardware::hidl_string& password, int32_t serviceClass, const ::android::hardware::hidl_string& appId) override;
    ::android::hardware::Return<void> setBarringPassword(int32_t serial, const ::android::hardware::hidl_string& facility, const ::android::hardware::hidl_string& oldPassword, const ::android::hardware::hidl_string& newPassword) override;
    ::android::hardware::Return<void> getNetworkSelectionMode(int32_t serial) override;
    ::android::hardware::Return<void> setNetworkSelectionModeAutomatic(int32_t serial) override;
    ::android::hardware::Return<void> setNetworkSelectionModeManual(int32_t serial, const ::android::hardware::hidl_string& operatorNumeric) override;
    ::android::hardware::Return<void> getAvailableNetworks(int32_t serial) override;
    ::android::hardware::Return<void> startDtmf(int32_t serial, const ::android::hardware::hidl_string& s) override;
    ::android::hardware::Return<void> stopDtmf(int32_t serial) override;
    ::android::hardware::Return<void> getBasebandVersion(int32_t serial) override;
    ::android::hardware::Return<void> separateConnection(int32_t serial, int32_t gsmIndex) override;
    ::android::hardware::Return<void> setMute(int32_t serial, bool enable) override;
    ::android::hardware::Return<void> getMute(int32_t serial) override;
    ::android::hardware::Return<void> getClip(int32_t serial) override;
    ::android::hardware::Return<void> getDataCallList(int32_t serial) override;
    ::android::hardware::Return<void> setSuppServiceNotifications(int32_t serial, bool enable) override;
    ::android::hardware::Return<void> writeSmsToSim(int32_t serial, const ::android::hardware::radio::V1_0::SmsWriteArgs& smsWriteArgs) override;
    ::android::hardware::Return<void> deleteSmsOnSim(int32_t serial, int32_t index) override;
    ::android::hardware::Return<void> setBandMode(int32_t serial, ::android::hardware::radio::V1_0::RadioBandMode mode) override;
    ::android::hardware::Return<void> getAvailableBandModes(int32_t serial) override;
    ::android::hardware::Return<void> sendEnvelope(int32_t serial, const ::android::hardware::hidl_string& command) override;
    ::android::hardware::Return<void> sendTerminalResponseToSim(int32_t serial, const ::android::hardware::hidl_string& commandResponse) override;
    ::android::hardware::Return<void> handleStkCallSetupRequestFromSim(int32_t serial, bool accept) override;
    ::android::hardware::Return<void> explicitCallTransfer(int32_t serial) override;
    ::android::hardware::Return<void> setPreferredNetworkType(int32_t serial, ::android::hardware::radio::V1_0::PreferredNetworkType nwType) override;
    ::android::hardware::Return<void> getPreferredNetworkType(int32_t serial) override;
    ::android::hardware::Return<void> getNeighboringCids(int32_t serial) override;
    ::android::hardware::Return<void> setLocationUpdates(int32_t serial, bool enable) override;
    ::android::hardware::Return<void> setCdmaSubscriptionSource(int32_t serial, ::android::hardware::radio::V1_0::CdmaSubscriptionSource cdmaSub) override;
    ::android::hardware::Return<void> setCdmaRoamingPreference(int32_t serial, ::android::hardware::radio::V1_0::CdmaRoamingType type) override;
    ::android::hardware::Return<void> getCdmaRoamingPreference(int32_t serial) override;
    ::android::hardware::Return<void> setTTYMode(int32_t serial, ::android::hardware::radio::V1_0::TtyMode mode) override;
    ::android::hardware::Return<void> getTTYMode(int32_t serial) override;
    ::android::hardware::Return<void> setPreferredVoicePrivacy(int32_t serial, bool enable) override;
    ::android::hardware::Return<void> getPreferredVoicePrivacy(int32_t serial) override;
    ::android::hardware::Return<void> sendCDMAFeatureCode(int32_t serial, const ::android::hardware::hidl_string& featureCode) override;
    ::android::hardware::Return<void> sendBurstDtmf(int32_t serial, const ::android::hardware::hidl_string& dtmf, int32_t on, int32_t off) override;
    ::android::hardware::Return<void> sendCdmaSms(int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsMessage& sms) override;
    ::android::hardware::Return<void> acknowledgeLastIncomingCdmaSms(int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsAck& smsAck) override;
    ::android::hardware::Return<void> getGsmBroadcastConfig(int32_t serial) override;
    ::android::hardware::Return<void> setGsmBroadcastConfig(int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::GsmBroadcastSmsConfigInfo>& configInfo) override;
    ::android::hardware::Return<void> setGsmBroadcastActivation(int32_t serial, bool activate) override;
    ::android::hardware::Return<void> getCdmaBroadcastConfig(int32_t serial) override;
    ::android::hardware::Return<void> setCdmaBroadcastConfig(int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CdmaBroadcastSmsConfigInfo>& configInfo) override;
    ::android::hardware::Return<void> setCdmaBroadcastActivation(int32_t serial, bool activate) override;
    ::android::hardware::Return<void> getCDMASubscription(int32_t serial) override;
    ::android::hardware::Return<void> writeSmsToRuim(int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsWriteArgs& cdmaSms) override;
    ::android::hardware::Return<void> deleteSmsOnRuim(int32_t serial, int32_t index) override;
    ::android::hardware::Return<void> getDeviceIdentity(int32_t serial) override;
    ::android::hardware::Return<void> exitEmergencyCallbackMode(int32_t serial) override;
    ::android::hardware::Return<void> getSmscAddress(int32_t serial) override;
    ::android::hardware::Return<void> setSmscAddress(int32_t serial, const ::android::hardware::hidl_string& smsc) override;
    ::android::hardware::Return<void> reportSmsMemoryStatus(int32_t serial, bool available) override;
    ::android::hardware::Return<void> reportStkServiceIsRunning(int32_t serial) override;
    ::android::hardware::Return<void> getCdmaSubscriptionSource(int32_t serial) override;
    ::android::hardware::Return<void> requestIsimAuthentication(int32_t serial, const ::android::hardware::hidl_string& challenge) override;
    ::android::hardware::Return<void> acknowledgeIncomingGsmSmsWithPdu(int32_t serial, bool success, const ::android::hardware::hidl_string& ackPdu) override;
    ::android::hardware::Return<void> sendEnvelopeWithStatus(int32_t serial, const ::android::hardware::hidl_string& contents) override;
    ::android::hardware::Return<void> getVoiceRadioTechnology(int32_t serial) override;
    ::android::hardware::Return<void> getCellInfoList(int32_t serial) override;
    ::android::hardware::Return<void> setCellInfoListRate(int32_t serial, int32_t rate) override;
    ::android::hardware::Return<void> setInitialAttachApn(int32_t serial, const ::android::hardware::radio::V1_0::DataProfileInfo& dataProfileInfo, bool modemCognitive, bool isRoaming) override;
    ::android::hardware::Return<void> getImsRegistrationState(int32_t serial) override;
    ::android::hardware::Return<void> sendImsSms(int32_t serial, const ::android::hardware::radio::V1_0::ImsSmsMessage& message) override;
    ::android::hardware::Return<void> iccTransmitApduBasicChannel(int32_t serial, const ::android::hardware::radio::V1_0::SimApdu& message) override;
    ::android::hardware::Return<void> iccOpenLogicalChannel(int32_t serial, const ::android::hardware::hidl_string& aid, int32_t p2) override;
    ::android::hardware::Return<void> iccCloseLogicalChannel(int32_t serial, int32_t channelId) override;
    ::android::hardware::Return<void> iccTransmitApduLogicalChannel(int32_t serial, const ::android::hardware::radio::V1_0::SimApdu& message) override;
    ::android::hardware::Return<void> nvReadItem(int32_t serial, ::android::hardware::radio::V1_0::NvItem itemId) override;
    ::android::hardware::Return<void> nvWriteItem(int32_t serial, const ::android::hardware::radio::V1_0::NvWriteItem& item) override;
    ::android::hardware::Return<void> nvWriteCdmaPrl(int32_t serial, const ::android::hardware::hidl_vec<uint8_t>& prl) override;
    ::android::hardware::Return<void> nvResetConfig(int32_t serial, ::android::hardware::radio::V1_0::ResetNvType resetType) override;
    ::android::hardware::Return<void> setUiccSubscription(int32_t serial, const ::android::hardware::radio::V1_0::SelectUiccSub& uiccSub) override;
    ::android::hardware::Return<void> setDataAllowed(int32_t serial, bool allow) override;
    ::android::hardware::Return<void> getHardwareConfig(int32_t serial) override;
    ::android::hardware::Return<void> requestIccSimAuthentication(int32_t serial, int32_t authContext, const ::android::hardware::hidl_string& authData, const ::android::hardware::hidl_string& aid) override;
    ::android::hardware::Return<void> setDataProfile(int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::DataProfileInfo>& profiles, bool isRoaming) override;
    ::android::hardware::Return<void> requestShutdown(int32_t serial) override;
    ::android::hardware::Return<void> getRadioCapability(int32_t serial) override;
    ::android::hardware::Return<void> setRadioCapability(int32_t serial, const ::android::hardware::radio::V1_0::RadioCapability& rc) override;
    ::android::hardware::Return<void> startLceService(int32_t serial, int32_t reportInterval, bool pullMode) override;
    ::android::hardware::Return<void> stopLceService(int32_t serial) override;
    ::android::hardware::Return<void> pullLceData(int32_t serial) override;
    ::android::hardware::Return<void> getModemActivityInfo(int32_t serial) override;
    ::android::hardware::Return<void> setAllowedCarriers(int32_t serial, bool allAllowed, const ::android::hardware::radio::V1_0::CarrierRestrictions& carriers) override;
    ::android::hardware::Return<void> getAllowedCarriers(int32_t serial) override;
    ::android::hardware::Return<void> sendDeviceState(int32_t serial, ::android::hardware::radio::V1_0::DeviceStateType deviceStateType, bool state) override;
    ::android::hardware::Return<void> setIndicationFilter(int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_0::IndicationFilter> indicationFilter) override;
    ::android::hardware::Return<void> setSimCardPower(int32_t serial, bool powerUp) override;
    ::android::hardware::Return<void> responseAcknowledgement() override;

    // Methods from ::android::hardware::radio::V1_1::IRadio follow.
    ::android::hardware::Return<void> setCarrierInfoForImsiEncryption(int32_t serial, const ::android::hardware::radio::V1_1::ImsiEncryptionInfo& imsiEncryptionInfo) override;
    ::android::hardware::Return<void> setSimCardPower_1_1(int32_t serial, ::android::hardware::radio::V1_1::CardPowerState powerUp) override;
    ::android::hardware::Return<void> startNetworkScan(int32_t serial, const ::android::hardware::radio::V1_1::NetworkScanRequest& request) override;
    ::android::hardware::Return<void> stopNetworkScan(int32_t serial) override;
    ::android::hardware::Return<void> startKeepalive(int32_t serial, const ::android::hardware::radio::V1_1::KeepaliveRequest& keepalive) override;
    ::android::hardware::Return<void> stopKeepalive(int32_t serial, int32_t sessionHandle) override;

    // Methods from ::android::hardware::radio::V1_2::IRadio follow.
    ::android::hardware::Return<void> startNetworkScan_1_2(int32_t serial, const ::android::hardware::radio::V1_2::NetworkScanRequest& request) override;
    ::android::hardware::Return<void> setIndicationFilter_1_2(int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_2::IndicationFilter> indicationFilter) override;
    ::android::hardware::Return<void> setSignalStrengthReportingCriteria(int32_t serial, int32_t hysteresisMs, int32_t hysteresisDb, const ::android::hardware::hidl_vec<int32_t>& thresholdsDbm, ::android::hardware::radio::V1_2::AccessNetwork accessNetwork) override;
    ::android::hardware::Return<void> setLinkCapacityReportingCriteria(int32_t serial, int32_t hysteresisMs, int32_t hysteresisDlKbps, int32_t hysteresisUlKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsDownlinkKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsUplinkKbps, ::android::hardware::radio::V1_2::AccessNetwork accessNetwork) override;
    ::android::hardware::Return<void> setupDataCall_1_2(int32_t serial, ::android::hardware::radio::V1_2::AccessNetwork accessNetwork, const ::android::hardware::radio::V1_0::DataProfileInfo& dataProfileInfo, bool modemCognitive, bool roamingAllowed, bool isRoaming, ::android::hardware::radio::V1_2::DataRequestReason reason, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& addresses, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& dnses) override;
    ::android::hardware::Return<void> deactivateDataCall_1_2(int32_t serial, int32_t cid, ::android::hardware::radio::V1_2::DataRequestReason reason) override;

    // Methods from ::android::hardware::radio::V1_3::IRadio follow.
    ::android::hardware::Return<void> setSystemSelectionChannels(int32_t serial, bool specifyChannels, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::RadioAccessSpecifier>& specifiers) override;
    ::android::hardware::Return<void> enableModem(int32_t serial, bool on) override;
    ::android::hardware::Return<void> getModemStackStatus(int32_t serial) override;

    // Methods from ::android::hardware::radio::V1_4::IRadio follow.
    ::android::hardware::Return<void> setupDataCall_1_4(int32_t serial, ::android::hardware::radio::V1_4::AccessNetwork accessNetwork, const ::android::hardware::radio::V1_4::DataProfileInfo& dataProfileInfo, bool roamingAllowed, ::android::hardware::radio::V1_2::DataRequestReason reason, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& addresses, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& dnses) override;
    ::android::hardware::Return<void> setInitialAttachApn_1_4(int32_t serial, const ::android::hardware::radio::V1_4::DataProfileInfo& dataProfileInfo) override;
    ::android::hardware::Return<void> setDataProfile_1_4(int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_4::DataProfileInfo>& profiles) override;
    ::android::hardware::Return<void> emergencyDial(int32_t serial, const ::android::hardware::radio::V1_0::Dial& dialInfo, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_4::EmergencyServiceCategory> categories, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& urns, ::android::hardware::radio::V1_4::EmergencyCallRouting routing, bool hasKnownUserIntentEmergency, bool isTesting) override;
    ::android::hardware::Return<void> startNetworkScan_1_4(int32_t serial, const ::android::hardware::radio::V1_2::NetworkScanRequest& request) override;
    ::android::hardware::Return<void> getPreferredNetworkTypeBitmap(int32_t serial) override;
    ::android::hardware::Return<void> setPreferredNetworkTypeBitmap(int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_4::RadioAccessFamily> networkTypeBitmap) override;
    ::android::hardware::Return<void> setAllowedCarriers_1_4(int32_t serial, const ::android::hardware::radio::V1_4::CarrierRestrictionsWithPriority& carriers, ::android::hardware::radio::V1_4::SimLockMultiSimPolicy multiSimPolicy) override;
    ::android::hardware::Return<void> getAllowedCarriers_1_4(int32_t serial) override;
    ::android::hardware::Return<void> getSignalStrength_1_4(int32_t serial) override;

    // Methods from ::android::hardware::radio::V1_5::IRadio follow.
    ::android::hardware::Return<void> setSignalStrengthReportingCriteria_1_5(int32_t serial, const ::android::hardware::radio::V1_5::SignalThresholdInfo& signalThresholdInfo, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork) override;
    ::android::hardware::Return<void> setLinkCapacityReportingCriteria_1_5(int32_t serial, int32_t hysteresisMs, int32_t hysteresisDlKbps, int32_t hysteresisUlKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsDownlinkKbps, const ::android::hardware::hidl_vec<int32_t>& thresholdsUplinkKbps, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork) override;
    ::android::hardware::Return<void> enableUiccApplications(int32_t serial, bool enable) override;
    ::android::hardware::Return<void> areUiccApplicationsEnabled(int32_t serial) override;
    ::android::hardware::Return<void> setSystemSelectionChannels_1_5(int32_t serial, bool specifyChannels, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::RadioAccessSpecifier>& specifiers) override;
    ::android::hardware::Return<void> startNetworkScan_1_5(int32_t serial, const ::android::hardware::radio::V1_5::NetworkScanRequest& request) override;
    ::android::hardware::Return<void> setupDataCall_1_5(int32_t serial, ::android::hardware::radio::V1_5::AccessNetwork accessNetwork, const ::android::hardware::radio::V1_5::DataProfileInfo& dataProfileInfo, bool roamingAllowed, ::android::hardware::radio::V1_2::DataRequestReason reason, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::LinkAddress>& addresses, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& dnses) override;
    ::android::hardware::Return<void> setInitialAttachApn_1_5(int32_t serial, const ::android::hardware::radio::V1_5::DataProfileInfo& dataProfileInfo) override;
    ::android::hardware::Return<void> setDataProfile_1_5(int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::DataProfileInfo>& profiles) override;
    ::android::hardware::Return<void> setRadioPower_1_5(int32_t serial, bool powerOn, bool forEmergencyCall, bool preferredForEmergencyCall) override;
    ::android::hardware::Return<void> setIndicationFilter_1_5(int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::IndicationFilter> indicationFilter) override;
    ::android::hardware::Return<void> getBarringInfo(int32_t serial) override;
    ::android::hardware::Return<void> getVoiceRegistrationState_1_5(int32_t serial) override;
    ::android::hardware::Return<void> getDataRegistrationState_1_5(int32_t serial) override;
    ::android::hardware::Return<void> setNetworkSelectionModeManual_1_5(int32_t serial, const ::android::hardware::hidl_string& operatorNumeric, ::android::hardware::radio::V1_5::RadioAccessNetworks ran) override;
    ::android::hardware::Return<void> sendCdmaSmsExpectMore(int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsMessage& sms) override;
    ::android::hardware::Return<void> supplySimDepersonalization(int32_t serial, ::android::hardware::radio::V1_5::PersoSubstate persoType, const ::android::hardware::hidl_string& controlKey) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_5
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_BPHWRADIO_H
