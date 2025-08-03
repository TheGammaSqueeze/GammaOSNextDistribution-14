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
#include <aidl/android/hardware/bluetooth/audio/CodecConfiguration.h>
#include <aidl/android/hardware/bluetooth/audio/LeAudioBroadcastConfiguration.h>
#include <aidl/android/hardware/bluetooth/audio/LeAudioConfiguration.h>
#include <aidl/android/hardware/bluetooth/audio/PcmConfiguration.h>
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
class AudioConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    pcmConfig = 0,
    a2dpConfig = 1,
    leAudioConfig = 2,
    leAudioBroadcastConfig = 3,
  };

  // Expose tag symbols for legacy code
  static const inline Tag pcmConfig = Tag::pcmConfig;
  static const inline Tag a2dpConfig = Tag::a2dpConfig;
  static const inline Tag leAudioConfig = Tag::leAudioConfig;
  static const inline Tag leAudioBroadcastConfig = Tag::leAudioBroadcastConfig;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, AudioConfiguration>;

  AudioConfiguration() : _value(std::in_place_index<static_cast<size_t>(pcmConfig)>, ::aidl::android::hardware::bluetooth::audio::PcmConfiguration()) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr AudioConfiguration(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit AudioConfiguration(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static AudioConfiguration make(_Tp&&... _args) {
    return AudioConfiguration(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static AudioConfiguration make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return AudioConfiguration(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const AudioConfiguration& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const AudioConfiguration& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const AudioConfiguration& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const AudioConfiguration& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const AudioConfiguration& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const AudioConfiguration& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioConfiguration{";
    switch (getTag()) {
    case pcmConfig: os << "pcmConfig: " << ::android::internal::ToString(get<pcmConfig>()); break;
    case a2dpConfig: os << "a2dpConfig: " << ::android::internal::ToString(get<a2dpConfig>()); break;
    case leAudioConfig: os << "leAudioConfig: " << ::android::internal::ToString(get<leAudioConfig>()); break;
    case leAudioBroadcastConfig: os << "leAudioBroadcastConfig: " << ::android::internal::ToString(get<leAudioBroadcastConfig>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<::aidl::android::hardware::bluetooth::audio::PcmConfiguration, ::aidl::android::hardware::bluetooth::audio::CodecConfiguration, ::aidl::android::hardware::bluetooth::audio::LeAudioConfiguration, ::aidl::android::hardware::bluetooth::audio::LeAudioBroadcastConfiguration> _value;
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
[[nodiscard]] static inline std::string toString(AudioConfiguration::Tag val) {
  switch(val) {
  case AudioConfiguration::Tag::pcmConfig:
    return "pcmConfig";
  case AudioConfiguration::Tag::a2dpConfig:
    return "a2dpConfig";
  case AudioConfiguration::Tag::leAudioConfig:
    return "leAudioConfig";
  case AudioConfiguration::Tag::leAudioBroadcastConfig:
    return "leAudioBroadcastConfig";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag, 4> enum_values<aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag> = {
  aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag::pcmConfig,
  aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag::a2dpConfig,
  aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag::leAudioConfig,
  aidl::android::hardware::bluetooth::audio::AudioConfiguration::Tag::leAudioBroadcastConfig,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
