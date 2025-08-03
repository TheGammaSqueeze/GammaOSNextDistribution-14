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
#include <aidl/android/hardware/radio/network/Cdma2000RegistrationInfo.h>
#include <aidl/android/hardware/radio/network/EutranRegistrationInfo.h>
#include <aidl/android/hardware/radio/network/NrVopsInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class AccessTechnologySpecificInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    noinit = 0,
    cdmaInfo = 1,
    eutranInfo = 2,
    ngranNrVopsInfo = 3,
    geranDtmSupported = 4,
  };

  // Expose tag symbols for legacy code
  static const inline Tag noinit = Tag::noinit;
  static const inline Tag cdmaInfo = Tag::cdmaInfo;
  static const inline Tag eutranInfo = Tag::eutranInfo;
  static const inline Tag ngranNrVopsInfo = Tag::ngranNrVopsInfo;
  static const inline Tag geranDtmSupported = Tag::geranDtmSupported;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, AccessTechnologySpecificInfo>;

  AccessTechnologySpecificInfo() : _value(std::in_place_index<static_cast<size_t>(noinit)>, bool(false)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr AccessTechnologySpecificInfo(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit AccessTechnologySpecificInfo(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static AccessTechnologySpecificInfo make(_Tp&&... _args) {
    return AccessTechnologySpecificInfo(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static AccessTechnologySpecificInfo make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return AccessTechnologySpecificInfo(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const AccessTechnologySpecificInfo& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const AccessTechnologySpecificInfo& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const AccessTechnologySpecificInfo& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const AccessTechnologySpecificInfo& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const AccessTechnologySpecificInfo& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const AccessTechnologySpecificInfo& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AccessTechnologySpecificInfo{";
    switch (getTag()) {
    case noinit: os << "noinit: " << ::android::internal::ToString(get<noinit>()); break;
    case cdmaInfo: os << "cdmaInfo: " << ::android::internal::ToString(get<cdmaInfo>()); break;
    case eutranInfo: os << "eutranInfo: " << ::android::internal::ToString(get<eutranInfo>()); break;
    case ngranNrVopsInfo: os << "ngranNrVopsInfo: " << ::android::internal::ToString(get<ngranNrVopsInfo>()); break;
    case geranDtmSupported: os << "geranDtmSupported: " << ::android::internal::ToString(get<geranDtmSupported>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<bool, ::aidl::android::hardware::radio::network::Cdma2000RegistrationInfo, ::aidl::android::hardware::radio::network::EutranRegistrationInfo, ::aidl::android::hardware::radio::network::NrVopsInfo, bool> _value;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
[[nodiscard]] static inline std::string toString(AccessTechnologySpecificInfo::Tag val) {
  switch(val) {
  case AccessTechnologySpecificInfo::Tag::noinit:
    return "noinit";
  case AccessTechnologySpecificInfo::Tag::cdmaInfo:
    return "cdmaInfo";
  case AccessTechnologySpecificInfo::Tag::eutranInfo:
    return "eutranInfo";
  case AccessTechnologySpecificInfo::Tag::ngranNrVopsInfo:
    return "ngranNrVopsInfo";
  case AccessTechnologySpecificInfo::Tag::geranDtmSupported:
    return "geranDtmSupported";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag, 5> enum_values<aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag> = {
  aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag::noinit,
  aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag::cdmaInfo,
  aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag::eutranInfo,
  aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag::ngranNrVopsInfo,
  aidl::android::hardware::radio::network::AccessTechnologySpecificInfo::Tag::geranDtmSupported,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
