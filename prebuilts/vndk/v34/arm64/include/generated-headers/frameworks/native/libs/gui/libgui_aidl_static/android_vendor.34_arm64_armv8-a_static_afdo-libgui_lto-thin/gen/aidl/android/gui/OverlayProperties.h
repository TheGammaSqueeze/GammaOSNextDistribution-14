#pragma once

#include <android/binder_to_string.h>
#include <android/gui/OverlayProperties.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android {
namespace gui {
class OverlayProperties : public ::android::Parcelable {
public:
  class SupportedBufferCombinations : public ::android::Parcelable {
  public:
    ::std::vector<int32_t> pixelFormats;
    ::std::vector<int32_t> standards;
    ::std::vector<int32_t> transfers;
    ::std::vector<int32_t> ranges;
    inline bool operator!=(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) != std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }
    inline bool operator<(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) < std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }
    inline bool operator<=(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) <= std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }
    inline bool operator==(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) == std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }
    inline bool operator>(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) > std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }
    inline bool operator>=(const SupportedBufferCombinations& rhs) const {
      return std::tie(pixelFormats, standards, transfers, ranges) >= std::tie(rhs.pixelFormats, rhs.standards, rhs.transfers, rhs.ranges);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.gui.OverlayProperties.SupportedBufferCombinations");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream os;
      os << "SupportedBufferCombinations{";
      os << "pixelFormats: " << ::android::internal::ToString(pixelFormats);
      os << ", standards: " << ::android::internal::ToString(standards);
      os << ", transfers: " << ::android::internal::ToString(transfers);
      os << ", ranges: " << ::android::internal::ToString(ranges);
      os << "}";
      return os.str();
    }
  };  // class SupportedBufferCombinations
  ::std::vector<::android::gui::OverlayProperties::SupportedBufferCombinations> combinations;
  bool supportMixedColorSpaces = false;
  inline bool operator!=(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) != std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }
  inline bool operator<(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) < std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }
  inline bool operator<=(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) <= std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }
  inline bool operator==(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) == std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }
  inline bool operator>(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) > std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }
  inline bool operator>=(const OverlayProperties& rhs) const {
    return std::tie(combinations, supportMixedColorSpaces) >= std::tie(rhs.combinations, rhs.supportMixedColorSpaces);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.OverlayProperties");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "OverlayProperties{";
    os << "combinations: " << ::android::internal::ToString(combinations);
    os << ", supportMixedColorSpaces: " << ::android::internal::ToString(supportMixedColorSpaces);
    os << "}";
    return os.str();
  }
};  // class OverlayProperties
}  // namespace gui
}  // namespace android
