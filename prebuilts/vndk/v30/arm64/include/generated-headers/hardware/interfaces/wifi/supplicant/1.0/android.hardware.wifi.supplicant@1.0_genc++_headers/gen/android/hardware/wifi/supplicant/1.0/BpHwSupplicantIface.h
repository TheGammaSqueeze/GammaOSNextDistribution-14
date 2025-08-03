#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BpHwSupplicantIface : public ::android::hardware::BpInterface<ISupplicantIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface follow.
    static ::android::hardware::Return<void>  _hidl_getName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getName_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getType_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_addNetwork(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, addNetwork_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeNetwork(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id, removeNetwork_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getNetwork(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id, getNetwork_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_listNetworks(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, listNetworks_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsDeviceName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& name, setWpsDeviceName_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsDeviceType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 8>& type, setWpsDeviceType_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsManufacturer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& manufacturer, setWpsManufacturer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsModelName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& modelName, setWpsModelName_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsModelNumber(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& modelNumber, setWpsModelNumber_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsSerialNumber(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& serialNumber, setWpsSerialNumber_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWpsConfigMethods(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, setWpsConfigMethods_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface follow.
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> addNetwork(addNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeNetwork(uint32_t id, removeNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNetwork(uint32_t id, getNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> listNetworks(listNetworks_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsDeviceName(const ::android::hardware::hidl_string& name, setWpsDeviceName_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsDeviceType(const ::android::hardware::hidl_array<uint8_t, 8>& type, setWpsDeviceType_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsManufacturer(const ::android::hardware::hidl_string& manufacturer, setWpsManufacturer_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsModelName(const ::android::hardware::hidl_string& modelName, setWpsModelName_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsModelNumber(const ::android::hardware::hidl_string& modelNumber, setWpsModelNumber_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsSerialNumber(const ::android::hardware::hidl_string& serialNumber, setWpsSerialNumber_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsConfigMethods(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, setWpsConfigMethods_cb _hidl_cb) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTIFACE_H
