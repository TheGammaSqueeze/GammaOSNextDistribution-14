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
#include <aidl/android/hardware/gnss/IAGnssRil.h>
#include <aidl/android/hardware/gnss/IAGnssRilCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IAGnssRil : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IAGnssRil();
  virtual ~IAGnssRil();

  enum class AGnssRefLocationType : int32_t {
    GSM_CELLID = 1,
    UMTS_CELLID = 2,
    LTE_CELLID = 4,
    NR_CELLID = 8,
  };

  enum class SetIdType : int32_t {
    NONE = 0,
    IMSI = 1,
    MSISDM = 2,
  };

  class AGnssRefLocationCellID {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType type = ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType(0);
    int32_t mcc = 0;
    int32_t mnc = 0;
    int32_t lac = 0;
    int64_t cid = 0L;
    int32_t tac = 0;
    int32_t pcid = 0;
    int32_t arfcn = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) != std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }
    inline bool operator<(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) < std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }
    inline bool operator<=(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) <= std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }
    inline bool operator==(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) == std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }
    inline bool operator>(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) > std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }
    inline bool operator>=(const AGnssRefLocationCellID& rhs) const {
      return std::tie(type, mcc, mnc, lac, cid, tac, pcid, arfcn) >= std::tie(rhs.type, rhs.mcc, rhs.mnc, rhs.lac, rhs.cid, rhs.tac, rhs.pcid, rhs.arfcn);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "AGnssRefLocationCellID{";
      os << "type: " << ::android::internal::ToString(type);
      os << ", mcc: " << ::android::internal::ToString(mcc);
      os << ", mnc: " << ::android::internal::ToString(mnc);
      os << ", lac: " << ::android::internal::ToString(lac);
      os << ", cid: " << ::android::internal::ToString(cid);
      os << ", tac: " << ::android::internal::ToString(tac);
      os << ", pcid: " << ::android::internal::ToString(pcid);
      os << ", arfcn: " << ::android::internal::ToString(arfcn);
      os << "}";
      return os.str();
    }
  };
  class AGnssRefLocation {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType type = ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType(0);
    ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationCellID cellID;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) != std::tie(rhs.type, rhs.cellID);
    }
    inline bool operator<(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) < std::tie(rhs.type, rhs.cellID);
    }
    inline bool operator<=(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) <= std::tie(rhs.type, rhs.cellID);
    }
    inline bool operator==(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) == std::tie(rhs.type, rhs.cellID);
    }
    inline bool operator>(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) > std::tie(rhs.type, rhs.cellID);
    }
    inline bool operator>=(const AGnssRefLocation& rhs) const {
      return std::tie(type, cellID) >= std::tie(rhs.type, rhs.cellID);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "AGnssRefLocation{";
      os << "type: " << ::android::internal::ToString(type);
      os << ", cellID: " << ::android::internal::ToString(cellID);
      os << "}";
      return os.str();
    }
  };
  class NetworkAttributes {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t networkHandle = 0L;
    bool isConnected = false;
    int32_t capabilities = 0;
    std::string apn;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) != std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }
    inline bool operator<(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) < std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }
    inline bool operator<=(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) <= std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }
    inline bool operator==(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) == std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }
    inline bool operator>(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) > std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }
    inline bool operator>=(const NetworkAttributes& rhs) const {
      return std::tie(networkHandle, isConnected, capabilities, apn) >= std::tie(rhs.networkHandle, rhs.isConnected, rhs.capabilities, rhs.apn);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "NetworkAttributes{";
      os << "networkHandle: " << ::android::internal::ToString(networkHandle);
      os << ", isConnected: " << ::android::internal::ToString(isConnected);
      os << ", capabilities: " << ::android::internal::ToString(capabilities);
      os << ", apn: " << ::android::internal::ToString(apn);
      os << "}";
      return os.str();
    }
  };
  enum : int32_t { NETWORK_CAPABILITY_NOT_METERED = 1 };
  enum : int32_t { NETWORK_CAPABILITY_NOT_ROAMING = 2 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_setRefLocation = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setSetId = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_updateNetworkState = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<IAGnssRil> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IAGnssRil>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IAGnssRil>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IAGnssRil>& impl);
  static const std::shared_ptr<IAGnssRil>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRilCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus setRefLocation(const ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocation& in_agnssReflocation) = 0;
  virtual ::ndk::ScopedAStatus setSetId(::aidl::android::hardware::gnss::IAGnssRil::SetIdType in_type, const std::string& in_setid) = 0;
  virtual ::ndk::ScopedAStatus updateNetworkState(const ::aidl::android::hardware::gnss::IAGnssRil::NetworkAttributes& in_attributes) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IAGnssRil> default_impl;
};
class IAGnssRilDefault : public IAGnssRil {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssRilCallback>& in_callback) override;
  ::ndk::ScopedAStatus setRefLocation(const ::aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocation& in_agnssReflocation) override;
  ::ndk::ScopedAStatus setSetId(::aidl::android::hardware::gnss::IAGnssRil::SetIdType in_type, const std::string& in_setid) override;
  ::ndk::ScopedAStatus updateNetworkState(const ::aidl::android::hardware::gnss::IAGnssRil::NetworkAttributes& in_attributes) override;
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
[[nodiscard]] static inline std::string toString(IAGnssRil::AGnssRefLocationType val) {
  switch(val) {
  case IAGnssRil::AGnssRefLocationType::GSM_CELLID:
    return "GSM_CELLID";
  case IAGnssRil::AGnssRefLocationType::UMTS_CELLID:
    return "UMTS_CELLID";
  case IAGnssRil::AGnssRefLocationType::LTE_CELLID:
    return "LTE_CELLID";
  case IAGnssRil::AGnssRefLocationType::NR_CELLID:
    return "NR_CELLID";
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
constexpr inline std::array<aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType, 4> enum_values<aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType> = {
  aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType::GSM_CELLID,
  aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType::UMTS_CELLID,
  aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType::LTE_CELLID,
  aidl::android::hardware::gnss::IAGnssRil::AGnssRefLocationType::NR_CELLID,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IAGnssRil::SetIdType val) {
  switch(val) {
  case IAGnssRil::SetIdType::NONE:
    return "NONE";
  case IAGnssRil::SetIdType::IMSI:
    return "IMSI";
  case IAGnssRil::SetIdType::MSISDM:
    return "MSISDM";
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
constexpr inline std::array<aidl::android::hardware::gnss::IAGnssRil::SetIdType, 3> enum_values<aidl::android::hardware::gnss::IAGnssRil::SetIdType> = {
  aidl::android::hardware::gnss::IAGnssRil::SetIdType::NONE,
  aidl::android::hardware::gnss::IAGnssRil::SetIdType::IMSI,
  aidl::android::hardware::gnss::IAGnssRil::SetIdType::MSISDM,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
