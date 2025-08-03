#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAPCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAPCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.0/IHwSapCallback.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_0 {

struct BpHwSapCallback : public ::android::hardware::BpInterface<ISapCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSapCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISapCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::V1_0::ISapCallback follow.
    static ::android::hardware::Return<void>  _hidl_connectResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapConnectRsp sapConnectRsp, int32_t maxMsgSize);
    static ::android::hardware::Return<void>  _hidl_disconnectResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_disconnectIndication(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapDisconnectType disconnectType);
    static ::android::hardware::Return<void>  _hidl_apduResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, const ::android::hardware::hidl_vec<uint8_t>& apduRsp);
    static ::android::hardware::Return<void>  _hidl_transferAtrResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, const ::android::hardware::hidl_vec<uint8_t>& atr);
    static ::android::hardware::Return<void>  _hidl_powerResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode);
    static ::android::hardware::Return<void>  _hidl_resetSimResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode);
    static ::android::hardware::Return<void>  _hidl_statusIndication(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapStatus status);
    static ::android::hardware::Return<void>  _hidl_transferCardReaderStatusResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, int32_t cardReaderStatus);
    static ::android::hardware::Return<void>  _hidl_errorResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_transferProtocolResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode);

    // Methods from ::android::hardware::radio::V1_0::ISapCallback follow.
    ::android::hardware::Return<void> connectResponse(int32_t token, ::android::hardware::radio::V1_0::SapConnectRsp sapConnectRsp, int32_t maxMsgSize) override;
    ::android::hardware::Return<void> disconnectResponse(int32_t token) override;
    ::android::hardware::Return<void> disconnectIndication(int32_t token, ::android::hardware::radio::V1_0::SapDisconnectType disconnectType) override;
    ::android::hardware::Return<void> apduResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, const ::android::hardware::hidl_vec<uint8_t>& apduRsp) override;
    ::android::hardware::Return<void> transferAtrResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, const ::android::hardware::hidl_vec<uint8_t>& atr) override;
    ::android::hardware::Return<void> powerResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode) override;
    ::android::hardware::Return<void> resetSimResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode) override;
    ::android::hardware::Return<void> statusIndication(int32_t token, ::android::hardware::radio::V1_0::SapStatus status) override;
    ::android::hardware::Return<void> transferCardReaderStatusResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode, int32_t cardReaderStatus) override;
    ::android::hardware::Return<void> errorResponse(int32_t token) override;
    ::android::hardware::Return<void> transferProtocolResponse(int32_t token, ::android::hardware::radio::V1_0::SapResultCode resultCode) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAPCALLBACK_H
