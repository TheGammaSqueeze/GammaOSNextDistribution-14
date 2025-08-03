/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <cutils/sockets.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <utils/StrongPointer.h>

#include <chrono>
#include <cinttypes>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_header_utils.h"
#include "chre/version.h"
#include "chre_host/file_stream.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/log.h"
#include "chre_host/napp_header.h"
#include "chre_host/socket_client.h"
#include "generated/chre_power_test_generated.h"

/**
 * @file
 * A test utility that connects to the CHRE daemon and provides commands to take
 * control the power test nanoapp located at system/chre/apps/power_test
 *
 * Usage:
 *  chre_power_test_client load <optional: tcm> <optional: path>
 *  chre_power_test_client unload <optional: tcm>
 *  chre_power_test_client unloadall
 *  chre_power_test_client timer <optional: tcm> <enable> <interval_ns>
 *  chre_power_test_client wifi <optional: tcm> <enable> <interval_ns>
 *                              <optional: wifi_scan_type>
 *                              <optional: wifi_radio_chain>
 *                              <optional: wifi_channel_set>
 *  chre_power_test_client gnss <optional: tcm> <enable> <interval_ms>
 *                              <optional: next_fix_ms>
 *  chre_power_test_client cell <optional: tcm> <enable> <interval_ns>
 *  chre_power_test_client audio <optional: tcm> <enable> <duration_ns>
 *  chre_power_test_client sensor <optional: tcm> <enable> <sensor_type>
 *                                <interval_ns> <optional: latency_ns>
 *  chre_power_test_client breakit <optional: tcm> <enable>
 *  chre_power_test_client gnss_meas <optional: tcm> <enable> <interval_ms>
 *  chre_power_test_client wifi_nan_sub <optional: tcm> <sub_type>
 *                                <service_name>
 *  chre_power_test_client end_wifi_nan_sub <optional: tcm> <subscription_id>
 *
 * Command:
 *  load: load power test nanoapp to CHRE
 *  unload: unload power test nanoapp from CHRE
 *  unloadall: unload all nanoapps in CHRE
 *  timer: start/stop timer wake up
 *  wifi: start/stop periodic wifi scan
 *  gnss: start/stop periodic GPS scan
 *  cell: start/stop periodic cellular scan
 *  audio: start/stop periodic audio capture
 *  sensor: start/stop periodic sensor sampling
 *  breakit: start/stop all action for stress tests
 *  gnss_meas: start/stop periodic GNSS measurement
 *
 * <optional: tcm>: tcm for micro image, default for big image
 * <enable>: enable/disable
 *
 * <sensor_type>:
 *  accelerometer
 *  instant_motion
 *  stationary
 *  gyroscope
 *  uncalibrated_gyroscope
 *  geomagnetic
 *  uncalibrated_geomagnetic
 *  pressure
 *  light
 *  proximity
 *  step
 *  step_counter
 *  uncalibrated_accelerometer
 *  accelerometer_temperature
 *  gyroscope_temperature
 *  geomagnetic_temperature
 *
 * For instant_motion and stationary sensor, it is not necessary to provide the
 * interval and latency
 *
 * <wifi_scan_type>:
 *  active
 *  active_passive_dfs
 *  passive
 *  no_preference (default when omitted)
 *
 * <wifi_radio_chain>:
 *  default (default when omitted)
 *  low_latency
 *  low_power
 *  high_accuracy
 *
 * <wifi_channel_set>:
 *  non_dfs (default when omitted)
 *  all
 */

using android::sp;
using android::chre::FragmentedLoadTransaction;
using android::chre::getStringFromByteVector;
using android::chre::HostProtocolHost;
using android::chre::IChreMessageHandlers;
using android::chre::NanoAppBinaryHeader;
using android::chre::readFileContents;
using android::chre::SocketClient;
using chre::power_test::MessageType;
using chre::power_test::SensorType;
using chre::power_test::WifiChannelSet;
using chre::power_test::WifiRadioChain;
using chre::power_test::WifiScanType;
using flatbuffers::FlatBufferBuilder;
using std::string;

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;
namespace ptest = ::chre::power_test;

