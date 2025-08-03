/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "SensorEventQueue.h"

namespace android {

SensorEventQueue::SensorEventQueue(ASensorEventQueue* aSensorEventQueue)
    : mASensorEventQueue(aSensorEventQueue) {}

ssize_t SensorEventQueue::read(ASensorEvent* events, size_t numEvents) {
    return ASensorEventQueue_getEvents(mASensorEventQueue, events, numEvents);
}

status_t SensorEventQueue::waitForEvent() const {
    return OK;
}

status_t SensorEventQueue::enableSensor(Sensor const* sensor) const {
    return ASensorEventQueue_enableSensor(mASensorEventQueue, sensor->getASensorRef());
}

status_t SensorEventQueue::disableSensor(Sensor const* sensor) const {
    return ASensorEventQueue_disableSensor(mASensorEventQueue, sensor->getASensorRef());
}

status_t SensorEventQueue::setEventRate(Sensor const* sensor, nsecs_t ns) const {
    return ASensorEventQueue_setEventRate(mASensorEventQueue, sensor->getASensorRef(), ns);
}

}  // namespace android
