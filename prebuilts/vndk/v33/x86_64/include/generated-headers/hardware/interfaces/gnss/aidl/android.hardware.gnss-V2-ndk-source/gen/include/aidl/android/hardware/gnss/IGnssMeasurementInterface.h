#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/IGnssMeasurementCallback.h>
#include <aidl/android/hardware/gnss/IGnssMeasurementInterface.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssMeasurementInterface : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssMeasurementInterface();
  virtual ~IGnssMeasurementInterface();

  class Options {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    bool enableFullTracking = false;
    bool enableCorrVecOutputs = false;
    int32_t intervalMs = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) != std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }
    inline bool operator<(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) < std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }
    inline bool operator<=(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) <= std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }
    inline bool operator==(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) == std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }
    inline bool operator>(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) > std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }
    inline bool operator>=(const Options& rhs) const {
      return std::tie(enableFullTracking, enableCorrVecOutputs, intervalMs) >= std::tie(rhs.enableFullTracking, rhs.enableCorrVecOutputs, rhs.intervalMs);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Options{";
      os << "enableFullTracking: " << ::android::internal::ToString(enableFullTracking);
      os << ", enableCorrVecOutputs: " << ::android::internal::ToString(enableCorrVecOutputs);
      os << ", intervalMs: " << ::android::internal::ToString(intervalMs);
      os << "}";
      return os.str();
    }
  };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setCallbackWithOptions = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IGnssMeasurementInterface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssMeasurementInterface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssMeasurementInterface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssMeasurementInterface>& impl);
  static const std::shared_ptr<IGnssMeasurementInterface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus setCallbackWithOptions(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, const ::aidl::android::hardware::gnss::IGnssMeasurementInterface::Options& in_options) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssMeasurementInterface> default_impl;
};
class IGnssMeasurementInterfaceDefault : public IGnssMeasurementInterface {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus setCallbackWithOptions(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, const ::aidl::android::hardware::gnss::IGnssMeasurementInterface::Options& in_options) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
