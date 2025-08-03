#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCAS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCAS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/cas/1.0/IHwCas.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {

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

    // Methods from ::android::hardware::cas::V1_0::ICas follow.
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_setPrivateData(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& pvtData);
    static ::android::hardware::Return<void>  _hidl_openSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, openSession_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_closeSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_setSessionPrivateData(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& pvtData);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_processEcm(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& ecm);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_processEmm(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& emm);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_sendEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t event, int32_t arg, const ::android::hardware::hidl_vec<uint8_t>& eventData);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_provision(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& provisionString);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_refreshEntitlements(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t refreshType, const ::android::hardware::hidl_vec<uint8_t>& refreshData);
    static ::android::hardware::Return<::android::hardware::cas::V1_0::Status>  _hidl_release(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

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
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BPHWCAS_H
