#include "aidl/android/hardware/graphics/composer3/CommandResultPayload.h"

#include <android/binder_parcel_utils.h>

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
const char* CommandResultPayload::descriptor = "android.hardware.graphics.composer3.CommandResultPayload";

binder_status_t CommandResultPayload::readFromParcel(const AParcel* _parcel) {
  binder_status_t _aidl_ret_status;
  int32_t _aidl_tag;
  if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_tag)) != STATUS_OK) return _aidl_ret_status;
  switch (static_cast<Tag>(_aidl_tag)) {
  case error: {
    ::aidl::android::hardware::graphics::composer3::CommandError _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::CommandError>) {
      set<error>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<error>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case changedCompositionTypes: {
    ::aidl::android::hardware::graphics::composer3::ChangedCompositionTypes _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::ChangedCompositionTypes>) {
      set<changedCompositionTypes>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<changedCompositionTypes>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case displayRequest: {
    ::aidl::android::hardware::graphics::composer3::DisplayRequest _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::DisplayRequest>) {
      set<displayRequest>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<displayRequest>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case presentFence: {
    ::aidl::android::hardware::graphics::composer3::PresentFence _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::PresentFence>) {
      set<presentFence>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<presentFence>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case releaseFences: {
    ::aidl::android::hardware::graphics::composer3::ReleaseFences _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::ReleaseFences>) {
      set<releaseFences>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<releaseFences>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case presentOrValidateResult: {
    ::aidl::android::hardware::graphics::composer3::PresentOrValidate _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::PresentOrValidate>) {
      set<presentOrValidateResult>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<presentOrValidateResult>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  case clientTargetProperty: {
    ::aidl::android::hardware::graphics::composer3::ClientTargetPropertyWithBrightness _aidl_value;
    if ((_aidl_ret_status = ::ndk::AParcel_readData(_parcel, &_aidl_value)) != STATUS_OK) return _aidl_ret_status;
    if constexpr (std::is_trivially_copyable_v<::aidl::android::hardware::graphics::composer3::ClientTargetPropertyWithBrightness>) {
      set<clientTargetProperty>(_aidl_value);
    } else {
      // NOLINTNEXTLINE(performance-move-const-arg)
      set<clientTargetProperty>(std::move(_aidl_value));
    }
    return STATUS_OK; }
  }
  return STATUS_BAD_VALUE;
}
binder_status_t CommandResultPayload::writeToParcel(AParcel* _parcel) const {
  binder_status_t _aidl_ret_status = ::ndk::AParcel_writeData(_parcel, static_cast<int32_t>(getTag()));
  if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;
  switch (getTag()) {
  case error: return ::ndk::AParcel_writeData(_parcel, get<error>());
  case changedCompositionTypes: return ::ndk::AParcel_writeData(_parcel, get<changedCompositionTypes>());
  case displayRequest: return ::ndk::AParcel_writeData(_parcel, get<displayRequest>());
  case presentFence: return ::ndk::AParcel_writeData(_parcel, get<presentFence>());
  case releaseFences: return ::ndk::AParcel_writeData(_parcel, get<releaseFences>());
  case presentOrValidateResult: return ::ndk::AParcel_writeData(_parcel, get<presentOrValidateResult>());
  case clientTargetProperty: return ::ndk::AParcel_writeData(_parcel, get<clientTargetProperty>());
  }
  __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "can't reach here");
}

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
