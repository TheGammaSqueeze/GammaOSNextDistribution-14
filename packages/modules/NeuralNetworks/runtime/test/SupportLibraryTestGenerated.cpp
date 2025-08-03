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

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <ftw.h>
#include <gtest/gtest.h>
#include <libgen.h>
#include <stdlib.h>
#include <unistd.h>

#include <algorithm>
#include <cassert>
#include <cmath>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <random>
#include <set>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "GeneratedTestUtils.h"
#include "SupportLibraryTestUtils.h"
#include "SupportLibraryWrapper.h"
#include "TmpDirectoryUtils.h"

// Systrace is not available from CTS tests due to platform layering
// constraints. We reuse the NNTEST_ONLY_PUBLIC_API flag, as that should also be
// the case for CTS (public APIs only).
#ifndef NNTEST_ONLY_PUBLIC_API
#include <Tracing.h>
#else
#define NNTRACE_FULL_RAW(...)
#define NNTRACE_APP(...)
#define NNTRACE_APP_SWITCH(...)
#endif

const char* kQCDspLoadPathEnv = "ADSP_LIBRARY_PATH";

extern std::string SUPPORT_LIBRARY_NAME;

namespace android::nn::generated_tests {
using namespace sl_wrapper;
using namespace test_helper;

enum ComputeWithDeviceMemoriesResult {
    SKIP,
    OK,
};

class GeneratedTests : public GeneratedTestBase {
   protected:
    void SetUp() override;
    void TearDown() override;

    bool shouldSkipTest();

    ANeuralNetworksMemory* createDeviceMemoryForInput(const Compilation& compilation,
                                                      uint32_t index);
    ANeuralNetworksMemory* createDeviceMemoryForOutput(const Compilation& compilation,
                                                       uint32_t index);
    ComputeWithDeviceMemoriesResult computeWithDeviceMemories(
            const Compilation& compilation, const TestModel& testModel, Execution* execution,
            Execution::ComputeMode computeMode, Result* result, std::vector<TestBuffer>* outputs);
    bool checkSupported(const Model& model, ANeuralNetworksDevice* device);
    std::optional<Compilation> compileModel(const Model& model, ANeuralNetworksDevice* device);
    void executeWithCompilation(const Compilation& compilation, const TestModel& testModel);
    void executeOnce(const Model& model, const TestModel& testModel);
    void executeMultithreadedOwnCompilation(const Model& model, const TestModel& testModel);
    void executeMultithreadedSharedCompilation(const Model& model, const TestModel& testModel);
    // Test driver for those generated from ml/nn/runtime/test/spec
    void execute(const TestModel& testModel);

    // VNDK version of the device under test.
    static int mVndkVersion;

    std::string mCacheDir;
    std::vector<uint8_t> mToken;
    bool mTestCompilationCaching = false;
    bool mTestDynamicOutputShape = false;
    bool mExpectFailure = false;
    bool mTestQuantizationCoupling = false;
    bool mTestDeviceMemory = false;
    Execution::ComputeMode mComputeMode = Execution::getComputeMode();

    std::unique_ptr<const NnApiSupportLibrary> mNnApi;
};

int GeneratedTests::mVndkVersion = __ANDROID_API_FUTURE__;

// Tag for the dynamic output shape tests
class DynamicOutputShapeTest : public GeneratedTests {
   protected:
    DynamicOutputShapeTest() { mTestDynamicOutputShape = true; }
};

// Tag for the fenced execute tests
class FencedComputeTest : public GeneratedTests {};

// Tag for the generated validation tests
class GeneratedValidationTests : public GeneratedTests {
   protected:
    GeneratedValidationTests() { mExpectFailure = true; }
};

class QuantizationCouplingTest : public GeneratedTests {
   protected:
    QuantizationCouplingTest() { mTestQuantizationCoupling = true; }
};

class DeviceMemoryTest : public GeneratedTests {
   protected:
    DeviceMemoryTest() { mTestDeviceMemory = true; }
};

bool GeneratedTests::checkSupported(const Model& model, ANeuralNetworksDevice* device) {
    constexpr static int MAX_NUM_OPS = 256;
    std::array<bool, MAX_NUM_OPS> supportedOps;
    for (int i = 0; i < MAX_NUM_OPS; ++i) {
        supportedOps[i] = true;
    }
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksModel_getSupportedOperationsForDevices(
                      model.getHandle(), &device, /*numDevices=*/1, supportedOps.data()),
              ANEURALNETWORKS_NO_ERROR);
    const bool fullySupportedModel =
            std::all_of(supportedOps.begin(), supportedOps.end(), [](bool v) { return v; });
    return fullySupportedModel;
}

