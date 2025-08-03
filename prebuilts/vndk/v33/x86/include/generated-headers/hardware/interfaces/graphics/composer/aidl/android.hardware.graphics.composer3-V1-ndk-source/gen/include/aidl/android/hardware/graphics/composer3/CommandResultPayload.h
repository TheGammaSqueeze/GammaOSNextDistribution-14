#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/ChangedCompositionTypes.h>
#include <aidl/android/hardware/graphics/composer3/ClientTargetPropertyWithBrightness.h>
#include <aidl/android/hardware/graphics/composer3/CommandError.h>
#include <aidl/android/hardware/graphics/composer3/DisplayRequest.h>
#include <aidl/android/hardware/graphics/composer3/PresentFence.h>
#include <aidl/android/hardware/graphics/composer3/PresentOrValidate.h>
#include <aidl/android/hardware/graphics/composer3/ReleaseFences.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class CommandResultPayload {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    error = 0,
    changedCompositionTypes = 1,
    displayRequest = 2,
    presentFence = 3,
    releaseFences = 4,
    presentOrValidateResult = 5,
    clientTargetProperty = 6,
  };

  // Expose tag symbols for legacy code
  static const inline Tag error = Tag::error;
  static const inline Tag changedCompositionTypes = Tag::changedCompositionTypes;
  static const inline Tag displayRequest = Tag::displayRequest;
  static const inline Tag presentFence = Tag::presentFence;
  static const inline Tag releaseFences = Tag::releaseFences;
  static const inline Tag presentOrValidateResult = Tag::presentOrValidateResult;
  static const inline Tag clientTargetProperty = Tag::clientTargetProperty;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, CommandResultPayload>;

  CommandResultPayload() : _value(std::in_place_index<static_cast<size_t>(error)>, ::aidl::android::hardware::graphics::composer3::CommandError()) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr CommandResultPayload(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit CommandResultPayload(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static CommandResultPayload make(_Tp&&... _args) {
    return CommandResultPayload(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static CommandResultPayload make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return CommandResultPayload(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
  }

  Tag getTag() const {
    return static_cast<Tag>(_value.index());
  }

  template <Tag _tag>
  const auto& get() const {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<static_cast<size_t>(_tag)>(_value);
  }

  template <Tag _tag>
  auto& get() {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<static_cast<size_t>(_tag)>(_value);
  }

  template <Tag _tag, typename... _Tp>
  void set(_Tp&&... _args) {
    _value.emplace<static_cast<size_t>(_tag)>(std::forward<_Tp>(_args)...);
  }

  binder_status_t readFromParcel(const AParcel* _parcel);
  binder_status_t writeToParcel(AParcel* _parcel) const;

  inline bool operator!=(const CommandResultPayload& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const CommandResultPayload& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const CommandResultPayload& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const CommandResultPayload& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const CommandResultPayload& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const CommandResultPayload& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CommandResultPayload{";
    switch (getTag()) {
    case error: os << "error: " << ::android::internal::ToString(get<error>()); break;
    case changedCompositionTypes: os << "changedCompositionTypes: " << ::android::internal::ToString(get<changedCompositionTypes>()); break;
    case displayRequest: os << "displayRequest: " << ::android::internal::ToString(get<displayRequest>()); break;
    case presentFence: os << "presentFence: " << ::android::internal::ToString(get<presentFence>()); break;
    case releaseFences: os << "releaseFences: " << ::android::internal::ToString(get<releaseFences>()); break;
    case presentOrValidateResult: os << "presentOrValidateResult: " << ::android::internal::ToString(get<presentOrValidateResult>()); break;
    case clientTargetProperty: os << "clientTargetProperty: " << ::android::internal::ToString(get<clientTargetProperty>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<::aidl::android::hardware::graphics::composer3::CommandError, ::aidl::android::hardware::graphics::composer3::ChangedCompositionTypes, ::aidl::android::hardware::graphics::composer3::DisplayRequest, ::aidl::android::hardware::graphics::composer3::PresentFence, ::aidl::android::hardware::graphics::composer3::ReleaseFences, ::aidl::android::hardware::graphics::composer3::PresentOrValidate, ::aidl::android::hardware::graphics::composer3::ClientTargetPropertyWithBrightness> _value;
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
[[nodiscard]] static inline std::string toString(CommandResultPayload::Tag val) {
  switch(val) {
  case CommandResultPayload::Tag::error:
    return "error";
  case CommandResultPayload::Tag::changedCompositionTypes:
    return "changedCompositionTypes";
  case CommandResultPayload::Tag::displayRequest:
    return "displayRequest";
  case CommandResultPayload::Tag::presentFence:
    return "presentFence";
  case CommandResultPayload::Tag::releaseFences:
    return "releaseFences";
  case CommandResultPayload::Tag::presentOrValidateResult:
    return "presentOrValidateResult";
  case CommandResultPayload::Tag::clientTargetProperty:
    return "clientTargetProperty";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag, 7> enum_values<aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag> = {
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::error,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::changedCompositionTypes,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::displayRequest,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::presentFence,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::releaseFences,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::presentOrValidateResult,
  aidl::android::hardware::graphics::composer3::CommandResultPayload::Tag::clientTargetProperty,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
