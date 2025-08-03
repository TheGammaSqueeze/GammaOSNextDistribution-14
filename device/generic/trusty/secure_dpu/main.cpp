/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <errno.h>
#include <getopt.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <cutils/android_filesystem_config.h>

#include "DPUHandler.h"

static void show_usage_and_exit(int code) {
    LOG(ERROR) << "usage: securedpud -d <trusty_dev>";
    exit(code);
}

static void parse_device_name(int argc, char* argv[], std::string& device_name) {
    static const char* _sopts = "h:d:";
    static const struct option _lopts[] = {{"help", no_argument, NULL, 'h'},
                                           {"trusty_dev", required_argument, NULL, 'd'},
                                           {0, 0, 0, 0}};
    int opt;
    int oidx = 0;

    while ((opt = getopt_long(argc, argv, _sopts, _lopts, &oidx)) != -1) {
        switch (opt) {
            case 'd':
                device_name = optarg;
                break;

            default:
                LOG(ERROR) << "unrecognized option: " << opt;
                show_usage_and_exit(EXIT_FAILURE);
        }
    }

    if (device_name.empty()) {
        LOG(ERROR) << "missing required argument(s)";
        show_usage_and_exit(EXIT_FAILURE);
    }

    LOG(INFO) << "starting securedpud";
    LOG(INFO) << "trusty dev: " << device_name;
}

int main(int argc, char* argv[])
{
    std::string device_name;
    /* parse arguments */
    parse_device_name(argc, argv, device_name);

    android::trusty::secure_dpu::DPUHandler dpu_handler;
    auto rc = dpu_handler.Init(device_name);
    if (!rc.ok()) {
        LOG(ERROR) << rc.error();
        return EXIT_FAILURE;
    }

    /* main loop */
    while (1) {
        auto result = dpu_handler.Handle();
        if (!result.ok()) {
          LOG(ERROR) << result.error();
        }
    }
    LOG(ERROR) << "exiting securedpud loop";

    return EXIT_FAILURE;
}
