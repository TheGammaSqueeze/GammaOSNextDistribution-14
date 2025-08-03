#include "aidl/android/hardware/drm/DestinationBuffer.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
const char* DestinationBuffer::descriptor = "android.hardware.drm.DestinationBuffer";

binder_status_t DestinationBuffer::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case nonsecureMemory: {
    ::aidl::android::hardware::drm::SharedBuffer _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::drm::SharedBuffer>) {
      set<nonsecureMemory>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nonsecureMemory>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case secureMemory: {
    ::aidl::android::hardware::common::NativeHandle _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::common::NativeHandle>) {
      set<secureMemory>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<secureMemory>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t DestinationBuffer::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case nonsecureMemory: return ::ndk::AParcel_writeData(_parcel, get<nonsecureMemory>());
  case secureMemory: return ::ndk::AParcel_writeData(_parcel, get<secureMemory>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
