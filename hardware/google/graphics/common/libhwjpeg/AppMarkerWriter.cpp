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

#include "AppMarkerWriter.h"

#include "IFDWriter.h"
#include "hwjpeg-internal.h"

static const char ExifAsciiPrefix[] = {'A', 'S', 'C', 'I', 'I', 0x0, 0x0, 0x0};
static const char ExifIdentifierCode[6] = {'E', 'x', 'i', 'f', 0x00, 0x00};
static char TiffHeader[8] = {'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00};
static const unsigned char ComponentsConfiguration[4] = {1, 2, 3, 0}; // YCbCr
static const unsigned char SceneType[4] = {1, 0, 0, 0}; // A directly photographed image

#ifndef __LITTLE_ENDIAN__
CEndianessChecker __LITTLE_ENDIAN__;
#endif

CEndianessChecker::CEndianessChecker() {
    int num = 1;
    __little = (*reinterpret_cast<char *>(&num) == 1);
    if (__little) {
        TiffHeader[0] = 'I';
        TiffHeader[1] = 'I';
    } else {
        TiffHeader[0] = 'M';
        TiffHeader[1] = 'M';
    }
}

CAppMarkerWriter::CAppMarkerWriter()
      : m_pAppBase(NULL), m_pApp1End(NULL), m_pExif(NULL), m_pExtra(NULL) {
    Init();
}

CAppMarkerWriter::CAppMarkerWriter(char *base, exif_attribute_t *exif, debug_attribute_t *debug) {
    extra_appinfo_t extraInfo;
    app_info_t appInfo[15];

    memset(&extraInfo, 0, sizeof(extraInfo));
    memset(&appInfo, 0, sizeof(appInfo));

    extraInfo.appInfo = appInfo;

    ExtractDebugAttributeInfo(debug, &extraInfo);

    PrepareAppWriter(base, exif, &extraInfo);
}

void CAppMarkerWriter::Init() {
    m_pApp1End = NULL;

    m_szApp1 = 0;

    m_n0thIFDFields = 0;
    m_n1stIFDFields = 0;
    m_nExifIFDFields = 0;
    m_nGPSIFDFields = 0;

    m_szMake = 0;
    m_szSoftware = 0;
    m_szModel = 0;
    m_szUniqueID = 0;

    m_pThumbBase = NULL;
    m_szMaxThumbSize = 0;
    m_pThumbSizePlaceholder = NULL;
}

void CAppMarkerWriter::PrepareAppWriter(char *base, exif_attribute_t *exif,
                                        extra_appinfo_t *extra) {
    m_pAppBase = base;
    m_pExif = exif;

    Init();

    size_t applen = 0;

    if (exif) {
        // APP1
        applen += JPEG_SEGMENT_LENFIELD_SIZE + ARRSIZE(ExifIdentifierCode) + ARRSIZE(TiffHeader);

        // 0th IFD: Make, Model, Orientation, Software,
        //          DateTime, YCbCrPositioning, X/Y Resolutions, Exif and GPS
        applen += IFD_FIELDCOUNT_SIZE + IFD_VALOFF_SIZE;
        // Orientation, YCbCrPos, XYRes/Unit, DateTime and Exif
        m_n0thIFDFields = 7;
        applen += IFD_FIELD_SIZE * m_n0thIFDFields;
        applen += sizeof(rational_t) * 2; // values of XResolution and YResolution
        applen += EXIF_DATETIME_LENGTH;

        m_szMake = strlen(m_pExif->maker);
        if (m_szMake > 0) {
            m_n0thIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_szMake > 3) applen += m_szMake + 1;
        }

        m_szSoftware = strlen(m_pExif->software);
        if (m_szSoftware > 0) {
            m_n0thIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_szSoftware > 3) applen += m_szSoftware + 1;
        }

        m_szModel = strlen(m_pExif->model);
        if (m_szModel > 0) {
            m_n0thIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_szModel > 3) applen += m_szModel + 1;
        }

        if (m_pExif->enableGps) {
            m_n0thIFDFields++;
            applen += IFD_FIELD_SIZE;
        }

        /*
         * Exif SubIFD: 37 fields
         * Fields with no data offset: 19
         * - ExposureProgram, PhotographicSensitivity, ExifVersion, MeteringMode,
         * - Flash, FlashPixVersion, ColorSpace, PixelXDimension, PixelYDimension,
         * - ExposureMode, WhiteBalance, FocalLengthIn35mmFilm, SceneCaptureType,
         * - ComponentsConfiguration
         * - SceneType, CustomRendered, Contrast, Saturation, Sharpness
         * (S)Rational Fields: 9
         * - ExposureTime, FNumber, ShutterSpeedValue, ApertureValue,
         * - BrightnessValue, ExposureBiasValue, MaxApertureValue, FocalLength
         * - DigitalZoomRatio
         * ASCII Fields: 6
         * - DateTimeOriginal, DateTimeDigitized, SubsecTime, SubsecTimeOriginal,
         * - SubsecTimeDigitized, ImageUniqueID
         * Undefined Long Fields: 2
         * - MakerNote, UserComment
         * SubIFD: 1
         * - Interoperability IFD
         */
        m_nExifIFDFields = 28; // rational fields and fields withouth data offset
        applen += IFD_FIELDCOUNT_SIZE + IFD_VALOFF_SIZE;
        applen += IFD_FIELD_SIZE * m_nExifIFDFields;
        applen += sizeof(rational_t) * 9; // 9 rational values

        // DateTime*
        m_nExifIFDFields += 2;
        applen += (IFD_FIELD_SIZE + EXIF_DATETIME_LENGTH) * 2;

        // SubSecTime*
        m_nExifIFDFields += 3;
        applen += (IFD_FIELD_SIZE + EXIF_SUBSECTIME_LENGTH) * 3;

        m_szUniqueID = strlen(m_pExif->unique_id); // len should be 32!
        if (m_szUniqueID > 0) {
            m_nExifIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_szUniqueID > 3) applen += m_szUniqueID + 1;
        }

        if (m_pExif->maker_note_size > 0) {
            m_nExifIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_pExif->maker_note_size > 4) applen += m_pExif->maker_note_size;
        }

        if (m_pExif->user_comment_size > 0) {
            m_nExifIFDFields++;
            applen += IFD_FIELD_SIZE;
            if (m_pExif->user_comment_size > 4) applen += m_pExif->user_comment_size;
        }

        // Interoperability SubIFD
        m_nExifIFDFields++; // Interoperability is sub IFD of Exif sub IFD
        applen += IFD_FIELD_SIZE + IFD_FIELDCOUNT_SIZE + IFD_VALOFF_SIZE + IFD_FIELD_SIZE * 2;

        if (m_pExif->enableGps) {
            size_t len;
            /*
             * GPS SubIFD: 10 fields
             * Fields with no data offset: 4
             * - GPSVersionID, GPSLattitudeRef, GPSLongitudeRev, GPSAltitudeRef
             * Rational Fields: 4 (total 10 rational values)
             * - GPSLatitude(3), GPSLongitude(3), GPSAltitude(1), GPSTImeStamp(3)
             * ASCII or Undefined fields: 2
             * - PGSProcessingMethod, GPSDateStamp
             */
            m_nGPSIFDFields = 8;
            applen += IFD_FIELDCOUNT_SIZE + IFD_VALOFF_SIZE;
            applen += IFD_FIELD_SIZE * m_nGPSIFDFields;
            applen += sizeof(rational_t) * 10;

            // gps date stamp
            m_nGPSIFDFields += 1;
            applen += IFD_FIELD_SIZE + EXIF_GPSDATESTAMP_LENGTH;

            len = min(strlen(m_pExif->gps_processing_method),
                      MAX_GPS_PROCESSINGMETHOD_SIZE - sizeof(ExifAsciiPrefix) - 1);
            if (len > 0) {
                m_nGPSIFDFields++;
                applen += IFD_FIELD_SIZE + len + sizeof(ExifAsciiPrefix) + 1;
            }
        }

        if (m_pExif->enableThumb) {
            /*
             * 1st IFD: 6
             * Fields with no data offset: 6
             * - ImageWidth, ImageHeight, Compression, Orientation,
             * - JPEGInterchangeFormat, JPEGInterchangeFormatLength
             */
            if ((m_pExif->widthThumb < 16) || (m_pExif->heightThumb < 16)) {
                ALOGE("Insufficient thumbnail information %dx%d", m_pExif->widthThumb,
                      m_pExif->heightThumb);
                return;
            }

            m_n1stIFDFields = 6;
            applen += IFD_FIELDCOUNT_SIZE + IFD_VALOFF_SIZE;
            applen += IFD_FIELD_SIZE * m_n1stIFDFields;

            m_pThumbBase = m_pAppBase + JPEG_MARKER_SIZE + applen;
            m_szMaxThumbSize = JPEG_MAX_SEGMENT_SIZE - applen - JPEG_APP1_OEM_RESERVED;
        }

        m_szApp1 = applen;
    }

    if (extra) {
        for (int idx = 0; idx < extra->num_of_appmarker; idx++) {
            if ((extra->appInfo[idx].appid < EXTRA_APPMARKER_MIN) ||
                (extra->appInfo[idx].appid >= EXTRA_APPMARKER_LIMIT)) {
                ALOGE("Invalid extra APP segment ID %d", extra->appInfo[idx].appid);
                return;
            }

            if ((extra->appInfo[idx].dataSize == 0) ||
                (extra->appInfo[idx].dataSize >
                 (JPEG_MAX_SEGMENT_SIZE - JPEG_SEGMENT_LENFIELD_SIZE))) {
                ALOGE("Invalid APP%d segment size, %u bytes", extra->appInfo[idx].appid,
                      extra->appInfo[idx].dataSize);
                return;
            }

            ALOGD("APP%d: %u bytes", extra->appInfo[idx].appid, extra->appInfo[idx].dataSize);
        }
    }

    m_pExtra = extra;

    //     |<- m_szApp1 ->|<- m_szMaxThumbSize ->|<-m_szAppX->|
    //     |<----- size of total APP1 and APPX segments ----->|<-APP11->|<-- main image
    // m_pAppBase   m_pThumbBase                 |            |    return
    //     |              |                      |            |        ||
    //     v              v                      |            |        v|
    //   --|--------------------------------------------------|---------|-----------
    //   ^ ^              ^                      ^            |        ^^
    //   | |              |                      |            |        ||
    //   |APP1        SOIofThumb                APPX                SOIofMain
    //   |                                                              |
    //  SOI                                                           DHTofMain

    ALOGD("APP1: %u bytes(ThumbMax %zu)", m_szApp1, m_szMaxThumbSize);
}

