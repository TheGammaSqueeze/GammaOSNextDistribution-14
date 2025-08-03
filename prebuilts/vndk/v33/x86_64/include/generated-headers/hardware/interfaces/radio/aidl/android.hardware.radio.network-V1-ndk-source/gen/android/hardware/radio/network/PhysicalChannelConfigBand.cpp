#include "aidl/android/hardware/radio/network/PhysicalChannelConfigBand.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
const char* PhysicalChannelConfigBand::descriptor = "android.hardware.radio.network.PhysicalChannelConfigBand";

binder_status_t PhysicalChannelConfigBand::readFromParcel(const AParcel* _parcel) {
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
  case geranBand: {
    ::aidl::android::hardware::radio::network::GeranBands _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::GeranBands>) {
      set<geranBand>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<geranBand>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case utranBand: {
    ::aidl::android::hardware::radio::network::UtranBands _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::UtranBands>) {
      set<utranBand>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<utranBand>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case eutranBand: {
    ::aidl::android::hardware::radio::network::EutranBands _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::EutranBands>) {
      set<eutranBand>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<eutranBand>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case ngranBand: {
    ::aidl::android::hardware::radio::network::NgranBands _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::NgranBands>) {
      set<ngranBand>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ngranBand>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t PhysicalChannelConfigBand::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case geranBand: return ::ndk::AParcel_writeData(_parcel, get<geranBand>());
  case utranBand: return ::ndk::AParcel_writeData(_parcel, get<utranBand>());
  case eutranBand: return ::ndk::AParcel_writeData(_parcel, get<eutranBand>());
  case ngranBand: return ::ndk::AParcel_writeData(_parcel, get<ngranBand>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
