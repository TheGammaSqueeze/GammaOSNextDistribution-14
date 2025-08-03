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
#include <aidl/android/hardware/gnss/IGnssNavigationMessageCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssNavigationMessageCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssNavigationMessageCallback();
  virtual ~IGnssNavigationMessageCallback();

  class GnssNavigationMessage {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    enum class GnssNavigationMessageType : int32_t {
      UNKNOWN = 0,
      GPS_L1CA = 257,
      GPS_L2CNAV = 258,
      GPS_L5CNAV = 259,
      SBS = 513,
      GPS_CNAV2 = 260,
      GLO_L1CA = 769,
      QZS_L1CA = 1025,
      BDS_D1 = 1281,
      BDS_D2 = 1282,
      BDS_CNAV1 = 1283,
      BDS_CNAV2 = 1284,
      GAL_I = 1537,
      GAL_F = 1538,
      IRN_L5CA = 1793,
    };

    int32_t svid = 0;
    ::aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType type = ::aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType(0);
    int32_t status = 0;
    int32_t messageId = 0;
    int32_t submessageId = 0;
    std::vector<uint8_t> data;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) != std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }
    inline bool operator<(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) < std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }
    inline bool operator<=(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) <= std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }
    inline bool operator==(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) == std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }
    inline bool operator>(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) > std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }
    inline bool operator>=(const GnssNavigationMessage& rhs) const {
      return std::tie(svid, type, status, messageId, submessageId, data) >= std::tie(rhs.svid, rhs.type, rhs.status, rhs.messageId, rhs.submessageId, rhs.data);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    enum : int32_t { STATUS_PARITY_PASSED = 1 };
    enum : int32_t { STATUS_PARITY_REBUILT = 2 };
    enum : int32_t { STATUS_UNKNOWN = 0 };
    inline std::string toString() const {
      std::ostringstream os;
      os << "GnssNavigationMessage{";
      os << "svid: " << ::android::internal::ToString(svid);
      os << ", type: " << ::android::internal::ToString(type);
      os << ", status: " << ::android::internal::ToString(status);
      os << ", messageId: " << ::android::internal::ToString(messageId);
      os << ", submessageId: " << ::android::internal::ToString(submessageId);
      os << ", data: " << ::android::internal::ToString(data);
      os << "}";
      return os.str();
    }
  };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssNavigationMessageCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssNavigationMessageCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssNavigationMessageCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssNavigationMessageCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssNavigationMessageCallback>& impl);
  static const std::shared_ptr<IGnssNavigationMessageCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssNavigationMessageCb(const ::aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage& in_message) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssNavigationMessageCallback> default_impl;
};
class IGnssNavigationMessageCallbackDefault : public IGnssNavigationMessageCallback {
public:
  ::ndk::ScopedAStatus gnssNavigationMessageCb(const ::aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage& in_message) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType val) {
  switch(val) {
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::UNKNOWN:
    return "UNKNOWN";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L1CA:
    return "GPS_L1CA";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L2CNAV:
    return "GPS_L2CNAV";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L5CNAV:
    return "GPS_L5CNAV";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::SBS:
    return "SBS";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_CNAV2:
    return "GPS_CNAV2";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GLO_L1CA:
    return "GLO_L1CA";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::QZS_L1CA:
    return "QZS_L1CA";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_D1:
    return "BDS_D1";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_D2:
    return "BDS_D2";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_CNAV1:
    return "BDS_CNAV1";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_CNAV2:
    return "BDS_CNAV2";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GAL_I:
    return "GAL_I";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GAL_F:
    return "GAL_F";
  case IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::IRN_L5CA:
    return "IRN_L5CA";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType, 15> enum_values<aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType> = {
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::UNKNOWN,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L1CA,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L2CNAV,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_L5CNAV,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::SBS,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GPS_CNAV2,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GLO_L1CA,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::QZS_L1CA,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_D1,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_D2,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_CNAV1,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::BDS_CNAV2,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GAL_I,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::GAL_F,
  aidl::android::hardware::gnss::IGnssNavigationMessageCallback::GnssNavigationMessage::GnssNavigationMessageType::IRN_L5CA,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
