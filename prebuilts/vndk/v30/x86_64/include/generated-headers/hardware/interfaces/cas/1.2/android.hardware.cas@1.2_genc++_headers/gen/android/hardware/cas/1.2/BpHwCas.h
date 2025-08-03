#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_BPHWCAS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_BPHWCAS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/cas/1.2/IHwCas.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_2 {

struct BpHwCas : public ::android::hardware::BpInterface<ICas>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCas(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICas Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::cas::V1_2::ICas follow.
    static ::android::hardware::Return<void>  _hidl_openSession_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::cas::V1_2::SessionIntent intent, ::android::hardware::cas::V1_2::ScramblingMode mode, openSession_1_2_cb _hidl_cb);

    // Methods from ::android::hardware::cas::V1_0::ICas follow.
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> setPrivateData(const ::android::hardware::hidl_vec<uint8_t>& pvtData) override;
    ::android::hardware::Return<void> openSession(openSession_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> closeSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> setSessionPrivateData(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& pvtData) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> processEcm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& ecm) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> processEmm(const ::android::hardware::hidl_vec<uint8_t>& emm) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> sendEvent(int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& eventData) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> provision(const ::android::hardware::hidl_string& provisionString) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> refreshEntitlements(int32_t refreshType, const ::android::hardware::hidl_vec<uint8_t>& refreshData) override;
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> release() override;

    // Methods from ::android::hardware::cas::V1_1::ICas follow.
    ::android::hardware::Return<::android::hardware::cas::V1_0::Status> sendSessionEvent(const ::android::hardware::hidl_vec<uint8_t>& sessionId, int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& eventData) override;

    // Methods from ::android::hardware::cas::V1_2::ICas follow.
    ::android::hardware::Return<void> openSession_1_2(::android::hardware::cas::V1_2::SessionIntent intent, ::android::hardware::cas::V1_2::ScramblingMode mode, openSession_1_2_cb _hidl_cb) override;

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

}  // namespace V1_2
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_2_BPHWCAS_H
