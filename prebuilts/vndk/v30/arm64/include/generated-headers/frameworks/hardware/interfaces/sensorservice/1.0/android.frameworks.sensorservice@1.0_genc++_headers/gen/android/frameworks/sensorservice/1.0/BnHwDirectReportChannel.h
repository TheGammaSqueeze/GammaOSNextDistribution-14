#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BNHWDIRECTREPORTCHANNEL_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BNHWDIRECTREPORTCHANNEL_H

#include <android/frameworks/sensorservice/1.0/IHwDirectReportChannel.h>

namespace android {
namespace frameworks {
namespace sensorservice {
namespace V1_0 {

struct BnHwDirectReportChannel : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDirectReportChannel(const ::android::sp<IDirectReportChannel> &_hidl_impl);
    explicit BnHwDirectReportChannel(const ::android::sp<IDirectReportChannel> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDirectReportChannel();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDirectReportChannel Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDirectReportChannel> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::frameworks::sensorservice::V1_0::IDirectReportChannel follow.
    static ::android::status_t _hidl_configure(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::frameworks::sensorservice::V1_0::IDirectReportChannel follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDirectReportChannel> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace sensorservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_SENSORSERVICE_V1_0_BNHWDIRECTREPORTCHANNEL_H
