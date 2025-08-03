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
#include <aidl/android/hardware/bluetooth/audio/AacCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/AptxAdaptiveCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/AptxCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/CodecCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/CodecType.h>
#include <aidl/android/hardware/bluetooth/audio/Lc3Capabilities.h>
#include <aidl/android/hardware/bluetooth/audio/LdacCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/OpusCapabilities.h>
#include <aidl/android/hardware/bluetooth/audio/SbcCapabilities.h>
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
class CodecCapabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class VendorCapabilities {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::ndk::AParcelableHolder extension{::ndk::STABILITY_VINTF};

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const VendorCapabilities& rhs) const {
      return std::tie(extension) != std::tie(rhs.extension);
    }
    inline bool operator<(const VendorCapabilities& rhs) const {
      return std::tie(extension) < std::tie(rhs.extension);
    }
    inline bool operator<=(const VendorCapabilities& rhs) const {
      return std::tie(extension) <= std::tie(rhs.extension);
    }
    inline bool operator==(const VendorCapabilities& rhs) const {
      return std::tie(extension) == std::tie(rhs.extension);
    }
    inline bool operator>(const VendorCapabilities& rhs) const {
      return std::tie(extension) > std::tie(rhs.extension);
    }
    inline bool operator>=(const VendorCapabilities& rhs) const {
      return std::tie(extension) >= std::tie(rhs.extension);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "VendorCapabilities{";
      os << "extension: " << ::android::internal::ToString(extension);
      os << "}";
      return os.str();
    }
  };
  class Capabilities {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    enum class Tag : int32_t {
      sbcCapabilities = 0,
      aacCapabilities = 1,
      ldacCapabilities = 2,
      aptxCapabilities = 3,
      aptxAdaptiveCapabilities = 4,
      lc3Capabilities = 5,
      vendorCapabilities = 6,
      opusCapabilities = 7,
    };

    // Expose tag symbols for legacy code
    static const inline Tag sbcCapabilities = Tag::sbcCapabilities;
    static const inline Tag aacCapabilities = Tag::aacCapabilities;
    static const inline Tag ldacCapabilities = Tag::ldacCapabilities;
    static const inline Tag aptxCapabilities = Tag::aptxCapabilities;
    static const inline Tag aptxAdaptiveCapabilities = Tag::aptxAdaptiveCapabilities;
    static const inline Tag lc3Capabilities = Tag::lc3Capabilities;
    static const inline Tag vendorCapabilities = Tag::vendorCapabilities;
    static const inline Tag opusCapabilities = Tag::opusCapabilities;

    template<typename _Tp>
    static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, Capabilities>;

    Capabilities() : _value(std::in_place_index<static_cast<size_t>(sbcCapabilities)>, ::aidl::android::hardware::bluetooth::audio::SbcCapabilities()) { }

    template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
    // NOLINTNEXTLINE(google-explicit-constructor)
    constexpr Capabilities(_Tp&& _arg)
        : _value(std::forward<_Tp>(_arg)) {}

    template <size_t _Np, typename... _Tp>
    constexpr explicit Capabilities(std::in_place_index_t<_Np>, _Tp&&... _args)
        : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

    template <Tag _tag, typename... _Tp>
    static Capabilities make(_Tp&&... _args) {
      return Capabilities(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
    }

    template <Tag _tag, typename _Tp, typename... _Up>
    static Capabilities make(std::initializer_list<_Tp> _il, _Up&&... _args) {
      return Capabilities(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

    inline bool operator!=(const Capabilities& rhs) const {
      return _value != rhs._value;
    }
    inline bool operator<(const Capabilities& rhs) const {
      return _value < rhs._value;
    }
    inline bool operator<=(const Capabilities& rhs) const {
      return _value <= rhs._value;
    }
    inline bool operator==(const Capabilities& rhs) const {
      return _value == rhs._value;
    }
    inline bool operator>(const Capabilities& rhs) const {
      return _value > rhs._value;
    }
    inline bool operator>=(const Capabilities& rhs) const {
      return _value >= rhs._value;
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Capabilities{";
      switch (getTag()) {
      case sbcCapabilities: os << "sbcCapabilities: " << ::android::internal::ToString(get<sbcCapabilities>()); break;
      case aacCapabilities: os << "aacCapabilities: " << ::android::internal::ToString(get<aacCapabilities>()); break;
      case ldacCapabilities: os << "ldacCapabilities: " << ::android::internal::ToString(get<ldacCapabilities>()); break;
      case aptxCapabilities: os << "aptxCapabilities: " << ::android::internal::ToString(get<aptxCapabilities>()); break;
      case aptxAdaptiveCapabilities: os << "aptxAdaptiveCapabilities: " << ::android::internal::ToString(get<aptxAdaptiveCapabilities>()); break;
      case lc3Capabilities: os << "lc3Capabilities: " << ::android::internal::ToString(get<lc3Capabilities>()); break;
      case vendorCapabilities: os << "vendorCapabilities: " << ::android::internal::ToString(get<vendorCapabilities>()); break;
      case opusCapabilities: os << "opusCapabilities: " << ::android::internal::ToString(get<opusCapabilities>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    std::variant<::aidl::android::hardware::bluetooth::audio::SbcCapabilities, ::aidl::android::hardware::bluetooth::audio::AacCapabilities, ::aidl::android::hardware::bluetooth::audio::LdacCapabilities, ::aidl::android::hardware::bluetooth::audio::AptxCapabilities, ::aidl::android::hardware::bluetooth::audio::AptxAdaptiveCapabilities, ::aidl::android::hardware::bluetooth::audio::Lc3Capabilities, ::aidl::android::hardware::bluetooth::audio::CodecCapabilities::VendorCapabilities, std::optional<::aidl::android::hardware::bluetooth::audio::OpusCapabilities>> _value;
  };
  ::aidl::android::hardware::bluetooth::audio::CodecType codecType = ::aidl::android::hardware::bluetooth::audio::CodecType(0);
  ::aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities capabilities;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) != std::tie(rhs.codecType, rhs.capabilities);
  }
  inline bool operator<(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) < std::tie(rhs.codecType, rhs.capabilities);
  }
  inline bool operator<=(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) <= std::tie(rhs.codecType, rhs.capabilities);
  }
  inline bool operator==(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) == std::tie(rhs.codecType, rhs.capabilities);
  }
  inline bool operator>(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) > std::tie(rhs.codecType, rhs.capabilities);
  }
  inline bool operator>=(const CodecCapabilities& rhs) const {
    return std::tie(codecType, capabilities) >= std::tie(rhs.codecType, rhs.capabilities);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CodecCapabilities{";
    os << "codecType: " << ::android::internal::ToString(codecType);
    os << ", capabilities: " << ::android::internal::ToString(capabilities);
    os << "}";
    return os.str();
  }
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
[[nodiscard]] static inline std::string toString(CodecCapabilities::Capabilities::Tag val) {
  switch(val) {
  case CodecCapabilities::Capabilities::Tag::sbcCapabilities:
    return "sbcCapabilities";
  case CodecCapabilities::Capabilities::Tag::aacCapabilities:
    return "aacCapabilities";
  case CodecCapabilities::Capabilities::Tag::ldacCapabilities:
    return "ldacCapabilities";
  case CodecCapabilities::Capabilities::Tag::aptxCapabilities:
    return "aptxCapabilities";
  case CodecCapabilities::Capabilities::Tag::aptxAdaptiveCapabilities:
    return "aptxAdaptiveCapabilities";
  case CodecCapabilities::Capabilities::Tag::lc3Capabilities:
    return "lc3Capabilities";
  case CodecCapabilities::Capabilities::Tag::vendorCapabilities:
    return "vendorCapabilities";
  case CodecCapabilities::Capabilities::Tag::opusCapabilities:
    return "opusCapabilities";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag, 8> enum_values<aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag> = {
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::sbcCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::aacCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::ldacCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::aptxCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::aptxAdaptiveCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::lc3Capabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::vendorCapabilities,
  aidl::android::hardware::bluetooth::audio::CodecCapabilities::Capabilities::Tag::opusCapabilities,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
