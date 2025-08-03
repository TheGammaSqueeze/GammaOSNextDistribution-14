#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAP_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/radio/1.0/IHwSap.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_0 {

struct BpHwSap : public ::android::hardware::BpInterface<ISap>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSap(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISap Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::radio::V1_0::ISap follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::radio::V1_0::ISapCallback>& sapCallback);
    static ::android::hardware::Return<void>  _hidl_connectReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, int32_t maxMsgSize);
    static ::android::hardware::Return<void>  _hidl_disconnectReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_apduReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapApduType type, const ::android::hardware::hidl_vec<uint8_t>& command);
    static ::android::hardware::Return<void>  _hidl_transferAtrReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_powerReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, bool state);
    static ::android::hardware::Return<void>  _hidl_resetSimReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_transferCardReaderStatusReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token);
    static ::android::hardware::Return<void>  _hidl_setTransferProtocolReq(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t token, ::android::hardware::radio::V1_0::SapTransferProtocol transferProtocol);

    // Methods from ::android::hardware::radio::V1_0::ISap follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::radio::V1_0::ISapCallback>& sapCallback) override;
    ::android::hardware::Return<void> connectReq(int32_t token, int32_t maxMsgSize) override;
    ::android::hardware::Return<void> disconnectReq(int32_t token) override;
    ::android::hardware::Return<void> apduReq(int32_t token, ::android::hardware::radio::V1_0::SapApduType type, const ::android::hardware::hidl_vec<uint8_t>& command) override;
    ::android::hardware::Return<void> transferAtrReq(int32_t token) override;
    ::android::hardware::Return<void> powerReq(int32_t token, bool state) override;
    ::android::hardware::Return<void> resetSimReq(int32_t token) override;
    ::android::hardware::Return<void> transferCardReaderStatusReq(int32_t token) override;
    ::android::hardware::Return<void> setTransferProtocolReq(int32_t token, ::android::hardware::radio::V1_0::SapTransferProtocol transferProtocol) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_0_BPHWSAP_H
