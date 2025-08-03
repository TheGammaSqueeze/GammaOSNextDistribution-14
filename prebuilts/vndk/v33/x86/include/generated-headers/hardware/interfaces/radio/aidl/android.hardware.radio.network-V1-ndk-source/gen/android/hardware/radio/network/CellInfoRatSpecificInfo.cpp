#include "aidl/android/hardware/radio/network/CellInfoRatSpecificInfo.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
const char* CellInfoRatSpecificInfo::descriptor = "android.hardware.radio.network.CellInfoRatSpecificInfo";

binder_status_t CellInfoRatSpecificInfo::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case gsm: {
    ::aidl::android::hardware::radio::network::CellInfoGsm _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoGsm>) {
      set<gsm>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<gsm>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case wcdma: {
    ::aidl::android::hardware::radio::network::CellInfoWcdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoWcdma>) {
      set<wcdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<wcdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case tdscdma: {
    ::aidl::android::hardware::radio::network::CellInfoTdscdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoTdscdma>) {
      set<tdscdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<tdscdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case lte: {
    ::aidl::android::hardware::radio::network::CellInfoLte _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoLte>) {
      set<lte>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<lte>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case nr: {
    ::aidl::android::hardware::radio::network::CellInfoNr _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoNr>) {
      set<nr>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<nr>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case cdma: {
    ::aidl::android::hardware::radio::network::CellInfoCdma _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::CellInfoCdma>) {
      set<cdma>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<cdma>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t CellInfoRatSpecificInfo::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case gsm: return ::ndk::AParcel_writeData(_parcel, get<gsm>());
  case wcdma: return ::ndk::AParcel_writeData(_parcel, get<wcdma>());
  case tdscdma: return ::ndk::AParcel_writeData(_parcel, get<tdscdma>());
  case lte: return ::ndk::AParcel_writeData(_parcel, get<lte>());
  case nr: return ::ndk::AParcel_writeData(_parcel, get<nr>());
  case cdma: return ::ndk::AParcel_writeData(_parcel, get<cdma>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