#define APPMARKLEN (JPEG_MARKER_SIZE + JPEG_SEGMENT_LENFIELD_SIZE)
char *CAppMarkerWriter::WriteAPP11(char *current, size_t dummy, size_t align) {
    ALOG_ASSERT((align & ~align) == 0);

    if ((dummy == 0) && (align == 1)) return current;

    if (!m_pExif && !m_pExtra) return current;

    uint16_t len = PTR_TO_ULONG(current + APPMARKLEN) & (align - 1);

    if (len) len = align - len;

    len += dummy + JPEG_SEGMENT_LENFIELD_SIZE;

    *current++ = 0xFF;
    *current++ = 0xEB;
    WriteDataInBig(current, len);

    return current + len;
}

char *CAppMarkerWriter::WriteAPPX(char *current, bool just_reserve) {
    if (!m_pExtra) return current;

    for (int idx = 0; idx < m_pExtra->num_of_appmarker; idx++) {
        int appid = m_pExtra->appInfo[idx].appid;
        uint16_t len = m_pExtra->appInfo[idx].dataSize + JPEG_SEGMENT_LENFIELD_SIZE;

        // APPx marker
        *current++ = 0xFF;
        *current++ = 0xE0 + (appid & 0xF);
        // APPx length
        current = WriteDataInBig(current, len);
        // APPx data
        if (!just_reserve)
            memcpy(current, m_pExtra->appInfo[idx].appData, m_pExtra->appInfo[idx].dataSize);
        current += m_pExtra->appInfo[idx].dataSize;
    }

    return current;
}

