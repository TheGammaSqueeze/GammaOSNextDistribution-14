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

#include "ALooper.h"
#include "ASensorEventQueue.h"
#include "ASensorManager.h"

#define LOG_TAG "libsensorndkbridge"
#include <aidl/sensors/convert.h>
#include <android-base/logging.h>
#include <android/binder_auto_utils.h>
#include <android/binder_ibinder_platform.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/looper.h>

#include <thread>

using aidl::android::frameworks::sensorservice::IEventQueue;
using aidl::android::frameworks::sensorservice::ISensorManager;
using aidl::android::hardware::sensors::SensorInfo;
using aidl::android::hardware::sensors::SensorType;
using android::BAD_VALUE;
using android::Mutex;
using android::NO_INIT;
using android::OK;
using android::status_t;

static Mutex gLock;

// static
ASensorManager *ASensorManager::sInstance = NULL;

// static
ASensorManager *ASensorManager::getInstance() {
    Mutex::Autolock autoLock(gLock);
    if (sInstance == NULL) {
        sInstance = new ASensorManager;
        if (sInstance->initCheck() != OK) {
            delete sInstance;
            sInstance = NULL;
        }
    }
    return sInstance;
}

void ASensorManager::serviceDied(void*) {
    LOG(ERROR) << "Sensor service died. Cleanup sensor manager instance!";
    Mutex::Autolock autoLock(gLock);
    delete sInstance;
    sInstance = NULL;
}

ASensorManager::ASensorManager()
    : mInitCheck(NO_INIT) {
    if (!ABinderProcess_isThreadPoolStarted()) {
        std::thread([]() {
            ABinderProcess_joinThreadPool();
            LOG(ERROR) << "SHOULD NOT EXIT";
        }).detach();
        LOG(ERROR) << "The user of libsensorndkbridge did not start a threadpool";
    }
    const std::string name = std::string() + ISensorManager::descriptor + "/default";
    mManager =
        ISensorManager::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService(name.c_str())));
    if (mManager != NULL) {
        mDeathRecipient =
            ndk::ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(serviceDied));
        auto linked =
            AIBinder_linkToDeath(mManager->asBinder().get(), mDeathRecipient.get(), nullptr);
        if (linked != OK) {
            LOG(WARNING) << "Unable to link to sensor service death notifications";
        } else {
            LOG(DEBUG) << "Link to sensor service death notification successful";
            mInitCheck = OK;
        }
    }
}

status_t ASensorManager::initCheck() const {
    return mInitCheck;
}

int ASensorManager::getSensorList(ASensorList *out) {
    LOG(VERBOSE) << "ASensorManager::getSensorList";

    Mutex::Autolock autoLock(mLock);

    if (mSensorList == NULL) {
        ndk::ScopedAStatus ret = mManager->getSensorList(&mSensors);

        if (!ret.isOk()) {
            LOG(ERROR) << "Failed to get sensor list: " << ret;
        }

        mSensorList.reset(new ASensorRef[mSensors.size()]);
        for (size_t i = 0; i < mSensors.size(); ++i) {
            mSensorList.get()[i] =
                reinterpret_cast<ASensorRef>(&mSensors[i]);
        }
    }

    if (out) {
        *out = reinterpret_cast<ASensorList>(mSensorList.get());
    }

    return mSensors.size();
}

ASensorRef ASensorManager::getDefaultSensor(int type) {
    (void)getSensorList(NULL /* list */);

    ASensorRef defaultSensor = NULL;

    SensorInfo sensor;

    ndk::ScopedAStatus ret = mManager->getDefaultSensor(static_cast<SensorType>(type), &sensor);

    if (!ret.isOk()) {
        LOG(ERROR) << "Failed to get default sensor of type " << type << " with error: " << ret;
    }

    for (size_t i = 0; i < mSensors.size(); ++i) {
        if (sensor == mSensors[i]) {
            defaultSensor = reinterpret_cast<ASensorRef>(&mSensors[i]);

            break;
        }
    }

    return defaultSensor;
}

ASensorRef ASensorManager::getDefaultSensorEx(
        int /* type */, bool /* wakeup */) {
    // XXX ISensorManager's getDefaultSensorEx() lacks a "wakeup" parameter.
    return NULL;
}

