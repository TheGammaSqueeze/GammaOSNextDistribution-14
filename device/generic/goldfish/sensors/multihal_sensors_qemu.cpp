/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <log/log.h>
#include <utils/SystemClock.h>
#include <math.h>
#include <random>
#include <multihal_sensors.h>
#include "sensor_list.h"

namespace goldfish {
using ahs10::EventPayload;
using ahs21::SensorType;
using ahs10::SensorStatus;

namespace {
const char* testPrefix(const char* i, const char* end, const char* v, const char sep) {
    while (i < end) {
        if (*v == 0) {
            return (*i == sep) ? (i + 1) : nullptr;
        } else if (*v == *i) {
            ++v;
            ++i;
        } else {
            return nullptr;
        }
    }

    return nullptr;
}

bool approximatelyEqual(double a, double b, double eps) {
    return fabs(a - b) <= std::max(fabs(a), fabs(b)) * eps;
}

int64_t weigthedAverage(const int64_t a, int64_t aw, int64_t b, int64_t bw) {
    return (a * aw + b * bw) / (aw + bw);
}

}  // namespace

bool MultihalSensors::setSensorsReportingImpl(SensorsTransport& st,
                                              const int sensorHandle,
                                              const bool enabled) {
    char buffer[64];
    int len = snprintf(buffer, sizeof(buffer),
                       "set:%s:%d",
                       getQemuSensorNameByHandle(sensorHandle),
                       (enabled ? 1 : 0));

    if (st.Send(buffer, len) < 0) {
        ALOGE("%s:%d: send for %s failed", __func__, __LINE__, st.Name());
        return false;
    } else {
        return true;
    }
}

bool MultihalSensors::setAllSensorsReporting(SensorsTransport& st,
                                             uint32_t availableSensorsMask,
                                             const bool enabled) {
    for (int i = 0; availableSensorsMask; ++i, availableSensorsMask >>= 1) {
        if (availableSensorsMask & 1) {
            if (!setSensorsReportingImpl(st, i, enabled)) {
                return false;
            }
        }
    }

    return true;
}

bool MultihalSensors::setSensorsGuestTime(SensorsTransport& st, const int64_t value) {
    char buffer[64];
    int len = snprintf(buffer, sizeof(buffer), "time:%" PRId64, value);
    if (st.Send(buffer, len) < 0) {
        ALOGE("%s:%d: send for %s failed", __func__, __LINE__, st.Name());
        return false;
    } else {
        return true;
    }
}

bool MultihalSensors::setSensorsUpdateIntervalMs(SensorsTransport& st,
                                                 const uint32_t intervalMs) {
    char buffer[64];
    const int len = snprintf(buffer, sizeof(buffer), "set-delay:%u", intervalMs);
    if (st.Send(buffer, len) < 0) {
        ALOGE("%s:%d: send for %s failed", __func__, __LINE__, st.Name());
        return false;
    } else {
        return true;
    }
}

double MultihalSensors::randomError(float lo, float hi) {
    std::uniform_real_distribution<> distribution(lo, hi);
    return distribution(gen);
}

void MultihalSensors::parseQemuSensorEventLocked(QemuSensorsProtocolState* state) {
    char buf[256];
    const int len = m_sensorsTransport->Receive(buf, sizeof(buf) - 1);
    if (len < 0) {
        ALOGE("%s:%d: receive for %s failed", __func__, __LINE__, m_sensorsTransport->Name());
    }
    const int64_t nowNs = ::android::elapsedRealtimeNano();
    buf[len] = 0;
    const char* end = buf + len;
    bool parsed = false;
    Event event;
    EventPayload* payload = &event.u;
    ahs10::Vec3* vec3 = &payload->vec3;
    ahs10::Uncal* uncal = &payload->uncal;
    if (const char* values = testPrefix(buf, end, "acceleration", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &vec3->x, &vec3->y, &vec3->z) == 3) {
            vec3->status = SensorStatus::ACCURACY_MEDIUM;
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleAccelerometer;
            event.sensorType = SensorType::ACCELEROMETER;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "acceleration-uncalibrated", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &uncal->x, &uncal->y, &uncal->z) == 3) {
            // A little bias noise to pass CTS
            uncal->x_bias = randomError(-0.003f, 0.003f);
            uncal->y_bias = randomError(-0.003f, 0.003f);
            uncal->z_bias = randomError(-0.003f, 0.003f);
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleAccelerometerUncalibrated;
            event.sensorType = SensorType::ACCELEROMETER_UNCALIBRATED;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "gyroscope", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &vec3->x, &vec3->y, &vec3->z) == 3) {
            vec3->status = SensorStatus::ACCURACY_MEDIUM;
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleGyroscope;
            event.sensorType = SensorType::GYROSCOPE;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "gyroscope-uncalibrated", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &uncal->x, &uncal->y, &uncal->z) == 3) {
            //Uncalibrated gyro values needs to be close to 0,0,0.
            uncal->x += randomError(0.00005f, 0.001f);
            uncal->y += randomError(0.00005f, 0.001f);
            uncal->z += randomError(0.00005f, 0.001f);
            // Bias noise
            uncal->x_bias = randomError(-0.0003f, 0.0003f);
            uncal->y_bias = randomError(-0.0003f, 0.0003f);
            uncal->z_bias = randomError(-0.0003f, 0.0003f);
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleGyroscopeFieldUncalibrated;
            event.sensorType = SensorType::GYROSCOPE_UNCALIBRATED;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "orientation", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &vec3->x, &vec3->y, &vec3->z) == 3) {
            vec3->status = SensorStatus::ACCURACY_HIGH;
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleOrientation;
            event.sensorType = SensorType::ORIENTATION;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "magnetic", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &vec3->x, &vec3->y, &vec3->z) == 3) {
            vec3->status = SensorStatus::ACCURACY_HIGH;
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleMagneticField;
            event.sensorType = SensorType::MAGNETIC_FIELD;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "magnetic-uncalibrated", ':')) {
        if (sscanf(values, "%f:%f:%f",
                   &uncal->x, &uncal->y, &uncal->z) == 3) {
            // A little bias noise to pass CTS
            uncal->x_bias = randomError( -0.003f, 0.003f);
            uncal->y_bias = randomError(-0.003f, 0.003f);
            uncal->z_bias = randomError(-0.003f, 0.003f);
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandleMagneticFieldUncalibrated;
            event.sensorType = SensorType::MAGNETIC_FIELD_UNCALIBRATED;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "temperature", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastAmbientTemperatureValue,
                                    payload->scalar, 0.001)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleAmbientTemperature;
                event.sensorType = SensorType::AMBIENT_TEMPERATURE;
                postSensorEventLocked(event);
                state->lastAmbientTemperatureValue = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "proximity", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastProximityValue,
                                    payload->scalar, 0.001)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleProximity;
                event.sensorType = SensorType::PROXIMITY;
                postSensorEventLocked(event);
                state->lastProximityValue = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "light", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastLightValue,
                                    payload->scalar, 0.001)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleLight;
                event.sensorType = SensorType::LIGHT;
                postSensorEventLocked(event);
                state->lastLightValue = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "pressure", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            event.timestamp = nowNs + state->timeBiasNs;
            event.sensorHandle = kSensorHandlePressure;
            event.sensorType = SensorType::PRESSURE;
            postSensorEventLocked(event);
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "humidity", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastRelativeHumidityValue,
                                    payload->scalar, 0.001)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleRelativeHumidity;
                event.sensorType = SensorType::RELATIVE_HUMIDITY;
                postSensorEventLocked(event);
                state->lastRelativeHumidityValue = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "hinge-angle0", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastHingeAngle0Value,
                                    payload->scalar, 0.001) &&
                // b/197586273, ignore the state tracking if system sensor
                // service has not enabled hinge sensor
                isSensorActive(kSensorHandleHingeAngle0)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleHingeAngle0;
                event.sensorType = SensorType::HINGE_ANGLE;
                postSensorEventLocked(event);
                state->lastHingeAngle0Value = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "hinge-angle1", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastHingeAngle1Value,
                                    payload->scalar, 0.001) &&
                isSensorActive(kSensorHandleHingeAngle1)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleHingeAngle1;
                event.sensorType = SensorType::HINGE_ANGLE;
                postSensorEventLocked(event);
                state->lastHingeAngle1Value = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "hinge-angle2", ':')) {
        if (sscanf(values, "%f", &payload->scalar) == 1) {
            if (!approximatelyEqual(state->lastHingeAngle2Value,
                                    payload->scalar, 0.001) &&
                isSensorActive(kSensorHandleHingeAngle2)) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleHingeAngle2;
                event.sensorType = SensorType::HINGE_ANGLE;
                postSensorEventLocked(event);
                state->lastHingeAngle2Value = payload->scalar;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "heart-rate", ':')) {
        if (sscanf(values, "%f", &payload->heartRate.bpm) == 1) {
            if (!approximatelyEqual(state->lastHeartRateValue,
                                    payload->heartRate.bpm, 0.001)) {
                payload->heartRate.status = SensorStatus::ACCURACY_HIGH;
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleHeartRate;
                event.sensorType = SensorType::HEART_RATE;
                postSensorEventLocked(event);
                state->lastHeartRateValue = payload->heartRate.bpm;
            }
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "wrist-tilt", ':')) {
        long measurementId;
        int args = sscanf(values, "%f:%ld", &payload->scalar, &measurementId);
        if (args == 2) {
            if (state->lastWristTiltMeasurement != measurementId) {
                event.timestamp = nowNs + state->timeBiasNs;
                event.sensorHandle = kSensorHandleWristTilt;
                event.sensorType = SensorType::WRIST_TILT_GESTURE;
                postSensorEventLocked(event);
                state->lastWristTiltMeasurement = measurementId;
            }
        }
        if (args >= 1) {
            // Skip if the measurement id is not included.
            parsed = true;
        }

     } else if (const char* values = testPrefix(buf, end, "guest-sync", ':')) {
        long long value;
        if ((sscanf(values, "%lld", &value) == 1) && (value >= 0)) {
            const int64_t guestTimeNs = static_cast<int64_t>(value * 1000LL);
            const int64_t timeBiasNs = guestTimeNs - nowNs;
            state->timeBiasNs =
                std::min(int64_t(0),
                         weigthedAverage(state->timeBiasNs, 3, timeBiasNs, 1));
            parsed = true;
        }
    } else if (const char* values = testPrefix(buf, end, "sync", ':')) {
        parsed = true;
    }

    if (!parsed) {
        ALOGW("%s:%d: don't know how to parse '%s'", __func__, __LINE__, buf);
    }
}

}  // namespace