static std::vector<base::unique_fd> createCacheFds(const std::vector<std::string>& files) {
    std::vector<base::unique_fd> fds;
    fds.reserve(files.size());
    for (const auto& file : files) {
        auto fd = base::unique_fd(open(file.c_str(), O_RDWR | O_CREAT, S_IRUSR | S_IWUSR));
        if (fd.get() == -1) {
            [] { FAIL(); }();
            return {};
        }
        fds.push_back(std::move(fd));
    }
    return fds;
}

std::optional<Compilation> GeneratedTests::compileModel(const Model& model,
                                                        ANeuralNetworksDevice* device) {
    NNTRACE_APP(NNTRACE_PHASE_COMPILATION, "compileModel");

    if (mTestCompilationCaching) {
        // Compile the model twice with the same token, so that compilation caching will be
        // exercised if supported by the driver.
        // No invalid model will be passed to this branch.
        EXPECT_FALSE(mExpectFailure);

        std::string mode = ::android::base::GetProperty("debug.nn.slts.caching", "random");
        bool useSetCachingFromFds;
        if (mode == "path") {
            useSetCachingFromFds = false;
        } else if (mode == "fds") {
            useSetCachingFromFds = true;
        } else if (mode == "random") {
            std::string testName = ::testing::UnitTest::GetInstance()->current_test_info()->name();
            std::seed_seq seq(testName.begin(), testName.end());
            std::mt19937 gen(seq);
            std::bernoulli_distribution d(0.5);
            useSetCachingFromFds = d(gen);
        } else {
            [&mode] {
                FAIL() << "System property debug.nn.slts.caching should be one of \"path\", "
                          "\"fds\", or \"random\"; got \""
                       << mode << "\"";
            }();
            return {};
        }
        SCOPED_TRACE("Use setCachingFromFds = " + std::to_string(useSetCachingFromFds) + " (" +
                     mode + ")");
        std::cout << "\nUse setCachingFromFds = " << std::boolalpha << useSetCachingFromFds << " ("
                  << mode << ")" << std::endl;

        std::vector<std::string> modelCacheFilenames, dataCacheFilenames;
        if (useSetCachingFromFds) {
            uint32_t numModelCacheFiles, numDataCacheFiles;
            EXPECT_EQ(mNnApi->getFL5()->SL_ANeuralNetworksDevice_getNumberOfCacheFilesNeeded(
                              device, &numModelCacheFiles, &numDataCacheFiles),
                      ANEURALNETWORKS_NO_ERROR);
            for (uint32_t i = 0; i < numModelCacheFiles; i++) {
                modelCacheFilenames.push_back({mCacheDir + "/model" + std::to_string(i)});
            }
            for (uint32_t i = 0; i < numDataCacheFiles; i++) {
                dataCacheFilenames.push_back({mCacheDir + "/data" + std::to_string(i)});
            }
        }

        auto resultCompilation1 = Compilation::createForDevice(mNnApi.get(), &model, device);
        EXPECT_EQ(resultCompilation1.first, Result::NO_ERROR);
        auto compilation1 = std::move(resultCompilation1.second);
        if (useSetCachingFromFds) {
            auto modelCacheFds = createCacheFds(modelCacheFilenames);
            auto dataCacheFds = createCacheFds(dataCacheFilenames);
            EXPECT_EQ(compilation1.setCachingFromFds(modelCacheFds, dataCacheFds, mToken),
                      Result::NO_ERROR);
        } else {
            EXPECT_EQ(compilation1.setCaching(mCacheDir, mToken), Result::NO_ERROR);
        }
        EXPECT_EQ(compilation1.finish(), Result::NO_ERROR);

        auto resultCompilation2 = Compilation::createForDevice(mNnApi.get(), &model, device);
        EXPECT_EQ(resultCompilation2.first, Result::NO_ERROR);
        auto compilation2 = std::move(resultCompilation2.second);
        if (useSetCachingFromFds) {
            auto modelCacheFds = createCacheFds(modelCacheFilenames);
            auto dataCacheFds = createCacheFds(dataCacheFilenames);
            EXPECT_EQ(compilation2.setCachingFromFds(modelCacheFds, dataCacheFds, mToken),
                      Result::NO_ERROR);
        } else {
            EXPECT_EQ(compilation2.setCaching(mCacheDir, mToken), Result::NO_ERROR);
        }
        EXPECT_EQ(compilation2.finish(), Result::NO_ERROR);

        return compilation2;
    } else {
        auto resultCompilation = Compilation::createForDevice(mNnApi.get(), &model, device);
        EXPECT_EQ(resultCompilation.first, Result::NO_ERROR);
        auto compilation = std::move(resultCompilation.second);
        Result result = compilation.finish();

        // For valid model, we check the compilation result == NO_ERROR.
        // For invalid model, the driver may fail at compilation or execution, so any result code is
        // permitted at this point.
        if (mExpectFailure && result != Result::NO_ERROR) return std::nullopt;
        EXPECT_EQ(result, Result::NO_ERROR);
        return compilation;
    }
}

