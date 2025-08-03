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

#ifndef CHRE_CORE_EVENT_LOOP_COMMON_H_
#define CHRE_CORE_EVENT_LOOP_COMMON_H_

#include "chre_api/chre/event.h"

#include <cstdint>

namespace chre {

//! An identifier for a system callback, which is mapped into a CHRE event type
//! in the user-defined range.
enum class SystemCallbackType : uint16_t {
  FirstCallbackType = CHRE_EVENT_FIRST_USER_VALUE,

  MessageToHostComplete,
  WifiScanMonitorStateChange,
  WifiRequestScanResponse,
  WifiHandleScanEvent,
  NanoappListResponse,
  SensorLastEventUpdate,
  FinishLoadingNanoapp,
  WwanHandleCellInfoResult,
  HandleUnloadNanoapp,
  GnssSessionStatusChange,
  SensorStatusUpdate,
  PerformDebugDump,
  TimerPoolTick,
  AudioHandleDataEvent,
  WifiHandleRangingEvent,
  AudioAvailabilityChange,
  AudioHandleHostAwake,
  SensorFlushComplete,
  SensorFlushTimeout,
  SensorStatusInfoResponse,
  DeferredMessageToNanoappFromHost,
  SettingChangeEvent,
  GnssLocationReportEvent,
  GnssMeasurementReportEvent,
  Shutdown,
  TimerSyncRequest,
  DelayedFatalError,
  GnssRequestResyncEvent,
  SendBufferedLogMessage,
  HostEndpointConnected,
  HostEndpointDisconnected,
  WifiNanServiceIdEvent,
  WifiNanServiceDiscoveryEvent,
  WifiNanServiceSessionLostEvent,
  WifiNanServiceTerminatedEvent,
  WifiNanAvailabilityEvent,
  DeferredMetricPostEvent,
  BleAdvertisementEvent,
  BleScanResponse,
  BleRequestResyncEvent,
  RequestTimeoutEvent,
  BleReadRssiEvent,
  BleFlushComplete,
  BleFlushTimeout,
  PulseResponse,
};

//! Deferred/delayed callbacks use the event subsystem but are invariably sent
//! by the system and received by the system, so they are able to make use of an
//! extra parameter
//! @see Event
using SystemEventCallbackFunction = void(uint16_t type, void *data,
                                         void *extraData);

}  // namespace chre

#endif  // CHRE_CORE_EVENT_LOOP_COMMON_H_
