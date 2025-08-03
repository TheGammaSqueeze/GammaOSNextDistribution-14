#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BNHWWEAVER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BNHWWEAVER_H

#include <android/hardware/weaver/1.0/IHwWeaver.h>

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {

struct BnHwWeaver : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwWeaver(const ::android::sp<IWeaver> &_hidl_impl);
    explicit BnHwWeaver(const ::android::sp<IWeaver> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwWeaver();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IWeaver Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IWeaver> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::weaver::V1_0::IWeaver follow.
    static ::android::status_t _hidl_getConfig(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_write(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_read(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::weaver::V1_0::IWeaver follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IWeaver> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace weaver
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WEAVER_V1_0_BNHWWEAVER_H
