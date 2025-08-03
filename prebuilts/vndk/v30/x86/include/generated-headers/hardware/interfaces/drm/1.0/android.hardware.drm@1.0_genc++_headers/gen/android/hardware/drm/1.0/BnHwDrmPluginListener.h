#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWDRMPLUGINLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWDRMPLUGINLISTENER_H

#include <android/hardware/drm/1.0/IHwDrmPluginListener.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {

struct BnHwDrmPluginListener : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDrmPluginListener(const ::android::sp<IDrmPluginListener> &_hidl_impl);
    explicit BnHwDrmPluginListener(const ::android::sp<IDrmPluginListener> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDrmPluginListener();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDrmPluginListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDrmPluginListener> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::drm::V1_0::IDrmPluginListener follow.
    static ::android::status_t _hidl_sendEvent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_sendExpirationUpdate(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_sendKeysChange(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::drm::V1_0::IDrmPluginListener follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDrmPluginListener> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_0_BNHWDRMPLUGINLISTENER_H
