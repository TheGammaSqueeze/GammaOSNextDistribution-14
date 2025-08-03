/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <cpu.h>
#include <cpu/cpuMath.h>
#include <heap.h>
#include <sensors.h>
#include <sensors_priv.h>
#include <seos.h>
#include <seos_priv.h>
#include <syscall.h>
#include <timer.h>
#include <util.h>
#include <printf.h>
#include <nanohubCommand.h>

#include <chre.h>
#include <chreApi.h>

#define MINIMUM_INTERVAL_DEFAULT_HZ SENSOR_HZ(1.0f)

/*
 * This is to ensure that message size and some extra headers will stay representable with 1 byte
 * Code relies on that in many places.
 */
C_STATIC_ASSERT(max_chre_msg_size, CHRE_MESSAGE_TO_HOST_MAX_SIZE <= 240);

/*
 * Many syscalls rely on the property that uintptr_t can hold uint32_t without data loss
 * This is enforced by static assertion in chreApi.h
 * None of the methods returning uint32_t are cast to uintptr_t
 * This is done in order to let compiler warn us if our assumption is not safe for some reason
 */

static inline uint64_t osChreGetAppId(void)
{
    struct Task *task = osGetCurrentTask();
    const struct AppHdr *app = task ? task->app : NULL;

    return app ? app->hdr.appId : 0;
}

static void osChreApiGetAppId(uintptr_t *retValP, va_list args)
{
    uint64_t *appId = va_arg(args, uint64_t *);
    if (appId)
        *appId = osChreGetAppId();
}

static void osChreApiGetInstanceId(uintptr_t *retValP, va_list args)
{
    *retValP = osGetCurrentTid();
}

static void osChreApiLogLogv(uintptr_t *retValP, va_list args)
{
    va_list innerArgs;
    enum chreLogLevel level = va_arg(args, int /* enums promoted to ints in va_args in C */);
    const static char levels[] = "EWIDV";
    char clevel = (level > CHRE_LOG_DEBUG || (int) level < 0) ? 'V' : levels[level];
    const char *str = va_arg(args, const char*);
    uintptr_t inner = va_arg(args, uintptr_t);

    va_copy(innerArgs, INTEGER_TO_VA_LIST(inner));
    osLogv(clevel, PRINTF_FLAG_CHRE, str, innerArgs);
    va_end(innerArgs);
}

static void osChreApiLogLogvOld(uintptr_t *retValP, va_list args)
{
    va_list innerArgs;
    enum chreLogLevel level = va_arg(args, int /* enums promoted to ints in va_args in C */);
    const static char levels[] = "EWIDV";
    char clevel = (level > CHRE_LOG_DEBUG || (int) level < 0) ? 'V' : levels[level];
    const char *str = va_arg(args, const char*);
    uintptr_t inner = va_arg(args, uintptr_t);

    va_copy(innerArgs, INTEGER_TO_VA_LIST(inner));
    osLogv(clevel, PRINTF_FLAG_CHRE | PRINTF_FLAG_SHORT_DOUBLE, str, innerArgs);
    va_end(innerArgs);
}

static void osChreApiGetTime(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    if (timeNanos)
        *timeNanos = sensorGetTime();
}

static void osChreApiGetHostTimeOffset(uintptr_t *retValP, va_list args)
{
    uint64_t *timeNanos = va_arg(args, uint64_t *);
    if (timeNanos)
        *timeNanos = hostGetTimeDelta();
}

static inline uint32_t osChreTimerSet(uint64_t duration, const void* cookie, bool oneShot)
{
    uint32_t timId = timTimerSetNew(duration, cookie, oneShot);

    return timId == 0 ? CHRE_TIMER_INVALID : timId;
}

static void osChreApiTimerSet(uintptr_t *retValP, va_list args)
{
    uint32_t length_lo = va_arg(args, uint32_t);
    uint32_t length_hi = va_arg(args, uint32_t);
    void *cookie = va_arg(args, void *);
    bool oneshot = va_arg(args, int);
    uint64_t length = (((uint64_t)length_hi) << 32) | length_lo;

    *retValP = osChreTimerSet(length, cookie, oneshot);
}

