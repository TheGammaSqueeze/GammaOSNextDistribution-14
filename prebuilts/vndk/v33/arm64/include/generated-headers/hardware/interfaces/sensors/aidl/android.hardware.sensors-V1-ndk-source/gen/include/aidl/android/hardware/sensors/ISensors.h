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
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/common/fmq/MQDescriptor.h>
#include <aidl/android/hardware/common/fmq/SynchronizedReadWrite.h>
#include <aidl/android/hardware/sensors/Event.h>
#include <aidl/android/hardware/sensors/ISensors.h>
#include <aidl/android/hardware/sensors/ISensorsCallback.h>
#include <aidl/android/hardware/sensors/SensorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class ISensors : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISensors();
  virtual ~ISensors();

  enum class RateLevel : int32_t {
    STOP = 0,
    NORMAL = 1,
    FAST = 2,
    VERY_FAST = 3,
  };

  enum class OperationMode : int32_t {
    NORMAL = 0,
    DATA_INJECTION = 1,
  };

  class SharedMemInfo {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    enum class SharedMemFormat : int32_t {
      SENSORS_EVENT = 1,
    };

    enum class SharedMemType : int32_t {
      ASHMEM = 1,
      GRALLOC = 2,
    };

    ::aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType type = ::aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType(0);
    ::aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemFormat format = ::aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemFormat(0);
    int32_t size = 0;
    ::aidl::android::hardware::common::NativeHandle memoryHandle;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) != std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }
    inline bool operator<(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) < std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }
    inline bool operator<=(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) <= std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }
    inline bool operator==(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) == std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }
    inline bool operator>(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) > std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }
    inline bool operator>=(const SharedMemInfo& rhs) const {
      return std::tie(type, format, size, memoryHandle) >= std::tie(rhs.type, rhs.format, rhs.size, rhs.memoryHandle);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "SharedMemInfo{";
      os << "type: " << ::android::internal::ToString(type);
      os << ", format: " << ::android::internal::ToString(format);
      os << ", size: " << ::android::internal::ToString(size);
      os << ", memoryHandle: " << ::android::internal::ToString(memoryHandle);
      os << "}";
      return os.str();
    }
  };
  enum : int32_t { ERROR_NO_MEMORY = -12 };
  enum : int32_t { ERROR_BAD_VALUE = -22 };
  enum : int32_t { WAKE_LOCK_TIMEOUT_SECONDS = 1 };
  enum : int32_t { EVENT_QUEUE_FLAG_BITS_READ_AND_PROCESS = 1 };
  enum : int32_t { EVENT_QUEUE_FLAG_BITS_EVENTS_READ = 2 };
  enum : int32_t { WAKE_LOCK_QUEUE_FLAG_BITS_DATA_WRITTEN = 1 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_FIELD = 0 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_REPORT_TOKEN = 4 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_SENSOR_TYPE = 8 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_ATOMIC_COUNTER = 12 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_TIMESTAMP = 16 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_DATA = 24 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_OFFSET_SIZE_RESERVED = 88 };
  enum : int32_t { DIRECT_REPORT_SENSOR_EVENT_TOTAL_LENGTH = 104 };
  static const int32_t version = 1;
  static inline const std::string hash = "c38c6b75e592ba5a6400b6348ad7f27db3064410";
  static constexpr uint32_t TRANSACTION_activate = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_batch = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_configDirectReport = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_flush = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getSensorsList = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_initialize = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_injectSensorData = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_registerDirectChannel = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_setOperationMode = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_unregisterDirectChannel = FIRST_CALL_TRANSACTION + 9;

  static std::shared_ptr<ISensors> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISensors>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISensors>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISensors>& impl);
  static const std::shared_ptr<ISensors>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus activate(int32_t in_sensorHandle, bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus batch(int32_t in_sensorHandle, int64_t in_samplingPeriodNs, int64_t in_maxReportLatencyNs) = 0;
  virtual ::ndk::ScopedAStatus configDirectReport(int32_t in_sensorHandle, int32_t in_channelHandle, ::aidl::android::hardware::sensors::ISensors::RateLevel in_rate, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus flush(int32_t in_sensorHandle) = 0;
  virtual ::ndk::ScopedAStatus getSensorsList(std::vector<::aidl::android::hardware::sensors::SensorInfo>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus initialize(const ::aidl::android::hardware::common::fmq::MQDescriptor<::aidl::android::hardware::sensors::Event, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_eventQueueDescriptor, const ::aidl::android::hardware::common::fmq::MQDescriptor<int32_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_wakeLockDescriptor, const std::shared_ptr<::aidl::android::hardware::sensors::ISensorsCallback>& in_sensorsCallback) = 0;
  virtual ::ndk::ScopedAStatus injectSensorData(const ::aidl::android::hardware::sensors::Event& in_event) = 0;
  virtual ::ndk::ScopedAStatus registerDirectChannel(const ::aidl::android::hardware::sensors::ISensors::SharedMemInfo& in_mem, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setOperationMode(::aidl::android::hardware::sensors::ISensors::OperationMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus unregisterDirectChannel(int32_t in_channelHandle) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISensors> default_impl;
};
class ISensorsDefault : public ISensors {
public:
  ::ndk::ScopedAStatus activate(int32_t in_sensorHandle, bool in_enabled) override;
  ::ndk::ScopedAStatus batch(int32_t in_sensorHandle, int64_t in_samplingPeriodNs, int64_t in_maxReportLatencyNs) override;
  ::ndk::ScopedAStatus configDirectReport(int32_t in_sensorHandle, int32_t in_channelHandle, ::aidl::android::hardware::sensors::ISensors::RateLevel in_rate, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus flush(int32_t in_sensorHandle) override;
  ::ndk::ScopedAStatus getSensorsList(std::vector<::aidl::android::hardware::sensors::SensorInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus initialize(const ::aidl::android::hardware::common::fmq::MQDescriptor<::aidl::android::hardware::sensors::Event, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_eventQueueDescriptor, const ::aidl::android::hardware::common::fmq::MQDescriptor<int32_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_wakeLockDescriptor, const std::shared_ptr<::aidl::android::hardware::sensors::ISensorsCallback>& in_sensorsCallback) override;
  ::ndk::ScopedAStatus injectSensorData(const ::aidl::android::hardware::sensors::Event& in_event) override;
  ::ndk::ScopedAStatus registerDirectChannel(const ::aidl::android::hardware::sensors::ISensors::SharedMemInfo& in_mem, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setOperationMode(::aidl::android::hardware::sensors::ISensors::OperationMode in_mode) override;
  ::ndk::ScopedAStatus unregisterDirectChannel(int32_t in_channelHandle) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(ISensors::RateLevel val) {
  switch(val) {
  case ISensors::RateLevel::STOP:
    return "STOP";
  case ISensors::RateLevel::NORMAL:
    return "NORMAL";
  case ISensors::RateLevel::FAST:
    return "FAST";
  case ISensors::RateLevel::VERY_FAST:
    return "VERY_FAST";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::ISensors::RateLevel, 4> enum_values<aidl::android::hardware::sensors::ISensors::RateLevel> = {
  aidl::android::hardware::sensors::ISensors::RateLevel::STOP,
  aidl::android::hardware::sensors::ISensors::RateLevel::NORMAL,
  aidl::android::hardware::sensors::ISensors::RateLevel::FAST,
  aidl::android::hardware::sensors::ISensors::RateLevel::VERY_FAST,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(ISensors::OperationMode val) {
  switch(val) {
  case ISensors::OperationMode::NORMAL:
    return "NORMAL";
  case ISensors::OperationMode::DATA_INJECTION:
    return "DATA_INJECTION";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::ISensors::OperationMode, 2> enum_values<aidl::android::hardware::sensors::ISensors::OperationMode> = {
  aidl::android::hardware::sensors::ISensors::OperationMode::NORMAL,
  aidl::android::hardware::sensors::ISensors::OperationMode::DATA_INJECTION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(ISensors::SharedMemInfo::SharedMemFormat val) {
  switch(val) {
  case ISensors::SharedMemInfo::SharedMemFormat::SENSORS_EVENT:
    return "SENSORS_EVENT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemFormat, 1> enum_values<aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemFormat> = {
  aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemFormat::SENSORS_EVENT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(ISensors::SharedMemInfo::SharedMemType val) {
  switch(val) {
  case ISensors::SharedMemInfo::SharedMemType::ASHMEM:
    return "ASHMEM";
  case ISensors::SharedMemInfo::SharedMemType::GRALLOC:
    return "GRALLOC";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType, 2> enum_values<aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType> = {
  aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType::ASHMEM,
  aidl::android::hardware::sensors::ISensors::SharedMemInfo::SharedMemType::GRALLOC,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
