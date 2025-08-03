/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <cinttypes>

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_api/chre.h"

#define LOG_TAG "[SensorWorld]"

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

using chre::kOneMillisecondInNanoseconds;
using chre::Milliseconds;
using chre::Seconds;

namespace {

//! Enable BreakIt test mode.
// In BreakIt test mode, a timer will be set periodically to randomly
// enable/disable each sensor.
constexpr bool kBreakIt = false;
constexpr Milliseconds kBreakItPeriod = Milliseconds(2000);
uint32_t gBreakItTimerHandle;

//! Enable chreSensorFlushAsync test
// When enabled, SensorWorld will set a timer to invoke
// chreSensorFlushAsync(sensors[kFlushSensorIndex].handle)
// halfway through sensors[kFlushSensorIndex].latency
//
// If CHRE_EVENT_SENSOR_FLUSH_COMPLETE is not received before
// kFlushItTimeout expires, an error message will be logged.
constexpr bool kFlushIt = true;
constexpr uint32_t kFlushCookie = 0xdeadbeef;
constexpr uint32_t kFlushSensorIndex = 0;  // CHRE_SENSOR_TYPE_ACCELEROMETER
uint32_t gFlushItTimerHandle;

constexpr Milliseconds kFlushItTimeout = Milliseconds(5000);
uint32_t gFlushItTimeoutTimerHandle;

//! Whether to enable sensor event logging or not.
constexpr bool kEnableSensorEventLogging = true;

//! Enable/disable all sensors by default.
// This allows disabling all sensens by default and enabling only targeted
// sensors for testing by locally overriding 'enable' field in SensorState.
// Note that enabling BreakIt test disables all sensors at init by default.
constexpr bool kEnableDefault = !kBreakIt;

struct SensorState {
  const uint8_t type;
  const uint8_t sensorIndex;
  uint32_t handle;
  bool isInitialized;
  bool enable;
  uint64_t interval;  // nsec
  uint64_t latency;   // nsec
  chreSensorInfo info;
};

SensorState sensors[] = {
    {
        .type = CHRE_SENSOR_TYPE_ACCELEROMETER,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = false,  // InstantMotion is triggered by Prox
        .interval = CHRE_SENSOR_INTERVAL_DEFAULT,
        .latency = CHRE_SENSOR_LATENCY_DEFAULT,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_STATIONARY_DETECT,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = false,  // StationaryDetect is triggered by Prox
        .interval = CHRE_SENSOR_INTERVAL_DEFAULT,
        .latency = CHRE_SENSOR_LATENCY_DEFAULT,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_GYROSCOPE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_GEOMAGNETIC_FIELD,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_PRESSURE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(200).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_LIGHT,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(200).toRawNanoseconds(),
        .latency = 0,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_PROXIMITY,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(200).toRawNanoseconds(),
        .latency = 0,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_STEP_DETECT,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = CHRE_SENSOR_INTERVAL_DEFAULT,
        .latency = CHRE_SENSOR_LATENCY_ASAP,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_STEP_COUNTER,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = CHRE_SENSOR_INTERVAL_DEFAULT,
        .latency = CHRE_SENSOR_LATENCY_ASAP,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_ACCELEROMETER_TEMPERATURE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Seconds(2).toRawNanoseconds(),
        .latency = 0,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_GYROSCOPE_TEMPERATURE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Seconds(2).toRawNanoseconds(),
        .latency = 0,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_GEOMAGNETIC_FIELD_TEMPERATURE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Seconds(2).toRawNanoseconds(),
        .latency = 0,
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_UNCALIBRATED_ACCELEROMETER,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_UNCALIBRATED_GYROSCOPE,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
    {
        .type = CHRE_SENSOR_TYPE_UNCALIBRATED_GEOMAGNETIC_FIELD,
        .sensorIndex = 0,
        .handle = 0,
        .isInitialized = false,
        .enable = kEnableDefault,
        .interval = Milliseconds(80).toRawNanoseconds(),
        .latency = Seconds(4).toRawNanoseconds(),
        .info = {},
    },
};

// Conditional logging macro
#define CLOGI(fmt, ...)              \
  do {                               \
    if (kEnableSensorEventLogging) { \
      LOGI(fmt, ##__VA_ARGS__);      \
    }                                \
  } while (0);

// Helpers for testing InstantMotion and StationaryDetect
enum class MotionMode {
  Instant,
  Stationary,
};

// Storage to help access InstantMotion and StationaryDetect sensor handle and
// info
size_t motionSensorIndices[2];
MotionMode motionMode = MotionMode::Instant;

size_t getMotionSensorIndex() {
  motionMode = (motionMode == MotionMode::Instant) ? MotionMode::Stationary
                                                   : MotionMode::Instant;
  return motionSensorIndices[static_cast<size_t>(motionMode)];
}

//! Used to loop through all sensors to query sensor sampling status.
size_t statusIndex = 0;

// Obtains 16-bit psuedo-random numbers.
uint16_t getNextLfsrState() {
  // 15-bit LFSR with feedback polynomial x^15 + x^14 + 1 gives us a
  // pseudo-random sequence over all 32767 possible values
  static uint16_t lfsr = 0x1337;
  uint16_t nextBit = ((lfsr << 14) ^ (lfsr << 13)) & 0x4000;
  lfsr = nextBit | (lfsr >> 1);

  return lfsr;
}

const char *getSensorName(uint32_t sensorHandle) {
  for (size_t i = 0; i < ARRAY_SIZE(sensors); i++) {
    if (sensors[i].handle == sensorHandle) {
      return sensors[i].info.sensorName;
    }
  }
  return nullptr;
}

void handleTimerEvent(const uint32_t *ev) {
  if (*ev == gFlushItTimerHandle) {
    LOGI("FlushIt Timer Fired");
    if (chreSensorFlushAsync(sensors[kFlushSensorIndex].handle,
                             &kFlushCookie)) {
      gFlushItTimeoutTimerHandle =
          chreTimerSet(kFlushItTimeout.toRawNanoseconds(),
                       &gFlushItTimeoutTimerHandle, true /* oneShot */);
    } else {
      LOGE("chreSensorFlushAsync failed");
    }

  } else if (*ev == gFlushItTimeoutTimerHandle) {
    LOGE("chreSensorFlushAsync Timeout");

  } else if (*ev == gBreakItTimerHandle) {
    for (size_t i = 0; i < ARRAY_SIZE(sensors); i++) {
      SensorState &sensor = sensors[i];

      bool enable = getNextLfsrState() & 0x1;
      if (sensor.isInitialized && sensor.enable != enable) {
        sensor.enable = enable;

        bool status;
        if (!enable) {
          status = chreSensorConfigureModeOnly(sensor.handle,
                                               CHRE_SENSOR_CONFIGURE_MODE_DONE);
        } else {
          enum chreSensorConfigureMode mode =
              sensor.info.isOneShot ? CHRE_SENSOR_CONFIGURE_MODE_ONE_SHOT
                                    : CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS;
          status = chreSensorConfigure(sensor.handle, mode, sensor.interval,
                                       sensor.latency);
        }

        LOGI("Configure [enable %d, status %d]: %s", enable, status,
             sensor.info.sensorName);
      }
    }

    gBreakItTimerHandle =
        chreTimerSet(kBreakItPeriod.toRawNanoseconds(), &gBreakItTimerHandle,
                     true /* oneShot */);
  }
}

void handleThreeAxisEvent(const chreSensorThreeAxisData *ev,
                          uint16_t eventType) {
  const auto header = ev->header;
  const auto *data = ev->readings;
  const auto accuracy = header.accuracy;
  uint64_t sampleTime = header.baseTimestamp;
  uint64_t chreTime = chreGetTime();

  float x = 0, y = 0, z = 0;
  for (size_t i = 0; i < header.readingCount; i++) {
    x += data[i].v[0];
    y += data[i].v[1];
    z += data[i].v[2];
    sampleTime += data[i].timestampDelta;
  }
  x /= header.readingCount;
  y /= header.readingCount;
  z /= header.readingCount;

  CLOGI("%s, %d samples: %f %f %f, accuracy: %u, t=%" PRIu64 " ms",
        getSensorName(header.sensorHandle), header.readingCount,
        static_cast<double>(x), static_cast<double>(y), static_cast<double>(z),
        accuracy, header.baseTimestamp / kOneMillisecondInNanoseconds);

  if (eventType == CHRE_EVENT_SENSOR_UNCALIBRATED_GYROSCOPE_DATA) {
    CLOGI("UncalGyro time: first %" PRIu64 " last %" PRIu64 " chre %" PRIu64
          " delta [%" PRId64 ", %" PRId64 "]ms",
          header.baseTimestamp, sampleTime, chreTime,
          static_cast<int64_t>(header.baseTimestamp - chreTime) /
              static_cast<int64_t>(kOneMillisecondInNanoseconds),
          static_cast<int64_t>(sampleTime - chreTime) /
              static_cast<int64_t>(kOneMillisecondInNanoseconds));
  }
}

void handleFloatEvent(const chreSensorFloatData *ev) {
  const auto header = ev->header;

  float v = 0;
  for (size_t i = 0; i < header.readingCount; i++) {
    v += ev->readings[i].value;
  }
  v /= header.readingCount;

  CLOGI("%s, %d samples: %f, accuracy = %u, t=%" PRIu64 " ms",
        getSensorName(header.sensorHandle), header.readingCount,
        static_cast<double>(v), header.accuracy,
        header.baseTimestamp / kOneMillisecondInNanoseconds);
}

void handleProximityEvent(const chreSensorByteData *ev) {
  const auto header = ev->header;
  const auto reading = ev->readings[0];
  uint64_t sampleTime = header.baseTimestamp;
  uint64_t chreTime = chreGetTime();

  CLOGI("%s, %d samples: isNear %d, invalid %d, accuracy: %u",
        getSensorName(header.sensorHandle), header.readingCount, reading.isNear,
        reading.invalid, header.accuracy);

  CLOGI("Prox time: sample %" PRIu64 " chre %" PRIu64 " delta %" PRId64 "ms",
        header.baseTimestamp, chreTime,
        static_cast<int64_t>(sampleTime - chreTime) / 1000000);

  // Enable InstantMotion and StationaryDetect alternatively on near->far.
  if (reading.isNear == 0 && !kBreakIt) {
    size_t motionSensorIndex = getMotionSensorIndex();
    bool status = chreSensorConfigure(
        sensors[motionSensorIndex].handle, CHRE_SENSOR_CONFIGURE_MODE_ONE_SHOT,
        CHRE_SENSOR_INTERVAL_DEFAULT, CHRE_SENSOR_LATENCY_DEFAULT);
    LOGI("Requested %s: %s", sensors[motionSensorIndex].info.sensorName,
         status ? "success" : "failure");
  }

  // Exercise chreGetSensorSamplingStatus on one sensor on near->far.
  if (sensors[statusIndex].isInitialized && reading.isNear == 0) {
    struct chreSensorSamplingStatus status;
    bool success =
        chreGetSensorSamplingStatus(sensors[statusIndex].handle, &status);
    LOGI("%s success %d: enabled %d interval %" PRIu64 " latency %" PRIu64,
         sensors[statusIndex].info.sensorName, success, status.enabled,
         status.interval, status.latency);
  }
  statusIndex = (statusIndex + 1) % ARRAY_SIZE(sensors);
}

}  // namespace

bool nanoappStart() {
  LOGI("App started on platform ID %" PRIx64, chreGetPlatformId());

  for (size_t i = 0; i < ARRAY_SIZE(sensors); i++) {
    SensorState &sensor = sensors[i];
    sensor.isInitialized =
        chreSensorFind(sensor.type, sensor.sensorIndex, &sensor.handle);
    LOGI("Sensor %zu initialized: %s with handle %" PRIu32, i,
         sensor.isInitialized ? "true" : "false", sensor.handle);

    if (sensor.type == CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT) {
      motionSensorIndices[static_cast<size_t>(MotionMode::Instant)] = i;
    } else if (sensor.type == CHRE_SENSOR_TYPE_STATIONARY_DETECT) {
      motionSensorIndices[static_cast<size_t>(MotionMode::Stationary)] = i;
    }

    if (sensor.isInitialized) {
      // Get sensor info
      chreSensorInfo &info = sensor.info;
      bool infoStatus = chreGetSensorInfo(sensor.handle, &info);
      if (infoStatus) {
        LOGI("SensorInfo: %s, Type=%" PRIu8
             " OnChange=%d OneShot=%d Passive=%d "
             "minInterval=%" PRIu64 "nsec",
             info.sensorName, info.sensorType, info.isOnChange, info.isOneShot,
             info.supportsPassiveMode, info.minInterval);
      } else {
        LOGE("chreGetSensorInfo failed");
      }

      // Subscribe to sensors
      if (sensor.enable) {
        float odrHz = 1e9f / static_cast<float>(sensor.interval);
        float latencySec = static_cast<float>(sensor.latency) / 1e9f;
        bool status = chreSensorConfigure(sensor.handle,
                                          CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS,
                                          sensor.interval, sensor.latency);
        LOGI("Requested data: odr %f Hz, latency %f sec, %s",
             static_cast<double>(odrHz), static_cast<double>(latencySec),
             status ? "success" : "failure");
      }
    }
  }

  // Set timer for BreakIt test.
  if (kBreakIt) {
    gBreakItTimerHandle =
        chreTimerSet(kBreakItPeriod.toRawNanoseconds(), &gBreakItTimerHandle,
                     true /* oneShot */);
  }

  if (kFlushIt) {
    // Triger a flush half way through the target sensor latency
    gFlushItTimerHandle =
        chreTimerSet(sensors[kFlushSensorIndex].latency / 2,
                     &gFlushItTimerHandle, true /* oneShot */);
  }

  return true;
}

void nanoappHandleEvent(uint32_t senderInstanceId, uint16_t eventType,
                        const void *eventData) {
  UNUSED_VAR(senderInstanceId);

  switch (eventType) {
    case CHRE_EVENT_SENSOR_ACCELEROMETER_DATA:
    case CHRE_EVENT_SENSOR_UNCALIBRATED_ACCELEROMETER_DATA:
    case CHRE_EVENT_SENSOR_GYROSCOPE_DATA:
    case CHRE_EVENT_SENSOR_UNCALIBRATED_GYROSCOPE_DATA:
    case CHRE_EVENT_SENSOR_GEOMAGNETIC_FIELD_DATA:
    case CHRE_EVENT_SENSOR_UNCALIBRATED_GEOMAGNETIC_FIELD_DATA:
      handleThreeAxisEvent(
          static_cast<const chreSensorThreeAxisData *>(eventData), eventType);
      break;

    case CHRE_EVENT_SENSOR_PRESSURE_DATA:
    case CHRE_EVENT_SENSOR_LIGHT_DATA:
    case CHRE_EVENT_SENSOR_ACCELEROMETER_TEMPERATURE_DATA:
    case CHRE_EVENT_SENSOR_GYROSCOPE_TEMPERATURE_DATA:
    case CHRE_EVENT_SENSOR_GEOMAGNETIC_FIELD_TEMPERATURE_DATA:
      handleFloatEvent(static_cast<const chreSensorFloatData *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_PROXIMITY_DATA:
      handleProximityEvent(static_cast<const chreSensorByteData *>(eventData));
      break;

    case CHRE_EVENT_SENSOR_INSTANT_MOTION_DETECT_DATA:
    case CHRE_EVENT_SENSOR_STATIONARY_DETECT_DATA:
    case CHRE_EVENT_SENSOR_STEP_DETECT_DATA: {
      const auto *ev = static_cast<const chreSensorOccurrenceData *>(eventData);
      const auto header = ev->header;

      CLOGI("%s, %d samples, accuracy: %u", getSensorName(header.sensorHandle),
            header.readingCount, header.accuracy);
      break;
    }

    case CHRE_EVENT_SENSOR_STEP_COUNTER_DATA: {
      const auto *ev = static_cast<const chreSensorUint64Data *>(eventData);
      const auto header = ev->header;
      const uint64_t reading = ev->readings[header.readingCount - 1].value;

      CLOGI("%s, %" PRIu16 " samples: latest %" PRIu64,
            getSensorName(header.sensorHandle), header.readingCount, reading);
      break;
    }

    case CHRE_EVENT_SENSOR_SAMPLING_CHANGE: {
      const auto *ev =
          static_cast<const chreSensorSamplingStatusEvent *>(eventData);

      CLOGI("Sampling Change: handle %" PRIu32 ", status: interval %" PRIu64
            " latency %" PRIu64 " enabled %d",
            ev->sensorHandle, ev->status.interval, ev->status.latency,
            ev->status.enabled);
      break;
    }

    case CHRE_EVENT_TIMER:
      if (kBreakIt || kFlushIt) {
        handleTimerEvent(static_cast<const uint32_t *>(eventData));
      } else {
        LOGE("Timer event received with kBreakIt and kFlushIt disabled");
      }
      break;

    case CHRE_EVENT_SENSOR_FLUSH_COMPLETE: {
      const auto *ev =
          static_cast<const chreSensorFlushCompleteEvent *>(eventData);
      chreTimerCancel(gFlushItTimeoutTimerHandle);

      LOGI("Flush Complete: handle %" PRIu32 ", errorCode: %d",
           ev->sensorHandle, ev->errorCode);
      break;
    }

    default:
      LOGW("Unhandled event %d", eventType);
      break;
  }
}

void nanoappEnd() {
  LOGI("Stopped");
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // anonymous namespace
}  // namespace chre

#include "chre/platform/static_nanoapp_init.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"

CHRE_STATIC_NANOAPP_INIT(SensorWorld, chre::kSensorWorldAppId, 0,
                         chre::NanoappPermissions::CHRE_PERMS_NONE);
#endif  // CHRE_NANOAPP_INTERNAL