char *CAppMarkerWriter::WriteAPP1(char *current, bool reserve_thumbnail_space, bool updating) {
    if (!m_pExif) return current;

    // APP1 Marker
    *current++ = 0xFF;
    *current++ = 0xE1;

    // APP1 length
    if (updating) {
        current += JPEG_SEGMENT_LENFIELD_SIZE;
    } else {
        uint16_t len = m_szApp1;
        if (reserve_thumbnail_space) len += m_szMaxThumbSize + JPEG_APP1_OEM_RESERVED;
        current = WriteDataInBig(current, len);
    }

    // Exif Identifier
    for (size_t i = 0; i < ARRSIZE(ExifIdentifierCode); i++) *current++ = ExifIdentifierCode[i];

    char *tiffheader = current;
    for (size_t i = 0; i < ARRSIZE(TiffHeader); i++) *current++ = TiffHeader[i];

    CIFDWriter writer(tiffheader, current, m_n0thIFDFields);

    writer.WriteShort(EXIF_TAG_ORIENTATION, 1, &m_pExif->orientation);
    writer.WriteShort(EXIF_TAG_YCBCR_POSITIONING, 1, &m_pExif->ycbcr_positioning);
    writer.WriteRational(EXIF_TAG_X_RESOLUTION, 1, &m_pExif->x_resolution);
    writer.WriteRational(EXIF_TAG_Y_RESOLUTION, 1, &m_pExif->y_resolution);
    writer.WriteShort(EXIF_TAG_RESOLUTION_UNIT, 1, &m_pExif->resolution_unit);
    if (m_szMake > 0) writer.WriteASCII(EXIF_TAG_MAKE, m_szMake + 1, m_pExif->maker);
    if (m_szModel > 0) writer.WriteASCII(EXIF_TAG_MODEL, m_szModel + 1, m_pExif->model);
    if (m_szSoftware > 0) writer.WriteASCII(EXIF_TAG_SOFTWARE, m_szSoftware + 1, m_pExif->software);
    writer.WriteCString(EXIF_TAG_DATE_TIME, EXIF_DATETIME_LENGTH, m_pExif->date_time);

    char *pSubIFDBase = writer.BeginSubIFD(EXIF_TAG_EXIF_IFD_POINTER);
    if (pSubIFDBase) { // This should be always true!!
        CIFDWriter exifwriter(tiffheader, pSubIFDBase, m_nExifIFDFields);
        exifwriter.WriteRational(EXIF_TAG_EXPOSURE_TIME, 1, &m_pExif->exposure_time);
        exifwriter.WriteRational(EXIF_TAG_FNUMBER, 1, &m_pExif->fnumber);
        exifwriter.WriteShort(EXIF_TAG_EXPOSURE_PROGRAM, 1, &m_pExif->exposure_program);
        exifwriter.WriteShort(EXIF_TAG_ISO_SPEED_RATING, 1, &m_pExif->iso_speed_rating);
        exifwriter.WriteUndef(EXIF_TAG_EXIF_VERSION, 4,
                              reinterpret_cast<unsigned char *>(m_pExif->exif_version));
        exifwriter.WriteCString(EXIF_TAG_DATE_TIME_ORG, EXIF_DATETIME_LENGTH, m_pExif->date_time);
        exifwriter.WriteCString(EXIF_TAG_DATE_TIME_DIGITIZE, EXIF_DATETIME_LENGTH,
                                m_pExif->date_time);
        exifwriter.WriteSRational(EXIF_TAG_SHUTTER_SPEED, 1, &m_pExif->shutter_speed);
        exifwriter.WriteRational(EXIF_TAG_APERTURE, 1, &m_pExif->aperture);
        exifwriter.WriteSRational(EXIF_TAG_BRIGHTNESS, 1, &m_pExif->brightness);
        exifwriter.WriteSRational(EXIF_TAG_EXPOSURE_BIAS, 1, &m_pExif->exposure_bias);
        exifwriter.WriteRational(EXIF_TAG_MAX_APERTURE, 1, &m_pExif->max_aperture);
        exifwriter.WriteShort(EXIF_TAG_METERING_MODE, 1, &m_pExif->metering_mode);
        exifwriter.WriteShort(EXIF_TAG_FLASH, 1, &m_pExif->flash);
        exifwriter.WriteUndef(EXIF_TAG_FLASHPIX_VERSION, 4,
                              reinterpret_cast<const unsigned char *>("0100"));
        exifwriter.WriteUndef(EXIF_TAG_COMPONENTS_CONFIGURATION, 4, ComponentsConfiguration);
        exifwriter.WriteRational(EXIF_TAG_FOCAL_LENGTH, 1, &m_pExif->focal_length);
        exifwriter.WriteCString(EXIF_TAG_SUBSEC_TIME, EXIF_SUBSECTIME_LENGTH, m_pExif->sec_time);
        exifwriter.WriteCString(EXIF_TAG_SUBSEC_TIME_ORIG, EXIF_SUBSECTIME_LENGTH,
                                m_pExif->sec_time);
        exifwriter.WriteCString(EXIF_TAG_SUBSEC_TIME_DIG, EXIF_SUBSECTIME_LENGTH,
                                m_pExif->sec_time);
        if (m_pExif->maker_note_size > 0)
            exifwriter.WriteUndef(EXIF_TAG_MAKER_NOTE, m_pExif->maker_note_size,
                                  m_pExif->maker_note);
        if (m_pExif->user_comment_size > 0)
            exifwriter.WriteUndef(EXIF_TAG_USER_COMMENT, m_pExif->user_comment_size,
                                  m_pExif->user_comment);
        exifwriter.WriteShort(EXIF_TAG_COLOR_SPACE, 1, &m_pExif->color_space);
        exifwriter.WriteLong(EXIF_TAG_PIXEL_X_DIMENSION, 1, &m_pExif->width);
        exifwriter.WriteLong(EXIF_TAG_PIXEL_Y_DIMENSION, 1, &m_pExif->height);
        exifwriter.WriteUndef(EXIF_TAG_SCENE_TYPE, sizeof(SceneType), SceneType);
        exifwriter.WriteShort(EXIF_TAG_CUSTOM_RENDERED, 1, &m_pExif->custom_rendered);
        exifwriter.WriteShort(EXIF_TAG_EXPOSURE_MODE, 1, &m_pExif->exposure_mode);
        exifwriter.WriteShort(EXIF_TAG_WHITE_BALANCE, 1, &m_pExif->white_balance);
        exifwriter.WriteRational(EXIF_TAG_DIGITAL_ZOOM_RATIO, 1, &m_pExif->digital_zoom_ratio);
        exifwriter.WriteShort(EXIF_TAG_FOCA_LENGTH_IN_35MM_FILM, 1,
                              &m_pExif->focal_length_in_35mm_length);
        exifwriter.WriteShort(EXIF_TAG_SCENCE_CAPTURE_TYPE, 1, &m_pExif->scene_capture_type);
        exifwriter.WriteShort(EXIF_TAG_CONTRAST, 1, &m_pExif->contrast);
        exifwriter.WriteShort(EXIF_TAG_SATURATION, 1, &m_pExif->saturation);
        exifwriter.WriteShort(EXIF_TAG_SHARPNESS, 1, &m_pExif->sharpness);
        if (m_szUniqueID > 0)
            exifwriter.WriteASCII(EXIF_TAG_IMAGE_UNIQUE_ID, m_szUniqueID + 1, m_pExif->unique_id);
        pSubIFDBase = exifwriter.BeginSubIFD(EXIF_TAG_INTEROPERABILITY);
        if (pSubIFDBase) {
            CIFDWriter interopwriter(tiffheader, pSubIFDBase, 2);
            interopwriter.WriteASCII(EXIF_TAG_INTEROPERABILITY_INDEX, 4,
                                     m_pExif->interoperability_index ? "THM" : "R98");
            interopwriter.WriteUndef(EXIF_TAG_INTEROPERABILITY_VERSION, 4,
                                     reinterpret_cast<const unsigned char *>("0100"));
            interopwriter.Finish(true);
            exifwriter.EndSubIFD(interopwriter.GetNextIFDBase());
        } else {
            exifwriter.CancelSubIFD();
        }
        exifwriter.Finish(true);
        writer.EndSubIFD(exifwriter.GetNextIFDBase());
    } else {
        writer.CancelSubIFD();
    }

    if (m_pExif->enableGps) {
        pSubIFDBase = writer.BeginSubIFD(EXIF_TAG_GPS_IFD_POINTER);
        if (pSubIFDBase) { // This should be always true!!
            CIFDWriter gpswriter(tiffheader, pSubIFDBase, m_nGPSIFDFields);
            gpswriter.WriteByte(EXIF_TAG_GPS_VERSION_ID, 4, m_pExif->gps_version_id);
            gpswriter.WriteASCII(EXIF_TAG_GPS_LATITUDE_REF, 2, m_pExif->gps_latitude_ref);
            gpswriter.WriteRational(EXIF_TAG_GPS_LATITUDE, 3, m_pExif->gps_latitude);
            gpswriter.WriteASCII(EXIF_TAG_GPS_LONGITUDE_REF, 2, m_pExif->gps_longitude_ref);
            gpswriter.WriteRational(EXIF_TAG_GPS_LONGITUDE, 3, m_pExif->gps_longitude);
            gpswriter.WriteByte(EXIF_TAG_GPS_ALTITUDE_REF, 1, &m_pExif->gps_altitude_ref);
            gpswriter.WriteRational(EXIF_TAG_GPS_ALTITUDE, 1, &m_pExif->gps_altitude);
            gpswriter.WriteCString(EXIF_TAG_GPS_DATESTAMP, EXIF_GPSDATESTAMP_LENGTH,
                                   m_pExif->gps_datestamp);
            gpswriter.WriteRational(EXIF_TAG_GPS_TIMESTAMP, 3, m_pExif->gps_timestamp);
            size_t len = strlen(m_pExif->gps_processing_method);
            if (len > 0) {
                size_t idx;
                len = min(len, static_cast<size_t>(99UL));
                unsigned char buf[sizeof(ExifAsciiPrefix) + len + 1];
                for (idx = 0; idx < sizeof(ExifAsciiPrefix); idx++) buf[idx] = ExifAsciiPrefix[idx];
                strncpy(reinterpret_cast<char *>(buf) + idx, m_pExif->gps_processing_method,
                        len + 1);
                len += idx;
                buf[len] = '\0';
                gpswriter.WriteUndef(EXIF_TAG_GPS_PROCESSING_METHOD, len + 1, buf);
            }
            gpswriter.Finish(true);
            writer.EndSubIFD(gpswriter.GetNextIFDBase());
        } else {
            writer.CancelSubIFD();
        }
    }

    // thumbnail and the next IFD pointer is never updated.
    if (updating) return NULL;

    if (m_pExif->enableThumb) {
        writer.Finish(false);

        CIFDWriter thumbwriter(tiffheader, writer.GetNextIFDBase(), m_n1stIFDFields);
        thumbwriter.WriteLong(EXIF_TAG_IMAGE_WIDTH, 1, &m_pExif->widthThumb);
        thumbwriter.WriteLong(EXIF_TAG_IMAGE_HEIGHT, 1, &m_pExif->heightThumb);
        thumbwriter.WriteShort(EXIF_TAG_COMPRESSION_SCHEME, 1, &m_pExif->compression_scheme);
        thumbwriter.WriteShort(EXIF_TAG_ORIENTATION, 1, &m_pExif->orientation);

        ALOG_ASSERT(thumbwriter.GetNextIFDBase() != m_pThumbBase);
        uint32_t offset = thumbwriter.Offset(m_pThumbBase);
        thumbwriter.WriteLong(EXIF_TAG_JPEG_INTERCHANGE_FORMAT, 1, &offset);
        offset = 0; // temporarilly 0 byte
        thumbwriter.WriteLong(EXIF_TAG_JPEG_INTERCHANGE_FORMAT_LEN, 1, &offset);
        m_pThumbSizePlaceholder = thumbwriter.GetNextTagAddress() - 4;
        thumbwriter.Finish(true);

        size_t thumbspace = reserve_thumbnail_space ? m_szMaxThumbSize + JPEG_APP1_OEM_RESERVED : 0;

        return thumbwriter.GetNextIFDBase() + thumbspace;
    }

    writer.Finish(true);

    return writer.GetNextIFDBase();
}