void computeWithPtrs(const TestModel& testModel, Execution* execution,
                     Execution::ComputeMode computeMode, Result* result,
                     std::vector<TestBuffer>* outputs) {
    {
        NNTRACE_APP(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "computeWithPtrs example");
        createRequest(testModel, execution, outputs);
    }
    *result = execution->compute(computeMode);
}

ANeuralNetworksMemory* GeneratedTests::createDeviceMemoryForInput(const Compilation& compilation,
                                                                  uint32_t index) {
    ANeuralNetworksMemoryDesc* desc = nullptr;
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_create(&desc), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_addInputRole(
                      desc, compilation.getHandle(), index, 1.0f),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_finish(desc), ANEURALNETWORKS_NO_ERROR);
    ANeuralNetworksMemory* memory = nullptr;
    mNnApi->getFL5()->ANeuralNetworksMemory_createFromDesc(desc, &memory);
    mNnApi->getFL5()->ANeuralNetworksMemoryDesc_free(desc);
    return memory;
}

ANeuralNetworksMemory* GeneratedTests::createDeviceMemoryForOutput(const Compilation& compilation,
                                                                   uint32_t index) {
    ANeuralNetworksMemoryDesc* desc = nullptr;
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_create(&desc), ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_addOutputRole(
                      desc, compilation.getHandle(), index, 1.0f),
              ANEURALNETWORKS_NO_ERROR);
    EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemoryDesc_finish(desc), ANEURALNETWORKS_NO_ERROR);
    ANeuralNetworksMemory* memory = nullptr;
    mNnApi->getFL5()->ANeuralNetworksMemory_createFromDesc(desc, &memory);
    mNnApi->getFL5()->ANeuralNetworksMemoryDesc_free(desc);
    return memory;
}

