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

#include <inttypes.h>
#include <uapi/err.h>
#include <optional>
#include "device_parameters.h"

#define TLOG_TAG "confui-test"
#include <trusty_unittest.h>

#define MIN_EXPECTED_DRAW_COUNT 1000u
#define MIN_EXPECTED_DRAW_PERCENTAGE 1u

static const char* language_ids[] = {"en"};

static const char* messages[] = {
        /* Simple message */
        "Android Test Message",
        /* Two line message as required by Android design guidelines */
        "WM Line 1\nWM Line 2",
        /* 100 'W' characters as required by Android design guidelines */
        "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW",
        /* 8 groups of 12 'W' characters as required by Android design
           guidelines */
        "WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW WWWWWWWWWWWW"};

TEST(confui, display_count) {
    int display_count = devices::getDisplayCount();

    EXPECT_GT(display_count, 0);
}

typedef struct {
    uint32_t count;
} draw_stats_t;

typedef struct {
    const char* lang_id;
    const char* message;
    bool magnified;
    bool inverse;
} confuip_t;

TEST_F_SETUP(confuip) {
    const void* const* param_arr = (const void* const*)GetParam();
    const bool* magnified = (const bool*)param_arr[0];
    const bool* inverse = (const bool*)param_arr[1];
    const char** lang_id = (const char**)param_arr[2];
    const char** message = (const char**)param_arr[3];

    _state->magnified = *magnified;
    _state->inverse = *inverse;
    _state->lang_id = *lang_id;
    _state->message = *message;
}

TEST_F_TEARDOWN(confuip) {}

TEST_P(confuip, display_params) {
    int display_count = devices::getDisplayCount();

    for (int i = 0; i < display_count; i++) {
        std::optional<teeui::context<teeui::ConUIParameters>> params;

        params = devices::getDisplayContext(i, _state->magnified);
        EXPECT_EQ(params.has_value(), true, "getDisplayContext %d", i);

        if (params) {
            const uint32_t screen_width =
                    params->getParam<teeui::RightEdgeOfScreen>()->count() + 1;
            const uint32_t screen_height =
                    params->getParam<teeui::BottomOfScreen>()->count() + 1;
            std::optional<std::unique_ptr<teeui::layouts::ILayout>> optlayout;

            optlayout = devices::getDisplayLayout(i, _state->inverse, *params);
            EXPECT_EQ(optlayout.has_value(), true, "getDisplayLayout %d", i);

            /* Configure the layout */
            if (optlayout) {
                std::unique_ptr<teeui::layouts::ILayout> layout =
                        std::move(*optlayout);
                draw_stats_t stats = {};

                /* Configure the layout */
                layout->setLanguage(_state->lang_id);
                layout->setConfirmationMessage(_state->message);
                layout->showInstructions(true /* enable */);

                auto drawPixel = teeui::makePixelDrawer(
                        [&](uint32_t x, uint32_t y,
                            teeui::Color color) -> teeui::Error {
                            TLOGD("px %u %u: %08x", x, y, color);

                            /* Check bounds */
                            if (x >= screen_width || y >= screen_height) {
                                return teeui::Error::OutOfBoundsDrawing;
                            }

                            /* Count calls, noting there is no check if some
                             * pixels are written multiple times.
                             */
                            stats.count++;

                            return teeui::Error::OK;
                        });

                /* Render the layout */
                teeui::Error rc = layout->drawElements(drawPixel);
                EXPECT_EQ(rc.code(), teeui::Error::OK, "drawElements %d", i);

                EXPECT_GT(stats.count, MIN_EXPECTED_DRAW_COUNT,
                          "pixel count %d", i);

                const uint32_t area = screen_width * screen_height;
                const uint32_t coverage =
                        (float)stats.count / (float)area * 1000.0f;

                EXPECT_GT(coverage, MIN_EXPECTED_DRAW_PERCENTAGE * 10u,
                          "pixel coverage %" PRIu32 ".%" PRIu32 "%%",
                          coverage / 10, coverage % 10);

                trusty_unittest_printf("[   DATA   ] %" PRIu32 " x %" PRIu32
                                       ", %" PRIu32
                                       " plot calls = approx %" PRIu32
                                       ".%" PRIu32 "%% coverage\n",
                                       screen_width, screen_height, stats.count,
                                       coverage / 10, coverage % 10);
            }
        }
    }
}

void confuip_to_string(const void* param, char* buf, size_t buf_size) {
    const void* const* param_arr = (const void* const*)GetParam();
    const bool* magnified = (const bool*)param_arr[0];
    const bool* inverse = (const bool*)param_arr[1];
    const char** lang_id = (const char**)param_arr[2];
    const char** message = (const char**)param_arr[3];
    uint8_t msg_index = message - messages;

    snprintf(buf, buf_size, "%s/msg%" PRIu8 "%s%s", *lang_id, msg_index,
             *magnified ? "/mag" : "", *inverse ? "/inv" : "");
}

INSTANTIATE_TEST_SUITE_P(confui,
                         confuip,
                         testing_Combine(testing_Bool(), /* magnified */
                                         testing_Bool(), /* inverse */
                                         testing_ValuesIn(language_ids),
                                         testing_ValuesIn(messages)),
                         confuip_to_string);

PORT_TEST(confui, "com.android.trusty.confirmationui.test");
