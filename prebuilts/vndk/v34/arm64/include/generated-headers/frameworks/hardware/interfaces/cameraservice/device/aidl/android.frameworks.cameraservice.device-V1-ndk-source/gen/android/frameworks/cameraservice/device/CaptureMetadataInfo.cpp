#include "aidl/android/frameworks/cameraservice/device/CaptureMetadataInfo.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
const char* CaptureMetadataInfo::descriptor = "android.frameworks.cameraservice.device.CaptureMetadataInfo";

binder_status_t CaptureMetadataInfo::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case fmqMetadataSize: {
    int64_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int64_t>) {
      set<fmqMetadataSize>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<fmqMetadataSize>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case metadata: {
    ::aidl::android::frameworks::cameraservice::device::CameraMetadata _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::frameworks::cameraservice::device::CameraMetadata>) {
      set<metadata>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<metadata>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t CaptureMetadataInfo::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case fmqMetadataSize: return ::ndk::AParcel_writeData(_parcel, get<fmqMetadataSize>());
  case metadata: return ::ndk::AParcel_writeData(_parcel, get<metadata>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