namespace {

//! The host endpoint we use when sending; Clients may use a value above
//! 0x8000 to enable unicast messaging (currently requires internal coordination
//! to avoid conflict).
constexpr uint16_t kHostEndpoint = 0x8003;

constexpr uint64_t kPowerTestAppId = 0x012345678900000f;
constexpr uint64_t kPowerTestTcmAppId = 0x0123456789000010;
constexpr uint64_t kUint64Max = std::numeric_limits<uint64_t>::max();

constexpr auto kTimeout = std::chrono::seconds(10);

const string kPowerTestName = "power_test.so";
const string kPowerTestTcmName = "power_test_tcm.so";
std::condition_variable kReadyCond;
std::mutex kReadyMutex;
std::unique_lock<std::mutex> kReadyCondLock(kReadyMutex);

enum class Command : uint32_t {
  kUnloadAll = 0,
  kLoad,
  kUnload,
  kTimer,
  kWifi,
  kGnss,
  kCell,
  kAudio,
  kSensor,
  kBreakIt,
  kGnssMeas,
  kNanSub,
  kNanCancel,
};

std::unordered_map<string, Command> commandMap{
    {"unloadall", Command::kUnloadAll},
    {"load", Command::kLoad},
    {"unload", Command::kUnload},
    {"timer", Command::kTimer},
    {"wifi", Command::kWifi},
    {"gnss", Command::kGnss},
    {"cell", Command::kCell},
    {"audio", Command::kAudio},
    {"sensor", Command::kSensor},
    {"breakit", Command::kBreakIt},
    {"gnss_meas", Command::kGnssMeas},
    {"wifi_nan_sub", Command::kNanSub},
    {"end_wifi_nan_sub", Command::kNanCancel}};

std::unordered_map<string, MessageType> messageTypeMap{
    {"timer", MessageType::TIMER_TEST},
    {"wifi", MessageType::WIFI_SCAN_TEST},
    {"gnss", MessageType::GNSS_LOCATION_TEST},
    {"cell", MessageType::CELL_QUERY_TEST},
    {"audio", MessageType::AUDIO_REQUEST_TEST},
    {"sensor", MessageType::SENSOR_REQUEST_TEST},
    {"breakit", MessageType::BREAK_IT_TEST},
    {"gnss_meas", MessageType::GNSS_MEASUREMENT_TEST},
    {"wifi_nan_sub", MessageType::WIFI_NAN_SUB},
    {"end_wifi_nan_sub", MessageType::WIFI_NAN_SUB_CANCEL}};

std::unordered_map<string, SensorType> sensorTypeMap{
    {"accelerometer", SensorType::ACCELEROMETER},
    {"instant_motion", SensorType::INSTANT_MOTION_DETECT},
    {"stationary", SensorType::STATIONARY_DETECT},
    {"gyroscope", SensorType::GYROSCOPE},
    {"uncalibrated_gyroscope", SensorType::UNCALIBRATED_GYROSCOPE},
    {"geomagnetic", SensorType::GEOMAGNETIC_FIELD},
    {"uncalibrated_geomagnetic", SensorType::UNCALIBRATED_GEOMAGNETIC_FIELD},
    {"pressure", SensorType::PRESSURE},
    {"light", SensorType::LIGHT},
    {"proximity", SensorType::PROXIMITY},
    {"step", SensorType::STEP_DETECT},
    {"step_counter", SensorType::STEP_COUNTER},
    {"uncalibrated_accelerometer", SensorType::UNCALIBRATED_ACCELEROMETER},
    {"accelerometer_temperature", SensorType::ACCELEROMETER_TEMPERATURE},
    {"gyroscope_temperature", SensorType::GYROSCOPE_TEMPERATURE},
    {"geomagnetic_temperature", SensorType::GEOMAGNETIC_FIELD_TEMPERATURE}};

std::unordered_map<string, WifiScanType> wifiScanTypeMap{
    {"active", WifiScanType::ACTIVE},
    {"active_passive_dfs", WifiScanType::ACTIVE_PLUS_PASSIVE_DFS},
    {"passive", WifiScanType::PASSIVE},
    {"no_preference", WifiScanType::NO_PREFERENCE}};

std::unordered_map<string, WifiRadioChain> wifiRadioChainMap{
    {"default", WifiRadioChain::DEFAULT},
    {"low_latency", WifiRadioChain::LOW_LATENCY},
    {"low_power", WifiRadioChain::LOW_POWER},
    {"high_accuracy", WifiRadioChain::HIGH_ACCURACY}};

std::unordered_map<string, WifiChannelSet> wifiChannelSetMap{
    {"non_dfs", WifiChannelSet::NON_DFS}, {"all", WifiChannelSet::ALL}};

bool wifiScanTypeMatch(const string &name, WifiScanType *scanType) {
  if (wifiScanTypeMap.find(name) != wifiScanTypeMap.end()) {
    *scanType = wifiScanTypeMap[name];
    return true;
  }
  return false;
}

bool wifiRadioChainMatch(const string &name, WifiRadioChain *radioChain) {
  if (wifiRadioChainMap.find(name) != wifiRadioChainMap.end()) {
    *radioChain = wifiRadioChainMap[name];
    return true;
  }
  return false;
}

bool wifiChannelSetMatch(const string &name, WifiChannelSet *channelSet) {
  if (wifiChannelSetMap.find(name) != wifiChannelSetMap.end()) {
    *channelSet = wifiChannelSetMap[name];
    return true;
  }
  return false;
}

class SocketCallbacks : public SocketClient::ICallbacks,
                        public IChreMessageHandlers {
 public:
  SocketCallbacks(std::condition_variable &readyCond)
      : mConditionVariable(readyCond) {}

  void onMessageReceived(const void *data, size_t length) override {
    if (!HostProtocolHost::decodeMessageFromChre(data, length, *this)) {
      LOGE("Failed to decode message");
    }
  }

  void onConnected() override {
    LOGI("Socket (re)connected");
  }

  void onConnectionAborted() override {
    LOGI("Socket (re)connection aborted");
  }

  void onDisconnected() override {
    LOGI("Socket disconnected");
  }

  void handleNanoappMessage(const fbs::NanoappMessageT &message) override {
    LOGI("Got message from nanoapp 0x%" PRIx64 " to endpoint 0x%" PRIx16
         " with type 0x%" PRIx32 " and length %zu",
         message.app_id, message.host_endpoint, message.message_type,
         message.message.size());
    if (message.message_type ==
        static_cast<uint32_t>(MessageType::NANOAPP_RESPONSE)) {
      handlePowerTestNanoappResponse(message.message);
    }
  }

  void handlePowerTestNanoappResponse(const std::vector<uint8_t> &message) {
    auto response =
        flatbuffers::GetRoot<ptest::NanoappResponseMessage>(message.data());
    flatbuffers::Verifier verifier(message.data(), message.size());
    bool success = response->Verify(verifier);
    mSuccess = success ? response->success() : false;
    mConditionVariable.notify_all();
  }

  void handleNanoappListResponse(
      const fbs::NanoappListResponseT &response) override {
    LOGI("Got nanoapp list response with %zu apps:", response.nanoapps.size());
    mAppIdVector.clear();
    for (const auto &nanoapp : response.nanoapps) {
      LOGI("App ID 0x%016" PRIx64 " version 0x%" PRIx32
           " permissions 0x%" PRIx32 " enabled %d system %d",
           nanoapp->app_id, nanoapp->version, nanoapp->permissions,
           nanoapp->enabled, nanoapp->is_system);
      mAppIdVector.push_back(nanoapp->app_id);
    }
    mConditionVariable.notify_all();
  }

  void handleLoadNanoappResponse(
      const fbs::LoadNanoappResponseT &response) override {
    LOGI("Got load nanoapp response, transaction ID 0x%" PRIx32 " result %d",
         response.transaction_id, response.success);
    mSuccess = response.success;
    mConditionVariable.notify_all();
  }

  void handleUnloadNanoappResponse(
      const fbs::UnloadNanoappResponseT &response) override {
    LOGI("Got unload nanoapp response, transaction ID 0x%" PRIx32 " result %d",
         response.transaction_id, response.success);
    mSuccess = response.success;
    mConditionVariable.notify_all();
  }

  bool actionSucceeded() {
    return mSuccess;
  }

  std::vector<uint64_t> &getAppIdVector() {
    return mAppIdVector;
  }

 private:
  bool mSuccess = false;
  std::condition_variable &mConditionVariable;
  std::vector<uint64_t> mAppIdVector;
};

bool requestNanoappList(SocketClient &client) {
  FlatBufferBuilder builder(64);
  HostProtocolHost::encodeNanoappListRequest(builder);

  LOGI("Sending app list request (%" PRIu32 " bytes)", builder.GetSize());
  if (!client.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
    LOGE("Failed to send message");
    return false;
  }
  return true;
}

bool sendNanoappLoad(SocketClient &client, uint64_t appId, uint32_t appVersion,
                     uint32_t apiVersion, uint32_t appFlags,
                     const std::vector<uint8_t> &binary) {
  // Perform loading with 1 fragment for simplicity
  FlatBufferBuilder builder(binary.size() + 128);
  FragmentedLoadTransaction transaction = FragmentedLoadTransaction(
      1 /* transactionId */, appId, appVersion, appFlags, apiVersion, binary,
      binary.size() /* fragmentSize */);
  HostProtocolHost::encodeFragmentedLoadNanoappRequest(
      builder, transaction.getNextRequest());

  LOGI("Sending load nanoapp request (%" PRIu32
       " bytes total w/%zu bytes of "
       "payload)",
       builder.GetSize(), binary.size());
  return client.sendMessage(builder.GetBufferPointer(), builder.GetSize());
}

bool sendLoadNanoappRequest(SocketClient &client,
                            const std::string filenameNoExtension) {
  bool success = false;
  std::vector<uint8_t> headerBuffer;
  std::vector<uint8_t> binaryBuffer;
  std::string headerName = filenameNoExtension + ".napp_header";
  std::string binaryName = filenameNoExtension + ".so";
  if (readFileContents(headerName.c_str(), headerBuffer) &&
      readFileContents(binaryName.c_str(), binaryBuffer)) {
    if (headerBuffer.size() != sizeof(NanoAppBinaryHeader)) {
      LOGE("Header size mismatch");
    } else {
      // The header blob contains the struct above.
      const auto *appHeader =
          reinterpret_cast<const NanoAppBinaryHeader *>(headerBuffer.data());

      // Build the target API version from major and minor.
      uint32_t targetApiVersion = (appHeader->targetChreApiMajorVersion << 24) |
                                  (appHeader->targetChreApiMinorVersion << 16);

      success =
          sendNanoappLoad(client, appHeader->appId, appHeader->appVersion,
                          targetApiVersion, appHeader->flags, binaryBuffer);
    }
  }
  return success;
}

bool loadNanoapp(SocketClient &client, sp<SocketCallbacks> callbacks,
                 const std::string filenameNoExt) {
  if (!sendLoadNanoappRequest(client, filenameNoExt)) {
    return false;
  }
  auto status = kReadyCond.wait_for(kReadyCondLock, kTimeout);
  return (status == std::cv_status::no_timeout && callbacks->actionSucceeded());
}

bool sendUnloadNanoappRequest(SocketClient &client, uint64_t appId) {
  FlatBufferBuilder builder(64);
  constexpr uint32_t kTransactionId = 4321;
  HostProtocolHost::encodeUnloadNanoappRequest(
      builder, kTransactionId, appId, true /* allowSystemNanoappUnload */);

  LOGI("Sending unload request for nanoapp 0x%016" PRIx64 " (size %" PRIu32 ")",
       appId, builder.GetSize());
  if (!client.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
    LOGE("Failed to send message");
    return false;
  }
  return true;
}

bool unloadNanoapp(SocketClient &client, sp<SocketCallbacks> callbacks,
                   uint64_t appId) {
  if (!sendUnloadNanoappRequest(client, appId)) {
    return false;
  }
  auto status = kReadyCond.wait_for(kReadyCondLock, kTimeout);
  bool success =
      (status == std::cv_status::no_timeout && callbacks->actionSucceeded());
  LOGI("Unloaded the nanoapp with appId: %" PRIx64 " success: %d", appId,
       success);
  return success;
}

bool listNanoapps(SocketClient &client) {
  if (!requestNanoappList(client)) {
    LOGE("Failed in listing nanoapps");
    return false;
  }
  auto status = kReadyCond.wait_for(kReadyCondLock, kTimeout);
  bool success = (status == std::cv_status::no_timeout);
  LOGI("Listed nanoapps success: %d", success);
  return success;
}

bool unloadAllNanoapps(SocketClient &client, sp<SocketCallbacks> callbacks) {
  if (!listNanoapps(client)) {
    return false;
  }
  for (auto appId : callbacks->getAppIdVector()) {
    if (!unloadNanoapp(client, callbacks, appId)) {
      LOGE("Failed in unloading nanoapps, unloading aborted");
      return false;
    }
  }
  LOGI("Unloaded all nanoapps succeeded");
  return true;
}

bool isTcmArgSpecified(std::vector<string> &args) {
  return !args.empty() && args[0] == "tcm";
}

inline uint64_t getId(std::vector<string> &args) {
  return isTcmArgSpecified(args) ? kPowerTestTcmAppId : kPowerTestAppId;
}

const string searchPath(const string &name) {
  const string kAdspPath = "vendor/dsp/adsp/" + name;
  const string kSdspPath = "vendor/dsp/sdsp/" + name;
  const string kEtcPath = "vendor/etc/chre/" + name;

  struct stat buf;
  if (stat(kAdspPath.c_str(), &buf) == 0) {
    return kAdspPath;
  } else if (stat(kSdspPath.c_str(), &buf) == 0) {
    return kSdspPath;
  } else {
    return kEtcPath;
  }
}

/**
 * When user provides the customized path in tcm mode, the args[1] is the path.
 * In this case, the args[0] has to be "tcm". When user provide customized path
 * for non-tcm mode, the args[0] is the path.
 */

inline const string getPath(std::vector<string> &args) {
  if (args.empty()) {
    return searchPath(kPowerTestName);
  }
  if (args[0] == "tcm") {
    if (args.size() > 1) {
      return args[1];
    }
    return searchPath(kPowerTestTcmName);
  }
  return args[0];
}

inline uint64_t getNanoseconds(std::vector<string> &args, size_t index) {
  return args.size() > index ? strtoull(args[index].c_str(), NULL, 0) : 0;
}

inline uint32_t getMilliseconds(std::vector<string> &args, size_t index) {
  return args.size() > index ? strtoul(args[index].c_str(), NULL, 0) : 0;
}

bool isLoaded(SocketClient &client, sp<SocketCallbacks> callbacks,
              std::vector<string> &args) {
  uint64_t id = getId(args);
  if (!listNanoapps(client)) {
    return false;
  }
  for (auto appId : callbacks->getAppIdVector()) {
    if (appId == id) {
      LOGI("The required nanoapp was loaded");
      return true;
    }
  }
  LOGE("The required nanoapp was not loaded");
  return false;
}

bool validateSensorArguments(std::vector<string> &args) {
  if (args.size() < 3) {
    LOGE("Sensor type is required");
    return false;
  }

  if (sensorTypeMap.find(args[2]) == sensorTypeMap.end()) {
    LOGE("Invalid sensor type");
    return false;
  }

  SensorType sensorType = sensorTypeMap[args[2]];
  if (sensorType == SensorType::STATIONARY_DETECT ||
      sensorType == SensorType::INSTANT_MOTION_DETECT)
    return true;

  uint64_t intervalNanoseconds = getNanoseconds(args, 3);
  uint64_t latencyNanoseconds = getNanoseconds(args, 4);
  if (intervalNanoseconds == 0) {
    LOGE("Non zero sensor sampling interval is required when enable");
    return false;
  }
  if (latencyNanoseconds != 0 && latencyNanoseconds < intervalNanoseconds) {
    LOGE("The latency is not zero and smaller than the interval");
    return false;
  }
  return true;
}

bool validateWifiArguments(std::vector<string> &args) {
  if (args.size() < 3) {
    LOGE("The interval is required");
    return false;
  }

  bool valid = true;
  WifiScanType scanType;
  WifiRadioChain radioChain;
  WifiChannelSet channelSet;
  for (int i = 3; i < 6 && args.size() > i && valid; i++) {
    valid = wifiScanTypeMatch(args[i], &scanType) ||
            wifiRadioChainMatch(args[i], &radioChain) ||
            wifiChannelSetMatch(args[i], &channelSet);
    if (!valid) {
      LOGE("Invalid WiFi scan parameters: %s", args[i].c_str());
      return false;
    }
  }

  uint64_t intervalNanoseconds = getNanoseconds(args, 2);
  if (intervalNanoseconds == 0) {
    LOGE("Non-zero WiFi request interval is required");
    return false;
  }

  return true;
}

bool validateArguments(Command commandEnum, std::vector<string> &args) {
  // Commands: unloadall, load, unload
  if (static_cast<uint32_t>(commandEnum) < 3) return true;

  // The other commands.
  if (args.empty()) {
    LOGE("Not enough parameters");
    return false;
  }

  // For non tcm option, add one item to the head of args to align argument
  // position with that with tcm option.
  if (args[0] != "tcm") args.insert(args.begin(), "");
  if (args.size() < 2) {
    LOGE("Not enough parameters");
    return false;
  }

  if (commandEnum == Command::kNanSub) {
    if (args.size() != 3) {
      LOGE("Incorrect number of parameters for NAN sub");
      return false;
    }
    return true;
  }

  if (commandEnum == Command::kNanCancel) {
    if (args.size() != 2) {
      LOGE("Incorrect number of parameters for NAN cancel");
      return false;
    }
    return true;
  }

  if (args[1] != "enable" && args[1] != "disable") {
    LOGE("<enable> was neither enable nor disable");
    return false;
  }

  if (commandEnum == Command::kBreakIt) return true;

  if (args[1] == "disable") {
    if (commandEnum != Command::kSensor) return true;
    if (args.size() > 2 && sensorTypeMap.find(args[2]) != sensorTypeMap.end())
      return true;
    LOGE("No sensor type or invalid sensor type");
    return false;
  }

  // Case of "enable":
  if (commandEnum == Command::kSensor) {
    return validateSensorArguments(args);
  } else if (commandEnum == Command::kWifi) {
    return validateWifiArguments(args);
  } else {
    if (args.size() < 3) {
      LOGE("The interval or duration was not provided");
      return false;
    }

    // For checking if the interval is 0. The getNanoseconds and
    // and the getMilliseconds are exchangable in this case.
    if (getNanoseconds(args, 2) == 0) {
      LOGE("Non zero interval or duration is required when enable");
      return false;
    }
    return true;
  }
}

void createTimerMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint64_t intervalNanoseconds = getNanoseconds(args, 2);
  fbb.Finish(ptest::CreateTimerMessage(fbb, enable, intervalNanoseconds));
  LOGI("Created TimerMessage, enable %d, wakeup interval ns %" PRIu64, enable,
       intervalNanoseconds);
}

void createWifiMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint64_t intervalNanoseconds = getNanoseconds(args, 2);
  WifiScanType scanType = WifiScanType::NO_PREFERENCE;
  WifiRadioChain radioChain = WifiRadioChain::DEFAULT;
  WifiChannelSet channelSet = WifiChannelSet::NON_DFS;

  // Check for the 3 optional parameters.
  bool valid = true;
  for (int i = 3; i < 6 && args.size() > i && valid; i++) {
    valid = wifiScanTypeMatch(args[i], &scanType) ||
            wifiRadioChainMatch(args[i], &radioChain) ||
            wifiChannelSetMatch(args[i], &channelSet);
  }

  fbb.Finish(ptest::CreateWifiScanMessage(fbb, enable, intervalNanoseconds,
                                          scanType, radioChain, channelSet));
  LOGI("Created WifiScanMessage, enable %d, scan interval ns %" PRIu64
       " scan type %" PRIu8 " radio chain %" PRIu8 " channel set %" PRIu8,
       enable, intervalNanoseconds, static_cast<uint8_t>(scanType),
       static_cast<uint8_t>(radioChain), static_cast<uint8_t>(channelSet));
}

void createGnssMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint32_t intervalMilliseconds = getMilliseconds(args, 2);
  uint32_t toNextFixMilliseconds = getMilliseconds(args, 3);
  fbb.Finish(ptest::CreateGnssLocationMessage(fbb, enable, intervalMilliseconds,
                                              toNextFixMilliseconds));
  LOGI("Created GnssLocationMessage, enable %d, scan interval ms %" PRIu32
       " min time to next fix ms %" PRIu32,
       enable, intervalMilliseconds, toNextFixMilliseconds);
}

void createCellMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint64_t intervalNanoseconds = getNanoseconds(args, 2);
  fbb.Finish(ptest::CreateCellQueryMessage(fbb, enable, intervalNanoseconds));
  LOGI("Created CellQueryMessage, enable %d, query interval ns %" PRIu64,
       enable, intervalNanoseconds);
}

void createAudioMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint64_t durationNanoseconds = getNanoseconds(args, 2);
  fbb.Finish(
      ptest::CreateAudioRequestMessage(fbb, enable, durationNanoseconds));
  LOGI("Created AudioRequestMessage, enable %d, buffer duration ns %" PRIu64,
       enable, durationNanoseconds);
}

void createSensorMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  SensorType sensorType = sensorTypeMap[args[2]];
  uint64_t intervalNanoseconds = getNanoseconds(args, 3);
  uint64_t latencyNanoseconds = getNanoseconds(args, 4);
  if (sensorType == SensorType::STATIONARY_DETECT ||
      sensorType == SensorType::INSTANT_MOTION_DETECT) {
    intervalNanoseconds = kUint64Max;
    latencyNanoseconds = 0;
  }
  fbb.Finish(ptest::CreateSensorRequestMessage(
      fbb, enable, sensorType, intervalNanoseconds, latencyNanoseconds));
  LOGI(
      "Created SensorRequestMessage, enable %d, %s sensor, sampling "
      "interval ns %" PRIu64 ", latency ns %" PRIu64,
      enable, ptest::EnumNameSensorType(sensorType), intervalNanoseconds,
      latencyNanoseconds);
}

void createBreakItMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  fbb.Finish(ptest::CreateBreakItMessage(fbb, enable));
  LOGI("Created BreakItMessage, enable %d", enable);
}

void createGnssMeasMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  bool enable = (args[1] == "enable");
  uint32_t intervalMilliseconds = getMilliseconds(args, 2);
  fbb.Finish(
      ptest::CreateGnssMeasurementMessage(fbb, enable, intervalMilliseconds));
  LOGI("Created GnssMeasurementMessage, enable %d, interval ms %" PRIu32,
       enable, intervalMilliseconds);
}

void createNanSubMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  uint8_t subType = atoi(args[1].c_str());
  std::string &serviceName = args[2];
  std::vector<uint8_t> serviceNameBytes(serviceName.begin(), serviceName.end());
  fbb.Finish(
      ptest::CreateWifiNanSubMessageDirect(fbb, subType, &serviceNameBytes));
  LOGI("Created NAN subscription message, subType %d serviceName %s", subType,
       serviceName.c_str());
}

void createNanCancelMessage(FlatBufferBuilder &fbb, std::vector<string> &args) {
  uint32_t subId = strtoul(args[1].c_str(), nullptr /* endptr */, 0 /* base */);
  fbb.Finish(ptest::CreateWifiNanSubCancelMessage(fbb, subId));
  LOGI("Created NAN subscription cancel message, subId %" PRIu32, subId);
}

