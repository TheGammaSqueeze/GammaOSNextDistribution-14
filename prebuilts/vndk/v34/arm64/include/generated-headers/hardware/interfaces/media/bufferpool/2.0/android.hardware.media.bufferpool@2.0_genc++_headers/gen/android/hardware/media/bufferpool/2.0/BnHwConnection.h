#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V2_0_BNHWCONNECTION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V2_0_BNHWCONNECTION_H

#include <android/hardware/media/bufferpool/2.0/IHwConnection.h>

namespace android {
namespace hardware {
namespace media {
namespace bufferpool {
namespace V2_0 {

struct BnHwConnection : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwConnection(const ::android::sp<IConnection> &_hidl_impl);
    explicit BnHwConnection(const ::android::sp<IConnection> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwConnection();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IConnection Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IConnection> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::media::bufferpool::V2_0::IConnection follow.
    static ::android::status_t _hidl_fetch(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::media::bufferpool::V2_0::IConnection follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IConnection> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace bufferpool
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_BUFFERPOOL_V2_0_BNHWCONNECTION_H
