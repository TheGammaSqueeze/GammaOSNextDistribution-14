#include "aidl/android/hardware/radio/data/QosFilterIpsecSpi.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
const char* QosFilterIpsecSpi::descriptor = "android.hardware.radio.data.QosFilterIpsecSpi";

binder_status_t QosFilterIpsecSpi::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case noinit: {
    bool _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<bool>) {
      set<noinit>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<noinit>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case value: {
    int32_t _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<int32_t>) {
      set<value>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<value>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t QosFilterIpsecSpi::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case value: return ::ndk::AParcel_writeData(_parcel, get<value>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
