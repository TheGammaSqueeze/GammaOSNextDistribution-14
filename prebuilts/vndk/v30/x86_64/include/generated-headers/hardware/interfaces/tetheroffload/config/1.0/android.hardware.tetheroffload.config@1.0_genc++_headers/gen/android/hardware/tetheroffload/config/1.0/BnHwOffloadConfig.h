#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BNHWOFFLOADCONFIG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BNHWOFFLOADCONFIG_H

#include <android/hardware/tetheroffload/config/1.0/IHwOffloadConfig.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace config {
namespace V1_0 {

struct BnHwOffloadConfig : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwOffloadConfig(const ::android::sp<IOffloadConfig> &_hidl_impl);
    explicit BnHwOffloadConfig(const ::android::sp<IOffloadConfig> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwOffloadConfig();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IOffloadConfig Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IOffloadConfig> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::tetheroffload::config::V1_0::IOffloadConfig follow.
    static ::android::status_t _hidl_setHandles(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::tetheroffload::config::V1_0::IOffloadConfig follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IOffloadConfig> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace config
}  // namespace tetheroffload
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONFIG_V1_0_BNHWOFFLOADCONFIG_H
