#include "aidl/android/hardware/radio/data/Qos.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
const char* Qos::descriptor = "android.hardware.radio.data.Qos";

binder_status_t Qos::readFromParcel(const AParcel* _parcel) {
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
  case eps: {
    ::aidl::android::hardware::radio::data::EpsQos _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::data::EpsQos>) {
      set<eps>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<eps>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case nr: {
    ::aidl::android::hardware::radio::data::NrQos _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::data::NrQos>) {
      set<nr>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nr>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t Qos::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case eps: return ::ndk::AParcel_writeData(_parcel, get<eps>());
  case nr: return ::ndk::AParcel_writeData(_parcel, get<nr>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
