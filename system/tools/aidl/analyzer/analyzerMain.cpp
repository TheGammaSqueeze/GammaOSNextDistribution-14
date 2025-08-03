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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <binder/BpBinder.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/RecordedTransaction.h>
#include <signal.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include "include/Analyzer.h"

using android::IBinder;
using android::NO_ERROR;
using android::sp;
using android::status_t;
using android::String16;
using android::String8;
using android::aidl::Analyzer;
using android::base::unique_fd;
using android::binder::debug::RecordedTransaction;
using std::string;
namespace {

static std::atomic_uint gCtrlCCount = 0;
static constexpr unsigned kCtrlCLimit = 3;
static const char kStandardRecordingPath[] = "/data/local/recordings/";

string getRecordingPath(const string& serviceName) {
  // Service names may contain '/', replace them with '.' to avoid interpreting as path
  string filename = serviceName;
  std::replace(filename.begin(), filename.end(), '/', '.');
  return kStandardRecordingPath + filename;
}

status_t startRecording(const string& serviceName) {
  sp<IBinder> binder =
      android::defaultServiceManager()->checkService(String16(serviceName.c_str()));
  if (binder == nullptr) {
    return android::BAD_VALUE;
  }

  auto filePath = getRecordingPath(serviceName);
  if (auto mkdir_return = mkdir(kStandardRecordingPath, 0666);
      mkdir_return != 0 && errno != EEXIST) {
    std::cout << "Failed to create recordings directory.\n";
    return android::NO_ERROR;
  }

  int openFlags = O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC | O_BINARY;
  unique_fd fd(open(filePath.c_str(), openFlags, 0666));
  if (!fd.ok()) {
    std::cout << "Failed to open file for recording with error: " << strerror(errno) << '\n';
    return android::BAD_VALUE;
  }

  // TODO (b/245804633): this still requires setenforce 0, but nothing above does
  if (status_t err = binder->remoteBinder()->startRecordingBinder(fd); err != android::NO_ERROR) {
    auto checkSE = std::ifstream("/sys/fs/selinux/enforce");
    bool recommendSetenforce = false;
    if ((checkSE.rdstate() & std::ifstream::failbit) != 0) {
      std::cout << "Failed to determine selinux state.";
      recommendSetenforce = true;
    } else {
      char seState = checkSE.get();
      if (checkSE.good()) {
        if (seState == '1') {
          std::cout << "SELinux must be permissive.";
          recommendSetenforce = true;
        } else if (seState == '0') {
          std::cout << "SELinux is permissive. Failing for some other reason.\n";
        }
      } else {
        std::cout << "Failed to determine SELinux state.";
        recommendSetenforce = true;
      }
    }
    if (recommendSetenforce) {
      std::cout << " Try running:\n\n  setenforce 0\n\n";
    }
    std::cout << "Failed to start recording with error: " << android::statusToString(err) << '\n';
    return err;
  } else {
    std::cout << "Recording started successfully.\n";
    return android::NO_ERROR;
  }
}

status_t stopRecording(const string& serviceName) {
  sp<IBinder> binder =
      android::defaultServiceManager()->checkService(String16(serviceName.c_str()));
  if (binder == nullptr) {
    return android::BAD_VALUE;
  }

  if (status_t err = binder->remoteBinder()->stopRecordingBinder(); err != NO_ERROR) {
    std::cout << "Failed to stop recording with error: " << err << '\n';
    return err;
  }

  std::cout << "Recording stopped successfully.\n";
  return NO_ERROR;
}

void printTransaction(const RecordedTransaction& transaction) {
  auto& analyzers = Analyzer::getAnalyzers();

  auto analyzer = analyzers.find(transaction.getInterfaceName());
  if (analyzer != analyzers.end()) {
    (analyzer->second)
        ->getAnalyzeFunction()(transaction.getCode(), transaction.getDataParcel(),
                               transaction.getReplyParcel());
  } else {
    std::cout << "No analyzer:";
    std::cout << "  interface: " << transaction.getInterfaceName() << "\n";
    std::cout << "  code: " << transaction.getCode() << "\n";
    std::cout << "  data: " << transaction.getDataParcel().dataSize() << " bytes\n";
    std::cout << "  reply: " << transaction.getReplyParcel().dataSize() << " bytes\n";
  }
  std::cout << "  status: " << transaction.getReturnedStatus() << "\n\n";
}

status_t inspectRecording(const string& path) {
  auto& analyzers = Analyzer::getAnalyzers();

  unique_fd fd(open(path.c_str(), O_RDONLY));
  if (!fd.ok()) {
    std::cout << "Failed to open recording file with error: " << strerror(errno) << '\n';
    return android::BAD_VALUE;
  }

  int i = 1;
  while (auto transaction = RecordedTransaction::fromFile(fd)) {
    std::cout << "Transaction " << i << ":\n";
    printTransaction(transaction.value());
    i++;
  }
  return NO_ERROR;
}

void incrementCtrlCCount(int signum) {
  if (++gCtrlCCount > kCtrlCLimit) {
    std::cout
        << "Ctrl+C multiple times, but could not quit application. If recording still running, you "
           "might stop it manually.\n";
    exit(signum);
  }
}

status_t listenToFile(const string& serviceName) {
  auto filePath = getRecordingPath(serviceName);
  unique_fd listenFd(open(filePath.c_str(), O_RDONLY));
  if (!listenFd.ok()) {
    std::cout << "Failed to open listening file with error: " << strerror(errno) << '\n';
    return android::BAD_VALUE;
  }

  auto& analyzers = Analyzer::getAnalyzers();

  signal(SIGINT, incrementCtrlCCount);
  std::cout << "Starting to listen:\n";
  int i = 1;
  while (gCtrlCCount == 0) {
    auto transaction = RecordedTransaction::fromFile(listenFd);
    if (!transaction) {
      sleep(1);
      continue;
    }
    std::cout << "Transaction " << i << ":\n";
    printTransaction(transaction.value());
    i++;
  }
  return NO_ERROR;
}

status_t replayFile(const sp<IBinder>& binder, const string& path) {
  auto& analyzers = Analyzer::getAnalyzers();

  unique_fd fd(open(path.c_str(), O_RDONLY));
  if (!fd.ok()) {
    std::cout << "Failed to open recording file with error: " << strerror(errno) << '\n';
    return android::BAD_VALUE;
  }

  int failureCount = 0;
  int i = 1;
  while (auto transaction = RecordedTransaction::fromFile(fd)) {
    std::cout << "Replaying Transaction " << i << ":\n";
    printTransaction(transaction.value());

    android::Parcel send, reply;
    send.setData(transaction->getDataParcel().data(), transaction->getDataParcel().dataSize());
    android::status_t status = binder->remoteBinder()->transact(transaction->getCode(), send,
                                                                &reply, transaction->getFlags());
    if (status != transaction->getReturnedStatus()) {
      std::cout << "Failure: Expected status " << transaction->getReturnedStatus()
                << " but received status " << status << "\n\n";
      failureCount++;
    } else {
      std::cout << "Transaction replayed correctly."
                << "\n\n";
    }
    i++;
  }
  std::cout << i << " transactions replayed.\n";
  if (failureCount > 0) {
    std::cout << failureCount << " transactions had unexpected status. See logs for details.\n";
    return android::UNKNOWN_ERROR;
  } else {
    return NO_ERROR;
  }
}

status_t listAvailableInterfaces(int, char**) {
  auto& analyzers = Analyzer::getAnalyzers();
  std::cout << "Available Interfaces (" << analyzers.size() << "):\n";
  for (auto a = analyzers.begin(); a != analyzers.end(); a++) {
    std::cout << "  " << a->second->getInterfaceName() << '\n';
  }
  return NO_ERROR;
}

struct AnalyzerCommand {
  std::function<status_t(int, char*[])> command;
  std::string overview;
  std::string compactArguments;
  std::string helpDetail;
};

status_t helpCommandEntryPoint(int argc, char* argv[]);

const AnalyzerCommand helpCommand = {helpCommandEntryPoint, "Show help information.", "<command>",
                                     ""};

const AnalyzerCommand listCommand = {listAvailableInterfaces,
                                     "Prints a list of available interfaces.", "", ""};

status_t startRecordingAllBinders() {
  auto services = android::defaultServiceManager()->listServices();
  for (auto service : services) {
    std::string serviceName = String8(service.c_str()).c_str();
    // Print failed service name. Don't exit early because it would leave the previous successful
    // services recording.
    if (status_t result = startRecording(serviceName); result != NO_ERROR) {
      std::cout << "Failed to start binder recording on service : " << service << std::endl;
    }
  }

  return NO_ERROR;
}

status_t startCommandEntryPoint(int argc, char* argv[]) {
  if (argc != 3) {
    helpCommandEntryPoint(argc, argv);
    return android::BAD_VALUE;
  }

  string startOption = argv[2];
  if (startOption == "--all") {
    return startRecordingAllBinders();
  }
  return startRecording(startOption);
}

const AnalyzerCommand startCommand = {startCommandEntryPoint,
                                      "Start recording Binder transactions from a given service. "
                                      "Use --all to start recoding all binders.",
                                      "<service>, --all",
                                      "  <service>\tService to record. See 'dumpsys -l'"};

status_t stopRecordingAllBinders() {
  auto services = android::defaultServiceManager()->listServices();
  for (auto service : services) {
    std::string serviceName = String8(service.c_str()).c_str();
    // Print failed service name. Don't exit early because it would leave the other recordings on.
    if (status_t result = stopRecording(serviceName); result != NO_ERROR) {
      std::cout << "Failed to stop binder recording on service : " << service << std::endl;
    }
  }

  return NO_ERROR;
}

status_t stopCommandEntryPoint(int argc, char* argv[]) {
  if (argc != 3) {
    helpCommandEntryPoint(argc, argv);
    return android::BAD_VALUE;
  }
  string stopOption = argv[2];
  if (stopOption == "--all") {
    stopRecordingAllBinders();
  }

  return stopRecording(stopOption);
}

const AnalyzerCommand stopCommand = {
    stopCommandEntryPoint,
    "Stops recording Binder transactions from a given process. (See 'start') Use --all to stop "
    "recoding all binders",
    "<service>, --all",
    "  <service>\tService to stop recording; <service> argument to previous 'start' command."};

status_t inspectCommandEntryPoint(int argc, char* argv[]) {
  if (argc != 3) {
    helpCommandEntryPoint(argc, argv);
    return android::BAD_VALUE;
  }
  std::string path = kStandardRecordingPath + string(argv[2]);

  return inspectRecording(path);
}

const AnalyzerCommand inspectCommand = {
    inspectCommandEntryPoint,
    "Writes the binder transactions in <file-name> to stdout in a human-friendly format.",
    "<file-name>",
    "  <file-name>\tA recording in /data/local/recordings/, and the name of the service"};

status_t listenCommandEntryPoint(int argc, char* argv[]) {
  if (argc != 3) {
    helpCommandEntryPoint(argc, argv);
    return android::BAD_VALUE;
  }

  string serviceName = argv[2];
  if (status_t startErr = startRecording(serviceName); startErr != NO_ERROR) {
    return startErr;
  }

  status_t listenStatus = listenToFile(serviceName);

  if (status_t stopErr = stopRecording(serviceName); stopErr != NO_ERROR) {
    return stopErr;
  }

  return listenStatus;
}

const AnalyzerCommand listenCommand = {
    listenCommandEntryPoint,
    "Starts recording binder transactions in <service> and writes transactions to "
    "stdout.",
    "<service>", "  <service>\t?\n"};

int replayFunction(int argc, char* argv[]) {
  if (argc != 4) {
    return helpCommandEntryPoint(argc, argv);
  }

  sp<IBinder> binder = android::defaultServiceManager()->checkService(String16(argv[2]));
  std::string path = kStandardRecordingPath + string(argv[3]);

  return replayFile(binder, path);
}

const AnalyzerCommand replayCommand = {
    replayFunction, "No overview", "<service> <file-name>",
    "  <service>\t?\n"
    "  <file-name>\tThe name of a file in /data/local/recordings/"};

auto& commands = *new std::map<std::string, AnalyzerCommand>{
    {"start", startCommand},   {"stop", stopCommand},     {"inspect", inspectCommand},
    {"listen", listenCommand}, {"replay", replayCommand}, {"help", helpCommand}};

void printGeneralHelp(std::string& toolName) {
  std::cout << "USAGE: " << toolName << " <command> [<args>]\n\n";
  std::cout << "COMMANDS:\n";
  // Display overview this many characters from the start of a line.
  // Subtract the length of the command name to calculate padding.
  const size_t commandOverviewDisplayAlignment = 12;
  for (const auto& command : commands) {
    if (command.first == "help") {
      continue;
    }
    std::cout << "  " << command.first
              << std::string(commandOverviewDisplayAlignment - command.first.length(), ' ')
              << command.second.overview << "\n";
  }
  std::cout << "\n  See '" << toolName << " help <command>' for detailed help.\n";
}

status_t helpCommandEntryPoint(int argc, char* argv[]) {
  std::string toolName = argv[0];

  if (argc < 2) {
    printGeneralHelp(toolName);
    return 0;
  }

  std::string commandName = argv[1];

  if (commandName == "help") {
    if (argc < 3) {
      printGeneralHelp(toolName);
      return 0;
    }
    commandName = argv[2];
  } else {
    commandName = argv[1];
  }

  auto command = commands.find(commandName);
  if (command == commands.end()) {
    std::cout << "Unrecognized command: " << commandName << "\n";
    printGeneralHelp(toolName);
    return -1;
  }

  std::cout << "OVERVIEW: " << command->second.overview << "\n\n";
  std::cout << "USAGE: " << toolName << " " << commandName << " "
            << command->second.compactArguments << "\n\n";
  std::cout << "ARGUMENTS:\n" << command->second.helpDetail << "\n";

  return 0;
}

}  // namespace

int main(int argc, char* argv[]) {
  std::string toolName = argv[0];

  auto& analyzers = Analyzer::getAnalyzers();
  if (analyzers.size() >= 1) {
    commands["list"] = listCommand;
  }

  if (argc < 2 ||
      (argc >= 2 && ((strcmp(argv[1], "--help") == 0) || (strcmp(argv[1], "-h") == 0)))) {
    // General help
    printGeneralHelp(toolName);
    return 0;
  }

  auto command = commands.find(argv[1]);
  if (command == commands.end()) {
    std::cout << "Unrecognized command: " << argv[1] << "\n";
    printGeneralHelp(toolName);
    return -1;
  }

  return command->second.command(argc, argv);
}
