#pragma once

#include <android/binder_to_string.h>
#include <android/gui/DisplayModeSpecs.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class DisplayModeSpecs : public ::android::Parcelable {
public:
  class RefreshRateRanges : public ::android::Parcelable {
  public:
    class RefreshRateRange : public ::android::Parcelable {
    public:
      float min = 0.000000f;
      float max = 0.000000f;
      inline bool operator!=(const RefreshRateRange& rhs) const {
        return std::tie(min, max) != std::tie(rhs.min, rhs.max);
      }
      inline bool operator<(const RefreshRateRange& rhs) const {
        return std::tie(min, max) < std::tie(rhs.min, rhs.max);
      }
      inline bool operator<=(const RefreshRateRange& rhs) const {
        return std::tie(min, max) <= std::tie(rhs.min, rhs.max);
      }
      inline bool operator==(const RefreshRateRange& rhs) const {
        return std::tie(min, max) == std::tie(rhs.min, rhs.max);
      }
      inline bool operator>(const RefreshRateRange& rhs) const {
        return std::tie(min, max) > std::tie(rhs.min, rhs.max);
      }
      inline bool operator>=(const RefreshRateRange& rhs) const {
        return std::tie(min, max) >= std::tie(rhs.min, rhs.max);
      }

      ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
      ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
      static const ::android::String16& getParcelableDescriptor() {
        static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayModeSpecs.RefreshRateRanges.RefreshRateRange");
        return DESCRIPTOR;
      }
      inline std::string toString() const {
        std::ostringstream os;
        os << "RefreshRateRange{";
        os << "min: " << ::android::internal::ToString(min);
        os << ", max: " << ::android::internal::ToString(max);
        os << "}";
        return os.str();
      }
    };  // class RefreshRateRange
    ::android::gui::DisplayModeSpecs::RefreshRateRanges::RefreshRateRange physical;
    ::android::gui::DisplayModeSpecs::RefreshRateRanges::RefreshRateRange render;
    inline bool operator!=(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) != std::tie(rhs.physical, rhs.render);
    }
    inline bool operator<(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) < std::tie(rhs.physical, rhs.render);
    }
    inline bool operator<=(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) <= std::tie(rhs.physical, rhs.render);
    }
    inline bool operator==(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) == std::tie(rhs.physical, rhs.render);
    }
    inline bool operator>(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) > std::tie(rhs.physical, rhs.render);
    }
    inline bool operator>=(const RefreshRateRanges& rhs) const {
      return std::tie(physical, render) >= std::tie(rhs.physical, rhs.render);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayModeSpecs.RefreshRateRanges");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream os;
      os << "RefreshRateRanges{";
      os << "physical: " << ::android::internal::ToString(physical);
      os << ", render: " << ::android::internal::ToString(render);
      os << "}";
      return os.str();
    }
  };  // class RefreshRateRanges
  int32_t defaultMode = 0;
  bool allowGroupSwitching = false;
  ::android::gui::DisplayModeSpecs::RefreshRateRanges primaryRanges;
  ::android::gui::DisplayModeSpecs::RefreshRateRanges appRequestRanges;
  inline bool operator!=(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) != std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }
  inline bool operator<(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) < std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }
  inline bool operator<=(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) <= std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }
  inline bool operator==(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) == std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }
  inline bool operator>(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) > std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }
  inline bool operator>=(const DisplayModeSpecs& rhs) const {
    return std::tie(defaultMode, allowGroupSwitching, primaryRanges, appRequestRanges) >= std::tie(rhs.defaultMode, rhs.allowGroupSwitching, rhs.primaryRanges, rhs.appRequestRanges);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayModeSpecs");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayModeSpecs{";
    os << "defaultMode: " << ::android::internal::ToString(defaultMode);
    os << ", allowGroupSwitching: " << ::android::internal::ToString(allowGroupSwitching);
    os << ", primaryRanges: " << ::android::internal::ToString(primaryRanges);
    os << ", appRequestRanges: " << ::android::internal::ToString(appRequestRanges);
    os << "}";
    return os.str();
  }
};  // class DisplayModeSpecs
}  // namespace gui
}  // namespace android
