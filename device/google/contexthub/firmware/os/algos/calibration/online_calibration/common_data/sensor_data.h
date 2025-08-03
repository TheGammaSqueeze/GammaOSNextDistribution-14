/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This module provides the component definitions used to represent sensor
 * data employed by the online sensor calibration algorithms.
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_ONLINE_CALIBRATION_COMMON_DATA_SENSOR_DATA_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_ONLINE_CALIBRATION_COMMON_DATA_SENSOR_DATA_H_

#include <sys/types.h>

#include <cstdint>
#include <cstring>

#include "common/math/macros.h"

namespace online_calibration {

// Defines an invalid or uninitialized temperature value (referenced from
// common/math/macros.h).
constexpr float kInvalidTemperatureCelsius = INVALID_TEMPERATURE_CELSIUS;

// Unit conversion from nanoseconds to microseconds.
constexpr uint64_t NanoToMicroseconds(uint64_t x) { return x / 1000; }

// Identifies the various sensing devices used by the calibration algorithms.
enum class SensorType : int8_t {
  kUndefined = 0,
  kAccelerometerMps2 = 1,   // 3-axis sensor (units = meter/sec^2).
  kGyroscopeRps = 2,        // 3-axis sensor (units = radian/sec).
  kMagnetometerUt = 3,      // 3-axis sensor (units = micro-Tesla).
  kTemperatureCelsius = 4,  // 1-axis sensor (units = degrees Celsius).
  kBarometerHpa = 5,        // 1-axis sensor (units = hecto-Pascal).
  kWifiM = 6,               // 3-axis sensor (units = meter).
  kProximity = 7,           // 1-axis sensor (units = ?).
};

// Helper function for determining if a sensor type is 3-axis, otherwise it's
// single-axis.
inline bool is_three_axis(SensorType type) {
  return type == SensorType::kAccelerometerMps2 ||
         type == SensorType::kGyroscopeRps ||
         type == SensorType::kMagnetometerUt;
}

/*
 * SensorData is a generalized data structure used to represent sensor samples
 * produced by either a single- or three-axis device. Usage is implied through
 * the sensor type (i.e., Gyroscope is a three-axis sensor and would therefore
 * use all elements of 'data'; a pressure sensor is single-dimensional and would
 * use 'data[SensorIndex::kSingleAxis]'). This arbitration is determined
 * at the algorithm wrapper level where knowledge of a sensor's dimensionality
 * is clearly understood.
 *
 * NOTE: The unified dimensional representation makes it convenient to pass
 * either type of data into the interface functions defined in the
 * OnlineCalibration. Additionally, this approach, although admittedly wasteful
 * for single-axis sensors, allows one to avoid dynamic memory allocation for
 * hardware systems where having a fixed memory footprint is either required or
 * extremely desireable.
 */

// Axis index definitions for SensorData::data.
enum SensorIndex : int8_t {
  kSingleAxis = 0,
  kXAxis = kSingleAxis,
  kYAxis = 1,
  kZAxis = 2,
};

struct SensorData {
  // Sensor sample timestamp.
  uint64_t timestamp_nanos;

  // Generalized sensor sample (represents either single- or three-axis data).
  float data[3];

  // Indicates the type of sensor this data originated from.
  SensorType type;

  SensorData() : timestamp_nanos(0), type(SensorType::kUndefined) {
    memset(data, 0, sizeof(data));
  }

  SensorData(SensorType type, uint64_t timestamp_nanos, float x_axis,
             float y_axis, float z_axis)
      : timestamp_nanos(timestamp_nanos), type(type) {
    data[SensorIndex::kXAxis] = x_axis;
    data[SensorIndex::kYAxis] = y_axis;
    data[SensorIndex::kZAxis] = z_axis;
  }

  SensorData(SensorType type, uint64_t timestamp_nanos,
             float single_axis_sample)
      : timestamp_nanos(timestamp_nanos), type(type) {
    data[SensorIndex::kSingleAxis] = single_axis_sample;
  }
};

}  // namespace online_calibration

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_ONLINE_CALIBRATION_COMMON_DATA_SENSOR_DATA_H_
