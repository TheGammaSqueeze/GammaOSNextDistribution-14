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

#include <stdarg.h>

#include <gpio.h>
#include <osApi.h>
#include <sensors.h>
#include <seos.h>
#include <util.h>

/* CHRE syscalls */
#include <chre.h>
#include <chreApi.h>
#include <syscall.h>
#include <syscall_defs.h>

#define SYSCALL_CHRE_API(name) \
    SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_API, SYSCALL_CHRE_MAIN_API_ ## name)

uint64_t chreGetAppId(void)
{
    uint64_t appId = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_APP_ID), &appId);
    return appId;
}

uint32_t chreGetInstanceId(void)
{
    return syscallDo0P(SYSCALL_CHRE_API(GET_INST_ID));
}

uint64_t chreGetTime(void) {
    uint64_t time_ns = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_TIME), &time_ns);
    return time_ns;
}

int64_t chreGetEstimatedHostTimeOffset(void) {
    int64_t time_ns = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_HOST_TIME_OFFSET), &time_ns);
    return time_ns;
}

void chreLog(enum chreLogLevel level, const char *str, ...)
{
    va_list vl;

    va_start(vl, str);
    (void)syscallDo3P(SYSCALL_CHRE_API(LOG), level, str, VA_LIST_TO_INTEGER(vl));
    va_end(vl);
}

uint32_t chreTimerSet(uint64_t duration, const void* cookie, bool oneShot)
{
    uint32_t dur_lo = duration;
    uint32_t dur_hi = duration >> 32;
    return syscallDo4P(SYSCALL_CHRE_API(TIMER_SET), dur_lo, dur_hi, cookie, oneShot);
}

bool chreTimerCancel(uint32_t timerId)
{
    return syscallDo1P(SYSCALL_CHRE_API(TIMER_CANCEL), timerId);
}

void chreAbort(uint32_t abortCode)
{
    (void)syscallDo1P(SYSCALL_CHRE_API(ABORT), abortCode);
}

void* chreHeapAlloc(uint32_t bytes)
{
    return (void *)syscallDo1P(SYSCALL_CHRE_API(HEAP_ALLOC), bytes);
}

void chreHeapFree(void* ptr)
{
    (void)syscallDo1P(SYSCALL_CHRE_API(HEAP_FREE), ptr);
}

bool chreSensorFindDefault(uint8_t sensorType, uint32_t *handle)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_FIND_DEFAULT), sensorType, handle);
}

bool chreGetSensorInfo(uint32_t sensorHandle, struct chreSensorInfo *info)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_GET_INFO), sensorHandle, info);
}

bool chreGetSensorSamplingStatus(uint32_t sensorHandle,
                                 struct chreSensorSamplingStatus *status)
{
    return syscallDo2P(SYSCALL_CHRE_API(SENSOR_GET_STATUS), sensorHandle, status);
}

bool chreSensorConfigure(uint32_t sensorHandle,
                         enum chreSensorConfigureMode mode,
                         uint64_t interval, uint64_t latency)
{
    uint32_t interval_lo = interval;
    uint32_t interval_hi = interval >> 32;
    uint32_t latency_lo = latency;
    uint32_t latency_hi = latency >> 32;
    return syscallDo6P(SYSCALL_CHRE_API(SENSOR_CONFIG), sensorHandle, mode,
                       interval_lo, interval_hi, latency_lo, latency_hi);
}

uint32_t chreGetApiVersion(void)
{
    static uint32_t apiVersion = 0;
    if (!apiVersion)
        apiVersion = syscallDo0P(SYSCALL_CHRE_API(GET_OS_API_VERSION));
    return apiVersion;
}

uint32_t chreGetVersion(void)
{
    return syscallDo0P(SYSCALL_CHRE_API(GET_OS_VERSION));
}

uint64_t chreGetPlatformId(void)
{
    uint64_t plat = 0;
    (void)syscallDo1P(SYSCALL_CHRE_API(GET_PLATFORM_ID), &plat);
    return plat;
}

bool chreSendEvent(uint16_t eventType, void *eventData,
                   chreEventCompleteFunction *freeCallback,
                   uint32_t targetInstanceId)
{
    if (chreGetApiVersion() == CHRE_API_VERSION_1_0)
        return syscallDo4P(SYSCALL_CHRE_API(SEND_EVENT), eventType, eventData, freeCallback, targetInstanceId);
    else
        return syscallDo4P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_SEND_EVENT), eventType, eventData, freeCallback, targetInstanceId);
}

bool chreSendMessageToHost(void *message, uint32_t messageSize,
                           uint32_t messageType,
                           chreMessageFreeFunction *freeCallback)
{
    if (chreGetApiVersion() == CHRE_API_VERSION_1_0)
        return syscallDo4P(SYSCALL_CHRE_API(SEND_MSG), message, messageSize, messageType, freeCallback);
    else
        return syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_SEND_MSG), message, messageSize, messageType, CHRE_HOST_ENDPOINT_BROADCAST, freeCallback);
}

