/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/Union.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/Union.aidl
 */
#pragma once

#include <android/aidl/tests/ByteEnum.h>
#include <android/binder_to_string.h>
#include <array>
#include <binder/Enums.h>
#include <binder/IBinder.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cassert>
#include <cstdint>
#include <string>
#include <type_traits>
#include <utility>
#include <utils/String16.h>
#include <variant>
#include <vector>

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace android {
namespace aidl {
namespace tests {
class Union : public ::android::Parcelable {
public:
  enum class Tag : int32_t {
    ns = 0,
    n = 1,
    m = 2,
    s = 3,
    ibinder = 4,
    ss = 5,
    be = 6,
  };
  // Expose tag symbols for legacy code
  static const inline Tag ns = Tag::ns;
  static const inline Tag n = Tag::n;
  static const inline Tag m = Tag::m;
  static const inline Tag s = Tag::s;
  static const inline Tag ibinder = Tag::ibinder;
  static const inline Tag ss = Tag::ss;
  static const inline Tag be = Tag::be;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, Union>;

  Union() : _value(std::in_place_index<static_cast<size_t>(ns)>, ::std::vector<int32_t>({})) { }

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

  static const ::std::string& S1();
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.Union");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "Union{";
    switch (getTag()) {
    case ns: os << "ns: " << ::android::internal::ToString(get<ns>()); break;
    case n: os << "n: " << ::android::internal::ToString(get<n>()); break;
    case m: os << "m: " << ::android::internal::ToString(get<m>()); break;
    case s: os << "s: " << ::android::internal::ToString(get<s>()); break;
    case ibinder: os << "ibinder: " << ::android::internal::ToString(get<ibinder>()); break;
    case ss: os << "ss: " << ::android::internal::ToString(get<ss>()); break;
    case be: os << "be: " << ::android::internal::ToString(get<be>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<::std::vector<int32_t>, int32_t, int32_t, ::std::string, ::android::sp<::android::IBinder>, ::std::vector<::std::string>, ::android::aidl::tests::ByteEnum> _value;
};  // class Union
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(Union::Tag val) {
  switch(val) {
  case Union::Tag::ns:
    return "ns";
  case Union::Tag::n:
    return "n";
  case Union::Tag::m:
    return "m";
  case Union::Tag::s:
    return "s";
  case Union::Tag::ibinder:
    return "ibinder";
  case Union::Tag::ss:
    return "ss";
  case Union::Tag::be:
    return "be";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::tests::Union::Tag, 7> enum_values<::android::aidl::tests::Union::Tag> = {
  ::android::aidl::tests::Union::Tag::ns,
  ::android::aidl::tests::Union::Tag::n,
  ::android::aidl::tests::Union::Tag::m,
  ::android::aidl::tests::Union::Tag::s,
  ::android::aidl::tests::Union::Tag::ibinder,
  ::android::aidl::tests::Union::Tag::ss,
  ::android::aidl::tests::Union::Tag::be,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