static void osChreApiTimerCancel(uintptr_t *retValP, va_list args)
{
    uint32_t timerId = va_arg(args, uint32_t);
    *retValP = timTimerCancelEx(timerId, true);
}

static inline void osChreAbort(uint32_t abortCode)
{
    struct Task *task = osGetCurrentTask();
    if (task) {
        if (task->app) {
            osLog(LOG_ERROR, "APP ID=0x%" PRIX64 " TID=0x%" PRIX16 " aborted [code 0x%" PRIX32 "]",
                  task->app->hdr.appId, task->tid, abortCode);
        } else {
            osLog(LOG_ERROR, "APP ID=NULL TID=0x%" PRIX16 " aborted [code 0x%" PRIX32 "]",
                  task->tid, abortCode);
        }
        osTaskAbort(task);
    } else {
        osLog(LOG_ERROR, "osChreAbort called with no current task [code 0x%" PRIX32 "]",
              abortCode);
    }
}

static void osChreApiAbort(uintptr_t *retValP, va_list args)
{
    uint32_t code = va_arg(args, uint32_t);
    osChreAbort(code);
}

static void osChreApiHeapAlloc(uintptr_t *retValP, va_list args)
{
    uint32_t size = va_arg(args, uint32_t);
    *retValP = (uintptr_t)heapAlloc(size);
}

static void osChreApiHeapFree(uintptr_t *retValP, va_list args)
{
    void *ptr = va_arg(args, void *);
    heapFree(ptr);
}

/*
 * we have no way to verify if this is a CHRE event; just trust the caller to do the right thing
 */
void osChreFreeEvent(uint32_t tid, chreEventCompleteFunction *cbFreeEvt, uint32_t evtType, void * evtData)
{
    struct Task *chreTask = osTaskFindByTid(tid);
    struct Task *preempted = osSetCurrentTask(chreTask);
    if (chreTask && osTaskIsChre(chreTask))
        osTaskInvokeEventFreeCallback(chreTask, cbFreeEvt, evtType, evtData);
    osSetCurrentTask(preempted);
}

static bool osChreSendEvent(uint16_t evtType, void *evtData,
                            chreEventCompleteFunction *evtFreeCallback,
                            uint32_t toTid)
{
    /*
     * this primitive may only be used for USER CHRE events;
     * system events come from the OS itself through different path,
     * and are interpreted by the CHRE app compatibility library.
     * therefore, we have to enforce the evtType >= CHRE_EVENT_FIRST_USER_VALUE.
     */
    if (evtType < CHRE_EVENT_FIRST_USER_VALUE) {
        osChreFreeEvent(osGetCurrentTid(), evtFreeCallback, evtType, evtData);
        return false;
    }
    return osEnqueuePrivateEvtNew(evtType, evtData, evtFreeCallback, toTid);
}

static bool osChreSendMessageToHost(void *message, uint32_t messageSize,
                           uint32_t messageType, uint16_t hostEndpoint,
                           chreMessageFreeFunction *freeCallback)
{
    bool result = false;
    struct HostHubChrePacket *hostMsg = NULL;

    if (messageSize > CHRE_MESSAGE_TO_HOST_MAX_SIZE || (messageSize && !message))
        goto out;

    hostMsg = heapAlloc(sizeof(*hostMsg) + messageSize);
    if (!hostMsg)
        goto out;

    if (messageSize)
        memcpy(hostMsg+1, message, messageSize);

    hostMsg->appId = osChreGetAppId();
    hostMsg->messageSize = messageSize;
    hostMsg->messageType = messageType;
    hostMsg->hostEndpoint = hostEndpoint;
    result = osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, hostMsg, heapFree);

