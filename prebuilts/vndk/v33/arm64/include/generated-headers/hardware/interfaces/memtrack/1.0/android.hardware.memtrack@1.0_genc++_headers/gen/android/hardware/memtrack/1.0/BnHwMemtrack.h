#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BNHWMEMTRACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BNHWMEMTRACK_H

#include <android/hardware/memtrack/1.0/IHwMemtrack.h>

namespace android {
namespace hardware {
namespace memtrack {
namespace V1_0 {

struct BnHwMemtrack : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwMemtrack(const ::android::sp<IMemtrack> &_hidl_impl);
    explicit BnHwMemtrack(const ::android::sp<IMemtrack> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwMemtrack();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IMemtrack Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IMemtrack> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::memtrack::V1_0::IMemtrack follow.
    static ::android::status_t _hidl_getMemory(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::memtrack::V1_0::IMemtrack follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IMemtrack> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace memtrack
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BNHWMEMTRACK_H
