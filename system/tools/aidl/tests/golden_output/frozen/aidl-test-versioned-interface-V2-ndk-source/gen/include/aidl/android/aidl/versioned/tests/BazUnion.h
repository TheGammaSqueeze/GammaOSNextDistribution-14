/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk --structured --version 2 --hash da8c4bc94ca7feff0e0a65563a466787698b5891 --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-ndk-source/gen/staging/android/aidl/versioned/tests/BazUnion.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-ndk-source/gen/staging -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/2 system/tools/aidl/aidl_api/aidl-test-versioned-interface/2/android/aidl/versioned/tests/BazUnion.aidl
 */
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
namespace aidl {
namespace versioned {
namespace tests {
class BazUnion {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    intNum = 0,
    longNum = 1,
  };

  // Expose tag symbols for legacy code
  static const inline Tag intNum = Tag::intNum;
  static const inline Tag longNum = Tag::longNum;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, BazUnion>;

  BazUnion() : _value(std::in_place_index<static_cast<size_t>(intNum)>, int32_t(0)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr BazUnion(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit BazUnion(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static BazUnion make(_Tp&&... _args) {
    return BazUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static BazUnion make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return BazUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator==(const BazUnion& _rhs) const {
    return _value == _rhs._value;
  }
  inline bool operator<(const BazUnion& _rhs) const {
    return _value < _rhs._value;
  }
  inline bool operator!=(const BazUnion& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const BazUnion& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const BazUnion& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const BazUnion& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BazUnion{";
    switch (getTag()) {
    case intNum: os << "intNum: " << ::android::internal::ToString(get<intNum>()); break;
    case longNum: os << "longNum: " << ::android::internal::ToString(get<longNum>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<int32_t, int64_t> _value;
};
}  // namespace tests
}  // namespace versioned
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace versioned {
namespace tests {
[[nodiscard]] static inline std::string toString(BazUnion::Tag val) {
  switch(val) {
  case BazUnion::Tag::intNum:
    return "intNum";
  case BazUnion::Tag::longNum:
    return "longNum";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace tests
}  // namespace versioned
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::versioned::tests::BazUnion::Tag, 2> enum_values<aidl::android::aidl::versioned::tests::BazUnion::Tag> = {
  aidl::android::aidl::versioned::tests::BazUnion::Tag::intNum,
  aidl::android::aidl::versioned::tests::BazUnion::Tag::longNum,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
