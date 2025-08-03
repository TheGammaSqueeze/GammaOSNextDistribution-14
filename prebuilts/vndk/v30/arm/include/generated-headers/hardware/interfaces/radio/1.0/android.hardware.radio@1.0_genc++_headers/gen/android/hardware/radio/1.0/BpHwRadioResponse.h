#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIORESPONSE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIORESPONSE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.0/IHwRadioResponse.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_0 {

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

    // Methods from ::android::hardware::radio::V1_0::IRadioResponse follow.
    static ::android::hardware::Return<void>  _hidl_getIccCardStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::CardStatus& cardStatus);
    static ::android::hardware::Return<void>  _hidl_supplyIccPinForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_supplyIccPukForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_supplyIccPin2ForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_supplyIccPuk2ForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_changeIccPinForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_changeIccPin2ForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_supplyNetworkDepersonalizationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t remainingRetries);
    static ::android::hardware::Return<void>  _hidl_getCurrentCallsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::Call>& calls);
    static ::android::hardware::Return<void>  _hidl_dialResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getIMSIForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& imsi);
    static ::android::hardware::Return<void>  _hidl_hangupConnectionResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_hangupWaitingOrBackgroundResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_hangupForegroundResumeBackgroundResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_switchWaitingOrHoldingAndActiveResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_conferenceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_rejectCallResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getLastCallFailCauseResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LastCallFailCauseInfo& failCauseinfo);
    static ::android::hardware::Return<void>  _hidl_getSignalStrengthResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SignalStrength& sigStrength);
    static ::android::hardware::Return<void>  _hidl_getVoiceRegistrationStateResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::VoiceRegStateResult& voiceRegResponse);
    static ::android::hardware::Return<void>  _hidl_getDataRegistrationStateResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::DataRegStateResult& dataRegResponse);
    static ::android::hardware::Return<void>  _hidl_getOperatorResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& longName, const ::android::hardware::hidl_string& shortName, const ::android::hardware::hidl_string& numeric);
    static ::android::hardware::Return<void>  _hidl_setRadioPowerResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_sendDtmfResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_sendSmsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms);
    static ::android::hardware::Return<void>  _hidl_sendSMSExpectMoreResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms);
    static ::android::hardware::Return<void>  _hidl_setupDataCallResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SetupDataCallResult& dcResponse);
    static ::android::hardware::Return<void>  _hidl_iccIOForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& iccIo);
    static ::android::hardware::Return<void>  _hidl_sendUssdResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_cancelPendingUssdResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getClirResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t n, int32_t m);
    static ::android::hardware::Return<void>  _hidl_setClirResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCallForwardStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CallForwardInfo>& callForwardInfos);
    static ::android::hardware::Return<void>  _hidl_setCallForwardResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCallWaitingResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable, int32_t serviceClass);
    static ::android::hardware::Return<void>  _hidl_setCallWaitingResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_acknowledgeLastIncomingGsmSmsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_acceptCallResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_deactivateDataCallResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getFacilityLockForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t response);
    static ::android::hardware::Return<void>  _hidl_setFacilityLockForAppResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t retry);
    static ::android::hardware::Return<void>  _hidl_setBarringPasswordResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getNetworkSelectionModeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool manual);
    static ::android::hardware::Return<void>  _hidl_setNetworkSelectionModeAutomaticResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setNetworkSelectionModeManualResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getAvailableNetworksResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::OperatorInfo>& networkInfos);
    static ::android::hardware::Return<void>  _hidl_startDtmfResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_stopDtmfResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getBasebandVersionResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& version);
    static ::android::hardware::Return<void>  _hidl_separateConnectionResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setMuteResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getMuteResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable);
    static ::android::hardware::Return<void>  _hidl_getClipResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::ClipStatus status);
    static ::android::hardware::Return<void>  _hidl_getDataCallListResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::SetupDataCallResult>& dcResponse);
    static ::android::hardware::Return<void>  _hidl_setSuppServiceNotificationsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_writeSmsToSimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t index);
    static ::android::hardware::Return<void>  _hidl_deleteSmsOnSimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setBandModeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getAvailableBandModesResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::RadioBandMode>& bandModes);
    static ::android::hardware::Return<void>  _hidl_sendEnvelopeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& commandResponse);
    static ::android::hardware::Return<void>  _hidl_sendTerminalResponseToSimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_handleStkCallSetupRequestFromSimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_explicitCallTransferResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setPreferredNetworkTypeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getPreferredNetworkTypeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::PreferredNetworkType nwType);
    static ::android::hardware::Return<void>  _hidl_getNeighboringCidsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::NeighboringCell>& cells);
    static ::android::hardware::Return<void>  _hidl_setLocationUpdatesResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setCdmaSubscriptionSourceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setCdmaRoamingPreferenceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCdmaRoamingPreferenceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::CdmaRoamingType type);
    static ::android::hardware::Return<void>  _hidl_setTTYModeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getTTYModeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::TtyMode mode);
    static ::android::hardware::Return<void>  _hidl_setPreferredVoicePrivacyResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getPreferredVoicePrivacyResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool enable);
    static ::android::hardware::Return<void>  _hidl_sendCDMAFeatureCodeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_sendBurstDtmfResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_sendCdmaSmsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms);
    static ::android::hardware::Return<void>  _hidl_acknowledgeLastIncomingCdmaSmsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getGsmBroadcastConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::GsmBroadcastSmsConfigInfo>& configs);
    static ::android::hardware::Return<void>  _hidl_setGsmBroadcastConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setGsmBroadcastActivationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCdmaBroadcastConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CdmaBroadcastSmsConfigInfo>& configs);
    static ::android::hardware::Return<void>  _hidl_setCdmaBroadcastConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setCdmaBroadcastActivationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCDMASubscriptionResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& mdn, const ::android::hardware::hidl_string& hSid, const ::android::hardware::hidl_string& hNid, const ::android::hardware::hidl_string& min, const ::android::hardware::hidl_string& prl);
    static ::android::hardware::Return<void>  _hidl_writeSmsToRuimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, uint32_t index);
    static ::android::hardware::Return<void>  _hidl_deleteSmsOnRuimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getDeviceIdentityResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& imei, const ::android::hardware::hidl_string& imeisv, const ::android::hardware::hidl_string& esn, const ::android::hardware::hidl_string& meid);
    static ::android::hardware::Return<void>  _hidl_exitEmergencyCallbackModeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getSmscAddressResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& smsc);
    static ::android::hardware::Return<void>  _hidl_setSmscAddressResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_reportSmsMemoryStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_reportStkServiceIsRunningResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getCdmaSubscriptionSourceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::CdmaSubscriptionSource source);
    static ::android::hardware::Return<void>  _hidl_requestIsimAuthenticationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& response);
    static ::android::hardware::Return<void>  _hidl_acknowledgeIncomingGsmSmsWithPduResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_sendEnvelopeWithStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& iccIo);
    static ::android::hardware::Return<void>  _hidl_getVoiceRadioTechnologyResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, ::android::hardware::radio::V1_0::RadioTechnology rat);
    static ::android::hardware::Return<void>  _hidl_getCellInfoListResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CellInfo>& cellInfo);
    static ::android::hardware::Return<void>  _hidl_setCellInfoListRateResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setInitialAttachApnResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getImsRegistrationStateResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool isRegistered, ::android::hardware::radio::V1_0::RadioTechnologyFamily ratFamily);
    static ::android::hardware::Return<void>  _hidl_sendImsSmsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::SendSmsResult& sms);
    static ::android::hardware::Return<void>  _hidl_iccTransmitApduBasicChannelResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result);
    static ::android::hardware::Return<void>  _hidl_iccOpenLogicalChannelResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t channelId, const ::android::hardware::hidl_vec<int8_t>& selectResponse);
    static ::android::hardware::Return<void>  _hidl_iccCloseLogicalChannelResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_iccTransmitApduLogicalChannelResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result);
    static ::android::hardware::Return<void>  _hidl_nvReadItemResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_string& result);
    static ::android::hardware::Return<void>  _hidl_nvWriteItemResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_nvWriteCdmaPrlResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_nvResetConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setUiccSubscriptionResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setDataAllowedResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getHardwareConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::HardwareConfig>& config);
    static ::android::hardware::Return<void>  _hidl_requestIccSimAuthenticationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::IccIoResult& result);
    static ::android::hardware::Return<void>  _hidl_setDataProfileResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_requestShutdownResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_getRadioCapabilityResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::RadioCapability& rc);
    static ::android::hardware::Return<void>  _hidl_setRadioCapabilityResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::RadioCapability& rc);
    static ::android::hardware::Return<void>  _hidl_startLceServiceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceStatusInfo& statusInfo);
    static ::android::hardware::Return<void>  _hidl_stopLceServiceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceStatusInfo& statusInfo);
    static ::android::hardware::Return<void>  _hidl_pullLceDataResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::LceDataInfo& lceInfo);
    static ::android::hardware::Return<void>  _hidl_getModemActivityInfoResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, const ::android::hardware::radio::V1_0::ActivityStatsInfo& activityInfo);
    static ::android::hardware::Return<void>  _hidl_setAllowedCarriersResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, int32_t numAllowed);
    static ::android::hardware::Return<void>  _hidl_getAllowedCarriersResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info, bool allAllowed, const ::android::hardware::radio::V1_0::CarrierRestrictions& carriers);
    static ::android::hardware::Return<void>  _hidl_sendDeviceStateResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setIndicationFilterResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_setSimCardPowerResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::radio::V1_0::RadioResponseInfo& info);
    static ::android::hardware::Return<void>  _hidl_acknowledgeRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);

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

}  // namespace V1_0
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIORESPONSE_H
