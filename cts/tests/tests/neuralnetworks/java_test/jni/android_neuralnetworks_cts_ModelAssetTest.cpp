/*
 * Copyright 2021 The Android Open Source Project
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
 *
 */

#define LOG_TAG "ModelAssetTest"

#include <NeuralNetworks.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android-base/unique_fd.h>
#include <android/asset_manager_jni.h>
#include <android/sharedmem.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "Utils.h"

namespace {

using namespace android::nn::cts;

// A map from data type to element size in bytes.
// The keys only include the primary data types that will be used to construct a model
// in createAndCompileAddModel. The data types are selected in a way that a driver is likely to
// support at least one of the data types.
const std::map<int, uint32_t> kDataTypeToElementSizeMap = {
        {ANEURALNETWORKS_TENSOR_FLOAT16, 2},
        {ANEURALNETWORKS_TENSOR_FLOAT32, 4},
        {ANEURALNETWORKS_TENSOR_QUANT8_ASYMM, 1},
        {ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED, 1},
};

bool isQuantizedType(int dataType) {
    return dataType == ANEURALNETWORKS_TENSOR_QUANT8_ASYMM ||
            dataType == ANEURALNETWORKS_TENSOR_QUANT8_ASYMM_SIGNED;
}

std::vector<const char*> getNnapiDeviceNames(JNIEnv* env) {
    // Get the number of available NNAPI devices
    uint32_t numDevices = 0;
    ASSERT_OR_RETURN_DEFAULT(ANeuralNetworks_getDeviceCount(&numDevices) ==
                             ANEURALNETWORKS_NO_ERROR);

    std::vector<const char*> deviceNames(numDevices, nullptr);
    for (uint32_t i = 0; i < numDevices; i++) {
        // Get device
        ANeuralNetworksDevice* currentDevice;
        ASSERT_OR_RETURN_DEFAULT(ANeuralNetworks_getDevice(/*devIndex=*/i, &currentDevice) ==
                                 ANEURALNETWORKS_NO_ERROR);

        // Get device name
        const char* deviceName = nullptr;
        ASSERT_OR_RETURN_DEFAULT(ANeuralNetworksDevice_getName(currentDevice, &deviceName) ==
                                 ANEURALNETWORKS_NO_ERROR);
        deviceNames[i] = deviceName;
    }
    return deviceNames;
}

int64_t getDeviceFeatureLevel(JNIEnv* env, const ANeuralNetworksDevice* device) {
    int64_t featureLevel;
    ASSERT_OR_RETURN_ZERO(ANeuralNetworksDevice_getFeatureLevel(device, &featureLevel) ==
                          ANEURALNETWORKS_NO_ERROR);
    return featureLevel;
}

const ANeuralNetworksDevice* findDevice(JNIEnv* env, const std::string& name) {
    // Get the number of available NNAPI devices
    uint32_t numDevices = 0;
    ASSERT_OR_RETURN_NULL(ANeuralNetworks_getDeviceCount(&numDevices) == ANEURALNETWORKS_NO_ERROR);

    for (uint32_t i = 0; i < numDevices; i++) {
        // Get device
        ANeuralNetworksDevice* currentDevice;
        ASSERT_OR_RETURN_NULL(ANeuralNetworks_getDevice(/*devIndex=*/i, &currentDevice) ==
                              ANEURALNETWORKS_NO_ERROR);

        // Get device name
        const char* deviceName = nullptr;
        ASSERT_OR_RETURN_NULL(ANeuralNetworksDevice_getName(currentDevice, &deviceName) ==
                              ANEURALNETWORKS_NO_ERROR);

        // Return if name matches
        if (name == deviceName) {
            return currentDevice;
        }
    }
    fail(env, "No device found with name %s", name.c_str());
    return nullptr;
}

// Create a NNAPI memory directly from the asset file
ANeuralNetworksMemory* createMemoryFromAsset(JNIEnv* env, AAsset* asset) {
    // Open the asset file as a file descriptor
    off_t offset, length;
    android::base::unique_fd assetFd(AAsset_openFileDescriptor(asset, &offset, &length));
    ASSERT_OR_RETURN_NULL(assetFd.ok());

    // Create NNAPI memory from the asset file
    ANeuralNetworksMemory* memory;
    ASSERT_OR_RETURN_NULL(ANeuralNetworksMemory_createFromFd(length, PROT_READ, assetFd.get(),
                                                             offset,
                                                             &memory) == ANEURALNETWORKS_NO_ERROR);
    return memory;
}

// Copy the content of the asset file to an ashmem, and create a NNAPI memory from the ashmem
ANeuralNetworksMemory* createMemoryFromAshmem(JNIEnv* env, AAsset* asset) {
    // Create an ashmem
    off_t length = AAsset_getLength(asset);
    android::base::unique_fd ashmemFd(ASharedMemory_create("model_data", length));
    ASSERT_OR_RETURN_NULL(ashmemFd.ok());

    // Copy the content of the asset file to the ashmem
    void* ashmemData =
            mmap(nullptr, length, PROT_READ | PROT_WRITE, MAP_SHARED, ashmemFd.get(), /*offset=*/0);
    ASSERT_OR_RETURN_NULL(ashmemData != nullptr);
    int bytesRead = AAsset_read(asset, ashmemData, length);
    munmap(ashmemData, length);
    ASSERT_OR_RETURN_NULL(bytesRead == length);

    // Create NNAPI memory from the ashmem
    ANeuralNetworksMemory* memory;
    ASSERT_OR_RETURN_NULL(ANeuralNetworksMemory_createFromFd(length, PROT_READ, ashmemFd.get(),
                                                             /*offset=*/0,
                                                             &memory) == ANEURALNETWORKS_NO_ERROR);
    return memory;
}

// Create and compile a model with a single ADD operation. We choose the ADD operation because
// it is commonly supported on most of the devices.
// The input dataType must be one of the keys in kDataTypeToElementSizeMap.
// The value of the constant tensor will be loaded from the given memory with offset = 0.
// This method returns whether the compilation is successful or not.
bool createAndCompileAddModel(JNIEnv* env, int dataType, const ANeuralNetworksMemory* memory,
                              const ANeuralNetworksDevice* device) {
    ASSERT_OR_RETURN_FALSE(kDataTypeToElementSizeMap.count(dataType) > 0);
    const uint32_t tensorDims[] = {1, 256, 256, 4};
    const uint32_t tensorSize = 1 * 256 * 256 * 4 * kDataTypeToElementSizeMap.at(dataType);
    const uint32_t activation = 0;

    // Create model
    ANeuralNetworksModel* model = nullptr;
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_create(&model) == ANEURALNETWORKS_NO_ERROR);
    auto modelGuard =
            android::base::make_scope_guard([model]() { ANeuralNetworksModel_free(model); });

