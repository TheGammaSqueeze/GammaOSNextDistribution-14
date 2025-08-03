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
#include <aidl/android/hardware/graphics/common/Hdr.h>
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
namespace common {
class HdrConversionStrategy {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    passthrough = 0,
    autoAllowedHdrTypes = 1,
    forceHdrConversion = 2,
  };

  // Expose tag symbols for legacy code
  static const inline Tag passthrough = Tag::passthrough;
  static const inline Tag autoAllowedHdrTypes = Tag::autoAllowedHdrTypes;
  static const inline Tag forceHdrConversion = Tag::forceHdrConversion;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, HdrConversionStrategy>;

  HdrConversionStrategy() : _value(std::in_place_index<static_cast<size_t>(passthrough)>, bool(true)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr HdrConversionStrategy(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit HdrConversionStrategy(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static HdrConversionStrategy make(_Tp&&... _args) {
    return HdrConversionStrategy(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static HdrConversionStrategy make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return HdrConversionStrategy(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const HdrConversionStrategy& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const HdrConversionStrategy& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const HdrConversionStrategy& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const HdrConversionStrategy& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const HdrConversionStrategy& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const HdrConversionStrategy& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HdrConversionStrategy{";
    switch (getTag()) {
    case passthrough: os << "passthrough: " << ::android::internal::ToString(get<passthrough>()); break;
    case autoAllowedHdrTypes: os << "autoAllowedHdrTypes: " << ::android::internal::ToString(get<autoAllowedHdrTypes>()); break;
    case forceHdrConversion: os << "forceHdrConversion: " << ::android::internal::ToString(get<forceHdrConversion>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<bool, std::vector<::aidl::android::hardware::graphics::common::Hdr>, ::aidl::android::hardware::graphics::common::Hdr> _value;
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
[[nodiscard]] static inline std::string toString(HdrConversionStrategy::Tag val) {
  switch(val) {
  case HdrConversionStrategy::Tag::passthrough:
    return "passthrough";
  case HdrConversionStrategy::Tag::autoAllowedHdrTypes:
    return "autoAllowedHdrTypes";
  case HdrConversionStrategy::Tag::forceHdrConversion:
    return "forceHdrConversion";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::common::HdrConversionStrategy::Tag, 3> enum_values<aidl::android::hardware::graphics::common::HdrConversionStrategy::Tag> = {
  aidl::android::hardware::graphics::common::HdrConversionStrategy::Tag::passthrough,
  aidl::android::hardware::graphics::common::HdrConversionStrategy::Tag::autoAllowedHdrTypes,
  aidl::android::hardware::graphics::common::HdrConversionStrategy::Tag::forceHdrConversion,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