// Set result = Result::NO_ERROR and outputs = {} if the test should be skipped.
ComputeWithDeviceMemoriesResult GeneratedTests::computeWithDeviceMemories(
        const Compilation& compilation, const TestModel& testModel, Execution* execution,
        Execution::ComputeMode computeMode, Result* result, std::vector<TestBuffer>* outputs) {
    EXPECT_NE(execution, nullptr);
    EXPECT_NE(result, nullptr);
    EXPECT_NE(outputs, nullptr);
    outputs->clear();
    std::vector<Memory> inputMemories, outputMemories;

    {
        NNTRACE_APP(NNTRACE_PHASE_INPUTS_AND_OUTPUTS, "computeWithDeviceMemories example");
        // Model inputs.
        for (uint32_t i = 0; i < testModel.main.inputIndexes.size(); i++) {
            SCOPED_TRACE("Input index: " + std::to_string(i));
            const auto& operand = testModel.main.operands[testModel.main.inputIndexes[i]];
            // Omitted input.
            if (operand.data.size() == 0) {
                EXPECT_EQ(Result::NO_ERROR, execution->setInput(i, nullptr, 0));
                continue;
            }

            // Create device memory.
            ANeuralNetworksMemory* memory = createDeviceMemoryForInput(compilation, i);
            if (memory == nullptr) {
                return ComputeWithDeviceMemoriesResult::SKIP;
            }
            auto& wrapperMemory = inputMemories.emplace_back(Memory(mNnApi.get(), memory));

            // Copy data from TestBuffer to device memory.
            auto ashmem = TestAshmem::createFrom(mNnApi.get(), operand.data);
            EXPECT_NE(ashmem, nullptr);
            EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemory_copy(ashmem->get()->get(), memory),
                      ANEURALNETWORKS_NO_ERROR);
            EXPECT_EQ(Result::NO_ERROR, execution->setInputFromMemory(i, &wrapperMemory, 0, 0));
        }

        // Model outputs.
        for (uint32_t i = 0; i < testModel.main.outputIndexes.size(); i++) {
            SCOPED_TRACE("Output index: " + std::to_string(i));
            ANeuralNetworksMemory* memory = createDeviceMemoryForOutput(compilation, i);
            if (memory == nullptr) {
                return ComputeWithDeviceMemoriesResult::SKIP;
            }
            auto& wrapperMemory = outputMemories.emplace_back(Memory(mNnApi.get(), memory));
            EXPECT_EQ(Result::NO_ERROR, execution->setOutputFromMemory(i, &wrapperMemory, 0, 0));
        }
    }

    *result = execution->compute(computeMode);

    // Copy out output results.
    for (uint32_t i = 0; i < testModel.main.outputIndexes.size(); i++) {
        SCOPED_TRACE("Output index: " + std::to_string(i));
        const auto& operand = testModel.main.operands[testModel.main.outputIndexes[i]];
        const size_t bufferSize = operand.data.size();
        auto& output = outputs->emplace_back(bufferSize);

        auto ashmem = TestAshmem::createFrom(mNnApi.get(), output);
        EXPECT_NE(ashmem, nullptr);
        EXPECT_EQ(mNnApi->getFL5()->ANeuralNetworksMemory_copy(outputMemories[i].get(),
                                                               ashmem->get()->get()),
                  ANEURALNETWORKS_NO_ERROR);
        std::copy(ashmem->dataAs<uint8_t>(), ashmem->dataAs<uint8_t>() + bufferSize,
                  output.getMutable<uint8_t>());
    }
    return ComputeWithDeviceMemoriesResult::OK;
}

