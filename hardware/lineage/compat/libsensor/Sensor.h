/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/sensor.h>
#include <stdint.h>
#include <utils/Errors.h>
#include <utils/String8.h>

namespace android {

class Sensor {
  public:
    Sensor(ASensorRef sensorRef);
    ~Sensor() = default;

    const String8& getName() const;
    const String8& getVendor() const;
    int32_t getHandle() const;
    int32_t getType() const;
    /*
    float getMinValue() const;
    float getMaxValue() const;
    */
    float getResolution() const;
    /*
    float getPowerUsage() const;
    */
    int32_t getMinDelay() const;
    /*
    nsecs_t getMinDelayNs() const;
    int32_t getVersion() const;
    */
    uint32_t getFifoReservedEventCount() const;
    uint32_t getFifoMaxEventCount() const;
    const String8& getStringType() const;
    /*
    const String8& getRequiredPermission() const;
    bool isRequiredPermissionRuntime() const;
    int32_t getRequiredAppOp() const;
    int32_t getMaxDelay() const;
    uint32_t getFlags() const;
    bool isWakeUpSensor() const;
    bool isDynamicSensor() const;
    bool isDataInjectionSupported() const;
    bool hasAdditionalInfo() const;
    int32_t getHighestDirectReportRateLevel() const;
    bool isDirectChannelTypeSupported(int32_t sharedMemType) const;
    int32_t getReportingMode() const;
    */

    // Custom methods
    ASensorRef getASensorRef() const;

  private:
    ASensorRef mASensorRef;

    String8 mName;
    String8 mVendor;
    int32_t mHandle;
    int32_t mType;
    float mResolution;
    int32_t mMinDelay;
    uint32_t mFifoReservedEventCount;
    uint32_t mFifoMaxEventCount;
    String8 mStringType;
};

}  // namespace android
