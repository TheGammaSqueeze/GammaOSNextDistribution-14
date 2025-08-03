#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControlCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
class IGnssVisibilityControlCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssVisibilityControlCallback();
  virtual ~IGnssVisibilityControlCallback();

  enum class NfwProtocolStack : int32_t {
    CTRL_PLANE = 0,
    SUPL = 1,
    IMS = 10,
    SIM = 11,
    OTHER_PROTOCOL_STACK = 100,
  };

  enum class NfwRequestor : int32_t {
    CARRIER = 0,
    OEM = 10,
    MODEM_CHIPSET_VENDOR = 11,
    GNSS_CHIPSET_VENDOR = 12,
    OTHER_CHIPSET_VENDOR = 13,
    AUTOMOBILE_CLIENT = 20,
    OTHER_REQUESTOR = 100,
  };

  enum class NfwResponseType : int32_t {
    REJECTED = 0,
    ACCEPTED_NO_LOCATION_PROVIDED = 1,
    ACCEPTED_LOCATION_PROVIDED = 2,
  };

  class NfwNotification {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    std::string proxyAppPackageName;
    ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack protocolStack = ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack(0);
    std::string otherProtocolStackName;
    ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor requestor = ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor(0);
    std::string requestorId;
    ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType responseType = ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType(0);
    bool inEmergencyMode = false;
    bool isCachedLocation = false;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) != std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }
    inline bool operator<(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) < std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }
    inline bool operator<=(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) <= std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }
    inline bool operator==(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) == std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }
    inline bool operator>(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) > std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }
    inline bool operator>=(const NfwNotification& rhs) const {
      return std::tie(proxyAppPackageName, protocolStack, otherProtocolStackName, requestor, requestorId, responseType, inEmergencyMode, isCachedLocation) >= std::tie(rhs.proxyAppPackageName, rhs.protocolStack, rhs.otherProtocolStackName, rhs.requestor, rhs.requestorId, rhs.responseType, rhs.inEmergencyMode, rhs.isCachedLocation);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "NfwNotification{";
      os << "proxyAppPackageName: " << ::android::internal::ToString(proxyAppPackageName);
      os << ", protocolStack: " << ::android::internal::ToString(protocolStack);
      os << ", otherProtocolStackName: " << ::android::internal::ToString(otherProtocolStackName);
      os << ", requestor: " << ::android::internal::ToString(requestor);
      os << ", requestorId: " << ::android::internal::ToString(requestorId);
      os << ", responseType: " << ::android::internal::ToString(responseType);
      os << ", inEmergencyMode: " << ::android::internal::ToString(inEmergencyMode);
      os << ", isCachedLocation: " << ::android::internal::ToString(isCachedLocation);
      os << "}";
      return os.str();
    }
  };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_nfwNotifyCb = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_isInEmergencySession = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IGnssVisibilityControlCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssVisibilityControlCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssVisibilityControlCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssVisibilityControlCallback>& impl);
  static const std::shared_ptr<IGnssVisibilityControlCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus nfwNotifyCb(const ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwNotification& in_notification) = 0;
  virtual ::ndk::ScopedAStatus isInEmergencySession(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssVisibilityControlCallback> default_impl;
};
class IGnssVisibilityControlCallbackDefault : public IGnssVisibilityControlCallback {
public:
  ::ndk::ScopedAStatus nfwNotifyCb(const ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwNotification& in_notification) override;
  ::ndk::ScopedAStatus isInEmergencySession(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
[[nodiscard]] static inline std::string toString(IGnssVisibilityControlCallback::NfwProtocolStack val) {
  switch(val) {
  case IGnssVisibilityControlCallback::NfwProtocolStack::CTRL_PLANE:
    return "CTRL_PLANE";
  case IGnssVisibilityControlCallback::NfwProtocolStack::SUPL:
    return "SUPL";
  case IGnssVisibilityControlCallback::NfwProtocolStack::IMS:
    return "IMS";
  case IGnssVisibilityControlCallback::NfwProtocolStack::SIM:
    return "SIM";
  case IGnssVisibilityControlCallback::NfwProtocolStack::OTHER_PROTOCOL_STACK:
    return "OTHER_PROTOCOL_STACK";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack, 5> enum_values<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack> = {
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack::CTRL_PLANE,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack::SUPL,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack::IMS,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack::SIM,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwProtocolStack::OTHER_PROTOCOL_STACK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
[[nodiscard]] static inline std::string toString(IGnssVisibilityControlCallback::NfwRequestor val) {
  switch(val) {
  case IGnssVisibilityControlCallback::NfwRequestor::CARRIER:
    return "CARRIER";
  case IGnssVisibilityControlCallback::NfwRequestor::OEM:
    return "OEM";
  case IGnssVisibilityControlCallback::NfwRequestor::MODEM_CHIPSET_VENDOR:
    return "MODEM_CHIPSET_VENDOR";
  case IGnssVisibilityControlCallback::NfwRequestor::GNSS_CHIPSET_VENDOR:
    return "GNSS_CHIPSET_VENDOR";
  case IGnssVisibilityControlCallback::NfwRequestor::OTHER_CHIPSET_VENDOR:
    return "OTHER_CHIPSET_VENDOR";
  case IGnssVisibilityControlCallback::NfwRequestor::AUTOMOBILE_CLIENT:
    return "AUTOMOBILE_CLIENT";
  case IGnssVisibilityControlCallback::NfwRequestor::OTHER_REQUESTOR:
    return "OTHER_REQUESTOR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor, 7> enum_values<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor> = {
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::CARRIER,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::OEM,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::MODEM_CHIPSET_VENDOR,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::GNSS_CHIPSET_VENDOR,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::OTHER_CHIPSET_VENDOR,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::AUTOMOBILE_CLIENT,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwRequestor::OTHER_REQUESTOR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
[[nodiscard]] static inline std::string toString(IGnssVisibilityControlCallback::NfwResponseType val) {
  switch(val) {
  case IGnssVisibilityControlCallback::NfwResponseType::REJECTED:
    return "REJECTED";
  case IGnssVisibilityControlCallback::NfwResponseType::ACCEPTED_NO_LOCATION_PROVIDED:
    return "ACCEPTED_NO_LOCATION_PROVIDED";
  case IGnssVisibilityControlCallback::NfwResponseType::ACCEPTED_LOCATION_PROVIDED:
    return "ACCEPTED_LOCATION_PROVIDED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType, 3> enum_values<aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType> = {
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType::REJECTED,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType::ACCEPTED_NO_LOCATION_PROVIDED,
  aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwResponseType::ACCEPTED_LOCATION_PROVIDED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
