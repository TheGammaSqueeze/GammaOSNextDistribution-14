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
#include <aidl/android/hardware/sensors/AdditionalInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class AdditionalInfo {
public:
  typedef std::true_type fixed_size;
  static const char* descriptor;

  class AdditionalInfoPayload {
  public:
    typedef std::true_type fixed_size;
    static const char* descriptor;

    class Int32Values {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      std::array<int32_t, 14> values __attribute__((aligned (4))) = {{}};

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Int32Values& rhs) const {
        return std::tie(values) != std::tie(rhs.values);
      }
      inline bool operator<(const Int32Values& rhs) const {
        return std::tie(values) < std::tie(rhs.values);
      }
      inline bool operator<=(const Int32Values& rhs) const {
        return std::tie(values) <= std::tie(rhs.values);
      }
      inline bool operator==(const Int32Values& rhs) const {
        return std::tie(values) == std::tie(rhs.values);
      }
      inline bool operator>(const Int32Values& rhs) const {
        return std::tie(values) > std::tie(rhs.values);
      }
      inline bool operator>=(const Int32Values& rhs) const {
        return std::tie(values) >= std::tie(rhs.values);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Int32Values{";
        os << "values: " << ::android::internal::ToString(values);
        os << "}";
        return os.str();
      }
    };
    class FloatValues {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      std::array<float, 14> values __attribute__((aligned (4))) = {{}};

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const FloatValues& rhs) const {
        return std::tie(values) != std::tie(rhs.values);
      }
      inline bool operator<(const FloatValues& rhs) const {
        return std::tie(values) < std::tie(rhs.values);
      }
      inline bool operator<=(const FloatValues& rhs) const {
        return std::tie(values) <= std::tie(rhs.values);
      }
      inline bool operator==(const FloatValues& rhs) const {
        return std::tie(values) == std::tie(rhs.values);
      }
      inline bool operator>(const FloatValues& rhs) const {
        return std::tie(values) > std::tie(rhs.values);
      }
      inline bool operator>=(const FloatValues& rhs) const {
        return std::tie(values) >= std::tie(rhs.values);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "FloatValues{";
        os << "values: " << ::android::internal::ToString(values);
        os << "}";
        return os.str();
      }
    };
    enum class Tag : int8_t {
      dataInt32 = 0,
      dataFloat = 1,
    };

    // Expose tag symbols for legacy code
    static const inline Tag dataInt32 = Tag::dataInt32;
    static const inline Tag dataFloat = Tag::dataFloat;

    template <Tag _Tag>
    using _at = typename std::tuple_element<static_cast<size_t>(_Tag), std::tuple<::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Int32Values, ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::FloatValues>>::type;
    template <Tag _Tag, typename _Type>
    static AdditionalInfoPayload make(_Type&& _arg) {
      AdditionalInfoPayload _inst;
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

    static int _cmp(const AdditionalInfoPayload& _lhs, const AdditionalInfoPayload& _rhs) {
      return _cmp_value(_lhs.getTag(), _rhs.getTag()) || _cmp_value_at<dataFloat>(_lhs, _rhs);
    }
    template <Tag _Tag>
    static int _cmp_value_at(const AdditionalInfoPayload& _lhs, const AdditionalInfoPayload& _rhs) {
      if constexpr (_Tag == dataInt32) {
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
    inline bool operator!=(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) != 0;
    }
    inline bool operator<(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) < 0;
    }
    inline bool operator<=(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) <= 0;
    }
    inline bool operator==(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) == 0;
    }
    inline bool operator>(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) > 0;
    }
    inline bool operator>=(const AdditionalInfoPayload&_rhs) const {
      return _cmp(*this, _rhs) >= 0;
    }
    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "AdditionalInfoPayload{";
      switch (getTag()) {
      case dataInt32: os << "dataInt32: " << ::android::internal::ToString(get<dataInt32>()); break;
      case dataFloat: os << "dataFloat: " << ::android::internal::ToString(get<dataFloat>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    Tag _tag = dataInt32;
    union _value_t {
      _value_t() {}
      ~_value_t() {}
      ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Int32Values dataInt32 = ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Int32Values();
      ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::FloatValues dataFloat;
    } _value;
  };
  enum class AdditionalInfoType : int32_t {
    AINFO_BEGIN = 0,
    AINFO_END = 1,
    AINFO_UNTRACKED_DELAY = 65536,
    AINFO_INTERNAL_TEMPERATURE = 65537,
    AINFO_VEC3_CALIBRATION = 65538,
    AINFO_SENSOR_PLACEMENT = 65539,
    AINFO_SAMPLING = 65540,
    AINFO_CHANNEL_NOISE = 131072,
    AINFO_CHANNEL_SAMPLER = 131073,
    AINFO_CHANNEL_FILTER = 131074,
    AINFO_CHANNEL_LINEAR_TRANSFORM = 131075,
    AINFO_CHANNEL_NONLINEAR_MAP = 131076,
    AINFO_CHANNEL_RESAMPLER = 131077,
    AINFO_LOCAL_GEOMAGNETIC_FIELD = 196608,
    AINFO_LOCAL_GRAVITY = 196609,
    AINFO_DOCK_STATE = 196610,
    AINFO_HIGH_PERFORMANCE_MODE = 196611,
    AINFO_MAGNETIC_FIELD_CALIBRATION = 196612,
    AINFO_CUSTOM_START = 268435456,
    AINFO_DEBUGGING_START = 1073741824,
  };

  ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType type __attribute__((aligned (4))) = ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType(0);
  int32_t serial __attribute__((aligned (4))) = 0;
  ::aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload payload;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) != std::tie(rhs.type, rhs.serial, rhs.payload);
  }
  inline bool operator<(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) < std::tie(rhs.type, rhs.serial, rhs.payload);
  }
  inline bool operator<=(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) <= std::tie(rhs.type, rhs.serial, rhs.payload);
  }
  inline bool operator==(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) == std::tie(rhs.type, rhs.serial, rhs.payload);
  }
  inline bool operator>(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) > std::tie(rhs.type, rhs.serial, rhs.payload);
  }
  inline bool operator>=(const AdditionalInfo& rhs) const {
    return std::tie(type, serial, payload) >= std::tie(rhs.type, rhs.serial, rhs.payload);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AdditionalInfo{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", serial: " << ::android::internal::ToString(serial);
    os << ", payload: " << ::android::internal::ToString(payload);
    os << "}";
    return os.str();
  }
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(AdditionalInfo::AdditionalInfoPayload::Tag val) {
  switch(val) {
  case AdditionalInfo::AdditionalInfoPayload::Tag::dataInt32:
    return "dataInt32";
  case AdditionalInfo::AdditionalInfoPayload::Tag::dataFloat:
    return "dataFloat";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Tag, 2> enum_values<aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Tag> = {
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Tag::dataInt32,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoPayload::Tag::dataFloat,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(AdditionalInfo::AdditionalInfoType val) {
  switch(val) {
  case AdditionalInfo::AdditionalInfoType::AINFO_BEGIN:
    return "AINFO_BEGIN";
  case AdditionalInfo::AdditionalInfoType::AINFO_END:
    return "AINFO_END";
  case AdditionalInfo::AdditionalInfoType::AINFO_UNTRACKED_DELAY:
    return "AINFO_UNTRACKED_DELAY";
  case AdditionalInfo::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE:
    return "AINFO_INTERNAL_TEMPERATURE";
  case AdditionalInfo::AdditionalInfoType::AINFO_VEC3_CALIBRATION:
    return "AINFO_VEC3_CALIBRATION";
  case AdditionalInfo::AdditionalInfoType::AINFO_SENSOR_PLACEMENT:
    return "AINFO_SENSOR_PLACEMENT";
  case AdditionalInfo::AdditionalInfoType::AINFO_SAMPLING:
    return "AINFO_SAMPLING";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_NOISE:
    return "AINFO_CHANNEL_NOISE";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_SAMPLER:
    return "AINFO_CHANNEL_SAMPLER";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_FILTER:
    return "AINFO_CHANNEL_FILTER";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM:
    return "AINFO_CHANNEL_LINEAR_TRANSFORM";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP:
    return "AINFO_CHANNEL_NONLINEAR_MAP";
  case AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER:
    return "AINFO_CHANNEL_RESAMPLER";
  case AdditionalInfo::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD:
    return "AINFO_LOCAL_GEOMAGNETIC_FIELD";
  case AdditionalInfo::AdditionalInfoType::AINFO_LOCAL_GRAVITY:
    return "AINFO_LOCAL_GRAVITY";
  case AdditionalInfo::AdditionalInfoType::AINFO_DOCK_STATE:
    return "AINFO_DOCK_STATE";
  case AdditionalInfo::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE:
    return "AINFO_HIGH_PERFORMANCE_MODE";
  case AdditionalInfo::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION:
    return "AINFO_MAGNETIC_FIELD_CALIBRATION";
  case AdditionalInfo::AdditionalInfoType::AINFO_CUSTOM_START:
    return "AINFO_CUSTOM_START";
  case AdditionalInfo::AdditionalInfoType::AINFO_DEBUGGING_START:
    return "AINFO_DEBUGGING_START";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType, 20> enum_values<aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType> = {
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_BEGIN,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_END,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_UNTRACKED_DELAY,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_VEC3_CALIBRATION,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_SENSOR_PLACEMENT,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_SAMPLING,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_NOISE,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_SAMPLER,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_FILTER,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_LOCAL_GRAVITY,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_DOCK_STATE,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_CUSTOM_START,
  aidl::android::hardware::sensors::AdditionalInfo::AdditionalInfoType::AINFO_DEBUGGING_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
