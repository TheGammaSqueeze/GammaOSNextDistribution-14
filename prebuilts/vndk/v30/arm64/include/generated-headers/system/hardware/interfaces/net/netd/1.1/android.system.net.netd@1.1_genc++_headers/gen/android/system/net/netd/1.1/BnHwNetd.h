#ifndef HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_BNHWNETD_H
#define HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_BNHWNETD_H

#include <android/system/net/netd/1.1/IHwNetd.h>

namespace android {
namespace system {
namespace net {
namespace netd {
namespace V1_1 {

struct BnHwNetd : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwNetd(const ::android::sp<INetd> &_hidl_impl);
    explicit BnHwNetd(const ::android::sp<INetd> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwNetd();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef INetd Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<INetd> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::system::net::netd::V1_1::INetd follow.
    static ::android::status_t _hidl_addRouteToOemNetwork(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_removeRouteFromOemNetwork(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_addInterfaceToOemNetwork(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_removeInterfaceFromOemNetwork(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setIpForwardEnable(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setForwardingBetweenInterfaces(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::system::net::netd::V1_0::INetd follow.

    // Methods from ::android::system::net::netd::V1_1::INetd follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<INetd> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace netd
}  // namespace net
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_BNHWNETD_H
