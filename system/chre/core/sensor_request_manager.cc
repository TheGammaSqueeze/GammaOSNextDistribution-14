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

#include "chre/core/sensor_request_manager.h"

#include "chre/core/event_loop_manager.h"
#include "chre/util/macros.h"
#include "chre/util/nested_data_ptr.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/event_callbacks.h"
#include "chre/util/time.h"
#include "chre_api/chre/version.h"

#define LOG_INVALID_HANDLE(x) \
  LOGE("Invalid sensor handle %" PRIu32 ": line %d", x, __LINE__)

namespace chre {
namespace {

bool isSensorRequestValid(const Sensor &sensor,
                          const SensorRequest &sensorRequest) {
  bool isRequestOneShot = sensorModeIsOneShot(sensorRequest.getMode());
  bool isRequestOff = sensorRequest.getMode() == SensorMode::Off;
  uint64_t requestedInterval = sensorRequest.getInterval().toRawNanoseconds();
  bool isRequestPassive = sensorModeIsPassive(sensorRequest.getMode());

  bool success = false;
  if (!isRequestOff && requestedInterval < sensor.getMinInterval()) {
    LOGE("Requested interval %" PRIu64 " < sensor's minInterval %" PRIu64,
         requestedInterval, sensor.getMinInterval());
  } else if (!isRequestOff && isRequestOneShot != sensor.isOneShot()) {
    LOGE("Invalid request type for sensor reporting mode");
  } else if (isRequestPassive && !sensor.supportsPassiveMode()) {
    LOGE("Passive mode not supported");
  } else {
    success = true;
  }
  return success;
}

/**
 * A helper function that updates the last event of a sensor in the main thread.
 *
 * @param eventData A non-null pointer to the sensor's CHRE event data.
 */
void updateLastEvent(void *eventData) {
  CHRE_ASSERT(eventData);

  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    auto *sensorData = static_cast<ChreSensorData *>(data);
    Sensor *sensor =
        EventLoopManagerSingleton::get()->getSensorRequestManager().getSensor(
            sensorData->header.sensorHandle);

    // Mark last event as valid only if the sensor is enabled. Event data may
    // arrive after sensor is disabled.
    if (sensor != nullptr &&
        sensor->getMaximalRequest().getMode() != SensorMode::Off) {
      sensor->setLastEvent(sensorData);
    }
  };

  // Schedule a deferred callback.
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::SensorLastEventUpdate, eventData, callback);
}

void sensorDataEventFree(uint16_t eventType, void *eventData) {
  EventLoopManagerSingleton::get()
      ->getSensorRequestManager()
      .releaseSensorDataEvent(eventType, eventData);
}

/**
 * Posts a CHRE_EVENT_SENSOR_SAMPLING_CHANGE event to the specified Nanoapp.
 *
 * @param instanceId The instance ID of the nanoapp with an open request.
 * @param sensorHandle The handle of the sensor.
 * @param status A reference of the sampling status to be posted.
 */
void postSamplingStatusEvent(uint16_t instanceId, uint32_t sensorHandle,
                             const struct chreSensorSamplingStatus &status) {
  auto *event = memoryAlloc<struct chreSensorSamplingStatusEvent>();
  if (event == nullptr) {
    LOG_OOM();
  } else {
    event->sensorHandle = sensorHandle;
    event->status = status;

    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_SENSOR_SAMPLING_CHANGE, event, freeEventDataCallback,
        instanceId);
  }
}

/**
 * Notifies all listening nanoapps of the latest sampling status update.
 *
 * @param sensorHandle The handle of the sensor.
 * @param status A reference of the sampling status to be posted.
 */
void postSamplingStatus(uint32_t sensorHandle,
                        struct chreSensorSamplingStatus &status) {
  // Only post to Nanoapps with an open request.
  const DynamicVector<SensorRequest> &requests =
      EventLoopManagerSingleton::get()->getSensorRequestManager().getRequests(
          sensorHandle);
  for (const auto &req : requests) {
    postSamplingStatusEvent(req.getInstanceId(), sensorHandle, status);
  }
}

}  // namespace

SensorRequestManager::~SensorRequestManager() {
  for (size_t i = 0; i < mSensors.size(); i++) {
    // Disable sensors that have been enabled previously.
    removeAllRequests(mSensors[i]);
  }
}

