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

#ifndef A_SENSOR_EVENT_QUEUE_H_

#define A_SENSOR_EVENT_QUEUE_H_

#include <aidl/android/frameworks/sensorservice/BnEventQueueCallback.h>
#include <aidl/android/frameworks/sensorservice/IEventQueue.h>
#include <aidl/sensors/convert.h>
#include <android-base/macros.h>
#include <android/binder_auto_utils.h>
#include <android/looper.h>
#include <android/sensor.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>

#include <atomic>

struct ALooper;

struct ASensorEventQueue : public aidl::android::frameworks::sensorservice::BnEventQueueCallback {
    using Event = aidl::android::hardware::sensors::Event;
    using IEventQueue = aidl::android::frameworks::sensorservice::IEventQueue;

    ASensorEventQueue(
            ALooper *looper,
            ALooper_callbackFunc callback,
            void *data);

    ndk::ScopedAStatus onEvent(const Event& event) override;

    void setImpl(const std::shared_ptr<IEventQueue>& queueImpl);

    int registerSensor(
            ASensorRef sensor,
            int32_t samplingPeriodUs,
            int64_t maxBatchReportLatencyUs);

    int enableSensor(ASensorRef sensor);
    int disableSensor(ASensorRef sensor);

    int setEventRate(ASensorRef sensor, int32_t samplingPeriodUs);

    int requestAdditionalInfoEvents(bool enable);

    ssize_t getEvents(ASensorEvent *events, size_t count);
    int hasEvents() const;

    void dispatchCallback();

    void invalidate();

private:
    ALooper *mLooper;
    ALooper_callbackFunc mCallback;
    void *mData;
    std::shared_ptr<IEventQueue> mQueueImpl;

    android::Mutex mLock;
    std::vector<sensors_event_t> mQueue;

    std::atomic_bool mRequestAdditionalInfo;
    android::Mutex mValidLock;
    bool mValid;

    DISALLOW_COPY_AND_ASSIGN(ASensorEventQueue);
};

#endif  // A_SENSOR_EVENT_QUEUE_H_

