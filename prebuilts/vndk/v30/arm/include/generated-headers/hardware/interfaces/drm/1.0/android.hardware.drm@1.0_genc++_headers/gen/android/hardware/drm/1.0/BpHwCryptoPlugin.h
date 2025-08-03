#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BPHWCRYPTOPLUGIN_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BPHWCRYPTOPLUGIN_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/drm/1.0/IHwCryptoPlugin.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {

struct BpHwCryptoPlugin : public ::android::hardware::BpInterface<ICryptoPlugin>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCryptoPlugin(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICryptoPlugin Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::drm::V1_0::ICryptoPlugin follow.
    static ::android::hardware::Return<bool>  _hidl_requiresSecureDecoderComponent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& mime);
    static ::android::hardware::Return<void>  _hidl_notifyResolution(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t width, uint32_t height);
    static ::android::hardware::Return<::android::hardware::drm::V1_0::Status>  _hidl_setMediaDrmSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId);
    static ::android::hardware::Return<void>  _hidl_setSharedBufferBase(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_memory& base, uint32_t bufferId);
    static ::android::hardware::Return<void>  _hidl_decrypt(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool secure, const ::android::hardware::hidl_array<uint8_t, 16>& keyId, const ::android::hardware::hidl_array<uint8_t, 16>& iv, ::android::hardware::drm::V1_0::Mode mode, const ::android::hardware::drm::V1_0::Pattern& pattern, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::SubSample>& subSamples, const ::android::hardware::drm::V1_0::SharedBuffer& source, uint64_t offset, const ::android::hardware::drm::V1_0::DestinationBuffer& destination, decrypt_cb _hidl_cb);

    // Methods from ::android::hardware::drm::V1_0::ICryptoPlugin follow.
    ::android::hardware::Return<bool> requiresSecureDecoderComponent(const ::android::hardware::hidl_string& mime) override;
    ::android::hardware::Return<void> notifyResolution(uint32_t width, uint32_t height) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setMediaDrmSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<void> setSharedBufferBase(const ::android::hardware::hidl_memory& base, uint32_t bufferId) override;
    ::android::hardware::Return<void> decrypt(bool secure, const ::android::hardware::hidl_array<uint8_t, 16>& keyId, const ::android::hardware::hidl_array<uint8_t, 16>& iv, ::android::hardware::drm::V1_0::Mode mode, const ::android::hardware::drm::V1_0::Pattern& pattern, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::SubSample>& subSamples, const ::android::hardware::drm::V1_0::SharedBuffer& source, uint64_t offset, const ::android::hardware::drm::V1_0::DestinationBuffer& destination, decrypt_cb _hidl_cb) override;

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
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BPHWCRYPTOPLUGIN_H
