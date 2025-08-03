#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
enum class Status : int32_t {
  OK = 0,
  ERROR_DRM_NO_LICENSE = 1,
  ERROR_DRM_LICENSE_EXPIRED = 2,
  ERROR_DRM_SESSION_NOT_OPENED = 3,
  ERROR_DRM_CANNOT_HANDLE = 4,
  ERROR_DRM_INVALID_STATE = 5,
  BAD_VALUE = 6,
  ERROR_DRM_NOT_PROVISIONED = 7,
  ERROR_DRM_RESOURCE_BUSY = 8,
  ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION = 9,
  ERROR_DRM_DEVICE_REVOKED = 10,
  ERROR_DRM_DECRYPT = 11,
  ERROR_DRM_UNKNOWN = 12,
  ERROR_DRM_INSUFFICIENT_SECURITY = 13,
  ERROR_DRM_FRAME_TOO_LARGE = 14,
  ERROR_DRM_SESSION_LOST_STATE = 15,
  ERROR_DRM_RESOURCE_CONTENTION = 16,
  CANNOT_DECRYPT_ZERO_SUBSAMPLES = 17,
  CRYPTO_LIBRARY_ERROR = 18,
  GENERAL_OEM_ERROR = 19,
  GENERAL_PLUGIN_ERROR = 20,
  INIT_DATA_INVALID = 21,
  KEY_NOT_LOADED = 22,
  LICENSE_PARSE_ERROR = 23,
  LICENSE_POLICY_ERROR = 24,
  LICENSE_RELEASE_ERROR = 25,
  LICENSE_REQUEST_REJECTED = 26,
  LICENSE_RESTORE_ERROR = 27,
  LICENSE_STATE_ERROR = 28,
  MALFORMED_CERTIFICATE = 29,
  MEDIA_FRAMEWORK_ERROR = 30,
  MISSING_CERTIFICATE = 31,
  PROVISIONING_CERTIFICATE_ERROR = 32,
  PROVISIONING_CONFIGURATION_ERROR = 33,
  PROVISIONING_PARSE_ERROR = 34,
  PROVISIONING_REQUEST_REJECTED = 35,
  RETRYABLE_PROVISIONING_ERROR = 36,
  SECURE_STOP_RELEASE_ERROR = 37,
  STORAGE_READ_FAILURE = 38,
  STORAGE_WRITE_FAILURE = 39,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(Status val) {
  switch(val) {
  case Status::OK:
    return "OK";
  case Status::ERROR_DRM_NO_LICENSE:
    return "ERROR_DRM_NO_LICENSE";
  case Status::ERROR_DRM_LICENSE_EXPIRED:
    return "ERROR_DRM_LICENSE_EXPIRED";
  case Status::ERROR_DRM_SESSION_NOT_OPENED:
    return "ERROR_DRM_SESSION_NOT_OPENED";
  case Status::ERROR_DRM_CANNOT_HANDLE:
    return "ERROR_DRM_CANNOT_HANDLE";
  case Status::ERROR_DRM_INVALID_STATE:
    return "ERROR_DRM_INVALID_STATE";
  case Status::BAD_VALUE:
    return "BAD_VALUE";
  case Status::ERROR_DRM_NOT_PROVISIONED:
    return "ERROR_DRM_NOT_PROVISIONED";
  case Status::ERROR_DRM_RESOURCE_BUSY:
    return "ERROR_DRM_RESOURCE_BUSY";
  case Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION:
    return "ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION";
  case Status::ERROR_DRM_DEVICE_REVOKED:
    return "ERROR_DRM_DEVICE_REVOKED";
  case Status::ERROR_DRM_DECRYPT:
    return "ERROR_DRM_DECRYPT";
  case Status::ERROR_DRM_UNKNOWN:
    return "ERROR_DRM_UNKNOWN";
  case Status::ERROR_DRM_INSUFFICIENT_SECURITY:
    return "ERROR_DRM_INSUFFICIENT_SECURITY";
  case Status::ERROR_DRM_FRAME_TOO_LARGE:
    return "ERROR_DRM_FRAME_TOO_LARGE";
  case Status::ERROR_DRM_SESSION_LOST_STATE:
    return "ERROR_DRM_SESSION_LOST_STATE";
  case Status::ERROR_DRM_RESOURCE_CONTENTION:
    return "ERROR_DRM_RESOURCE_CONTENTION";
  case Status::CANNOT_DECRYPT_ZERO_SUBSAMPLES:
    return "CANNOT_DECRYPT_ZERO_SUBSAMPLES";
  case Status::CRYPTO_LIBRARY_ERROR:
    return "CRYPTO_LIBRARY_ERROR";
  case Status::GENERAL_OEM_ERROR:
    return "GENERAL_OEM_ERROR";
  case Status::GENERAL_PLUGIN_ERROR:
    return "GENERAL_PLUGIN_ERROR";
  case Status::INIT_DATA_INVALID:
    return "INIT_DATA_INVALID";
  case Status::KEY_NOT_LOADED:
    return "KEY_NOT_LOADED";
  case Status::LICENSE_PARSE_ERROR:
    return "LICENSE_PARSE_ERROR";
  case Status::LICENSE_POLICY_ERROR:
    return "LICENSE_POLICY_ERROR";
  case Status::LICENSE_RELEASE_ERROR:
    return "LICENSE_RELEASE_ERROR";
  case Status::LICENSE_REQUEST_REJECTED:
    return "LICENSE_REQUEST_REJECTED";
  case Status::LICENSE_RESTORE_ERROR:
    return "LICENSE_RESTORE_ERROR";
  case Status::LICENSE_STATE_ERROR:
    return "LICENSE_STATE_ERROR";
  case Status::MALFORMED_CERTIFICATE:
    return "MALFORMED_CERTIFICATE";
  case Status::MEDIA_FRAMEWORK_ERROR:
    return "MEDIA_FRAMEWORK_ERROR";
  case Status::MISSING_CERTIFICATE:
    return "MISSING_CERTIFICATE";
  case Status::PROVISIONING_CERTIFICATE_ERROR:
    return "PROVISIONING_CERTIFICATE_ERROR";
  case Status::PROVISIONING_CONFIGURATION_ERROR:
    return "PROVISIONING_CONFIGURATION_ERROR";
  case Status::PROVISIONING_PARSE_ERROR:
    return "PROVISIONING_PARSE_ERROR";
  case Status::PROVISIONING_REQUEST_REJECTED:
    return "PROVISIONING_REQUEST_REJECTED";
  case Status::RETRYABLE_PROVISIONING_ERROR:
    return "RETRYABLE_PROVISIONING_ERROR";
  case Status::SECURE_STOP_RELEASE_ERROR:
    return "SECURE_STOP_RELEASE_ERROR";
  case Status::STORAGE_READ_FAILURE:
    return "STORAGE_READ_FAILURE";
  case Status::STORAGE_WRITE_FAILURE:
    return "STORAGE_WRITE_FAILURE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::drm::Status, 40> enum_values<aidl::android::hardware::drm::Status> = {
  aidl::android::hardware::drm::Status::OK,
  aidl::android::hardware::drm::Status::ERROR_DRM_NO_LICENSE,
  aidl::android::hardware::drm::Status::ERROR_DRM_LICENSE_EXPIRED,
  aidl::android::hardware::drm::Status::ERROR_DRM_SESSION_NOT_OPENED,
  aidl::android::hardware::drm::Status::ERROR_DRM_CANNOT_HANDLE,
  aidl::android::hardware::drm::Status::ERROR_DRM_INVALID_STATE,
  aidl::android::hardware::drm::Status::BAD_VALUE,
  aidl::android::hardware::drm::Status::ERROR_DRM_NOT_PROVISIONED,
  aidl::android::hardware::drm::Status::ERROR_DRM_RESOURCE_BUSY,
  aidl::android::hardware::drm::Status::ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION,
  aidl::android::hardware::drm::Status::ERROR_DRM_DEVICE_REVOKED,
  aidl::android::hardware::drm::Status::ERROR_DRM_DECRYPT,
  aidl::android::hardware::drm::Status::ERROR_DRM_UNKNOWN,
  aidl::android::hardware::drm::Status::ERROR_DRM_INSUFFICIENT_SECURITY,
  aidl::android::hardware::drm::Status::ERROR_DRM_FRAME_TOO_LARGE,
  aidl::android::hardware::drm::Status::ERROR_DRM_SESSION_LOST_STATE,
  aidl::android::hardware::drm::Status::ERROR_DRM_RESOURCE_CONTENTION,
  aidl::android::hardware::drm::Status::CANNOT_DECRYPT_ZERO_SUBSAMPLES,
  aidl::android::hardware::drm::Status::CRYPTO_LIBRARY_ERROR,
  aidl::android::hardware::drm::Status::GENERAL_OEM_ERROR,
  aidl::android::hardware::drm::Status::GENERAL_PLUGIN_ERROR,
  aidl::android::hardware::drm::Status::INIT_DATA_INVALID,
  aidl::android::hardware::drm::Status::KEY_NOT_LOADED,
  aidl::android::hardware::drm::Status::LICENSE_PARSE_ERROR,
  aidl::android::hardware::drm::Status::LICENSE_POLICY_ERROR,
  aidl::android::hardware::drm::Status::LICENSE_RELEASE_ERROR,
  aidl::android::hardware::drm::Status::LICENSE_REQUEST_REJECTED,
  aidl::android::hardware::drm::Status::LICENSE_RESTORE_ERROR,
  aidl::android::hardware::drm::Status::LICENSE_STATE_ERROR,
  aidl::android::hardware::drm::Status::MALFORMED_CERTIFICATE,
  aidl::android::hardware::drm::Status::MEDIA_FRAMEWORK_ERROR,
  aidl::android::hardware::drm::Status::MISSING_CERTIFICATE,
  aidl::android::hardware::drm::Status::PROVISIONING_CERTIFICATE_ERROR,
  aidl::android::hardware::drm::Status::PROVISIONING_CONFIGURATION_ERROR,
  aidl::android::hardware::drm::Status::PROVISIONING_PARSE_ERROR,
  aidl::android::hardware::drm::Status::PROVISIONING_REQUEST_REJECTED,
  aidl::android::hardware::drm::Status::RETRYABLE_PROVISIONING_ERROR,
  aidl::android::hardware::drm::Status::SECURE_STOP_RELEASE_ERROR,
  aidl::android::hardware::drm::Status::STORAGE_READ_FAILURE,
  aidl::android::hardware::drm::Status::STORAGE_WRITE_FAILURE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
