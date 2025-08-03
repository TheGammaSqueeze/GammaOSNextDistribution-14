/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
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

// To prevent build conflict with the previous libhwjpeg
#ifndef __HARDWARE_EXYNOS_EXYNOS_EXIF_H__
#define __HARDWARE_EXYNOS_EXYNOS_EXIF_H__

#include <math.h>

#include <sys/types.h>

#define EXIF_LOG2(x)                    (log((double)(x)) / log(2.0))
#define APEX_FNUM_TO_APERTURE(x)        ((EXIF_LOG2((double)(x))) * 2.0)
#define APEX_EXPOSURE_TO_SHUTTER(x)     ((x) >= 1 ?                                 \
                                        (int)(-(EXIF_LOG2((double)(x)) + 0.5)) :    \
                                        (int)(-(EXIF_LOG2((double)(x)) - 0.5)))
#define APEX_ISO_TO_FILMSENSITIVITY(x)  ((int)(EXIF_LOG2((x) / 3.125) + 0.5))

#define NUM_SIZE                    2
#define IFD_SIZE                    12
#define OFFSET_SIZE                 4
#define EXIF_INFO_LIMIT_SIZE        12288

#define NUM_0TH_IFD_TIFF            10
#define NUM_0TH_IFD_EXIF            29
#define NUM_0TH_IFD_INTEROPERABILITY (2)
#define NUM_0TH_IFD_GPS             10
#define NUM_1TH_IFD_TIFF            9

/* Type */
#define EXIF_TYPE_BYTE              1
#define EXIF_TYPE_ASCII             2
#define EXIF_TYPE_SHORT             3
#define EXIF_TYPE_LONG              4
#define EXIF_TYPE_RATIONAL          5
#define EXIF_TYPE_UNDEFINED         7
#define EXIF_TYPE_SLONG             9
#define EXIF_TYPE_SRATIONAL         10

#define EXIF_FILE_SIZE              28800

/* 0th IFD TIFF Tags */
#define EXIF_TAG_IMAGE_WIDTH                    0x0100
#define EXIF_TAG_IMAGE_HEIGHT                   0x0101
#define EXIF_TAG_MAKE                           0x010f
#define EXIF_TAG_MODEL                          0x0110
#define EXIF_TAG_ORIENTATION                    0x0112
#define EXIF_TAG_SOFTWARE                       0x0131
#define EXIF_TAG_DATE_TIME                      0x0132
#define EXIF_TAG_YCBCR_POSITIONING              0x0213
#define EXIF_TAG_EXIF_IFD_POINTER               0x8769
#define EXIF_TAG_GPS_IFD_POINTER                0x8825

/* 0th IFD Exif Private Tags */
#define EXIF_TAG_EXPOSURE_TIME                  0x829A
#define EXIF_TAG_FNUMBER                        0x829D
#define EXIF_TAG_EXPOSURE_PROGRAM               0x8822
#define EXIF_TAG_ISO_SPEED_RATING               0x8827
#define EXIF_TAG_EXIF_VERSION                   0x9000
#define EXIF_TAG_DATE_TIME_ORG                  0x9003
#define EXIF_TAG_DATE_TIME_DIGITIZE             0x9004
#define EXIF_TAG_COMPONENTS_CONFIGURATION       0x9101
#define EXIF_TAG_SHUTTER_SPEED                  0x9201
#define EXIF_TAG_APERTURE                       0x9202
#define EXIF_TAG_BRIGHTNESS                     0x9203
#define EXIF_TAG_EXPOSURE_BIAS                  0x9204
#define EXIF_TAG_MAX_APERTURE                   0x9205
#define EXIF_TAG_METERING_MODE                  0x9207
#define EXIF_TAG_FLASH                          0x9209
#define EXIF_TAG_FOCAL_LENGTH                   0x920A
#define EXIF_TAG_MAKER_NOTE                     0x927C
#define EXIF_TAG_USER_COMMENT                   0x9286
#define EXIF_TAG_SUBSEC_TIME                    0x9290
#define EXIF_TAG_SUBSEC_TIME_ORIG               0x9291
#define EXIF_TAG_SUBSEC_TIME_DIG                0x9292
#define EXIF_TAG_FLASHPIX_VERSION               0xA000
#define EXIF_TAG_COLOR_SPACE                    0xA001
#define EXIF_TAG_PIXEL_X_DIMENSION              0xA002
#define EXIF_TAG_PIXEL_Y_DIMENSION              0xA003
#define EXIF_TAG_RELATED_SOUND_FILE             0xA004
#define EXIF_TAG_INTEROPERABILITY               0xA005
#define EXIF_TAG_SCENE_TYPE                     0xA301
#define EXIF_TAG_CUSTOM_RENDERED                0xA401
#define EXIF_TAG_EXPOSURE_MODE                  0xA402
#define EXIF_TAG_WHITE_BALANCE                  0xA403
#define EXIF_TAG_DIGITAL_ZOOM_RATIO             0xA404
#define EXIF_TAG_FOCA_LENGTH_IN_35MM_FILM       0xA405
#define EXIF_TAG_SCENCE_CAPTURE_TYPE            0xA406
#define EXIF_TAG_CONTRAST                       0xA408
#define EXIF_TAG_SATURATION                     0xA409
#define EXIF_TAG_SHARPNESS                      0xA40A
#define EXIF_TAG_IMAGE_UNIQUE_ID                0xA420

