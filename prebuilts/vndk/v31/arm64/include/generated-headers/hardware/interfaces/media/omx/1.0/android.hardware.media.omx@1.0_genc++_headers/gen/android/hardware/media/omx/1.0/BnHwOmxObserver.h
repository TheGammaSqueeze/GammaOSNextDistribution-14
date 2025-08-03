#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BNHWOMXOBSERVER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BNHWOMXOBSERVER_H

#include <android/hardware/media/omx/1.0/IHwOmxObserver.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {

struct BnHwOmxObserver : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwOmxObserver(const ::android::sp<IOmxObserver> &_hidl_impl);
    explicit BnHwOmxObserver(const ::android::sp<IOmxObserver> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwOmxObserver();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IOmxObserver Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IOmxObserver> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::media::omx::V1_0::IOmxObserver follow.
    static ::android::status_t _hidl_onMessages(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::media::omx::V1_0::IOmxObserver follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IOmxObserver> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BNHWOMXOBSERVER_H
