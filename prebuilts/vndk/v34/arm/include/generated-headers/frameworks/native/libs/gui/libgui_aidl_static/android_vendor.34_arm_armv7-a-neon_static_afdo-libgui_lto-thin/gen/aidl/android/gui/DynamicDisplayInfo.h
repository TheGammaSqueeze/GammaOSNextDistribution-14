#pragma once

#include <android/binder_to_string.h>
#include <android/gui/DisplayMode.h>
#include <android/gui/HdrCapabilities.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android::gui {
class DisplayMode;
class HdrCapabilities;
}  // namespace android::gui
namespace android {
namespace gui {
class DynamicDisplayInfo : public ::android::Parcelable {
public:
  ::std::vector<::android::gui::DisplayMode> supportedDisplayModes;
  int32_t activeDisplayModeId = 0;
  float renderFrameRate = 0.000000f;
  ::std::vector<int32_t> supportedColorModes;
  int32_t activeColorMode = 0;
  ::android::gui::HdrCapabilities hdrCapabilities;
  bool autoLowLatencyModeSupported = false;
  bool gameContentTypeSupported = false;
  int32_t preferredBootDisplayMode = 0;
  inline bool operator!=(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) != std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }
  inline bool operator<(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) < std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }
  inline bool operator<=(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) <= std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }
  inline bool operator==(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) == std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }
  inline bool operator>(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) > std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }
  inline bool operator>=(const DynamicDisplayInfo& rhs) const {
    return std::tie(supportedDisplayModes, activeDisplayModeId, renderFrameRate, supportedColorModes, activeColorMode, hdrCapabilities, autoLowLatencyModeSupported, gameContentTypeSupported, preferredBootDisplayMode) >= std::tie(rhs.supportedDisplayModes, rhs.activeDisplayModeId, rhs.renderFrameRate, rhs.supportedColorModes, rhs.activeColorMode, rhs.hdrCapabilities, rhs.autoLowLatencyModeSupported, rhs.gameContentTypeSupported, rhs.preferredBootDisplayMode);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DynamicDisplayInfo");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DynamicDisplayInfo{";
    os << "supportedDisplayModes: " << ::android::internal::ToString(supportedDisplayModes);
    os << ", activeDisplayModeId: " << ::android::internal::ToString(activeDisplayModeId);
    os << ", renderFrameRate: " << ::android::internal::ToString(renderFrameRate);
    os << ", supportedColorModes: " << ::android::internal::ToString(supportedColorModes);
    os << ", activeColorMode: " << ::android::internal::ToString(activeColorMode);
    os << ", hdrCapabilities: " << ::android::internal::ToString(hdrCapabilities);
    os << ", autoLowLatencyModeSupported: " << ::android::internal::ToString(autoLowLatencyModeSupported);
    os << ", gameContentTypeSupported: " << ::android::internal::ToString(gameContentTypeSupported);
    os << ", preferredBootDisplayMode: " << ::android::internal::ToString(preferredBootDisplayMode);
    os << "}";
    return os.str();
  }
};  // class DynamicDisplayInfo
}  // namespace gui
}  // namespace android