/* 0th IFD Interoperability Info Tags */
#define EXIF_TAG_INTEROPERABILITY_INDEX         0x0001
#define EXIF_TAG_INTEROPERABILITY_VERSION       0x0002

/* 0th IFD GPS Info Tags */
#define EXIF_TAG_GPS_VERSION_ID                 0x0000
#define EXIF_TAG_GPS_LATITUDE_REF               0x0001
#define EXIF_TAG_GPS_LATITUDE                   0x0002
#define EXIF_TAG_GPS_LONGITUDE_REF              0x0003
#define EXIF_TAG_GPS_LONGITUDE                  0x0004
#define EXIF_TAG_GPS_ALTITUDE_REF               0x0005
#define EXIF_TAG_GPS_ALTITUDE                   0x0006
#define EXIF_TAG_GPS_TIMESTAMP                  0x0007
#define EXIF_TAG_GPS_PROCESSING_METHOD          0x001B
#define EXIF_TAG_GPS_DATESTAMP                  0x001D

/* 1th IFD TIFF Tags */
#define EXIF_TAG_COMPRESSION_SCHEME             0x0103
#define EXIF_TAG_X_RESOLUTION                   0x011A
#define EXIF_TAG_Y_RESOLUTION                   0x011B
#define EXIF_TAG_RESOLUTION_UNIT                0x0128
#define EXIF_TAG_JPEG_INTERCHANGE_FORMAT        0x0201
#define EXIF_TAG_JPEG_INTERCHANGE_FORMAT_LEN    0x0202

typedef enum {
    EXIF_ORIENTATION_UP     = 1,
    EXIF_ORIENTATION_90     = 6,
    EXIF_ORIENTATION_180    = 3,
    EXIF_ORIENTATION_270    = 8,
} ExifOrientationType;

typedef enum {
    EXIF_SCENE_STANDARD,
    EXIF_SCENE_LANDSCAPE,
    EXIF_SCENE_PORTRAIT,
    EXIF_SCENE_NIGHT,
} CamExifSceneCaptureType;

typedef enum {
    EXIF_METERING_UNKNOWN,
    EXIF_METERING_AVERAGE,
    EXIF_METERING_CENTER,
    EXIF_METERING_SPOT,
    EXIF_METERING_MULTISPOT,
    EXIF_METERING_PATTERN,
    EXIF_METERING_PARTIAL,
    EXIF_METERING_OTHER     = 255,
} CamExifMeteringModeType;

typedef enum {
    EXIF_EXPOSURE_AUTO,
    EXIF_EXPOSURE_MANUAL,
    EXIF_EXPOSURE_AUTO_BRACKET,
} CamExifExposureModeType;

typedef enum {
    EXIF_WB_AUTO,
    EXIF_WB_MANUAL,
    EXIF_WB_INCANDESCENT,
    EXIF_WB_FLUORESCENT,
    EXIF_WB_DAYLIGHT,
    EXIF_WB_CLOUDY,
} CamExifWhiteBalanceType;