void SensorRequestManager::init() {
  // The Platform sensor must be initialized prior to interacting with any
  // sensors.
  mPlatformSensorManager.init();

  mSensors = mPlatformSensorManager.getSensors();
}

bool SensorRequestManager::getSensorHandle(uint8_t sensorType,
                                           uint8_t sensorIndex,
                                           uint16_t targetGroupId,
                                           uint32_t *sensorHandle) const {
  CHRE_ASSERT(sensorHandle);

  bool sensorHandleIsValid = false;
  for (uint32_t i = 0; i < mSensors.size(); i++) {
    if ((mSensors[i].getSensorType() == sensorType) &&
        (mSensors[i].getSensorIndex() == sensorIndex) &&
        (BITMASK_HAS_VALUE(mSensors[i].getTargetGroupMask(), targetGroupId))) {
      sensorHandleIsValid = true;
      *sensorHandle = i;
      break;
    }
  }

  return sensorHandleIsValid;
}

bool SensorRequestManager::setSensorRequest(
    Nanoapp *nanoapp, uint32_t sensorHandle,
    const SensorRequest &sensorRequest) {
  CHRE_ASSERT(nanoapp);

  bool success = false;
  bool requestChanged = false;

  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else {
    Sensor &sensor = mSensors[sensorHandle];
    if (isSensorRequestValid(sensor, sensorRequest)) {
      // Copy the request so it can be modified below.
      SensorRequest request = sensorRequest;
      if (sensor.isOneShot()) {
        // Always use a latency value of ASAP for one-shot sensors since
        // one-shot data is always expected to be delivered immediately.
        request.setLatency(Nanoseconds(CHRE_SENSOR_LATENCY_ASAP));
      }

      size_t requestIndex;
      uint8_t sensorType = sensor.getSensorType();
      uint16_t eventType = getSampleEventTypeForSensorType(sensorType);
      bool nanoappHasRequest =
          sensor.getRequestMultiplexer().findRequest(nanoapp->getInstanceId(),
                                                     &requestIndex) != nullptr;

      if (request.getMode() == SensorMode::Off) {
        if (nanoappHasRequest) {
          // The request changes the mode to off and there was an existing
          // request. The existing request is removed from the multiplexer. The
          // nanoapp is unregistered from events of this type if this request
          // was successful.
          success = removeRequest(sensor, requestIndex, &requestChanged);
          if (success) {
            cancelFlushRequests(sensorHandle, nanoapp->getInstanceId());

            // Only unregister if nanoapp no longer has an outstanding request
            // for a sensor + target group mask.
            uint16_t activeMask =
                getActiveTargetGroupMask(nanoapp->getInstanceId(), sensorType);
            uint16_t inactiveMask = sensor.getTargetGroupMask() & ~activeMask;
            if (inactiveMask != 0) {
              nanoapp->unregisterForBroadcastEvent(eventType, inactiveMask);

              uint16_t biasEventType;
              if (sensor.getBiasEventType(&biasEventType)) {
                // Per API requirements, turn off bias reporting when
                // unsubscribing from the sensor.
                nanoapp->unregisterForBroadcastEvent(biasEventType,
                                                     inactiveMask);
              }
            }
          }
        } else {
          // The sensor is being configured to Off, but is already Off (there is
          // no existing request). We assign to success to be true and no other
          // operation is required.
          success = true;
        }
      } else if (!nanoappHasRequest) {
        // The request changes the mode to the enabled state and there was no
        // existing request. The request is newly created and added to the
        // multiplexer. The nanoapp is registered for events if this request was
        // successful.
        uint16_t biasEventType;
        if (sensor.getBiasEventType(&biasEventType) && sensor.isCalibrated()) {
          // Per API requirements, turn on bias reporting for calibrated sensors
          // by default when subscribed.
          request.setBiasUpdatesRequested(true);
        }

        success = addRequest(sensor, request, &requestChanged);
        if (success) {
          nanoapp->registerForBroadcastEvent(eventType,
                                             sensor.getTargetGroupMask());

          if (request.getBiasUpdatesRequested()) {
            nanoapp->registerForBroadcastEvent(biasEventType,
                                               sensor.getTargetGroupMask());
          }

          // Deliver last valid event to new clients of on-change sensors
          if (sensor.getLastEvent() != nullptr) {
            EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
                eventType, sensor.getLastEvent(), nullptr /* freeCallback */,
                nanoapp->getInstanceId());
          }
        }
      } else {
        // Ensure bias events stay requested if they were previously enabled.
        const SensorRequest &previousRequest =
            sensor.getRequestMultiplexer().getRequests()[requestIndex];
        if (previousRequest.getBiasUpdatesRequested()) {
          request.setBiasUpdatesRequested(true);
        }
        // The request changes the mode to the enabled state and there was an
        // existing request. The existing request is updated.
        success = updateRequest(sensor, requestIndex, request, &requestChanged);
      }

      // TODO: Allow translating the sensor request

      if (requestChanged) {
        // TODO: Send an event to nanoapps to indicate the rate change.
      }

      if (success) {
        addSensorRequestLog(nanoapp->getInstanceId(), sensorHandle, request);
      }
    }
  }

  return success;
}

