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

#define FAILURE_DEBUG_PREFIX "exif"

#include <vector>

#if defined(__LP64__)
#include <time.h>
using Timestamp = time_t;
#define TIMESTAMP_TO_TM(timestamp, tm) gmtime_r(timestamp, tm)
#else
#include <time64.h>
using Timestamp = time64_t;
#define TIMESTAMP_TO_TM(timestamp, tm) gmtime64_r(timestamp, tm)
#endif

#include <math.h>

#include <android-base/properties.h>
#include <system/camera_metadata.h>

#include "exif.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace exif {
namespace {

struct ExifMemDeleter {
    void operator()(ExifMem* allocator) const {
        exif_mem_unref(allocator);
    }
};

typedef std::unique_ptr<ExifMem, ExifMemDeleter> ExifMemPtr;

ExifEntry* allocateEntry(ExifMem* mem, const ExifTag tag, const ExifFormat format,
                         const size_t numComponents) {
    ExifEntry* e = exif_entry_new_mem(mem);
    const size_t size = numComponents * exif_format_get_size(format);
    e->data = static_cast<unsigned char*>(exif_mem_alloc(mem, size));
    e->size = size;
    e->tag = tag;
    e->components = numComponents;
    e->format = format;
    return e;
}

void appendEntry(ExifData* edata, ExifMem* mem,
                 const ExifIfd ifd, const ExifTag tag) {
    ExifEntry* e = exif_entry_new_mem(mem);
    exif_entry_initialize(e, tag);
    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

void appendEntryU8(ExifData* edata, ExifMem* mem,
                    const ExifIfd ifd, const ExifTag tag,
                    const uint8_t value) {
    ExifEntry* e = allocateEntry(mem, tag, EXIF_FORMAT_BYTE, 1);
    *e->data = value;
    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

void appendEntryU16(ExifData* edata, ExifMem* mem,
                    const ExifIfd ifd, const ExifTag tag,
                    const uint16_t value) {
    ExifEntry* e = allocateEntry(mem, tag, EXIF_FORMAT_SHORT, 1);
    exif_set_short(e->data, exif_data_get_byte_order(edata), value);
    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

void appendEntryU32(ExifData* edata, ExifMem* mem,
                    const ExifIfd ifd, const ExifTag tag,
                    const uint32_t value) {
    ExifEntry* e = allocateEntry(mem, tag, EXIF_FORMAT_LONG, 1);
    exif_set_long(e->data, exif_data_get_byte_order(edata), value);
    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

void appendEntryR32(ExifData* edata, ExifMem* mem,
                    const ExifIfd ifd, const ExifTag tag,
                    const ExifRational* src, size_t n) {
    ExifEntry* e = allocateEntry(mem, tag, EXIF_FORMAT_RATIONAL, n);

    for (uint8_t* dst = e->data; n > 0; --n, ++src, dst += sizeof(ExifRational)) {
        exif_set_rational(dst, exif_data_get_byte_order(edata), *src);
    }

    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

void appendEntryR32(ExifData* edata, ExifMem* mem,
                    const ExifIfd ifd, const ExifTag tag,
                    const uint32_t num, const uint32_t dem) {
    const ExifRational value = { num, dem };
    appendEntryR32(edata, mem, ifd, tag, &value, 1);
}

void appendEntryS(ExifData* edata, ExifMem* mem,
                  const ExifIfd ifd, const ExifTag tag,
                  const char* value, const size_t size,
                  const ExifFormat format) {
    ExifEntry* e = allocateEntry(mem, tag, format, size);
    memcpy(e->data, value, size);
    exif_content_add_entry(edata->ifd[ifd], e);
    exif_entry_unref(e);
}

std::tuple<uint32_t, uint32_t, uint32_t> convertDegToDegMmSs(double v) {
    const uint32_t ideg = floor(v);
    v = (v - ideg) * 60;
    const uint32_t minutes = floor(v);
    v = (v - minutes) * 60;
    const uint32_t secondsM = round(v * 1000000);
    return {ideg, minutes, secondsM};
}

struct tm convertT64ToTm(const int64_t t) {
    Timestamp t2 = t;
    struct tm result;
    TIMESTAMP_TO_TM(&t2, &result);
    return result;
}

}  // namespace

ExifDataPtr createExifData(const CameraMetadata& metadata,
                           const Rect<uint16_t> size) {
    const camera_metadata_t* const rawMetadata =
        reinterpret_cast<const camera_metadata_t*>(metadata.metadata.data());
    camera_metadata_ro_entry_t metadataEntry;

    ExifMemPtr allocator(exif_mem_new_default());
    ExifDataPtr exifData(exif_data_new_mem(allocator.get()));

    exif_data_set_option(exifData.get(), EXIF_DATA_OPTION_FOLLOW_SPECIFICATION);
    exif_data_set_data_type(exifData.get(), EXIF_DATA_TYPE_COMPRESSED);
    exif_data_set_byte_order(exifData.get(), EXIF_BYTE_ORDER_INTEL);
    exif_data_fix(exifData.get());

    {
        const std::string v = base::GetProperty("ro.product.manufacturer", "");
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_0, EXIF_TAG_MAKE,
                     v.c_str(), v.size() + 1, EXIF_FORMAT_ASCII);
    }
    {
        const std::string v = base::GetProperty("ro.product.model", "");
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_0, EXIF_TAG_MODEL,
                     v.c_str(), v.size() + 1, EXIF_FORMAT_ASCII);
    }

    {
        struct tm now;
        {
            time_t t = time(nullptr);
            localtime_r(&t, &now);
        }

        char timeStr[20];
        const int len = snprintf(timeStr, sizeof(timeStr),
                                 "%04d:%02d:%02d %02d:%02d:%02d",
                                 now.tm_year + 1900, now.tm_mon + 1, now.tm_mday,
                                 now.tm_hour, now.tm_min, now.tm_sec) + 1;
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_0,
                     EXIF_TAG_DATE_TIME, timeStr, len, EXIF_FORMAT_ASCII);
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                     EXIF_TAG_DATE_TIME_ORIGINAL, timeStr, len, EXIF_FORMAT_ASCII);
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                     EXIF_TAG_DATE_TIME_DIGITIZED, timeStr, len, EXIF_FORMAT_ASCII);
    }
    {
        char ddd[4];
        const int len = snprintf(ddd, sizeof(ddd), "%03d", 0);
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                     EXIF_TAG_SUB_SEC_TIME, ddd, len, EXIF_FORMAT_ASCII);
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                     EXIF_TAG_SUB_SEC_TIME_ORIGINAL, ddd, len, EXIF_FORMAT_ASCII);
        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                     EXIF_TAG_SUB_SEC_TIME_DIGITIZED, ddd, len, EXIF_FORMAT_ASCII);
    }

    if ((size.width > 0) && (size.height > 0)) {
        appendEntryU32(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_PIXEL_X_DIMENSION, size.width);
        appendEntryU32(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_PIXEL_Y_DIMENSION, size.width);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_ORIENTATION,
                                       &metadataEntry)) {
        unsigned v;
        switch (metadataEntry.data.i32[0]) {
        default:
        case 0:     v = 1; break;
        case 90:    v = 6; break;
        case 180:   v = 3; break;
        case 270:   v = 8; break;
        }

        appendEntryU16(exifData.get(), allocator.get(), EXIF_IFD_0,
                       EXIF_TAG_ORIENTATION, v);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_LENS_APERTURE,
                                       &metadataEntry)) {
        const float v = metadataEntry.data.f[0];
        appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_FNUMBER, uint32_t(v * 1000U), 1000U);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_LENS_FOCAL_LENGTH,
                                       &metadataEntry)) {
        const float v = metadataEntry.data.f[0];
        appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_FOCAL_LENGTH, uint32_t(v * 1000U), 1000U);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_FLASH_MODE,
                                       &metadataEntry)) {
        const unsigned v =
            (metadataEntry.data.i32[0] == ANDROID_FLASH_MODE_OFF) ? 0 : 1;
        appendEntryU16(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_FLASH, v);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_SENSOR_EXPOSURE_TIME,
                                       &metadataEntry)) {
        int64_t num = metadataEntry.data.i64[0];
        uint32_t dem = 1000000000U;
        while (num > std::numeric_limits<uint32_t>::max()) {
            num /= 10;
            dem /= 10;
        }

        appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_EXPOSURE_TIME, uint32_t(num), dem);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_SENSOR_SENSITIVITY,
                                       &metadataEntry)) {
        appendEntryU16(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_ISO_SPEED_RATINGS, metadataEntry.data.i32[0]);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_CONTROL_AWB_MODE,
                                       &metadataEntry)) {
        const unsigned v =
            (metadataEntry.data.i32[0] == ANDROID_CONTROL_AWB_MODE_AUTO) ? 0 : 1;

        appendEntryU16(exifData.get(), allocator.get(), EXIF_IFD_EXIF,
                       EXIF_TAG_WHITE_BALANCE, v);
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_GPS_COORDINATES,
                                       &metadataEntry)) {
        {
            const auto [ideg, minutes, secondsM] = convertDegToDegMmSs(
                fabs(metadataEntry.data.d[0]));
            ExifRational degmmss[3] = {{ideg, 1}, {minutes, 1},
                                       {secondsM, 1000000}};
            appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                           static_cast<ExifTag>(EXIF_TAG_GPS_LATITUDE),
                           degmmss, 3);

            const char* latRef = (metadataEntry.data.d[0] < 0.0) ? "S" : "N";
            appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                         static_cast<ExifTag>(EXIF_TAG_GPS_LATITUDE_REF),
                         latRef, 2, EXIF_FORMAT_ASCII);
        }
        {
            const auto [ideg, minutes, secondsM] = convertDegToDegMmSs(
                fabs(metadataEntry.data.d[1]));
            ExifRational degmmss[3] = {{ideg, 1}, {minutes, 1},
                                       {secondsM, 1000000}};
            appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                           static_cast<ExifTag>(EXIF_TAG_GPS_LONGITUDE),
                           degmmss, 3);

            const char* latRef = (metadataEntry.data.d[1] < 0.0) ? "W" : "E";
            appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                         static_cast<ExifTag>(EXIF_TAG_GPS_LONGITUDE_REF),
                         latRef, 2, EXIF_FORMAT_ASCII);
        }
        {
            appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                           static_cast<ExifTag>(EXIF_TAG_GPS_ALTITUDE),
                           static_cast<uint32_t>(fabs(metadataEntry.data.d[2]) * 1000.0),
                           1000);
            appendEntryU8(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                          static_cast<ExifTag>(EXIF_TAG_GPS_ALTITUDE_REF),
                          (metadataEntry.data.d[2] < 0.0) ? 1 : 0);
        }
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_GPS_TIMESTAMP,
                                       &metadataEntry)) {
        struct tm gpsTime = convertT64ToTm(metadataEntry.data.i64[0]);

        {
            char yyyymmdd[12];
            const int len = snprintf(yyyymmdd, sizeof(yyyymmdd), "%04d:%02d:%02d",
                                     gpsTime.tm_year + 1900, gpsTime.tm_mon + 1,
                                     gpsTime.tm_mday) + 1;
            appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                         static_cast<ExifTag>(EXIF_TAG_GPS_DATE_STAMP),
                         yyyymmdd, len, EXIF_FORMAT_ASCII);
        }
        {
            ExifRational hhmmss[3] = {{static_cast<ExifLong>(gpsTime.tm_hour), 1},
                                      {static_cast<ExifLong>(gpsTime.tm_min), 1},
                                      {static_cast<ExifLong>(gpsTime.tm_sec), 1}};
            appendEntryR32(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                           static_cast<ExifTag>(EXIF_TAG_GPS_TIME_STAMP),
                           hhmmss, 3);
        }
    }

    if (!find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_GPS_PROCESSING_METHOD,
                                       &metadataEntry)) {
        // EXIF_FORMAT_UNDEFINED requires this prefix
        std::vector<char> value = {0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00};
        value.insert(value.end(),
                     reinterpret_cast<const char*>(metadataEntry.data.u8),
                     reinterpret_cast<const char*>(metadataEntry.data.u8) + metadataEntry.count);

        appendEntryS(exifData.get(), allocator.get(), EXIF_IFD_GPS,
                     static_cast<ExifTag>(EXIF_TAG_GPS_PROCESSING_METHOD),
                     value.data(), value.size(), EXIF_FORMAT_UNDEFINED);
    }

    return exifData;
}

void* exifDataAllocThumbnail(ExifData* const edata, const size_t size) {
    // WARNING: maloc and free must match the functions that are used in
    // exif_mem_new_default (see above) to manage memory. They will be used in
    // exif_data_free to deallocate memory allocated here.
    void* mem = malloc(size);
    if (mem) {
        if (edata->data) {
            free(edata->data);
        }
        edata->size = size;
        edata->data = static_cast<uint8_t*>(mem);
    }
    return mem;
}

void ExifDataDeleter::operator()(ExifData* p) const {
    exif_data_free(p);
}

}  // namespace exif
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
