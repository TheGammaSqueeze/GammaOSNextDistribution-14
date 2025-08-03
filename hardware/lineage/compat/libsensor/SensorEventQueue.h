/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/sensor.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <utils/Timers.h>
#include "Sensor.h"

namespace android {

class SensorEventQueue : public RefBase {
  public:
    SensorEventQueue(ASensorEventQueue* aSensorEventQueue);
    ~SensorEventQueue() = default;

    ssize_t read(ASensorEvent* events, size_t numEvents);

    status_t waitForEvent() const;

    status_t enableSensor(Sensor const* sensor) const;
    /*
    status_t enableSensor(Sensor const* sensor, int32_t samplingPeriodUs) const;
    */
    status_t disableSensor(Sensor const* sensor) const;
    status_t setEventRate(Sensor const* sensor, nsecs_t ns) const;

  private:
    ASensorEventQueue* mASensorEventQueue;
};

}  // namespace android
