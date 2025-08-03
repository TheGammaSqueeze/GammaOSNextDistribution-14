/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/sensor.h>
#include <stdint.h>
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>
#include "Sensor.h"
#include "SensorEventQueue.h"

namespace android {

class SensorManager {
  public:
    static SensorManager& getInstanceForPackage(const String16& packageName);
    static void removeInstanceForPackage(const String16& packageName);
    ~SensorManager() = default;

    ssize_t getSensorList(Sensor const* const** list);
    /*
    ssize_t getDynamicSensorList(Vector<Sensor>& list);
    ssize_t getDynamicSensorList(Sensor const* const** list);
    */
    Sensor const* getDefaultSensor(int type);
    sp<SensorEventQueue> createEventQueue(String8 packageName /*= String8("")*/, int mode /*= 0*/,
                                          String16 attributionTag /*= String16("")*/);
    /*
    bool isDataInjectionEnabled();
    int createDirectChannel(size_t size, int channelType, const native_handle_t* channelData);
    void destroyDirectChannel(int channelNativeHandle);
    int configureDirectChannel(int channelNativeHandle, int sensorHandle, int rateLevel);
    int setOperationParameter(int handle, int type, const Vector<float>& floats,
                              const Vector<int32_t>& ints);
    */

    // Shims
    sp<SensorEventQueue> createEventQueue(String8 packageName);
    sp<SensorEventQueue> createEventQueue(String8 packageName, int mode);

    // Custom methods
    status_t initCheck() const;

  private:
    static SensorManager* sInstance;
    static Mutex sInstanceLock;
    static SensorManager* getInstance();

    SensorManager();

    status_t mInitCheck;
    ASensorManager* mASensorManager;

    Mutex mLock;
    Sensor const** mSensors;
};

}  // namespace android
