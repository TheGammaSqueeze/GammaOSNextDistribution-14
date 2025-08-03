/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <aidl/android/hardware/power/IPower.h>
#include <aidl/google/hardware/power/extension/pixel/IPowerExt.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>

#include <getopt.h>

using ::aidl::android::hardware::power::IPower;
using ::aidl::google::hardware::power::extension::pixel::IPowerExt;

static void DualLogger(android::base::LogId id, android::base::LogSeverity severity,
                       const char *tag, const char *file, unsigned int line, const char *message) {
    android::base::KernelLogger(id, severity, tag, file, line, message);
    android::base::StderrLogger(id, severity, tag, file, line, message);
}

static void printUsage(const char *exec_name) {
    android::base::SetLogger(android::base::StderrLogger);
    std::string usage = exec_name;
    usage = usage +
            " is a command-line tool to send hint to Pixel Power HAL.\n"
            "Usages:\n" +
            exec_name +
            " [options]\n"
            "\n"
            "Options:\n"
            "   --mode, -m\n"
            "       Mode for hint\n\n"
            "   --enable, -e\n"
            "       1 for enable mode, 0 for disable\n\n"
            "   --boost, -b\n"
            "       Boost for hint\n\n"
            "   --duration, -d\n"
            "       Boost duration\n\n"
            "   --help, -h\n"
            "       print this message\n\n";

    LOG(INFO) << usage;
}

static std::shared_ptr<IPowerExt> connect() {
    const std::string kInstance = std::string(IPower::descriptor) + "/default";
    ndk::SpAIBinder power_binder = ndk::SpAIBinder(AServiceManager_getService(kInstance.c_str()));
    ndk::SpAIBinder ext_power_binder;
    std::shared_ptr<IPowerExt> powerext = nullptr;

    if (power_binder.get() == nullptr) {
        LOG(ERROR) << "Cannot get Power Hal Binder";
        return powerext;
    }

    if (STATUS_OK != AIBinder_getExtension(power_binder.get(), ext_power_binder.getR()) ||
        ext_power_binder.get() == nullptr) {
        LOG(ERROR) << "Cannot get Power Hal Extension Binder";
        return powerext;
    }

    powerext = IPowerExt::fromBinder(ext_power_binder);
    if (powerext == nullptr) {
        LOG(ERROR) << "Cannot get Power Hal Extension AIDL";
    }

    return powerext;
}

static bool setMode(std::shared_ptr<IPowerExt> hal, const std::string &type, const bool &enable) {
    if (!hal->setMode(type, enable).isOk()) {
        LOG(ERROR) << "Fail to set mode: " << type << " enabled: " << enable;
        return false;
    } else {
        LOG(INFO) << "Set mode: " << type << " enabled: " << enable;
        return true;
    }
}

static bool setBoost(std::shared_ptr<IPowerExt> hal, const std::string &type,
                     const int32_t duration_ms) {
    if (!hal->setBoost(type, duration_ms).isOk()) {
        LOG(ERROR) << "Fail to set boost: " << type << " duration: " << duration_ms;
        return false;
    } else {
        LOG(INFO) << "Set boost: " << type << " duration: " << duration_ms;
        return true;
    }
}

int main(int argc, char *argv[]) {
    android::base::SetLogger(DualLogger);
    std::string boost;
    unsigned int duration_ms = 0;
    std::string mode;
    bool enabled = true;

    static struct option opts[] = {
            {"boost", optional_argument, nullptr, 'b'},
            {"duration", optional_argument, nullptr, 'd'},
            {"mode", optional_argument, nullptr, 'm'},
            {"enable", optional_argument, nullptr, 'e'},
            {0, 0, 0, 0}  // termination of the option list
    };

    int c = -1;
    while ((c = getopt_long(argc, argv, "b:d:m:e:h", opts, nullptr)) != -1) {
        switch (c) {
            case 'b':
                boost = optarg;
                break;
            case 'd':
                duration_ms = std::stoi(optarg);
                break;
            case 'm':
                mode = optarg;
                break;
            case 'e':
                enabled = std::stoi(optarg);
                break;
            case 'h':
                printUsage(argv[0]);
                return 0;
            default:
                printUsage(argv[0]);
                return 1;
        }
    }
    if (boost.empty() && mode.empty()) {
        LOG(ERROR) << "Need specify a boost or mode to send hint";
        printUsage(argv[0]);
        return 1;
    }

    std::shared_ptr<IPowerExt> powerext = connect();
    if (!powerext) {
        return 1;
    }

    if (!boost.empty() && !setBoost(powerext, boost, duration_ms)) {
        return 1;
    }

    if (!mode.empty() && !setMode(powerext, mode, enabled)) {
        return 1;
    }
    return 0;
}
