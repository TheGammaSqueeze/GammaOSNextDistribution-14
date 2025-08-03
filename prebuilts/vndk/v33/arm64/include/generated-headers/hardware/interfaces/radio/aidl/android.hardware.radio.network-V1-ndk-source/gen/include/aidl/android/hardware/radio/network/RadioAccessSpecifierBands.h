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
#include <aidl/android/hardware/radio/network/EutranBands.h>
#include <aidl/android/hardware/radio/network/GeranBands.h>
#include <aidl/android/hardware/radio/network/NgranBands.h>
#include <aidl/android/hardware/radio/network/UtranBands.h>
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
class RadioAccessSpecifierBands {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    noinit = 0,
    geranBands = 1,
    utranBands = 2,
    eutranBands = 3,
    ngranBands = 4,
  };

  // Expose tag symbols for legacy code
  static const inline Tag noinit = Tag::noinit;
  static const inline Tag geranBands = Tag::geranBands;
  static const inline Tag utranBands = Tag::utranBands;
  static const inline Tag eutranBands = Tag::eutranBands;
  static const inline Tag ngranBands = Tag::ngranBands;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, RadioAccessSpecifierBands>;

  RadioAccessSpecifierBands() : _value(std::in_place_index<static_cast<size_t>(noinit)>, bool(false)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr RadioAccessSpecifierBands(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit RadioAccessSpecifierBands(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static RadioAccessSpecifierBands make(_Tp&&... _args) {
    return RadioAccessSpecifierBands(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static RadioAccessSpecifierBands make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return RadioAccessSpecifierBands(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const RadioAccessSpecifierBands& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const RadioAccessSpecifierBands& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const RadioAccessSpecifierBands& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const RadioAccessSpecifierBands& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const RadioAccessSpecifierBands& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const RadioAccessSpecifierBands& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RadioAccessSpecifierBands{";
    switch (getTag()) {
    case noinit: os << "noinit: " << ::android::internal::ToString(get<noinit>()); break;
    case geranBands: os << "geranBands: " << ::android::internal::ToString(get<geranBands>()); break;
    case utranBands: os << "utranBands: " << ::android::internal::ToString(get<utranBands>()); break;
    case eutranBands: os << "eutranBands: " << ::android::internal::ToString(get<eutranBands>()); break;
    case ngranBands: os << "ngranBands: " << ::android::internal::ToString(get<ngranBands>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<bool, std::vector<::aidl::android::hardware::radio::network::GeranBands>, std::vector<::aidl::android::hardware::radio::network::UtranBands>, std::vector<::aidl::android::hardware::radio::network::EutranBands>, std::vector<::aidl::android::hardware::radio::network::NgranBands>> _value;
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
[[nodiscard]] static inline std::string toString(RadioAccessSpecifierBands::Tag val) {
  switch(val) {
  case RadioAccessSpecifierBands::Tag::noinit:
    return "noinit";
  case RadioAccessSpecifierBands::Tag::geranBands:
    return "geranBands";
  case RadioAccessSpecifierBands::Tag::utranBands:
    return "utranBands";
  case RadioAccessSpecifierBands::Tag::eutranBands:
    return "eutranBands";
  case RadioAccessSpecifierBands::Tag::ngranBands:
    return "ngranBands";
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
constexpr inline std::array<aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag, 5> enum_values<aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag> = {
  aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag::noinit,
  aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag::geranBands,
  aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag::utranBands,
  aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag::eutranBands,
  aidl::android::hardware::radio::network::RadioAccessSpecifierBands::Tag::ngranBands,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