/* Values */
#define EXIF_DEF_MAKER          "Samsung Electronics Co., Ltd."    /* testJpegExif on the CTS test. This should match Build.MANUFACTURER  */
#define EXIF_DEF_MODEL          "SAMSUNG"
#define EXIF_DEF_SOFTWARE       "SAMSUNG"
#define EXIF_DEF_EXIF_VERSION   "0220"
#define EXIF_DEF_USERCOMMENTS   "User comments"

#define EXIF_DEF_YCBCR_POSITIONING  1   /* centered */
#define EXIF_DEF_EXPOSURE_MANUAL    1   /* manual program */
#define EXIF_DEF_EXPOSURE_PROGRAM   2   /* normal program */
#define EXIF_DEF_FLASH              0   /* O: off, 1: on*/
#define EXIF_DEF_COLOR_SPACE        1
#define EXIF_DEF_INTEROPERABILITY  (0)
#define EXIF_DEF_EXPOSURE_MODE      EXIF_EXPOSURE_AUTO
#define EXIF_DEF_APEX_DEN          (100)

#define EXIF_DEF_COMPRESSION        6
#define EXIF_DEF_RESOLUTION_NUM     72
#define EXIF_DEF_RESOLUTION_DEN     1
#define EXIF_DEF_RESOLUTION_UNIT    2   /* inches */

#define APP_MARKER_2                2
#define APP_MARKER_3                3
#define APP_MARKER_4                4
#define APP_MARKER_5                5
#define APP_MARKER_6                6

typedef struct {
    uint32_t num;
    uint32_t den;
} rational_t;

typedef struct {
    int32_t num;
    int32_t den;
} srational_t;

typedef struct {
    bool enableGps;
    bool enableThumb;

    char maker[32];
    char model[32];
    char software[32];
    char exif_version[4];
    char date_time[20];
    char sec_time[5];
    unsigned int  maker_note_size;
    unsigned char *maker_note;
    unsigned int  user_comment_size;
    unsigned char *user_comment;

    uint32_t width;
    uint32_t height;
    uint32_t widthThumb;
    uint32_t heightThumb;

    uint16_t orientation;
    uint16_t ycbcr_positioning;
    uint16_t exposure_program;
    uint16_t iso_speed_rating;
    uint16_t metering_mode;
    uint16_t flash;
    uint16_t color_space;
    uint16_t interoperability_index;
    uint16_t custom_rendered;
    uint16_t contrast;
    uint16_t saturation;
    uint16_t sharpness;

    uint16_t exposure_mode;
    uint16_t white_balance;
    uint16_t focal_length_in_35mm_length;
    uint16_t scene_capture_type;
    char unique_id[30];

    rational_t exposure_time;
    rational_t fnumber;
    rational_t aperture;
    rational_t max_aperture;
    rational_t focal_length;
    rational_t digital_zoom_ratio;

    srational_t shutter_speed;
    srational_t brightness;
    srational_t exposure_bias;

    char gps_latitude_ref[2];
    char gps_longitude_ref[2];

    uint8_t gps_version_id[4];
    uint8_t gps_altitude_ref;

    rational_t gps_latitude[3];
    rational_t gps_longitude[3];
    rational_t gps_altitude;
    rational_t gps_timestamp[3];
    char gps_datestamp[11];
    char gps_processing_method[100];

    rational_t x_resolution;
    rational_t y_resolution;
    uint16_t resolution_unit;
    uint16_t compression_scheme;
} exif_attribute_t;

typedef struct {
    int num_of_appmarker; /* number of app marker */
    int idx[15][1]; /* idx[number_of_appmarker][appmarker_number] */
    char *debugData[15]; /* 0-base */
    unsigned int debugSize[15];
} debug_attribute_t;

bool UpdateDebugData(char *jpeg, size_t jpeglen, debug_attribute_t *debug);
bool UpdateExif(char *jpeg, size_t jpeglen, exif_attribute_t *exif);

#endif /* __HARDWARE_EXYNOS_EXYNOS_EXIF_H__ */