bool chreSendMessageToHostEndpoint(void *message, size_t messageSize,
                                   uint32_t messageType, uint16_t hostEndpoint,
                                   chreMessageFreeFunction *freeCallback)
{
    if (chreGetApiVersion() == CHRE_API_VERSION_1_0)
        return syscallDo4P(SYSCALL_CHRE_API(SEND_MSG), message, messageSize, messageType, freeCallback);
    else
        return syscallDo5P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_SEND_MSG), message, messageSize, messageType, hostEndpoint, freeCallback);
}

bool chreGetNanoappInfoByAppId(uint64_t appId, struct chreNanoappInfo *info)
{
    uint32_t app_lo = appId;
    uint32_t app_hi = appId >> 32;
    return syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_INFO_BY_APP_ID), app_lo, app_hi, info);
}

bool chreGetNanoappInfoByInstanceId(uint32_t instanceId, struct chreNanoappInfo *info)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_INFO_BY_INST_ID), instanceId, info);
}

void chreConfigureNanoappInfoEvents(bool enable)
{
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_CFG_INFO), enable);
}

void chreConfigureHostSleepStateEvents(bool enable)
{
    syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_HOST_SLEEP), enable);
}

bool chreIsHostAwake(void)
{
    return syscallDo0P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_MAIN, SYSCALL_CHRE_MAIN_EVENT, SYSCALL_CHRE_MAIN_EVENT_IS_HOST_AWAKE));
}

uint32_t chreGnssGetCapabilities(void)
{
    return syscallDo0P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_GET_CAP));
}

bool chreGnssLocationSessionStartAsync(uint32_t minIntervalMs, uint32_t minTimeToNextFixMs, const void *cookie)
{
    return syscallDo3P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_LOC_START_ASYNC), minIntervalMs, minTimeToNextFixMs, cookie);
}

bool chreGnssLocationSessionStopAsync(const void *cookie)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_LOC_STOP_ASYNC), cookie);
}

bool chreGnssMeasurementSessionStartAsync(uint32_t minIntervalMs, const void *cookie)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_MEAS_START_ASYNC), minIntervalMs, cookie);
}

bool chreGnssMeasurementSessionStopAsync(const void *cookie)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_MEAS_STOP_ASYNC), cookie);
}

bool chreGnssConfigurePassiveLocationListener(bool enable)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_GNSS, SYSCALL_CHRE_DRV_GNSS_CONF_PASV_LOC_LIS), enable);
}

uint32_t chreWifiGetCapabilities(void)
{
    return syscallDo0P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_WIFI, SYSCALL_CHRE_DRV_WIFI_GET_CAP));
}

bool chreWifiConfigureScanMonitorAsync(bool enable, const void *cookie)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_WIFI, SYSCALL_CHRE_DRV_WIFI_CONF_SCAN_MON_ASYNC), enable, cookie);
}

bool chreWifiRequestScanAsync(const struct chreWifiScanParams *params, const void *cookie)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_WIFI, SYSCALL_CHRE_DRV_WIFI_REQ_SCAN_ASYNC), params, cookie);
}

uint32_t chreWwanGetCapabilities(void)
{
    return syscallDo0P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_WWAN, SYSCALL_CHRE_DRV_WWAN_GET_CAP));
}

bool chreWwanGetCellInfoAsync(const void *cookie)
{
    return syscallDo1P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_WWAN, SYSCALL_CHRE_DRV_WWAN_GET_CELL_INFO_ASYNC), cookie);
}

bool chreAudioGetSource(uint32_t handle, struct chreAudioSource *audioSource)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_AUDIO, SYSCALL_CHRE_DRV_AUDIO_GET_SRC), handle, audioSource);
}

bool chreAudioConfigureSource(uint32_t handle, bool enable,
                              uint64_t bufferDuration,
                              uint64_t deliveryInterval)
{
    uint32_t duration_lo = bufferDuration;
    uint32_t duration_hi = bufferDuration >> 32;
    uint32_t interval_lo = deliveryInterval;
    uint32_t interval_hi = deliveryInterval >> 32;

    return syscallDo6P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_AUDIO, SYSCALL_CHRE_DRV_AUDIO_CONF_SRC), handle, enable, duration_lo, duration_hi, interval_lo, interval_hi);
}

bool chreAudioGetStatus(uint32_t handle, struct chreAudioSourceStatus *status)
{
    return syscallDo2P(SYSCALL_NO(SYSCALL_DOMAIN_CHRE, SYSCALL_CHRE_DRIVERS, SYSCALL_CHRE_DRV_AUDIO, SYSCALL_CHRE_DRV_AUDIO_GET_STATUS), handle, status);
}
