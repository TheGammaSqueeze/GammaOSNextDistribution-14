#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWCRYPTOPLUGIN_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWCRYPTOPLUGIN_H

#include <android/hardware/drm/1.0/IHwCryptoPlugin.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {

struct BnHwCryptoPlugin : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCryptoPlugin(const ::android::sp<ICryptoPlugin> &_hidl_impl);
    explicit BnHwCryptoPlugin(const ::android::sp<ICryptoPlugin> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCryptoPlugin();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICryptoPlugin Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICryptoPlugin> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::drm::V1_0::ICryptoPlugin follow.
    static ::android::status_t _hidl_requiresSecureDecoderComponent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_notifyResolution(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setMediaDrmSession(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setSharedBufferBase(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_decrypt(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::drm::V1_0::ICryptoPlugin follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICryptoPlugin> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWCRYPTOPLUGIN_H
