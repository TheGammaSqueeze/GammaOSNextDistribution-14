/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <fstream>
#include <getopt.h>
#include <gtest/gtest.h>
#include <inttypes.h>
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <teeui/example/example.h>
#include <unistd.h>

#include "teeui_device_config.h"
#include <teeui/test/teeui_render_test.h>

#define TeeuiRenderTest_DO_LOG_DEBUG

namespace teeui {

namespace test {

using namespace example;

void initRenderTest(int argc, char** argv) {
    ::teeui::test::TeeuiRenderTest::Instance()->initFromOptions(argc, argv);
}

void saveToPpm(const uint32_t* data, uint32_t w, uint32_t h, uint32_t linestride) {
    const testing::TestInfo* const test_info =
        testing::UnitTest::GetInstance()->current_test_info();
    std::string testname = test_info->name();
    std::ofstream out;

    out.open(testname + ".ppm", std::ios::binary);
    if (out.is_open()) {
        uint32_t linestart = 0;

        /* Write the header */
        out << "P6\n" << w << " " << h << "\n255\n";

        /* Write binary Pixel data */
        for (uint32_t line = 0; line < h; line++) {
            for (uint32_t col = 0; col < w; col++) {
                const uint32_t color = data[linestart + col];
                char rgb[3];

                rgb[0] = color >> 16;
                rgb[1] = color >> 8;
                rgb[2] = color;

                out.write(rgb, sizeof(rgb));
            }

            linestart += linestride;
        }

        out.close();
    }
}

int runRenderTest(const char* language, bool magnified, bool inverted,
                  const char* confirmationMessage, const char* layout) {
    std::unique_ptr<ITeeuiExample> sCurrentExample = createExample(
        (strcmp(layout, kTouchButtonLayout) == 0) ? Examples::TouchButton : Examples::PhysButton);

    DeviceInfo* device_info_ptr = &TeeuiRenderTest::Instance()->device_info;
    sCurrentExample->setDeviceInfo(*device_info_ptr, magnified, inverted);
    uint32_t w = device_info_ptr->width_;
    uint32_t h = device_info_ptr->height_;
    uint32_t linestride = w;
    uint32_t buffer_size = h * linestride;
    std::vector<uint32_t> buffer(buffer_size);
    sCurrentExample->setConfirmationMessage(confirmationMessage);
    sCurrentExample->selectLanguage(language);

    int error =
        sCurrentExample->renderUIIntoBuffer(0, 0, w, h, linestride, buffer.data(), buffer_size);

    if (TeeuiRenderTest::Instance()->saveScreen()) {
        saveToPpm(buffer.data(), w, h, linestride);
    }

    return error;
}

void TeeuiRenderTest::initFromOptions(int argc, char** argv) {

    int option_index = 0;
    static struct option options[] = {{"width", required_argument, 0, 'w'},
                                      {"height", required_argument, 0, 'l'},
                                      {"dp2px", required_argument, 0, 'd'},
                                      {"mm2px", required_argument, 0, 'm'},
                                      {"powerButtonTop", required_argument, 0, 't'},
                                      {"powerButtonBottom", required_argument, 0, 'b'},
                                      {"volUpButtonTop", required_argument, 0, 'u'},
                                      {"volUpButtonBottom", required_argument, 0, 'v'},
                                      {"saveScreen", 0, 0, 's'},
                                      {"help", 0, 0, 'h'},
                                      {"?", 0, 0, '?'},
                                      {0, 0, 0, 0}};
    while (true) {
        int c = getopt_long(argc, argv, "w:l:d:m:t:b:u:v:h?", options, &option_index);
        if (c == -1) break;
        switch (c) {
        case 'w':
            device_info.width_ = strtol(optarg, NULL, 10);
            break;
        case 'l':
            device_info.height_ = strtol(optarg, NULL, 10);
            break;
        case 'd':
            device_info.dp2px_ = strtod(optarg, NULL);
            break;
        case 'm':
            device_info.mm2px_ = strtod(optarg, NULL);
            break;
        case 't':
            device_info.powerButtonTopMm_ = strtod(optarg, NULL);
            break;
        case 'b':
            device_info.powerButtonBottomMm_ = strtod(optarg, NULL);
            break;
        case 'u':
            device_info.volUpButtonTopMm_ = strtod(optarg, NULL);
            break;
        case 'v':
            device_info.volUpButtonBottomMm_ = strtod(optarg, NULL);
            break;
        case 's':
            saveScreen_ = true;
            break;
        case '?':
        case 'h':
            std::cout << "Options:" << std::endl;
            std::cout << "--width=<device width in pixels>" << std::endl;
            std::cout << "--height=<device height in pixels>" << std::endl;
            std::cout << "--dp2px=<pixel per density independent pixel (px/dp) ratio of the "
                         "device. Typically <width in pixels>/412 >"
                      << std::endl;
            std::cout << "--mm2px=<pixel per millimeter (px/mm) ratio>" << std::endl;
            std::cout << "--powerButtonTop=<distance from the top of the power button to the top "
                         "of the screen in mm>"
                      << std::endl;
            std::cout << "--powerButtonBottom=<distance from the bottom of the power button to the "
                         "top of the screen in mm>"
                      << std::endl;
            std::cout << "--volUpButtonTop=<distance from the top of the UP volume button to the "
                         "top of the screen in mm>"
                      << std::endl;
            std::cout << "--volUpButtonBottom=<distance from the bottom of the UP power button to "
                         "the top of the screen in mm>"
                      << std::endl;
            std::cout << "--saveScreen - save rendered screen to ppm files in working directory"
                      << std::endl;
            exit(0);
        }
    }
}

}  // namespace test

}  // namespace teeui
