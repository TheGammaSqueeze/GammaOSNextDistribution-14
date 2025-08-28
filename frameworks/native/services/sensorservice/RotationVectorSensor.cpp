/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <stdint.h>
#include <math.h>
#include <sys/types.h>
#include <cutils/properties.h>

#include <utils/Errors.h>

#include <hardware/sensors.h>

#include "RotationVectorSensor.h"

namespace android {
// ---------------------------------------------------------------------------

RotationVectorSensor::RotationVectorSensor(int mode) :
      mMode(mode) {
    const sensor_t sensor = {
        .name       = getSensorName(),
        .vendor     = "AOSP",
        .version    = 3,
        .handle     = getSensorToken(),
        .type       = getSensorType(),
        .maxRange   = 1,
        .resolution = 1.0f / (1<<24),
        .power      = mSensorFusion.getPowerUsage(),
        .minDelay   = mSensorFusion.getMinDelay(),
    };
    mSensor = Sensor(&sensor);
}

bool RotationVectorSensor::process(sensors_event_t* outEvent,
        const sensors_event_t& event)
{
    if (event.type == SENSOR_TYPE_ACCELEROMETER) {
        if (mSensorFusion.hasEstimate(mMode)) {
            vec4_t q(mSensorFusion.getAttitude(mMode));
            // GammaOS: adjust quaternion by accelerometer mounting orientation
            {
                char prop[PROPERTY_VALUE_MAX];
                property_get("ro.sensors.accelerometer_orientation", prop, "0");
                int sensorRot = atoi(prop);
                if (sensorRot != 0) {
                    const float halfRad = (sensorRot * M_PI / 180.0f) * 0.5f;
                    // quaternion for rotation about Z axis by sensorRot degrees
                    vec4_t zrot;
                    zrot.x = 0.0f; zrot.y = 0.0f;
                    zrot.z = sinf(halfRad);
                    zrot.w = cosf(halfRad);
                    // q' = zrot * q
                    vec4_t out;
                    out.x = zrot.w * q.x + zrot.x * q.w + zrot.y * q.z - zrot.z * q.y;
                    out.y = zrot.w * q.y - zrot.x * q.z + zrot.y * q.w + zrot.z * q.x;
                    out.z = zrot.w * q.z + zrot.x * q.y - zrot.y * q.x + zrot.z * q.w;
                    out.w = zrot.w * q.w - zrot.x * q.x - zrot.y * q.y - zrot.z * q.z;
                    q = out;
                }
            }
            *outEvent = event;
            outEvent->data[0] = q.x;
            outEvent->data[1] = q.y;
            outEvent->data[2] = q.z;
            outEvent->data[3] = q.w;
            outEvent->sensor = getSensorToken();
            outEvent->type = getSensorType();
            return true;
        }
    }
    return false;
}

status_t RotationVectorSensor::activate(void* ident, bool enabled) {
    return mSensorFusion.activate(mMode, ident, enabled);
}

status_t RotationVectorSensor::setDelay(void* ident, int /*handle*/, int64_t ns) {
    return mSensorFusion.setDelay(mMode, ident, ns);
}

int RotationVectorSensor::getSensorType() const {
    switch(mMode) {
        case FUSION_9AXIS:
            return SENSOR_TYPE_ROTATION_VECTOR;
        case FUSION_NOMAG:
            return SENSOR_TYPE_GAME_ROTATION_VECTOR;
        case FUSION_NOGYRO:
            return SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR;
        default:
            assert(0);
            return 0;
    }
}

const char* RotationVectorSensor::getSensorName() const {
    switch(mMode) {
        case FUSION_9AXIS:
            return "Rotation Vector Sensor";
        case FUSION_NOMAG:
            return "Game Rotation Vector Sensor";
        case FUSION_NOGYRO:
            return "GeoMag Rotation Vector Sensor";
        default:
            assert(0);
            return nullptr;
    }
}

int RotationVectorSensor::getSensorToken() const {
    switch(mMode) {
        case FUSION_9AXIS:
            return '_rov';
        case FUSION_NOMAG:
            return '_gar';
        case FUSION_NOGYRO:
            return '_geo';
        default:
            assert(0);
            return 0;
    }
}

// ---------------------------------------------------------------------------

GyroDriftSensor::GyroDriftSensor() {
    const sensor_t sensor = {
        .name       = "Gyroscope Bias (debug)",
        .vendor     = "AOSP",
        .version    = 1,
        .handle     = '_gbs',
        .type       = SENSOR_TYPE_ACCELEROMETER,
        .maxRange   = 1,
        .resolution = 1.0f / (1<<24),
        .power      = mSensorFusion.getPowerUsage(),
        .minDelay   = mSensorFusion.getMinDelay(),
    };
    mSensor = Sensor(&sensor);
}

bool GyroDriftSensor::process(sensors_event_t* outEvent,
        const sensors_event_t& event)
{
    if (event.type == SENSOR_TYPE_ACCELEROMETER) {
        if (mSensorFusion.hasEstimate()) {
            const vec3_t b(mSensorFusion.getGyroBias());
            *outEvent = event;
            outEvent->data[0] = b.x;
            outEvent->data[1] = b.y;
            outEvent->data[2] = b.z;
            outEvent->sensor = '_gbs';
            outEvent->type = SENSOR_TYPE_ACCELEROMETER;
            return true;
        }
    }
    return false;
}

status_t GyroDriftSensor::activate(void* ident, bool enabled) {
    return mSensorFusion.activate(FUSION_9AXIS, ident, enabled);
}

status_t GyroDriftSensor::setDelay(void* ident, int /*handle*/, int64_t ns) {
    return mSensorFusion.setDelay(FUSION_9AXIS, ident, ns);
}

// ---------------------------------------------------------------------------
}; // namespace android

