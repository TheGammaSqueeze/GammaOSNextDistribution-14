#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_BNHWCONTEXTHUB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_BNHWCONTEXTHUB_H

#include <android/hardware/contexthub/1.1/IHwContexthub.h>

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_1 {

struct BnHwContexthub : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwContexthub(const ::android::sp<IContexthub> &_hidl_impl);
    explicit BnHwContexthub(const ::android::sp<IContexthub> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwContexthub();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IContexthub Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IContexthub> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::contexthub::V1_1::IContexthub follow.
    static ::android::status_t _hidl_onSettingChanged(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::contexthub::V1_0::IContexthub follow.

    // Methods from ::android::hardware::contexthub::V1_1::IContexthub follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IContexthub> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONTEXTHUB_V1_1_BNHWCONTEXTHUB_H