    // Operand type for tensors
    ANeuralNetworksOperandType tensorType;
    tensorType.type = dataType;
    tensorType.scale = isQuantizedType(dataType) ? 1.0f : 0.0f;
    tensorType.zeroPoint = 0;
    tensorType.dimensionCount = 4;
    tensorType.dimensions = tensorDims;

    // Operand type for activation
    ANeuralNetworksOperandType activationType;
    activationType.type = ANEURALNETWORKS_INT32;
    activationType.scale = 0.0f;
    activationType.zeroPoint = 0;
    activationType.dimensionCount = 0;
    activationType.dimensions = nullptr;

    // Operands
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_addOperand(model, &tensorType) ==
                           ANEURALNETWORKS_NO_ERROR);
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_addOperand(model, &tensorType) ==
                           ANEURALNETWORKS_NO_ERROR);
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_addOperand(model, &activationType) ==
                           ANEURALNETWORKS_NO_ERROR);
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_addOperand(model, &tensorType) ==
                           ANEURALNETWORKS_NO_ERROR);

    // Constant values
    ASSERT_OR_RETURN_FALSE(
            ANeuralNetworksModel_setOperandValueFromMemory(model, /*index=*/1, memory, /*offset=*/0,
                                                           tensorSize) == ANEURALNETWORKS_NO_ERROR);
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_setOperandValue(model, /*index=*/2, &activation,
                                                                sizeof(int32_t)) ==
                           ANEURALNETWORKS_NO_ERROR);

    // ADD operation
    uint32_t operation0InputIndexes[] = {0, 1, 2};
    uint32_t operation0OutputIndexes[] = {3};
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_addOperation(model, ANEURALNETWORKS_ADD, 3,
                                                             operation0InputIndexes, 1,
                                                             operation0OutputIndexes) ==
                           ANEURALNETWORKS_NO_ERROR);

    // Model inputs and outputs
    uint32_t modelInputIndexes[] = {0};
    uint32_t modelOutputIndexes[] = {3};
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_identifyInputsAndOutputs(model, 1,
                                                                         modelInputIndexes, 1,
                                                                         modelOutputIndexes) ==
                           ANEURALNETWORKS_NO_ERROR);

    // Finish the model
    ASSERT_OR_RETURN_FALSE(
            ANeuralNetworksModel_relaxComputationFloat32toFloat16(model, /*allow=*/true) ==
            ANEURALNETWORKS_NO_ERROR);
    ASSERT_OR_RETURN_FALSE(ANeuralNetworksModel_finish(model) == ANEURALNETWORKS_NO_ERROR);

    // Create compilation
    ANeuralNetworksCompilation* compilation;
    ASSERT_OR_RETURN_FALSE(
            ANeuralNetworksCompilation_createForDevices(model, &device, /*numDevices=*/1,
                                                        &compilation) == ANEURALNETWORKS_NO_ERROR);
    auto compilationGuard = android::base::make_scope_guard(
            [compilation]() { ANeuralNetworksCompilation_free(compilation); });

    // Compile
    return ANeuralNetworksCompilation_finish(compilation) == ANEURALNETWORKS_NO_ERROR;
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_android_neuralnetworks_cts_ModelAssetTest_getNnapiDevices(JNIEnv* env, jobject /* this */) {
    // Get device names
    auto deviceNames = getNnapiDeviceNames(env);
    RETURN_IF_FAILED nullptr;

    // Convert to Java string array
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray jDeviceNames = env->NewObjectArray(deviceNames.size(), stringClass, nullptr);
    for (uint32_t i = 0; i < deviceNames.size(); i++) {
        jstring jDeviceName = env->NewStringUTF(deviceNames[i]);
        env->SetObjectArrayElement(jDeviceNames, i, jDeviceName);
    }
    return jDeviceNames;
}

