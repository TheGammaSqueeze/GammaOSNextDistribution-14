/*
 *
 * Copyright 2019 Samsung Electronics S.LSI Co. LTD
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

/*
 * @file    VendorVideoAPI.cpp
 * @author  ByungGwan Kang (bk0917.kang@samsung.com)
 * @version 1.0
 * @history
 *   2019.08.08 : Create
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "VendorVideoAPI"

/* Check data boundary before read it */
#define CHECK_BOUNDARY(bit_offset, size) {                      \
    if ((bit_offset) > (size) * 8) {                            \
        ALOGE("[%s][%d] read bit offset(%d) > total bits(%d)",  \
              __func__, __LINE__, (bit_offset), (size) * 8);    \
        return -1;                                              \
    }                                                           \
}

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <log/log.h>

#include <VendorVideoAPI.h>

#ifdef __cplusplus
extern "C" {
#endif

int Exynos_parsing_user_data_registered_itu_t_t35 (
    ExynosHdrDynamicInfo *dest,
    void                 *src,
    int                  size)

{
    int   bit_offset = 0;
    int   data       = 0;
    ExynosHdrDynamicInfo *pHdr10PlusInfo;

    int windows = 0;
    int targeted_system_display_actual_peak_luminance_flag     = 0;
    int num_rows_targeted_system_display_actual_peak_luminance = 0;
    int num_cols_targeted_system_display_actual_peak_luminance = 0;
    int mastering_display_actual_peak_luminance_flag           = 0;
    int num_rows_mastering_display_actual_peak_luminance       = 0;
    int color_saturation_mapping_flag                          = 0;
    int num_cols_mastering_display_actual_peak_luminance       = 0;

    int extraBit, extraByte = 0;

    int i, j, k, l;

    if ((dest == NULL) || (src == NULL)) {
        ALOGE("[%s] invalid parameters", __FUNCTION__);
        return -1;
    }

    pHdr10PlusInfo = dest;

    CHECK_BOUNDARY(bit_offset + 8, size);
    /* country_code : 8bit */
    for (i = 0; i < 1; i++) {
        for (j = 0; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << j));
        }
    }
    bit_offset += 8;
    pHdr10PlusInfo->data.country_code = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 16, size);
    /* terminal_provider_code : 16bit */
    for (i = 0; i < 2; i++) {
        for (j = 0; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << j));
        }
    }
    bit_offset += 16;
    pHdr10PlusInfo->data.provider_code = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 16, size);
    /* terminal_provider_oriented_code : 16bit */
    for (i = 0; i < 2; i++) {
        for (j = 0; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << j));
        }
    }
    bit_offset += 16;
    pHdr10PlusInfo->data.provider_oriented_code = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 8, size);
    /* application_identifier : 8bit*/
    for (i = 0; i < 1; i++) {
        for (j = 0; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << j));
        }
    }
    bit_offset += 8;
    pHdr10PlusInfo->data.application_identifier = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 8, size);
    /* application_version : 8bit*/
    for (i = 0; i < 1; i++) {
        for (j = 0; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << j));
        }
    }
    bit_offset += 8;
    pHdr10PlusInfo->data.application_version = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 2, size);
    /* num_windows : 2bit*/
    for (i = 0; i < 1; i++) {
        for (j = 0; j < 2; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
        }
    }
    bit_offset += 2;
    extraBit   = (bit_offset % 8);
    data       = data >> (8 - extraBit);
    pHdr10PlusInfo->data.num_windows = data;
    data = 0;

    for (i = 1; i < pHdr10PlusInfo->data.num_windows; i++) {
        CHECK_BOUNDARY(bit_offset + 16, size);
        /* window_upper_left_corner_x : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.window_upper_left_corner_x[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* window_upper_left_corner_y : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.window_upper_left_corner_y[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* window_lower_right_corner_x : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.window_lower_right_corner_x[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* window_lower_right_corner_y : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.window_lower_right_corner_y[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* center_of_ellipse_x : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.center_of_ellipse_x[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* center_of_ellipse_y : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.center_of_ellipse_y[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 8, size);
        /* rotation_angle : 8bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 1 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 8;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.rotation_angle[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* semimajor_axis_internal_ellipse : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.semimajor_axis_internal_ellipse[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* semimajor_axis_external_ellipse : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.semimajor_axis_external_ellipse[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 16, size);
        /* semiminor_axis_external_ellipse : 16bit */
        if (extraBit > 0)
            extraByte = 1;

        for (j = 0; j < 2 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 16;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.semiminor_axis_external_ellipse[i - 1] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 1, size);
        /* overlap_process_option : 1bit */
        data |= (*((char *)src + (bit_offset / 8)) & (1 << (7 - extraBit)));

        bit_offset  += 1;
        extraByte    = 0;
        extraBit     = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.overlap_process_option[i - 1] = data;
        data = 0;
    }

    CHECK_BOUNDARY(bit_offset + 27, size);
    /* targeted_system_display_maximum_luminance : 27bit */
    if (extraBit > 5)
        extraByte = 2;
    else if (extraBit <= 5)
        extraByte = 1;

    for (i = 0; i < 3 + extraByte; i++) {
        for (j = extraBit; j < 8; j++) {
            data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
        }

        if (i < 3 + extraByte - 1)
            data = data << 8;

        extraBit += (j - extraBit);
        extraBit %= 8;
    }
    bit_offset += 27;
    extraByte   = 0;
    extraBit    = bit_offset % 8;
    data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
    pHdr10PlusInfo->data.targeted_system_display_maximum_luminance = data;
    data = 0;

    CHECK_BOUNDARY(bit_offset + 1, size);
    /* targeted_system_display_actual_peak_luminance_flag : 1bit */
    data |= (*((char *)src + (bit_offset / 8)) & (1 << (7 - extraBit)));

    bit_offset  += 1;
    extraBit     = bit_offset % 8;
    data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
    pHdr10PlusInfo->data.targeted_system_display_actual_peak_luminance_flag = data;
    targeted_system_display_actual_peak_luminance_flag = data;
    data = 0;

    if (targeted_system_display_actual_peak_luminance_flag) {
        CHECK_BOUNDARY(bit_offset + 5, size);
        /* num_rows_targeted_system_display_actual_peak_luminance : 5bit */
        if (extraBit > 3)
            extraByte = 1;

        for (i = 0; i < 1 + extraByte; i++) {
            for (j = extraBit; j < 8; j++) {
                data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
            }

            if (i < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (j - extraBit);
            extraBit %= 8;
        }
        bit_offset += 5;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.num_rows_targeted_system_display_actual_peak_luminance = data;
        num_rows_targeted_system_display_actual_peak_luminance = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 5, size);
        /* num_cols_targeted_system_display_actual_peak_luminance : 5bit */
        if (extraBit > 3)
            extraByte = 1;

        for (i = 0; i < 1 + extraByte; i++) {
            for (j = extraBit; j < 8; j++) {
                data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
            }

            if (i < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (j - extraBit);
            extraBit %= 8;
        }
        bit_offset += 5;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.num_cols_targeted_system_display_actual_peak_luminance = data;
        num_cols_targeted_system_display_actual_peak_luminance = data;
        data = 0;

        for (i = 0; i < num_rows_targeted_system_display_actual_peak_luminance; i++) {
            for (j = 0; j < num_cols_targeted_system_display_actual_peak_luminance; j++) {
                CHECK_BOUNDARY(bit_offset + 4, size);
                /* targeted_system_display_actual_peak_luminance : 4bit */
                if (extraBit > 4)
                    extraByte = 1;

                for (k = 0; k < 1 + extraByte; k++) {
                    for (l = extraBit; l < 8; l++) {
                        data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                    }

                    if (k < 1 + extraByte - 1)
                        data = data << 8;

                    extraBit += (l - extraBit);
                    extraBit %= 8;
                }
                bit_offset += 4;
                extraByte   = 0;
                extraBit    = bit_offset % 8;
                data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
                pHdr10PlusInfo->data.targeted_system_display_actual_peak_luminance[i][j] = data;
                data = 0;
            }
        }
    }

    for (i = 0; i < pHdr10PlusInfo->data.num_windows; i++) {
        for (j = 0; j < 3; j++) {
            CHECK_BOUNDARY(bit_offset + 17, size);
            /* maxscl : 17bit */
            for (k = 0; k < 3; k++) {
                for (l = extraBit; l < 8; l++) {
                    data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                }

                if (k < 2)
                    data = data << 8;

                extraBit += (l - extraBit);
                extraBit %= 8;
            }
            bit_offset += 17;
            extraBit    = bit_offset % 8;

            if (extraBit != 0)
                data = data >> (8 - extraBit);

            pHdr10PlusInfo->data.maxscl[i][j] = data;
            data = 0;
        }

        CHECK_BOUNDARY(bit_offset + 17, size);
        /* average_maxrgb : 17bit */
        for (j = 0; j < 3; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 2)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 17;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.average_maxrgb[i] = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 4, size);
        /* num_distribution_maxrgb_percentiles : 4bit */
        if (extraBit > 4)
            extraByte = 1;

        for (j = 0; j < 1 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 4;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.num_maxrgb_percentiles[i] = data;
        data = 0;

        for (j = 0; j < pHdr10PlusInfo->data.num_maxrgb_percentiles[i]; j++) {
            CHECK_BOUNDARY(bit_offset + 7, size);
            /* distribution_maxrgb_percentages : 7bit */
            if (extraBit > 1)
                extraByte = 1;

            for (k = 0; k < 1 + extraByte; k++) {
                for (l = extraBit; l < 8; l++) {
                    data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                }

                if (k < 1 + extraByte - 1)
                    data = data << 8;

                extraBit += (l - extraBit);
                extraBit %= 8;
            }
            bit_offset += 7;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.maxrgb_percentages[i][j] = data;
            data = 0;

            CHECK_BOUNDARY(bit_offset + 17, size);
            /* distribution_maxrgb_percentiles : 17bit */
            if (extraBit >= 0)
                extraByte = 1;

            for (k = 0; k < 2 + extraByte; k++) {
                for (l = extraBit; l < 8; l++) {
                    data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                }

                if (k < 2 + extraByte - 1)
                    data = data << 8;

                extraBit += (l - extraBit);
                extraBit %= 8;
            }
            bit_offset += 17;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.maxrgb_percentiles[i][j] = data;
            data = 0;
        }

        CHECK_BOUNDARY(bit_offset + 10, size);
        /* fraction_bright_pixels : 10bit*/
        if (extraBit > 6)
            extraByte = 2;
        else if (extraBit <= 6)
            extraByte = 1;

        for (j = 0; j < 1 + extraByte; j++) {
            for (k = extraBit; k < 8; k++) {
                data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
            }

            if (j < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (k - extraBit);
            extraBit %= 8;
        }
        bit_offset += 10;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.fraction_bright_pixels[i] = data;
        data = 0;
    }

    CHECK_BOUNDARY(bit_offset + 1, size);
    /* mastering_display_actual_peak_luminance_flag : 1bit */
    data |= (*((char *)src + (bit_offset / 8)) & (1 << (7 - extraBit)));

    bit_offset  += 1;
    extraBit     = bit_offset % 8;
    data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
    pHdr10PlusInfo->data.mastering_display_actual_peak_luminance_flag = data;
    mastering_display_actual_peak_luminance_flag = data;
    data = 0;

    if (mastering_display_actual_peak_luminance_flag) {
        CHECK_BOUNDARY(bit_offset + 5, size);
        /* num_rows_mastering_display_actual_peak_luminance : 5bit */
        if (extraBit > 3)
            extraByte = 1;

        for (i = 0; i < 1 + extraByte; i++) {
            for (j = extraBit; j < 8; j++) {
                data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
            }

            if (i < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (j - extraBit);
            extraBit %= 8;
        }
        bit_offset += 5;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.num_rows_mastering_display_actual_peak_luminance = data;
        num_rows_mastering_display_actual_peak_luminance = data;
        data = 0;

        CHECK_BOUNDARY(bit_offset + 5, size);
        /* num_cols_mastering_display_actual_peak_luminance : 5bit */
        if (extraBit > 3)
            extraByte = 1;

        for (i = 0; i < 1 + extraByte; i++) {
            for (j = extraBit; j < 8; j++) {
                data |= (*((char *)src + (bit_offset / 8) + i) & (1 << (7 - j)));
            }

            if (i < 1 + extraByte - 1)
                data = data << 8;

            extraBit += (j - extraBit);
            extraBit %= 8;
        }
        bit_offset += 5;
        extraByte   = 0;
        extraBit    = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.num_cols_mastering_display_actual_peak_luminance = data;
        num_cols_mastering_display_actual_peak_luminance = data;
        data = 0;

        for (i = 0; i < num_rows_mastering_display_actual_peak_luminance; i++) {
            for (j = 0; j < num_cols_mastering_display_actual_peak_luminance; j++) {
                CHECK_BOUNDARY(bit_offset + 4, size);
                /* mastering_display_actual_peak_luminance : 4bit */
                if (extraBit > 4)
                    extraByte = 1;

                for (k = 0; k < 1 + extraByte; k++) {
                    for (l = extraBit; l < 8; l++) {
                        data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                    }

                    if (k < 1 + extraByte - 1)
                        data = data << 8;

                    extraBit += (l - extraBit);
                    extraBit %= 8;
                }
                bit_offset += 4;
                extraByte   = 0;
                extraBit    = bit_offset % 8;
                data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
                pHdr10PlusInfo->data.mastering_display_actual_peak_luminance[i][j] = data;
                data = 0;
            }
        }
    }

    for (i = 0; i < pHdr10PlusInfo->data.num_windows; i++) {
        CHECK_BOUNDARY(bit_offset + 1, size);
        /* tone_mapping_flag : 1bit */
        data |= (*((char *)src + (bit_offset / 8)) & (1 << (7 - extraBit)));

        bit_offset  += 1;
        extraBit     = bit_offset % 8;
        data         = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.tone_mapping.tone_mapping_flag[i] = data;
        data = 0;

        if (pHdr10PlusInfo->data.tone_mapping.tone_mapping_flag[i]) {
            CHECK_BOUNDARY(bit_offset + 12, size);
            /* knee_point_x : 12bit */
            if (extraBit > 4)
                extraByte = 2;
            else if (extraBit <= 4)
                extraByte = 1;

            for (j = 0; j < 1 + extraByte; j++) {
                for (k = extraBit; k < 8; k++) {
                    data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
                }

                if (j < 1 + extraByte - 1)
                    data = data << 8;

                extraBit += (k - extraBit);
                extraBit %= 8;
            }
            bit_offset += 12;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.tone_mapping.knee_point_x[i] = data;
            data = 0;

            CHECK_BOUNDARY(bit_offset + 12, size);
            /* knee_point_y : 12bit */
            if (extraBit > 4)
                extraByte = 2;
            else if (extraBit <= 4)
                extraByte = 1;

            for (j = 0; j < 1 + extraByte; j++) {
                for (k = extraBit; k < 8; k++) {
                    data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
                }

                if (j < 1 + extraByte - 1)
                    data = data << 8;

                extraBit += (k - extraBit);
                extraBit %= 8;
            }
            bit_offset += 12;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.tone_mapping.knee_point_y[i] = data;
            data = 0;

            CHECK_BOUNDARY(bit_offset + 4, size);
            /* num_bezier_curve_anchors : 4bit */
            if (extraBit > 4)
                extraByte = 1;

            for (j = 0; j < 1 + extraByte; j++) {
                for (k = extraBit; k < 8; k++) {
                    data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
                }

                if (j < 1 + extraByte - 1)
                    data = data << 8;

                extraBit += (k - extraBit);
                extraBit %= 8;
            }
            bit_offset += 4;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.tone_mapping.num_bezier_curve_anchors[i] = data;
            data = 0;

            for (j = 0; j < pHdr10PlusInfo->data.tone_mapping.num_bezier_curve_anchors[i]; j++) {
                CHECK_BOUNDARY(bit_offset + 10, size);
                /* bezier_curve_anchors : 10bit */
                if (extraBit > 6)
                    extraByte = 2;
                else if (extraBit <= 6)
                    extraByte = 1;

                for (k = 0; k < 1 + extraByte; k++) {
                    for (l = extraBit; l < 8; l++) {
                        data |= (*((char *)src + (bit_offset / 8) + k) & (1 << (7 - l)));
                    }

                    if (k < 1 + extraByte - 1)
                        data = data << 8;

                    extraBit += (l - extraBit);
                    extraBit %= 8;
                }
                bit_offset += 10;
                extraByte   = 0;
                extraBit    = bit_offset % 8;
                data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
                pHdr10PlusInfo->data.tone_mapping.bezier_curve_anchors[i][j] = data;
                data = 0;
            }
        }

        CHECK_BOUNDARY(bit_offset + 1, size);
        /* color_saturation_mapping_flag : 1bit */
        data |= (*((char *)src + (bit_offset / 8)) & (1 << (7 - extraBit)));

        bit_offset  += 1;
        extraByte    = 0;
        extraBit     = bit_offset % 8;
        data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
        pHdr10PlusInfo->data.color_saturation_mapping_flag[i] = data;
        data = 0;

        if (pHdr10PlusInfo->data.color_saturation_mapping_flag[i]) {
            CHECK_BOUNDARY(bit_offset + 6, size);
            /* color_saturation_weight : 6bit */
            if (extraBit > 2)
                extraByte = 1;

            for (j = 0; j < 1 + extraByte; j++) {
                for (k = extraBit; k < 8; k++) {
                    data |= (*((char *)src + (bit_offset / 8) + j) & (1 << (7 - k)));
                }

                if (j < 1 + extraByte - 1)
                    data = data << 8;

                extraBit += (k - extraBit);
                extraBit %= 8;
            }
            bit_offset += 6;
            extraByte   = 0;
            extraBit    = bit_offset % 8;
            data        = (extraBit != 0) ? (data >> (8 - extraBit)) : data;
            pHdr10PlusInfo->data.color_saturation_weight[i] = data;
            data = 0;
        }
    }

    return 0;
}

int Exynos_dynamic_meta_to_itu_t_t35 (
    ExynosHdrDynamicInfo *src,
    char                 *dst)
{
    ExynosHdrDynamicInfo *pHDRDynamicInfo = NULL;
    char                 *pBlob           = NULL;

    int size         = 0;
    int bit_offset   = 0;
    int extraBit     = 0;
    int extraByte    = 0;
    int offset_limit = 0;

    int buffer     = 0;
    int tempBuffer = 0;

    int i, j, k, l;

    if ((src == NULL) || (dst == NULL)) {
        ALOGE("[%s] invalid parameters", __FUNCTION__);
        return -1;
    }

    pHDRDynamicInfo = src;
    pBlob           = dst;

    /* country_code: 8bit */
    offset_limit = bit_offset + 8;

    for (i = 0; i < 8; i++) {
        *((char *)pBlob + (bit_offset / 8)) |=
            (*((char *)&pHDRDynamicInfo->data.country_code) & (1 << (7 - i)));
        bit_offset++;

        if (bit_offset == offset_limit)
            break;
    }
    extraBit = (bit_offset % 8);

    /* terminal_provider_code: 16bit */
    offset_limit = bit_offset + 16;

    for (i = 0; i < 2; i++) {
        for (j = 0; j < 8; j++) {
            *((char *)pBlob + (bit_offset / 8)) |=
                (*((char *)&pHDRDynamicInfo->data.provider_code + 1 - i) & (1 << (7 - j)));
            bit_offset++;

            if (bit_offset == offset_limit)
                break;
        }
    }
    extraBit = (bit_offset % 8);

    /* terminal_provider_oriented_code: 16bit */
    offset_limit = bit_offset + 16;

    for (i = 0; i < 2; i++) {
        for (j = 0; j < 8; j++) {
            *((char *)pBlob + (bit_offset / 8)) |=
                (*((char *)&pHDRDynamicInfo->data.provider_oriented_code + 1 - i) & (1 << (7 - j)));
            bit_offset++;

            if (bit_offset == offset_limit)
                break;
        }
    }
    extraBit = (bit_offset % 8);

    /* application_identifier: 8bit */
    offset_limit = bit_offset + 8;

    for (i = 0; i < 8; i++) {
        *((char *)pBlob + (bit_offset / 8)) |=
            (*((char *)&pHDRDynamicInfo->data.application_identifier) & (1 << (7 - i)));
        bit_offset++;

        if (bit_offset == offset_limit)
            break;
    }
    extraBit = (bit_offset % 8);

    /* application_version: 8bit */
    offset_limit = bit_offset + 8;

    for (i = 0; i < 8; i++) {
        *((char *)pBlob + (bit_offset / 8)) |=
            (*((char *)&pHDRDynamicInfo->data.application_version) & (1 << (7 - i)));
        bit_offset++;

        if (bit_offset == offset_limit)
            break;
    }
    extraBit = (bit_offset % 8);

    /* num_windows: 2bit */
    offset_limit = bit_offset + 2;

    tempBuffer = pHDRDynamicInfo->data.num_windows << (6 - extraBit);
    for (i = 0; i < 2; i++) {
        /* num_windows is always 1 now */
        *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&tempBuffer) & (1 << (7 - i)));
        bit_offset++;

        if (bit_offset == offset_limit)
            break;
    }
    extraBit = (bit_offset % 8);
    tempBuffer = 0;

    for (i = 1; i < pHDRDynamicInfo->data.num_windows; i++) {
        /* window_upper_left_corner_x: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.window_upper_left_corner_x[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* window_upper_left_corner_y: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.window_upper_left_corner_y[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* window_lower_right_corner_x: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.window_lower_right_corner_x[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* window_lower_right_corner_y: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.window_lower_right_corner_y[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* center_of_ellipse_x: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.center_of_ellipse_x[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* center_of_ellipse_y: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.center_of_ellipse_y[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* rotation_angle: 8bit */
        offset_limit = bit_offset + 8;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.rotation_angle[i - 1];
        tempBuffer = tempBuffer << (24 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (1 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* semimajor_axis_internal_ellipse: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.semimajor_axis_internal_ellipse[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* semimajor_axis_external_ellipse: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.semimajor_axis_external_ellipse[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* semiminor_axis_external_ellipse: 16bit */
        offset_limit = bit_offset + 16;

        if (extraBit > 0)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.semiminor_axis_external_ellipse[i - 1];
        tempBuffer = tempBuffer << (16 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* overlap_process_option: 1bit */
        if (pHDRDynamicInfo->data.overlap_process_option[i - 1]) {
            *((char *)pBlob + (bit_offset / 8)) |= (1 << (7 - extraBit));
        }

        bit_offset += 1;
        extraBit = (bit_offset % 8);
    }

    /* targeted_system_display_maximum_luminance: 27bit */
    offset_limit = bit_offset + 27;

    tempBuffer = pHDRDynamicInfo->data.targeted_system_display_maximum_luminance;
    tempBuffer = tempBuffer << (5 - extraBit);
    for (i = 0; i < 4; i++) {
        memcpy((char *)&buffer + i, (char *)&tempBuffer + (3 - i), sizeof(char));
    }

    for (i = 0; i < 4; i++) {
        for (j = extraBit; j < 8; j++) {
            *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + i) & (1 << (7 - j)));
            bit_offset++;

            if (bit_offset == offset_limit)
                break;
        }
        extraBit = (bit_offset % 8);
    }
    buffer     = 0;
    tempBuffer = 0;

    /* targeted_system_display_actual_peak_luminance_flag: 1bit */
    if (pHDRDynamicInfo->data.targeted_system_display_actual_peak_luminance_flag) {
        *((char *)pBlob + (bit_offset / 8)) |= (1 << (7 - extraBit));
    }
    bit_offset += 1;
    extraBit = (bit_offset % 8);

    if (pHDRDynamicInfo->data.targeted_system_display_actual_peak_luminance_flag) {
        /* num_rows_targeted_system_display_actual_peak_luminance: 5bit */
        offset_limit = bit_offset + 5;

        if (extraBit > 3)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.num_rows_targeted_system_display_actual_peak_luminance;
        tempBuffer = tempBuffer << (27 - extraBit);
        for (i = 0; i < 4; i++) {
            memcpy((char *)&buffer + i, (char *)&tempBuffer + (3 - i), sizeof(char));
        }

        for (i = 0; i < (1 + extraByte); i++) {
            for (j = extraBit; j < 8; j++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + i) & (1 << (7 - j)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* num_cols_targeted_system_display_actual_peak_luminance: 5bit */
        offset_limit = bit_offset + 5;

        if (extraBit > 3)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.num_cols_targeted_system_display_actual_peak_luminance;
        tempBuffer = tempBuffer << (27 - extraBit);
        for (i = 0; i < 4; i++) {
            memcpy((char *)&buffer + i, (char *)&tempBuffer + (3 - i), sizeof(char));
        }

        for (i = 0; i < (1 + extraByte); i++) {
            for (j = extraBit; j < 8; j++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + i) & (1 << (7 - j)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* targeted_system_display_actual_peak_luminance[row][col]: 4bit */
        for (i = 0; i < pHDRDynamicInfo->data.num_rows_targeted_system_display_actual_peak_luminance; i++) {
            for (j = 0; j < pHDRDynamicInfo->data.num_cols_targeted_system_display_actual_peak_luminance; j++) {
                offset_limit = bit_offset + 4;

                if (extraBit > 4)
                    extraByte = 1;

                tempBuffer = pHDRDynamicInfo->data.targeted_system_display_actual_peak_luminance[i][j];
                tempBuffer = tempBuffer << (28 - extraBit);
                for (k = 0; k < 4; k++) {
                    memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
                }

                for (k = 0; k < (1 + extraByte); k++) {
                    for (l = extraBit; l < 8; l++) {
                        *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                        bit_offset++;

                        if (bit_offset == offset_limit) {
                            break;
                        }
                    }
                    extraBit = (bit_offset % 8);
                }
                buffer     = 0;
                tempBuffer = 0;
                extraByte  = 0;
            }
        }
    }

    for (i = 0; i < pHDRDynamicInfo->data.num_windows; i++) {
        /* maxscl: 17bit */
        for (j = 0; j < 3; j++) {
            offset_limit = bit_offset + 17;

            tempBuffer = pHDRDynamicInfo->data.maxscl[i][j] << (15 - extraBit);
            for (k = 0; k < 4; k++) {
                memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
            }

            for (k = 0; k < 3; k++) {
                for (l = extraBit; l < 8; l++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                    bit_offset++;

                    if (bit_offset == offset_limit)
                        break;
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
        }

        /* average_maxrgb: 17bit */
        offset_limit = bit_offset + 17;

        tempBuffer = pHDRDynamicInfo->data.average_maxrgb[i] << (15 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < 3; j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit)
                    break;
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;

        /* num_distribution_maxrgb_percentiles: 4bit */
        offset_limit = bit_offset + 4;

        if (extraBit > 4)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.num_maxrgb_percentiles[i] << (28 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (1 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit)
                    break;
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        for (j = 0; j < pHDRDynamicInfo->data.num_maxrgb_percentiles[i]; j++) {
            /* distribution_maxrgb_percentaged: 7bit */
            offset_limit = bit_offset + 7;

            if (extraBit > 1)
                extraByte = 1;

            tempBuffer = pHDRDynamicInfo->data.maxrgb_percentages[i][j];
            tempBuffer = tempBuffer << (25 - extraBit);
            for (k = 0; k < 4; k++) {
                memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
            }

            for (k = 0; k < (1 + extraByte); k++) {
                for (l = extraBit; l < 8; l++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
            extraByte  = 0;

            /* distribution_maxrgb_percentiles: 17bit */
            offset_limit = bit_offset + 17;

            tempBuffer = pHDRDynamicInfo->data.maxrgb_percentiles[i][j] << (15 - extraBit);
            for (k = 0; k < 4; k++) {
                memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
            }

            for (k = 0; k < 3; k++) {
                for (l = extraBit; l < 8; l++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
        }

        /* fraction_bright_pixels: 10bit */
        if (extraBit > 6)
            extraByte = 1;

        offset_limit = bit_offset + 10;

        tempBuffer = pHDRDynamicInfo->data.fraction_bright_pixels[i];
        tempBuffer = tempBuffer << (22 - extraBit);
        for (j = 0; j < 4; j++) {
            memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
        }

        for (j = 0; j < (2 + extraByte); j++) {
            for (k = extraBit; k < 8; k++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;
    }

    /* mastering_display_actual_peak_luminance_flag: 1bit */
    if (pHDRDynamicInfo->data.mastering_display_actual_peak_luminance_flag) {
        *((char *)pBlob + (bit_offset / 8)) |= (1 << (7 - extraBit));
    }
    bit_offset += 1;
    extraBit = (bit_offset % 8);

    if (pHDRDynamicInfo->data.mastering_display_actual_peak_luminance_flag) {
        /* num_rows_mastering_display_actual_peak_luminance: 5bit */
        offset_limit = bit_offset + 5;

        if (extraBit > 3)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.num_rows_mastering_display_actual_peak_luminance;
        tempBuffer = tempBuffer << (27 - extraBit);
        for (i = 0; i < 4; i++) {
            memcpy((char *)&buffer + i, (char *)&tempBuffer + (3 - i), sizeof(char));
        }

        for (i = 0; i < (1 + extraByte); i++) {
            for (j = extraBit; j < 8; j++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + i) & (1 << (7 - j)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* num_cols_mastering_display_actual_peak_luminance: 5bit */
        offset_limit = bit_offset + 5;

        if (extraBit > 3)
            extraByte = 1;

        tempBuffer = pHDRDynamicInfo->data.num_cols_mastering_display_actual_peak_luminance;
        tempBuffer = tempBuffer << (27 - extraBit);
        for (i = 0; i < 4; i++) {
            memcpy((char *)&buffer + i, (char *)&tempBuffer + (3 - i), sizeof(char));
        }

        for (i = 0; i < (1 + extraByte); i++) {
            for (j = extraBit; j < 8; j++) {
                *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + i) & (1 << (7 - j)));
                bit_offset++;

                if (bit_offset == offset_limit) {
                    break;
                }
            }
            extraBit = (bit_offset % 8);
        }
        buffer     = 0;
        tempBuffer = 0;
        extraByte  = 0;

        /* mastering_display_actual_peak_luminance[row][col]: 4bit */
        for (i = 0; i < pHDRDynamicInfo->data.num_rows_mastering_display_actual_peak_luminance; i++) {
            for (j = 0; j < pHDRDynamicInfo->data.num_cols_mastering_display_actual_peak_luminance; j++) {
                offset_limit = bit_offset + 4;

                if (extraBit > 4)
                    extraByte = 1;

                tempBuffer = pHDRDynamicInfo->data.mastering_display_actual_peak_luminance[i][j];
                tempBuffer = tempBuffer << (28 - extraBit);
                for (k = 0; k < 4; k++) {
                    memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
                }

                for (k = 0; k < (1 + extraByte); k++) {
                    for (l = extraBit; l < 8; l++) {
                        *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                        bit_offset++;

                        if (bit_offset == offset_limit) {
                            break;
                        }
                    }
                    extraBit = (bit_offset % 8);
                }
                buffer     = 0;
                tempBuffer = 0;
                extraByte  = 0;
            }
        }
    }

    for (i = 0; i < pHDRDynamicInfo->data.num_windows; i++) {
        /* tone_mapping_flag: 1bit */
        if (pHDRDynamicInfo->data.tone_mapping.tone_mapping_flag[i]) {
            *((char *)pBlob + (bit_offset / 8)) |= (1 << (7 - extraBit));
        }
        bit_offset += 1;
        extraBit = (bit_offset % 8);

        if (pHDRDynamicInfo->data.tone_mapping.tone_mapping_flag[i]) {
            /* knee_point_x: 12bit */
            if (extraBit > 4)
                extraByte = 1;

            offset_limit = bit_offset + 12;

            tempBuffer = pHDRDynamicInfo->data.tone_mapping.knee_point_x[i];
            tempBuffer = tempBuffer << (20 - extraBit);
            for (j = 0; j < 4; j++) {
                memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
            }

            for (j = 0; j < (2 + extraByte); j++) {
                for (k = extraBit; k < 8; k++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
            extraByte  = 0;

            /* knee_point_y: 12bit */
            if (extraBit > 4)
                extraByte = 1;

            offset_limit = bit_offset + 12;

            tempBuffer = pHDRDynamicInfo->data.tone_mapping.knee_point_y[i];
            tempBuffer = tempBuffer << (20 - extraBit);
            for (j = 0; j < 4; j++) {
                memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
            }

            for (j = 0; j < (2 + extraByte); j++) {
                for (k = extraBit; k < 8; k++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
            extraByte  = 0;

            /* num_bezier_curve_anchors: 4bit */
            if (extraBit > 4)
                extraByte = 1;

            offset_limit = bit_offset + 4;

            tempBuffer = pHDRDynamicInfo->data.tone_mapping.num_bezier_curve_anchors[i];
            tempBuffer = tempBuffer << (28 - extraBit);
            for (j = 0; j < 4; j++) {
                memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
            }

            for (j = 0; j < (1 + extraByte); j++) {
                for (k = extraBit; k < 8; k++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
            extraByte  = 0;

            for (j = 0; j < pHDRDynamicInfo->data.tone_mapping.num_bezier_curve_anchors[i]; j++) {
                /* bezier_curve_anchors: 10bit */
                if (extraBit > 6)
                    extraByte = 1;

                offset_limit = bit_offset + 10;

                tempBuffer = pHDRDynamicInfo->data.tone_mapping.bezier_curve_anchors[i][j];
                tempBuffer = tempBuffer << (22 - extraBit);
                for (k = 0; k < 4; k++) {
                    memcpy((char *)&buffer + k, (char *)&tempBuffer + (3 - k), sizeof(char));
                }

                for (k = 0; k < (2 + extraByte); k++) {
                    for (l = extraBit; l < 8; l++) {
                        *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + k) & (1 << (7 - l)));
                        bit_offset++;

                        if (bit_offset == offset_limit) {
                            break;
                        }
                    }
                    extraBit = (bit_offset % 8);
                }
                buffer     = 0;
                tempBuffer = 0;
                extraByte  = 0;
            }
        }

        /* color_saturation_mapping_flag: 1bit */
        if (pHDRDynamicInfo->data.color_saturation_mapping_flag[i]) {
            *((char *)pBlob + (bit_offset / 8)) |= (1 << (7 - extraBit));
        }
        bit_offset += 1;
        extraBit = (bit_offset % 8);

        if (pHDRDynamicInfo->data.color_saturation_mapping_flag[i]) {
            /* color_saturation_weight: 6bit */
            if (extraBit > 2)
                extraByte = 1;

            offset_limit = bit_offset + 6;

            tempBuffer = pHDRDynamicInfo->data.color_saturation_weight[i];
            tempBuffer = tempBuffer << (26 - extraBit);
            for (j = 0; j < 4; j++) {
                memcpy((char *)&buffer + j, (char *)&tempBuffer + (3 - j), sizeof(char));
            }

            for (j = 0; j < (1 + extraByte); j++) {
                for (k = extraBit; k < 8; k++) {
                    *((char *)pBlob + (bit_offset / 8)) |= (*((char *)&buffer + j) & (1 << (7 - k)));
                    bit_offset++;

                    if (bit_offset == offset_limit) {
                        break;
                    }
                }
                extraBit = (bit_offset % 8);
            }
            buffer     = 0;
            tempBuffer = 0;
            extraByte  = 0;
        }
    }

    if(extraBit > 0) {
        size = (bit_offset / 8) + 1;
    } else {
        size = (bit_offset / 8);
    }

    return size;
}

#ifdef __cplusplus
}
#endif
