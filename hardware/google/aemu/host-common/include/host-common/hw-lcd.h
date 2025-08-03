/* Copyright (C) 2009 The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#pragma once

#include "aemu/base/c_header.h"

#define  LCD_DENSITY_LDPI      120
#define  LCD_DENSITY_140       140
#define  LCD_DENSITY_MDPI      160
#define  LCD_DENSITY_180       180
#define  LCD_DENSITY_200       200
#define  LCD_DENSITY_TVDPI     213
#define  LCD_DENSITY_HDPI      240
#define  LCD_DENSITY_260DPI    260
#define  LCD_DENSITY_280DPI    280
#define  LCD_DENSITY_300DPI    300
#define  LCD_DENSITY_XHDPI     320
#define  LCD_DENSITY_340DPI    340
#define  LCD_DENSITY_360DPI    360
#define  LCD_DENSITY_400DPI    400
#define  LCD_DENSITY_420DPI    420
#define  LCD_DENSITY_440DPI    440
#define  LCD_DENSITY_XXHDPI    480
#define  LCD_DENSITY_560DPI    560
#define  LCD_DENSITY_XXXHDPI   640

typedef enum hwLcd_screenSize {
    LCD_SIZE_SMALL,
    LCD_SIZE_NORMAL,
    LCD_SIZE_LARGE,
    LCD_SIZE_XLARGE
} hwLcd_screenSize_t;

ANDROID_BEGIN_HEADER

extern hwLcd_screenSize_t hwLcd_getScreenSize(int heightPx,
                                              int widthPx,
                                              int density);

/* Don't call this directly.
 * It is public only to allow unit testing.
 */
extern int hwLcd_mapDensity(int density);

ANDROID_END_HEADER