bool SensorRequestManager::getSensorInfo(uint32_t sensorHandle,
                                         const Nanoapp &nanoapp,
                                         struct chreSensorInfo *info) const {
  CHRE_ASSERT(info);

  bool success = false;
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else {
    mSensors[sensorHandle].populateSensorInfo(info,
                                              nanoapp.getTargetApiVersion());
    success = true;
  }

  return success;
}

bool SensorRequestManager::removeAllRequests(uint32_t sensorHandle) {
  bool success = false;
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else {
    Sensor &sensor = mSensors[sensorHandle];
    uint8_t sensorType = sensor.getSensorType();
    uint16_t eventType = getSampleEventTypeForSensorType(sensorType);
    for (const SensorRequest &request : sensor.getRequests()) {
      Nanoapp *nanoapp = EventLoopManagerSingleton::get()
                             ->getEventLoop()
                             .findNanoappByInstanceId(request.getInstanceId());
      if (nanoapp != nullptr) {
        nanoapp->unregisterForBroadcastEvent(eventType,
                                             sensor.getTargetGroupMask());
      }
    }

    cancelFlushRequests(sensorHandle);
    success = removeAllRequests(sensor);
  }

  return success;
}

Sensor *SensorRequestManager::getSensor(uint32_t sensorHandle) {
  Sensor *sensorPtr = nullptr;
  if (sensorHandle < mSensors.size()) {
    sensorPtr = &mSensors[sensorHandle];
  }
  return sensorPtr;
}

bool SensorRequestManager::getSensorSamplingStatus(
    uint32_t sensorHandle, struct chreSensorSamplingStatus *status) const {
  CHRE_ASSERT(status);

  bool success = false;
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else {
    success = mSensors[sensorHandle].getSamplingStatus(status);
  }

  return success;
}

const DynamicVector<SensorRequest> &SensorRequestManager::getRequests(
    uint32_t sensorHandle) const {
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
    sensorHandle = 0;
  }
  return mSensors[sensorHandle].getRequests();
}

bool SensorRequestManager::configureBiasEvents(Nanoapp *nanoapp,
                                               uint32_t sensorHandle,
                                               bool enable) {
  bool success = false;
  uint16_t eventType;
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else if (enable && !mSensors[sensorHandle].isSensorEnabled()) {
    LOGE("Bias events can't be configured for a disabled sensor!");
  } else if (mSensors[sensorHandle].getBiasEventType(&eventType)) {
    Sensor &sensor = mSensors[sensorHandle];
    size_t requestIndex;
    bool nanoappHasRequest =
        sensor.getRequestMultiplexer().findRequest(nanoapp->getInstanceId(),
                                                   &requestIndex) != nullptr;
    if (enable && !nanoappHasRequest) {
      LOGE("0x%" PRIx64
           " configuring bias events without an existing sensor request",
           nanoapp->getAppId());
    } else if (!enable && !nanoappHasRequest) {
      // Treat configuration request as a success since the nanoapp's request
      // already has been removed which would result in disabling bias event
      // updates
      success = true;
    } else {
      SensorRequest previousRequest =
          sensor.getRequestMultiplexer().getRequests()[requestIndex];
      previousRequest.setBiasUpdatesRequested(enable);
      bool requestChanged;
      success =
          updateRequest(sensor, requestIndex, previousRequest, &requestChanged);
      if (success) {
        if (enable) {
          nanoapp->registerForBroadcastEvent(eventType,
                                             sensor.getTargetGroupMask());
        } else {
          nanoapp->unregisterForBroadcastEvent(eventType,
                                               sensor.getTargetGroupMask());
        }
      }
    }
  }

  return success;
}

