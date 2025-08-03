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

#include "chre_cross_validator_sensor_manager.h"

#include <algorithm>
#include <cinttypes>

#include <pb_decode.h>
#include <pb_encode.h>

#include "chre/util/macros.h"
#include "chre/util/nanoapp/callbacks.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/optional.h"
#include "chre/util/time.h"
#include "chre_api/chre.h"
#include "chre_cross_validation_sensor.nanopb.h"
#include "send_message.h"

#define LOG_TAG "[ChreCrossValidator]"

namespace chre {

namespace cross_validator_sensor {

namespace {

bool decodeSensorName(pb_istream_t *stream, const pb_field_s *field,
                      void **arg) {
  UNUSED_VAR(field);

  unsigned char *name = static_cast<unsigned char *>(*arg);

  if (stream->bytes_left > kMaxSensorNameSize - 1) return false;

  size_t bytesToCopy = stream->bytes_left;
  if (!pb_read(stream, name, stream->bytes_left)) return false;
  name[bytesToCopy] = '\0';

  return true;
}

}  // namespace

Manager::~Manager() {
  cleanup();
}

void Manager::cleanup() {
  if (mCrossValidatorState.has_value()) {
    switch (mCrossValidatorState->crossValidatorType) {
      case CrossValidatorType::SENSOR:
        if (!chreSensorConfigureModeOnly(mCrossValidatorState->sensorHandle,
                                         CHRE_SENSOR_CONFIGURE_MODE_DONE)) {
          LOGE(
              "Sensor cleanup failed when trying to configure sensor with "
              "handle "
              "%" PRIu32 " to done mode",
              mCrossValidatorState->sensorHandle);
        }
        break;
      default:
        break;
    }
  }
}

void Manager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                          const void *eventData) {
  switch (eventType) {
    case CHRE_EVENT_MESSAGE_FROM_HOST:
      handleMessageFromHost(
          senderInstanceId,
          static_cast<const chreMessageFromHostData *>(eventData));
      break;
    // TODO(b/146052784): Check that data received from CHRE apis is the correct
    // type for current test.
    case CHRE_EVENT_SENSOR_ACCELEROMETER_DATA:
      handleSensorThreeAxisData(
          static_cast<const chreSensorThreeAxisData *>(eventData),
          CHRE_SENSOR_TYPE_ACCELEROMETER);
      break;
    case CHRE_EVENT_SENSOR_GYROSCOPE_DATA:
      handleSensorThreeAxisData(
          static_cast<const chreSensorThreeAxisData *>(eventData),
          CHRE_SENSOR_TYPE_GYROSCOPE);
      break;
    case CHRE_EVENT_SENSOR_GEOMAGNETIC_FIELD_DATA:
      handleSensorThreeAxisData(
          static_cast<const chreSensorThreeAxisData *>(eventData),
          CHRE_SENSOR_TYPE_GEOMAGNETIC_FIELD);
      break;
    case CHRE_EVENT_SENSOR_PRESSURE_DATA:
      handleSensorFloatData(static_cast<const chreSensorFloatData *>(eventData),
                            CHRE_SENSOR_TYPE_PRESSURE);
      break;
    case CHRE_EVENT_SENSOR_LIGHT_DATA:
      handleSensorFloatData(static_cast<const chreSensorFloatData *>(eventData),
                            CHRE_SENSOR_TYPE_LIGHT);
      break;
    case CHRE_EVENT_SENSOR_PROXIMITY_DATA:
      handleProximityData(static_cast<const chreSensorByteData *>(eventData));
      break;
    case CHRE_EVENT_SENSOR_STEP_COUNTER_DATA:
      handleStepCounterData(
          static_cast<const chreSensorUint64Data *>(eventData));
      break;
    case CHRE_EVENT_SENSOR_SAMPLING_CHANGE:
      // Ignore sampling state changes
      break;
    default:
      LOGE("Got unknown event type from senderInstanceId %" PRIu32
           " and with eventType %" PRIu16,
           senderInstanceId, eventType);
  }
}

bool Manager::encodeThreeAxisSensorDatapointValues(pb_ostream_t *stream,
                                                   const pb_field_t * /*field*/,
                                                   void *const *arg) {
  const auto *sensorThreeAxisDataSample = static_cast<
      const chreSensorThreeAxisData::chreSensorThreeAxisSampleData *>(*arg);

  for (size_t i = 0; i < 3; i++) {
    if (!pb_encode_tag_for_field(
            stream,
            &chre_cross_validation_sensor_SensorDatapoint_fields
                [chre_cross_validation_sensor_SensorDatapoint_values_tag -
                 1])) {
      return false;
    }
    if (!pb_encode_fixed32(stream, &sensorThreeAxisDataSample->values[i])) {
      return false;
    }
  }
  return true;
}

chre_cross_validation_sensor_SensorDatapoint Manager::makeDatapoint(
    bool (*encodeFunc)(pb_ostream_t *, const pb_field_t *, void *const *),
    const void *sampleDataFromChre, uint64_t currentTimestamp) {
  return chre_cross_validation_sensor_SensorDatapoint{
      .has_timestampInNs = true,
      .timestampInNs = currentTimestamp,
      .values = {.funcs = {.encode = encodeFunc},
                 .arg = const_cast<void *>(sampleDataFromChre)}};
}

bool Manager::encodeFloatSensorDatapointValue(pb_ostream_t *stream,
                                              const pb_field_t * /*field*/,
                                              void *const *arg) {
  const auto *sensorFloatDataSample =
      static_cast<const chreSensorFloatData::chreSensorFloatSampleData *>(*arg);
  if (!pb_encode_tag_for_field(
          stream,
          &chre_cross_validation_sensor_SensorDatapoint_fields
              [chre_cross_validation_sensor_SensorDatapoint_values_tag - 1])) {
    return false;
  }
  if (!pb_encode_fixed32(stream, &sensorFloatDataSample->value)) {
    return false;
  }
  return true;
}

bool Manager::encodeProximitySensorDatapointValue(pb_ostream_t *stream,
                                                  const pb_field_t * /*field*/,
                                                  void *const *arg) {
  const auto *sensorFloatDataSample =
      static_cast<const chreSensorByteData::chreSensorByteSampleData *>(*arg);
  if (!pb_encode_tag_for_field(
          stream,
          &chre_cross_validation_sensor_SensorDatapoint_fields
              [chre_cross_validation_sensor_SensorDatapoint_values_tag - 1])) {
    return false;
  }
  float isNearFloat = sensorFloatDataSample->isNear ? 0.0 : 1.0;
  if (!pb_encode_fixed32(stream, &isNearFloat)) {
    return false;
  }
  return true;
}

bool Manager::encodeStepCounterSensorDatapointValue(pb_ostream_t *stream,
                                                    const pb_field_t *field,
                                                    void *const *arg) {
  UNUSED_VAR(field);

  const auto *sensorUint64DataSample =
      static_cast<const chreSensorUint64Data::chreSensorUint64SampleData *>(
          *arg);
  if (!pb_encode_tag_for_field(
          stream,
          &chre_cross_validation_sensor_SensorDatapoint_fields
              [chre_cross_validation_sensor_SensorDatapoint_values_tag - 1])) {
    return false;
  }
  // This value is casted to a float for the Java sensors framework so do it
  // here to make it easier to encode into the existing proto message.
  float stepValue = float(sensorUint64DataSample->value);
  if (!pb_encode_fixed32(stream, &stepValue)) {
    return false;
  }
  return true;
}

bool Manager::encodeThreeAxisSensorDatapoints(pb_ostream_t *stream,
                                              const pb_field_t * /*field*/,
                                              void *const *arg) {
  const auto *sensorThreeAxisData =
      static_cast<const chreSensorThreeAxisData *>(*arg);
  uint64_t currentTimestamp = sensorThreeAxisData->header.baseTimestamp +
                              chreGetEstimatedHostTimeOffset();
  for (size_t i = 0; i < sensorThreeAxisData->header.readingCount; i++) {
    const chreSensorThreeAxisData::chreSensorThreeAxisSampleData &sampleData =
        sensorThreeAxisData->readings[i];
    currentTimestamp += sampleData.timestampDelta;
    if (!pb_encode_tag_for_field(
            stream,
            &chre_cross_validation_sensor_SensorData_fields
                [chre_cross_validation_sensor_SensorData_datapoints_tag - 1])) {
      return false;
    }
    chre_cross_validation_sensor_SensorDatapoint datapoint = makeDatapoint(
        encodeThreeAxisSensorDatapointValues, &sampleData, currentTimestamp);
    if (!pb_encode_submessage(
            stream, chre_cross_validation_sensor_SensorDatapoint_fields,
            &datapoint)) {
      return false;
    }
  }
  return true;
}

bool Manager::encodeFloatSensorDatapoints(pb_ostream_t *stream,
                                          const pb_field_t * /*field*/,
                                          void *const *arg) {
  const auto *sensorFloatData = static_cast<const chreSensorFloatData *>(*arg);
  uint64_t currentTimestamp =
      sensorFloatData->header.baseTimestamp + chreGetEstimatedHostTimeOffset();
  for (size_t i = 0; i < sensorFloatData->header.readingCount; i++) {
    const chreSensorFloatData::chreSensorFloatSampleData &sampleData =
        sensorFloatData->readings[i];
    currentTimestamp += sampleData.timestampDelta;
    if (!pb_encode_tag_for_field(
            stream,
            &chre_cross_validation_sensor_SensorData_fields
                [chre_cross_validation_sensor_SensorData_datapoints_tag - 1])) {
      return false;
    }
    chre_cross_validation_sensor_SensorDatapoint datapoint = makeDatapoint(
        encodeFloatSensorDatapointValue, &sampleData, currentTimestamp);
    if (!pb_encode_submessage(
            stream, chre_cross_validation_sensor_SensorDatapoint_fields,
            &datapoint)) {
      return false;
    }
  }
  return true;
}

bool Manager::encodeProximitySensorDatapoints(pb_ostream_t *stream,
                                              const pb_field_t * /*field*/,
                                              void *const *arg) {
  const auto *sensorProximityData =
      static_cast<const chreSensorByteData *>(*arg);
  uint64_t currentTimestamp = sensorProximityData->header.baseTimestamp +
                              chreGetEstimatedHostTimeOffset();
  for (size_t i = 0; i < sensorProximityData->header.readingCount; i++) {
    const chreSensorByteData::chreSensorByteSampleData &sampleData =
        sensorProximityData->readings[i];
    currentTimestamp += sampleData.timestampDelta;
    if (!pb_encode_tag_for_field(
            stream,
            &chre_cross_validation_sensor_SensorData_fields
                [chre_cross_validation_sensor_SensorData_datapoints_tag - 1])) {
      return false;
    }
    chre_cross_validation_sensor_SensorDatapoint datapoint = makeDatapoint(
        encodeProximitySensorDatapointValue, &sampleData, currentTimestamp);
    if (!pb_encode_submessage(
            stream, chre_cross_validation_sensor_SensorDatapoint_fields,
            &datapoint)) {
      return false;
    }
  }
  return true;
}

bool Manager::encodeStepCounterSensorDatapoints(pb_ostream_t *stream,
                                                const pb_field_t *field,
                                                void *const *arg) {
  UNUSED_VAR(field);

  const auto *sensorStepCounterData =
      static_cast<const chreSensorUint64Data *>(*arg);
  uint64_t currentTimestamp = sensorStepCounterData->header.baseTimestamp +
                              chreGetEstimatedHostTimeOffset();
  for (size_t i = 0; i < sensorStepCounterData->header.readingCount; i++) {
    const chreSensorUint64Data::chreSensorUint64SampleData &sampleData =
        sensorStepCounterData->readings[i];
    currentTimestamp += sampleData.timestampDelta;
    if (!pb_encode_tag_for_field(
            stream,
            &chre_cross_validation_sensor_SensorData_fields
                [chre_cross_validation_sensor_SensorData_datapoints_tag - 1])) {
      return false;
    }
    chre_cross_validation_sensor_SensorDatapoint datapoint = makeDatapoint(
        encodeStepCounterSensorDatapointValue, &sampleData, currentTimestamp);
    if (!pb_encode_submessage(
            stream, chre_cross_validation_sensor_SensorDatapoint_fields,
            &datapoint)) {
      return false;
    }
  }
  return true;
}

bool Manager::handleStartSensorMessage(
    const chre_cross_validation_sensor_StartSensorCommand &startSensorCommand) {
  bool success = false;
  uint8_t sensorType = startSensorCommand.chreSensorType;
  uint64_t intervalFromApInNs =
      startSensorCommand.intervalInMs * kOneMillisecondInNanoseconds;
  uint64_t latencyInNs =
      startSensorCommand.latencyInMs * kOneMillisecondInNanoseconds;
  bool isContinuous = startSensorCommand.isContinuous;
  uint32_t sensorIndex = startSensorCommand.sensorIndex;

  uint32_t handle;
  if (!getSensor(sensorType, sensorIndex, &handle)) {
    LOGE("Could not find default sensor for sensorType %" PRIu8
         " index %" PRIu32,
         sensorType, sensorIndex);
    // TODO(b/146052784): Test other sensor configure modes
  } else {
    LOGI("Starting x-validation for sensor type %" PRIu8 " index %" PRIu32,
         sensorType, sensorIndex);
    chreSensorInfo sensorInfo;
    if (!chreGetSensorInfo(handle, &sensorInfo)) {
      LOGE("Error getting sensor info for sensor");
    } else {
      // TODO(b/154271547): Send minInterval to AP and have the AP decide from
      // both CHRE and AP min and max interval.
      uint64_t intervalInNs =
          std::max(intervalFromApInNs, sensorInfo.minInterval);
      // Copy hostEndpoint param from previous version of cross validator
      // state
      mCrossValidatorState = CrossValidatorState(
          CrossValidatorType::SENSOR, sensorType, handle, chreGetTime(),
          mCrossValidatorState->hostEndpoint, isContinuous);
      if (!chreSensorConfigure(handle, CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS,
                               intervalInNs, latencyInNs)) {
        LOGE("Error configuring sensor with sensorType %" PRIu8
             ", interval %" PRIu64 "ns, and latency %" PRIu64 "ns",
             sensorType, intervalInNs, latencyInNs);
      } else {
        LOGD("Sensor with sensor type %" PRIu8 " configured", sensorType);
        success = true;
      }
    }
  }
  return success;
}

bool Manager::isValidHeader(const chreSensorDataHeader &header) {
  // On-change sensors may send cached values because the data value has not
  // changed since the test started
  bool isTimestampValid =
      !mCrossValidatorState->isContinuous ||
      header.baseTimestamp >= mCrossValidatorState->timeStart;
  return header.readingCount > 0 && isTimestampValid;
}

void Manager::handleStartMessage(uint16_t hostEndpoint,
                                 const chreMessageFromHostData *hostData) {
  bool success = true;
  // Default values for everything but hostEndpoint param
  mCrossValidatorState = CrossValidatorState(CrossValidatorType::SENSOR, 0, 0,
                                             0, hostEndpoint, false);
  pb_istream_t istream = pb_istream_from_buffer(
      static_cast<const pb_byte_t *>(hostData->message), hostData->messageSize);
  chre_cross_validation_sensor_StartCommand startCommand =
      chre_cross_validation_sensor_StartCommand_init_default;
  if (!pb_decode(&istream, chre_cross_validation_sensor_StartCommand_fields,
                 &startCommand)) {
    LOGE("Could not decode start command");
  } else {
    switch (startCommand.which_command) {
      case chre_cross_validation_sensor_StartCommand_startSensorCommand_tag:
        success =
            handleStartSensorMessage(startCommand.command.startSensorCommand);
        break;
      default:
        LOGE("Unknown start command type %" PRIu8, startCommand.which_command);
    }
  }
  // If error occurred in validation setup then resetting mCrossValidatorState
  // will alert the event handler
  if (!success) {
    mCrossValidatorState.reset();
  }
}

void Manager::handleInfoMessage(uint16_t hostEndpoint,
                                const chreMessageFromHostData *hostData) {
  chre_cross_validation_sensor_SensorInfoResponse infoResponse =
      chre_cross_validation_sensor_SensorInfoResponse_init_default;
  pb_istream_t istream = pb_istream_from_buffer(
      static_cast<const pb_byte_t *>(hostData->message), hostData->messageSize);
  chre_cross_validation_sensor_SensorInfoCommand infoCommand =
      chre_cross_validation_sensor_SensorInfoCommand_init_default;

  infoCommand.sensorName.funcs.decode = decodeSensorName;
  infoCommand.sensorName.arg = mSensorNameArray;

  if (!pb_decode(&istream,
                 chre_cross_validation_sensor_SensorInfoCommand_fields,
                 &infoCommand)) {
    LOGE("Could not decode info command");
  } else {
    uint32_t handle;
    infoResponse.has_chreSensorType = true;
    infoResponse.chreSensorType = infoCommand.chreSensorType;
    infoResponse.has_isAvailable = true;
    infoResponse.isAvailable = false;
    infoResponse.has_sensorIndex = false;

    bool supportsMultiSensors =
        chreSensorFind(infoCommand.chreSensorType, 1, &handle);
    for (uint8_t i = 0; chreSensorFind(infoCommand.chreSensorType, i, &handle);
         i++) {
      struct chreSensorInfo info;
      if (!chreGetSensorInfo(handle, &info)) {
        LOGE("Failed to get sensor info");
      } else {
        bool equal = true;
        if (supportsMultiSensors) {
          equal = (strcmp(info.sensorName, mSensorNameArray) == 0);
          LOGI("Got sensor name %s in-name %s, equal %d", info.sensorName,
               mSensorNameArray, equal);
        }
        if (equal) {
          infoResponse.isAvailable = true;
          infoResponse.has_sensorIndex = true;
          infoResponse.sensorIndex = i;
          break;
        }
      }
    }
  }

  sendInfoResponse(hostEndpoint, infoResponse);
}

void Manager::handleMessageFromHost(uint32_t senderInstanceId,
                                    const chreMessageFromHostData *hostData) {
  if (senderInstanceId != CHRE_INSTANCE_ID) {
    LOGE("Incorrect sender instance id: %" PRIu32, senderInstanceId);
  } else {
    uint16_t hostEndpoint;
    if (hostData->hostEndpoint != CHRE_HOST_ENDPOINT_UNSPECIFIED) {
      hostEndpoint = hostData->hostEndpoint;
    } else {
      hostEndpoint = CHRE_HOST_ENDPOINT_BROADCAST;
    }

    switch (hostData->messageType) {
      case chre_cross_validation_sensor_MessageType_CHRE_CROSS_VALIDATION_START:
        handleStartMessage(hostEndpoint, hostData);
        break;
      case chre_cross_validation_sensor_MessageType_CHRE_CROSS_VALIDATION_INFO:
        handleInfoMessage(hostEndpoint, hostData);
        break;
      default:
        LOGE("Unknown message type %" PRIu32 " for host message",
             hostData->messageType);
    }
  }
}

chre_cross_validation_sensor_Data Manager::makeSensorThreeAxisData(
    const chreSensorThreeAxisData *threeAxisDataFromChre, uint8_t sensorType) {
  chre_cross_validation_sensor_SensorData newThreeAxisData = {
      .has_chreSensorType = true,
      .chreSensorType = sensorType,
      .has_accuracy = true,
      .accuracy = threeAxisDataFromChre->header.accuracy,
      .datapoints = {
          .funcs = {.encode = encodeThreeAxisSensorDatapoints},
          .arg = const_cast<chreSensorThreeAxisData *>(threeAxisDataFromChre)}};
  chre_cross_validation_sensor_Data newData = {
      .which_data = chre_cross_validation_sensor_Data_sensorData_tag,
      .data =
          {
              .sensorData = newThreeAxisData,
          },
  };
  return newData;
}

chre_cross_validation_sensor_Data Manager::makeSensorFloatData(
    const chreSensorFloatData *floatDataFromChre, uint8_t sensorType) {
  chre_cross_validation_sensor_SensorData newfloatData = {
      .has_chreSensorType = true,
      .chreSensorType = sensorType,
      .has_accuracy = true,
      .accuracy = floatDataFromChre->header.accuracy,
      .datapoints = {
          .funcs = {.encode = encodeFloatSensorDatapoints},
          .arg = const_cast<chreSensorFloatData *>(floatDataFromChre)}};
  chre_cross_validation_sensor_Data newData = {
      .which_data = chre_cross_validation_sensor_Data_sensorData_tag,
      .data =
          {
              .sensorData = newfloatData,
          },
  };
  return newData;
}

chre_cross_validation_sensor_Data Manager::makeSensorProximityData(
    const chreSensorByteData *proximityDataFromChre) {
  chre_cross_validation_sensor_SensorData newProximityData = {
      .has_chreSensorType = true,
      .chreSensorType = CHRE_SENSOR_TYPE_PROXIMITY,
      .has_accuracy = true,
      .accuracy = proximityDataFromChre->header.accuracy,
      .datapoints = {
          .funcs = {.encode = encodeProximitySensorDatapoints},
          .arg = const_cast<chreSensorByteData *>(proximityDataFromChre)}};
  chre_cross_validation_sensor_Data newData = {
      .which_data = chre_cross_validation_sensor_Data_sensorData_tag,
      .data =
          {
              .sensorData = newProximityData,
          },
  };
  return newData;
}

chre_cross_validation_sensor_Data Manager::makeSensorStepCounterData(
    const chreSensorUint64Data *stepCounterDataFromChre) {
  chre_cross_validation_sensor_SensorData newStepCounterData = {
      .has_chreSensorType = true,
      .chreSensorType = CHRE_SENSOR_TYPE_STEP_COUNTER,
      .has_accuracy = true,
      .accuracy = stepCounterDataFromChre->header.accuracy,
      .datapoints = {
          .funcs = {.encode = encodeStepCounterSensorDatapoints},
          .arg = const_cast<chreSensorUint64Data *>(stepCounterDataFromChre)}};
  chre_cross_validation_sensor_Data newData = {
      .which_data = chre_cross_validation_sensor_Data_sensorData_tag,
      .data =
          {
              .sensorData = newStepCounterData,
          },
  };
  return newData;
}

void Manager::handleSensorThreeAxisData(
    const chreSensorThreeAxisData *threeAxisDataFromChre, uint8_t sensorType) {
  if (processSensorData(threeAxisDataFromChre->header, sensorType)) {
    chre_cross_validation_sensor_Data newData =
        makeSensorThreeAxisData(threeAxisDataFromChre, sensorType);
    sendDataToHost(newData);
  }
}

void Manager::handleSensorFloatData(
    const chreSensorFloatData *floatDataFromChre, uint8_t sensorType) {
  if (processSensorData(floatDataFromChre->header, sensorType)) {
    chre_cross_validation_sensor_Data newData =
        makeSensorFloatData(floatDataFromChre, sensorType);
    sendDataToHost(newData);
  }
}

void Manager::handleProximityData(
    const chreSensorByteData *proximityDataFromChre) {
  if (processSensorData(proximityDataFromChre->header,
                        CHRE_SENSOR_TYPE_PROXIMITY)) {
    chre_cross_validation_sensor_Data newData =
        makeSensorProximityData(proximityDataFromChre);
    sendDataToHost(newData);
  }
}

void Manager::handleStepCounterData(
    const chreSensorUint64Data *stepCounterDataFromChre) {
  if (processSensorData(stepCounterDataFromChre->header,
                        CHRE_SENSOR_TYPE_STEP_COUNTER)) {
    chre_cross_validation_sensor_Data newData =
        makeSensorStepCounterData(stepCounterDataFromChre);
    sendDataToHost(newData);
  }
}

void Manager::sendDataToHost(const chre_cross_validation_sensor_Data &data) {
  test_shared::sendMessageToHost(
      mCrossValidatorState->hostEndpoint, &data,
      chre_cross_validation_sensor_Data_fields,
      chre_cross_validation_sensor_MessageType_CHRE_CROSS_VALIDATION_DATA);
}

void Manager::sendInfoResponse(
    uint16_t hostEndpoint,
    const chre_cross_validation_sensor_SensorInfoResponse &infoResponse) {
  test_shared::sendMessageToHost(
      hostEndpoint, &infoResponse,
      chre_cross_validation_sensor_SensorInfoResponse_fields,
      chre_cross_validation_sensor_MessageType_CHRE_CROSS_VALIDATION_INFO_RESPONSE);
}

bool Manager::processSensorData(const chreSensorDataHeader &header,
                                uint8_t sensorType) {
  if (!mCrossValidatorState.has_value()) {
    LOGE("Start message not received or invalid when data received");
  } else if (!isValidHeader(header)) {
    LOGE("Invalid data being thrown away");
  } else if (!sensorTypeIsValid(sensorType)) {
    LOGE("Unexpected sensor data type %" PRIu8 ", expected %" PRIu8, sensorType,
         mCrossValidatorState->sensorType);
  } else {
    return true;
  }
  return false;
}

bool Manager::sensorTypeIsValid(uint8_t sensorType) {
  return sensorType == mCrossValidatorState->sensorType;
}

bool Manager::getSensor(uint32_t sensorType, uint32_t sensorIndex,
                        uint32_t *handle) {
  bool success = false;

  bool supportsMultiSensor = (chreGetApiVersion() >= CHRE_API_VERSION_1_5);
  if (sensorIndex > UINT8_MAX) {
    LOGE("CHRE only supports max of 255 sensor indices");
  } else if (!supportsMultiSensor && sensorIndex != 0) {
    LOGW("CHRE API does not support multi-sensors");
  } else {
    success = chreSensorFind(sensorType, sensorIndex, handle);
  }

  return success;
}

}  // namespace cross_validator_sensor

}  // namespace chre