void GeneratedTests::executeWithCompilation(const Compilation& compilation,
                                            const TestModel& testModel) {
    NNTRACE_APP(NNTRACE_PHASE_EXECUTION, "executeWithCompilation example");

    Execution execution(mNnApi.get(), &compilation);
    Result result;
    std::vector<TestBuffer> outputs;

    if (mTestDeviceMemory) {
        if (computeWithDeviceMemories(compilation, testModel, &execution, mComputeMode, &result,
                                      &outputs) == ComputeWithDeviceMemoriesResult::SKIP) {
            std::cout << "\nModel not supported by device memories. Skipping" << std::endl;
            return;
        }
    } else {
        computeWithPtrs(testModel, &execution, mComputeMode, &result, &outputs);
    }

    if (result == Result::NO_ERROR && outputs.empty()) {
        return;
    }

    {
        NNTRACE_APP(NNTRACE_PHASE_RESULTS, "executeWithCompilation example");
        if (mExpectFailure) {
            ASSERT_NE(result, Result::NO_ERROR);
            return;
        } else {
            ASSERT_EQ(result, Result::NO_ERROR);
        }

        // Check output dimensions.
        for (uint32_t i = 0; i < testModel.main.outputIndexes.size(); i++) {
            SCOPED_TRACE("Output index: " + std::to_string(i));
            const auto& output = testModel.main.operands[testModel.main.outputIndexes[i]];
            if (output.isIgnored) continue;
            std::vector<uint32_t> actualDimensions;
            ASSERT_EQ(Result::NO_ERROR, execution.getOutputOperandDimensions(i, &actualDimensions));
            ASSERT_EQ(output.dimensions, actualDimensions);
        }

        checkResults(testModel, outputs);
    }
}

void GeneratedTests::executeOnce(const Model& model, const TestModel& testModel) {
    NNTRACE_APP(NNTRACE_PHASE_OVERALL, "executeOnce");
    uint32_t numDevices = 0;
    mNnApi->getFL5()->ANeuralNetworks_getDeviceCount(&numDevices);
    bool modelSupported = false;
    for (uint32_t i = 0; i < numDevices; ++i) {
        ANeuralNetworksDevice* device = nullptr;
        mNnApi->getFL5()->ANeuralNetworks_getDevice(i, &device);
        const char* deviceName = nullptr;
        mNnApi->getFL5()->ANeuralNetworksDevice_getName(device, &deviceName);
        SCOPED_TRACE("Device = " + std::string(deviceName));
        std::cout << "\nDevice = " << deviceName << std::endl;
        if (!checkSupported(model, device)) {
            std::cout << "\nModel not supported by device " << deviceName << ". Skipping"
                      << std::endl;
            continue;
        }
        modelSupported = true;
        std::cout << "\nModel supported" << std::endl;
        std::optional<Compilation> compilation = compileModel(model, device);
        // Early return if compilation fails. The compilation result code is
        // checked in compileModel.
        if (!compilation) return;
        executeWithCompilation(compilation.value(), testModel);
        std::cout << "\nExecution completed" << std::endl;
    }
    if (!modelSupported) {
        std::cout << "\nModel not supported by any device\n"
                  << "SKIPPED" << std::endl;
    }
}

void GeneratedTests::executeMultithreadedOwnCompilation(const Model& model,
                                                        const TestModel& testModel) {
    NNTRACE_APP(NNTRACE_PHASE_OVERALL, "executeMultithreadedOwnCompilation");
    SCOPED_TRACE("MultithreadedOwnCompilation");
    std::cout << "\nMultithreadedOwnCompilation" << std::endl;
    std::vector<std::thread> threads;
    for (int i = 0; i < 10; i++) {
        threads.push_back(std::thread([&]() { executeOnce(model, testModel); }));
    }
    std::for_each(threads.begin(), threads.end(), [](std::thread& t) { t.join(); });
}

