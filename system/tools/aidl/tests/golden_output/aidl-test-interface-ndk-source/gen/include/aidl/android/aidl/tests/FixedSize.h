/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/FixedSize.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/FixedSize.aidl
 */
#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/FixedSize.h>
#include <aidl/android/aidl/tests/LongEnum.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class FixedSize {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class FixedUnion {
  public:
    typedef std::true_type fixed_size;
    static const char* descriptor;

    enum class Tag : int8_t {
      booleanValue = 0,
      byteValue = 1,
      charValue = 2,
      intValue = 3,
      longValue = 4,
      floatValue = 5,
      doubleValue = 6,
      enumValue = 7,
    };

    // Expose tag symbols for legacy code
    static const inline Tag booleanValue = Tag::booleanValue;
    static const inline Tag byteValue = Tag::byteValue;
    static const inline Tag charValue = Tag::charValue;
    static const inline Tag intValue = Tag::intValue;
    static const inline Tag longValue = Tag::longValue;
    static const inline Tag floatValue = Tag::floatValue;
    static const inline Tag doubleValue = Tag::doubleValue;
    static const inline Tag enumValue = Tag::enumValue;

    template <Tag _Tag>
    using _at = typename std::tuple_element<static_cast<size_t>(_Tag), std::tuple<bool, int8_t, char16_t, int32_t, int64_t, float, double, ::aidl::android::aidl::tests::LongEnum>>::type;
    template <Tag _Tag, typename _Type>
    static FixedUnion make(_Type&& _arg) {
      FixedUnion _inst;
      _inst.set<_Tag>(std::forward<_Type>(_arg));
      return _inst;
    }
    constexpr Tag getTag() const {
      return _tag;
    }
    template <Tag _Tag>
    const _at<_Tag>& get() const {
      if (_Tag != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return *(_at<_Tag>*)(&_value);
    }
    template <Tag _Tag>
    _at<_Tag>& get() {
      if (_Tag != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return *(_at<_Tag>*)(&_value);
    }
    template <Tag _Tag, typename _Type>
    void set(_Type&& _arg) {
      _tag = _Tag;
      get<_Tag>() = std::forward<_Type>(_arg);
    }
    binder_status_t readFromParcel(const AParcel* _parcel);
    binder_status_t writeToParcel(AParcel* _parcel) const;

    static int _cmp(const FixedUnion& _lhs, const FixedUnion& _rhs) {
      return _cmp_value(_lhs.getTag(), _rhs.getTag()) || _cmp_value_at<enumValue>(_lhs, _rhs);
    }
    template <Tag _Tag>
    static int _cmp_value_at(const FixedUnion& _lhs, const FixedUnion& _rhs) {
      if constexpr (_Tag == booleanValue) {
        return _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>());
      } else {
        return (_lhs.getTag() == _Tag)
          ? _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>())
          : _cmp_value_at<static_cast<Tag>(static_cast<size_t>(_Tag)-1)>(_lhs, _rhs);
      }
    }
    template <typename _Type>
    static int _cmp_value(const _Type& _lhs, const _Type& _rhs) {
      return (_lhs == _rhs) ? 0 : (_lhs < _rhs) ? -1 : 1;
    }
    inline bool operator!=(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) != 0;
    }
    inline bool operator<(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) < 0;
    }
    inline bool operator<=(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) <= 0;
    }
    inline bool operator==(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) == 0;
    }
    inline bool operator>(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) > 0;
    }
    inline bool operator>=(const FixedUnion&_rhs) const {
      return _cmp(*this, _rhs) >= 0;
    }
    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream os;
      os << "FixedUnion{";
      switch (getTag()) {
      case booleanValue: os << "booleanValue: " << ::android::internal::ToString(get<booleanValue>()); break;
      case byteValue: os << "byteValue: " << ::android::internal::ToString(get<byteValue>()); break;
      case charValue: os << "charValue: " << ::android::internal::ToString(get<charValue>()); break;
      case intValue: os << "intValue: " << ::android::internal::ToString(get<intValue>()); break;
      case longValue: os << "longValue: " << ::android::internal::ToString(get<longValue>()); break;
      case floatValue: os << "floatValue: " << ::android::internal::ToString(get<floatValue>()); break;
      case doubleValue: os << "doubleValue: " << ::android::internal::ToString(get<doubleValue>()); break;
      case enumValue: os << "enumValue: " << ::android::internal::ToString(get<enumValue>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    Tag _tag = booleanValue;
    union _value_t {
      _value_t() {}
      ~_value_t() {}
      bool booleanValue __attribute__((aligned (1))) = bool(false);
      int8_t byteValue __attribute__((aligned (1)));
      char16_t charValue __attribute__((aligned (2)));
      int32_t intValue __attribute__((aligned (4)));
      int64_t longValue __attribute__((aligned (8)));
      float floatValue __attribute__((aligned (4)));
      double doubleValue __attribute__((aligned (8)));
      ::aidl::android::aidl::tests::LongEnum enumValue __attribute__((aligned (8)));
    } _value;
  };
  class FixedParcelable {
  public:
    typedef std::true_type fixed_size;
    static const char* descriptor;

    bool booleanValue __attribute__((aligned (1))) = false;
    int8_t byteValue __attribute__((aligned (1))) = 0;
    char16_t charValue __attribute__((aligned (2))) = '\0';
    int32_t intValue __attribute__((aligned (4))) = 0;
    int64_t longValue __attribute__((aligned (8))) = 0L;
    float floatValue __attribute__((aligned (4))) = 0.000000f;
    double doubleValue __attribute__((aligned (8))) = 0.000000;
    ::aidl::android::aidl::tests::LongEnum enumValue __attribute__((aligned (8))) = ::aidl::android::aidl::tests::LongEnum::FOO;
    ::aidl::android::aidl::tests::FixedSize::FixedUnion parcelableValue;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator==(const FixedParcelable& _rhs) const {
      return std::tie(booleanValue, byteValue, charValue, intValue, longValue, floatValue, doubleValue, enumValue, parcelableValue) == std::tie(_rhs.booleanValue, _rhs.byteValue, _rhs.charValue, _rhs.intValue, _rhs.longValue, _rhs.floatValue, _rhs.doubleValue, _rhs.enumValue, _rhs.parcelableValue);
    }
    inline bool operator<(const FixedParcelable& _rhs) const {
      return std::tie(booleanValue, byteValue, charValue, intValue, longValue, floatValue, doubleValue, enumValue, parcelableValue) < std::tie(_rhs.booleanValue, _rhs.byteValue, _rhs.charValue, _rhs.intValue, _rhs.longValue, _rhs.floatValue, _rhs.doubleValue, _rhs.enumValue, _rhs.parcelableValue);
    }
    inline bool operator!=(const FixedParcelable& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const FixedParcelable& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const FixedParcelable& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const FixedParcelable& _rhs) const {
      return !(_rhs < *this);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "FixedParcelable{";
      _aidl_os << "booleanValue: " << ::android::internal::ToString(booleanValue);
      _aidl_os << ", byteValue: " << ::android::internal::ToString(byteValue);
      _aidl_os << ", charValue: " << ::android::internal::ToString(charValue);
      _aidl_os << ", intValue: " << ::android::internal::ToString(intValue);
      _aidl_os << ", longValue: " << ::android::internal::ToString(longValue);
      _aidl_os << ", floatValue: " << ::android::internal::ToString(floatValue);
      _aidl_os << ", doubleValue: " << ::android::internal::ToString(doubleValue);
      _aidl_os << ", enumValue: " << ::android::internal::ToString(enumValue);
      _aidl_os << ", parcelableValue: " << ::android::internal::ToString(parcelableValue);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const FixedSize&) const {
    return std::tie() == std::tie();
  }
  inline bool operator<(const FixedSize&) const {
    return std::tie() < std::tie();
  }
  inline bool operator!=(const FixedSize& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const FixedSize& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const FixedSize& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const FixedSize& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "FixedSize{";
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(FixedSize::FixedUnion::Tag val) {
  switch(val) {
  case FixedSize::FixedUnion::Tag::booleanValue:
    return "booleanValue";
  case FixedSize::FixedUnion::Tag::byteValue:
    return "byteValue";
  case FixedSize::FixedUnion::Tag::charValue:
    return "charValue";
  case FixedSize::FixedUnion::Tag::intValue:
    return "intValue";
  case FixedSize::FixedUnion::Tag::longValue:
    return "longValue";
  case FixedSize::FixedUnion::Tag::floatValue:
    return "floatValue";
  case FixedSize::FixedUnion::Tag::doubleValue:
    return "doubleValue";
  case FixedSize::FixedUnion::Tag::enumValue:
    return "enumValue";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::FixedSize::FixedUnion::Tag, 8> enum_values<aidl::android::aidl::tests::FixedSize::FixedUnion::Tag> = {
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::booleanValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::byteValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::charValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::intValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::longValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::floatValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::doubleValue,
  aidl::android::aidl::tests::FixedSize::FixedUnion::Tag::enumValue,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