bool SensorRequestManager::getThreeAxisBias(
    uint32_t sensorHandle, struct chreSensorThreeAxisData *bias) const {
  CHRE_ASSERT(bias != nullptr);

  bool success = false;
  if (bias != nullptr) {
    if (sensorHandle >= mSensors.size()) {
      LOG_INVALID_HANDLE(sensorHandle);
    } else {
      success =
          mPlatformSensorManager.getThreeAxisBias(mSensors[sensorHandle], bias);
    }
  }

  return success;
}

bool SensorRequestManager::flushAsync(Nanoapp *nanoapp, uint32_t sensorHandle,
                                      const void *cookie) {
  bool success = false;

  uint16_t nanoappInstanceId = nanoapp->getInstanceId();
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
  } else if (mSensors[sensorHandle].isOneShot()) {
    LOGE("Cannot flush a one-shot sensor of type %" PRIu8,
         mSensors[sensorHandle].getSensorType());
  } else if (mFlushRequestQueue.full()) {
    LOG_OOM();
  } else {
    mFlushRequestQueue.emplace_back(sensorHandle, nanoappInstanceId, cookie);
    success = makeFlushRequest(mFlushRequestQueue.back()) == CHRE_ERROR_NONE;
    if (!success) {
      mFlushRequestQueue.pop_back();
    }
  }

  return success;
}

void SensorRequestManager::releaseSensorDataEvent(uint16_t eventType,
                                                  void *eventData) {
  // Remove all requests if it's a one-shot sensor and only after data has been
  // delivered to all clients.
  mPlatformSensorManager.releaseSensorDataEvent(eventData);
  uint8_t sensorType = getSensorTypeForSampleEventType(eventType);
  uint32_t sensorHandle;
  if (getDefaultSensorHandle(sensorType, &sensorHandle) &&
      mSensors[sensorHandle].isOneShot()) {
    removeAllRequests(sensorHandle);
  }
}

void SensorRequestManager::handleFlushCompleteEvent(uint32_t sensorHandle,
                                                    uint32_t flushRequestId,
                                                    uint8_t errorCode) {
  UNUSED_VAR(flushRequestId);

  if (sensorHandle < mSensors.size() &&
      mSensors[sensorHandle].isFlushRequestPending()) {
    // Cancel flush request timer before posting to the event queue to ensure
    // a timeout event isn't processed by CHRE now that the complete event
    // has been received.
    mSensors[sensorHandle].cancelPendingFlushRequestTimer();

    auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
      uint8_t cbErrorCode = NestedDataPtr<uint8_t>(data);
      uint32_t cbSensorHandle = NestedDataPtr<uint32_t>(extraData);
      EventLoopManagerSingleton::get()
          ->getSensorRequestManager()
          .handleFlushCompleteEventSync(cbErrorCode, cbSensorHandle);
    };

    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::SensorFlushComplete,
        NestedDataPtr<uint8_t>(errorCode), callback,
        NestedDataPtr<uint32_t>(sensorHandle));
  }
}

void SensorRequestManager::handleSensorDataEvent(uint32_t sensorHandle,
                                                 void *event) {
  if (sensorHandle >= mSensors.size()) {
    LOG_INVALID_HANDLE(sensorHandle);
    mPlatformSensorManager.releaseSensorDataEvent(event);
  } else {
    Sensor &sensor = mSensors[sensorHandle];
    if (sensor.isOnChange()) {
      updateLastEvent(event);
    }

    uint16_t eventType =
        getSampleEventTypeForSensorType(sensor.getSensorType());

    // Only allow dropping continuous sensor events since losing one-shot or
    // on-change events could result in nanoapps stuck in a bad state.
    if (sensor.isContinuous()) {
      EventLoopManagerSingleton::get()
          ->getEventLoop()
          .postLowPriorityEventOrFree(eventType, event, sensorDataEventFree,
                                      kSystemInstanceId, kBroadcastInstanceId,
                                      sensor.getTargetGroupMask());
    } else {
      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          eventType, event, sensorDataEventFree, kBroadcastInstanceId,
          sensor.getTargetGroupMask());
    }
  }
}