void GeneratedTests::executeMultithreadedSharedCompilation(const Model& model,
                                                           const TestModel& testModel) {
    NNTRACE_APP(NNTRACE_PHASE_OVERALL, "executeMultithreadedSharedCompilation");
    SCOPED_TRACE("MultithreadedSharedCompilation");
    std::cout << "\nMultithreadedSharedCompilation" << std::endl;
    uint32_t numDevices = 0;
    mNnApi->getFL5()->ANeuralNetworks_getDeviceCount(&numDevices);
    bool modelSupported = false;
    for (uint32_t i = 0; i < numDevices; ++i) {
        ANeuralNetworksDevice* device = nullptr;
        mNnApi->getFL5()->ANeuralNetworks_getDevice(i, &device);
        const char* deviceName = nullptr;
        mNnApi->getFL5()->ANeuralNetworksDevice_getName(device, &deviceName);
        SCOPED_TRACE("Device = " + std::string(deviceName));
        std::cout << "\nDevice = " << deviceName << std::endl;
        if (!checkSupported(model, device)) {
            std::cout << "\nModel not supported by device " << deviceName << ". Skipping"
                      << std::endl;
            continue;
        }
        modelSupported = true;
        std::cout << "\nModel supported" << std::endl;
        std::optional<Compilation> compilation = compileModel(model, device);
        // Early return if compilation fails. The ompilation result code is
        // checked in compileModel.
        if (!compilation) return;
        std::vector<std::thread> threads;
        for (int i = 0; i < 10; i++) {
            threads.push_back(
                    std::thread([&]() { executeWithCompilation(compilation.value(), testModel); }));
        }
        std::for_each(threads.begin(), threads.end(), [](std::thread& t) { t.join(); });
        std::cout << "\nExecution completed" << std::endl;
    }
    if (!modelSupported) {
        std::cout << "\nModel not supported by any device\n"
                  << "SKIPPED" << std::endl;
    }
}

// Test driver for those generated from ml/nn/runtime/test/spec
void GeneratedTests::execute(const TestModel& testModel) {
    NNTRACE_APP(NNTRACE_PHASE_OVERALL, "execute");
    GeneratedModel model(mNnApi.get());
    createModel(mNnApi.get(), testModel, mTestDynamicOutputShape, &model);
    if (testModel.expectFailure && !model.isValid()) {
        return;
    }
    ASSERT_EQ(model.finish(), Result::NO_ERROR);
    ASSERT_TRUE(model.isValid());
    auto executeInternal = [&testModel, &model, this]() {
        SCOPED_TRACE("TestCompilationCaching = " + std::to_string(mTestCompilationCaching));
        std::cout << "\nCompilationCaching = " << std::boolalpha << mTestCompilationCaching
                  << std::endl;
#ifndef NNTEST_MULTITHREADED
        executeOnce(model, testModel);
#else   // defined(NNTEST_MULTITHREADED)
        executeMultithreadedOwnCompilation(model, testModel);
        executeMultithreadedSharedCompilation(model, testModel);
#endif  // !defined(NNTEST_MULTITHREADED)
    };
    mTestCompilationCaching = false;
    executeInternal();
    if (!mExpectFailure) {
        mTestCompilationCaching = true;
        executeInternal();
    }
}

bool GeneratedTests::shouldSkipTest() {
    // A map of {min VNDK version -> tests that should be skipped with earlier VNDK versions}.
    // The listed tests are added in a later release, but exercising old APIs. They should be
    // skipped if the device has a mixed build of system and vendor partitions.
    static const std::map<int, std::set<std::string>> kMapOfMinVndkVersionToTests = {
            {
                    __ANDROID_API_R__,
                    {
                            "add_broadcast_quant8_all_inputs_as_internal",
                    },
            },
    };
    for (const auto& [minVersion, names] : kMapOfMinVndkVersionToTests) {
        if (mVndkVersion < minVersion && names.count(kTestName) > 0) {
            return true;
        }
    }
    return false;
}

