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
namespace sensors {
enum class SensorType : int32_t {
  META_DATA = 0,
  ACCELEROMETER = 1,
  MAGNETIC_FIELD = 2,
  ORIENTATION = 3,
  GYROSCOPE = 4,
  LIGHT = 5,
  PRESSURE = 6,
  PROXIMITY = 8,
  GRAVITY = 9,
  LINEAR_ACCELERATION = 10,
  ROTATION_VECTOR = 11,
  RELATIVE_HUMIDITY = 12,
  AMBIENT_TEMPERATURE = 13,
  MAGNETIC_FIELD_UNCALIBRATED = 14,
  GAME_ROTATION_VECTOR = 15,
  GYROSCOPE_UNCALIBRATED = 16,
  SIGNIFICANT_MOTION = 17,
  STEP_DETECTOR = 18,
  STEP_COUNTER = 19,
  GEOMAGNETIC_ROTATION_VECTOR = 20,
  HEART_RATE = 21,
  TILT_DETECTOR = 22,
  WAKE_GESTURE = 23,
  GLANCE_GESTURE = 24,
  PICK_UP_GESTURE = 25,
  WRIST_TILT_GESTURE = 26,
  DEVICE_ORIENTATION = 27,
  POSE_6DOF = 28,
  STATIONARY_DETECT = 29,
  MOTION_DETECT = 30,
  HEART_BEAT = 31,
  DYNAMIC_SENSOR_META = 32,
  ADDITIONAL_INFO = 33,
  LOW_LATENCY_OFFBODY_DETECT = 34,
  ACCELEROMETER_UNCALIBRATED = 35,
  HINGE_ANGLE = 36,
  HEAD_TRACKER = 37,
  ACCELEROMETER_LIMITED_AXES = 38,
  GYROSCOPE_LIMITED_AXES = 39,
  ACCELEROMETER_LIMITED_AXES_UNCALIBRATED = 40,
  GYROSCOPE_LIMITED_AXES_UNCALIBRATED = 41,
  HEADING = 42,
  DEVICE_PRIVATE_BASE = 65536,
};

}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(SensorType val) {
  switch(val) {
  case SensorType::META_DATA:
    return "META_DATA";
  case SensorType::ACCELEROMETER:
    return "ACCELEROMETER";
  case SensorType::MAGNETIC_FIELD:
    return "MAGNETIC_FIELD";
  case SensorType::ORIENTATION:
    return "ORIENTATION";
  case SensorType::GYROSCOPE:
    return "GYROSCOPE";
  case SensorType::LIGHT:
    return "LIGHT";
  case SensorType::PRESSURE:
    return "PRESSURE";
  case SensorType::PROXIMITY:
    return "PROXIMITY";
  case SensorType::GRAVITY:
    return "GRAVITY";
  case SensorType::LINEAR_ACCELERATION:
    return "LINEAR_ACCELERATION";
  case SensorType::ROTATION_VECTOR:
    return "ROTATION_VECTOR";
  case SensorType::RELATIVE_HUMIDITY:
    return "RELATIVE_HUMIDITY";
  case SensorType::AMBIENT_TEMPERATURE:
    return "AMBIENT_TEMPERATURE";
  case SensorType::MAGNETIC_FIELD_UNCALIBRATED:
    return "MAGNETIC_FIELD_UNCALIBRATED";
  case SensorType::GAME_ROTATION_VECTOR:
    return "GAME_ROTATION_VECTOR";
  case SensorType::GYROSCOPE_UNCALIBRATED:
    return "GYROSCOPE_UNCALIBRATED";
  case SensorType::SIGNIFICANT_MOTION:
    return "SIGNIFICANT_MOTION";
  case SensorType::STEP_DETECTOR:
    return "STEP_DETECTOR";
  case SensorType::STEP_COUNTER:
    return "STEP_COUNTER";
  case SensorType::GEOMAGNETIC_ROTATION_VECTOR:
    return "GEOMAGNETIC_ROTATION_VECTOR";
  case SensorType::HEART_RATE:
    return "HEART_RATE";
  case SensorType::TILT_DETECTOR:
    return "TILT_DETECTOR";
  case SensorType::WAKE_GESTURE:
    return "WAKE_GESTURE";
  case SensorType::GLANCE_GESTURE:
    return "GLANCE_GESTURE";
  case SensorType::PICK_UP_GESTURE:
    return "PICK_UP_GESTURE";
  case SensorType::WRIST_TILT_GESTURE:
    return "WRIST_TILT_GESTURE";
  case SensorType::DEVICE_ORIENTATION:
    return "DEVICE_ORIENTATION";
  case SensorType::POSE_6DOF:
    return "POSE_6DOF";
  case SensorType::STATIONARY_DETECT:
    return "STATIONARY_DETECT";
  case SensorType::MOTION_DETECT:
    return "MOTION_DETECT";
  case SensorType::HEART_BEAT:
    return "HEART_BEAT";
  case SensorType::DYNAMIC_SENSOR_META:
    return "DYNAMIC_SENSOR_META";
  case SensorType::ADDITIONAL_INFO:
    return "ADDITIONAL_INFO";
  case SensorType::LOW_LATENCY_OFFBODY_DETECT:
    return "LOW_LATENCY_OFFBODY_DETECT";
  case SensorType::ACCELEROMETER_UNCALIBRATED:
    return "ACCELEROMETER_UNCALIBRATED";
  case SensorType::HINGE_ANGLE:
    return "HINGE_ANGLE";
  case SensorType::HEAD_TRACKER:
    return "HEAD_TRACKER";
  case SensorType::ACCELEROMETER_LIMITED_AXES:
    return "ACCELEROMETER_LIMITED_AXES";
  case SensorType::GYROSCOPE_LIMITED_AXES:
    return "GYROSCOPE_LIMITED_AXES";
  case SensorType::ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
    return "ACCELEROMETER_LIMITED_AXES_UNCALIBRATED";
  case SensorType::GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
    return "GYROSCOPE_LIMITED_AXES_UNCALIBRATED";
  case SensorType::HEADING:
    return "HEADING";
  case SensorType::DEVICE_PRIVATE_BASE:
    return "DEVICE_PRIVATE_BASE";
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
constexpr inline std::array<aidl::android::hardware::sensors::SensorType, 43> enum_values<aidl::android::hardware::sensors::SensorType> = {
  aidl::android::hardware::sensors::SensorType::META_DATA,
  aidl::android::hardware::sensors::SensorType::ACCELEROMETER,
  aidl::android::hardware::sensors::SensorType::MAGNETIC_FIELD,
  aidl::android::hardware::sensors::SensorType::ORIENTATION,
  aidl::android::hardware::sensors::SensorType::GYROSCOPE,
  aidl::android::hardware::sensors::SensorType::LIGHT,
  aidl::android::hardware::sensors::SensorType::PRESSURE,
  aidl::android::hardware::sensors::SensorType::PROXIMITY,
  aidl::android::hardware::sensors::SensorType::GRAVITY,
  aidl::android::hardware::sensors::SensorType::LINEAR_ACCELERATION,
  aidl::android::hardware::sensors::SensorType::ROTATION_VECTOR,
  aidl::android::hardware::sensors::SensorType::RELATIVE_HUMIDITY,
  aidl::android::hardware::sensors::SensorType::AMBIENT_TEMPERATURE,
  aidl::android::hardware::sensors::SensorType::MAGNETIC_FIELD_UNCALIBRATED,
  aidl::android::hardware::sensors::SensorType::GAME_ROTATION_VECTOR,
  aidl::android::hardware::sensors::SensorType::GYROSCOPE_UNCALIBRATED,
  aidl::android::hardware::sensors::SensorType::SIGNIFICANT_MOTION,
  aidl::android::hardware::sensors::SensorType::STEP_DETECTOR,
  aidl::android::hardware::sensors::SensorType::STEP_COUNTER,
  aidl::android::hardware::sensors::SensorType::GEOMAGNETIC_ROTATION_VECTOR,
  aidl::android::hardware::sensors::SensorType::HEART_RATE,
  aidl::android::hardware::sensors::SensorType::TILT_DETECTOR,
  aidl::android::hardware::sensors::SensorType::WAKE_GESTURE,
  aidl::android::hardware::sensors::SensorType::GLANCE_GESTURE,
  aidl::android::hardware::sensors::SensorType::PICK_UP_GESTURE,
  aidl::android::hardware::sensors::SensorType::WRIST_TILT_GESTURE,
  aidl::android::hardware::sensors::SensorType::DEVICE_ORIENTATION,
  aidl::android::hardware::sensors::SensorType::POSE_6DOF,
  aidl::android::hardware::sensors::SensorType::STATIONARY_DETECT,
  aidl::android::hardware::sensors::SensorType::MOTION_DETECT,
  aidl::android::hardware::sensors::SensorType::HEART_BEAT,
  aidl::android::hardware::sensors::SensorType::DYNAMIC_SENSOR_META,
  aidl::android::hardware::sensors::SensorType::ADDITIONAL_INFO,
  aidl::android::hardware::sensors::SensorType::LOW_LATENCY_OFFBODY_DETECT,
  aidl::android::hardware::sensors::SensorType::ACCELEROMETER_UNCALIBRATED,
  aidl::android::hardware::sensors::SensorType::HINGE_ANGLE,
  aidl::android::hardware::sensors::SensorType::HEAD_TRACKER,
  aidl::android::hardware::sensors::SensorType::ACCELEROMETER_LIMITED_AXES,
  aidl::android::hardware::sensors::SensorType::GYROSCOPE_LIMITED_AXES,
  aidl::android::hardware::sensors::SensorType::ACCELEROMETER_LIMITED_AXES_UNCALIBRATED,
  aidl::android::hardware::sensors::SensorType::GYROSCOPE_LIMITED_AXES_UNCALIBRATED,
  aidl::android::hardware::sensors::SensorType::HEADING,
  aidl::android::hardware::sensors::SensorType::DEVICE_PRIVATE_BASE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
