#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/device/OutputConfiguration.h>
#include <aidl/android/frameworks/cameraservice/device/StreamConfigurationMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::device {
class OutputConfiguration;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class SessionConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::frameworks::cameraservice::device::OutputConfiguration> outputStreams;
  int32_t inputWidth = 0;
  int32_t inputHeight = 0;
  int32_t inputFormat = 0;
  ::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode operationMode = ::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) != std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }
  inline bool operator<(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) < std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }
  inline bool operator<=(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) <= std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }
  inline bool operator==(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) == std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }
  inline bool operator>(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) > std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }
  inline bool operator>=(const SessionConfiguration& rhs) const {
    return std::tie(outputStreams, inputWidth, inputHeight, inputFormat, operationMode) >= std::tie(rhs.outputStreams, rhs.inputWidth, rhs.inputHeight, rhs.inputFormat, rhs.operationMode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SessionConfiguration{";
    os << "outputStreams: " << ::android::internal::ToString(outputStreams);
    os << ", inputWidth: " << ::android::internal::ToString(inputWidth);
    os << ", inputHeight: " << ::android::internal::ToString(inputHeight);
    os << ", inputFormat: " << ::android::internal::ToString(inputFormat);
    os << ", operationMode: " << ::android::internal::ToString(operationMode);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
