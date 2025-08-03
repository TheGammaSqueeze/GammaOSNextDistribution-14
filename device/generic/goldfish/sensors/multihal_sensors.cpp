/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <cstdint>
#include <log/log.h>
#include <utils/SystemClock.h>
#include <multihal_sensors.h>
#include "sensor_list.h"

namespace goldfish {
using ahs21::SensorType;
using ahs10::EventPayload;
using ahs10::SensorFlagBits;
using ahs10::SensorStatus;
using ahs10::MetaDataEventType;
using ahs10::AdditionalInfoType;

namespace {
constexpr int64_t kMaxSamplingPeriodNs = 1000000000;

struct SensorsTransportStub : public SensorsTransport {
    int Send(const void*, int) override { return -1; }
    int Receive(void*, int) override { return -1; }
    bool Ok() const override { return false; }
    int Fd() const override { return -1; }
    const char* Name() const override { return "stub"; }
};

const SensorsTransportStub g_sensorsTransportStub;
}

MultihalSensors::MultihalSensors(SensorsTransportFactory stf)
        : m_sensorsTransportFactory(std::move(stf))
        , m_sensorsTransport(const_cast<SensorsTransportStub*>(&g_sensorsTransportStub))
        , m_batchInfo(getSensorNumber()) {
    {
        const auto st = m_sensorsTransportFactory();

        LOG_ALWAYS_FATAL_IF(!st->Ok(), "%s:%d: sensors transport is not opened",
                            __func__, __LINE__);

        using namespace std::literals;
        const std::string_view kListSensorsCmd = "list-sensors"sv;

        LOG_ALWAYS_FATAL_IF(st->Send(kListSensorsCmd.data(), kListSensorsCmd.size()) < 0,
                            "%s:%d: send for %s failed", __func__, __LINE__, st->Name());

        char buffer[64];
        const int len = st->Receive(buffer, sizeof(buffer) - 1);
        LOG_ALWAYS_FATAL_IF(len < 0, "%s:%d: receive for %s failed", __func__, __LINE__,
                            st->Name());

        buffer[len] = 0;
        uint32_t hostSensorsMask = 0;
        LOG_ALWAYS_FATAL_IF(sscanf(buffer, "%u", &hostSensorsMask) != 1,
                            "%s:%d: Can't parse qemud response", __func__, __LINE__);

        m_availableSensorsMask = hostSensorsMask & ((1u << getSensorNumber()) - 1);

        ALOGI("%s:%d: host sensors mask=%x, available sensors mask=%x",
              __func__, __LINE__, hostSensorsMask, m_availableSensorsMask);
    }

    LOG_ALWAYS_FATAL_IF(!::android::base::Socketpair(AF_LOCAL, SOCK_STREAM, 0,
                                                     &m_callersFd, &m_sensorThreadFd),
                        "%s:%d: Socketpair failed", __func__, __LINE__);

    setAdditionalInfoFrames();

    m_sensorThread = std::thread(&MultihalSensors::qemuSensorListenerThread, this);
    m_batchThread = std::thread(&MultihalSensors::batchThread, this);
}

MultihalSensors::~MultihalSensors() {
    m_batchRunning = false;
    m_batchUpdated.notify_one();
    m_batchThread.join();

    qemuSensorThreadSendCommand(kCMD_QUIT);
    m_sensorThread.join();
}

const std::string MultihalSensors::getName() {
    return "hal_sensors_2_1_impl_ranchu";
}

Return<void> MultihalSensors::debug(const hidl_handle& fd, const hidl_vec<hidl_string>& args) {
    (void)fd;
    (void)args;
    return {};
}

Return<void> MultihalSensors::getSensorsList_2_1(getSensorsList_2_1_cb _hidl_cb) {
    std::vector<SensorInfo> sensors;

    uint32_t mask = m_availableSensorsMask;
    for (int i = 0; mask; ++i, mask >>= 1) {
        if (mask & 1) {
            sensors.push_back(*getSensorInfoByHandle(i));
        }
    }

    _hidl_cb(sensors);
    return {};
}

Return<Result> MultihalSensors::setOperationMode(const OperationMode mode) {
    std::unique_lock<std::mutex> lock(m_mtx);

    if (m_activeSensorsMask) {
        return Result::INVALID_OPERATION;
    } else {
        m_opMode = mode;
        return Result::OK;
    }
}

Return<Result> MultihalSensors::activate(const int32_t sensorHandle,
                                         const bool enabled) {
    if (!isSensorHandleValid(sensorHandle)) {
        return Result::BAD_VALUE;
    }

    std::unique_lock<std::mutex> lock(m_mtx);
    BatchInfo& batchInfo = m_batchInfo[sensorHandle];

    if (enabled) {
        const SensorInfo* sensor = getSensorInfoByHandle(sensorHandle);
        LOG_ALWAYS_FATAL_IF(!sensor);
        if (sensor->flags & static_cast<uint32_t>(SensorFlagBits::ON_CHANGE_MODE)) {
            doPostSensorEventLocked(*sensor,
                                    activationOnChangeSensorEvent(sensorHandle, *sensor));
        } else {
            if (batchInfo.samplingPeriodNs <= 0) {
                return Result::BAD_VALUE;
            }

            BatchEventRef batchEventRef;
            batchEventRef.timestamp =
                ::android::elapsedRealtimeNano() + batchInfo.samplingPeriodNs;
            batchEventRef.sensorHandle = sensorHandle;
            batchEventRef.generation = ++batchInfo.generation;

            m_batchQueue.push(batchEventRef);
            m_batchUpdated.notify_one();
        }
        sendAdditionalInfoReport(sensorHandle);
        m_activeSensorsMask = m_activeSensorsMask | (1u << sensorHandle);
    } else {
        m_activeSensorsMask = m_activeSensorsMask & ~(1u << sensorHandle);
    }
    return Result::OK;
}

Event MultihalSensors::activationOnChangeSensorEvent(const int32_t sensorHandle,
                                                     const SensorInfo& sensor) const {
    Event event;
    EventPayload* payload = &event.u;

    switch (sensor.type) {
    case SensorType::LIGHT:
        payload->scalar = m_protocolState.lastLightValue;
        break;

    case SensorType::PROXIMITY:
        payload->scalar = m_protocolState.lastProximityValue;
        break;

    case SensorType::RELATIVE_HUMIDITY:
        payload->scalar = m_protocolState.lastRelativeHumidityValue;
        break;

    case SensorType::AMBIENT_TEMPERATURE:
        payload->scalar = m_protocolState.kSensorNoValue;
        break;

    case SensorType::HEART_RATE:
        // Heart rate sensor's first data after activation should be
        // SENSOR_STATUS_UNRELIABLE.
        payload->heartRate.status = SensorStatus::UNRELIABLE;
        payload->heartRate.bpm = 0;
        break;

    case SensorType::HINGE_ANGLE:
        switch (sensorHandle) {
        case kSensorHandleHingeAngle0:
            payload->scalar = m_protocolState.lastHingeAngle0Value;
            break;

        case kSensorHandleHingeAngle1:
            payload->scalar = m_protocolState.lastHingeAngle1Value;
            break;

        case kSensorHandleHingeAngle2:
            payload->scalar = m_protocolState.lastHingeAngle2Value;
            break;

        default:
            LOG_ALWAYS_FATAL("%s:%d: unexpected hinge sensor: %d",
                             __func__, __LINE__, sensorHandle);
            break;
        }
        break;

    default:
        LOG_ALWAYS_FATAL("%s:%d: unexpected sensor type: %u",
                         __func__, __LINE__, static_cast<unsigned>(sensor.type));
        break;
    }

    event.sensorHandle = sensorHandle;
    event.sensorType = sensor.type;
    event.timestamp = ::android::elapsedRealtimeNano();

    return event;
}

Return<Result> MultihalSensors::batch(const int32_t sensorHandle,
                                      const int64_t samplingPeriodNs,
                                      const int64_t maxReportLatencyNs) {
    (void)maxReportLatencyNs;

    if (!isSensorHandleValid(sensorHandle)) {
        return Result::BAD_VALUE;
    }

    const SensorInfo* sensor = getSensorInfoByHandle(sensorHandle);
    LOG_ALWAYS_FATAL_IF(!sensor);

    if (samplingPeriodNs < sensor->minDelay) {
        return Result::BAD_VALUE;
    }

    std::unique_lock<std::mutex> lock(m_mtx);
    if (m_opMode == OperationMode::NORMAL) {
        m_batchInfo[sensorHandle].samplingPeriodNs = samplingPeriodNs;

        auto minSamplingPeriodNs = kMaxSamplingPeriodNs;
        auto activeSensorsMask = m_activeSensorsMask;
        for (const auto& b : m_batchInfo) {
            if (activeSensorsMask & 1) {
                const auto periodNs = b.samplingPeriodNs;
                if ((periodNs > 0) && (periodNs < minSamplingPeriodNs)) {
                    minSamplingPeriodNs = periodNs;
                }
            }

            activeSensorsMask >>= 1;
        }

        const uint32_t sensorsUpdateIntervalMs = std::max(1, int(minSamplingPeriodNs / 1000000));
        m_protocolState.sensorsUpdateIntervalMs = sensorsUpdateIntervalMs;
        if (!setSensorsUpdateIntervalMs(*m_sensorsTransport, sensorsUpdateIntervalMs)) {
            qemuSensorThreadSendCommand(kCMD_RESTART);
        }
    }

    return Result::OK;
}

Return<Result> MultihalSensors::flush(const int32_t sensorHandle) {
    if (!isSensorHandleValid(sensorHandle)) {
        return Result::BAD_VALUE;
    }

    const SensorInfo* sensor = getSensorInfoByHandle(sensorHandle);
    LOG_ALWAYS_FATAL_IF(!sensor);

    std::unique_lock<std::mutex> lock(m_mtx);
    if (!isSensorActive(sensorHandle)) {
        return Result::BAD_VALUE;
    }

    Event event;
    event.sensorHandle = sensorHandle;
    event.sensorType = SensorType::META_DATA;
    event.u.meta.what = MetaDataEventType::META_DATA_FLUSH_COMPLETE;

    doPostSensorEventLocked(*sensor, event);
    sendAdditionalInfoReport(sensorHandle);

    return Result::OK;
}

Return<Result> MultihalSensors::injectSensorData_2_1(const Event& event) {
    if (!isSensorHandleValid(event.sensorHandle)) {
        return Result::BAD_VALUE;
    }
    if (event.sensorType == SensorType::ADDITIONAL_INFO) {
        return Result::OK;
    }

    std::unique_lock<std::mutex> lock(m_mtx);
    if (m_opMode != OperationMode::DATA_INJECTION) {
        return Result::INVALID_OPERATION;
    }
    const SensorInfo* sensor = getSensorInfoByHandle(event.sensorHandle);
    LOG_ALWAYS_FATAL_IF(!sensor);
    if (sensor->type != event.sensorType) {
        return Result::BAD_VALUE;
    }

    doPostSensorEventLocked(*sensor, event);
    return Result::OK;
}

Return<Result> MultihalSensors::initialize(const sp<IHalProxyCallback>& halProxyCallback) {
    std::unique_lock<std::mutex> lock(m_mtx);
    m_opMode = OperationMode::NORMAL;
    m_halProxyCallback = halProxyCallback;
    return Result::OK;
}

void MultihalSensors::postSensorEventLocked(const Event& event) {
    const SensorInfo* sensor = getSensorInfoByHandle(event.sensorHandle);
    LOG_ALWAYS_FATAL_IF(!sensor);

    if (sensor->flags & static_cast<uint32_t>(SensorFlagBits::ON_CHANGE_MODE)) {
        if (isSensorActive(event.sensorHandle)) {
            doPostSensorEventLocked(*sensor, event);
        }
    } else {    // CONTINUOUS_MODE
        m_batchInfo[event.sensorHandle].event = event;
    }
}

void MultihalSensors::doPostSensorEventLocked(const SensorInfo& sensor,
                                              const Event& event) {
    const bool isWakeupEvent =
        sensor.flags & static_cast<uint32_t>(SensorFlagBits::WAKE_UP);

    m_halProxyCallback->postEvents(
        {event},
        m_halProxyCallback->createScopedWakelock(isWakeupEvent));
}

void MultihalSensors::setAdditionalInfoFrames() {
    // https://developer.android.com/reference/android/hardware/SensorAdditionalInfo#TYPE_SENSOR_PLACEMENT
    AdditionalInfo additionalInfoSensorPlacement = {
            .type = AdditionalInfoType::AINFO_SENSOR_PLACEMENT,
            .serial = 0,
            .u.data_float{ {0, 1, 0, 0, -1, 0, 0, 10, 0, 0, 1, -2.5} },
    };
    const AdditionalInfo additionalInfoBegin = {
            .type = AdditionalInfoType::AINFO_BEGIN,
            .serial = 0,
    };
    const AdditionalInfo additionalInfoEnd = {
            .type = AdditionalInfoType::AINFO_END,
            .serial = 0,
    };

    mAdditionalInfoFrames.insert(
            mAdditionalInfoFrames.end(),
            {additionalInfoBegin, additionalInfoSensorPlacement, additionalInfoEnd});
}

void MultihalSensors::sendAdditionalInfoReport(int sensorHandle) {
    const SensorInfo* sensor = getSensorInfoByHandle(sensorHandle);
    const bool isWakeupEvent =
        sensor->flags & static_cast<uint32_t>(SensorFlagBits::WAKE_UP);
    std::vector<Event> events;

    for (const auto& frame : mAdditionalInfoFrames) {
        events.emplace_back(Event{
                .timestamp = android::elapsedRealtimeNano(),
                .sensorHandle = sensorHandle,
                .sensorType = SensorType::ADDITIONAL_INFO,
                .u.additional = frame,
        });
    }

    if (!events.empty()) {
        m_halProxyCallback->postEvents(
                events,
                m_halProxyCallback->createScopedWakelock(isWakeupEvent));
    }
}

bool MultihalSensors::qemuSensorThreadSendCommand(const char cmd) const {
    return TEMP_FAILURE_RETRY(write(m_callersFd.get(), &cmd, 1)) == 1;
}

bool MultihalSensors::isSensorHandleValid(int sensorHandle) const {
    if (!goldfish::isSensorHandleValid(sensorHandle)) {
        return false;
    }

    if (!(m_availableSensorsMask & (1u << sensorHandle))) {
        return false;
    }

    return true;
}

void MultihalSensors::qemuSensorListenerThread() {
    while (true) {
        const auto st = m_sensorsTransportFactory();

        LOG_ALWAYS_FATAL_IF(!setSensorsGuestTime(
            *st, ::android::elapsedRealtimeNano()));
        LOG_ALWAYS_FATAL_IF(!setSensorsUpdateIntervalMs(
            *st, m_protocolState.sensorsUpdateIntervalMs));
        LOG_ALWAYS_FATAL_IF(!setAllSensorsReporting(
            *st, m_availableSensorsMask, true));

        {
            std::unique_lock<std::mutex> lock(m_mtx);
            m_sensorsTransport = st.get();
        }

        const bool cont = qemuSensorListenerThreadImpl(st->Fd());

        {
            std::unique_lock<std::mutex> lock(m_mtx);
            m_sensorsTransport = const_cast<SensorsTransportStub*>(&g_sensorsTransportStub);
        }

        if (!cont) {
            break;
        }
    }
}

void MultihalSensors::batchThread() {
    while (m_batchRunning) {
        std::unique_lock<std::mutex> lock(m_mtx);
        if (m_batchQueue.empty()) {
            m_batchUpdated.wait(lock);
        } else {
            const int64_t d =
                m_batchQueue.top().timestamp - ::android::elapsedRealtimeNano();
            m_batchUpdated.wait_for(lock, std::chrono::nanoseconds(d));
        }

        const int64_t nowNs = ::android::elapsedRealtimeNano();
        while (!m_batchQueue.empty() && (nowNs >= m_batchQueue.top().timestamp)) {
            BatchEventRef evRef = m_batchQueue.top();
            m_batchQueue.pop();

            const int sensorHandle = evRef.sensorHandle;
            LOG_ALWAYS_FATAL_IF(!goldfish::isSensorHandleValid(sensorHandle));
            if (!isSensorActive(sensorHandle)) {
                continue;
            }

            BatchInfo &batchInfo = m_batchInfo[sensorHandle];
            if (batchInfo.event.sensorType == SensorType::META_DATA) {
                ALOGW("%s:%d the host has not provided value yet for sensorHandle=%d",
                      __func__, __LINE__, sensorHandle);
            } else {
                batchInfo.event.timestamp = evRef.timestamp;
                const SensorInfo* sensor = getSensorInfoByHandle(sensorHandle);
                LOG_ALWAYS_FATAL_IF(!sensor);
                doPostSensorEventLocked(*sensor, batchInfo.event);
            }

            if (evRef.generation == batchInfo.generation) {
                const int64_t samplingPeriodNs = batchInfo.samplingPeriodNs;
                LOG_ALWAYS_FATAL_IF(samplingPeriodNs <= 0);

                evRef.timestamp += samplingPeriodNs;
                m_batchQueue.push(evRef);
            }
        }
    }
}

/// not supported //////////////////////////////////////////////////////////////
Return<void> MultihalSensors::registerDirectChannel(const SharedMemInfo& mem,
                                                    registerDirectChannel_cb _hidl_cb) {
    (void)mem;
    _hidl_cb(Result::INVALID_OPERATION, -1);
    return {};
}

Return<Result> MultihalSensors::unregisterDirectChannel(int32_t channelHandle) {
    (void)channelHandle;
    return Result::INVALID_OPERATION;
}

Return<void> MultihalSensors::configDirectReport(int32_t sensorHandle,
                                                 int32_t channelHandle,
                                                 RateLevel rate,
                                                 configDirectReport_cb _hidl_cb) {
    (void)sensorHandle;
    (void)channelHandle;
    (void)rate;
    _hidl_cb(Result::INVALID_OPERATION, 0 /* reportToken */);
    return {};
}

}  // namespace goldfish