ASensorEventQueue *ASensorManager::createEventQueue(
        ALooper *looper,
        int /* ident */,
        ALooper_callbackFunc callback,
        void *data) {
    LOG(VERBOSE) << "ASensorManager::createEventQueue";

    std::shared_ptr<ASensorEventQueue> queue =
        ndk::SharedRefBase::make<ASensorEventQueue>(looper, callback, data);

    AIBinder_setMinSchedulerPolicy(queue->asBinder().get(), SCHED_FIFO, 98);
    std::shared_ptr<IEventQueue> eventQueue;
    ndk::ScopedAStatus ret = mManager->createEventQueue(queue, &eventQueue);

    if (!ret.isOk()) {
        LOG(ERROR) << "FAILED to create event queue: " << ret;
        return NULL;
    }
    queue->setImpl(eventQueue);

    {
        Mutex::Autolock autoLock(mQueuesLock);
        mQueues.push_back(queue);
    }

    LOG(VERBOSE) << "Returning event queue " << queue.get();
    return queue.get();
}

void ASensorManager::destroyEventQueue(ASensorEventQueue *queue) {
    LOG(VERBOSE) << "ASensorManager::destroyEventQueue(" << queue << ")";

    queue->invalidate();

    {
        Mutex::Autolock autoLock(mQueuesLock);
        mQueues.erase(std::remove_if(mQueues.begin(), mQueues.end(),
                                     [&](const std::shared_ptr<ASensorEventQueue>& ptr) {
                                         return ptr.get() == queue;
                                     }),
                      mQueues.end());
    }
}

////////////////////////////////////////////////////////////////////////////////

ASensorManager *ASensorManager_getInstance() {
    return ASensorManager::getInstance();
}

ASensorManager *ASensorManager_getInstanceForPackage(
        const char* /* packageName */) {
    return ASensorManager::getInstance();
}

#define RETURN_IF_MANAGER_IS_NULL(x)    \
    do {                                \
        if (manager == NULL) {          \
            return x;                   \
        }                               \
    } while (0)

#define RETURN_IF_QUEUE_IS_NULL(x)      \
    do {                                \
        if (queue == NULL) {            \
            return x;                   \
        }                               \
    } while (0)

#define RETURN_IF_SENSOR_IS_NULL(x)     \
    do {                                \
        if (sensor == NULL) {           \
            return x;                   \
        }                               \
    } while (0)

int ASensorManager_getSensorList(ASensorManager* manager, ASensorList* list) {
    RETURN_IF_MANAGER_IS_NULL(BAD_VALUE);
    return manager->getSensorList(list);
}

ASensor const* ASensorManager_getDefaultSensor(
        ASensorManager* manager, int type) {
    RETURN_IF_MANAGER_IS_NULL(NULL);

    return manager->getDefaultSensor(type);
}

#if 0
ASensor const* ASensorManager_getDefaultSensorEx(
        ASensorManager* manager, int type, bool wakeUp) {
    RETURN_IF_MANAGER_IS_NULL(NULL);

    return manager->getDefaultSensorEx(type, wakeUp);
}
#endif

ASensorEventQueue* ASensorManager_createEventQueue(
        ASensorManager* manager,
        ALooper* looper,
        int ident,
        ALooper_callbackFunc callback,
        void* data) {
    RETURN_IF_MANAGER_IS_NULL(NULL);

    if (looper == NULL) {
        return NULL;
    }

    return manager->createEventQueue(looper, ident, callback, data);
}

int ASensorManager_destroyEventQueue(
        ASensorManager* manager, ASensorEventQueue* queue) {
    RETURN_IF_MANAGER_IS_NULL(BAD_VALUE);
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);

    manager->destroyEventQueue(queue);
    queue = NULL;

    return OK;
}

#if 0
int ASensorManager_createSharedMemoryDirectChannel(
        ASensorManager* manager, int fd, size_t size) {
    RETURN_IF_MANAGER_IS_NULL(BAD_VALUE);

    return OK;
}

int ASensorManager_createHardwareBufferDirectChannel(
        ASensorManager* manager, AHardwareBuffer const * buffer, size_t size) {
    RETURN_IF_MANAGER_IS_NULL(BAD_VALUE);

    return OK;
}

void ASensorManager_destroyDirectChannel(
        ASensorManager* manager, int channelId) {
}

int ASensorManager_configureDirectReport(
        ASensorManager* manager,
        ASensor const* sensor,
        int channelId,int rate) {
    RETURN_IF_MANAGER_IS_NULL(BAD_VALUE);
    return OK;
}
#endif

int ASensorEventQueue_registerSensor(
        ASensorEventQueue* queue,
        ASensor const* sensor,
        int32_t samplingPeriodUs,
        int64_t maxBatchReportLatencyUs) {
    LOG(VERBOSE) << "ASensorEventQueue_registerSensor";
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->registerSensor(
            sensor, samplingPeriodUs, maxBatchReportLatencyUs);
}