void SensorRequestManager::handleSamplingStatusUpdate(
    uint32_t sensorHandle, struct chreSensorSamplingStatus *status) {
  Sensor *sensor =
      EventLoopManagerSingleton::get()->getSensorRequestManager().getSensor(
          sensorHandle);
  if (sensor == nullptr || sensor->isOneShot()) {
    releaseSamplingStatusUpdate(status);
  } else {
    sensor->setSamplingStatus(*status);

    auto callback = [](uint16_t /*type*/, void *data, void *extraData) {
      uint32_t cbSensorHandle = NestedDataPtr<uint32_t>(data);
      auto *cbStatus =
          static_cast<struct chreSensorSamplingStatus *>(extraData);
      postSamplingStatus(cbSensorHandle, *cbStatus);
      EventLoopManagerSingleton::get()
          ->getSensorRequestManager()
          .releaseSamplingStatusUpdate(cbStatus);
    };

    // Schedule a deferred callback to handle sensor status change in the main
    // thread.
    EventLoopManagerSingleton::get()->deferCallback(
        SystemCallbackType::SensorStatusUpdate,
        NestedDataPtr<uint32_t>(sensorHandle), callback, status);
  }
}

void SensorRequestManager::handleBiasEvent(uint32_t sensorHandle,
                                           void *biasData) {
  Sensor *sensor =
      EventLoopManagerSingleton::get()->getSensorRequestManager().getSensor(
          sensorHandle);
  CHRE_ASSERT(sensor != nullptr);

  if (sensor == nullptr) {
    releaseBiasData(biasData);
  } else {
    uint16_t eventType;
    if (!sensor->reportsBiasEvents() || !sensor->getBiasEventType(&eventType)) {
      LOGE("Received bias event for unsupported sensor type %s",
           sensor->getSensorName());
    } else {
      auto freeCallback = [](uint16_t /* type */, void *data) {
        EventLoopManagerSingleton::get()
            ->getSensorRequestManager()
            .releaseBiasData(data);
      };

      EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
          eventType, biasData, freeCallback, kBroadcastInstanceId,
          sensor->getTargetGroupMask());
    }
  }
}

void SensorRequestManager::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print("\nSensors:\n");
  for (uint8_t i = 0; i < mSensors.size(); i++) {
    for (const auto &request : mSensors[i].getRequests()) {
      // TODO: Rearrange these prints to be similar to sensor request logs
      // below
      debugDump.print(
          " %s: mode=%d int=%" PRIu64 " lat=%" PRIu64 " nappId=%" PRIu16 "\n",
          mSensors[i].getSensorTypeName(), static_cast<int>(request.getMode()),
          request.getInterval().toRawNanoseconds(),
          request.getLatency().toRawNanoseconds(), request.getInstanceId());
    }
  }
  debugDump.print("\n Last %zu Sensor Requests:\n", mSensorRequestLogs.size());
  static_assert(kMaxSensorRequestLogs <= INT8_MAX,
                "kMaxSensorRequestLogs must be <= INT8_MAX");
  for (int8_t i = static_cast<int8_t>(mSensorRequestLogs.size()) - 1; i >= 0;
       i--) {
    const auto &log = mSensorRequestLogs[static_cast<size_t>(i)];
    const Sensor &sensor = mSensors[log.sensorHandle];
    debugDump.print("  ts=%" PRIu64 " nappId=%" PRIu16 " type=%s idx=%" PRIu8
                    " mask=%" PRIx16 " mode=%s",
                    log.timestamp.toRawNanoseconds(), log.instanceId,
                    sensor.getSensorTypeName(), sensor.getSensorIndex(),
                    sensor.getTargetGroupMask(), getSensorModeName(log.mode));

    if (sensorModeIsContinuous(log.mode)) {
      debugDump.print(" int=%" PRIu64 " lat=%" PRIu64,
                      log.interval.toRawNanoseconds(),
                      log.latency.toRawNanoseconds());
    }
    debugDump.print("\n");
  }
}