out:
    if (freeCallback)
        osTaskInvokeMessageFreeCallback(osGetCurrentTask(), freeCallback, message, messageSize);
    return result;
}

static void osChreApiSendMessageToHost(uintptr_t *retValP, va_list args)
{
    void *message = va_arg(args, void *);
    uint32_t messageSize = va_arg(args, uint32_t);
    uint32_t messageType = va_arg(args, uint32_t);
    chreMessageFreeFunction *freeCallback = va_arg(args, chreMessageFreeFunction *);

    *retValP = osChreSendMessageToHost(message, messageSize, messageType, CHRE_HOST_ENDPOINT_BROADCAST, freeCallback);
}

static bool osChreSensorFindDefault(uint8_t sensorType, uint32_t *pHandle)
{
    if (!pHandle)
        return false;

    const struct SensorInfo *info = sensorFind(sensorType, 0, pHandle);

    return info != NULL;
}

static void osChreApiSensorFindDefault(uintptr_t *retValP, va_list args)
{
    uint8_t sensorType = va_arg(args, uint32_t);
    uint32_t *pHandle = va_arg(args, uint32_t *);
    *retValP = osChreSensorFindDefault(sensorType, pHandle);
}

static bool osChreSensorGetInfoOld(uint32_t sensorHandle, struct chreSensorInfo *info)
{
    struct Sensor *s = sensorFindByHandle(sensorHandle);
    if (!s || !info)
        return false;
    const struct SensorInfo *si = s->si;
    info->sensorName = si->sensorName;
    info->sensorType = si->sensorType;
    info->unusedFlags = 0;

    if (si->sensorType == CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT
        || si->sensorType == CHRE_SENSOR_TYPE_STATIONARY_DETECT)
        info->isOneShot = true;
    else
        info->isOneShot = false;
    info->isOnChange = s->hasOnchange;

    return true;
}

static bool osChreSensorGetInfo(uint32_t sensorHandle, struct chreSensorInfo *info)
{
    struct Sensor *s = sensorFindByHandle(sensorHandle);
    uint32_t max = 0;
    int i;
    if (!s || !info)
        return false;
    const struct SensorInfo *si = s->si;
    info->sensorName = si->sensorName;
    info->sensorType = si->sensorType;
    info->unusedFlags = 0;

    if (si->sensorType == CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT
        || si->sensorType == CHRE_SENSOR_TYPE_STATIONARY_DETECT)
        info->isOneShot = true;
    else
        info->isOneShot = false;
    info->isOnChange = s->hasOnchange;
    info->minInterval = CHRE_SENSOR_INTERVAL_DEFAULT;
    if (si->supportedRates) {
        for (i=0; si->supportedRates[i] != 0; i++) {
            if (si->supportedRates[i] > max
                && si->supportedRates[i] != SENSOR_RATE_ONDEMAND
                && si->supportedRates[i] != SENSOR_RATE_ONCHANGE
                && si->supportedRates[i] != SENSOR_RATE_ONESHOT) {
                max = si->supportedRates[i];
            }
        }
        if (max)
            info->minInterval = (UINT32_C(1024000000) / max) * UINT64_C(1000);
    }

    return true;
}

static void osChreApiSensorGetInfoOld(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);
    struct chreSensorInfo *info = va_arg(args, struct chreSensorInfo *);
    *retValP = osChreSensorGetInfoOld(sensorHandle, info);
}

static void osChreApiSensorGetInfo(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);
    struct chreSensorInfo *info = va_arg(args, struct chreSensorInfo *);
    *retValP = osChreSensorGetInfo(sensorHandle, info);
}

static bool osChreSensorGetSamplingStatus(uint32_t sensorHandle,
                                 struct chreSensorSamplingStatus *status)
{
    struct Sensor *s = sensorFindByHandle(sensorHandle);
    uint32_t rate;
    uint64_t latency;

    if (!s || !status)
        return false;

    rate = sensorGetHwRate(sensorHandle);
    latency = sensorGetHwLatency(sensorHandle);

