#include "aidl/android/hardware/vibrator/PrimitivePwle.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
const char* PrimitivePwle::descriptor = "android.hardware.vibrator.PrimitivePwle";

binder_status_t PrimitivePwle::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = AParcel_readInt32(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (_aidl_tag) {
  case active: {
    ::aidl::android::hardware::vibrator::ActivePwle _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readParcelable(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::vibrator::ActivePwle>) {
      set<active>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<active>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case braking: {
    ::aidl::android::hardware::vibrator::BrakingPwle _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readParcelable(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::vibrator::BrakingPwle>) {
      set<braking>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<braking>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t PrimitivePwle::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = AParcel_writeInt32(_parcel, getTag());
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case active: return ::ndk::AParcel_writeParcelable(_parcel, get<active>());
  case braking: return ::ndk::AParcel_writeParcelable(_parcel, get<braking>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