uint32_t SensorRequestManager::disableAllSubscriptions(Nanoapp *nanoapp) {
  uint32_t numDisabledSubscriptions = 0;

  const uint32_t numSensors = static_cast<uint32_t>(mSensors.size());
  for (uint32_t handle = 0; handle < numSensors; handle++) {
    Sensor &sensor = mSensors[handle];
    bool nanoappHasRequest =
        sensor.getRequestMultiplexer().findRequest(
            nanoapp->getInstanceId(), nullptr /*index*/) != nullptr;
    if (nanoappHasRequest) {
      numDisabledSubscriptions++;
      SensorRequest request(SensorMode::Off, Nanoseconds() /*interval*/,
                            Nanoseconds() /*latency*/);
      setSensorRequest(nanoapp, handle, request);
    }
  }

  return numDisabledSubscriptions;
}

void SensorRequestManager::postFlushCompleteEvent(uint32_t sensorHandle,
                                                  uint8_t errorCode,
                                                  const FlushRequest &request) {
  auto *event = memoryAlloc<chreSensorFlushCompleteEvent>();
  if (event == nullptr) {
    LOG_OOM();
  } else {
    event->sensorHandle = sensorHandle;
    event->errorCode = errorCode;
    event->cookie = request.cookie;
    memset(event->reserved, 0, sizeof(event->reserved));

    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_SENSOR_FLUSH_COMPLETE, event, freeEventDataCallback,
        request.nanoappInstanceId);
  }
}

void SensorRequestManager::completeFlushRequestAtIndex(size_t index,
                                                       uint8_t errorCode) {
  if (index < mFlushRequestQueue.size()) {
    const FlushRequest &request = mFlushRequestQueue[index];
    uint32_t sensorHandle = request.sensorHandle;
    if (request.isActive) {
      mSensors[sensorHandle].clearPendingFlushRequest();
    }

    postFlushCompleteEvent(sensorHandle, errorCode, request);
    mFlushRequestQueue.erase(index);
  }
}

void SensorRequestManager::dispatchNextFlushRequest(uint32_t sensorHandle) {
  for (size_t i = 0; i < mFlushRequestQueue.size(); i++) {
    FlushRequest &request = mFlushRequestQueue[i];
    if (request.sensorHandle == sensorHandle) {
      uint8_t newRequestErrorCode = makeFlushRequest(request);
      if (newRequestErrorCode == CHRE_ERROR_NONE) {
        break;
      } else {
        completeFlushRequestAtIndex(i, newRequestErrorCode);
        i--;
      }
    }
  }
}

void SensorRequestManager::onFlushTimeout(uint32_t sensorHandle) {
  if (sensorHandle < mSensors.size()) {
    Sensor &sensor = mSensors[sensorHandle];
    sensor.setFlushRequestTimerHandle(CHRE_TIMER_INVALID);
  }
}

void SensorRequestManager::handleFlushCompleteEventSync(uint8_t errorCode,
                                                        uint32_t sensorHandle) {
  for (size_t i = 0; i < mFlushRequestQueue.size(); i++) {
    if (mFlushRequestQueue[i].sensorHandle == sensorHandle) {
      completeFlushRequestAtIndex(i, errorCode);
      dispatchNextFlushRequest(sensorHandle);
      break;
    }
  }
}

void SensorRequestManager::cancelFlushRequests(uint32_t sensorHandle,
                                               uint32_t nanoappInstanceId) {
  bool removeAll = (nanoappInstanceId == kSystemInstanceId);
  for (size_t i = 0; i < mFlushRequestQueue.size(); i++) {
    const FlushRequest &request = mFlushRequestQueue[i];
    if (request.sensorHandle == sensorHandle &&
        (request.nanoappInstanceId == nanoappInstanceId || removeAll)) {
      completeFlushRequestAtIndex(i,
                                  CHRE_ERROR_FUNCTION_DISABLED /* errorCode */);
      i--;
    }
  }

  if (!mSensors[sensorHandle].isFlushRequestPending()) {
    dispatchNextFlushRequest(sensorHandle);
  }
}

void SensorRequestManager::addSensorRequestLog(
    uint16_t nanoappInstanceId, uint32_t sensorHandle,
    const SensorRequest &sensorRequest) {
  mSensorRequestLogs.kick_push(SensorRequestLog(
      SystemTime::getMonotonicTime(), nanoappInstanceId, sensorHandle,
      sensorRequest.getMode(), sensorRequest.getInterval(),
      sensorRequest.getLatency()));
}

