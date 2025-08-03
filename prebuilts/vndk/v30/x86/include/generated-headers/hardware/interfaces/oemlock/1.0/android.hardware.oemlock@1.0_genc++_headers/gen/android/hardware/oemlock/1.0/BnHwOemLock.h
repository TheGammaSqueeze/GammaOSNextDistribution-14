#ifndef HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BNHWOEMLOCK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BNHWOEMLOCK_H

#include <android/hardware/oemlock/1.0/IHwOemLock.h>

namespace android {
namespace hardware {
namespace oemlock {
namespace V1_0 {

struct BnHwOemLock : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwOemLock(const ::android::sp<IOemLock> &_hidl_impl);
    explicit BnHwOemLock(const ::android::sp<IOemLock> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwOemLock();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IOemLock Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IOemLock> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::oemlock::V1_0::IOemLock follow.
    static ::android::status_t _hidl_getName(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setOemUnlockAllowedByCarrier(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_isOemUnlockAllowedByCarrier(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setOemUnlockAllowedByDevice(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_isOemUnlockAllowedByDevice(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::oemlock::V1_0::IOemLock follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IOemLock> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace oemlock
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BNHWOEMLOCK_H