void CAppMarkerWriter::Finalize(size_t thumbsize) {
    if (m_pThumbSizePlaceholder) {
        uint32_t len = static_cast<uint32_t>(thumbsize);
        WriteData(m_pThumbSizePlaceholder, len);
        m_pThumbSizePlaceholder = NULL;
    }
}
void CAppMarkerWriter::UpdateApp1Size(size_t amount) {
    if (m_pAppBase) {
        uint16_t len = m_szApp1 + amount;
        WriteDataInBig(m_pAppBase + JPEG_MARKER_SIZE, len);
    }
}

static const char *dbgerrmsg = "Updating debug data failed";

static inline size_t GetSegLen(char *p) {
    size_t len = (*reinterpret_cast<unsigned char *>(p) & 0xFF) << 8;
    return len | (*reinterpret_cast<unsigned char *>(p + 1) & 0xFF);
}

static inline size_t GetExtraAPPSize(extra_appinfo_t *info) {
    size_t len = 0;

    for (int idx = 0; idx < info->num_of_appmarker; idx++) {
        if ((info->appInfo[idx].appid < EXTRA_APPMARKER_MIN) ||
            (info->appInfo[idx].appid >= EXTRA_APPMARKER_LIMIT)) {
            ALOGE("%s: Invalid extra APP segment ID %d", dbgerrmsg, info->appInfo[idx].appid);
            return 0;
        }

        if ((info->appInfo[idx].dataSize == 0) ||
            (info->appInfo[idx].dataSize > (JPEG_MAX_SEGMENT_SIZE - JPEG_SEGMENT_LENFIELD_SIZE))) {
            ALOGE("%s: Invalid APP%d segment size, %u bytes.", dbgerrmsg, info->appInfo[idx].appid,
                  info->appInfo[idx].dataSize);
            return 0;
        }

        len += info->appInfo[idx].dataSize + JPEG_MARKER_SIZE + JPEG_SEGMENT_LENFIELD_SIZE;
    }

    return len;
}