bool SensorRequestManager::addRequest(Sensor &sensor,
                                      const SensorRequest &request,
                                      bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  size_t addIndex;
  bool success = true;
  SensorRequestMultiplexer &multiplexer = sensor.getRequestMultiplexer();
  SensorRequest prevRequest = sensor.getMaximalRequest();
  if (!multiplexer.addRequest(request, &addIndex, requestChanged)) {
    *requestChanged = false;
    success = false;
    LOG_OOM();
  } else if (*requestChanged) {
    success = configurePlatformSensor(sensor, prevRequest);
    if (!success) {
      // Remove the newly added request since the platform failed to handle
      // it. The sensor is expected to maintain the existing request so there is
      // no need to reset the platform to the last maximal request.
      multiplexer.removeRequest(addIndex, requestChanged);

      // This is a roll-back operation so the maximal change in the
      // multiplexer must not have changed. The request changed state is forced
      // to false.
      *requestChanged = false;
    }
  }

  return success;
}

bool SensorRequestManager::updateRequest(Sensor &sensor, size_t updateIndex,
                                         const SensorRequest &request,
                                         bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  bool success = true;
  SensorRequestMultiplexer &multiplexer = sensor.getRequestMultiplexer();
  SensorRequest previousRequest = multiplexer.getRequests()[updateIndex];
  SensorRequest prevMaxRequest = sensor.getMaximalRequest();

  multiplexer.updateRequest(updateIndex, request, requestChanged);
  if (*requestChanged) {
    success = configurePlatformSensor(sensor, prevMaxRequest);
    if (!success) {
      // Roll back the request since sending it to the sensor failed. The
      // request will roll back to the previous maximal. The sensor is
      // expected to maintain the existing request if a request fails so there
      // is no need to reset the platform to the last maximal request.
      multiplexer.updateRequest(updateIndex, previousRequest, requestChanged);

      // This is a roll-back operation so the maximal change in the multiplexer
      // must not have changed. The request changed state is forced to false.
      *requestChanged = false;
    }
  }

  return success;
}

bool SensorRequestManager::removeRequest(Sensor &sensor, size_t removeIndex,
                                         bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  bool success = true;
  const SensorRequest prevRequest = sensor.getMaximalRequest();
  sensor.getRequestMultiplexer().removeRequest(removeIndex, requestChanged);
  if (*requestChanged) {
    success = configurePlatformSensor(sensor, prevRequest);
    if (!success) {
      LOGE("SensorRequestManager failed to remove a request");

      // If the platform fails to handle this request in a debug build there is
      // likely an error in the platform. This is not strictly a programming
      // error but it does make sense to use assert semantics when a platform
      // fails to handle a request that it had been sent previously.
      CHRE_ASSERT(false);

      // The request to the platform to set a request when removing has failed
      // so the request has not changed.
      *requestChanged = false;
    }
  }
  return success;
}

bool SensorRequestManager::removeAllRequests(Sensor &sensor) {
  bool requestChanged;
  SensorRequest prevRequest = sensor.getMaximalRequest();
  sensor.getRequestMultiplexer().removeAllRequests(&requestChanged);

  bool success = true;
  if (requestChanged) {
    success = configurePlatformSensor(sensor, prevRequest);

    if (!success) {
      LOGE("SensorRequestManager failed to remove all requests");

      // If the platform fails to handle this request in a debug build there
      // is likely an error in the platform. This is not strictly a programming
      // error but it does make sense to use assert semantics when a platform
      // fails to handle a request that it had been sent previously.
      CHRE_ASSERT(false);
    }
  }

  return success;
}

