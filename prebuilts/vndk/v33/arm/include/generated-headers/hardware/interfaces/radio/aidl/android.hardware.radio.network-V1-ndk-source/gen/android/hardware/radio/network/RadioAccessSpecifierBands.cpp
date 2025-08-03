#include "aidl/android/hardware/radio/network/RadioAccessSpecifierBands.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
const char* RadioAccessSpecifierBands::descriptor = "android.hardware.radio.network.RadioAccessSpecifierBands";

binder_status_t RadioAccessSpecifierBands::readFromParcel(const AParcel* _parcel) {
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
  case geranBands: {
    std::vector<::aidl::android::hardware::radio::network::GeranBands> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<::aidl::android::hardware::radio::network::GeranBands>>) {
      set<geranBands>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<geranBands>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case utranBands: {
    std::vector<::aidl::android::hardware::radio::network::UtranBands> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<::aidl::android::hardware::radio::network::UtranBands>>) {
      set<utranBands>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<utranBands>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case eutranBands: {
    std::vector<::aidl::android::hardware::radio::network::EutranBands> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<::aidl::android::hardware::radio::network::EutranBands>>) {
      set<eutranBands>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<eutranBands>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case ngranBands: {
    std::vector<::aidl::android::hardware::radio::network::NgranBands> _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<std::vector<::aidl::android::hardware::radio::network::NgranBands>>) {
      set<ngranBands>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ngranBands>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t RadioAccessSpecifierBands::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case geranBands: return ::ndk::AParcel_writeData(_parcel, get<geranBands>());
  case utranBands: return ::ndk::AParcel_writeData(_parcel, get<utranBands>());
  case eutranBands: return ::ndk::AParcel_writeData(_parcel, get<eutranBands>());
  case ngranBands: return ::ndk::AParcel_writeData(_parcel, get<ngranBands>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
