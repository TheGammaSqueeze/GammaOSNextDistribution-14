#include "aidl/android/hardware/radio/network/CellIdentity.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
const char* CellIdentity::descriptor = "android.hardware.radio.network.CellIdentity";

binder_status_t CellIdentity::readFromParcel(const AParcel* _parcel) {
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
  case gsm: {
    ::aidl::android::hardware::radio::network::CellIdentityGsm _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityGsm>) {
      set<gsm>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<gsm>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case wcdma: {
    ::aidl::android::hardware::radio::network::CellIdentityWcdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityWcdma>) {
      set<wcdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<wcdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case tdscdma: {
    ::aidl::android::hardware::radio::network::CellIdentityTdscdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityTdscdma>) {
      set<tdscdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<tdscdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case cdma: {
    ::aidl::android::hardware::radio::network::CellIdentityCdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityCdma>) {
      set<cdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<cdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case lte: {
    ::aidl::android::hardware::radio::network::CellIdentityLte _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityLte>) {
      set<lte>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<lte>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case nr: {
    ::aidl::android::hardware::radio::network::CellIdentityNr _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellIdentityNr>) {
      set<nr>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nr>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t CellIdentity::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case gsm: return ::ndk::AParcel_writeData(_parcel, get<gsm>());
  case wcdma: return ::ndk::AParcel_writeData(_parcel, get<wcdma>());
  case tdscdma: return ::ndk::AParcel_writeData(_parcel, get<tdscdma>());
  case cdma: return ::ndk::AParcel_writeData(_parcel, get<cdma>());
  case lte: return ::ndk::AParcel_writeData(_parcel, get<lte>());
  case nr: return ::ndk::AParcel_writeData(_parcel, get<nr>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
