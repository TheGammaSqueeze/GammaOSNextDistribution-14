#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/system/keystore2/IKeystoreOperation.h>
#include <aidl/android/system/keystore2/KeyParameters.h>
#include <aidl/android/system/keystore2/OperationChallenge.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class CreateOperationResponse {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::shared_ptr<::aidl::android::system::keystore2::IKeystoreOperation> iOperation;
  std::optional<::aidl::android::system::keystore2::OperationChallenge> operationChallenge;
  std::optional<::aidl::android::system::keystore2::KeyParameters> parameters;
  std::optional<std::vector<uint8_t>> upgradedBlob;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) != std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }
  inline bool operator<(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) < std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }
  inline bool operator<=(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) <= std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }
  inline bool operator==(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) == std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }
  inline bool operator>(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) > std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }
  inline bool operator>=(const CreateOperationResponse& rhs) const {
    return std::tie(iOperation, operationChallenge, parameters, upgradedBlob) >= std::tie(rhs.iOperation, rhs.operationChallenge, rhs.parameters, rhs.upgradedBlob);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CreateOperationResponse{";
    os << "iOperation: " << ::android::internal::ToString(iOperation);
    os << ", operationChallenge: " << ::android::internal::ToString(operationChallenge);
    os << ", parameters: " << ::android::internal::ToString(parameters);
    os << ", upgradedBlob: " << ::android::internal::ToString(upgradedBlob);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
