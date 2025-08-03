#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/common/fmq/GrantorDescriptor.h>
namespace aidl {
namespace android {
namespace hardware {
namespace common {
namespace fmq {
template <typename T, typename Flavor>
class MQDescriptor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::common::fmq::GrantorDescriptor> grantors;
  ::aidl::android::hardware::common::NativeHandle handle;
  int32_t quantum = 0;
  int32_t flags = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) != std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }
  inline bool operator<(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) < std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }
  inline bool operator<=(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) <= std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }
  inline bool operator==(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) == std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }
  inline bool operator>(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) > std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }
  inline bool operator>=(const MQDescriptor& rhs) const {
    return std::tie(grantors, handle, quantum, flags) >= std::tie(rhs.grantors, rhs.handle, rhs.quantum, rhs.flags);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "MQDescriptor{";
    os << "grantors: " << ::android::internal::ToString(grantors);
    os << ", handle: " << ::android::internal::ToString(handle);
    os << ", quantum: " << ::android::internal::ToString(quantum);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << "}";
    return os.str();
  }
};
}  // namespace fmq
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
#include "aidl/android/hardware/common/fmq/MQDescriptor.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace common {
namespace fmq {
template <typename T, typename Flavor>
const char* MQDescriptor<T, Flavor>::descriptor = "android.hardware.common.fmq.MQDescriptor";

template <typename T, typename Flavor>
binder_status_t MQDescriptor<T, Flavor>::readFromParcel(const AParcel* parcel) {
  int32_t _aidl_parcelable_size;
  int32_t _aidl_start_pos = AParcel_getDataPosition(parcel);
  binder_status_t _aidl_ret_status = AParcel_readInt32(parcel, &_aidl_parcelable_size);
  if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return STATUS_BAD_VALUE;
  if (_aidl_parcelable_size < 0) return STATUS_BAD_VALUE;
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readVector(parcel, &grantors);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = ::ndk::AParcel_readParcelable(parcel, &handle);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = AParcel_readInt32(parcel, &quantum);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  if (AParcel_getDataPosition(parcel) - _aidl_start_pos >= _aidl_parcelable_size) {
    AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
    return _aidl_ret_status;
  }
  _aidl_ret_status = AParcel_readInt32(parcel, &flags);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  AParcel_setDataPosition(parcel, _aidl_start_pos + _aidl_parcelable_size);
  return _aidl_ret_status;
}
template <typename T, typename Flavor>
binder_status_t MQDescriptor<T, Flavor>::writeToParcel(AParcel* parcel) const {
  binder_status_t _aidl_ret_status;
  size_t _aidl_start_pos = AParcel_getDataPosition(parcel);
  _aidl_ret_status = AParcel_writeInt32(parcel, 0);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeVector(parcel, grantors);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = ::ndk::AParcel_writeParcelable(parcel, handle);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = AParcel_writeInt32(parcel, quantum);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  _aidl_ret_status = AParcel_writeInt32(parcel, flags);
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;

  size_t _aidl_end_pos = AParcel_getDataPosition(parcel);
  AParcel_setDataPosition(parcel, _aidl_start_pos);
  AParcel_writeInt32(parcel, _aidl_end_pos - _aidl_start_pos);
  AParcel_setDataPosition(parcel, _aidl_end_pos);
  return _aidl_ret_status;
}

}  // namespace fmq
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
