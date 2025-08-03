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
#include <aidl/android/frameworks/cameraservice/device/CameraMetadata.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl::android::frameworks::cameraservice::device {
class CameraMetadata;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class CaptureMetadataInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    fmqMetadataSize = 0,
    metadata = 1,
  };

  // Expose tag symbols for legacy code
  static const inline Tag fmqMetadataSize = Tag::fmqMetadataSize;
  static const inline Tag metadata = Tag::metadata;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, CaptureMetadataInfo>;

  CaptureMetadataInfo() : _value(std::in_place_index<static_cast<size_t>(fmqMetadataSize)>, int64_t(0L)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr CaptureMetadataInfo(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit CaptureMetadataInfo(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static CaptureMetadataInfo make(_Tp&&... _args) {
    return CaptureMetadataInfo(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static CaptureMetadataInfo make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return CaptureMetadataInfo(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const CaptureMetadataInfo& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const CaptureMetadataInfo& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const CaptureMetadataInfo& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const CaptureMetadataInfo& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const CaptureMetadataInfo& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const CaptureMetadataInfo& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CaptureMetadataInfo{";
    switch (getTag()) {
    case fmqMetadataSize: os << "fmqMetadataSize: " << ::android::internal::ToString(get<fmqMetadataSize>()); break;
    case metadata: os << "metadata: " << ::android::internal::ToString(get<metadata>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<int64_t, ::aidl::android::frameworks::cameraservice::device::CameraMetadata> _value;
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
[[nodiscard]] static inline std::string toString(CaptureMetadataInfo::Tag val) {
  switch(val) {
  case CaptureMetadataInfo::Tag::fmqMetadataSize:
    return "fmqMetadataSize";
  case CaptureMetadataInfo::Tag::metadata:
    return "metadata";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo::Tag, 2> enum_values<aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo::Tag> = {
  aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo::Tag::fmqMetadataSize,
  aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo::Tag::metadata,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
