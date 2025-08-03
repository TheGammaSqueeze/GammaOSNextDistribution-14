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
#include <aidl/android/hardware/radio/network/CellIdentityCdma.h>
#include <aidl/android/hardware/radio/network/CellIdentityGsm.h>
#include <aidl/android/hardware/radio/network/CellIdentityLte.h>
#include <aidl/android/hardware/radio/network/CellIdentityNr.h>
#include <aidl/android/hardware/radio/network/CellIdentityTdscdma.h>
#include <aidl/android/hardware/radio/network/CellIdentityWcdma.h>
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
class CellIdentity {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    noinit = 0,
    gsm = 1,
    wcdma = 2,
    tdscdma = 3,
    cdma = 4,
    lte = 5,
    nr = 6,
  };

  // Expose tag symbols for legacy code
  static const inline Tag noinit = Tag::noinit;
  static const inline Tag gsm = Tag::gsm;
  static const inline Tag wcdma = Tag::wcdma;
  static const inline Tag tdscdma = Tag::tdscdma;
  static const inline Tag cdma = Tag::cdma;
  static const inline Tag lte = Tag::lte;
  static const inline Tag nr = Tag::nr;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, CellIdentity>;

  CellIdentity() : _value(std::in_place_index<static_cast<size_t>(noinit)>, bool(false)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr CellIdentity(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit CellIdentity(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static CellIdentity make(_Tp&&... _args) {
    return CellIdentity(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static CellIdentity make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return CellIdentity(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const CellIdentity& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const CellIdentity& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const CellIdentity& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const CellIdentity& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const CellIdentity& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const CellIdentity& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellIdentity{";
    switch (getTag()) {
    case noinit: os << "noinit: " << ::android::internal::ToString(get<noinit>()); break;
    case gsm: os << "gsm: " << ::android::internal::ToString(get<gsm>()); break;
    case wcdma: os << "wcdma: " << ::android::internal::ToString(get<wcdma>()); break;
    case tdscdma: os << "tdscdma: " << ::android::internal::ToString(get<tdscdma>()); break;
    case cdma: os << "cdma: " << ::android::internal::ToString(get<cdma>()); break;
    case lte: os << "lte: " << ::android::internal::ToString(get<lte>()); break;
    case nr: os << "nr: " << ::android::internal::ToString(get<nr>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<bool, ::aidl::android::hardware::radio::network::CellIdentityGsm, ::aidl::android::hardware::radio::network::CellIdentityWcdma, ::aidl::android::hardware::radio::network::CellIdentityTdscdma, ::aidl::android::hardware::radio::network::CellIdentityCdma, ::aidl::android::hardware::radio::network::CellIdentityLte, ::aidl::android::hardware::radio::network::CellIdentityNr> _value;
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
[[nodiscard]] static inline std::string toString(CellIdentity::Tag val) {
  switch(val) {
  case CellIdentity::Tag::noinit:
    return "noinit";
  case CellIdentity::Tag::gsm:
    return "gsm";
  case CellIdentity::Tag::wcdma:
    return "wcdma";
  case CellIdentity::Tag::tdscdma:
    return "tdscdma";
  case CellIdentity::Tag::cdma:
    return "cdma";
  case CellIdentity::Tag::lte:
    return "lte";
  case CellIdentity::Tag::nr:
    return "nr";
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
constexpr inline std::array<aidl::android::hardware::radio::network::CellIdentity::Tag, 7> enum_values<aidl::android::hardware::radio::network::CellIdentity::Tag> = {
  aidl::android::hardware::radio::network::CellIdentity::Tag::noinit,
  aidl::android::hardware::radio::network::CellIdentity::Tag::gsm,
  aidl::android::hardware::radio::network::CellIdentity::Tag::wcdma,
  aidl::android::hardware::radio::network::CellIdentity::Tag::tdscdma,
  aidl::android::hardware::radio::network::CellIdentity::Tag::cdma,
  aidl::android::hardware::radio::network::CellIdentity::Tag::lte,
  aidl::android::hardware::radio::network::CellIdentity::Tag::nr,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
