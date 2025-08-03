#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIO_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIO_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.0/IHwRadio.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_0 {

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

    // Methods from ::android::hardware::radio::V1_0::IRadio follow.
    static ::android::hardware::Return<void>  _hidl_setResponseFunctions(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::radio::V1_0::IRadioResponse>& radioResponse, const ::android::sp<::android::hardware::radio::V1_0::IRadioIndication>& radioIndication);
    static ::android::hardware::Return<void>  _hidl_getIccCardStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_supplyIccPinForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& pin, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_supplyIccPukForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& puk, const ::android::hardware::hidl_string& pin, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_supplyIccPin2ForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& pin2, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_supplyIccPuk2ForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& puk2, const ::android::hardware::hidl_string& pin2, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_changeIccPinForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& oldPin, const ::android::hardware::hidl_string& newPin, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_changeIccPin2ForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& oldPin2, const ::android::hardware::hidl_string& newPin2, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_supplyNetworkDepersonalization(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& netPin);
    static ::android::hardware::Return<void>  _hidl_getCurrentCalls(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_dial(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::Dial& dialInfo);
    static ::android::hardware::Return<void>  _hidl_getImsiForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_hangup(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t gsmIndex);
    static ::android::hardware::Return<void>  _hidl_hangupWaitingOrBackground(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_hangupForegroundResumeBackground(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_switchWaitingOrHoldingAndActive(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_conference(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_rejectCall(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getLastCallFailCause(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getSignalStrength(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getVoiceRegistrationState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getDataRegistrationState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getOperator(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setRadioPower(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool on);
    static ::android::hardware::Return<void>  _hidl_sendDtmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& s);
    static ::android::hardware::Return<void>  _hidl_sendSms(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::GsmSmsMessage& message);
    static ::android::hardware::Return<void>  _hidl_sendSMSExpectMore(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::GsmSmsMessage& message);
    static ::android::hardware::Return<void>  _hidl_setupDataCall(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::RadioTechnology radioTechnology, const ::android::hardware::radio::V1_0::DataProfileInfo& dataProfileInfo, bool modemCognitive, bool roamingAllowed, bool isRoaming);
    static ::android::hardware::Return<void>  _hidl_iccIOForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::IccIo& iccIo);
    static ::android::hardware::Return<void>  _hidl_sendUssd(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& ussd);
    static ::android::hardware::Return<void>  _hidl_cancelPendingUssd(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getClir(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setClir(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t status);
    static ::android::hardware::Return<void>  _hidl_getCallForwardStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CallForwardInfo& callInfo);
    static ::android::hardware::Return<void>  _hidl_setCallForward(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CallForwardInfo& callInfo);
    static ::android::hardware::Return<void>  _hidl_getCallWaiting(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t serviceClass);
    static ::android::hardware::Return<void>  _hidl_setCallWaiting(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable, int32_t serviceClass);
    static ::android::hardware::Return<void>  _hidl_acknowledgeLastIncomingGsmSms(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool success, ::android::hardware::radio::V1_0::SmsAcknowledgeFailCause cause);
    static ::android::hardware::Return<void>  _hidl_acceptCall(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_deactivateDataCall(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t cid, bool reasonRadioShutDown);
    static ::android::hardware::Return<void>  _hidl_getFacilityLockForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& facility, const ::android::hardware::hidl_string& password, int32_t serviceClass, const ::android::hardware::hidl_string& appId);
    static ::android::hardware::Return<void>  _hidl_setFacilityLockForApp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& facility, bool lockState, const ::android::hardware::hidl_string& password, int32_t serviceClass, const ::android::hardware::hidl_string& appId);
    static ::android::hardware::Return<void>  _hidl_setBarringPassword(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& facility, const ::android::hardware::hidl_string& oldPassword, const ::android::hardware::hidl_string& newPassword);
    static ::android::hardware::Return<void>  _hidl_getNetworkSelectionMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setNetworkSelectionModeAutomatic(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setNetworkSelectionModeManual(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& operatorNumeric);
    static ::android::hardware::Return<void>  _hidl_getAvailableNetworks(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_startDtmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& s);
    static ::android::hardware::Return<void>  _hidl_stopDtmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getBasebandVersion(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_separateConnection(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t gsmIndex);
    static ::android::hardware::Return<void>  _hidl_setMute(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable);
    static ::android::hardware::Return<void>  _hidl_getMute(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getClip(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getDataCallList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setSuppServiceNotifications(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable);
    static ::android::hardware::Return<void>  _hidl_writeSmsToSim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::SmsWriteArgs& smsWriteArgs);
    static ::android::hardware::Return<void>  _hidl_deleteSmsOnSim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t index);
    static ::android::hardware::Return<void>  _hidl_setBandMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::RadioBandMode mode);
    static ::android::hardware::Return<void>  _hidl_getAvailableBandModes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_sendEnvelope(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& command);
    static ::android::hardware::Return<void>  _hidl_sendTerminalResponseToSim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& commandResponse);
    static ::android::hardware::Return<void>  _hidl_handleStkCallSetupRequestFromSim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool accept);
    static ::android::hardware::Return<void>  _hidl_explicitCallTransfer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setPreferredNetworkType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::PreferredNetworkType nwType);
    static ::android::hardware::Return<void>  _hidl_getPreferredNetworkType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getNeighboringCids(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setLocationUpdates(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable);
    static ::android::hardware::Return<void>  _hidl_setCdmaSubscriptionSource(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::CdmaSubscriptionSource cdmaSub);
    static ::android::hardware::Return<void>  _hidl_setCdmaRoamingPreference(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::CdmaRoamingType type);
    static ::android::hardware::Return<void>  _hidl_getCdmaRoamingPreference(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setTTYMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::TtyMode mode);
    static ::android::hardware::Return<void>  _hidl_getTTYMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setPreferredVoicePrivacy(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool enable);
    static ::android::hardware::Return<void>  _hidl_getPreferredVoicePrivacy(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_sendCDMAFeatureCode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& featureCode);
    static ::android::hardware::Return<void>  _hidl_sendBurstDtmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& dtmf, int32_t on, int32_t off);
    static ::android::hardware::Return<void>  _hidl_sendCdmaSms(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsMessage& sms);
    static ::android::hardware::Return<void>  _hidl_acknowledgeLastIncomingCdmaSms(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsAck& smsAck);
    static ::android::hardware::Return<void>  _hidl_getGsmBroadcastConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setGsmBroadcastConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::GsmBroadcastSmsConfigInfo>& configInfo);
    static ::android::hardware::Return<void>  _hidl_setGsmBroadcastActivation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool activate);
    static ::android::hardware::Return<void>  _hidl_getCdmaBroadcastConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setCdmaBroadcastConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::CdmaBroadcastSmsConfigInfo>& configInfo);
    static ::android::hardware::Return<void>  _hidl_setCdmaBroadcastActivation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool activate);
    static ::android::hardware::Return<void>  _hidl_getCDMASubscription(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_writeSmsToRuim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::CdmaSmsWriteArgs& cdmaSms);
    static ::android::hardware::Return<void>  _hidl_deleteSmsOnRuim(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t index);
    static ::android::hardware::Return<void>  _hidl_getDeviceIdentity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_exitEmergencyCallbackMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getSmscAddress(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setSmscAddress(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& smsc);
    static ::android::hardware::Return<void>  _hidl_reportSmsMemoryStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool available);
    static ::android::hardware::Return<void>  _hidl_reportStkServiceIsRunning(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getCdmaSubscriptionSource(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_requestIsimAuthentication(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& challenge);
    static ::android::hardware::Return<void>  _hidl_acknowledgeIncomingGsmSmsWithPdu(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool success, const ::android::hardware::hidl_string& ackPdu);
    static ::android::hardware::Return<void>  _hidl_sendEnvelopeWithStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& contents);
    static ::android::hardware::Return<void>  _hidl_getVoiceRadioTechnology(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getCellInfoList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setCellInfoListRate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t rate);
    static ::android::hardware::Return<void>  _hidl_setInitialAttachApn(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::DataProfileInfo& dataProfileInfo, bool modemCognitive, bool isRoaming);
    static ::android::hardware::Return<void>  _hidl_getImsRegistrationState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_sendImsSms(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::ImsSmsMessage& message);
    static ::android::hardware::Return<void>  _hidl_iccTransmitApduBasicChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::SimApdu& message);
    static ::android::hardware::Return<void>  _hidl_iccOpenLogicalChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_string& aid, int32_t p2);
    static ::android::hardware::Return<void>  _hidl_iccCloseLogicalChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t channelId);
    static ::android::hardware::Return<void>  _hidl_iccTransmitApduLogicalChannel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::SimApdu& message);
    static ::android::hardware::Return<void>  _hidl_nvReadItem(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::NvItem itemId);
    static ::android::hardware::Return<void>  _hidl_nvWriteItem(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::NvWriteItem& item);
    static ::android::hardware::Return<void>  _hidl_nvWriteCdmaPrl(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_vec<uint8_t>& prl);
    static ::android::hardware::Return<void>  _hidl_nvResetConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::ResetNvType resetType);
    static ::android::hardware::Return<void>  _hidl_setUiccSubscription(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::SelectUiccSub& uiccSub);
    static ::android::hardware::Return<void>  _hidl_setDataAllowed(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool allow);
    static ::android::hardware::Return<void>  _hidl_getHardwareConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_requestIccSimAuthentication(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t authContext, const ::android::hardware::hidl_string& authData, const ::android::hardware::hidl_string& aid);
    static ::android::hardware::Return<void>  _hidl_setDataProfile(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::hidl_vec<::android::hardware::radio::V1_0::DataProfileInfo>& profiles, bool isRoaming);
    static ::android::hardware::Return<void>  _hidl_requestShutdown(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getRadioCapability(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setRadioCapability(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, const ::android::hardware::radio::V1_0::RadioCapability& rc);
    static ::android::hardware::Return<void>  _hidl_startLceService(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, int32_t reportInterval, bool pullMode);
    static ::android::hardware::Return<void>  _hidl_stopLceService(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_pullLceData(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_getModemActivityInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_setAllowedCarriers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool allAllowed, const ::android::hardware::radio::V1_0::CarrierRestrictions& carriers);
    static ::android::hardware::Return<void>  _hidl_getAllowedCarriers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial);
    static ::android::hardware::Return<void>  _hidl_sendDeviceState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::radio::V1_0::DeviceStateType deviceStateType, bool state);
    static ::android::hardware::Return<void>  _hidl_setIndicationFilter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_0::IndicationFilter> indicationFilter);
    static ::android::hardware::Return<void>  _hidl_setSimCardPower(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t serial, bool powerUp);
    static ::android::hardware::Return<void>  _hidl_responseAcknowledgement(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWRADIO_H
