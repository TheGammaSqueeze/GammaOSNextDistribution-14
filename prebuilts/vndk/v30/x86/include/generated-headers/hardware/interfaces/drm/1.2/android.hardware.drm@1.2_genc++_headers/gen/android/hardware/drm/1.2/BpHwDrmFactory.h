#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMFACTORY_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/drm/1.2/IHwDrmFactory.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_2 {

struct BpHwDrmFactory : public ::android::hardware::BpInterface<IDrmFactory>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDrmFactory(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDrmFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::drm::V1_2::IDrmFactory follow.
    static ::android::hardware::Return<bool>  _hidl_isCryptoSchemeSupported_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 16>& uuid, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_1::SecurityLevel securityLevel);

    // Methods from ::android::hardware::drm::V1_0::IDrmFactory follow.
    ::android::hardware::Return<bool> isCryptoSchemeSupported(const ::android::hardware::hidl_array<uint8_t, 16>& uuid) override;
    ::android::hardware::Return<bool> isContentTypeSupported(const ::android::hardware::hidl_string& mimeType) override;
    ::android::hardware::Return<void> createPlugin(const ::android::hardware::hidl_array<uint8_t, 16>& uuid, const ::android::hardware::hidl_string& appPackageName, createPlugin_cb _hidl_cb) override;

    // Methods from ::android::hardware::drm::V1_2::IDrmFactory follow.
    ::android::hardware::Return<bool> isCryptoSchemeSupported_1_2(const ::android::hardware::hidl_array<uint8_t, 16>& uuid, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_1::SecurityLevel securityLevel) override;

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
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMFACTORY_H