    if (rate == SENSOR_RATE_OFF) {
        status->enabled = 0;
        status->interval = 0;
        status->latency = 0;
    } else {
        status->enabled = true;
        if (rate == SENSOR_RATE_ONDEMAND
            || rate == SENSOR_RATE_ONCHANGE
            || rate == SENSOR_RATE_ONESHOT)
            status->interval = CHRE_SENSOR_INTERVAL_DEFAULT;
        else
            status->interval = (UINT32_C(1024000000) / rate) * UINT64_C(1000);

        if (latency == SENSOR_LATENCY_NODATA)
            status->latency = CHRE_SENSOR_INTERVAL_DEFAULT;
        else
            status->latency = latency;
    }

    return true;
}

static void osChreApiSensorGetStatus(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);
    struct chreSensorSamplingStatus *status = va_arg(args, struct chreSensorSamplingStatus *);
    *retValP = osChreSensorGetSamplingStatus(sensorHandle, status);
}

static bool osChreSensorConfigure(uint32_t sensorHandle,
                         enum chreSensorConfigureMode mode,
                         uint64_t interval, uint64_t latency)
{
    uint32_t rate, interval_us;
    bool ret;
    struct Sensor *s = sensorFindByHandle(sensorHandle);
    int i;
    if (!s)
        return false;

    if (mode & CHRE_SENSOR_CONFIGURE_RAW_POWER_ON) {
        if (interval == CHRE_SENSOR_INTERVAL_DEFAULT) {
            // use first rate in supported rates list > minimum (if avaliable)
            const struct SensorInfo *si = s->si;
            if (!si)
                return false;

            if (!si->supportedRates || si->supportedRates[0] == 0)
                rate = SENSOR_RATE_ONCHANGE;
            else {
                for (i = 0; si->supportedRates[i] != 0; i++) {
                    rate = si->supportedRates[i];
                    if (rate >= MINIMUM_INTERVAL_DEFAULT_HZ)
                        break;
                }
            }
        } else {
            interval_us = U64_DIV_BY_CONST_U16(interval, 1000);
            rate = UINT32_C(1024000000) / interval_us;
        }
        if (!rate) // 0 is a reserved value. minimum is 1
            rate = 1;
        if (latency == CHRE_SENSOR_LATENCY_DEFAULT)
            latency = 0ULL;
        if (sensorGetReqRate(sensorHandle) == SENSOR_RATE_OFF) {
            if ((ret = sensorRequest(0, sensorHandle, rate, latency))) {
                if (!(ret = osEventsSubscribe(2, sensorGetMyEventType(s->si->sensorType), sensorGetMyCfgEventType(s->si->sensorType))))
                    sensorRelease(0, sensorHandle);
            }
        } else {
            ret = sensorRequestRateChange(0, sensorHandle, rate, latency);
        }
    } else if (mode & (CHRE_SENSOR_CONFIGURE_RAW_REPORT_CONTINUOUS|CHRE_SENSOR_CONFIGURE_RAW_REPORT_ONE_SHOT)) {
        if (sensorGetReqRate(sensorHandle) == SENSOR_RATE_OFF)
            ret = osEventsSubscribe(2, sensorGetMyEventType(s->si->sensorType), sensorGetMyCfgEventType(s->si->sensorType));
        else
            ret = true;
    } else {
        if (sensorGetReqRate(sensorHandle) != SENSOR_RATE_OFF) {
            if ((ret = sensorRelease(0, sensorHandle)))
                ret = osEventsUnsubscribe(2, sensorGetMyEventType(s->si->sensorType), sensorGetMyCfgEventType(s->si->sensorType));
        } else {
            ret = osEventsUnsubscribe(2, sensorGetMyEventType(s->si->sensorType), sensorGetMyCfgEventType(s->si->sensorType));
        }
    }

    return ret;
}

