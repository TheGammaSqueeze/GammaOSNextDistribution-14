#ifndef HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_BNHWSTORAGE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_BNHWSTORAGE_H

#include <android/hardware/health/storage/1.0/IHwStorage.h>

namespace android {
namespace hardware {
namespace health {
namespace storage {
namespace V1_0 {

struct BnHwStorage : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwStorage(const ::android::sp<IStorage> &_hidl_impl);
    explicit BnHwStorage(const ::android::sp<IStorage> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwStorage();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IStorage Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IStorage> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::health::storage::V1_0::IStorage follow.
    static ::android::status_t _hidl_garbageCollect(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::health::storage::V1_0::IStorage follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IStorage> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_STORAGE_V1_0_BNHWSTORAGE_H
