/**
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "NN_RAND_MODEL"

#include <android-base/logging.h>
#include <jni.h>

#include <algorithm>
#include <fstream>
#include <memory>
#include <optional>
#include <random>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include "GeneratedTestUtils.h"
#include "fuzzing/OperationManager.h"
#include "fuzzing/RandomGraphGenerator.h"
#include "fuzzing/RandomGraphGeneratorUtils.h"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
  android::base::InitLogging(nullptr, android::base::LogdLogger());
  android::base::SetMinimumLogSeverity(android::base::INFO);
  return JNI_VERSION_1_6;
}

enum RandomModelExecutionResult {
  kSuccess = 0,
  kFailedCompilation,
  kFailedExecution,
  kFailedOtherNnApiCall,
  // The following conditions are for internal retry
  kInvalidModelGenerated,
  kUnsupportedModelGenerated
};

class FuzzerLogRAII {
 public:
  FuzzerLogRAII(const std::string& nnapiLogPath) {
    using android::nn::fuzzing_test::alignedString;
    using android::nn::fuzzing_test::Logger;
    using android::nn::fuzzing_test::LoggerStream;

    NN_FUZZER_LOG_WRITE_FATAL_TO_SYSLOG(LOG_TAG);

    mFuzzerLogOpen = false;
    if (!nnapiLogPath.empty()) {
      // Checking if we can write to target file
      std::ofstream os;
      os.open(nnapiLogPath);

      if (os.fail()) {
        LOG(ERROR) << "Opening file " << nnapiLogPath << " failed";
      } else {
        NN_FUZZER_LOG_INIT(nnapiLogPath);
        LOG(INFO) << "Logging NNAPI to file " << nnapiLogPath;
        mFuzzerLogOpen = true;
      }
    }
  }
  ~FuzzerLogRAII() {
    if (mFuzzerLogOpen) {
      using android::nn::fuzzing_test::alignedString;
      using android::nn::fuzzing_test::Logger;
      using android::nn::fuzzing_test::LoggerStream;

      NN_FUZZER_LOG_CLOSE;
    }
  }

 private:
  bool mFuzzerLogOpen;
};

std::vector<test_helper::TestOperationType> getOperationsInModel(
    const test_helper::TestModel& testModel) {
  std::vector<test_helper::TestOperationType> result;
  testModel.forEachSubgraph(
      [&result](const test_helper::TestSubgraph& subgraph) {
        for (const auto& operation : subgraph.operations) {
          result.push_back(operation.type);
        }
      });

  return result;
}

const ANeuralNetworksDevice* findDeviceByName(const char* deviceName) {
  if (!deviceName) return nullptr;

  std::string deviceNameStr(deviceName);
  uint32_t numDevices = 0;
  ANeuralNetworks_getDeviceCount(&numDevices);

  for (uint32_t i = 0; i < numDevices; i++) {
    ANeuralNetworksDevice* device = nullptr;
    const char* buffer = nullptr;
    int getDeviceResult = ANeuralNetworks_getDevice(i, &device);
    if (getDeviceResult != ANEURALNETWORKS_NO_ERROR) {
      LOG(ERROR) << "Unable to get NNAPI device " << i << ": "
                 << getDeviceResult;
      return nullptr;
    }

    int getDeviceNameResult = ANeuralNetworksDevice_getName(device, &buffer);
    if (getDeviceNameResult != ANEURALNETWORKS_NO_ERROR) {
      LOG(ERROR) << "Unable to get name of NNAPI device " << i << ": "
                 << getDeviceNameResult;
      return nullptr;
    }

    if (deviceNameStr == buffer) {
      return device;
    }
  }

  LOG(ERROR) << "No device with name " << deviceNameStr;
  return nullptr;
}

const ANeuralNetworksDevice* getNnApiReferenceDevice() {
  return findDeviceByName("nnapi-reference");
}

class RandomGraphGenerator {
 public:
  RandomGraphGenerator(const ANeuralNetworksDevice* device,
                       const std::string& deviceName,
                       const std::string& testName, uint32_t numOperations,
                       uint32_t dimensionRange, std::string nnapiLogPath,
                       std::string failedModelDumpPath)
      : mTestName(testName),
        mDevice(device),
        mDeviceName(deviceName),
        mNnApiReference(getNnApiReferenceDevice()),
        mSupportedOpsFilter(),
        mNumOperations(numOperations),
        mDimensionRange(dimensionRange),
        nnapiFuzzerLogRAII(nnapiLogPath),
        mFailedModelDumpPath(failedModelDumpPath) {}

  RandomModelExecutionResult init() {
    // Limiting the ops in the generator to a subset we know the target device
    // supports to avoid failing the test because we are unable to find a
    // suitable model to compile.
    RandomModelExecutionResult filterInitResult;
    filterInitResult =
        HalVersionsSupportedByDevice(&mSupportedOpsFilter.versions);
    if (filterInitResult != kSuccess) return filterInitResult;

    filterInitResult =
        OperandTypesSupportedByDevice(&mSupportedOpsFilter.dataTypes);
    if (filterInitResult != kSuccess) return filterInitResult;

    return OperationsSupportedByDevice(mSupportedOpsFilter,
                                       &mSupportedOpsFilter.opcodes);
  }

  RandomModelExecutionResult runRandomModel(bool compilationOnly) {
    using android::nn::generated_tests::createModel;
    using android::nn::generated_tests::createRequest;
    using android::nn::generated_tests::GeneratedModel;
    using android::nn::test_wrapper::Compilation;
    using android::nn::test_wrapper::Execution;
    using android::nn::wrapper::Result;

    std::optional<test_helper::TestModel> testModel =
        createRandomModel(mSupportedOpsFilter);
    if (!testModel) {
      LOG(ERROR) << mTestName << ": No model generated";
      return kInvalidModelGenerated;
    }

    GeneratedModel model;
    createModel(*testModel, &model);
    if (!model.isValid()) {
      LOG(ERROR) << mTestName << ": Randomly generated model is not valid";
      return kInvalidModelGenerated;
    }
    auto modelFinishResult = model.finish();
    if (modelFinishResult != Result::NO_ERROR) {
      LOG(ERROR) << mTestName << ": Failed to finish model, result is "
                 << static_cast<int>(modelFinishResult);
      return kInvalidModelGenerated;
    }

    bool fullySupportedModel = false;
    if (mDevice) {
      std::unique_ptr<bool[]> opsSupportedFlags =
          std::make_unique<bool[]>(mNumOperations);
      std::fill(opsSupportedFlags.get(),
                opsSupportedFlags.get() + mNumOperations, false);
      // Check if the device fully supports the graph.
      int supportedOpResult =
          ANeuralNetworksModel_getSupportedOperationsForDevices(
              model.getHandle(), &mDevice, 1, opsSupportedFlags.get());
      if (supportedOpResult != ANEURALNETWORKS_NO_ERROR) {
        return kFailedOtherNnApiCall;
      }

      // accepting the model even if partially supported since we found that it
      // is extremely difficult to have fully supported models.
      // We could consider a minimum number (or percentage of total number) of
      // operations to be supported to consider the model  acceptable. For the
      // moment we just accept any model that has any supported op.
      bool supported = std::any_of(opsSupportedFlags.get(),
                                   opsSupportedFlags.get() + mNumOperations,
                                   [](bool v) { return v; });
      if (!supported) {
        return kUnsupportedModelGenerated;
      }

      fullySupportedModel = std::all_of(
          opsSupportedFlags.get(), opsSupportedFlags.get() + mNumOperations,
          [](bool v) { return v; });
    }

    std::vector<const ANeuralNetworksDevice*> devices;
    if (mDevice) {
      devices.push_back(mDevice);
      if (!fullySupportedModel) {
        // If model is not fully supported we allow NNAPI to use reference
        // implementation. This is to avoid having this test constantly
        // nullified by the inability of finding a fully supported model.
        LOG(VERBOSE) << "Allowing model to be partially executed on NNAPI "
                        "reference device";
        devices.push_back(mNnApiReference);
      }
    }

    auto [compilationResult, compilation] = CreateCompilation(model, devices);
    if (compilationResult != Result::NO_ERROR) {
      LOG(WARNING) << mTestName
                   << ": Compilation preparation failed with result "
                   << static_cast<int>(compilationResult);

      dumpModel(*testModel);
      return kFailedCompilation;
    }
    compilationResult = compilation.finish();
    if (compilationResult != Result::NO_ERROR) {
      LOG(WARNING) << mTestName << ": Compilation failed with result "
                   << static_cast<int>(compilationResult);

      dumpModel(*testModel);
      return kFailedCompilation;
    }

    if (!compilationOnly) {
      Execution execution(&compilation);
      std::vector<test_helper::TestBuffer> outputs;
      createRequest(*testModel, &execution, &outputs);

      // Compute result.
      Result executeReturn = execution.compute();
      if (executeReturn != Result::NO_ERROR) {
        LOG(WARNING) << mTestName << ": Execution failed with result "
                     << static_cast<int>(executeReturn);

        dumpModel(*testModel);
        return kFailedExecution;
      }
    }

    return kSuccess;
  }

  const std::string mTestName;

 private:
  android::nn::fuzzing_test::RandomGraph mRandomGraph;
  std::random_device mSeedGenerator;
  const ANeuralNetworksDevice* mDevice;
  // empty string if mDevice is null
  const std::string mDeviceName;
  const ANeuralNetworksDevice* mNnApiReference;
  android::nn::fuzzing_test::OperationFilter mSupportedOpsFilter;
  const uint32_t mNumOperations;
  const uint32_t mDimensionRange;
  FuzzerLogRAII nnapiFuzzerLogRAII;
  const std::string mFailedModelDumpPath;

  std::optional<test_helper::TestModel> createRandomModel(
      const android::nn::fuzzing_test::OperationFilter& opFilter) {
    android::nn::fuzzing_test::OperationManager::get()->applyFilter(opFilter);

    auto seed = mSeedGenerator();
    if (!mRandomGraph.generate(seed, mNumOperations, mDimensionRange)) {
      return std::nullopt;
    }

    return {mRandomGraph.createTestModel()};
  }

  RandomModelExecutionResult HalVersionsSupportedByDevice(
      std::vector<test_helper::TestHalVersion>* result) {
    if (!mDevice) {
      return kSuccess;
    }

    int64_t featureLevel;
    auto getDeviceFeatureLevelResult =
        ANeuralNetworksDevice_getFeatureLevel(mDevice, &featureLevel);
    if (getDeviceFeatureLevelResult != ANEURALNETWORKS_NO_ERROR) {
      LOG(ERROR) << mTestName << ": Unable to query device feature level";
      return kFailedOtherNnApiCall;
    }

    if (featureLevel == 27) *result = {test_helper::TestHalVersion::V1_0};
    if (featureLevel == 28) *result = {test_helper::TestHalVersion::V1_1};
    if (featureLevel == 29) *result = {test_helper::TestHalVersion::V1_2};

    return kSuccess;
  }

  RandomModelExecutionResult OperandTypesSupportedByDevice(
      std::vector<test_helper::TestOperandType>* result) {
    if (!mDevice) {
      return kSuccess;
    }

    int32_t deviceType;
    auto getDeviceTypeResult =
        ANeuralNetworksDevice_getType(mDevice, &deviceType);
    if (getDeviceTypeResult != ANEURALNETWORKS_NO_ERROR) {
      LOG(ERROR) << mTestName << ": Unable to query device type";
      return kFailedOtherNnApiCall;
    }
    using test_helper::TestOperandType;
    switch (deviceType) {
      case ANEURALNETWORKS_DEVICE_GPU:
        // No quantized types
        *result = {
            TestOperandType::FLOAT32,        TestOperandType::INT32,
            TestOperandType::UINT32,         TestOperandType::TENSOR_FLOAT32,
            TestOperandType::TENSOR_INT32,   TestOperandType::BOOL,
            TestOperandType::TENSOR_FLOAT16, TestOperandType::TENSOR_BOOL8,
            TestOperandType::FLOAT16};
        break;
      case ANEURALNETWORKS_DEVICE_CPU:
      case ANEURALNETWORKS_DEVICE_ACCELERATOR:
        result->clear();  // no filter
        break;
      case ANEURALNETWORKS_DEVICE_UNKNOWN:
      case ANEURALNETWORKS_DEVICE_OTHER:
        if (mDeviceName.find("dsp") != std::string::npos) {
          *result = {TestOperandType::INT32,
                     TestOperandType::UINT32,
                     TestOperandType::TENSOR_INT32,
                     TestOperandType::BOOL,
                     TestOperandType::TENSOR_BOOL8,
                     TestOperandType::TENSOR_QUANT8_ASYMM,
                     TestOperandType::TENSOR_QUANT16_SYMM,
                     TestOperandType::TENSOR_QUANT8_SYMM_PER_CHANNEL,
                     TestOperandType::TENSOR_QUANT16_ASYMM,
                     TestOperandType::TENSOR_QUANT8_SYMM,
                     TestOperandType::TENSOR_QUANT8_ASYMM_SIGNED};
          break;
        }
        FALLTHROUGH_INTENDED;
      default:
        result->clear();  // no filter
    }
    return kSuccess;
  }

  /// Finds some operations supported by the device
  RandomModelExecutionResult OperationsSupportedByDevice(
      const android::nn::fuzzing_test::OperationFilter& basicFilter,
      std::vector<test_helper::TestOperationType>* result) {
    if (!mDevice) {
      return kSuccess;
    }

    constexpr int kNumOfAttempts = 50;
    std::set<test_helper::TestOperationType> supportedOps;
    for (int i = 0; i < kNumOfAttempts; i++) {
      std::optional<test_helper::TestModel> testModel =
          createRandomModel(basicFilter);
      if (!testModel) {
        LOG(ERROR)
            << mTestName
            << ": Unable to generate a model trying to understand the ops "
               "supported by target device";
        continue;
      }

      android::nn::generated_tests::GeneratedModel model;
      createModel(*testModel, &model);
      if (!model.isValid()) {
        LOG(WARNING) << mTestName << ": Randomly generated model is not valid";
        continue;
      }
      auto modelFinishResult = model.finish();
      if (modelFinishResult != android::nn::wrapper::Result::NO_ERROR) {
        LOG(WARNING) << "Model::finish call failed, result is "
                     << static_cast<int>(modelFinishResult);
        continue;
      }

      std::unique_ptr<bool[]> opsSupportedFlags =
          std::make_unique<bool[]>(mNumOperations);
      std::fill(opsSupportedFlags.get(),
                opsSupportedFlags.get() + mNumOperations, false);

      // Check if the device fully supports the graph.
      int supportedOpResult =
          ANeuralNetworksModel_getSupportedOperationsForDevices(
              model.getHandle(), &mDevice, 1, opsSupportedFlags.get());
      if (supportedOpResult != ANEURALNETWORKS_NO_ERROR) {
        return kFailedOtherNnApiCall;
      }

      std::vector<test_helper::TestOperationType> opsInModel =
          getOperationsInModel(*testModel);
      for (int opIndex = 0; opIndex < mNumOperations; opIndex++) {
        test_helper::TestOperationType currOp = opsInModel[opIndex];
        if (opsSupportedFlags[opIndex]) {
          supportedOps.insert(currOp);
        }
      }
    }
    std::copy(supportedOps.begin(), supportedOps.end(),
              std::back_inserter(*result));

    if (result->empty()) {
      LOG(WARNING)
          << mTestName
          << ": Could not find any operation supported by target device."
          << " Returning no filter.";
    } else {
      LOG(INFO) << mTestName << ": Filtering to " << result->size()
                << " supported operations";
    }

    return kSuccess;
  }

  void dumpModel(const test_helper::TestModel& testModel) {
    if (mFailedModelDumpPath.empty()) return;

    LOG(INFO) << mTestName << ": Dumping model failing tests to "
              << mFailedModelDumpPath;

    std::ofstream os(mFailedModelDumpPath);
    ASSERT_TRUE(os.is_open());
    os << "# Generated from " << mTestName << ". Do not edit.\n\n";
    test_helper::SpecDumper dumper(testModel, os);
    dumper.dumpTestModel();
  }

  std::pair<android::nn::wrapper::Result,
            android::nn::test_wrapper::Compilation>
  CreateCompilation(const android::nn::generated_tests::GeneratedModel& model,
                    const std::vector<const ANeuralNetworksDevice*>& devices) {
    using android::nn::test_wrapper::Compilation;
    if (!devices.empty())
      return Compilation::createForDevices(&model, devices);
    else
      return {android::nn::wrapper::Result::NO_ERROR, Compilation(&model)};
  }
};

extern "C" JNIEXPORT jint JNICALL
Java_com_android_nn_crashtest_core_test_RandomGraphTest_runRandomModel(
    JNIEnv* env, jclass /* static method */, jlong _generatorHandle,
    jboolean _compilationOnly, jlong _maxModelSearchTimeSeconds) {
  RandomGraphGenerator* graphGenerator =
      reinterpret_cast<RandomGraphGenerator*>(_generatorHandle);

  std::time_t startTime = std::time(nullptr);

  int result = kSuccess;
  int modelSearchAttempt = 0;
  while (std::difftime(std::time(nullptr), startTime) <
         _maxModelSearchTimeSeconds) {
    modelSearchAttempt++;

    result = graphGenerator->runRandomModel(_compilationOnly);

    // if by chance we generated an invalid model or a model that couldn't run
    // on the target accelerator we will try again.
    if (result != kInvalidModelGenerated &&
        result != kUnsupportedModelGenerated) {
      break;
    }
  }

  if (result == kInvalidModelGenerated ||
      result == kUnsupportedModelGenerated) {
    LOG(WARNING) << graphGenerator->mTestName
                 << ": Max time to search for a model of "
                 << static_cast<long>(_maxModelSearchTimeSeconds)
                 << "seconds reached. Aborting test at attempt "
                 << modelSearchAttempt;
  }

  return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_android_nn_crashtest_core_test_RandomGraphTest_createRandomGraphGenerator(
    JNIEnv* env, jclass /* static method */, jstring _nnApiDeviceName,
    jint _numOperations, jint _dimensionRange, jstring _testName,
    jstring _nnapiLogPath, jstring _failedModelDumpPath) {
  const char* nnApiDeviceName =
      _nnApiDeviceName ? env->GetStringUTFChars(_nnApiDeviceName, nullptr)
                       : nullptr;

  std::string nnApiDeviceNameStr{nnApiDeviceName ? nnApiDeviceName : ""};
  const ANeuralNetworksDevice* device = nullptr;
  if (nnApiDeviceName) {
    device = findDeviceByName(nnApiDeviceName);
    if (!device) {
      LOG(ERROR) << ": Unable to find accelerator " << nnApiDeviceName;
      env->ReleaseStringUTFChars(_nnApiDeviceName, nnApiDeviceName);
      return reinterpret_cast<jlong>(nullptr);
    }
    env->ReleaseStringUTFChars(_nnApiDeviceName, nnApiDeviceName);
  }

  std::string testName{"no-test-name"};
  if (_testName) {
    const char* testNameBuf = env->GetStringUTFChars(_testName, nullptr);
    testName = testNameBuf;
    env->ReleaseStringUTFChars(_testName, testNameBuf);
  }

  std::string nnapiLogPath;
  if (_nnapiLogPath) {
    const char* nnapiLogPathTmp =
        env->GetStringUTFChars(_nnapiLogPath, nullptr);
    nnapiLogPath = nnapiLogPathTmp;
    env->ReleaseStringUTFChars(_nnapiLogPath, nnapiLogPathTmp);
  }

  std::string failedModelDumpPath;
  if (_failedModelDumpPath) {
    const char* failedModelDumpPathTmp =
        env->GetStringUTFChars(_failedModelDumpPath, nullptr);
    failedModelDumpPath = failedModelDumpPathTmp;
    env->ReleaseStringUTFChars(_failedModelDumpPath, failedModelDumpPathTmp);
  }

  uint32_t numOperations = static_cast<uint32_t>(_numOperations);
  uint32_t dimensionRange = static_cast<uint32_t>(_dimensionRange);

  RandomGraphGenerator* result = new RandomGraphGenerator(
      device, nnApiDeviceNameStr, testName, numOperations, dimensionRange,
      nnapiLogPath, failedModelDumpPath);

  if (result->init() != kSuccess) {
    delete result;
    return reinterpret_cast<jlong>(nullptr);
  }

  return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_android_nn_crashtest_core_test_RandomGraphTest_destroyRandomGraphGenerator(
    JNIEnv* env, jclass /* static method */, jlong generatorHandle) {
  RandomGraphGenerator* graphGenerator =
      reinterpret_cast<RandomGraphGenerator*>(generatorHandle);
  delete graphGenerator;
}
