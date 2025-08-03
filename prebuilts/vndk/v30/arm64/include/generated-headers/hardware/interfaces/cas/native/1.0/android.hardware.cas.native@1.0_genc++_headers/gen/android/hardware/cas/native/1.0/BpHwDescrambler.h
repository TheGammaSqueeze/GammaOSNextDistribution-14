#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BPHWDESCRAMBLER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BPHWDESCRAMBLER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/cas/native/1.0/IHwDescrambler.h>

namespace android {
namespace hardware {
namespace cas {
namespace native {
namespace V1_0 {

struct BpHwDescrambler : public ::android::hardware::BpInterface<IDescrambler>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDescrambler(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDescrambler Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::cas::native::V1_0::IDescrambler follow.
    static ::android::hardware::Return<void>  _hidl_descramble(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::cas::native::V1_0::ScramblingControl scramblingControl, const ::android::hardware::hidl_vec<::android::hardware::cas::native::V1_0::SubSample>& subSamples, const ::android::hardware::cas::native::V1_0::SharedBuffer& srcBuffer, uint64_t srcOffset, const ::android::hardware::cas::native::V1_0::DestinationBuffer& dstBuffer, uint64_t dstOffset, descramble_cb _hidl_cb);

    // Methods from ::android::hardware::cas::V1_0::IDescramblerBase follow.
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> setMediaCasSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<bool> requiresSecureDecoderComponent(const ::android::hardware::hidl_string& mime) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> release() override;

    // Methods from ::android::hardware::cas::native::V1_0::IDescrambler follow.
    ::android::hardware::Return<void> descramble(::android::hardware::cas::native::V1_0::ScramblingControl scramblingControl, const ::android::hardware::hidl_vec<::android::hardware::cas::native::V1_0::SubSample>& subSamples, const ::android::hardware::cas::native::V1_0::SharedBuffer& srcBuffer, uint64_t srcOffset, const ::android::hardware::cas::native::V1_0::DestinationBuffer& dstBuffer, uint64_t dstOffset, descramble_cb _hidl_cb) override;

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
}  // namespace native
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BPHWDESCRAMBLER_H