static void osChreApiSensorConfig(uintptr_t *retValP, va_list args)
{
    uint32_t sensorHandle = va_arg(args, uint32_t);
    enum chreSensorConfigureMode mode = va_arg(args, int);
    uint64_t interval = va_arg(args, uint32_t);
    uint32_t interval_hi = va_arg(args, uint32_t);
    uint64_t latency = va_arg(args, uint32_t);
    uint32_t latency_hi = va_arg(args, uint32_t);

    interval |= ((uint64_t)interval_hi) << 32;
    latency  |= ((uint64_t)latency_hi) << 32;

    *retValP = osChreSensorConfigure(sensorHandle, mode, interval, latency);
}

static uint32_t osChreGetApiVersion(void)
{
    return CHRE_API_VERSION;
}

static void osChreApiChreApiVersion(uintptr_t *retValP, va_list args)
{
    *retValP = osChreGetApiVersion();
}

static uint32_t osChreGetVersion(void)
{
    return CHRE_API_VERSION | NANOHUB_OS_PATCH_LEVEL;
}

static void osChreApiChreOsVersion(uintptr_t *retValP, va_list args)
{
    *retValP = (uintptr_t)osChreGetVersion();
}

static uint64_t osChreGetPlatformId(void)
{
    return HW_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
}

static void osChreApiPlatformId(uintptr_t *retValP, va_list args)
{
    uint64_t *pHwId = va_arg(args, uint64_t*);
    if (pHwId)
        *pHwId = osChreGetPlatformId();
}

static void osChreEventSendEvent(uintptr_t *retValP, va_list args)
{
    uint16_t evtType = va_arg(args, uint32_t); // stored as 32-bit
    void *evtData = va_arg(args, void *);
    chreEventCompleteFunction *freeCallback = va_arg(args, chreEventCompleteFunction *);
    uint32_t toTid = va_arg(args, uint32_t);
    *retValP = osChreSendEvent(evtType, evtData, freeCallback, toTid);
}

static void osChreEventSendMessageToHost(uintptr_t *retValP, va_list args)
{
    void *message = va_arg(args, void *);
    uint32_t messageSize = va_arg(args, size_t);
    uint32_t messageType = va_arg(args, uint32_t);
    uint16_t hostEndpoint = va_arg(args, uint32_t);
    chreMessageFreeFunction *freeCallback = va_arg(args, chreMessageFreeFunction *);

    *retValP = osChreSendMessageToHost(message, messageSize, messageType, hostEndpoint, freeCallback);
}

static bool chreInfoByTid(uint32_t tid, struct chreNanoappInfo *info)
{
    struct Task *task = osTaskFindByTid(tid);
    if (task) {
        info->appId = task->app->hdr.appId;
        info->version = task->app->hdr.appVer;
        info->instanceId = tid;
        return true;
    } else {
        return false;
    }
}

static void osChreEventInfoByAppId(uintptr_t *retValP, va_list args)
{
    uint32_t app_lo = va_arg(args, uint32_t);
    uint32_t app_hi = va_arg(args, uint32_t);
    struct chreNanoappInfo *info = va_arg(args, struct chreNanoappInfo *);
    uint64_t appId = (((uint64_t)app_hi) << 32) | app_lo;
    uint32_t tid;

    if (osTidById(&appId, &tid))
        *retValP = chreInfoByTid(tid, info);
    else
        *retValP = false;
}

static void osChreEeventInfoByInstId(uintptr_t *retValP, va_list args)
{
    uint32_t tid = va_arg(args, uint32_t);
    struct chreNanoappInfo *info = va_arg(args, struct chreNanoappInfo *);

    *retValP = chreInfoByTid(tid, info);
}

static void osChreEventCfgInfo(uintptr_t *retValP, va_list args)
{
    bool enable = va_arg(args, int);
    if (enable)
        osEventsSubscribe(2, EVT_APP_STARTED, EVT_APP_STOPPED);
    else
        osEventsUnsubscribe(2, EVT_APP_STARTED, EVT_APP_STOPPED);
}

