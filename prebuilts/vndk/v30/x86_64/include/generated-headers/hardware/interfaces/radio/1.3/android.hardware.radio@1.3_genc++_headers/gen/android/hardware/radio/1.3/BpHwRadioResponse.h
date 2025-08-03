#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BPHWRADIORESPONSE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BPHWRADIORESPONSE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.3/IHwRadioResponse.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_3 {

struct BpHwRadioResponse : public ::android::hardware::BpInterface<IRadioResponse>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwRadioResponse(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioResponse Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::V1_3::IRadioResponse follow.
    static ::android::hardware::Return<void>  _hidl_setSystemSelectionChannelsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_enableModemResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getModemStackStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool isEnabled);

    // Methods from ::android::hardware::radio::V1_0::IRadioResponse follow.
    ::android::hardware::Return<void> getIccCardStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::CardStatus& cardStatus) override;
    ::android::hardware::Return<void> supplyIccPinForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> supplyIccPukForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> supplyIccPin2ForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> supplyIccPuk2ForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> changeIccPinForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> changeIccPin2ForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> supplyNetworkDepersonalizationResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries) override;
    ::android::hardware::Return<void> getCurrentCallsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::Call>& calls) override;
    ::android::hardware::Return<void> dialResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getIMSIForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& imsi) override;
    ::android::hardware::Return<void> hangupConnectionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> hangupWaitingOrBackgroundResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> hangupForegroundResumeBackgroundResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> switchWaitingOrHoldingAndActiveResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> conferenceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> rejectCallResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getLastCallFailCauseResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LastCallFailCauseInfo& failCauseinfo) override;
    ::android::hardware::Return<void> getSignalStrengthResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SignalStrength& sigStrength) override;
    ::android::hardware::Return<void> getVoiceRegistrationStateResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::VoiceRegStateResult& voiceRegResponse) override;
    ::android::hardware::Return<void> getDataRegistrationStateResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::DataRegStateResult& dataRegResponse) override;
    ::android::hardware::Return<void> getOperatorResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& longName, const ::android::hardware::hidl_string& shortName, const ::android::hardware::hidl_string& numeric) override;
    ::android::hardware::Return<void> setRadioPowerResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> sendDtmfResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> sendSmsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms) override;
    ::android::hardware::Return<void> sendSMSExpectMoreResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms) override;
    ::android::hardware::Return<void> setupDataCallResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SetupDataCallResult& dcResponse) override;
    ::android::hardware::Return<void> iccIOForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& iccIo) override;
    ::android::hardware::Return<void> sendUssdResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> cancelPendingUssdResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getClirResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t n, int32_t m) override;
    ::android::hardware::Return<void> setClirResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCallForwardStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CallForwardInfo>& callForwardInfos) override;
    ::android::hardware::Return<void> setCallForwardResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCallWaitingResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable, int32_t serviceClass) override;
    ::android::hardware::Return<void> setCallWaitingResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> acknowledgeLastIncomingGsmSmsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> acceptCallResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> deactivateDataCallResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getFacilityLockForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t response) override;
    ::android::hardware::Return<void> setFacilityLockForAppResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t retry) override;
    ::android::hardware::Return<void> setBarringPasswordResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getNetworkSelectionModeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool manual) override;
    ::android::hardware::Return<void> setNetworkSelectionModeAutomaticResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setNetworkSelectionModeManualResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getAvailableNetworksResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::OperatorInfo>& networkInfos) override;
    ::android::hardware::Return<void> startDtmfResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> stopDtmfResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getBasebandVersionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& version) override;
    ::android::hardware::Return<void> separateConnectionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setMuteResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getMuteResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable) override;
    ::android::hardware::Return<void> getClipResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::ClipStatus status) override;
    ::android::hardware::Return<void> getDataCallListResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::SetupDataCallResult>& dcResponse) override;
    ::android::hardware::Return<void> setSuppServiceNotificationsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> writeSmsToSimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t index) override;
    ::android::hardware::Return<void> deleteSmsOnSimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setBandModeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getAvailableBandModesResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::RadioBandMode>& bandModes) override;
    ::android::hardware::Return<void> sendEnvelopeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& commandResponse) override;
    ::android::hardware::Return<void> sendTerminalResponseToSimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> handleStkCallSetupRequestFromSimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> explicitCallTransferResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setPreferredNetworkTypeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getPreferredNetworkTypeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::PreferredNetworkType nwType) override;
    ::android::hardware::Return<void> getNeighboringCidsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::NeighboringCell>& cells) override;
    ::android::hardware::Return<void> setLocationUpdatesResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setCdmaSubscriptionSourceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setCdmaRoamingPreferenceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCdmaRoamingPreferenceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::CdmaRoamingType type) override;
    ::android::hardware::Return<void> setTTYModeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getTTYModeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::TtyMode mode) override;
    ::android::hardware::Return<void> setPreferredVoicePrivacyResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getPreferredVoicePrivacyResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable) override;
    ::android::hardware::Return<void> sendCDMAFeatureCodeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> sendBurstDtmfResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> sendCdmaSmsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms) override;
    ::android::hardware::Return<void> acknowledgeLastIncomingCdmaSmsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getGsmBroadcastConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::GsmBroadcastSmsConfigInfo>& configs) override;
    ::android::hardware::Return<void> setGsmBroadcastConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setGsmBroadcastActivationResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCdmaBroadcastConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CdmaBroadcastSmsConfigInfo>& configs) override;
    ::android::hardware::Return<void> setCdmaBroadcastConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setCdmaBroadcastActivationResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCDMASubscriptionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& mdn, const ::android::hardware::hidl_string& hSid, const ::android::hardware::hidl_string& hNid, const ::android::hardware::hidl_string& min, const ::android::hardware::hidl_string& prl) override;
    ::android::hardware::Return<void> writeSmsToRuimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, uint32_t index) override;
    ::android::hardware::Return<void> deleteSmsOnRuimResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getDeviceIdentityResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& imei, const ::android::hardware::hidl_string& imeisv, const ::android::hardware::hidl_string& esn, const ::android::hardware::hidl_string& meid) override;
    ::android::hardware::Return<void> exitEmergencyCallbackModeResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getSmscAddressResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& smsc) override;
    ::android::hardware::Return<void> setSmscAddressResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> reportSmsMemoryStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> reportStkServiceIsRunningResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCdmaSubscriptionSourceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::CdmaSubscriptionSource source) override;
    ::android::hardware::Return<void> requestIsimAuthenticationResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& response) override;
    ::android::hardware::Return<void> acknowledgeIncomingGsmSmsWithPduResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> sendEnvelopeWithStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& iccIo) override;
    ::android::hardware::Return<void> getVoiceRadioTechnologyResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::RadioTechnology rat) override;
    ::android::hardware::Return<void> getCellInfoListResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CellInfo>& cellInfo) override;
    ::android::hardware::Return<void> setCellInfoListRateResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setInitialAttachApnResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getImsRegistrationStateResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool isRegistered, ::android::hardware::radio::V1_0::RadioTechnologyFamily ratFamily) override;
    ::android::hardware::Return<void> sendImsSmsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms) override;
    ::android::hardware::Return<void> iccTransmitApduBasicChannelResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result) override;
    ::android::hardware::Return<void> iccOpenLogicalChannelResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t channelId, const ::android::hardware::hidl_vec<int8_t>& selectResponse) override;
    ::android::hardware::Return<void> iccCloseLogicalChannelResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> iccTransmitApduLogicalChannelResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result) override;
    ::android::hardware::Return<void> nvReadItemResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& result) override;
    ::android::hardware::Return<void> nvWriteItemResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> nvWriteCdmaPrlResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> nvResetConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setUiccSubscriptionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setDataAllowedResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getHardwareConfigResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::HardwareConfig>& config) override;
    ::android::hardware::Return<void> requestIccSimAuthenticationResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result) override;
    ::android::hardware::Return<void> setDataProfileResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> requestShutdownResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getRadioCapabilityResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::RadioCapability& rc) override;
    ::android::hardware::Return<void> setRadioCapabilityResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::RadioCapability& rc) override;
    ::android::hardware::Return<void> startLceServiceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceStatusInfo& statusInfo) override;
    ::android::hardware::Return<void> stopLceServiceResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceStatusInfo& statusInfo) override;
    ::android::hardware::Return<void> pullLceDataResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceDataInfo& lceInfo) override;
    ::android::hardware::Return<void> getModemActivityInfoResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::ActivityStatsInfo& activityInfo) override;
    ::android::hardware::Return<void> setAllowedCarriersResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t numAllowed) override;
    ::android::hardware::Return<void> getAllowedCarriersResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool allAllowed, const ::android::hardware::radio::V1_0::CarrierRestrictions& carriers) override;
    ::android::hardware::Return<void> sendDeviceStateResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setIndicationFilterResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setSimCardPowerResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> acknowledgeRequest(int32_t serial) override;

    // Methods from ::android::hardware::radio::V1_1::IRadioResponse follow.
    ::android::hardware::Return<void> setCarrierInfoForImsiEncryptionResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setSimCardPowerResponse_1_1(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> startNetworkScanResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> stopNetworkScanResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> startKeepaliveResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_1::KeepaliveStatus& status) override;
    ::android::hardware::Return<void> stopKeepaliveResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;

    // Methods from ::android::hardware::radio::V1_2::IRadioResponse follow.
    ::android::hardware::Return<void> getCellInfoListResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_2::CellInfo>& cellInfo) override;
    ::android::hardware::Return<void> getIccCardStatusResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_2::CardStatus& cardStatus) override;
    ::android::hardware::Return<void> setSignalStrengthReportingCriteriaResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> setLinkCapacityReportingCriteriaResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getCurrentCallsResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_2::Call>& calls) override;
    ::android::hardware::Return<void> getSignalStrengthResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_2::SignalStrength& signalStrength) override;
    ::android::hardware::Return<void> getVoiceRegistrationStateResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_2::VoiceRegStateResult& voiceRegResponse) override;
    ::android::hardware::Return<void> getDataRegistrationStateResponse_1_2(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_2::DataRegStateResult& dataRegResponse) override;

    // Methods from ::android::hardware::radio::V1_3::IRadioResponse follow.
    ::android::hardware::Return<void> setSystemSelectionChannelsResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> enableModemResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info) override;
    ::android::hardware::Return<void> getModemStackStatusResponse(const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool isEnabled) override;

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

}  // namespace V1_3
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BPHWRADIORESPONSE_H