void GeneratedTests::SetUp() {
    const char* libdir = dirname(SUPPORT_LIBRARY_NAME.c_str());
    setenv(kQCDspLoadPathEnv, libdir, 1);
    LOG(INFO) << "Overwritten system env variable " << kQCDspLoadPathEnv << " with " << libdir;
    mNnApi = loadNnApiSupportLibrary(SUPPORT_LIBRARY_NAME);

    GeneratedTestBase::SetUp();

    mVndkVersion = ::android::base::GetIntProperty("ro.vndk.version", __ANDROID_API_FUTURE__);
    if (shouldSkipTest()) {
        GTEST_SKIP();
        return;
    }

    char cacheDirTemp[] = NN_TMP_DIR "/TestCompilationCachingXXXXXX";
    char* cacheDir = mkdtemp(cacheDirTemp);
    ASSERT_NE(cacheDir, nullptr);
    mCacheDir = cacheDir;
    mToken = std::vector<uint8_t>(ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN, 0);
}

void GeneratedTests::TearDown() {
    mNnApi.reset(nullptr);

    if (!::testing::Test::HasFailure()) {
        // TODO: Switch to std::filesystem::remove_all once libc++fs is made available in CTS.
        // Remove the cache directory specified by path recursively.
        auto callback = [](const char* child, const struct stat*, int, struct FTW*) {
            return remove(child);
        };
        nftw(mCacheDir.c_str(), callback, 128, FTW_DEPTH | FTW_MOUNT | FTW_PHYS);
    }
    GeneratedTestBase::TearDown();
}

#ifdef NNTEST_COMPUTE_MODE
TEST_P(GeneratedTests, Sync) {
    std::cout << "\nComputeMode = SYNC" << std::endl;
    mComputeMode = Execution::ComputeMode::SYNC;
    execute(testModel);
}

TEST_P(GeneratedTests, Burst) {
    std::cout << "\nComputeMode = BURST" << std::endl;
    mComputeMode = Execution::ComputeMode::BURST;
    execute(testModel);
}
#else
TEST_P(GeneratedTests, Test) {
    execute(testModel);
}
#endif

TEST_P(DynamicOutputShapeTest, Test) {
    execute(testModel);
}

TEST_P(GeneratedValidationTests, Test) {
    execute(testModel);
}

TEST_P(QuantizationCouplingTest, Test) {
    execute(convertQuant8AsymmOperandsToSigned(testModel));
}

TEST_P(DeviceMemoryTest, Test) {
    execute(testModel);
}

TEST_P(FencedComputeTest, Test) {
    mComputeMode = Execution::ComputeMode::FENCED;
    execute(testModel);
}

INSTANTIATE_GENERATED_TEST(GeneratedTests,
                           [](const TestModel& testModel) { return !testModel.expectFailure; });

INSTANTIATE_GENERATED_TEST(DynamicOutputShapeTest, [](const TestModel& testModel) {
    return !testModel.expectFailure && !testModel.hasScalarOutputs();
});

INSTANTIATE_GENERATED_TEST(GeneratedValidationTests, [](const TestModel& testModel) {
    return testModel.expectFailure && !testModel.isInfiniteLoopTimeoutTest();
});

INSTANTIATE_GENERATED_TEST(QuantizationCouplingTest, [](const TestModel& testModel) {
    return !testModel.expectFailure && testModel.main.operations.size() == 1 &&
           testModel.referenced.size() == 0 && testModel.hasQuant8CoupledOperands();
});

INSTANTIATE_GENERATED_TEST(DeviceMemoryTest, [](const TestModel& testModel) {
    return !testModel.expectFailure &&
           std::all_of(testModel.main.outputIndexes.begin(), testModel.main.outputIndexes.end(),
                       [&testModel](uint32_t index) {
                           return testModel.main.operands[index].data.size() > 0;
                       });
});

INSTANTIATE_GENERATED_TEST(FencedComputeTest, [](const TestModel& testModel) {
    return !testModel.expectFailure &&
           std::all_of(testModel.main.outputIndexes.begin(), testModel.main.outputIndexes.end(),
                       [&testModel](uint32_t index) {
                           return testModel.main.operands[index].data.size() > 0;
                       });
});

}  // namespace android::nn::generated_tests