bool sendMessageToNanoapp(SocketClient &client, sp<SocketCallbacks> callbacks,
                          FlatBufferBuilder &fbb, uint64_t appId,
                          MessageType messageType) {
  FlatBufferBuilder builder(128);
  HostProtocolHost::encodeNanoappMessage(
      builder, appId, static_cast<uint32_t>(messageType), kHostEndpoint,
      fbb.GetBufferPointer(), fbb.GetSize());
  LOGI("sending %s message to nanoapp (%" PRIu32 " bytes w/%" PRIu32
       " bytes of payload)",
       ptest::EnumNameMessageType(messageType), builder.GetSize(),
       fbb.GetSize());
  if (!client.sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
    LOGE("Failed to send %s message", ptest::EnumNameMessageType(messageType));
    return false;
  }
  auto status = kReadyCond.wait_for(kReadyCondLock, kTimeout);
  bool success =
      (status == std::cv_status::no_timeout && callbacks->actionSucceeded());
  LOGI("Sent %s message to nanoapp success: %d",
       ptest::EnumNameMessageType(messageType), success);
  if (status == std::cv_status::timeout) {
    LOGE("Sent %s message to nanoapp timeout",
         ptest::EnumNameMessageType(messageType));
  }
  return success;
}

static void usage() {
  LOGI(
      "\n"
      "Usage:\n"
      " chre_power_test_client load <optional: tcm> <optional: path>\n"
      " chre_power_test_client unload <optional: tcm>\n"
      " chre_power_test_client unloadall\n"
      " chre_power_test_client timer <optional: tcm> <enable> <interval_ns>\n"
      " chre_power_test_client wifi <optional: tcm> <enable> <interval_ns>"
      " <optional: wifi_scan_type> <optional: wifi_radio_chain>"
      " <optional: wifi_channel_set>\n"
      " chre_power_test_client gnss <optional: tcm> <enable> <interval_ms>"
      " <next_fix_ms>\n"
      " chre_power_test_client cell <optional: tcm> <enable> <interval_ns>\n"
      " chre_power_test_client audio <optional: tcm> <enable> <duration_ns>\n"
      " chre_power_test_client sensor <optional: tcm> <enable> <sensor_type>"
      " <interval_ns> <optional: latency_ns>\n"
      " chre_power_test_client breakit <optional: tcm> <enable>\n"
      " chre_power_test_client gnss_meas <optional: tcm> <enable> <interval_ms>"
      "\n"
      " chre_power_test_client wifi_nan_sub <optional: tcm> <sub_type>"
      " <service_name>\n"
      " chre_power_test_client end_wifi_nan_sub <optional: tcm>"
      " <subscription_id>\n"
      "Command:\n"
      "load: load power test nanoapp to CHRE\n"
      "unload: unload power test nanoapp from CHRE\n"
      "unloadall: unload all nanoapps in CHRE\n"
      "timer: start/stop timer wake up\n"
      "wifi: start/stop periodic wifi scan\n"
      "gnss: start/stop periodic GPS scan\n"
      "cell: start/stop periodic cellular scan\n"
      "audio: start/stop periodic audio capture\n"
      "sensor: start/stop periodic sensor sampling\n"
      "breakit: start/stop all action for stress tests\n"
      "gnss_meas: start/stop periodic GNSS measurement\n"
      "wifi_nan_sub: start a WiFi NAN subscription\n"
      "end_wifi_nan_sub: end a WiFi NAN subscription\n"
      "\n"
      "<optional: tcm>: tcm for micro image, default for big image\n"
      "<enable>: enable/disable\n"
      "\n"
      "<sensor_type>:\n"
      " accelerometer\n"
      " instant_motion\n"
      " stationary\n"
      " gyroscope\n"
      " uncalibrated_gyroscope\n"
      " geomagnetic\n"
      " uncalibrated_geomagnetic\n"
      " pressure\n"
      " light\n"
      " proximity\n"
      " step\n"
      " uncalibrated_accelerometer\n"
      " accelerometer_temperature\n"
      " gyroscope_temperature\n"
      " geomanetic_temperature\n"
      "\n"
      " For instant_montion and stationary sersor, it is not necessary to"
      " provide the interval and latency.\n"
      "\n"
      "<wifi_scan_type>:\n"
      " active\n"
      " active_passive_dfs\n"
      " passive\n"
      " no_preference (default when omitted)\n"
      "\n"
      "<wifi_radio_chain>:\n"
      " default (default when omitted)\n"
      " low_latency\n"
      " low_power\n"
      " high_accuracy\n"
      "\n"
      "<wifi_channel_set>:\n"
      " non_dfs (default when omitted)\n"
      " all\n");
}

