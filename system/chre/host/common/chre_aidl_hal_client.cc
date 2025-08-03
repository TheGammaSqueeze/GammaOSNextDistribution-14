/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <aidl/android/hardware/contexthub/BnContextHubCallback.h>
#include <aidl/android/hardware/contexthub/IContextHub.h>
#include <aidl/android/hardware/contexthub/NanoappBinary.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <dirent.h>
#include <utils/String16.h>

#include <cctype>
#include <filesystem>
#include <fstream>
#include <future>
#include <map>
#include <regex>
#include <stdexcept>
#include <string>
#include <unordered_set>
#include <vector>

#include "chre_api/chre/version.h"
#include "chre_host/file_stream.h"
#include "chre_host/hal_client.h"
#include "chre_host/log.h"
#include "chre_host/napp_header.h"

using ::aidl::android::hardware::contexthub::AsyncEventType;
using ::aidl::android::hardware::contexthub::BnContextHubCallback;
using ::aidl::android::hardware::contexthub::ContextHubInfo;
using ::aidl::android::hardware::contexthub::ContextHubMessage;
using ::aidl::android::hardware::contexthub::HostEndpointInfo;
using ::aidl::android::hardware::contexthub::IContextHub;
using ::aidl::android::hardware::contexthub::MessageDeliveryStatus;
using ::aidl::android::hardware::contexthub::NanoappBinary;
using ::aidl::android::hardware::contexthub::NanoappInfo;
using ::aidl::android::hardware::contexthub::NanSessionRequest;
using ::aidl::android::hardware::contexthub::Setting;
using ::android::chre::HalClient;
using ::android::chre::NanoAppBinaryHeader;
using ::android::chre::readFileContents;
using ::android::internal::ToString;
using ::ndk::ScopedAStatus;

namespace {
// A default id 0 is used for every command requiring a context hub id. When
// this is not the case the id number should be one of the arguments of the
// commands.
constexpr uint32_t kContextHubId = 0;
constexpr int32_t kLoadTransactionId = 1;
constexpr int32_t kUnloadTransactionId = 2;

// Though IContextHub.aidl says loading operation is capped at 30s to finish,
// multiclient HAL can terminate a load/unload transaction after 5s to avoid
// blocking other load/unload transactions.
constexpr auto kTimeOutThresholdInSec = std::chrono::seconds(5);

// 34a3a27e-9b83-4098-b564-e83b0c28d4bb
constexpr std::array<uint8_t, 16> kUuid = {0x34, 0xa3, 0xa2, 0x7e, 0x9b, 0x83,
                                           0x40, 0x98, 0xb5, 0x64, 0xe8, 0x3b,
                                           0x0c, 0x28, 0xd4, 0xbb};

// Locations should be searched in the sequence defined below:
const char *kPredefinedNanoappPaths[] = {
    "/vendor/etc/chre/",
    "/vendor/dsp/adsp/",
    "/vendor/dsp/sdsp/",
    "/vendor/lib/rfsa/adsp/",
};

const std::string kClientName{"ChreAidlHalClient"};

inline void throwError(const std::string &message) {
  throw std::system_error{std::error_code(), message};
}

bool isValidHexNumber(const std::string &number) {
  if (number.empty() ||
      (number.substr(0, 2) != "0x" && number.substr(0, 2) != "0X")) {
    return false;
  }
  for (int i = 2; i < number.size(); i++) {
    if (!isxdigit(number[i])) {
      throwError("Hex app id " + number + " contains invalid character.");
    }
  }
  return number.size() > 2;
}

uint16_t verifyAndConvertEndpointHexId(const std::string &number) {
  // host endpoint id must be a 16-bits long hex number.
  if (isValidHexNumber(number)) {
    int convertedNumber = std::stoi(number, /* idx= */ nullptr, /* base= */ 16);
    if (convertedNumber < std::numeric_limits<uint16_t>::max()) {
      return static_cast<uint16_t>(convertedNumber);
    }
  }
  throwError("host endpoint id must be a 16-bits long hex number.");
  return 0;  // code never reached.
}

bool isValidNanoappHexId(const std::string &number) {
  if (!isValidHexNumber(number)) {
    return false;
  }
  // Once the input has the hex prefix, an exception will be thrown if it is
  // malformed because it shouldn't be treated as an app name anymore.
  if (number.size() > 18) {
    throwError("Hex app id must has a length of [3, 18] including the prefix.");
  }
  return true;
}

std::string parseAppVersion(uint32_t version) {
  std::ostringstream stringStream;
  stringStream << std::hex << "0x" << version << std::dec << " (v"
               << CHRE_EXTRACT_MAJOR_VERSION(version) << "."
               << CHRE_EXTRACT_MINOR_VERSION(version) << "."
               << CHRE_EXTRACT_PATCH_VERSION(version) << ")";
  return stringStream.str();
}

std::string parseTransactionId(int32_t transactionId) {
  switch (transactionId) {
    case kLoadTransactionId:
      return "Loading";
    case kUnloadTransactionId:
      return "Unloading";
    default:
      return "Unknown";
  }
}

class ContextHubCallback : public BnContextHubCallback {
 public:
  ScopedAStatus handleNanoappInfo(
      const std::vector<NanoappInfo> &appInfo) override {
    std::cout << appInfo.size() << " nanoapps loaded" << std::endl;
    for (const NanoappInfo &app : appInfo) {
      std::cout << "appId: 0x" << std::hex << app.nanoappId << std::dec << " {"
                << "\n\tappVersion: " << parseAppVersion(app.nanoappVersion)
                << "\n\tenabled: " << (app.enabled ? "true" : "false")
                << "\n\tpermissions: " << ToString(app.permissions)
                << "\n\trpcServices: " << ToString(app.rpcServices) << "\n}"
                << std::endl;
    }
    resetPromise();
    return ScopedAStatus::ok();
  }

