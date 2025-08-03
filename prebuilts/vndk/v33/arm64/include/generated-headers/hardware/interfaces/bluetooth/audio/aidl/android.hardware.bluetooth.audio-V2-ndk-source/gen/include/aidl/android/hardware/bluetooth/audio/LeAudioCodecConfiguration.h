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
#include <aidl/android/hardware/bluetooth/audio/Lc3Configuration.h>
#include <aidl/android/hardware/bluetooth/audio/LeAudioCodecConfiguration.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
class LeAudioCodecConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class VendorConfiguration {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::ndk::AParcelableHolder extension{::ndk::STABILITY_VINTF};

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const VendorConfiguration& rhs) const {
      return std::tie(extension) != std::tie(rhs.extension);
    }
    inline bool operator<(const VendorConfiguration& rhs) const {
      return std::tie(extension) < std::tie(rhs.extension);
    }
    inline bool operator<=(const VendorConfiguration& rhs) const {
      return std::tie(extension) <= std::tie(rhs.extension);
    }
    inline bool operator==(const VendorConfiguration& rhs) const {
      return std::tie(extension) == std::tie(rhs.extension);
    }
    inline bool operator>(const VendorConfiguration& rhs) const {
      return std::tie(extension) > std::tie(rhs.extension);
    }
    inline bool operator>=(const VendorConfiguration& rhs) const {
      return std::tie(extension) >= std::tie(rhs.extension);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "VendorConfiguration{";
      os << "extension: " << ::android::internal::ToString(extension);
      os << "}";
      return os.str();
    }
  };
  enum class Tag : int32_t {
    lc3Config = 0,
    vendorConfig = 1,
  };

  // Expose tag symbols for legacy code
  static const inline Tag lc3Config = Tag::lc3Config;
  static const inline Tag vendorConfig = Tag::vendorConfig;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, LeAudioCodecConfiguration>;

  LeAudioCodecConfiguration() : _value(std::in_place_index<static_cast<size_t>(lc3Config)>, ::aidl::android::hardware::bluetooth::audio::Lc3Configuration()) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr LeAudioCodecConfiguration(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit LeAudioCodecConfiguration(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static LeAudioCodecConfiguration make(_Tp&&... _args) {
    return LeAudioCodecConfiguration(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static LeAudioCodecConfiguration make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return LeAudioCodecConfiguration(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const LeAudioCodecConfiguration& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const LeAudioCodecConfiguration& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const LeAudioCodecConfiguration& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const LeAudioCodecConfiguration& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const LeAudioCodecConfiguration& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const LeAudioCodecConfiguration& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LeAudioCodecConfiguration{";
    switch (getTag()) {
    case lc3Config: os << "lc3Config: " << ::android::internal::ToString(get<lc3Config>()); break;
    case vendorConfig: os << "vendorConfig: " << ::android::internal::ToString(get<vendorConfig>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<::aidl::android::hardware::bluetooth::audio::Lc3Configuration, ::aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration::VendorConfiguration> _value;
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
[[nodiscard]] static inline std::string toString(LeAudioCodecConfiguration::Tag val) {
  switch(val) {
  case LeAudioCodecConfiguration::Tag::lc3Config:
    return "lc3Config";
  case LeAudioCodecConfiguration::Tag::vendorConfig:
    return "vendorConfig";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration::Tag, 2> enum_values<aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration::Tag> = {
  aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration::Tag::lc3Config,
  aidl::android::hardware::bluetooth::audio::LeAudioCodecConfiguration::Tag::vendorConfig,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
