#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/IGnssAntennaInfoCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssAntennaInfoCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssAntennaInfoCallback();
  virtual ~IGnssAntennaInfoCallback();

  class Row {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    std::vector<double> row;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Row& rhs) const {
      return std::tie(row) != std::tie(rhs.row);
    }
    inline bool operator<(const Row& rhs) const {
      return std::tie(row) < std::tie(rhs.row);
    }
    inline bool operator<=(const Row& rhs) const {
      return std::tie(row) <= std::tie(rhs.row);
    }
    inline bool operator==(const Row& rhs) const {
      return std::tie(row) == std::tie(rhs.row);
    }
    inline bool operator>(const Row& rhs) const {
      return std::tie(row) > std::tie(rhs.row);
    }
    inline bool operator>=(const Row& rhs) const {
      return std::tie(row) >= std::tie(rhs.row);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Row{";
      os << "row: " << ::android::internal::ToString(row);
      os << "}";
      return os.str();
    }
  };
  class Coord {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    double x = 0.000000;
    double xUncertainty = 0.000000;
    double y = 0.000000;
    double yUncertainty = 0.000000;
    double z = 0.000000;
    double zUncertainty = 0.000000;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) != std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }
    inline bool operator<(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) < std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }
    inline bool operator<=(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) <= std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }
    inline bool operator==(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) == std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }
    inline bool operator>(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) > std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }
    inline bool operator>=(const Coord& rhs) const {
      return std::tie(x, xUncertainty, y, yUncertainty, z, zUncertainty) >= std::tie(rhs.x, rhs.xUncertainty, rhs.y, rhs.yUncertainty, rhs.z, rhs.zUncertainty);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Coord{";
      os << "x: " << ::android::internal::ToString(x);
      os << ", xUncertainty: " << ::android::internal::ToString(xUncertainty);
      os << ", y: " << ::android::internal::ToString(y);
      os << ", yUncertainty: " << ::android::internal::ToString(yUncertainty);
      os << ", z: " << ::android::internal::ToString(z);
      os << ", zUncertainty: " << ::android::internal::ToString(zUncertainty);
      os << "}";
      return os.str();
    }
  };
  class GnssAntennaInfo {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t carrierFrequencyHz = 0L;
    ::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::Coord phaseCenterOffsetCoordinateMillimeters;
    std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::Row> phaseCenterVariationCorrectionMillimeters;
    std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::Row> phaseCenterVariationCorrectionUncertaintyMillimeters;
    std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::Row> signalGainCorrectionDbi;
    std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::Row> signalGainCorrectionUncertaintyDbi;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) != std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }
    inline bool operator<(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) < std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }
    inline bool operator<=(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) <= std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }
    inline bool operator==(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) == std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }
    inline bool operator>(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) > std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }
    inline bool operator>=(const GnssAntennaInfo& rhs) const {
      return std::tie(carrierFrequencyHz, phaseCenterOffsetCoordinateMillimeters, phaseCenterVariationCorrectionMillimeters, phaseCenterVariationCorrectionUncertaintyMillimeters, signalGainCorrectionDbi, signalGainCorrectionUncertaintyDbi) >= std::tie(rhs.carrierFrequencyHz, rhs.phaseCenterOffsetCoordinateMillimeters, rhs.phaseCenterVariationCorrectionMillimeters, rhs.phaseCenterVariationCorrectionUncertaintyMillimeters, rhs.signalGainCorrectionDbi, rhs.signalGainCorrectionUncertaintyDbi);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "GnssAntennaInfo{";
      os << "carrierFrequencyHz: " << ::android::internal::ToString(carrierFrequencyHz);
      os << ", phaseCenterOffsetCoordinateMillimeters: " << ::android::internal::ToString(phaseCenterOffsetCoordinateMillimeters);
      os << ", phaseCenterVariationCorrectionMillimeters: " << ::android::internal::ToString(phaseCenterVariationCorrectionMillimeters);
      os << ", phaseCenterVariationCorrectionUncertaintyMillimeters: " << ::android::internal::ToString(phaseCenterVariationCorrectionUncertaintyMillimeters);
      os << ", signalGainCorrectionDbi: " << ::android::internal::ToString(signalGainCorrectionDbi);
      os << ", signalGainCorrectionUncertaintyDbi: " << ::android::internal::ToString(signalGainCorrectionUncertaintyDbi);
      os << "}";
      return os.str();
    }
  };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssAntennaInfoCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssAntennaInfoCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssAntennaInfoCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssAntennaInfoCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssAntennaInfoCallback>& impl);
  static const std::shared_ptr<IGnssAntennaInfoCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssAntennaInfoCb(const std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::GnssAntennaInfo>& in_gnssAntennaInfos) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssAntennaInfoCallback> default_impl;
};
class IGnssAntennaInfoCallbackDefault : public IGnssAntennaInfoCallback {
public:
  ::ndk::ScopedAStatus gnssAntennaInfoCb(const std::vector<::aidl::android::hardware::gnss::IGnssAntennaInfoCallback::GnssAntennaInfo>& in_gnssAntennaInfos) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