extern "C" JNIEXPORT void JNICALL
Java_android_neuralnetworks_cts_ModelAssetTest_nativeTestCompileFromAssetFile(JNIEnv* env,
                                                                              jobject /* this */,
                                                                              jobject jAssetManager,
                                                                              jstring jDeviceName) {
    AAssetManager* assetManager = AAssetManager_fromJava(env, jAssetManager);
    const char* deviceNameCStr = env->GetStringUTFChars(jDeviceName, nullptr);
    std::string deviceName = deviceNameCStr;
    env->ReleaseStringUTFChars(jDeviceName, deviceNameCStr);

    // Find the NNAPI device
    const auto* device = findDevice(env, deviceName);
    RETURN_IF_FAILED;

    // Check device feature level. This test is added in NNAPI feature level 5, so skip if the
    // device is of a lower feature level.
    const int64_t featureLevel = getDeviceFeatureLevel(env, device);
    RETURN_IF_FAILED;
    if (featureLevel < ANEURALNETWORKS_FEATURE_LEVEL_5) return;

    // Open the asset file
    AAsset* asset = AAssetManager_open(assetManager, "model_data.bin", AASSET_MODE_BUFFER);
    ASSERT_OR_RETURN(asset != nullptr);
    auto assetGuard = android::base::make_scope_guard([asset]() { AAsset_close(asset); });

    // Create NNAPI memory directly from asset file
    auto* memoryFromAsset = createMemoryFromAsset(env, asset);
    auto memoryFromAssetGuard = android::base::make_scope_guard(
            [memoryFromAsset]() { ANeuralNetworksMemory_free(memoryFromAsset); });
    RETURN_IF_FAILED;

    // Create NNAPI memory from ashmem
    auto* memoryFromAshmem = createMemoryFromAshmem(env, asset);
    auto memoryFromAshmemGuard = android::base::make_scope_guard(
            [memoryFromAshmem]() { ANeuralNetworksMemory_free(memoryFromAshmem); });
    RETURN_IF_FAILED;

    // Compile the model with both memories, we expect the compilation results are the same
    for (const auto& [dataType, _] : kDataTypeToElementSizeMap) {
        bool successWithAshmem = createAndCompileAddModel(env, dataType, memoryFromAshmem, device);
        RETURN_IF_FAILED;
        bool successWithAsset = createAndCompileAddModel(env, dataType, memoryFromAsset, device);
        RETURN_IF_FAILED;
        ASSERT_OR_RETURN(successWithAshmem == successWithAsset);
    }
}
