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
#include <android/log.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <fstream>
#include <memory>
#include <string>
#include <sstream>
#include <thread>
#include <vector>

#include "run_tflite.h"
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/nnapi/nnapi_implementation.h"

#define LOG_TAG "NN_MPROC_STRESS"

constexpr int kInvalidArguments = -1;

enum Arguments : int {
  kArgModelPath = 1,
  kArgInputDataPath,
  kArgInputShape,
  kArgInputElementSize,
  kArgProcessCount,
  kArgThreadCount,
  kArgDurationSeconds,
  kArgTestName,
  kArgJustCompileModel,
  kArgProcessFailureRatePercent,
  kArgNnApiDeviceName,
  kArgMmapModel
};

constexpr int kMandatoryArgsCount = 9;

const char* kUsage =
    R"""(%s modelFileName inputDataFile inputShape inputElementByteSize procCount threadCount durationInSeconds testName justCompileModel [processFailureRate] [nnapiDeviceName] [mmapModel]

                          where:
                              inputShape comma separated list of integers (e.g. '1,224,224,3')
                              justCompileModel: true/false)
                              processFailureRate: 0 to 100 percent probability of having one of the client processes failing. Defaults to 0.)
                              mmapModel: true/false select if the TFLite model should be memory mapped to the given file or created from program memory)""";

bool canReadInputFile(const char* path) {
  std::string modelFileName(path);
  std::ifstream fstream(modelFileName);
  std::stringstream readBuffer;
  readBuffer << fstream.rdbuf();
  return fstream.good();
}

bool readInputData(const char* inputDataFileName, std::vector<int> input_shape,
                   int inputElementSize,
                   std::vector<InferenceInOutSequence>* result) {
  int inputElementCount = 1;
  std::for_each(
      input_shape.begin(), input_shape.end(),
      [&inputElementCount](int dimSize) { inputElementCount *= dimSize; });
  size_t inputDataSizeBytes = inputElementCount * inputElementSize;

  std::ifstream dataFile;
  dataFile.open(inputDataFileName);
  if (!dataFile) {
    return false;
  }

  std::function<bool(uint8_t*, size_t)> failToGenerateData =
      [](uint8_t*, size_t) { return false; };
  while (!dataFile.eof()) {
    std::unique_ptr<uint8_t[]> dataBuffer =
        std::make_unique<uint8_t[]>(inputDataSizeBytes);
    if (!dataFile.read(reinterpret_cast<char*>(dataBuffer.get()),
                       inputDataSizeBytes)) {
      break;
    }
    InferenceInOut entry{
        dataBuffer.release(), inputDataSizeBytes, {}, failToGenerateData};
    result->push_back({entry});
  }

  return result;
}

bool runModel(const char* modelFileName,
              const std::vector<InferenceInOutSequence>& data,
              int durationSeconds, const std::string& nnApiDeviceName,
              bool justCompileModel, bool mmapModel) {
  if (justCompileModel) {
    std::time_t startTime = std::time(nullptr);
    while (std::difftime(std::time(nullptr), startTime) < durationSeconds) {
      int nnapiErrno = 0;
      std::unique_ptr<BenchmarkModel> model(BenchmarkModel::create(
          modelFileName, /*useNnApi=*/true,
          /*enableIntermediateTensorsDump=*/false,
          &nnapiErrno,
          nnApiDeviceName.empty() ? nullptr : nnApiDeviceName.c_str(), mmapModel,
          /*nnapi_cache_dir=*/nullptr));

      if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error creating model");
        return false;
      }

      // sleeping from 300ms to 800ms
      constexpr int kMinPauseMs = 300;
      constexpr int kMaxPauseMs = 800;
      int sleepForMs = kMinPauseMs + (drand48() * (kMaxPauseMs - kMinPauseMs));
      usleep(sleepForMs * 1000);
    }

    return true;
  } else {
    int nnapiErrno = 0;
    std::unique_ptr<BenchmarkModel> model(BenchmarkModel::create(
        modelFileName, /*useNnApi=*/true,
        /*enableIntermediateTensorsDump=*/false,
        &nnapiErrno,
        nnApiDeviceName.empty() ? nullptr : nnApiDeviceName.c_str(), mmapModel,
        /*nnapi_cache_dir=*/nullptr));

    if (!model) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error creating model");
      return false;
    }

    std::vector<InferenceResult> result;
    constexpr int flags =
        FLAG_DISCARD_INFERENCE_OUTPUT | FLAG_IGNORE_GOLDEN_OUTPUT;
    return model->benchmark(data, std::numeric_limits<int>::max(),
                            durationSeconds, flags, &result);
  }
}

bool getBooleanArg(int argc, char* argv[], int argIndex, bool defaultValue) {
    if (argc > argIndex) {
        std::string argAsString(argv[argIndex]);
        return argAsString == "true";
    } else {
        return defaultValue;
    }
}

