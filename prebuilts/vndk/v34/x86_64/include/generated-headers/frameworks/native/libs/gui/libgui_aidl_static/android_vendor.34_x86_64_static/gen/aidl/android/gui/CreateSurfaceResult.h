#pragma once

#include <android/binder_to_string.h>
#include <binder/IBinder.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class CreateSurfaceResult : public ::android::Parcelable {
public:
  ::android::sp<::android::IBinder> handle;
  int32_t layerId = 0;
  ::android::String16 layerName;
  int32_t transformHint = 0;
  inline bool operator!=(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) != std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }
  inline bool operator<(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) < std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }
  inline bool operator<=(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) <= std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }
  inline bool operator==(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) == std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }
  inline bool operator>(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) > std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }
  inline bool operator>=(const CreateSurfaceResult& rhs) const {
    return std::tie(handle, layerId, layerName, transformHint) >= std::tie(rhs.handle, rhs.layerId, rhs.layerName, rhs.transformHint);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.CreateSurfaceResult");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "CreateSurfaceResult{";
    os << "handle: " << ::android::internal::ToString(handle);
    os << ", layerId: " << ::android::internal::ToString(layerId);
    os << ", layerName: " << ::android::internal::ToString(layerName);
    os << ", transformHint: " << ::android::internal::ToString(transformHint);
    os << "}";
    return os.str();
  }
};  // class CreateSurfaceResult
}  // namespace gui
}  // namespace android
