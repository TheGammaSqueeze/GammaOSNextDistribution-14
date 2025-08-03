#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/IGnssBatching.h>
#include <aidl/android/hardware/gnss/IGnssBatchingCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssBatching : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssBatching();
  virtual ~IGnssBatching();

  class Options {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t periodNanos = 0L;
    float minDistanceMeters = 0.000000f;
    int32_t flags = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) != std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }
    inline bool operator<(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) < std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }
    inline bool operator<=(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) <= std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }
    inline bool operator==(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) == std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }
    inline bool operator>(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) > std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }
    inline bool operator>=(const Options& rhs) const {
      return std::tie(periodNanos, minDistanceMeters, flags) >= std::tie(rhs.periodNanos, rhs.minDistanceMeters, rhs.flags);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Options{";
      os << "periodNanos: " << ::android::internal::ToString(periodNanos);
      os << ", minDistanceMeters: " << ::android::internal::ToString(minDistanceMeters);
      os << ", flags: " << ::android::internal::ToString(flags);
      os << "}";
      return os.str();
    }
  };
  enum : int32_t { WAKEUP_ON_FIFO_FULL = 1 };
  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_init = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getBatchSize = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_start = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_flush = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_stop = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_cleanup = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IGnssBatching> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssBatching>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssBatching>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssBatching>& impl);
  static const std::shared_ptr<IGnssBatching>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus init(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatchingCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getBatchSize(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus start(const ::aidl::android::hardware::gnss::IGnssBatching::Options& in_options) = 0;
  virtual ::ndk::ScopedAStatus flush() = 0;
  virtual ::ndk::ScopedAStatus stop() = 0;
  virtual ::ndk::ScopedAStatus cleanup() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssBatching> default_impl;
};
class IGnssBatchingDefault : public IGnssBatching {
public:
  ::ndk::ScopedAStatus init(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatchingCallback>& in_callback) override;
  ::ndk::ScopedAStatus getBatchSize(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus start(const ::aidl::android::hardware::gnss::IGnssBatching::Options& in_options) override;
  ::ndk::ScopedAStatus flush() override;
  ::ndk::ScopedAStatus stop() override;
  ::ndk::ScopedAStatus cleanup() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
