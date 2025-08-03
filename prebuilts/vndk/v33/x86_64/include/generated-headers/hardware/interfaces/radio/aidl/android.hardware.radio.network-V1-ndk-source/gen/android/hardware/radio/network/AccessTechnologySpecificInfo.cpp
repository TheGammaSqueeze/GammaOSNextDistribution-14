#include "aidl/android/hardware/radio/network/AccessTechnologySpecificInfo.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
const char* AccessTechnologySpecificInfo::descriptor = "android.hardware.radio.network.AccessTechnologySpecificInfo";

binder_status_t AccessTechnologySpecificInfo::readFromParcel(const AParcel* _parcel) {
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
  case cdmaInfo: {
    ::aidl::android::hardware::radio::network::Cdma2000RegistrationInfo _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::Cdma2000RegistrationInfo>) {
      set<cdmaInfo>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<cdmaInfo>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case eutranInfo: {
    ::aidl::android::hardware::radio::network::EutranRegistrationInfo _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::EutranRegistrationInfo>) {
      set<eutranInfo>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<eutranInfo>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case ngranNrVopsInfo: {
    ::aidl::android::hardware::radio::network::NrVopsInfo _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::radio::network::NrVopsInfo>) {
      set<ngranNrVopsInfo>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<ngranNrVopsInfo>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case geranDtmSupported: {
    bool _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<bool>) {
      set<geranDtmSupported>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<geranDtmSupported>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t AccessTechnologySpecificInfo::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case noinit: return ::ndk::AParcel_writeData(_parcel, get<noinit>());
  case cdmaInfo: return ::ndk::AParcel_writeData(_parcel, get<cdmaInfo>());
  case eutranInfo: return ::ndk::AParcel_writeData(_parcel, get<eutranInfo>());
  case ngranNrVopsInfo: return ::ndk::AParcel_writeData(_parcel, get<ngranNrVopsInfo>());
  case geranDtmSupported: return ::ndk::AParcel_writeData(_parcel, get<geranDtmSupported>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
