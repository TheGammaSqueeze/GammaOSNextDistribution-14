/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "cartelemetryd_sample"

#include <aidl/android/frameworks/automotive/telemetry/BnCarTelemetryCallback.h>
#include <aidl/android/frameworks/automotive/telemetry/ICarTelemetry.h>
#include <android-base/stringprintf.h>
#include <android/binder_manager.h>
#include <utils/SystemClock.h>

#include <getopt.h>
#include <stdlib.h>
#include <sysexits.h>
#include <unistd.h>

#include <cstdint>
#include <cstdio>
#include <iostream>
#include <memory>
#include <vector>

using ::aidl::android::frameworks::automotive::telemetry::CallbackConfig;
using ::aidl::android::frameworks::automotive::telemetry::CarData;
using ::aidl::android::frameworks::automotive::telemetry::ICarTelemetry;
using ::android::base::StringPrintf;

class CarTelemetryCallbackImpl :
      public aidl::android::frameworks::automotive::telemetry::BnCarTelemetryCallback {
public:
    ndk::ScopedAStatus onChange(const std::vector<int32_t>& carDataIds) {
        for (int32_t id : carDataIds) {
            std::cout << "CarTelemetryCallbackImpl: CarData ID=" << id << " is active";
        }
        return ndk::ScopedAStatus::ok();
    }
};

void printHelp() {
    std::cerr << "Usage: --batch-size NUM --interval-micros MICROS --cardata-size LEN" << std::endl;
    std::cerr
            << "  Sends a batch of NUM car data of size LEN each with MICROS interval between them"
            << std::endl;
}

int main(int argc, char* argv[]) {
    struct option options[] = {
            {"batch-size", required_argument, nullptr, 'c'},
            {"interval-micros", required_argument, nullptr, 'i'},
            {"cardata-size", required_argument, nullptr, 's'},
            {nullptr, 0, nullptr, 0},
    };
    int opt = 0;
    int batchSize = 0;
    int intervalInMicros = 0;
    int cardataSize = 0;
    int option_index = -1;
    while ((opt = getopt_long_only(argc, argv, "", options, &option_index)) != -1) {
        bool argError = false;
        switch (opt) {
            case 'c':
                argError = sscanf(optarg, "%d", &batchSize) != 1;
                break;
            case 'i':
                argError = sscanf(optarg, "%d", &intervalInMicros) != 1;
                break;
            case 's':
                argError = sscanf(optarg, "%d", &cardataSize) != 1;
                break;
            // Unknown argument
            case '?':
            default:
                printHelp();
                return EX_USAGE;
        }
        if (argError) {
            std::cerr << "Invalid argument for " << options[option_index].name << " option"
                      << std::endl;
            printHelp();
            return EX_USAGE;
        }
    }

    if (batchSize == 0) {
        std::cerr << "Required argument --batch-size was not specified" << std::endl;
        printHelp();
        return EX_USAGE;
    }

    if (intervalInMicros == 0) {
        std::cerr << "Required argument --interval-micros was not specified" << std::endl;
        printHelp();
        return EX_USAGE;
    }

    if (cardataSize == 0) {
        std::cerr << "Required argument --cardata-size was not specified" << std::endl;
        printHelp();
        return EX_USAGE;
    }

    // The name of the service is described in
    // https://source.android.com/devices/architecture/aidl/aidl-hals#instance-names
    const std::string instance = StringPrintf("%s/default", ICarTelemetry::descriptor);
    std::cout << "Obtaining: " << instance << std::endl;
    std::shared_ptr<ICarTelemetry> service = ICarTelemetry::fromBinder(
            ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));
    if (!service) {
        std::cerr << "ICarTelemetry service not found, may be still initializing?" << std::endl;
        return EX_UNAVAILABLE;
    }

    // Add a ICarTelemetryCallback and listen for changes in CarData IDs 1, 2, and 3
    std::shared_ptr<CarTelemetryCallbackImpl> callback =
            ndk::SharedRefBase::make<CarTelemetryCallbackImpl>();
    CallbackConfig config;
    std::cout << "Adding a CarTelemetryCallback" << std::endl;
    ndk::ScopedAStatus addStatus = service->addCallback(config, callback);
    if (!addStatus.isOk()) {
        std::cerr << "Failed to add CarTelemetryCallback: " << addStatus.getMessage() << std::endl;
    }

    const int64_t batchStartTime = android::elapsedRealtime();
    std::cout << "Started sending the batch at " << batchStartTime << " millis since boot"
              << std::endl;

    for (int i = 0; i < batchSize; i++) {
        // Build a CarData message
        CarData msg;
        msg.id = 1;
        msg.content = std::vector<uint8_t>(cardataSize);

        // Send the data
        ndk::ScopedAStatus writeStatus = service->write({msg});

        if (!writeStatus.isOk()) {
            std::cerr << "Failed to write to the service: " << writeStatus.getMessage()
                      << std::endl;
        }

        usleep(intervalInMicros);
    }
    const int64_t batchFinishTime = android::elapsedRealtime();
    std::cout << "Finished sending the batch at " << batchFinishTime << " millis since boot"
              << std::endl;
    std::cout << "Took " << batchFinishTime - batchStartTime << " millis to send a batch of "
              << batchSize << " carData, each with payload of " << cardataSize << " bytes"
              << std::endl;

    // Remove the ICarTelemetryCallback to prevent a dead reference
    std::cout << "Removing a CarTelemetryCallback" << std::endl;
    ndk::ScopedAStatus removeStatus = service->removeCallback(callback);
    if (!removeStatus.isOk()) {
        std::cerr << "Failed to remove CarTelemetryCallback: " << removeStatus.getMessage()
                  << std::endl;
    }

    return EX_OK;
}
