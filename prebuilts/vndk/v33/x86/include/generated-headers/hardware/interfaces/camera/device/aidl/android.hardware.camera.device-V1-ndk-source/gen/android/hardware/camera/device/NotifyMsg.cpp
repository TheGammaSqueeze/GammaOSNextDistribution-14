#include "aidl/android/hardware/camera/device/NotifyMsg.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
const char* NotifyMsg::descriptor = "android.hardware.camera.device.NotifyMsg";

binder_status_t NotifyMsg::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case error: {
    ::aidl::android::hardware::camera::device::ErrorMsg _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::camera::device::ErrorMsg>) {
      set<error>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<error>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case shutter: {
    ::aidl::android::hardware::camera::device::ShutterMsg _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::camera::device::ShutterMsg>) {
      set<shutter>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<shutter>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t NotifyMsg::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case error: return ::ndk::AParcel_writeData(_parcel, get<error>());
  case shutter: return ::ndk::AParcel_writeData(_parcel, get<shutter>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