uint8_t SensorRequestManager::makeFlushRequest(FlushRequest &request) {
  uint8_t errorCode = CHRE_ERROR;
  Sensor &sensor = mSensors[request.sensorHandle];
  if (!sensor.isSensorEnabled()) {
    LOGE("Cannot flush on disabled sensor");
  } else if (!sensor.isFlushRequestPending()) {
    Nanoseconds now = SystemTime::getMonotonicTime();
    Nanoseconds deadline = request.deadlineTimestamp;
    if (now >= deadline) {
      LOGE("Flush sensor %s failed for nanoapp ID %" PRIu16
           ": deadline exceeded",
           sensor.getSensorName(), request.nanoappInstanceId);
      errorCode = CHRE_ERROR_TIMEOUT;
    } else if (doMakeFlushRequest(sensor)) {
      errorCode = CHRE_ERROR_NONE;
      Nanoseconds delay = deadline - now;
      request.isActive = true;

      auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
        LOGE("Flush request timed out");
        NestedDataPtr<uint32_t> sensorHandle(data);
        EventLoopManagerSingleton::get()
            ->getSensorRequestManager()
            .onFlushTimeout(sensorHandle);

        // Send a complete event, thus closing out this flush request. If the
        // request that has just timed out receives a response later, this may
        // inadvertently close out a new request before it has actually
        // completed.
        // TODO: Attach an ID to all flush requests / responses so stale
        // responses can be properly dropped.
        EventLoopManagerSingleton::get()
            ->getSensorRequestManager()
            .handleFlushCompleteEventSync(CHRE_ERROR_TIMEOUT, sensorHandle);
      };

      sensor.setFlushRequestTimerHandle(
          EventLoopManagerSingleton::get()->setDelayedCallback(
              SystemCallbackType::SensorFlushTimeout,
              NestedDataPtr<uint32_t>(request.sensorHandle), callback, delay));
    }
  } else {
    // Flush request will be made once the pending request is completed.
    // Return true so that the nanoapp can wait for a result through the
    // CHRE_EVENT_SENSOR_FLUSH_COMPLETE event.
    errorCode = CHRE_ERROR_NONE;
  }

  return errorCode;
}

bool SensorRequestManager::doMakeFlushRequest(Sensor &sensor) {
  // Set to true before making the request since the request may be a
  // synchronous request and we may get the complete event before it returns.
  sensor.setFlushRequestPending(true);
  // TODO: Refactor code to take the request ID into account so multiple flush
  // requests can be issued.
  uint32_t flushRequestId;
  bool success = mPlatformSensorManager.flush(sensor, &flushRequestId);
  sensor.setFlushRequestPending(success);
  return success;
}

bool SensorRequestManager::configurePlatformSensor(
    Sensor &sensor, const SensorRequest &prevSensorRequest) {
  bool success = false;
  const SensorRequest &request = sensor.getMaximalRequest();

  // Ensures that only configureBiasEvents is invoked if that's the only value
  // that has changed since the previous request since CHRE shouldn't configure
  // the platform for data events if the sensor data request hasn't changed.
  bool biasChanged = (request.getBiasUpdatesRequested() !=
                      prevSensorRequest.getBiasUpdatesRequested());
  bool onlyBiasChanged = request.onlyBiasRequestUpdated(prevSensorRequest);
  uint64_t currentLatency = 0;
  bool enable = (request.getMode() != SensorMode::Off);
  if (enable) {
    currentLatency = request.getLatency().toRawNanoseconds();
  }

  // Per platform API requirements, an active sensor subscription must exist
  // before any bias configuration can be done.
  if (!onlyBiasChanged &&
      !mPlatformSensorManager.configureSensor(sensor, request)) {
    LOGE("Failed to make platform sensor data request");
  } else if (biasChanged &&
             !mPlatformSensorManager.configureBiasEvents(
                 sensor, request.getBiasUpdatesRequested(), currentLatency)) {
    LOGE("Failed to make platform sensor bias request");
    if (!onlyBiasChanged) {
      mPlatformSensorManager.configureSensor(sensor, prevSensorRequest);
    }
  } else {
    success = true;

    // Reset last event if an on-change sensor is turned off.
    if (request.getMode() == SensorMode::Off) {
      sensor.clearLastEvent();
    }
  }
  return success;
}

uint16_t SensorRequestManager::getActiveTargetGroupMask(
    uint16_t nanoappInstanceId, uint8_t sensorType) {
  uint16_t mask = 0;
  for (Sensor &sensor : mSensors) {
    if (sensor.getSensorType() == sensorType) {
      size_t index;
      if (sensor.getRequestMultiplexer().findRequest(nanoappInstanceId,
                                                     &index) != nullptr) {
        mask |= sensor.getTargetGroupMask();
        break;
      }
    }
  }

  return mask;
}

}  // namespace chre