bool UpdateDebugData(char *jpeg, size_t jpeglen, debug_attribute_t *debug) // include/ExynosExif.h
{
    extra_appinfo_t extraInfo;
    app_info_t appInfo[15];

    memset(&extraInfo, 0, sizeof(extraInfo));
    memset(&appInfo, 0, sizeof(appInfo));

    extraInfo.appInfo = appInfo;

    ExtractDebugAttributeInfo(debug, &extraInfo);

    UpdateDebugData(jpeg, jpeglen, &extraInfo);

    return true;
}

bool UpdateDebugData(char *jpeg, size_t jpeglen, extra_appinfo_t *extra) // include/ExynosExif.h
{
    if (!extra) {
        ALOGI("No data to update in APPx");
        return true;
    }

    size_t validlen = GetExtraAPPSize(extra);

    if (jpeglen < (validlen + JPEG_MARKER_SIZE)) {
        ALOGE("%s: Too small JPEG stream length %zu", dbgerrmsg, jpeglen);
        return false;
    }

    if ((*jpeg++ != 0xFF) || (*jpeg++ != 0xD8)) {
        ALOGE("%s: %p is not a valid JPEG stream", dbgerrmsg, jpeg);
        return false;
    }
    jpeglen -= 2;

    int idx = 0;

    while ((*jpeg++ == 0xFF) && (validlen > 0) && (jpeglen > validlen)) {
        size_t seglen;
        char marker;
        int appid;

        marker = *jpeg++;
        jpeglen -= 2;

        if ((marker == 0xDA) || (marker == 0xD9)) { // SOS and EOI
            ALOGE("%s: No further space found for APPx metadata", dbgerrmsg);
            return false;
        }

        appid = marker & 0xF;
        if (((marker & 0xF0) == 0xE0) &&
            ((appid >= EXTRA_APPMARKER_MIN) && (appid <= EXTRA_APPMARKER_LIMIT))) {
            if (appid != extra->appInfo[idx].appid) {
                ALOGE("%s: stored appid(%d) is different with updated appid(%d)", dbgerrmsg, appid,
                      extra->appInfo[idx].appid);
                return false;
            }

            seglen = GetSegLen(jpeg);
            if (seglen < (extra->appInfo[idx].dataSize + JPEG_SEGMENT_LENFIELD_SIZE)) {
                ALOGE("%s: too small APP%d length %zu to store %u bytes", dbgerrmsg, appid, seglen,
                      extra->appInfo[idx].dataSize);
                return false;
            }

            memcpy(jpeg + JPEG_SEGMENT_LENFIELD_SIZE, extra->appInfo[idx].appData,
                   extra->appInfo[idx].dataSize);
            ALOGD("Successfully updated %u bytes to APP%d", extra->appInfo[idx].dataSize, appid);

            validlen -=
                    extra->appInfo[idx].dataSize + JPEG_MARKER_SIZE + JPEG_SEGMENT_LENFIELD_SIZE;
            idx++;
        } else {
            // just skip all other segments
            seglen = GetSegLen(jpeg);
            if (seglen == 0) seglen++; // fixup for invalid segment lengths
            if (jpeglen < seglen) seglen = jpeglen;
        }

        jpeg += seglen;
        jpeglen -= seglen;
    }

    return true;
}

