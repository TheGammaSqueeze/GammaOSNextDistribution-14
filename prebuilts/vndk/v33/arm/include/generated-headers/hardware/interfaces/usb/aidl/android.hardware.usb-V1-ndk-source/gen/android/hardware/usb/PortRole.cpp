#include "aidl/android/hardware/usb/PortRole.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace usb {
const char* PortRole::descriptor = "android.hardware.usb.PortRole";

binder_status_t PortRole::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case powerRole: {
    ::aidl::android::hardware::usb::PortPowerRole _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::usb::PortPowerRole>) {
      set<powerRole>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<powerRole>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case dataRole: {
    ::aidl::android::hardware::usb::PortDataRole _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::usb::PortDataRole>) {
      set<dataRole>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<dataRole>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case mode: {
    ::aidl::android::hardware::usb::PortMode _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::usb::PortMode>) {
      set<mode>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<mode>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t PortRole::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case powerRole: return ::ndk::AParcel_writeData(_parcel, get<powerRole>());
  case dataRole: return ::ndk::AParcel_writeData(_parcel, get<dataRole>());
  case mode: return ::ndk::AParcel_writeData(_parcel, get<mode>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
