#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_BNHWCLIENTMANAGER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_BNHWCLIENTMANAGER_H

#include <android/hardware/media/bufferpool/1.0/IHwClientManager.h>

namespace android {
namespace hardware {
namespace media {
namespace bufferpool {
namespace V1_0 {

struct BnHwClientManager : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwClientManager(const ::android::sp<IClientManager> &_hidl_impl);
    explicit BnHwClientManager(const ::android::sp<IClientManager> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwClientManager();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IClientManager Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IClientManager> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::media::bufferpool::V1_0::IClientManager follow.
    static ::android::status_t _hidl_registerSender(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::media::bufferpool::V1_0::IClientManager follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IClientManager> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace bufferpool
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V1_0_BNHWCLIENTMANAGER_H