  ScopedAStatus handleContextHubMessage(
      const ContextHubMessage &message,
      const std::vector<std::string> & /*msgContentPerms*/) override {
    std::cout << "Received a message!" << std::endl
              << "   From: 0x" << std::hex << message.nanoappId << std::endl
              << "     To: 0x" << static_cast<int>(message.hostEndPoint)
              << std::endl
              << "   Body: (type " << message.messageType << " size "
              << message.messageBody.size() << ") 0x";
    for (const uint8_t &data : message.messageBody) {
      std::cout << std::hex << static_cast<uint16_t>(data);
    }
    std::cout << std::endl << std::endl;
    resetPromise();
    return ScopedAStatus::ok();
  }

  ScopedAStatus handleContextHubAsyncEvent(AsyncEventType event) override {
    std::cout << "Received async event " << toString(event) << std::endl;
    resetPromise();
    return ScopedAStatus::ok();
  }

  // Called after loading/unloading a nanoapp.
  ScopedAStatus handleTransactionResult(int32_t transactionId,
                                        bool success) override {
    std::cout << parseTransactionId(transactionId) << " transaction is "
              << (success ? "successful" : "failed") << std::endl;
    resetPromise();
    return ScopedAStatus::ok();
  }

  ScopedAStatus handleNanSessionRequest(
      const NanSessionRequest & /* request */) override {
    resetPromise();
    return ScopedAStatus::ok();
  }

  ScopedAStatus handleMessageDeliveryStatus(
      char16_t /* hostEndPointId */,
      const MessageDeliveryStatus & /* messageDeliveryStatus */) override {
    resetPromise();
    return ScopedAStatus::ok();
  }

  ScopedAStatus getUuid(std::array<uint8_t, 16> *out_uuid) override {
    *out_uuid = kUuid;
    return ScopedAStatus::ok();
  }

  ScopedAStatus getName(std::string *out_name) override {
    *out_name = kClientName;
    return ScopedAStatus::ok();
  }

