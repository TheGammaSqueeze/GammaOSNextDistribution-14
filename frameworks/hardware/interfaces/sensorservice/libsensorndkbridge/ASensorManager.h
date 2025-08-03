/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef A_SENSOR_MANAGER_H_

#define A_SENSOR_MANAGER_H_

#include <aidl/android/frameworks/sensorservice/ISensorManager.h>
#include <android-base/macros.h>
#include <android/sensor.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>

struct ALooper;

struct ASensorManager {
    static ASensorManager *getInstance();

    ASensorManager();
    android::status_t initCheck() const;

    // Returns error or number of sensors returned.
    int getSensorList(ASensorList *list);

    ASensorRef getDefaultSensor(int type);
    ASensorRef getDefaultSensorEx(int type, bool wakeup);

    ASensorEventQueue *createEventQueue(
            ALooper *looper,
            int ident,
            ALooper_callbackFunc callback,
            void *data);

    // This must not be called from inside ALooper_callbackFunc to avoid deadlocking inside of the
    // ALooper.
    void destroyEventQueue(ASensorEventQueue *queue);

    static void serviceDied(void* cookie);

   private:
    using ISensorManager = aidl::android::frameworks::sensorservice::ISensorManager;
    using SensorInfo = aidl::android::hardware::sensors::SensorInfo;

    static ASensorManager *sInstance;
    ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

    android::status_t mInitCheck;
    std::shared_ptr<ISensorManager> mManager;

    mutable android::Mutex mQueuesLock;
    std::vector<std::shared_ptr<ASensorEventQueue>> mQueues;

    mutable android::Mutex mLock;
    std::vector<SensorInfo> mSensors;
    std::unique_ptr<ASensorRef[]> mSensorList;

    DISALLOW_COPY_AND_ASSIGN(ASensorManager);
};

#endif  // A_SENSOR_MANAGER_H_