static const char *exiferrmsg = "Updating exif failed";

bool UpdateExif(char *jpeg, size_t jpeglen, exif_attribute_t *exif) {
    if (!exif) {
        ALOGI("No Exif to update");
        return true;
    }

    if (jpeglen < (JPEG_MARKER_SIZE * 2 + JPEG_SEGMENT_LENFIELD_SIZE)) {
        ALOGE("%s: Too small stream length %zu", exiferrmsg, jpeglen);
        return false;
    }

    if ((*jpeg++ != 0xFF) || (*jpeg++ != 0xD8)) {
        ALOGE("%s: %p is not a valid JPEG stream", exiferrmsg, jpeg);
        return false;
    }

    if ((*jpeg != 0xFF) || (*(jpeg + 1) != 0xE1)) {
        ALOGE("%s: APP1 marker is not found", exiferrmsg);
        return false;
    }

    if (jpeglen < GetSegLen(jpeg + JPEG_MARKER_SIZE)) {
        ALOGE("%s: Too small stream length %zu", exiferrmsg, jpeglen);
        return false;
    }

    CAppMarkerWriter writer(jpeg, exif, NULL);
    writer.Update();

    ALOGD("Successfully updated Exif");

    return true;
}

void ExtractDebugAttributeInfo(debug_attribute_t *debug, extra_appinfo_t *extra) {
    if (!debug) {
        extra->num_of_appmarker = 0;
        return;
    }

    extra->num_of_appmarker = debug->num_of_appmarker;
    for (int idx = 0; idx < debug->num_of_appmarker; idx++) {
        int appid = debug->idx[idx][0];
        extra->appInfo[idx].appid = appid;
        extra->appInfo[idx].appData = debug->debugData[appid];
        extra->appInfo[idx].dataSize = debug->debugSize[appid];
    }
}
