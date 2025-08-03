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
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class DrmMetricValue {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    int64Value = 0,
    doubleValue = 1,
    stringValue = 2,
  };

  // Expose tag symbols for legacy code
  static const inline Tag int64Value = Tag::int64Value;
  static const inline Tag doubleValue = Tag::doubleValue;
  static const inline Tag stringValue = Tag::stringValue;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, DrmMetricValue>;

  DrmMetricValue() : _value(std::in_place_index<static_cast<size_t>(int64Value)>, int64_t(0L)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr DrmMetricValue(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit DrmMetricValue(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static DrmMetricValue make(_Tp&&... _args) {
    return DrmMetricValue(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static DrmMetricValue make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return DrmMetricValue(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const DrmMetricValue& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const DrmMetricValue& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const DrmMetricValue& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const DrmMetricValue& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const DrmMetricValue& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const DrmMetricValue& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DrmMetricValue{";
    switch (getTag()) {
    case int64Value: os << "int64Value: " << ::android::internal::ToString(get<int64Value>()); break;
    case doubleValue: os << "doubleValue: " << ::android::internal::ToString(get<doubleValue>()); break;
    case stringValue: os << "stringValue: " << ::android::internal::ToString(get<stringValue>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<int64_t, double, std::string> _value;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(DrmMetricValue::Tag val) {
  switch(val) {
  case DrmMetricValue::Tag::int64Value:
    return "int64Value";
  case DrmMetricValue::Tag::doubleValue:
    return "doubleValue";
  case DrmMetricValue::Tag::stringValue:
    return "stringValue";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::drm::DrmMetricValue::Tag, 3> enum_values<aidl::android::hardware::drm::DrmMetricValue::Tag> = {
  aidl::android::hardware::drm::DrmMetricValue::Tag::int64Value,
  aidl::android::hardware::drm::DrmMetricValue::Tag::doubleValue,
  aidl::android::hardware::drm::DrmMetricValue::Tag::stringValue,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
