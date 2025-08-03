/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "Sensor.h"

namespace android {

Sensor::Sensor(ASensorRef sensorRef) : mASensorRef(sensorRef) {
    mName = ASensor_getName(mASensorRef);
    mVendor = ASensor_getVendor(mASensorRef);
    mHandle = ASensor_getHandle(mASensorRef);
    mType = ASensor_getType(mASensorRef);
    mResolution = ASensor_getResolution(mASensorRef);
    mMinDelay = ASensor_getMinDelay(mASensorRef);
    mFifoReservedEventCount = ASensor_getFifoReservedEventCount(mASensorRef);
    mFifoMaxEventCount = ASensor_getFifoMaxEventCount(mASensorRef);
    mStringType = ASensor_getStringType(mASensorRef);
}

const String8& Sensor::getName() const {
    return mName;
}

const String8& Sensor::getVendor() const {
    return mVendor;
}

int32_t Sensor::getHandle() const {
    return mHandle;
}

int32_t Sensor::getType() const {
    return mType;
}

float Sensor::getResolution() const {
    return mResolution;
}

int32_t Sensor::getMinDelay() const {
    return mMinDelay;
}

uint32_t Sensor::getFifoReservedEventCount() const {
    return mFifoReservedEventCount;
}

uint32_t Sensor::getFifoMaxEventCount() const {
    return mFifoMaxEventCount;
}

const String8& Sensor::getStringType() const {
    return mStringType;
}

ASensorRef Sensor::getASensorRef() const {
    return mASensorRef;
}

}  // namespace android