static void osChreEventHostSleep(uintptr_t *retValP, va_list args)
{
    // bool enable = va_arg(args, int();
}

static void osChreEventIsHostAwake(uintptr_t *retValP, va_list args)
{
    *retValP = true;
}

static void osChreDrvGnssGetCap(uintptr_t *retValP, va_list args)
{
    *retValP = CHRE_GNSS_CAPABILITIES_NONE;
}

static void osChreDrvGnssLocStartAsync(uintptr_t *retValP, va_list args)
{
    // uint32_t minIntervalMs = va_args(args, uint32_t);
    // uint32_t minTimeToNextFixMs = va_args(args, uint32_t);
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvGnssLocStopAsync(uintptr_t *retValP, va_list args)
{
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvGnssMeasStartAsync(uintptr_t *retValP, va_list args)
{
    // uint32_t minIntervalMs = va_args(args, uint32_t);
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvGnssMeasStopAsync(uintptr_t *retValP, va_list args)
{
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvGnssConfLocMon(uintptr_t *retValP, va_list args)
{
    // bool enable = va_args(args, bool);
    *retValP = false;
}

static void osChreDrvWifiGetCap(uintptr_t *retValP, va_list args)
{
    *retValP = CHRE_WIFI_CAPABILITIES_NONE;
}

static void osChreDrvWifiConfScanMonAsync(uintptr_t *retValP, va_list args)
{
    // bool enable = va_args(args, int);
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvWifiReqScanAsync(uintptr_t *retValP, va_list args)
{
    // const struct chreWifiScanParams *params = va_args(args, struct chreWifiScanParams *);
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvWwanGetCap(uintptr_t *retValP, va_list args)
{
    *retValP = CHRE_WWAN_CAPABILITIES_NONE;
}

static void osChreDrvWwanGetCallInfoAsync(uintptr_t *retValP, va_list args)
{
    // const void *cookie = va_args(args, void *);
    *retValP = false;
}

static void osChreDrvAudioGetSrc(uintptr_t *retValP, va_list args)
{
    // uint32_t handle = va_args(args, uint32_t);
    // struct chreAudioSource *audioSource = va_args(args, struct chreAudioSource *);
    *retValP = false;
}

static void osChreDrvAudioConfSrc(uintptr_t *retValP, va_list args)
{
    // uint32_t handle = va_args(args, uint32_t);
    // bool enable = va_args(args, int);
    // uint32_t duration_lo = va_arg(args, uint32_t);
    // uint32_t duration_hi = va_arg(args, uint32_t);
    // uint64_t bufferDuration = (((uint64_t)dur_hi) << 32) | dur_lo;
    // uint32_t interval_lo = va_args(args, uint32_t);
    // uint32_t interval_hi = va_args(args, uint32_t);
    // uint64_t deliveryInterval = (((uint64_t)del_hi) << 32) | del_lo;
    *retValP = false;
}

static void osChreDrvAudioGetStatus(uintptr_t *retValP, va_list args)
{
    // uint32_t handle = va_args(args, uint32_t);
    // struct chreAudioSourceStatus *status = va_args(args, struct chreAudioSourceStatus *);
    *retValP = false;
}

static const struct SyscallTable chreMainApiTable = {
    .numEntries = SYSCALL_CHRE_MAIN_API_LAST,
    .entry = {
        [SYSCALL_CHRE_MAIN_API_LOG_OLD]                 = { .func = osChreApiLogLogvOld },
        [SYSCALL_CHRE_MAIN_API_LOG]                     = { .func = osChreApiLogLogv },
        [SYSCALL_CHRE_MAIN_API_GET_APP_ID]              = { .func = osChreApiGetAppId },
        [SYSCALL_CHRE_MAIN_API_GET_INST_ID]             = { .func = osChreApiGetInstanceId },
        [SYSCALL_CHRE_MAIN_API_GET_TIME]                = { .func = osChreApiGetTime },
        [SYSCALL_CHRE_MAIN_API_GET_HOST_TIME_OFFSET]    = { .func = osChreApiGetHostTimeOffset },
        [SYSCALL_CHRE_MAIN_API_TIMER_SET]               = { .func = osChreApiTimerSet },
        [SYSCALL_CHRE_MAIN_API_TIMER_CANCEL]            = { .func = osChreApiTimerCancel },
        [SYSCALL_CHRE_MAIN_API_ABORT]                   = { .func = osChreApiAbort },
        [SYSCALL_CHRE_MAIN_API_HEAP_ALLOC]              = { .func = osChreApiHeapAlloc },
        [SYSCALL_CHRE_MAIN_API_HEAP_FREE]               = { .func = osChreApiHeapFree },
        [SYSCALL_CHRE_MAIN_API_SEND_EVENT]              = { .func = osChreEventSendEvent },
        [SYSCALL_CHRE_MAIN_API_SEND_MSG]                = { .func = osChreApiSendMessageToHost },
        [SYSCALL_CHRE_MAIN_API_SENSOR_FIND_DEFAULT]     = { .func = osChreApiSensorFindDefault },
        [SYSCALL_CHRE_MAIN_API_SENSOR_GET_INFO_OLD]     = { .func = osChreApiSensorGetInfoOld },
        [SYSCALL_CHRE_MAIN_API_SENSOR_GET_INFO]         = { .func = osChreApiSensorGetInfo },
        [SYSCALL_CHRE_MAIN_API_SENSOR_GET_STATUS]       = { .func = osChreApiSensorGetStatus },
        [SYSCALL_CHRE_MAIN_API_SENSOR_CONFIG]           = { .func = osChreApiSensorConfig },
        [SYSCALL_CHRE_MAIN_API_GET_OS_API_VERSION]      = { .func = osChreApiChreApiVersion },
        [SYSCALL_CHRE_MAIN_API_GET_OS_VERSION]          = { .func = osChreApiChreOsVersion },
        [SYSCALL_CHRE_MAIN_API_GET_PLATFORM_ID]         = { .func = osChreApiPlatformId },
    },
};

static const struct SyscallTable chreMainEventTable = {
    .numEntries = SYSCALL_CHRE_MAIN_EVENT_LAST,
    .entry = {
        [SYSCALL_CHRE_MAIN_EVENT_SEND_EVENT]           = { .func = osChreEventSendEvent },
        [SYSCALL_CHRE_MAIN_EVENT_SEND_MSG]             = { .func = osChreEventSendMessageToHost },
        [SYSCALL_CHRE_MAIN_EVENT_INFO_BY_APP_ID]       = { .func = osChreEventInfoByAppId },
        [SYSCALL_CHRE_MAIN_EVENT_INFO_BY_INST_ID]      = { .func = osChreEeventInfoByInstId },
        [SYSCALL_CHRE_MAIN_EVENT_CFG_INFO]             = { .func = osChreEventCfgInfo },
        [SYSCALL_CHRE_MAIN_EVENT_HOST_SLEEP]           = { .func = osChreEventHostSleep },
        [SYSCALL_CHRE_MAIN_EVENT_IS_HOST_AWAKE]        = { .func = osChreEventIsHostAwake },
    },
};

static const struct SyscallTable chreMainTable = {
    .numEntries = SYSCALL_CHRE_MAIN_LAST,
    .entry = {
        [SYSCALL_CHRE_MAIN_API]     = { .subtable = (struct SyscallTable*)&chreMainApiTable,     },
        [SYSCALL_CHRE_MAIN_EVENT]   = { .subtable = (struct SyscallTable*)&chreMainEventTable,   },
    },
};

static const struct SyscallTable chreDrvGnssTable = {
    .numEntries = SYSCALL_CHRE_DRV_GNSS_LAST,
    .entry = {
        [SYSCALL_CHRE_DRV_GNSS_GET_CAP]                 = { .func = osChreDrvGnssGetCap },
        [SYSCALL_CHRE_DRV_GNSS_LOC_START_ASYNC]         = { .func = osChreDrvGnssLocStartAsync },
        [SYSCALL_CHRE_DRV_GNSS_LOC_STOP_ASYNC]          = { .func = osChreDrvGnssLocStopAsync },
        [SYSCALL_CHRE_DRV_GNSS_MEAS_START_ASYNC]        = { .func = osChreDrvGnssMeasStartAsync },
        [SYSCALL_CHRE_DRV_GNSS_MEAS_STOP_ASYNC]         = { .func = osChreDrvGnssMeasStopAsync },
        [SYSCALL_CHRE_DRV_GNSS_CONF_PASV_LOC_LIS]       = { .func = osChreDrvGnssConfLocMon },
    },
};

static const struct SyscallTable chreDrvWifiTable = {
    .numEntries = SYSCALL_CHRE_DRV_WIFI_LAST,
    .entry = {
        [SYSCALL_CHRE_DRV_WIFI_GET_CAP]                 = { .func = osChreDrvWifiGetCap },
        [SYSCALL_CHRE_DRV_WIFI_CONF_SCAN_MON_ASYNC]     = { .func = osChreDrvWifiConfScanMonAsync },
        [SYSCALL_CHRE_DRV_WIFI_REQ_SCAN_ASYNC]          = { .func = osChreDrvWifiReqScanAsync },
    },
};

static const struct SyscallTable chreDrvWwanTable = {
    .numEntries = SYSCALL_CHRE_DRV_WWAN_LAST,
    .entry = {
        [SYSCALL_CHRE_DRV_WWAN_GET_CAP]                 = { .func = osChreDrvWwanGetCap },
        [SYSCALL_CHRE_DRV_WWAN_GET_CELL_INFO_ASYNC]     = { .func = osChreDrvWwanGetCallInfoAsync },
    },
};

static const struct SyscallTable chreDrvAudioTable = {
    .numEntries = SYSCALL_CHRE_DRV_AUDIO_LAST,
    .entry = {
        [SYSCALL_CHRE_DRV_AUDIO_GET_SRC]                = { .func = osChreDrvAudioGetSrc },
        [SYSCALL_CHRE_DRV_AUDIO_CONF_SRC]               = { .func = osChreDrvAudioConfSrc },
        [SYSCALL_CHRE_DRV_AUDIO_GET_STATUS]             = { .func = osChreDrvAudioGetStatus },
    },
};

static const struct SyscallTable chreDriversTable = {
    .numEntries = SYSCALL_CHRE_DRV_LAST,
    .entry = {
        [SYSCALL_CHRE_DRV_GNSS]     = { .subtable = (struct SyscallTable*)&chreDrvGnssTable,     },
        [SYSCALL_CHRE_DRV_WIFI]     = { .subtable = (struct SyscallTable*)&chreDrvWifiTable,     },
        [SYSCALL_CHRE_DRV_WWAN]     = { .subtable = (struct SyscallTable*)&chreDrvWwanTable,     },
        [SYSCALL_CHRE_DRV_AUDIO]    = { .subtable = (struct SyscallTable*)&chreDrvAudioTable     },
    },
};

static const struct SyscallTable chreTable = {
    .numEntries = SYSCALL_CHRE_LAST,
    .entry = {
        [SYSCALL_CHRE_MAIN]    = { .subtable = (struct SyscallTable*)&chreMainTable,    },
        [SYSCALL_CHRE_DRIVERS] = { .subtable = (struct SyscallTable*)&chreDriversTable, },
    },
};

void osChreApiExport()
{
    if (!syscallAddTable(SYSCALL_NO(SYSCALL_DOMAIN_CHRE,0,0,0), 1, (struct SyscallTable*)&chreTable))
            osLog(LOG_ERROR, "Failed to export CHRE OS API");
}