void createRequestMessage(Command commandEnum, FlatBufferBuilder &fbb,
                          std::vector<string> &args) {
  switch (commandEnum) {
    case Command::kTimer:
      createTimerMessage(fbb, args);
      break;
    case Command::kWifi:
      createWifiMessage(fbb, args);
      break;
    case Command::kGnss:
      createGnssMessage(fbb, args);
      break;
    case Command::kCell:
      createCellMessage(fbb, args);
      break;
    case Command::kAudio:
      createAudioMessage(fbb, args);
      break;
    case Command::kSensor:
      createSensorMessage(fbb, args);
      break;
    case Command::kBreakIt:
      createBreakItMessage(fbb, args);
      break;
    case Command::kGnssMeas:
      createGnssMeasMessage(fbb, args);
      break;
    case Command::kNanSub:
      createNanSubMessage(fbb, args);
      break;
    case Command::kNanCancel:
      createNanCancelMessage(fbb, args);
      break;
    default: {
      usage();
    }
  }
}

}  // anonymous namespace

int main(int argc, char *argv[]) {
  int argi = 0;
  const std::string name{argv[argi++]};
  const std::string cmd{argi < argc ? argv[argi++] : ""};

  string commandLine(name);

  if (commandMap.find(cmd) == commandMap.end()) {
    usage();
    return -1;
  }

  commandLine.append(" " + cmd);
  Command commandEnum = commandMap[cmd];

  std::vector<std::string> args;
  while (argi < argc) {
    args.push_back(std::string(argv[argi++]));
    commandLine.append(" " + args.back());
  }

  LOGI("Command line: %s", commandLine.c_str());

  if (!validateArguments(commandEnum, args)) {
    LOGE("Invalid arguments");
    usage();
    return -1;
  }

  SocketClient client;
  sp<SocketCallbacks> callbacks = new SocketCallbacks(kReadyCond);

  if (!client.connect("chre", callbacks)) {
    LOGE("Couldn't connect to socket");
    return -1;
  }

  bool success = false;
  switch (commandEnum) {
    case Command::kUnloadAll: {
      success = unloadAllNanoapps(client, callbacks);
      break;
    }
    case Command::kUnload: {
      success = unloadNanoapp(client, callbacks, getId(args));
      break;
    }
    case Command::kLoad: {
      LOGI("Loading nanoapp from %s", getPath(args).c_str());
      std::string filepath = getPath(args);
      // Strip extension if present so the path can be used for both the
      // nanoapp header and .so
      size_t index = filepath.find_last_of(".");
      if (index != std::string::npos) {
        filepath = filepath.substr(0, index);
      }
      success = loadNanoapp(client, callbacks, filepath);
      break;
    }
    default: {
      if (!isLoaded(client, callbacks, args)) {
        LOGE("The power test nanoapp has to be loaded before sending request");
        return -1;
      }
      FlatBufferBuilder fbb(64);
      createRequestMessage(commandEnum, fbb, args);
      success = sendMessageToNanoapp(client, callbacks, fbb, getId(args),
                                     messageTypeMap[cmd]);
    }
  }

  client.disconnect();
  return success ? 0 : -1;
}
