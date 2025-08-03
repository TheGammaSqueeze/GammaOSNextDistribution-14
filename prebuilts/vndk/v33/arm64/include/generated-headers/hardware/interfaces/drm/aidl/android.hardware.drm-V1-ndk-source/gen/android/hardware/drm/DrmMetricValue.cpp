#include "aidl/android/hardware/drm/DrmMetricValue.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
const char* DrmMetricValue::descriptor = "android.hardware.drm.DrmMetricValue";

binder_status_t DrmMetricValue::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case int64Value: {
    int64_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int64_t>) {
      set<int64Value>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<int64Value>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case doubleValue: {
    double _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<double>) {
      set<doubleValue>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<doubleValue>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case stringValue: {
    std::string _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::string>) {
      set<stringValue>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<stringValue>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t DrmMetricValue::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case int64Value: return ::ndk::AParcel_writeData(_parcel, get<int64Value>());
  case doubleValue: return ::ndk::AParcel_writeData(_parcel, get<doubleValue>());
  case stringValue: return ::ndk::AParcel_writeData(_parcel, get<stringValue>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
