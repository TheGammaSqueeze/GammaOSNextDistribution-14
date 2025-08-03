#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class Properties {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string implementor;
  std::string description;
  int32_t version = 0;
  std::string uuid;
  std::string supportedModelArch;
  int32_t maxSoundModels = 0;
  int32_t maxKeyPhrases = 0;
  int32_t maxUsers = 0;
  int32_t recognitionModes = 0;
  bool captureTransition = false;
  int32_t maxBufferMs = 0;
  bool concurrentCapture = false;
  bool triggerInEvent = false;
  int32_t powerConsumptionMw = 0;
  int32_t audioCapabilities = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) != std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }
  inline bool operator<(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) < std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }
  inline bool operator<=(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) <= std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }
  inline bool operator==(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) == std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }
  inline bool operator>(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) > std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }
  inline bool operator>=(const Properties& rhs) const {
    return std::tie(implementor, description, version, uuid, supportedModelArch, maxSoundModels, maxKeyPhrases, maxUsers, recognitionModes, captureTransition, maxBufferMs, concurrentCapture, triggerInEvent, powerConsumptionMw, audioCapabilities) >= std::tie(rhs.implementor, rhs.description, rhs.version, rhs.uuid, rhs.supportedModelArch, rhs.maxSoundModels, rhs.maxKeyPhrases, rhs.maxUsers, rhs.recognitionModes, rhs.captureTransition, rhs.maxBufferMs, rhs.concurrentCapture, rhs.triggerInEvent, rhs.powerConsumptionMw, rhs.audioCapabilities);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Properties{";
    os << "implementor: " << ::android::internal::ToString(implementor);
    os << ", description: " << ::android::internal::ToString(description);
    os << ", version: " << ::android::internal::ToString(version);
    os << ", uuid: " << ::android::internal::ToString(uuid);
    os << ", supportedModelArch: " << ::android::internal::ToString(supportedModelArch);
    os << ", maxSoundModels: " << ::android::internal::ToString(maxSoundModels);
    os << ", maxKeyPhrases: " << ::android::internal::ToString(maxKeyPhrases);
    os << ", maxUsers: " << ::android::internal::ToString(maxUsers);
    os << ", recognitionModes: " << ::android::internal::ToString(recognitionModes);
    os << ", captureTransition: " << ::android::internal::ToString(captureTransition);
    os << ", maxBufferMs: " << ::android::internal::ToString(maxBufferMs);
    os << ", concurrentCapture: " << ::android::internal::ToString(concurrentCapture);
    os << ", triggerInEvent: " << ::android::internal::ToString(triggerInEvent);
    os << ", powerConsumptionMw: " << ::android::internal::ToString(powerConsumptionMw);
    os << ", audioCapabilities: " << ::android::internal::ToString(audioCapabilities);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