int getIntArg(int argc, char* argv[], int argIndex, int defaultValue) {
    if (argc > argIndex) {
        return std::atoi(argv[argIndex]);
    } else {
        return defaultValue;
    }
}

int main(int argc, char* argv[]) {
  if (argc < kMandatoryArgsCount) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, kUsage, kMandatoryArgsCount,
                        argc, argv[0]);
    return kInvalidArguments;
  }

  const char* modelFileName = argv[kArgModelPath];
  const char* inputDataFileName = argv[kArgInputDataPath];
  const char* testName = argv[kArgTestName];
  std::string nnApiDeviceName{
      argc > kArgNnApiDeviceName ? argv[kArgNnApiDeviceName] : ""};
  int numProcesses = getIntArg(argc, argv, kArgProcessCount, 0);
  int numThreads = getIntArg(argc, argv, kArgThreadCount, 0);
  int durationSeconds = getIntArg(argc, argv, kArgDurationSeconds, 0);
  bool justCompileModel =
      getBooleanArg(argc, argv, kArgJustCompileModel, false);
  std::vector<int> inputShape;
  std::istringstream inputShapeStream(argv[kArgInputShape]);
  std::string currSizeToken;
  while (std::getline(inputShapeStream, currSizeToken, ',')) {
    inputShape.push_back(std::stoi(currSizeToken));
  }
  int inputElementSize = getIntArg(argc, argv, kArgInputElementSize, 0);
  int processFailureRate = getIntArg(argc, argv, kArgProcessFailureRatePercent, 0);


  bool mmapModel = getBooleanArg(argc, argv, kArgMmapModel, true);

  // Validate params

  if (!canReadInputFile(modelFileName)) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "Error reading model file '%s'", modelFileName);
    return kInvalidArguments;
  }

  std::vector<InferenceInOutSequence> inputData;
  if (!justCompileModel) {
    if (!readInputData(inputDataFileName, inputShape, inputElementSize,
                       &inputData)) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                          "Error reading input data file '%s'",
                          inputDataFileName);
      return kInvalidArguments;
    }
  }

  if (numProcesses <= 0 || numThreads <= 0 || durationSeconds <= 0 ||
      inputElementSize <= 0) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Invalid arguments");
    return kInvalidArguments;
  }

  __android_log_print(
      ANDROID_LOG_INFO, LOG_TAG,
      "Test '%s': running %s of model at path '%s' with input shape [%s] "
      "(element data size %d),"
      " %d processes of %d threads each using device '%s' for %d seconds",
      testName, justCompileModel ? "compilation only" : "full inference",
      modelFileName, argv[kArgInputShape], inputElementSize, numProcesses,
      numThreads,
      nnApiDeviceName.empty() ? "no-device" : nnApiDeviceName.c_str(),
      durationSeconds);

  srand48(time(NULL) + getpid());

  std::vector<pid_t> children;
  pid_t pid = 1;
  bool forkSucceeded = true;
  bool isSubprocess = false;
  for (int i = 0; i < numProcesses; i++) {
    if (pid != 0) {
      pid = fork();
      if (pid > 0) {
        children.push_back(pid);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Forked child pid %d",
                            pid);
      } else if (pid < 0) {
        forkSucceeded = false;
        break;
      } else {
        isSubprocess = true;
      }
    }
  }

  if (isSubprocess) {
    __android_log_print(
        ANDROID_LOG_INFO, LOG_TAG,
        "%s model '%s': for %d seconds on device '%s' on %d threads",
        justCompileModel ? "Compiling" : "Running", modelFileName,
        durationSeconds,
        nnApiDeviceName.empty() ? "no-device" : nnApiDeviceName.c_str(),
        numThreads);

    bool shouldKillProcess = (drand48() * 100) <= (double)processFailureRate;

    if (shouldKillProcess) {
      float killAfter = durationSeconds * drand48();
      __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                          "This process will be killed in %f seconds",
                          killAfter);
      std::thread killer = std::thread([killAfter]() {
        usleep(killAfter * 1000.0 * 1000);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                            "Killing current test process.");
        kill(getpid(), 9);
      });
      killer.detach();
    }

    std::vector<std::thread> threads;
    threads.reserve(numThreads);
    for (int i = 0; i < numThreads; i++) {
      threads.push_back(std::thread([&]() {
        runModel(modelFileName, inputData, durationSeconds, nnApiDeviceName,
                 justCompileModel, mmapModel);
      }));
    }
    std::for_each(threads.begin(), threads.end(),
                  [](std::thread& t) { t.join(); });
  } else {
    for (auto pid : children) {
      waitpid(pid, nullptr, 0);
    }
  }

  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Test '%s': %s returning ",
                      testName, isSubprocess ? "Test process" : "Main process");

  return 0;
}
