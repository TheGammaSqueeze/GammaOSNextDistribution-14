#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>

#include <cassert>
#include <type_traits>
#include <utility>
#include <variant>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/security/keymint/Algorithm.h>
#include <aidl/android/hardware/security/keymint/BlockMode.h>
#include <aidl/android/hardware/security/keymint/Digest.h>
#include <aidl/android/hardware/security/keymint/EcCurve.h>
#include <aidl/android/hardware/security/keymint/HardwareAuthenticatorType.h>
#include <aidl/android/hardware/security/keymint/KeyOrigin.h>
#include <aidl/android/hardware/security/keymint/KeyPurpose.h>
#include <aidl/android/hardware/security/keymint/PaddingMode.h>
#include <aidl/android/hardware/security/keymint/SecurityLevel.h>

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class KeyParameterValue {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum Tag : int32_t {
    invalid = 0,  // int invalid;
    algorithm,  // android.hardware.security.keymint.Algorithm algorithm;
    blockMode,  // android.hardware.security.keymint.BlockMode blockMode;
    paddingMode,  // android.hardware.security.keymint.PaddingMode paddingMode;
    digest,  // android.hardware.security.keymint.Digest digest;
    ecCurve,  // android.hardware.security.keymint.EcCurve ecCurve;
    origin,  // android.hardware.security.keymint.KeyOrigin origin;
    keyPurpose,  // android.hardware.security.keymint.KeyPurpose keyPurpose;
    hardwareAuthenticatorType,  // android.hardware.security.keymint.HardwareAuthenticatorType hardwareAuthenticatorType;
    securityLevel,  // android.hardware.security.keymint.SecurityLevel securityLevel;
    boolValue,  // boolean boolValue;
    integer,  // int integer;
    longInteger,  // long longInteger;
    dateTime,  // long dateTime;
    blob,  // byte[] blob;
  };

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, KeyParameterValue>;

  KeyParameterValue() : _value(std::in_place_index<invalid>, int32_t(0)) { }
  KeyParameterValue(const KeyParameterValue&) = default;
  KeyParameterValue(KeyParameterValue&&) = default;
  KeyParameterValue& operator=(const KeyParameterValue&) = default;
  KeyParameterValue& operator=(KeyParameterValue&&) = default;

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr KeyParameterValue(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit KeyParameterValue(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static KeyParameterValue make(_Tp&&... _args) {
    return KeyParameterValue(std::in_place_index<_tag>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static KeyParameterValue make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return KeyParameterValue(std::in_place_index<_tag>, std::move(_il), std::forward<_Up>(_args)...);
  }

  Tag getTag() const {
    return static_cast<Tag>(_value.index());
  }

  template <Tag _tag>
  const auto& get() const {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<_tag>(_value);
  }

  template <Tag _tag>
  auto& get() {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<_tag>(_value);
  }

  template <Tag _tag, typename... _Tp>
  void set(_Tp&&... _args) {
    _value.emplace<_tag>(std::forward<_Tp>(_args)...);
  }

  binder_status_t readFromParcel(const AParcel* _parcel);
  binder_status_t writeToParcel(AParcel* _parcel) const;

  inline bool operator!=(const KeyParameterValue& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const KeyParameterValue& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const KeyParameterValue& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const KeyParameterValue& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const KeyParameterValue& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const KeyParameterValue& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyParameterValue{";
    switch (getTag()) {
    case invalid: os << "invalid: " << ::android::internal::ToString(get<invalid>()); break;
    case algorithm: os << "algorithm: " << ::android::internal::ToString(get<algorithm>()); break;
    case blockMode: os << "blockMode: " << ::android::internal::ToString(get<blockMode>()); break;
    case paddingMode: os << "paddingMode: " << ::android::internal::ToString(get<paddingMode>()); break;
    case digest: os << "digest: " << ::android::internal::ToString(get<digest>()); break;
    case ecCurve: os << "ecCurve: " << ::android::internal::ToString(get<ecCurve>()); break;
    case origin: os << "origin: " << ::android::internal::ToString(get<origin>()); break;
    case keyPurpose: os << "keyPurpose: " << ::android::internal::ToString(get<keyPurpose>()); break;
    case hardwareAuthenticatorType: os << "hardwareAuthenticatorType: " << ::android::internal::ToString(get<hardwareAuthenticatorType>()); break;
    case securityLevel: os << "securityLevel: " << ::android::internal::ToString(get<securityLevel>()); break;
    case boolValue: os << "boolValue: " << ::android::internal::ToString(get<boolValue>()); break;
    case integer: os << "integer: " << ::android::internal::ToString(get<integer>()); break;
    case longInteger: os << "longInteger: " << ::android::internal::ToString(get<longInteger>()); break;
    case dateTime: os << "dateTime: " << ::android::internal::ToString(get<dateTime>()); break;
    case blob: os << "blob: " << ::android::internal::ToString(get<blob>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<int32_t, ::aidl::android::hardware::security::keymint::Algorithm, ::aidl::android::hardware::security::keymint::BlockMode, ::aidl::android::hardware::security::keymint::PaddingMode, ::aidl::android::hardware::security::keymint::Digest, ::aidl::android::hardware::security::keymint::EcCurve, ::aidl::android::hardware::security::keymint::KeyOrigin, ::aidl::android::hardware::security::keymint::KeyPurpose, ::aidl::android::hardware::security::keymint::HardwareAuthenticatorType, ::aidl::android::hardware::security::keymint::SecurityLevel, bool, int32_t, int64_t, int64_t, std::vector<uint8_t>> _value;
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
