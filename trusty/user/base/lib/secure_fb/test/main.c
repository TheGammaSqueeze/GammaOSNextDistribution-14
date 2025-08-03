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

#define TLOG_TAG "secure_fb_test"

#include <lib/secure_fb/secure_fb.h>
#include <trusty/time.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

typedef struct {
    secure_fb_handle_t session;
    struct secure_fb_info fb_info;
} secure_fb_t;

TEST_F_SETUP(secure_fb) {
    int rc;

    _state->session = 0;
    rc = secure_fb_open(&_state->session, &_state->fb_info, 0);
    ASSERT_EQ(rc, 0);

test_abort:;
}

TEST_F_TEARDOWN(secure_fb) {
    secure_fb_close(_state->session);
}

TEST_F(secure_fb, open_and_close) {
    /* Only need Setup() and Teardown() to run. */
}

TEST_F(secure_fb, fb_info) {
    struct secure_fb_info* fb_info = &_state->fb_info;

    EXPECT_NE(fb_info->buffer, NULL);
    EXPECT_GT(fb_info->size, 0);
    EXPECT_GT(fb_info->pixel_stride, 0);
    EXPECT_GT(fb_info->line_stride, 0);
    EXPECT_GT(fb_info->width, 0);
    EXPECT_GT(fb_info->height, 0);
    EXPECT_NE(fb_info->pixel_format, TTUI_PF_INVALID);

    EXPECT_LE(fb_info->width * fb_info->pixel_stride, fb_info->line_stride);
    EXPECT_LE(fb_info->height * fb_info->line_stride, fb_info->size);

test_abort:;
}

/*
 * Pixel colouring function.
 * This divides the screen into 9 horizonal bars and then displays a blend
 * from each channel into the other.  The first 3 bars blend from red, the
 * second from green, the last 3 from blue.
 */
static void set_pixel(uint8_t* pixel,
                      const uint32_t x,
                      const uint32_t y,
                      const uint32_t w,
                      const uint32_t h) {
    const uint32_t bar_height = (h + 8) / 9;
    const uint8_t bar = y / bar_height;
    float rgb_left[3] = {0, 0, 0};
    float rgb_right[3] = {0, 0, 0};

    rgb_left[bar / 3] = 1.0f;
    rgb_right[bar % 3] = 1.0f;

    float blend = (float)x / (float)w;

    /* Set RGB */
    for (uint8_t i = 0; i < 3; i++) {
        float v = (rgb_left[i] * (1.0f - blend)) + (rgb_right[i] * blend);
        *pixel = 255.0f * v;
        pixel++;
    }

    /* Set alpha */
    *pixel = 0xff;
}

TEST_F(secure_fb, display) {
    int rc;
    const struct secure_fb_info* fb_info = &_state->fb_info;

    ASSERT_EQ(fb_info->pixel_format, TTUI_PF_RGBA8);

    for (uint32_t y = 0; y < fb_info->height; y++) {
        uint8_t* pixel = &fb_info->buffer[y * fb_info->line_stride];

        for (uint32_t x = 0; x < fb_info->width; x++) {
            set_pixel(pixel, x, y, fb_info->width, fb_info->height);
            pixel += fb_info->pixel_stride;
        }
    }

    rc = secure_fb_display_next(_state->session, &_state->fb_info);
    ASSERT_EQ(rc, 0);

    /* Wait 2 seconds to allow screen to be viewed */
    trusty_nanosleep(0, 0, 2000 * 1000 * 1000);

test_abort:;
}

TEST(secure_fb, stress) {
    secure_fb_handle_t session = NULL;
    struct secure_fb_info fb_info;
    int i, rc;

    for (i = 0; i < 256; i++) {
        rc = secure_fb_open(&session, &fb_info, 0);
        ASSERT_EQ(rc, 0);

        /* Fill with grey level */
        memset(fb_info.buffer, i, fb_info.size);

        rc = secure_fb_display_next(session, &fb_info);
        ASSERT_EQ(rc, 0);

        secure_fb_close(session);
        session = NULL;
    }

    return;

test_abort:
    trusty_unittest_printf("[   INFO   ] failed after %d iterations\n", i);

    if (session) {
        secure_fb_close(session);
    }
}

PORT_TEST(secure_fb, "com.android.trusty.secure_fb.test");
