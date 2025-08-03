/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging/android/aidl/loggable/Union.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Union.aidl
 */
#pragma once

#include <android/binder_to_string.h>
#include <array>
#include <binder/Enums.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cassert>
#include <cstdint>
#include <string>
#include <type_traits>
#include <utility>
#include <utils/String16.h>
#include <variant>

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace android {
namespace aidl {
namespace loggable {
class Union : public ::android::Parcelable {
public:
  enum class Tag : int32_t {
    num = 0,
    str = 1,
  };
  // Expose tag symbols for legacy code
  static const inline Tag num = Tag::num;
  static const inline Tag str = Tag::str;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, Union>;

  Union() : _value(std::in_place_index<static_cast<size_t>(num)>, int32_t(43)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr Union(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit Union(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static Union make(_Tp&&... _args) {
    return Union(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static Union make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return Union(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator==(const Union& _rhs) const {
    return _value == _rhs._value;
  }
  inline bool operator<(const Union& _rhs) const {
    return _value < _rhs._value;
  }
  inline bool operator!=(const Union& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const Union& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const Union& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const Union& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.loggable.Union");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "Union{";
    switch (getTag()) {
    case num: os << "num: " << ::android::internal::ToString(get<num>()); break;
    case str: os << "str: " << ::android::internal::ToString(get<str>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<int32_t, ::std::string> _value;
};  // class Union
}  // namespace loggable
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace loggable {
[[nodiscard]] static inline std::string toString(Union::Tag val) {
  switch(val) {
  case Union::Tag::num:
    return "num";
  case Union::Tag::str:
    return "str";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace loggable
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::loggable::Union::Tag, 2> enum_values<::android::aidl::loggable::Union::Tag> = {
  ::android::aidl::loggable::Union::Tag::num,
  ::android::aidl::loggable::Union::Tag::str,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
