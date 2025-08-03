/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "SensorManager.h"

namespace android {

SensorManager* SensorManager::sInstance = nullptr;

Mutex SensorManager::sInstanceLock;

SensorManager* SensorManager::getInstance() {
    Mutex::Autolock autoLock(sInstanceLock);
    if (sInstance == nullptr) {
        sInstance = new SensorManager;
        if (sInstance->initCheck() != OK) {
            delete sInstance;
            sInstance = nullptr;
        }
    }
    return sInstance;
}

SensorManager& SensorManager::getInstanceForPackage(const String16& /*packageName*/) {
    return *getInstance();
}

void SensorManager::removeInstanceForPackage(const String16& /*packageName*/) {
    // no-op
}

SensorManager::SensorManager() : mInitCheck(NO_INIT) {
    mASensorManager = ASensorManager_getInstanceForPackage("");
    if (mASensorManager != NULL) {
        mInitCheck = OK;
    }
}

ssize_t SensorManager::getSensorList(Sensor const* const** list) {
    Mutex::Autolock autoLock(mLock);

    ASensorList* aSensorList = nullptr;
    int count;
    ASensorRef aSensor;

    count = ASensorManager_getSensorList(mASensorManager, aSensorList);

    mSensors = static_cast<Sensor const**>(malloc(count * sizeof(Sensor*)));
    assert(mSensors != nullptr);

    for (int i = 0; i < count; i++) {
        aSensor = *aSensorList[i];
        mSensors[i] = new Sensor(aSensor);
    }

    *list = mSensors;

    return count;
}

Sensor const* SensorManager::getDefaultSensor(int type) {
    const ASensor* sensor = ASensorManager_getDefaultSensor(mASensorManager, type);

    return sensor != nullptr ? new Sensor(ASensorManager_getDefaultSensor(mASensorManager, type))
                             : nullptr;
}

sp<SensorEventQueue> SensorManager::createEventQueue(String8 /*packageName*/, int /*mode*/,
                                                     String16 /*attributionTag*/) {
    return sp(new SensorEventQueue(ASensorManager_createEventQueue(
            mASensorManager, ALooper_forThread(), 0, nullptr, nullptr)));
}

status_t SensorManager::initCheck() const {
    return mInitCheck;
}

sp<SensorEventQueue> SensorManager::createEventQueue(String8 packageName) {
    return createEventQueue(packageName, 0);
}

sp<SensorEventQueue> SensorManager::createEventQueue(String8 packageName, int mode) {
    return createEventQueue(packageName, mode, String16(""));
}

}  // namespace android