int ASensorEventQueue_enableSensor(
        ASensorEventQueue* queue, ASensor const* sensor) {
    LOG(VERBOSE) << "ASensorEventQueue_enableSensor(queue " << queue << ")";
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->enableSensor(sensor);
}

int ASensorEventQueue_disableSensor(
        ASensorEventQueue* queue, ASensor const* sensor) {
    LOG(VERBOSE) << "ASensorEventQueue_disableSensor";
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->disableSensor(sensor);
}

int ASensorEventQueue_setEventRate(
        ASensorEventQueue* queue,
        ASensor const* sensor,
        int32_t usec) {
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->setEventRate(sensor, usec);
}

int ASensorEventQueue_hasEvents(ASensorEventQueue* queue) {
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->hasEvents();
}

ssize_t ASensorEventQueue_getEvents(
        ASensorEventQueue* queue, ASensorEvent* events, size_t count) {
    LOG(VERBOSE) << "ASensorEventQueue_getEvents";
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->getEvents(events, count);
}

int ASensorEventQueue_requestAdditionalInfoEvents(ASensorEventQueue* queue, bool enable) {
    RETURN_IF_QUEUE_IS_NULL(BAD_VALUE);
    return queue->requestAdditionalInfoEvents(enable);
}

const char *ASensor_getName(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(NULL);
    return reinterpret_cast<const SensorInfo *>(sensor)->name.c_str();
}

const char *ASensor_getVendor(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(NULL);
    return reinterpret_cast<const SensorInfo *>(sensor)->vendor.c_str();
}

int ASensor_getType(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_TYPE_INVALID);
    return static_cast<int>(
            reinterpret_cast<const SensorInfo *>(sensor)->type);
}

float ASensor_getResolution(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_RESOLUTION_INVALID);
    return reinterpret_cast<const SensorInfo *>(sensor)->resolution;
}

int ASensor_getMinDelay(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_DELAY_INVALID);
    return reinterpret_cast<const SensorInfo*>(sensor)->minDelayUs;
}

int ASensor_getFifoMaxEventCount(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_FIFO_COUNT_INVALID);
    return reinterpret_cast<const SensorInfo *>(sensor)->fifoMaxEventCount;
}

int ASensor_getFifoReservedEventCount(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_FIFO_COUNT_INVALID);
    return reinterpret_cast<const SensorInfo *>(sensor)->fifoReservedEventCount;
}

const char* ASensor_getStringType(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(NULL);
    return reinterpret_cast<const SensorInfo *>(sensor)->typeAsString.c_str();
}

extern "C" float ASensor_getMaxRange(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(nanf(""));
    return reinterpret_cast<const SensorInfo *>(sensor)->maxRange;
}

int ASensor_getHandle(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_INVALID);
    return reinterpret_cast<const SensorInfo*>(sensor)->sensorHandle;
}

#if 0
int ASensor_getReportingMode(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(AREPORTING_MODE_INVALID);
    return 0;
}

bool ASensor_isWakeUpSensor(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(false);
    return false;
}

bool ASensor_isDirectChannelTypeSupported(
        ASensor const* sensor, int channelType) {
    RETURN_IF_SENSOR_IS_NULL(false);
    return false;
}

int ASensor_getHighestDirectReportRateLevel(ASensor const* sensor) {
    RETURN_IF_SENSOR_IS_NULL(ASENSOR_DIRECT_RATE_STOP);
    return 0;
}
#endif

static ALooper *getTheLooper() {
    static ALooper *sLooper = NULL;

    Mutex::Autolock autoLock(gLock);
    if (sLooper == NULL) {
        sLooper = new ALooper;
    }

    return sLooper;
}


ALooper *ALooper_forThread() {
    LOG(VERBOSE) << "ALooper_forThread";
    return getTheLooper();
}

ALooper *ALooper_prepare(int /* opts */) {
    LOG(VERBOSE) << "ALooper_prepare";
    return getTheLooper();
}

int ALooper_pollOnce(
        int timeoutMillis, int* outFd, int* outEvents, void** outData) {
    int res = getTheLooper()->pollOnce(timeoutMillis, outFd, outEvents, outData);
    LOG(VERBOSE) << "ALooper_pollOnce => " << res;
    return res;
}

void ALooper_wake(ALooper* looper) {
    LOG(VERBOSE) << "ALooper_wake";
    looper->wake();
}