  void resetPromise() {
    promise.set_value();
    promise = std::promise<void>{};
  }

  // TODO(b/247124878):
  // This promise is shared among all the HAL callbacks to simplify the
  // implementation. This is based on the assumption that every command should
  // get a response before timeout and the first callback triggered is for the
  // response.
  //
  // In very rare cases, however, the assumption doesn't hold:
  //  - multiple callbacks are triggered by a command and come back out of order
  //  - one command is timed out and the user typed in another command then the
  //  first callback for the first command is triggered
  // Once we have a chance we should consider refactor this design to let each
  // callback use their specific promises.
  std::promise<void> promise;
};

std::shared_ptr<IContextHub> gContextHub = nullptr;
std::shared_ptr<ContextHubCallback> gCallback = nullptr;

void registerHostCallback() {
  if (gCallback != nullptr) {
    gCallback.reset();
  }
  gCallback = ContextHubCallback::make<ContextHubCallback>();
  if (!gContextHub->registerCallback(kContextHubId, gCallback).isOk()) {
    throwError("Failed to register the callback");
  }
}

/** Initializes gContextHub and register gCallback. */
std::shared_ptr<IContextHub> getContextHub() {
  if (gContextHub == nullptr) {
    auto aidlServiceName = std::string() + IContextHub::descriptor + "/default";
    ndk::SpAIBinder binder(
        AServiceManager_waitForService(aidlServiceName.c_str()));
    if (binder.get() == nullptr) {
      throwError("Could not find Context Hub HAL");
    }
    gContextHub = IContextHub::fromBinder(binder);
  }
  if (gCallback == nullptr) {
    registerHostCallback();
  }
  return gContextHub;
}

void printNanoappHeader(const NanoAppBinaryHeader &header) {
  std::cout << " {"
            << "\n\tappId: 0x" << std::hex << header.appId << std::dec
            << "\n\tappVersion: " << parseAppVersion(header.appVersion)
            << "\n\tflags: " << header.flags << "\n\ttarget CHRE API version: "
            << static_cast<int>(header.targetChreApiMajorVersion) << "."
            << static_cast<int>(header.targetChreApiMinorVersion) << "\n}"
            << std::endl;
}

std::unique_ptr<NanoAppBinaryHeader> findHeaderByName(
    const std::string &appName, const std::string &binaryPath) {
  DIR *dir = opendir(binaryPath.c_str());
  if (dir == nullptr) {
    return nullptr;
  }
  std::regex regex(appName + ".napp_header");
  std::cmatch match;

  std::unique_ptr<NanoAppBinaryHeader> result = nullptr;
  for (struct dirent *entry; (entry = readdir(dir)) != nullptr;) {
    if (!std::regex_match(entry->d_name, match, regex)) {
      continue;
    }
    std::ifstream input(std::string(binaryPath) + "/" + entry->d_name,
                        std::ios::binary);
    result = std::make_unique<NanoAppBinaryHeader>();
    input.read(reinterpret_cast<char *>(result.get()),
               sizeof(NanoAppBinaryHeader));
    break;
  }
  closedir(dir);
  return result;
}

void readNanoappHeaders(std::map<std::string, NanoAppBinaryHeader> &nanoapps,
                        const std::string &binaryPath) {
  DIR *dir = opendir(binaryPath.c_str());
  if (dir == nullptr) {
    return;
  }
  std::regex regex("(\\w+)\\.napp_header");
  std::cmatch match;
  for (struct dirent *entry; (entry = readdir(dir)) != nullptr;) {
    if (!std::regex_match(entry->d_name, match, regex)) {
      continue;
    }
    std::ifstream input(std::string(binaryPath) + "/" + entry->d_name,
                        std::ios::binary);
    input.read(reinterpret_cast<char *>(&nanoapps[match[1]]),
               sizeof(NanoAppBinaryHeader));
  }
  closedir(dir);
}

void verifyStatus(const std::string &operation, const ScopedAStatus &status) {
  if (!status.isOk()) {
    gCallback->resetPromise();
    throwError(operation + " fails with abnormal status " +
               ToString(status.getMessage()) + " error code " +
               ToString(status.getServiceSpecificError()));
  }
}

void verifyStatusAndSignal(const std::string &operation,
                           const ScopedAStatus &status,
                           const std::future<void> &future_signal) {
  verifyStatus(operation, status);
  std::future_status future_status =
      future_signal.wait_for(kTimeOutThresholdInSec);
  if (future_status != std::future_status::ready) {
    gCallback->resetPromise();
    throwError(operation + " doesn't finish within " +
               ToString(kTimeOutThresholdInSec.count()) + " seconds");
  }
}

/** Finds the .napp_header file associated to the nanoapp.
 *
 * This function guarantees to return a non-null {@link NanoAppBinaryHeader}
 * pointer. In case a .napp_header file cannot be found an exception will be
 * raised.
 *
 * @param pathAndName name of the nanoapp that might be prefixed with it path.
 * It will be normalized to the format of <absolute-path><name>.so at the end.
 * For example, "abc" will be changed to "/path/to/abc.so".
 * @return a unique pointer to the {@link NanoAppBinaryHeader} found
 */
std::unique_ptr<NanoAppBinaryHeader> findHeaderAndNormalizePath(
    std::string &pathAndName) {
  // To match the file pattern of [path]<name>[.so]
  std::regex pathNameRegex("(.*?)(\\w+)(\\.so)?");
  std::smatch smatch;
  if (!std::regex_match(pathAndName, smatch, pathNameRegex)) {
    throwError("Invalid nanoapp: " + pathAndName);
  }
  std::string fullPath = smatch[1];
  std::string appName = smatch[2];
  // absolute path is provided:
  if (!fullPath.empty() && fullPath[0] == '/') {
    auto result = findHeaderByName(appName, fullPath);
    if (result == nullptr) {
      throwError("Unable to find the nanoapp header for " + pathAndName);
    }
    pathAndName = fullPath + appName + ".so";
    return result;
  }
  // relative path is searched form predefined locations:
  for (const std::string &predefinedPath : kPredefinedNanoappPaths) {
    auto result = findHeaderByName(appName, predefinedPath);
    if (result == nullptr) {
      continue;
    }
    pathAndName = predefinedPath + appName + ".so";
    return result;
  }
  throwError("Unable to find the nanoapp header for " + pathAndName);
  return nullptr;
}

int64_t getNanoappIdFrom(std::string &appIdOrName) {
  int64_t appId;
  if (isValidNanoappHexId(appIdOrName)) {
    appId = std::stoll(appIdOrName, nullptr, 16);
  } else {
    // Treat the appIdOrName as the app name and try again
    appId =
        static_cast<int64_t>(findHeaderAndNormalizePath(appIdOrName)->appId);
  }
  return appId;
}

void getAllContextHubs() {
  std::vector<ContextHubInfo> hubs{};
  getContextHub()->getContextHubs(&hubs);
  if (hubs.empty()) {
    std::cerr << "Failed to get any context hub." << std::endl;
    return;
  }
  for (const auto &hub : hubs) {
    std::cout << "Context Hub " << hub.id << ": " << std::endl
              << "  Name: " << hub.name << std::endl
              << "  Vendor: " << hub.vendor << std::endl
              << "  Max support message length (bytes): "
              << hub.maxSupportedMessageLengthBytes << std::endl
              << "  Version: " << static_cast<uint32_t>(hub.chreApiMajorVersion)
              << "." << static_cast<uint32_t>(hub.chreApiMinorVersion)
              << std::endl
              << "  Chre platform id: 0x" << std::hex << hub.chrePlatformId
              << std::endl;
  }
}

void loadNanoapp(std::string &pathAndName) {
  auto header = findHeaderAndNormalizePath(pathAndName);
  std::vector<uint8_t> soBuffer{};
  if (!readFileContents(pathAndName.c_str(), soBuffer)) {
    throwError("Failed to open the content of " + pathAndName);
  }
  NanoappBinary binary;
  binary.nanoappId = static_cast<int64_t>(header->appId);
  binary.customBinary = soBuffer;
  binary.flags = static_cast<int32_t>(header->flags);
  binary.targetChreApiMajorVersion =
      static_cast<int8_t>(header->targetChreApiMajorVersion);
  binary.targetChreApiMinorVersion =
      static_cast<int8_t>(header->targetChreApiMinorVersion);
  binary.nanoappVersion = static_cast<int32_t>(header->appVersion);

  auto status =
      getContextHub()->loadNanoapp(kContextHubId, binary, kLoadTransactionId);
  verifyStatusAndSignal(/* operation= */ "loading nanoapp " + pathAndName,
                        status, gCallback->promise.get_future());
}

void unloadNanoapp(std::string &appIdOrName) {
  auto appId = getNanoappIdFrom(appIdOrName);
  auto status = getContextHub()->unloadNanoapp(kContextHubId, appId,
                                               kUnloadTransactionId);
  verifyStatusAndSignal(/* operation= */ "unloading nanoapp " + appIdOrName,
                        status, gCallback->promise.get_future());
}

void queryNanoapps() {
  auto status = getContextHub()->queryNanoapps(kContextHubId);
  verifyStatusAndSignal(/* operation= */ "querying nanoapps", status,
                        gCallback->promise.get_future());
}

HostEndpointInfo createHostEndpointInfo(const std::string &hexEndpointId) {
  uint16_t hostEndpointId = verifyAndConvertEndpointHexId(hexEndpointId);
  return {
      .hostEndpointId = hostEndpointId,
      .type = HostEndpointInfo::Type::NATIVE,
      .packageName = "chre_aidl_hal_client",
      .attributionTag{},
  };
}

void onEndpointConnected(const std::string &hexEndpointId) {
  auto contextHub = getContextHub();
  HostEndpointInfo info = createHostEndpointInfo(hexEndpointId);
  // connect the endpoint to HAL
  verifyStatus(/* operation= */ "connect endpoint",
               contextHub->onHostEndpointConnected(info));
  std::cout << "Connected." << std::endl;
}

void onEndpointDisconnected(const std::string &hexEndpointId) {
  auto contextHub = getContextHub();
  uint16_t hostEndpointId = verifyAndConvertEndpointHexId(hexEndpointId);
  // disconnect the endpoint from HAL
  verifyStatus(/* operation= */ "disconnect endpoint",
               contextHub->onHostEndpointDisconnected(hostEndpointId));
  std::cout << "Disconnected." << std::endl;
}

ContextHubMessage createContextHubMessage(const std::string &hexHostEndpointId,
                                          std::string &appIdOrName,
                                          const std::string &hexPayload) {
  if (!isValidHexNumber(hexPayload)) {
    throwError("Invalid hex payload.");
  }
  auto appId = getNanoappIdFrom(appIdOrName);
  uint16_t hostEndpointId = verifyAndConvertEndpointHexId(hexHostEndpointId);
  ContextHubMessage contextHubMessage = {
      .nanoappId = appId,
      .hostEndPoint = hostEndpointId,
      .messageBody = {},
      .permissions = {},
  };
  // populate the payload
  for (int i = 2; i < hexPayload.size(); i += 2) {
    contextHubMessage.messageBody.push_back(
        std::stoi(hexPayload.substr(i, 2), /* idx= */ nullptr, /* base= */ 16));
  }
  return contextHubMessage;
}

/** Sends a hexPayload from hexHostEndpointId to appIdOrName. */
void sendMessageToNanoapp(const std::string &hexHostEndpointId,
                          std::string &appIdOrName,
                          const std::string &hexPayload) {
  ContextHubMessage contextHubMessage =
      createContextHubMessage(hexHostEndpointId, appIdOrName, hexPayload);
  // send the message
  auto contextHub = getContextHub();
  auto status = contextHub->sendMessageToHub(kContextHubId, contextHubMessage);
  verifyStatusAndSignal(/* operation= */ "sending a message to " + appIdOrName,
                        status, gCallback->promise.get_future());
}

void changeSetting(const std::string &setting, bool enabled) {
  auto contextHub = getContextHub();
  int settingType = std::stoi(setting);
  if (settingType < 1 || settingType > 7) {
    throwError("setting type must be within [1, 7].");
  }
  ScopedAStatus status =
      contextHub->onSettingChanged(static_cast<Setting>(settingType), enabled);
  std::cout << "onSettingChanged is called to "
            << (enabled ? "enable" : "disable") << " setting type "
            << settingType << std::endl;
  verifyStatus("change setting", status);
}

void enableTestModeOnContextHub() {
  auto status = getContextHub()->setTestMode(true);
  verifyStatus(/* operation= */ "enabling test mode", status);
  std::cout << "Test mode is enabled" << std::endl;
}

void disableTestModeOnContextHub() {
  auto status = getContextHub()->setTestMode(false);
  verifyStatus(/* operation= */ "disabling test mode", status);
  std::cout << "Test mode is disabled" << std::endl;
}

void getAllPreloadedNanoappIds() {
  std::vector<int64_t> appIds{};
  verifyStatus("get preloaded nanoapp ids",
               getContextHub()->getPreloadedNanoappIds(kContextHubId, &appIds));
  for (const auto &appId : appIds) {
    std::cout << "0x" << std::hex << appId << std::endl;
  }
}

// Please keep Command in alphabetical order
enum Command {
  connect,
  connectEndpoint,
  disableSetting,
  disableTestMode,
  disconnectEndpoint,
  enableSetting,
  enableTestMode,
  getContextHubs,
  getPreloadedNanoappIds,
  list,
  load,
  query,
  registerCallback,
  sendMessage,
  unload,
  unsupported
};

struct CommandInfo {
  Command cmd;
  u_int8_t numOfArgs;  // including cmd;
  std::string argsFormat;
  std::string usage;
};

const std::map<std::string, CommandInfo> kAllCommands{
    {"connect",
     {.cmd = connect,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "connect to HAL using hal_client library and keep the session "
               "alive while user can execute other commands. Use 'exit' to "
               "quit the session."}},
    {"connectEndpoint",
     {.cmd = connectEndpoint,
      .numOfArgs = 2,
      .argsFormat = "<HEX_ENDPOINT_ID>",
      .usage =
          "associate an endpoint with the current client and notify HAL."}},
    {"disableSetting",
     {.cmd = disableSetting,
      .numOfArgs = 2,
      .argsFormat = "<SETTING>",
      .usage = "disable a setting identified by a number defined in "
               "android/hardware/contexthub/Setting.aidl."}},
    {"disableTestMode",
     {.cmd = disableTestMode,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "disable test mode."}},
    {"disconnectEndpoint",
     {.cmd = disconnectEndpoint,
      .numOfArgs = 2,
      .argsFormat = "<HEX_ENDPOINT_ID>",
      .usage = "remove an endpoint with the current client and notify HAL."}},
    {"enableSetting",
     {.cmd = enableSetting,
      .numOfArgs = 2,
      .argsFormat = "<SETTING>",
      .usage = "enable a setting identified by a number defined in "
               "android/hardware/contexthub/Setting.aidl."}},
    {"enableTestMode",
     {.cmd = enableTestMode,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "enable test mode."}},
    {"getContextHubs",
     {.cmd = getContextHubs,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "get all the context hubs."}},
    {"getPreloadedNanoappIds",
     {.cmd = getPreloadedNanoappIds,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "get a list of ids for the preloaded nanoapps."}},
    {"list",
     {.cmd = list,
      .numOfArgs = 2,
      .argsFormat = "</PATH/TO/NANOAPPS>",
      .usage = "list all the nanoapps' header info in the path."}},
    {"load",
     {.cmd = load,
      .numOfArgs = 2,
      .argsFormat = "<APP_NAME | /PATH/TO/APP_NAME>",
      .usage = "load the nanoapp specified by the name. If an absolute path is "
               "not provided the default locations are searched."}},
    {"query",
     {.cmd = query,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "show all loaded nanoapps (system apps excluded)."}},
    {"registerCallback",
     {.cmd = registerCallback,
      .numOfArgs = 1,
      .argsFormat = "",
      .usage = "register a callback for the current client."}},
    {"sendMessage",
     {.cmd = sendMessage,
      .numOfArgs = 4,
      .argsFormat = "<HEX_ENDPOINT_ID> <HEX_NANOAPP_ID | APP_NAME | "
                    "/PATH/TO/APP_NAME> <HEX_PAYLOAD>",
      .usage = "send a payload to a nanoapp. If an absolute path is not "
               "provided the default locations are searched."}},
    {"unload",
     {.cmd = unload,
      .numOfArgs = 2,
      .argsFormat = "<HEX_NANOAPP_ID | APP_NAME | /PATH/TO/APP_NAME>",
      .usage = "unload the nanoapp specified by either the nanoapp id or the "
               "app name. If an absolute path is not provided the default "
               "locations are searched."}},
};

void fillSupportedCommandMap(
    const std::unordered_set<std::string> &supportedCommands,
    std::map<std::string, CommandInfo> &supportedCommandMap) {
  std::copy_if(kAllCommands.begin(), kAllCommands.end(),
               std::inserter(supportedCommandMap, supportedCommandMap.begin()),
               [&](auto const &kv_pair) {
                 return supportedCommands.find(kv_pair.first) !=
                        supportedCommands.end();
               });
}

void printUsage(const std::map<std::string, CommandInfo> &supportedCommands) {
  constexpr uint32_t kCommandLength = 40;
  std::cout << std::left << "Usage: COMMAND [ARGUMENTS]" << std::endl;
  for (auto const &kv_pair : supportedCommands) {
    std::string cmdLine = kv_pair.first + " " + kv_pair.second.argsFormat;
    std::cout << std::setw(kCommandLength) << cmdLine;
    if (cmdLine.size() > kCommandLength) {
      std::cout << std::endl << std::string(kCommandLength, ' ');
    }
    std::cout << " - " + kv_pair.second.usage << std::endl;
  }
  std::cout << std::endl;
}

Command parseCommand(
    const std::vector<std::string> &cmdLine,
    const std::map<std::string, CommandInfo> &supportedCommandMap) {
  if (cmdLine.empty() ||
      supportedCommandMap.find(cmdLine[0]) == supportedCommandMap.end()) {
    return unsupported;
  }
  auto cmdInfo = supportedCommandMap.at(cmdLine[0]);
  return cmdLine.size() == cmdInfo.numOfArgs ? cmdInfo.cmd : unsupported;
}

void executeCommand(std::vector<std::string> cmdLine) {
  switch (parseCommand(cmdLine, kAllCommands)) {
    case connectEndpoint: {
      onEndpointConnected(cmdLine[1]);
      break;
    }
    case disableSetting: {
      changeSetting(cmdLine[1], false);
      break;
    }
    case disableTestMode: {
      disableTestModeOnContextHub();
      break;
    }
    case disconnectEndpoint: {
      onEndpointDisconnected(cmdLine[1]);
      break;
    }
    case enableSetting: {
      changeSetting(cmdLine[1], true);
      break;
    }
    case enableTestMode: {
      enableTestModeOnContextHub();
      break;
    }
    case getContextHubs: {
      getAllContextHubs();
      break;
    }
    case getPreloadedNanoappIds: {
      getAllPreloadedNanoappIds();
      break;
    }
    case list: {
      std::map<std::string, NanoAppBinaryHeader> nanoapps{};
      readNanoappHeaders(nanoapps, cmdLine[1]);
      for (const auto &entity : nanoapps) {
        std::cout << entity.first;
        printNanoappHeader(entity.second);
      }
      break;
    }
    case load: {
      loadNanoapp(cmdLine[1]);
      break;
    }
    case query: {
      queryNanoapps();
      break;
    }
    case registerCallback: {
      registerHostCallback();
      break;
    }
    case sendMessage: {
      sendMessageToNanoapp(cmdLine[1], cmdLine[2], cmdLine[3]);
      break;
    }
    case unload: {
      unloadNanoapp(cmdLine[1]);
      break;
    }
    default:
      printUsage(kAllCommands);
  }
}

std::vector<std::string> getCommandLine() {
  std::string input;
  std::cout << "> ";
  std::getline(std::cin, input);
  input.push_back('\n');
  std::vector<std::string> result{};
  for (int begin = 0, end = 0; end < input.size();) {
    if (isspace(input[begin])) {
      end = begin = begin + 1;
      continue;
    }
    if (!isspace(input[end])) {
      end += 1;
      continue;
    }
    result.push_back(input.substr(begin, end - begin));
    begin = end;
  }
  return result;
}

void connectToHal() {
  if (gCallback == nullptr) {
    gCallback = ContextHubCallback::make<ContextHubCallback>();
  }
  std::unique_ptr<HalClient> halClient = HalClient::create(gCallback);
  if (halClient == nullptr) {
    LOGE("Failed to init the connection to HAL.");
    return;
  }
  std::unordered_set<std::string> supportedCommands = {
      "connectEndpoint", "disconnectEndpoint", "query", "sendMessage"};
  std::map<std::string, CommandInfo> supportedCommandMap{};
  fillSupportedCommandMap(supportedCommands, supportedCommandMap);

  while (true) {
    auto cmdLine = getCommandLine();
    if (cmdLine.empty()) {
      continue;
    }
    if (cmdLine.size() == 1 && cmdLine[0] == "exit") {
      break;
    }
    try {
      switch (parseCommand(cmdLine, supportedCommandMap)) {
        case connectEndpoint: {
          HostEndpointInfo info =
              createHostEndpointInfo(/* hexEndpointId= */ cmdLine[1]);
          verifyStatus(/* operation= */ "connect endpoint",
                       halClient->connectEndpoint(info));
          break;
        }

        case query: {
          verifyStatusAndSignal(/* operation= */ "querying nanoapps",
                                halClient->queryNanoapps(),
                                gCallback->promise.get_future());
          break;
        }

        case disconnectEndpoint: {
          uint16_t hostEndpointId =
              verifyAndConvertEndpointHexId(/* number= */ cmdLine[1]);
          verifyStatus(/* operation= */ "disconnect endpoint",
                       halClient->disconnectEndpoint(hostEndpointId));
          break;
        }
        case sendMessage: {
          ContextHubMessage message = createContextHubMessage(
              /* hexHostEndpointId= */ cmdLine[1],
              /* appIdOrName= */ cmdLine[2], /* hexPayload= */ cmdLine[3]);
          verifyStatusAndSignal(
              /* operation= */ "sending a message to " + cmdLine[2],
              halClient->sendMessage(message), gCallback->promise.get_future());
          break;
        }
        default:
          printUsage(supportedCommandMap);
      }
    } catch (std::system_error &e) {
      std::cerr << e.what() << std::endl;
    }
  }
}
}  // anonymous namespace

int main(int argc, char *argv[]) {
  // Start binder thread pool to enable callbacks.
  ABinderProcess_startThreadPool();

  std::vector<std::string> cmdLine{};
  for (int i = 1; i < argc; i++) {
    cmdLine.emplace_back(argv[i]);
  }
  try {
    if (cmdLine.size() == 1 && cmdLine[0] == "connect") {
      connectToHal();
      return 0;
    }
    executeCommand(cmdLine);
  } catch (std::system_error &e) {
    std::cerr << e.what() << std::endl;
    return -1;
  }
  return 0;
}
